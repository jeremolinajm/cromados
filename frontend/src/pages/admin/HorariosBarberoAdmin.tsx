import { useEffect, useMemo, useState } from "react";
import { useNavigate, useSearchParams } from "react-router-dom";
import { AdminApi, type BarberoDTO, type Page, type DiaExcepcionalBarberoDTO } from "../../lib/adminApi";
import { useToast } from "../../components/Toast";
import { RAW_URL } from "../../lib/adminApi";
import CustomSelect from "../../components/CustomSelect";
import { ensureCsrf, getCsrfCookie } from "../../lib/authApi";


type Horario = { diaSemana: number; inicio: string; fin: string };

const DIAS: { n: number; label: string }[] = [
  { n: 1, label: "Lunes" },
  { n: 2, label: "Martes" },
  { n: 3, label: "Miércoles" },
  { n: 4, label: "Jueves" },
  { n: 5, label: "Viernes" },
  { n: 6, label: "Sábado" },
  { n: 7, label: "Domingo" },
];

// HH:mm cada 30 minutos
const TIMES_30 = (() => {
  const out: string[] = [];
  for (let h = 0; h < 24; h++) {
    for (let m = 0; m < 60; m += 30) {
      out.push(`${String(h).padStart(2, "0")}:${String(m).padStart(2, "0")}`);
    }
  }
  return out;
})();

// Opciones para CustomSelect
const TIME_OPTIONS = TIMES_30.map((t) => ({ value: t, label: t }));

