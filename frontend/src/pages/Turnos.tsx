import { ensureCsrf, getCsrfCookie } from "../lib/authApi";
import { useEffect, useMemo, useState, useRef } from "react";
import { BASE, RAW_URL, resolveUrl } from "../lib/adminApi";
import { useToast } from '../components/Toast';
import IntroSucursales from "../components/IntroSucursales";
import DatosClienteModal from "../components/DatosClienteModal";

/** ===== Tipos públicos ===== */
type SucursalDTO = { id: number; nombre: string; direccion: string; fotoUrl?: string | null };
type BarberoDTO = { id: number; nombre: string; sucursalId: number; fotoUrl?: string | null };
type ServicioDTO = {
  id: number;
  nombre: string;
  precio: number;
  duracionMin: number;
  descripcion?: string | null;
  sesiones?: number | null;
  adicional: boolean;
};

type ClienteForm = { nombre: string; edad: number; telefono: string };

// Datos de cada sesión
type SesionData = {
  fecha: string | null;
  hora: string | null;
  adicionalesIds: number[];
};

/** ===== Helpers ===== */
async function getJSON<T>(url: string): Promise<T> {
  const res = await fetch(url);
  if (!res.ok) throw new Error(`HTTP ${res.status} — ${url}`);
  return res.json();
}

const PublicApi = {
  async sucursales(): Promise<SucursalDTO[]> {
    return getJSON(`${BASE}/sucursales`);
  },
  async barberosBySucursal(sucursalId: number): Promise<BarberoDTO[]> {
    return getJSON(`${BASE}/barberos?sucursalId=${sucursalId}`);
  },
  async servicios(barberoId?: number): Promise<ServicioDTO[]> {
    const url = barberoId
      ? `${BASE}/servicios?barberoId=${barberoId}`
      : `${BASE}/servicios`;
    return getJSON(url);
  },
};

type DayOpt = { iso: string; label: string };

function buildMonthDays(year: number, monthIdx0: number): DayOpt[] {
  const out: DayOpt[] = [];
  const d = new Date(year, monthIdx0, 1);
  const fmt = (x: Date) =>
    x.toLocaleDateString("es-AR", { weekday: "short", day: "2-digit", month: "short" });
  const pad = (n: number) => String(n).padStart(2, "0");
  while (d.getMonth() === monthIdx0) {
    const iso = `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`;
    out.push({ iso, label: fmt(d) });
    d.setDate(d.getDate() + 1);
  }
  const today = new Date();
  today.setHours(0, 0, 0, 0);
  return out.filter((opt) => {
    const dd = dateFromISOLocal(opt.iso);
    dd.setHours(0, 0, 0, 0);
    return dd >= today;
  });
}

function dateFromISOLocal(yyyy_mm_dd: string): Date {
  const [y, m, d] = yyyy_mm_dd.split("-").map(Number);
  return new Date(y, m - 1, d);
}

