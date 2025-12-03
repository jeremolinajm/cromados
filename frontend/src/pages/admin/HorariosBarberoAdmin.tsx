import { useEffect, useMemo, useState } from "react";
import { useNavigate, useSearchParams } from "react-router-dom";
import { AdminApi, type BarberoDTO, type Page } from "../../lib/adminApi";
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
    </div>
  );
}