export default function HorariosBarberoAdmin() {
  const toast = useToast();
  const navigate = useNavigate();
  const [searchParams, setSearchParams] = useSearchParams();
  const initialId = Number(searchParams.get("barberoId") || 0) || null;
  
  const [dirty, setDirty] = useState<Record<number, boolean>>({
  1:false, 2:false, 3:false, 4:false, 5:false, 6:false, 7:false
  });

  const [barberos, setBarberos] = useState<BarberoDTO[]>([]);
  const [barberoId, setBarberoId] = useState<number | null>(initialId);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  // Mapa de horarios en edición (por día)
  const [rows, setRows] = useState<Record<number, Horario | null>>({
    1: null, 2: null, 3: null, 4: null, 5: null, 6: null, 7: null,
  });

  const [rowsExtra, setRowsExtra] = useState<Record<number, Horario | null>>({
  1: null, 2: null, 3: null, 4: null, 5: null, 6: null, 7: null,
});

  // Estado para días excepcionales
  const [diasExcepcionales, setDiasExcepcionales] = useState<DiaExcepcionalBarberoDTO[]>([]);
  const [nuevoDiaExcepcional, setNuevoDiaExcepcional] = useState({
    fecha: "",
    inicio1: "09:00",
    fin1: "13:00",
    inicio2: "",
    fin2: "",
  });
  const [editandoFecha, setEditandoFecha] = useState<string | null>(null);

  const selected = useMemo(
    () => barberos.find((b) => Number(b.id) === Number(barberoId)) || null,
    [barberos, barberoId]
  );

  useEffect(() => {
    (async () => {
      try {
        setLoading(true);
        // Obtener CSRF token primero
        await ensureCsrf(RAW_URL);

        const page = (await AdminApi.listBarberos(0, 100, "nombre,asc")) as Page<BarberoDTO>;
        const list = Array.isArray(page?.content) ? page.content : [];
        setBarberos(list);
        setError(null);
      } catch (e: any) {
        setError(e?.message || "Error cargando barberos");
        toast("No se pudieron cargar barberos.");
      } finally {
        setLoading(false);
      }
    })();
  }, [toast]);

  useEffect(() => {
    (async () => {
      setSearchParams((prev) => {
        const sp = new URLSearchParams(prev);
        if (barberoId) sp.set("barberoId", String(barberoId));
        else sp.delete("barberoId");
        return sp;
      });

      if (!barberoId) {
        setRows({ 1: null,2: null,3: null,4: null,5: null,6: null,7: null });
        return;
      }

      try {
        setLoading(true);
        const hs = await AdminApi.horariosDeBarbero(barberoId);
        // normalizo por día y ordeno por inicio para decidir T1/T2
        const m1: Record<number, Horario | null> = { 1:null,2:null,3:null,4:null,5:null,6:null,7:null };
        const m2: Record<number, Horario | null> = { 1:null,2:null,3:null,4:null,5:null,6:null,7:null };

        (hs || [])
          .sort((a: any, b: any) => String(a.inicio).localeCompare(String(b.inicio)))
          .forEach((h: any) => {
            const d = Number(h.diaSemana);
            if (!m1[d]) m1[d] = { diaSemana: d, inicio: h.inicio, fin: h.fin };
            else if (!m2[d]) m2[d] = { diaSemana: d, inicio: h.inicio, fin: h.fin };
            // si vinieran más de 2, las ignoramos visualmente
          });

        setRows(m1);
        setRowsExtra(m2);

        // Cargar días excepcionales
        const excepcionales = await AdminApi.listDiasExcepcionales(barberoId);
        setDiasExcepcionales(excepcionales || []);

        setError(null);
      } catch (e: any) {
        setError(e?.message || "Error cargando horarios");
        toast("No se pudieron cargar horarios.");
      } finally {
        setLoading(false);
      }
    })();
  }, [barberoId, setSearchParams, toast]);

  const setValue = (dia: number, field: "inicio" | "fin", v: string) => {
    setRows((prev) => {
      const cur = prev[dia];
      const base: Horario = cur ? { ...cur } : { diaSemana: dia, inicio: "09:00", fin: "18:00" };
      (base as any)[field] = v;
      return { ...prev, [dia]: base };
    });
    setDirty((d) => ({ ...d, [dia]: true }));
  };

  const setValueExtra = (dia: number, field: "inicio" | "fin", v: string) => {
    setRowsExtra((prev) => {
      const cur = prev[dia];
      const base: Horario = cur ? { ...cur } : { diaSemana: dia, inicio: "14:00", fin: "20:00" };
      (base as any)[field] = v;
      return { ...prev, [dia]: base };
    });
    setDirty((d) => ({ ...d, [dia]: true }));
  };


  const cerrarDia = async (dia: number) => {
    if (!barberoId) return;
    try {
      setLoading(true);
      // Asegurar token CSRF fresco antes de DELETE
      await ensureCsrf(RAW_URL);
      await AdminApi.deleteHorarioBarbero(barberoId, dia);
      setRowsExtra((r) => ({ ...r, [dia]: null }));
      setRows((r) => ({ ...r, [dia]: null }));
      toast("Día cerrado.");
      setDirty((d) => ({ ...d, [dia]: false }));
    } catch (e: any) {
      toast(e?.message || "Error cerrando día.");
    } finally {
      setLoading(false);
    }
  };

  const guardarDia = async (dia: number) => {
    if (!barberoId) return;
    const h1 = rows[dia];      // franja 1 (mañana/única)
    const h2 = rowsExtra[dia]; // franja 2 (tarde, opcional)

    let h1m = h1;
    let h2m = h2;

    // Si el usuario solo cargó T2, traemos T1 del server para no borrarlo sin querer
    if ((!h1m || !h1m.inicio || !h1m.fin) && (h2m?.inicio && h2m?.fin)) {
      const hsServer = await AdminApi.horariosDeBarbero(barberoId);
      const delDia = (hsServer || [])
        .filter((h: any) => Number(h.diaSemana) === dia)
        .sort((a: any, b: any) => String(a.inicio).localeCompare(String(b.inicio)));
      if (delDia[0]) {
        h1m = { diaSemana: dia, inicio: delDia[0].inicio, fin: delDia[0].fin };
      }
    }

    // (opcionalmente también preservamos T2 si ya existía y el usuario solo tocó T1)
    if ((h1m?.inicio && h1m?.fin) && (!h2m || !h2m.inicio || !h2m.fin)) {
      const hsServer = await AdminApi.horariosDeBarbero(barberoId);
      const delDia = (hsServer || [])
        .filter((h: any) => Number(h.diaSemana) === dia)
        .sort((a: any, b: any) => String(a.inicio).localeCompare(String(b.inicio)));
      if (delDia[1]) {
        h2m = { diaSemana: dia, inicio: delDia[1].inicio, fin: delDia[1].fin };
      }
    }

    const payload: Record<string, string> = {};
    if (h1m?.inicio && h1m?.fin) {
      payload.inicio1 = h1m.inicio;
      payload.fin1    = h1m.fin;
      payload.inicio  = h1m.inicio; // compat
      payload.fin     = h1m.fin;    // compat
    }
    if (h2m?.inicio && h2m?.fin) {
      payload.inicio2 = h2m.inicio;
      payload.fin2    = h2m.fin;
    }

    // ✅ UN solo PUT que el backend ahora entiende (T1+T2)
    // Asegurar que tengamos un token CSRF fresco
    await ensureCsrf(RAW_URL);
    const csrf = getCsrfCookie();

    const res = await fetch(`${RAW_URL}/admin/horarios-barbero/${barberoId}/${dia}`, {
      method: "PUT",
      credentials: 'include', // ⬅️ Importante para enviar cookies JWT
      headers: {
        "Content-Type": "application/json",
        ...(csrf ? { "X-CSRF-Token": csrf } : {})
      },
      body: JSON.stringify(payload),
    });

    if (!res.ok) {
      const msg = await res.text();
      // Si es 409, mostramos el mensaje del server y recargamos el día
      if (res.status === 409) {
        try {
          const json = JSON.parse(msg);
          toast(json?.message || "No se pudo guardar por registros relacionados.");
        } catch {
          toast("No se pudo guardar por registros relacionados.");
        }
        // recarga la data del día para revertir lo que quedó en UI
        try {
          const hs = await AdminApi.horariosDeBarbero(barberoId);
          const m1: Record<number, Horario | null> = { 1:null,2:null,3:null,4:null,5:null,6:null,7:null };
          const m2: Record<number, Horario | null> = { 1:null,2:null,3:null,4:null,5:null,6:null,7:null };
          (hs || [])
            .sort((a: any, b: any) => String(a.inicio).localeCompare(String(b.inicio)))
            .forEach((h: any) => {
              const dnum = Number(h.diaSemana);
              if (!m1[dnum]) m1[dnum] = { diaSemana: dnum, inicio: h.inicio, fin: h.fin };
              else if (!m2[dnum]) m2[dnum] = { diaSemana: dnum, inicio: h.inicio, fin: h.fin };
            });
          setRows(m1);
          setRowsExtra(m2);
          setDirty((d) => ({ ...d, [dia]: false }));
        } catch {
          // silencioso
        }
        return;
      }

      // otros errores
      throw new Error(msg);
    }

    toast("Horario guardado.");

    // refrescar desde server para ver lo realmente persistido
    try {
      const hs = await AdminApi.horariosDeBarbero(barberoId);
      const m1: Record<number, Horario | null> = {1:null,2:null,3:null,4:null,5:null,6:null,7:null};
      const m2: Record<number, Horario | null> = {1:null,2:null,3:null,4:null,5:null,6:null,7:null};
      (hs || [])
        .sort((a: any, b: any) => String(a.inicio).localeCompare(String(b.inicio)))
        .forEach((h: any) => {
          const dnum = Number(h.diaSemana);
          if (!m1[dnum]) m1[dnum] = { diaSemana: dnum, inicio: h.inicio, fin: h.fin };
          else if (!m2[dnum]) m2[dnum] = { diaSemana: dnum, inicio: h.inicio, fin: h.fin };
        });
      setRows(m1);
      setRowsExtra(m2);
    } catch {/* silencioso */}

    setDirty((d) => ({ ...d, [dia]: false }));
  };

  const cargarDiaExcepcionalParaEditar = (fecha: string, franjas: DiaExcepcionalBarberoDTO[]) => {
    const franjasOrdenadas = franjas.sort((a, b) => a.inicio.localeCompare(b.inicio));
    const t1 = franjasOrdenadas[0];
    const t2 = franjasOrdenadas[1];

    setNuevoDiaExcepcional({
      fecha: fecha,
      inicio1: t1?.inicio || "09:00",
      fin1: t1?.fin || "13:00",
      inicio2: t2?.inicio || "",
      fin2: t2?.fin || "",
    });
    setEditandoFecha(fecha);

    // Scroll al formulario
    window.scrollTo({ top: document.body.scrollHeight, behavior: 'smooth' });
  };

  const agregarDiaExcepcional = async () => {
    if (!barberoId) return;

    const { fecha, inicio1, fin1, inicio2, fin2 } = nuevoDiaExcepcional;

    if (!fecha || !inicio1 || !fin1) {
      toast("Completá fecha, inicio y fin de T1");
      return;
    }

    try {
      setLoading(true);
      await ensureCsrf(RAW_URL);

      // Si estamos editando, primero eliminamos las franjas existentes
      if (editandoFecha) {
        const franjasAEliminar = diasExcepcionales.filter(d => d.fecha === editandoFecha);
        for (const franja of franjasAEliminar) {
          if (franja.id) {
            await AdminApi.eliminarDiaExcepcional(barberoId, franja.id);
          }
        }
      }

      // Agregar T1
      await AdminApi.agregarDiaExcepcional(barberoId, fecha, inicio1, fin1);

      // Agregar T2 si está completo
      if (inicio2 && fin2) {
        await AdminApi.agregarDiaExcepcional(barberoId, fecha, inicio2, fin2);
      }

      // Recargar lista
      const excepcionales = await AdminApi.listDiasExcepcionales(barberoId);
      setDiasExcepcionales(excepcionales || []);

      // Limpiar formulario
      setNuevoDiaExcepcional({
        fecha: "",
        inicio1: "09:00",
        fin1: "13:00",
        inicio2: "",
        fin2: "",
      });
      setEditandoFecha(null);

      toast(editandoFecha ? "Día excepcional actualizado." : "Día excepcional agregado.");
    } catch (e: any) {
      toast(e?.message || "Error guardando día excepcional.");
    } finally {
      setLoading(false);
    }
  };

  const cancelarEdicion = () => {
    setNuevoDiaExcepcional({
      fecha: "",
      inicio1: "09:00",
      fin1: "13:00",
      inicio2: "",
      fin2: "",
    });
    setEditandoFecha(null);
  };

  const eliminarDiaExcepcional = async (id: number) => {
    if (!barberoId) return;

    try {
      setLoading(true);
      await ensureCsrf(RAW_URL);
      await AdminApi.eliminarDiaExcepcional(barberoId, id);

      // Recargar lista
      const excepcionales = await AdminApi.listDiasExcepcionales(barberoId);
      setDiasExcepcionales(excepcionales || []);

      toast("Día excepcional eliminado.");
    } catch (e: any) {
      toast(e?.message || "Error eliminando día excepcional.");
    } finally {
      setLoading(false);
    }
  };

  // Agrupar días excepcionales por fecha
  const diasExcepcionalesAgrupados = useMemo(() => {
    const grupos: Record<string, DiaExcepcionalBarberoDTO[]> = {};
    diasExcepcionales.forEach((dia) => {
      if (!grupos[dia.fecha]) grupos[dia.fecha] = [];
      grupos[dia.fecha].push(dia);
    });
    return grupos;
  }, [diasExcepcionales]);

  return (
    <div className="space-y-6">
      {/* Header */}
      <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4">
        <div>
          <h1 className="text-2xl lg:text-3xl font-bold bg-gradient-to-r from-slate-900 to-slate-600 bg-clip-text text-transparent">
            Horarios de Barberos
          </h1>
          {selected && (
            <p className="text-sm text-slate-600 mt-1">
              Editando: <span className="font-semibold text-fuchsia-600">{selected.nombre}</span>
            </p>
          )}
        </div>
        <button
          className="inline-flex items-center justify-center gap-2 rounded-xl border border-slate-300 px-4 py-2 text-sm font-medium hover:bg-slate-50 transition-colors"
          onClick={() => navigate("/admin/barberos")}
        >
          <svg className="w-4 h-4" fill="none" viewBox="0 0 24 24" stroke="currentColor">
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M10 19l-7-7m0 0l7-7m-7 7h18" />
          </svg>
          Volver
        </button>
      </div>

      {/* Selector de barbero */}
      <div className="bg-white/80 backdrop-blur-xl rounded-2xl shadow-lg border border-slate-200/50 p-6">
        <label className="block text-sm font-medium text-slate-700 mb-3">Barbero</label>
        <CustomSelect
          value={barberoId ? String(barberoId) : ""}
          onChange={(value) => setBarberoId(value ? Number(value) : null)}
          options={barberos.map((b) => ({ value: String(b.id), label: b.nombre }))}
          placeholder="Elegí un barbero…"
        />
      </div>

      {error && (
        <div className="rounded-xl border border-red-300 bg-red-50 px-4 py-3 text-red-700 text-sm flex items-center gap-2">
          <svg className="w-5 h-5 flex-shrink-0" fill="currentColor" viewBox="0 0 20 20">
            <path fillRule="evenodd" d="M10 18a8 8 0 100-16 8 8 0 000 16zM8.707 7.293a1 1 0 00-1.414 1.414L8.586 10l-1.293 1.293a1 1 0 101.414 1.414L10 11.414l1.293 1.293a1 1 0 001.414-1.414L11.414 10l1.293-1.293a1 1 0 00-1.414-1.414L10 8.586 8.707 7.293z" clipRule="evenodd" />
          </svg>
          {error}
        </div>
      )}

      {loading && (
        <div className="bg-white/80 backdrop-blur-xl rounded-2xl shadow-lg border border-slate-200/50 p-12 text-center">
          <div className="inline-flex items-center justify-center w-12 h-12 rounded-full bg-fuchsia-100 mb-3">
            <svg className="animate-spin w-6 h-6 text-fuchsia-600" fill="none" viewBox="0 0 24 24">
              <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4"></circle>
              <path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4zm2 5.291A7.962 7.962 0 014 12H0c0 3.042 1.135 5.824 3 7.938l3-2.647z"></path>
            </svg>
          </div>
          <p className="text-slate-600">Cargando horarios…</p>
        </div>
      )}

      {!!barberoId && !loading && (
        <div className="bg-white/80 backdrop-blur-xl rounded-2xl shadow-lg border border-slate-200/50">
          <div className="px-4 sm:px-6 py-4 bg-gradient-to-r from-slate-50 to-white border-b border-slate-200 rounded-t-2xl">
            <h2 className="text-base sm:text-lg font-semibold text-slate-900">Configuración de Horarios</h2>
            <p className="text-xs sm:text-sm text-slate-500 mt-0.5">Definí las franjas horarias para cada día de la semana</p>
          </div>
          <div className="overflow-x-auto">
            <table className="w-full text-sm min-w-[800px]">
              <thead>
                <tr className="bg-gradient-to-r from-slate-50 to-white border-b border-slate-200">
                  <th className="px-2 sm:px-4 py-3 text-left text-xs font-semibold text-slate-600 uppercase tracking-wider whitespace-nowrap">Día</th>
                  <th className="px-2 sm:px-4 py-3 text-left text-xs font-semibold text-slate-600 uppercase tracking-wider whitespace-nowrap">T1 - Inicio</th>
                  <th className="px-2 sm:px-4 py-3 text-left text-xs font-semibold text-slate-600 uppercase tracking-wider whitespace-nowrap">T1 - Fin</th>
                  <th className="px-2 sm:px-4 py-3 text-left text-xs font-semibold text-slate-600 uppercase tracking-wider whitespace-nowrap">T2 - Inicio</th>
                  <th className="px-2 sm:px-4 py-3 text-left text-xs font-semibold text-slate-600 uppercase tracking-wider whitespace-nowrap">T2 - Fin</th>
                  <th className="px-2 sm:px-4 py-3 text-right text-xs font-semibold text-slate-600 uppercase tracking-wider whitespace-nowrap">Acciones</th>
                </tr>
              </thead>
              <tbody>
                {DIAS.map((d) => {
                  const h = rows[d.n];
                  const cerrado = !h;
                  return (
                    <tr key={d.n} className="border-b border-slate-100 hover:bg-slate-50/50 transition-colors">
                      <td className="px-2 sm:px-4 py-2 sm:py-3">
                        <span className="font-medium text-slate-900 text-xs sm:text-sm whitespace-nowrap">{d.label}</span>
                      </td>

                      {/* T1 Inicio */}
                      <td className="px-2 sm:px-4 py-2 sm:py-3 relative">
                        {cerrado ? (
                          <div className="text-slate-400 text-center text-xs">--:--</div>
                        ) : (
                          <CustomSelect
                            className="w-20 sm:w-full"
                            value={h?.inicio || ""}
                            onChange={(value) => setValue(d.n, "inicio", value)}
                            options={TIME_OPTIONS}
                            placeholder="--:--"
                          />
                        )}
                      </td>

                      {/* T1 Fin */}
                      <td className="px-2 sm:px-4 py-2 sm:py-3 relative">
                        {cerrado ? (
                          <div className="text-slate-400 text-center text-xs">--:--</div>
                        ) : (
                          <CustomSelect
                            className="w-20 sm:w-full"
                            value={h?.fin || ""}
                            onChange={(value) => setValue(d.n, "fin", value)}
                            options={TIME_OPTIONS}
                            placeholder="--:--"
                          />
                        )}
                      </td>

                      {/* T2 Inicio */}
                      <td className="px-2 sm:px-4 py-2 sm:py-3 relative">
                        {cerrado ? (
                          <div className="text-slate-400 text-center text-xs">--:--</div>
                        ) : (
                          <CustomSelect
                            className="w-20 sm:w-full"
                            value={rowsExtra[d.n]?.inicio || ""}
                            onChange={(value) => setValueExtra(d.n, "inicio", value)}
                            options={TIME_OPTIONS}
                            placeholder="--:--"
                          />
                        )}
                      </td>

                      {/* T2 Fin */}
                      <td className="px-2 sm:px-4 py-2 sm:py-3 relative">
                        {cerrado ? (
                          <div className="text-slate-400 text-center text-xs">--:--</div>
                        ) : (
                          <CustomSelect
                            className="w-20 sm:w-full"
                            value={rowsExtra[d.n]?.fin || ""}
                            onChange={(value) => setValueExtra(d.n, "fin", value)}
                            options={TIME_OPTIONS}
                            placeholder="--:--"
                          />
                        )}
                      </td>

                      {/* Acciones */}
                      <td className="px-2 sm:px-4 py-2 sm:py-3">
                        <div className="flex items-center justify-end gap-1 sm:gap-2">
                          {cerrado ? (
                            <button
                              className="inline-flex items-center gap-1 rounded-lg border border-green-300 bg-green-50 px-2 py-1.5 text-xs font-medium text-green-700 hover:bg-green-100 transition-colors whitespace-nowrap"
                              onClick={() => {
                                setRows((r) => ({ ...r, [d.n]: { diaSemana: d.n, inicio: "09:00", fin: "12:00" } }))
                                setRowsExtra((r) => ({ ...r, [d.n]: null }));
                                setDirty((dmap) => ({ ...dmap, [d.n]: true }));
                              }}
                            >
                              <svg className="w-3 h-3 sm:w-4 sm:h-4" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 4v16m8-8H4" />
                              </svg>
                              <span className="hidden sm:inline">Abrir</span>
                            </button>
                          ) : (
                            <>
                              {dirty[d.n] && (
                                <button
                                  className="inline-flex items-center gap-1 rounded-lg bg-gradient-to-r from-fuchsia-600 to-fuchsia-700 px-2 py-1.5 text-xs font-medium text-white hover:from-fuchsia-700 hover:to-fuchsia-800 shadow-md hover:shadow-lg transition-all whitespace-nowrap"
                                  onClick={() => guardarDia(d.n)}
                                >
                                  <svg className="w-3 h-3 sm:w-4 sm:h-4" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                                    <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M5 13l4 4L19 7" />
                                  </svg>
                                  <span className="hidden sm:inline">Guardar</span>
                                </button>
                              )}
                              <button
                                className="inline-flex items-center gap-1 rounded-lg border border-red-300 bg-red-50 px-2 py-1.5 text-xs font-medium text-red-700 hover:bg-red-100 transition-colors whitespace-nowrap"
                                onClick={() => cerrarDia(d.n)}
                                disabled={loading}
                              >
                                <svg className="w-3 h-3 sm:w-4 sm:h-4" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M6 18L18 6M6 6l12 12" />
                                </svg>
                                <span className="hidden sm:inline">Cerrar</span>
                              </button>
                            </>
                          )}
                        </div>
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>

          {loading && <div className="mt-3 text-slate-500 text-sm">Guardando…</div>}
        </div>
      )}

      {/* Sección de días excepcionales */}
      {!!barberoId && !loading && (
        <div className="bg-white/80 backdrop-blur-xl rounded-2xl shadow-lg border border-slate-200/50">
          <div className="px-4 sm:px-6 py-4 bg-gradient-to-r from-amber-50 to-white border-b border-amber-200 rounded-t-2xl">
            <h2 className="text-base sm:text-lg font-semibold text-slate-900">Días Excepcionales</h2>
            <p className="text-xs sm:text-sm text-slate-500 mt-0.5">
              Días específicos que no siguen el horario regular (ej: festivos, eventos especiales)
            </p>
          </div>

          <div className="p-4 sm:p-6 space-y-6">
            {/* Formulario para agregar/editar */}
            <div className="bg-gradient-to-r from-amber-50/50 to-white p-4 rounded-xl border border-amber-200">
              <div className="flex items-center justify-between mb-3">
                <h3 className="text-sm font-semibold text-slate-900">
                  {editandoFecha ? "Editar día excepcional" : "Agregar día excepcional"}
                </h3>
                {editandoFecha && (
                  <button
                    className="text-xs text-slate-600 hover:text-slate-900 underline"
                    onClick={cancelarEdicion}
                  >
                    Cancelar edición
                  </button>
                )}
              </div>
              <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-5 gap-3">
                <div>
                  <label className="block text-xs font-medium text-slate-700 mb-1">Fecha</label>
                  <input
                    type="date"
                    className="w-full px-3 py-2 text-sm border border-slate-300 rounded-lg focus:ring-2 focus:ring-fuchsia-500 focus:border-transparent disabled:bg-slate-100 disabled:cursor-not-allowed"
                    value={nuevoDiaExcepcional.fecha}
                    onChange={(e) => setNuevoDiaExcepcional({ ...nuevoDiaExcepcional, fecha: e.target.value })}
                    min={new Date().toISOString().split('T')[0]}
                    disabled={!!editandoFecha}
                  />
                </div>
                <div>
                  <label className="block text-xs font-medium text-slate-700 mb-1">T1 Inicio</label>
                  <CustomSelect
                    value={nuevoDiaExcepcional.inicio1}
                    onChange={(value) => setNuevoDiaExcepcional({ ...nuevoDiaExcepcional, inicio1: value })}
                    options={TIME_OPTIONS}
                  />
                </div>
                <div>
                  <label className="block text-xs font-medium text-slate-700 mb-1">T1 Fin</label>
                  <CustomSelect
                    value={nuevoDiaExcepcional.fin1}
                    onChange={(value) => setNuevoDiaExcepcional({ ...nuevoDiaExcepcional, fin1: value })}
                    options={TIME_OPTIONS}
                  />
                </div>
                <div>
                  <label className="block text-xs font-medium text-slate-700 mb-1">T2 Inicio</label>
                  <CustomSelect
                    value={nuevoDiaExcepcional.inicio2}
                    onChange={(value) => setNuevoDiaExcepcional({ ...nuevoDiaExcepcional, inicio2: value })}
                    options={TIME_OPTIONS}
                    placeholder="Opcional"
                  />
                </div>
                <div>
                  <label className="block text-xs font-medium text-slate-700 mb-1">T2 Fin</label>
                  <CustomSelect
                    value={nuevoDiaExcepcional.fin2}
                    onChange={(value) => setNuevoDiaExcepcional({ ...nuevoDiaExcepcional, fin2: value })}
                    options={TIME_OPTIONS}
                    placeholder="Opcional"
                  />
                </div>
              </div>
              <button
                className="mt-3 inline-flex items-center gap-2 rounded-lg bg-gradient-to-r from-fuchsia-600 to-fuchsia-700 px-4 py-2 text-sm font-medium text-white hover:from-fuchsia-700 hover:to-fuchsia-800 shadow-md hover:shadow-lg transition-all disabled:opacity-50 disabled:cursor-not-allowed"
                onClick={agregarDiaExcepcional}
                disabled={loading || !nuevoDiaExcepcional.fecha}
              >
                <svg className="w-4 h-4" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                  {editandoFecha ? (
                    <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M5 13l4 4L19 7" />
                  ) : (
                    <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 4v16m8-8H4" />
                  )}
                </svg>
                {editandoFecha ? "Guardar cambios" : "Agregar"}
              </button>
            </div>

            {/* Lista de días excepcionales */}
            {Object.keys(diasExcepcionalesAgrupados).length === 0 ? (
              <div className="text-center py-8 text-slate-500 text-sm">
                No hay días excepcionales configurados
              </div>
            ) : (
              <div className="space-y-2">
                {Object.entries(diasExcepcionalesAgrupados)
                  .sort(([a], [b]) => a.localeCompare(b))
                  .map(([fecha, franjas]) => {
                    const fechaObj = new Date(fecha + 'T00:00:00');
                    const diaNombre = fechaObj.toLocaleDateString('es-AR', { weekday: 'long', day: '2-digit', month: '2-digit', year: 'numeric' });

                    return (
                      <div key={fecha} className="flex items-center justify-between p-3 bg-white rounded-lg border border-slate-200 hover:border-fuchsia-300 transition-colors">
                        <div className="flex items-start gap-3">
                          <div className="flex-shrink-0 w-10 h-10 rounded-lg bg-gradient-to-br from-fuchsia-500 to-fuchsia-600 flex items-center justify-center">
                            <svg className="w-5 h-5 text-white" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M8 7V3m8 4V3m-9 8h10M5 21h14a2 2 0 002-2V7a2 2 0 00-2-2H5a2 2 0 00-2 2v12a2 2 0 002 2z" />
                            </svg>
                          </div>
                          <div>
                            <div className="font-semibold text-slate-900 capitalize text-sm">{diaNombre}</div>
                            <div className="text-xs text-slate-600 mt-1 space-x-2">
                              {franjas
                                .sort((a, b) => a.inicio.localeCompare(b.inicio))
                                .map((franja, idx) => (
                                  <span key={franja.id} className="inline-flex items-center gap-1">
                                    {idx > 0 && <span className="text-slate-400">|</span>}
                                    <span className="font-mono">{franja.inicio} - {franja.fin}</span>
                                  </span>
                                ))}
                            </div>
                          </div>
                        </div>
                        <div className="flex items-center gap-2">
                          <button
                            className="flex-shrink-0 inline-flex items-center gap-1 rounded-lg border border-blue-300 bg-blue-50 px-3 py-1.5 text-xs font-medium text-blue-700 hover:bg-blue-100 transition-colors"
                            onClick={() => cargarDiaExcepcionalParaEditar(fecha, franjas)}
                            disabled={loading}
                          >
                            <svg className="w-4 h-4" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M11 5H6a2 2 0 00-2 2v11a2 2 0 002 2h11a2 2 0 002-2v-5m-1.414-9.414a2 2 0 112.828 2.828L11.828 15H9v-2.828l8.586-8.586z" />
                            </svg>
                            Editar
                          </button>
                          <button
                            className="flex-shrink-0 inline-flex items-center gap-1 rounded-lg border border-red-300 bg-red-50 px-3 py-1.5 text-xs font-medium text-red-700 hover:bg-red-100 transition-colors"
                            onClick={() => {
                              // Eliminar todas las franjas de esta fecha
                              franjas.forEach((franja) => {
                                if (franja.id) eliminarDiaExcepcional(franja.id);
                              });
                            }}
                            disabled={loading}
                          >
                            <svg className="w-4 h-4" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M19 7l-.867 12.142A2 2 0 0116.138 21H7.862a2 2 0 01-1.995-1.858L5 7m5 4v6m4-6v6m1-10V4a1 1 0 00-1-1h-4a1 1 0 00-1 1v3M4 7h16" />
                            </svg>
                            Eliminar
                          </button>
                        </div>
                      </div>
                    );
                  })}
              </div>
            )}
          </div>
        </div>
      )}
    </div>
  );
}