/** ===== Componente principal ===== */
export default function Turnos() {
  const toast = useToast();
  const [introVisible, setIntroVisible] = useState(true);
  const topRef = useRef<HTMLDivElement>(null);
  const confirmarButtonRef = useRef<HTMLButtonElement>(null);
  const [shouldHighlight, setShouldHighlight] = useState(false);

  // Datos maestros
  const [sucursales, setSucursales] = useState<SucursalDTO[]>([]);
  const [barberos, setBarberos] = useState<BarberoDTO[]>([]);
  const [servicios, setServicios] = useState<ServicioDTO[]>([]);

  // Selección del flujo
  const [step, setStep] = useState<number>(1);
  const [sucursalId, setSucursalId] = useState<number | null>(null);
  const [barberoId, setBarberoId] = useState<number | null>(null);
  const [servicioPrincipalId, setServicioPrincipalId] = useState<number | null>(null);

  // Datos de sesiones (una por cada sesión del servicio)
  const [sesiones, setSesiones] = useState<SesionData[]>([]);

  // Cliente
  const [senia, setSenia] = useState(false);
  const [showDatosModal, setShowDatosModal] = useState(false);

  // Calendario para sesión actual
  const [monthOffset, setMonthOffset] = useState(0);
  const [weekOpenDays, setWeekOpenDays] = useState<Set<number>>(new Set([1, 2, 3, 4, 5, 6]));
  const [horasDisponibles, setHorasDisponibles] = useState<string[]>([]);
  const [diasConHorarios, setDiasConHorarios] = useState<Set<string>>(new Set());
  const [autoAdvanced, setAutoAdvanced] = useState(false);

  // Errores
  const [err, setErr] = useState<string | null>(null);

  // ================== Computed ==================
  const servicioPrincipal = servicios.find(s => s.id === servicioPrincipalId);
  const numSesiones = servicioPrincipal?.sesiones || 1;
  const totalSteps = 3 + numSesiones; // 1:Sucursal, 2:Barbero, 3:Servicio, 4+:Sesiones

  const sucursalSeleccionada = sucursales.find(s => s.id === sucursalId);
  const barberoSeleccionado = barberos.find(b => b.id === barberoId);
  const servicesAdicionales = servicios.filter(s => s.adicional);

  // Calcular total
  const totalImporte = useMemo(() => {
    let total = servicioPrincipal?.precio || 0;
    sesiones.forEach(sesion => {
      sesion.adicionalesIds.forEach(addId => {
        const add = servicios.find(s => s.id === addId);
        if (add) total += add.precio;
      });
    });
    return total;
  }, [servicioPrincipal, sesiones, servicios]);

  const montoAPagar = senia ? Math.round(totalImporte * 0.5) : totalImporte;
  const montoRestante = totalImporte - montoAPagar;

  // Validar si todo está completo
  const todoCompleto = useMemo(() => {
    if (!sucursalId || !barberoId || !servicioPrincipalId) return false;
    // Verificar que todas las sesiones tengan fecha y hora
    for (let i = 0; i < numSesiones; i++) {
      const ses = sesiones[i];
      if (!ses || !ses.fecha || !ses.hora) return false;
    }
    return true;
  }, [sucursalId, barberoId, servicioPrincipalId, sesiones, numSesiones]);

  // ================== Effects ==================
  useEffect(() => {
    (async () => {
      try {
        setErr(null);
        const [s, servs] = await Promise.all([
          PublicApi.sucursales(),
          PublicApi.servicios()
        ]);
        setSucursales(s);
        setServicios(servs);
      } catch (e: any) {
        setErr(e?.message || "Error cargando datos.");
      }
    })();
  }, []);

  useEffect(() => {
    (async () => {
      if (!sucursalId) {
        setBarberos([]);
        return;
      }
      try {
        setBarberos(await PublicApi.barberosBySucursal(sucursalId));
      } catch (e: any) {
        setErr(e?.message || "Error cargando barberos.");
      }
    })();
  }, [sucursalId]);

  // Recargar servicios cuando cambia el barbero seleccionado
  useEffect(() => {
    (async () => {
      if (!barberoId) {
        // Si no hay barbero seleccionado, mostrar todos los servicios
        try {
          setServicios(await PublicApi.servicios());
        } catch (e: any) {
          setErr(e?.message || "Error cargando servicios.");
        }
        return;
      }
      try {
        // Filtrar servicios según el barbero seleccionado
        setServicios(await PublicApi.servicios(barberoId));
      } catch (e: any) {
        setErr(e?.message || "Error cargando servicios del barbero.");
      }
    })();
  }, [barberoId]);

  // Traer horarios del barbero
  useEffect(() => {
    (async () => {
      if (!barberoId) {
        setWeekOpenDays(new Set([1, 2, 3, 4, 5, 6]));
        return;
      }
      try {
        const res = await fetch(`${BASE}/barberos/${barberoId}/horarios-semana`);
        if (!res.ok) throw new Error();
        const arr: Array<{ diaSemana: number; inicio: string; fin: string }> = await res.json();

        const days = new Set<number>();

        for (const h of arr) {
          const n = Number(h.diaSemana);
          const dow = n === 0 ? 7 : n;
          days.add(dow);
        }

        setWeekOpenDays(days);
      } catch {
        // defaults
      }
    })();
  }, [barberoId]);

  // Inicializar sesiones cuando se selecciona servicio principal
  useEffect(() => {
    if (servicioPrincipalId) {
      const num = servicioPrincipal?.sesiones || 1;
      const newSesiones: SesionData[] = [];
      for (let i = 0; i < num; i++) {
        newSesiones.push({
          fecha: null,
          hora: null,
          adicionalesIds: []
        });
      }
      setSesiones(newSesiones);
    }
  }, [servicioPrincipalId]);

  // Cargar disponibilidad para la sesión actual
  useEffect(() => {
    (async () => {
      if (!barberoId || step < 4) {
        setHorasDisponibles([]);
        return;
      }

      const sesionIdx = step - 4;
      if (sesionIdx >= numSesiones || sesionIdx < 0) {
        setHorasDisponibles([]);
        return;
      }

      const fechaSesion = sesiones[sesionIdx]?.fecha;
      if (!fechaSesion) {
        setHorasDisponibles([]);
        return;
      }

      try {
        const res = await fetch(`${BASE}/disponibilidad?barberoId=${barberoId}&fecha=${fechaSesion}`);
        if (!res.ok) return;
        const horas: string[] = await res.json();
        setHorasDisponibles(horas);

        // Actualizar set de días con horarios
        if (horas.length > 0) {
          setDiasConHorarios(prev => new Set(prev).add(fechaSesion));
        } else {
          setDiasConHorarios(prev => {
            const newSet = new Set(prev);
            newSet.delete(fechaSesion);
            return newSet;
          });
        }
      } catch {
        setHorasDisponibles([]);
      }
    })();
  }, [barberoId, step, sesiones, numSesiones]);

  // Pre-cargar disponibilidad de días del mes
  useEffect(() => {
    (async () => {
      if (!barberoId || step < 4) return;

      const base = new Date();
      base.setDate(1);
      base.setMonth(base.getMonth() + monthOffset);
      const dias = buildMonthDays(base.getFullYear(), base.getMonth()).filter((opt) => {
        const dd = dateFromISOLocal(opt.iso).getDay();
        const dow = dd === 0 ? 7 : dd;
        return weekOpenDays.has(dow);
      });

      const nuevosDiasConHorarios = new Set<string>();

      // Consultar disponibilidad de cada día en paralelo
      await Promise.all(
        dias.map(async (d) => {
          try {
            const res = await fetch(`${BASE}/disponibilidad?barberoId=${barberoId}&fecha=${d.iso}`);
            if (res.ok) {
              const horas: string[] = await res.json();
              if (horas.length > 0) {
                nuevosDiasConHorarios.add(d.iso);
              }
            }
          } catch {
            // ignorar errores
          }
        })
      );

      setDiasConHorarios(nuevosDiasConHorarios);

      // Si el mes actual no tiene días con horarios Y no hemos avanzado automáticamente todavía, avanzar al siguiente
      if (nuevosDiasConHorarios.size === 0 && monthOffset < 12 && !autoAdvanced) {
        setMonthOffset(monthOffset + 1);
        setAutoAdvanced(true);
      }
    })();
  }, [barberoId, monthOffset, step, weekOpenDays, autoAdvanced]);

  // Resetear cuando cambia el barbero o cuando entra al step de fecha (step 4)
  useEffect(() => {
    if (step === 4) {
      setAutoAdvanced(false);
      setMonthOffset(0);
    }
  }, [barberoId, step]);

  // Auto-scroll al cambiar de paso
  useEffect(() => {
    if (topRef.current) {
      topRef.current.scrollIntoView({ behavior: 'smooth', block: 'start' });
    }
  }, [step]);

  const monthDays = useMemo(() => {
    const base = new Date();
    base.setDate(1);
    base.setMonth(base.getMonth() + monthOffset);
    return buildMonthDays(base.getFullYear(), base.getMonth()).filter((opt) => {
      const dd = dateFromISOLocal(opt.iso).getDay();
      const dow = dd === 0 ? 7 : dd;
      // Solo mostrar días laborables Y que tengan horarios disponibles
      return weekOpenDays.has(dow) && diasConHorarios.has(opt.iso);
    });
  }, [monthOffset, weekOpenDays, diasConHorarios]);

  const labelMes = useMemo(() => {
    const base = new Date();
    base.setMonth(base.getMonth() + monthOffset);
    return base.toLocaleDateString("es-AR", { month: "long", year: "numeric" });
  }, [monthOffset]);

  // ================== Handlers ==================
  const irSiguiente = () => {
    if (step < totalSteps) {
      setStep(step + 1);
    } else if (step === totalSteps) {
      // Último paso completado - hacer scroll al botón o brillar
      if (confirmarButtonRef.current) {
        const isMobile = window.innerWidth < 1024; // lg breakpoint

        if (isMobile) {
          // Mobile: scroll al botón
          confirmarButtonRef.current.scrollIntoView({
            behavior: 'smooth',
            block: 'center'
          });
        } else {
          // Desktop: hacer brillar el botón
          setShouldHighlight(true);
          setTimeout(() => setShouldHighlight(false), 2000);
        }
      }
    }
  };

  const irAnterior = () => {
    if (step > 1) {
      // Borrar datos del step actual al retroceder
      if (step === 2) {
        // Retrocede de barbero -> borrar barbero
        setBarberoId(null);
      } else if (step === 3) {
        // Retrocede de servicio -> borrar servicio y sesiones
        setServicioPrincipalId(null);
        setSesiones([]);
      } else if (step >= 4) {
        // Retrocede de alguna sesión -> borrar datos de esa sesión
        const sesionIdx = step - 4;
        if (sesionIdx >= 0 && sesionIdx < sesiones.length) {
          const newSesiones = [...sesiones];
          newSesiones[sesionIdx] = {
            fecha: null,
            hora: null,
            adicionalesIds: []
          };
          setSesiones(newSesiones);
        }
      }
      setStep(step - 1);
    }
  };

  const toggleAdicionalSesion = (sesionIdx: number, servicioId: number) => {
    const newSesiones = [...sesiones];
    const sesion = newSesiones[sesionIdx];
    if (sesion.adicionalesIds.includes(servicioId)) {
      sesion.adicionalesIds = sesion.adicionalesIds.filter(id => id !== servicioId);
    } else {
      sesion.adicionalesIds = [...sesion.adicionalesIds, servicioId];
    }
    setSesiones(newSesiones);
  };

  const selectFechaSesion = (sesionIdx: number, iso: string) => {
    const newSesiones = [...sesiones];
    newSesiones[sesionIdx].fecha = iso;
    newSesiones[sesionIdx].hora = null; // Resetear hora al cambiar fecha
    setSesiones(newSesiones);
  };

  const selectHoraSesion = (sesionIdx: number, h: string) => {
    const newSesiones = [...sesiones];
    newSesiones[sesionIdx].hora = h;
    setSesiones(newSesiones);
  };

  const confirmar = async () => {
    if (!todoCompleto) {
      toast("Completá todos los pasos antes de confirmar.");
      return;
    }
    setShowDatosModal(true);
  };

  const pagar = async (datos: ClienteForm) => {
    setShowDatosModal(false);

    if (!servicioPrincipalId || !sucursalId || !barberoId || !todoCompleto) {
      toast("Faltan datos de la reserva.");
      return;
    }

    const clean = {
      nombre: datos.nombre.trim(),
      telefono: datos.telefono,
      edad: Number(datos.edad),
    };

    if (!clean.nombre || !clean.telefono || !clean.edad) {
      toast("Completá todos tus datos.");
      return;
    }

    try {
      await ensureCsrf();
      const csrfToken = getCsrfCookie();

      // Construir payload con múltiples sesiones
      const turnosSesiones = sesiones.map((sesion) => ({
        fecha: sesion.fecha!,
        hora: sesion.hora!,
        adicionalesIds: sesion.adicionalesIds.length > 0 ? sesion.adicionalesIds : null
      }));

      const body = {
        sucursalId,
        barberoId,
        tipoCorteId: servicioPrincipalId,
        sesiones: turnosSesiones, // Array de sesiones
        clienteNombre: clean.nombre,
        clienteTelefono: clean.telefono,
        clienteEdad: clean.edad,
        senia,
      };

      const res = await fetch(`${RAW_URL}/pagos/checkout`, {
        method: "POST",
        headers: { "Content-Type": "application/json", "X-CSRF-Token": csrfToken },
        body: JSON.stringify(body),
        credentials: "include",
      });

      if (!res.ok) {
        const txt = await res.text();
        throw new Error(txt || "Error al crear la preferencia de pago.");
      }

      const data = await res.json();
      if (data.initPoint) {
        window.location.href = data.initPoint;
      } else {
        throw new Error("No se recibió el initPoint de Mercado Pago.");
      }
    } catch (e: any) {
      console.error(e);
      toast(e?.message || "Error al crear la preferencia de pago.");
    }
  };

  // ================== Render ==================
  return (
    <div className="min-h-screen bg-white">
      {introVisible && (
        <IntroSucursales onSelect={(id) => { setSucursalId(id); setStep(2); setIntroVisible(false); }} />
      )}

      <div ref={topRef} className="mx-auto max-w-7xl px-4 py-8">
        <h1 className="mb-6 text-3xl font-bold">Reservar Turno</h1>

        {err && (
          <div className="mb-4 rounded-lg border border-red-300 bg-red-50 p-3 text-red-800">
            {err}
          </div>
        )}

        {/* STEPPER */}
        <div className="mb-8">
          <div className="flex items-center justify-center gap-2 sm:gap-4">
            {Array.from({ length: totalSteps }, (_, i) => i + 1).map((s, idx) => (
              <div key={s} className="flex items-center">
                <div
                  className={`flex h-10 w-10 items-center justify-center rounded-full font-semibold transition-all ${
                    step === s
                      ? "bg-fuchsia-600 text-white ring-4 ring-fuchsia-200"
                      : step > s
                      ? "bg-fuchsia-600 text-white"
                      : "bg-slate-300 text-slate-600"
                  }`}
                >
                  {step > s ? "✓" : s}
                </div>
                {idx < totalSteps - 1 && (
                  <div
                    className={`h-1 w-8 sm:w-16 transition-all ${
                      step > s ? "bg-fuchsia-600" : "bg-slate-300"
                    }`}
                  />
                )}
              </div>
            ))}
          </div>
          <div className="mt-3 text-center text-sm text-slate-600">
            {step === 1 && "Seleccioná la sucursal"}
            {step === 2 && "Seleccioná el barbero"}
            {step === 3 && "Seleccioná el servicio"}
            {step >= 4 && step <= 3 + numSesiones && `Sesión ${step - 3} de ${numSesiones}: Fecha, hora y adicionales`}
          </div>
        </div>

        <div className="grid gap-6 lg:grid-cols-[1fr_400px]">
          {/* COLUMNA IZQUIERDA: STEP BY STEP */}
          <div className="space-y-4">
            {/* STEP 1: Sucursal */}
            {step === 1 && (
            <Section title="Seleccioná la sucursal" expanded={true}>
              <div className="grid gap-3 sm:grid-cols-2">
                {sucursales.map(s => (
                  <button
                    key={s.id}
                    className={`rounded-lg border p-4 text-left transition-all ${
                      sucursalId === s.id
                        ? "border-fuchsia-500 bg-fuchsia-50 ring-2 ring-fuchsia-500"
                        : "border-slate-300 hover:border-fuchsia-300"
                    }`}
                    onClick={() => setSucursalId(s.id)}
                  >
                    <div className="font-semibold">{s.nombre}</div>
                    <div className="text-sm text-slate-600">{s.direccion}</div>
                  </button>
                ))}
              </div>
              {sucursalId && (
                <div className="mt-6">
                  <button
                    className="w-full rounded-lg bg-fuchsia-600 px-6 py-3 font-semibold text-white shadow-lg hover:bg-fuchsia-700 transition-all"
                    onClick={irSiguiente}
                  >
                    Siguiente
                  </button>
                </div>
              )}
            </Section>
            )}

            {/* STEP 2: Barbero */}
            {step === 2 && (
              <Section title="Seleccioná el barbero" expanded={true}>
                <div className="grid gap-3 sm:grid-cols-2">
                  {barberos.map(b => (
                    <button
                      key={b.id}
                      className={`rounded-lg border p-4 text-left transition-all ${
                        barberoId === b.id
                          ? "border-fuchsia-500 bg-fuchsia-50 ring-2 ring-fuchsia-500"
                          : "border-slate-300 hover:border-fuchsia-300"
                      }`}
                      onClick={() => setBarberoId(b.id)}
                    >
                      {b.fotoUrl && (
                        <img
                          src={resolveUrl(b.fotoUrl)}
                          alt={b.nombre}
                          className="mb-2 h-20 w-20 rounded-full object-cover"
                        />
                      )}
                      <div className="font-semibold">{b.nombre}</div>
                    </button>
                  ))}
                </div>
                <div className="mt-6 flex gap-3">
                  <button
                    className="flex-1 rounded-lg border border-slate-300 px-6 py-3 font-semibold text-slate-700 hover:bg-slate-50 transition-all"
                    onClick={irAnterior}
                  >
                    Anterior
                  </button>
                  {barberoId && (
                    <button
                      className="flex-1 rounded-lg bg-fuchsia-600 px-6 py-3 font-semibold text-white shadow-lg hover:bg-fuchsia-700 transition-all"
                      onClick={irSiguiente}
                    >
                      Siguiente
                    </button>
                  )}
                </div>
              </Section>
            )}

            {/* STEP 3: Servicio Principal */}
            {step === 3 && (
              <Section title="Seleccioná el servicio" expanded={true}>
                <div className="grid gap-3">
                  {servicios.filter(s => !s.adicional).map(s => (
                    <button
                      key={s.id}
                      className={`rounded-lg border p-4 text-left transition-all ${
                        servicioPrincipalId === s.id
                          ? "border-fuchsia-500 bg-fuchsia-50 ring-2 ring-fuchsia-500"
                          : "border-slate-300 hover:border-fuchsia-300"
                      }`}
                      onClick={() => setServicioPrincipalId(s.id)}
                    >
                      <div className="flex items-start justify-between">
                        <div>
                          <div className="font-semibold">{s.nombre}</div>
                          {s.descripcion && (
                            <div className="text-sm text-slate-600">{s.descripcion}</div>
                          )}
                          {(s.sesiones || 1) > 1 && (
                            <div className="mt-1 inline-flex items-center gap-1 rounded-full bg-fuchsia-100 px-2 py-0.5 text-xs font-medium text-fuchsia-700">
                              <svg className="w-3 h-3" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M8 7V3m8 4V3m-9 8h10M5 21h14a2 2 0 002-2V7a2 2 0 00-2-2H5a2 2 0 00-2 2v12a2 2 0 002 2z" />
                              </svg>
                              {s.sesiones} sesiones
                            </div>
                          )}
                        </div>
                        <div className="text-lg font-bold text-fuchsia-600">${s.precio}</div>
                      </div>
                    </button>
                  ))}
                </div>
                <div className="mt-6 flex gap-3">
                  <button
                    className="flex-1 rounded-lg border border-slate-300 px-6 py-3 font-semibold text-slate-700 hover:bg-slate-50 transition-all"
                    onClick={irAnterior}
                  >
                    Anterior
                  </button>
                  {servicioPrincipalId && (
                    <button
                      className="flex-1 rounded-lg bg-fuchsia-600 px-6 py-3 font-semibold text-white shadow-lg hover:bg-fuchsia-700 transition-all"
                      onClick={irSiguiente}
                    >
                      Siguiente
                    </button>
                  )}
                </div>
              </Section>
            )}

            {/* STEPS 4+: Por cada sesión */}
            {step >= 4 && step <= 3 + numSesiones && (() => {
              const sesionIdx = step - 4;
              const sesion = sesiones[sesionIdx];
              if (!sesion) return null;

              return (
                <Section title={`Sesión ${sesionIdx + 1} de ${numSesiones}`} expanded={true}>
                  {/* Servicios adicionales para esta sesión */}
                  {servicesAdicionales.length > 0 && (
                    <div className="mb-6">
                      <h4 className="mb-3 text-sm font-medium text-slate-700">Servicios adicionales (opcional)</h4>
                      <div className="grid gap-2">
                        {servicesAdicionales.map(s => (
                          <button
                            key={s.id}
                            className={`rounded-lg border p-3 text-left transition-all ${
                              sesion.adicionalesIds.includes(s.id)
                                ? "border-green-500 bg-green-50 ring-2 ring-green-500"
                                : "border-slate-300 hover:border-green-300"
                            }`}
                            onClick={() => toggleAdicionalSesion(sesionIdx, s.id)}
                          >
                            <div className="flex items-start justify-between">
                              <div>
                                <div className="font-semibold text-sm">{s.nombre}</div>
                                {s.descripcion && (
                                  <div className="text-xs text-slate-600">{s.descripcion}</div>
                                )}
                              </div>
                              <div className="text-sm font-bold text-green-600">+${s.precio}</div>
                            </div>
                          </button>
                        ))}
                      </div>
                    </div>
                  )}

                  {/* Fecha y hora */}
                  <div className="space-y-4">
                    <div>
                      <h4 className="mb-2 text-sm font-medium text-slate-700">Seleccioná la fecha</h4>
                      <div className="mb-2 flex items-center justify-between">
                        <div className="text-sm font-medium text-slate-700">{labelMes}</div>
                        <div className="flex gap-2">
                          <button
                            className="rounded-lg border-2 border-slate-300 px-4 py-2 text-xl font-bold text-slate-700 hover:bg-slate-100 hover:border-fuchsia-400 transition-all disabled:opacity-40 disabled:cursor-not-allowed"
                            onClick={() => setMonthOffset(Math.max(0, monthOffset - 1))}
                            disabled={monthOffset === 0}
                            aria-label="Mes anterior"
                          >
                            ‹
                          </button>
                          <button
                            className="rounded-lg border-2 border-slate-300 px-4 py-2 text-xl font-bold text-slate-700 hover:bg-slate-100 hover:border-fuchsia-400 transition-all"
                            onClick={() => setMonthOffset(monthOffset + 1)}
                            aria-label="Mes siguiente"
                          >
                            ›
                          </button>
                        </div>
                      </div>
                      <div className="grid grid-cols-3 sm:grid-cols-4 md:grid-cols-5 gap-2">
                        {monthDays.map(d => (
                          <button
                            key={d.iso}
                            className={`rounded-lg border p-2 text-sm ${
                              sesion.fecha === d.iso
                                ? "border-fuchsia-500 bg-fuchsia-500 text-white"
                                : "border-slate-300 hover:border-fuchsia-300"
                            }`}
                            onClick={() => selectFechaSesion(sesionIdx, d.iso)}
                          >
                            {d.label}
                          </button>
                        ))}
                      </div>
                    </div>

                    {sesion.fecha && (
                      <div>
                        <h4 className="mb-2 text-sm font-medium text-slate-700">Horarios disponibles</h4>
                        <div className="grid grid-cols-4 sm:grid-cols-6 md:grid-cols-8 gap-2">
                          {horasDisponibles.map(h => (
                            <button
                              key={h}
                              className={`rounded-lg border p-2 text-sm ${
                                sesion.hora === h
                                  ? "border-fuchsia-500 bg-fuchsia-500 text-white"
                                  : "border-slate-300 hover:border-fuchsia-300"
                              }`}
                              onClick={() => selectHoraSesion(sesionIdx, h)}
                            >
                              {h}
                            </button>
                          ))}
                        </div>
                      </div>
                    )}
                  </div>

                  <div className="mt-6 flex gap-3">
                    <button
                      className="flex-1 rounded-lg border border-slate-300 px-6 py-3 font-semibold text-slate-700 hover:bg-slate-50 transition-all"
                      onClick={irAnterior}
                    >
                      Anterior
                    </button>
                    {sesion.fecha && sesion.hora && (
                      <button
                        className="flex-1 rounded-lg bg-fuchsia-600 px-6 py-3 font-semibold text-white shadow-lg hover:bg-fuchsia-700 transition-all"
                        onClick={irSiguiente}
                      >
                        {sesionIdx === numSesiones - 1 ? "Finalizar" : "Siguiente"}
                      </button>
                    )}
                  </div>
                </Section>
              );
            })()}
          </div>

          {/* COLUMNA DERECHA: RESUMEN */}
          <div className="lg:sticky lg:top-4 lg:self-start">
            <div className="rounded-xl border border-slate-300 bg-white p-6 shadow-xl">
              <h2 className="mb-4 text-xl font-bold">Resumen</h2>

              <div className="space-y-3 text-sm">
                {sucursalSeleccionada && (
                  <div>
                    <div className="font-medium text-slate-500">Sucursal:</div>
                    <div>{sucursalSeleccionada.nombre}</div>
                  </div>
                )}

                {barberoSeleccionado && (
                  <div>
                    <div className="font-medium text-slate-500">Barbero:</div>
                    <div>{barberoSeleccionado.nombre}</div>
                  </div>
                )}

                {servicioPrincipal && (
                  <div>
                    <div className="font-medium text-slate-500">Servicio:</div>
                    <div>{servicioPrincipal.nombre}</div>
                    <div className="text-fuchsia-600">${servicioPrincipal.precio}</div>
                  </div>
                )}

                {/* Mostrar resumen de cada sesión */}
                {sesiones.map((sesion, idx) => {
                  const adicionalesSesion = servicios.filter(s => sesion.adicionalesIds.includes(s.id));
                  const tieneDatos = sesion.fecha || sesion.hora || adicionalesSesion.length > 0;

                  if (!tieneDatos) return null;

                  return (
                    <div key={idx} className="border-t pt-3">
                      <div className="font-medium text-slate-500 mb-1">Sesión {idx + 1}:</div>

                      {sesion.fecha && (
                        <div className="text-xs">
                          📅 {dateFromISOLocal(sesion.fecha).toLocaleDateString("es-AR", {
                            weekday: "short",
                            day: "2-digit",
                            month: "short"
                          })}
                          {sesion.hora && ` a las ${sesion.hora} hs`}
                        </div>
                      )}

                      {adicionalesSesion.length > 0 && (
                        <div className="mt-1">
                          {adicionalesSesion.map(a => (
                            <div key={a.id} className="flex justify-between text-xs">
                              <span>+ {a.nombre}</span>
                              <span className="text-green-600">+${a.precio}</span>
                            </div>
                          ))}
                        </div>
                      )}
                    </div>
                  );
                })}
              </div>

              <div className="mt-4 border-t pt-4">
                <div className="flex justify-between text-lg font-bold mb-2">
                  <span>Total:</span>
                  <span className="text-fuchsia-600">${montoAPagar}</span>
                </div>

                {senia && (
                  <div className="mb-3 text-xs text-yellow-600 font-medium">
                    + ${montoRestante} en efectivo
                  </div>
                )}

                <div className="flex items-center justify-between p-3 rounded-lg border border-slate-200 hover:border-fuchsia-300 transition-all mb-3">
                  <label htmlFor="senia" className="text-sm font-medium cursor-pointer select-none flex-1">
                    Pagar seña del 50%
                  </label>
                  <button
                    type="button"
                    role="switch"
                    aria-checked={senia}
                    onClick={() => setSenia(!senia)}
                    className={`relative inline-flex h-6 w-11 items-center rounded-full transition-colors focus:outline-none focus:ring-2 focus:ring-fuchsia-500 focus:ring-offset-2 ${
                      senia ? 'bg-fuchsia-600' : 'bg-slate-300'
                    }`}
                  >
                    <span
                      className={`inline-block h-4 w-4 transform rounded-full bg-white shadow-lg transition-transform ${
                        senia ? 'translate-x-6' : 'translate-x-1'
                      }`}
                    />
                  </button>
                </div>
              </div>

              <button
                ref={confirmarButtonRef}
                className={`mt-6 w-full rounded-lg px-6 py-3 font-semibold text-white shadow-lg transition-all ${
                  todoCompleto
                    ? "bg-green-600 hover:bg-green-700"
                    : "bg-slate-400 cursor-not-allowed opacity-60"
                } ${shouldHighlight ? 'animate-pulse ring-4 ring-green-400' : ''}`}
                onClick={confirmar}
                disabled={!todoCompleto}
              >
                {todoCompleto ? "Confirmar y Pagar" : "Completá todos los datos"}
              </button>
            </div>
          </div>
        </div>
      </div>

      <DatosClienteModal
        open={showDatosModal}
        onCancel={() => setShowDatosModal(false)}
        onConfirm={pagar}
      />
    </div>
  );
}

// ===== Section Component =====
type SectionProps = {
  title: string;
  expanded: boolean;
  children: React.ReactNode;
};

function Section({ title, expanded, children }: SectionProps) {
  if (!expanded) return null;

  return (
    <div className="rounded-xl border border-slate-300 bg-white p-6 shadow-md">
      <h3 className="mb-4 text-lg font-semibold">{title}</h3>
      {children}
    </div>
  );
}
