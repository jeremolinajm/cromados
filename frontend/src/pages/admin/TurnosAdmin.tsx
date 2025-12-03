// src/pages/admin/TurnosAdmin.tsx
import { useEffect, useMemo, useState } from "react";
import AdminApi, { PublicApi } from "../../lib/adminApi";
import CustomSelect from "../../components/CustomSelect";

// Helper para formatear fecha AAAA-MM-DD a DD/MM/AAAA
const formatFecha = (fecha: string): string => {
  const [y, m, d] = fecha.split('-');
  return `${d}/${m}/${y}`;
};

type TurnoAdminDTO = {
  id: number;
  clienteNombre: string;
  clienteEmail?: string | null;
  clienteTelefono?: string | null;
  sucursalId: number;
  barberoId: number;
  tipoCorteId: number;
  fecha: string;
  hora: string;
  estado: string;
  pagoConfirmado?: boolean | null;
  barberoNombre?: string;
  servicioNombre?: string;
  // 🆕 Nuevos campos
  montoPagado?: number;
  senia?: boolean;
  montoEfectivo?: number;
  grupoId?: string | null; // UUID para agrupar turnos multi-sesión
  adicionales?: string; // Servicios adicionales separados por comas
};

type Page<T> = {
  content: T[];
  totalElements: number;
  totalPages: number;
  number: number;
  size: number;
};

function toIsoDate(d: Date) {
  const y = d.getFullYear();
  const m = String(d.getMonth() + 1).padStart(2, "0");
  const day = String(d.getDate()).padStart(2, "0");
  return `${y}-${m}-${day}`;
}

export default function TurnosAdmin() {
  const [page, setPage] = useState(0);
  const [size, setSize] = useState(20);
  const [sort, setSort] = useState("id,desc"); // ✅ Predeterminar por ID descendente
  const [data, setData] = useState<Page<TurnoAdminDTO>>({
    content: [],
    totalElements: 0,
    totalPages: 0,
    number: 0,
    size,
  });
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const [q, setQ] = useState("");
  const [barberoId, setBarberoId] = useState<string>("");
  const [mostrarBloqueados, setMostrarBloqueados] = useState(true);
  const [mostrarPasados, setMostrarPasados] = useState(false); // ✅ NUEVO: Toggle para mostrar pasados

  const today = new Date();
  today.setHours(0, 0, 0, 0);
  const plus365 = new Date(today); // ✅ Extendido a 1 año para cubrir servicios multi-sesión
  plus365.setDate(today.getDate() + 365);
  const minus30 = new Date(today); // ✅ NUEVO: Para mostrar históricos
  minus30.setDate(today.getDate() - 30);

  const [from, setFrom] = useState<string>(toIsoDate(today)); // ✅ Predeterminar desde hoy (vigentes)
  const [to, setTo] = useState<string>(toIsoDate(plus365));

  const [barberoName, setBarberoName] = useState<Record<number, string>>({});
  const [tipoCorteName, setTipoCorteName] = useState<Record<number, string>>({});

  // Helper para obtener badge de estado
  const getEstadoBadge = (estado: string, pagoConfirmado?: boolean | null) => {
    if (estado === "BLOQUEADO") {
      return (
        <span className="inline-flex items-center gap-1 rounded-full bg-red-100 px-2.5 py-0.5 text-xs font-medium text-red-800">
          🔒 Bloqueado
        </span>
      );
    }
    if (pagoConfirmado) {
      return (
        <span className="inline-flex items-center gap-1 rounded-full bg-green-100 px-2.5 py-0.5 text-xs font-medium text-green-800">
          ✓ Confirmado
        </span>
      );
    }
    return (
      <span className="inline-flex items-center rounded-full bg-yellow-100 px-2.5 py-0.5 text-xs font-medium text-yellow-800">
        Pendiente
      </span>
    );
  };

  // ✅ NUEVO: Actualizar rango de fechas al cambiar toggle de pasados
  useEffect(() => {
    if (mostrarPasados) {
      // Mostrar últimos 30 días + próximos 365 días
      setFrom(toIsoDate(minus30));
      setTo(toIsoDate(plus365));
    } else {
      // Solo vigentes (hoy + próximos 365 días para cubrir servicios multi-sesión)
      setFrom(toIsoDate(today));
      setTo(toIsoDate(plus365));
    }
  }, [mostrarPasados]);

  useEffect(() => {
    (async () => {
      try {
        const bs = await PublicApi.barberos();
        const bmap: Record<number, string> = {};
        (Array.isArray(bs) ? bs : []).forEach((b: any) => {
          if (b?.id != null) bmap[Number(b.id)] = String(b.nombre || `#${b.id}`);
        });
        setBarberoName(bmap);

        const ss = await PublicApi.servicios();
        const smap: Record<number, string> = {};
        (Array.isArray(ss) ? ss : []).forEach((s: any) => {
          if (s?.id != null) smap[Number(s.id)] = String(s.nombre || `#${s.id}`);
        });
        setTipoCorteName(smap);
      } catch {}
    })();
  }, []);

  const load = async (pageArg = page) => {
    setLoading(true);
    setError(null);
    try {
      const res = await AdminApi.listTurnos({
        desde: from,
        hasta: to,
        page: pageArg,
        size,
        sort, // 🔧 Añadir parámetro sort
      });
      setData(res);
      setPage(pageArg);
    } catch (e: any) {
      setError(e?.message || "Error cargando turnos");
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    load(0);
  }, [size, sort, from, to]);

  const filtered = useMemo(() => {
    const term = q.trim().toLowerCase();
    return (data.content || []).filter((t) => {
      const byText =
        !term ||
        `${t.id}`.includes(term) ||
        (t.clienteNombre || "").toLowerCase().includes(term) ||
        (t.clienteEmail || "").toLowerCase().includes(term) ||
        (t.clienteTelefono || "").toLowerCase().includes(term);

      const byBarbero = !barberoId || String(t.barberoId) === String(barberoId);
      const byFrom = !from || t.fecha >= from;
      const byTo = !to || t.fecha <= to;

      // 🆕 Filtro de bloqueados
      const byEstado = mostrarBloqueados || t.estado !== "BLOQUEADO";

      return byText && byBarbero && byFrom && byTo && byEstado;
    });
  }, [data.content, q, barberoId, from, to, mostrarBloqueados]);

  const exportCsv = async () => {
    try {
      setLoading(true);
      const first = await AdminApi.listTurnos({
        desde: from,
        hasta: to,
        page: 0,
        size: 100,
      });
      const all: TurnoAdminDTO[] = [...(first.content || [])];
      for (let p = 1; p < (first.totalPages || 0); p++) {
        const next = await AdminApi.listTurnos({
          desde: from,
          hasta: to,
          page: p,
          size: 100,
        });
        all.push(...(next.content || []));
      }

      const term = q.trim().toLowerCase();
      const filteredAll = all.filter((t) => {
        const byText =
          !term ||
          `${t.id}`.includes(term) ||
          (t.clienteNombre || "").toLowerCase().includes(term) ||
          (t.clienteEmail || "").toLowerCase().includes(term) ||
          (t.clienteTelefono || "").toLowerCase().includes(term);
        const byBarbero = !barberoId || String(t.barberoId) === String(barberoId);
        const byFrom = !from || t.fecha >= from;
        const byTo = !to || t.fecha <= to;
        const byEstado = mostrarBloqueados || t.estado !== "BLOQUEADO";
        return byText && byBarbero && byFrom && byTo && byEstado;
      });

      const header = [
        "ID",
        "Cliente",
        "Email",
        "Teléfono",
        "SucursalID",
        "Barbero",
        "Servicio",
        "Adicionales",
        "Fecha",
        "Hora",
        "Estado",
        "MontoPagado",
        "Seña",
        "MontoEfectivo"
      ];
      const rows = filteredAll.map((t) => {
        // Determinar estado unificado
        let estadoFinal = t.estado;
        if (t.estado === "BLOQUEADO") {
          estadoFinal = "BLOQUEADO";
        } else if (t.pagoConfirmado) {
          estadoFinal = "CONFIRMADO";
        } else {
          estadoFinal = "PENDIENTE";
        }

        return [
          t.id,
          safe(t.clienteNombre),
          safe(t.clienteEmail),
          safe(t.clienteTelefono),
          t.sucursalId,
          safe(t.barberoNombre ?? barberoName[t.barberoId] ?? t.barberoId),
          safe(t.servicioNombre ?? tipoCorteName[t.tipoCorteId] ?? t.tipoCorteId),
          safe(t.adicionales),
          t.fecha,
          t.hora,
          estadoFinal,
          t.montoPagado ?? 0,
          t.senia ? "SI" : "NO",
          t.montoEfectivo ?? 0
        ];
      });

      const csv = [header, ...rows]
        .map((r) =>
          r
            .map((cell) =>
              typeof cell === "string"
                ? `"${cell.replaceAll(`"`, `""`)}"`
                : String(cell)
            )
            .join(",")
        )
        .join("\n");

      const blob = new Blob([csv], { type: "text/csv;charset=utf-8" });
      const url = URL.createObjectURL(blob);
      const a = document.createElement("a");
      a.href = url;
      a.download = `turnos_${new Date().toISOString().slice(0, 19)}.csv`;
      a.click();
      URL.revokeObjectURL(url);
    } catch (e: any) {
      alert(e?.message || "Error exportando CSV");
    } finally {
      setLoading(false);
    }
  };

  const safe = (v: any) => (v == null ? "" : String(v));

  return (
    <div className="space-y-6">
      <header className="flex flex-col sm:flex-row sm:items-start sm:justify-between gap-4">
        <div className="flex-1">
          <h1 className="text-2xl lg:text-3xl font-bold bg-gradient-to-r from-slate-900 to-slate-600 bg-clip-text text-transparent">
            Gestión de Turnos
          </h1>
          <p className="text-slate-600 text-sm lg:text-base mt-1">
            Listado completo con filtros y exportación. Estados actualizados automáticamente.
          </p>
        </div>
        <div className="flex flex-wrap gap-2">
          <button
            onClick={() => load(page)}
            disabled={loading}
            className="inline-flex items-center gap-2 rounded-xl border border-slate-300 px-4 py-2.5 text-sm font-medium hover:bg-slate-50 transition-colors disabled:opacity-50"
          >
            <svg className="w-4 h-4" fill="none" viewBox="0 0 24 24" stroke="currentColor">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M4 4v5h.582m15.356 2A8.001 8.001 0 004.582 9m0 0H9m11 11v-5h-.581m0 0a8.003 8.003 0 01-15.357-2m15.357 2H15" />
            </svg>
            <span className="hidden sm:inline">Refrescar</span>
          </button>
          <button
            onClick={exportCsv}
            disabled={loading}
            className="inline-flex items-center gap-2 rounded-xl bg-gradient-to-r from-fuchsia-600 to-fuchsia-700 px-4 py-2.5 text-sm font-medium text-white hover:from-fuchsia-700 hover:to-fuchsia-800 shadow-md hover:shadow-lg transition-all disabled:opacity-50"
          >
            <svg className="w-4 h-4" fill="none" viewBox="0 0 24 24" stroke="currentColor">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 10v6m0 0l-3-3m3 3l3-3m2 8H7a2 2 0 01-2-2V5a2 2 0 012-2h5.586a1 1 0 01.707.293l5.414 5.414a1 1 0 01.293.707V19a2 2 0 01-2 2z" />
            </svg>
            Exportar CSV
          </button>
        </div>
      </header>

      {error && (
        <div className="rounded-lg border border-red-200 bg-red-50 px-4 py-2 text-red-700">
          {error}
        </div>
      )}

      {/* Filtros */}
      <div className="bg-white/80 backdrop-blur-xl rounded-2xl shadow-lg border border-slate-200/50 p-4 sm:p-6">
        <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
          <div>
            <label className="block text-sm font-medium text-slate-700 mb-2">Buscar</label>
            <input
              className="w-full rounded-lg border border-slate-300 px-3 py-2 text-slate-900 placeholder-slate-400 focus:outline-none focus:ring-2 focus:ring-fuchsia-500 transition-all"
              placeholder="Buscar por ID o cliente…"
              value={q}
              onChange={(e) => setQ(e.target.value)}
            />
          </div>
          <div>
            <label className="block text-sm font-medium text-slate-700 mb-2">Barbero</label>
            <select
              className="w-full rounded-lg border border-slate-300 px-3 py-2 text-slate-900 focus:outline-none focus:ring-2 focus:ring-fuchsia-500 transition-all"
              value={barberoId}
              onChange={(e) => setBarberoId(e.target.value)}
            >
              <option value="">Todos los barberos</option>
              {Object.entries(barberoName).map(([id, nombre]) => (
                <option key={id} value={id}>
                  {nombre} (ID: {id})
                </option>
              ))}
            </select>
          </div>
          <div>
            <label className="block text-sm font-medium text-slate-700 mb-2">Desde</label>
            <input
              type="date"
              className="w-full rounded-lg border border-slate-300 px-3 py-2 text-slate-900 focus:outline-none focus:ring-2 focus:ring-fuchsia-500 transition-all"
              value={from}
              onChange={(e) => setFrom(e.target.value)}
            />
          </div>
          <div>
            <label className="block text-sm font-medium text-slate-700 mb-2">Hasta</label>
            <input
              type="date"
              className="w-full rounded-lg border border-slate-300 px-3 py-2 text-slate-900 focus:outline-none focus:ring-2 focus:ring-fuchsia-500 transition-all"
              value={to}
              onChange={(e) => setTo(e.target.value)}
            />
          </div>
        </div>
      </div>

      {/* Toggles de filtros */}
      <div className="flex flex-col sm:flex-row gap-4">
        <div className="flex items-center gap-2">
          <input
            type="checkbox"
            id="mostrarBloqueados"
            checked={mostrarBloqueados}
            onChange={(e) => setMostrarBloqueados(e.target.checked)}
            className="rounded border-slate-300 text-fuchsia-600 focus:ring-fuchsia-500"
          />
          <label htmlFor="mostrarBloqueados" className="text-sm text-slate-600 cursor-pointer select-none">
            Mostrar turnos bloqueados desde Telegram
          </label>
        </div>

        <div className="flex items-center gap-2">
          <input
            type="checkbox"
            id="mostrarPasados"
            checked={mostrarPasados}
            onChange={(e) => setMostrarPasados(e.target.checked)}
            className="rounded border-slate-300 text-fuchsia-600 focus:ring-fuchsia-500"
          />
          <label htmlFor="mostrarPasados" className="text-sm text-slate-600 cursor-pointer select-none">
            Mostrar turnos pasados/históricos
          </label>
        </div>
      </div>

      {/* Tabla - RESPONSIVE */}
      <div className="rounded-2xl border border-slate-200 bg-white shadow-sm">
        {/* Vista móvil: cards */}
        <div className="block md:hidden">
          {loading && (
            <div className="p-4 text-slate-500 text-sm">Cargando…</div>
          )}

          {!loading && filtered.map((t) => {
            const horaDisplay = t.hora === "00:00:00" || t.hora === "00:00" ? "FH" : t.hora;

            return (
              <div key={t.id} className="border-b last:border-b-0 p-3">
                <div className="flex flex-col gap-2">
                  {/* Header con ID y estado */}
                  <div className="flex items-center justify-between gap-2">
                    <span className="font-mono text-xs font-semibold text-slate-900">#{t.id}</span>
                    {getEstadoBadge(t.estado, t.pagoConfirmado)}
                  </div>

                  {/* Fecha y Hora */}
                  <div className="flex items-center gap-3 text-sm">
                    <span className="font-medium text-slate-700">📅 {formatFecha(t.fecha)}</span>
                    {horaDisplay === "FH" ? (
                      <span className="inline-flex items-center rounded-full bg-purple-100 px-2 py-0.5 text-xs text-purple-800 font-medium">
                        FH
                      </span>
                    ) : (
                      <span className="text-slate-600">🕐 {horaDisplay}</span>
                    )}
                  </div>

                  {/* Cliente */}
                  <div className="flex items-center gap-2">
                    <span className="font-medium text-slate-900">{t.clienteNombre}</span>
                    {t.grupoId && (
                      <span
                        className="inline-flex items-center gap-1 rounded-full bg-fuchsia-100 px-2 py-0.5 text-xs font-medium text-fuchsia-700"
                        title={`Grupo multi-sesión: ${t.grupoId.substring(0, 8)}...`}
                      >
                        <svg className="w-3 h-3" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                          <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M8 7V3m8 4V3m-9 8h10M5 21h14a2 2 0 002-2V7a2 2 0 00-2-2H5a2 2 0 00-2 2v12a2 2 0 002 2z" />
                        </svg>
                        Multi
                      </span>
                    )}
                  </div>

                  {/* Teléfono */}
                  {t.clienteTelefono && (
                    <div className="text-xs text-slate-600">📞 {t.clienteTelefono}</div>
                  )}

                  {/* Barbero y Servicio */}
                  <div className="text-xs text-slate-600 space-y-1">
                    <div>💈 {t.barberoNombre || barberoName[t.barberoId] || `Barbero #${t.barberoId}`}</div>
                    <div>✂️ {t.servicioNombre || tipoCorteName[t.tipoCorteId] || `Servicio #${t.tipoCorteId}`}</div>
                    {t.adicionales && t.adicionales.trim() !== "" && (
                      <div className="text-fuchsia-600">+ {t.adicionales}</div>
                    )}
                  </div>

                  {/* Montos */}
                  <div className="flex items-center gap-4 text-xs mt-1">
                    <div>
                      <span className="text-slate-500">Monto: </span>
                      {(t.montoPagado ?? 0) > 0 ? (
                        <span className="font-semibold text-slate-900">${t.montoPagado}</span>
                      ) : t.grupoId ? (
                        <span className="text-fuchsia-600 italic">Ver principal</span>
                      ) : (
                        <span className="text-slate-400">-</span>
                      )}
                    </div>
                    <div>
                      <span className="text-slate-500">Seña: </span>
                      {t.senia ? (
                        <span className="text-orange-600 font-medium">50%</span>
                      ) : (
                        <span className="text-slate-500">No</span>
                      )}
                    </div>
                    <div>
                      <span className="text-slate-500">Efectivo: </span>
                      {(t.montoEfectivo ?? 0) > 0 ? (
                        <span className="font-semibold text-slate-900">${t.montoEfectivo}</span>
                      ) : (
                        <span className="text-slate-400">-</span>
                      )}
                    </div>
                  </div>
                </div>
              </div>
            );
          })}

          {!loading && filtered.length === 0 && (
            <div className="p-8 text-center">
              <svg className="w-12 h-12 text-slate-300 mx-auto mb-3" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M9 12h6m-6 4h6m2 5H7a2 2 0 01-2-2V5a2 2 0 012-2h5.586a1 1 0 01.707.293l5.414 5.414a1 1 0 01.293.707V19a2 2 0 01-2 2z" />
              </svg>
              <p className="text-sm text-slate-500 mb-1">Sin resultados</p>
              <p className="text-xs text-slate-400">No se encontraron turnos con los filtros aplicados</p>
            </div>
          )}
        </div>

        {/* Vista desktop: tabla */}
        <div className="hidden md:block overflow-x-auto">
          <table className="min-w-full text-sm">
            <thead className="bg-slate-50 text-slate-700">
              <tr>
                <th className="px-3 py-3 text-left whitespace-nowrap text-xs font-semibold uppercase tracking-wider">#</th>
                <th className="px-3 py-3 text-left whitespace-nowrap text-xs font-semibold uppercase tracking-wider">Fecha</th>
                <th className="px-3 py-3 text-left whitespace-nowrap text-xs font-semibold uppercase tracking-wider">Hora</th>
                <th className="px-3 py-3 text-left whitespace-nowrap text-xs font-semibold uppercase tracking-wider">Cliente</th>
                <th className="px-3 py-3 text-left whitespace-nowrap text-xs font-semibold uppercase tracking-wider">Teléfono</th>
                <th className="px-3 py-3 text-left whitespace-nowrap text-xs font-semibold uppercase tracking-wider">Barbero</th>
                <th className="px-3 py-3 text-left whitespace-nowrap text-xs font-semibold uppercase tracking-wider">Servicio</th>
                <th className="px-3 py-3 text-left whitespace-nowrap text-xs font-semibold uppercase tracking-wider">Adicionales</th>
                <th className="px-3 py-3 text-left whitespace-nowrap text-xs font-semibold uppercase tracking-wider">Estado</th>
                <th className="px-3 py-3 text-left whitespace-nowrap text-xs font-semibold uppercase tracking-wider">Monto</th>
                <th className="px-3 py-3 text-left whitespace-nowrap text-xs font-semibold uppercase tracking-wider">Seña</th>
                <th className="px-3 py-3 text-left whitespace-nowrap text-xs font-semibold uppercase tracking-wider">Efectivo</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-100">
              {filtered.map((t) => {
                // Mostrar "FH" si la hora es 00:00:00 (fuera de horario)
                const horaDisplay = t.hora === "00:00:00" || t.hora === "00:00" ? "FH" : t.hora;

                return (
                <tr key={t.id} className="hover:bg-slate-50 transition-colors">
                  <td className="px-3 py-3">
                    <span className="font-mono text-xs font-semibold text-slate-900">#{t.id}</span>
                  </td>
                  <td className="px-3 py-3 whitespace-nowrap text-slate-700">{formatFecha(t.fecha)}</td>
                  <td className="px-3 py-3 whitespace-nowrap">
                    {horaDisplay === "FH" ? (
                      <span className="inline-flex items-center rounded-full bg-purple-100 px-2 py-1 text-xs text-purple-800 font-medium">
                        FH
                      </span>
                    ) : (
                      <span className="text-slate-700">{horaDisplay}</span>
                    )}
                  </td>
                  <td className="px-3 py-3">
                    <div className="flex items-center gap-2">
                      <div className="font-medium text-slate-900">{t.clienteNombre}</div>
                      {t.grupoId && (
                        <span
                          className="inline-flex items-center gap-1 rounded-full bg-fuchsia-100 px-2 py-0.5 text-xs font-medium text-fuchsia-700"
                          title={`Grupo multi-sesión: ${t.grupoId.substring(0, 8)}...`}
                        >
                          <svg className="w-3 h-3" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M8 7V3m8 4V3m-9 8h10M5 21h14a2 2 0 002-2V7a2 2 0 00-2-2H5a2 2 0 00-2 2v12a2 2 0 002 2z" />
                          </svg>
                          Multi
                        </span>
                      )}
                    </div>
                  </td>
                  <td className="px-3 py-3 whitespace-nowrap text-slate-700">{t.clienteTelefono || "-"}</td>
                  <td className="px-3 py-3 text-slate-700">{t.barberoNombre || barberoName[t.barberoId] || t.barberoId}</td>
                  <td className="px-3 py-3 text-slate-700">{t.servicioNombre || tipoCorteName[t.tipoCorteId] || t.tipoCorteId}</td>
                  <td className="px-3 py-3 text-slate-700">
                    {t.adicionales && t.adicionales.trim() !== "" ? (
                      <span className="text-fuchsia-600 text-xs">+ {t.adicionales}</span>
                    ) : (
                      <span className="text-slate-400">-</span>
                    )}
                  </td>
                  <td className="px-3 py-3 whitespace-nowrap">
                    {getEstadoBadge(t.estado, t.pagoConfirmado)}
                  </td>
                  <td className="px-3 py-3 whitespace-nowrap">
                    {(t.montoPagado ?? 0) > 0 ? (
                      <span className="font-semibold text-slate-900">${t.montoPagado}</span>
                    ) : t.grupoId ? (
                      <span className="text-xs text-fuchsia-600 italic">Ver turno principal</span>
                    ) : (
                      <span className="text-slate-400">-</span>
                    )}
                  </td>
                  <td className="px-3 py-3 whitespace-nowrap">
                    {t.senia ? (
                      <span className="text-orange-600 font-medium">Sí (50%)</span>
                    ) : t.grupoId && (t.montoPagado ?? 0) === 0 ? (
                      <span className="text-xs text-slate-400 italic">-</span>
                    ) : (
                      <span className="text-slate-500">No</span>
                    )}
                  </td>
                  <td className="px-3 py-3 whitespace-nowrap">
                    {(t.montoEfectivo ?? 0) > 0 ? (
                      <span className="font-semibold text-slate-900">${t.montoEfectivo}</span>
                    ) : t.grupoId && (t.montoPagado ?? 0) === 0 ? (
                      <span className="text-xs text-fuchsia-600 italic">Ver turno principal</span>
                    ) : (
                      <span className="text-slate-400">-</span>
                    )}
                  </td>
                </tr>
                );
              })}
              {filtered.length === 0 && (
                <tr>
                  <td colSpan={12} className="px-4 py-8 text-slate-500 text-center">
                    <div className="flex flex-col items-center gap-2">
                      <svg className="w-12 h-12 text-slate-300" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                        <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M9 12h6m-6 4h6m2 5H7a2 2 0 01-2-2V5a2 2 0 012-2h5.586a1 1 0 01.707.293l5.414 5.414a1 1 0 01.293.707V19a2 2 0 01-2 2z" />
                      </svg>
                      <p className="font-medium">Sin resultados</p>
                      <p className="text-sm text-slate-400">No se encontraron turnos con los filtros aplicados</p>
                    </div>
                  </td>
                </tr>
              )}
            </tbody>
          </table>
        </div>
      </div>

      {/* Paginación - RESPONSIVE */}
      <div className="flex flex-col sm:flex-row items-center justify-between gap-4">
        <div className="flex items-center gap-2 flex-wrap">
          <span className="text-sm text-slate-600">
            Pág. {data.number + 1} de {Math.max(1, data.totalPages || 1)}
          </span>
          <CustomSelect
            className="w-32"
            value={String(size)}
            onChange={(value) => setSize(Number(value))}
            dropup={true}
            options={[
              { value: "10", label: "10 por pág." },
              { value: "20", label: "20 por pág." },
              { value: "50", label: "50 por pág." },
              { value: "100", label: "100 por pág." }
            ]}
          />
          <CustomSelect
            className="w-28"
            value={sort}
            onChange={(value) => setSort(value)}
            dropup={true}
            options={[
              { value: "fecha,desc", label: "Fecha ↓" },
              { value: "fecha,asc", label: "Fecha ↑" },
              { value: "id,desc", label: "ID ↓" },
              { value: "id,asc", label: "ID ↑" }
            ]}
          />
        </div>
        <div className="flex gap-2">
          <button
            className="rounded-lg border border-slate-300 px-3 py-2 text-sm hover:bg-slate-50 disabled:opacity-40 disabled:cursor-not-allowed transition-colors"
            onClick={() => load(page - 1)}
            disabled={page <= 0 || loading}
          >
            ← Anterior
          </button>
          <button
            className="rounded-lg border border-slate-300 px-3 py-2 text-sm hover:bg-slate-50 disabled:opacity-40 disabled:cursor-not-allowed transition-colors"
            onClick={() => load(page + 1)}
            disabled={page + 1 >= (data.totalPages || 1) || loading}
          >
            Siguiente →
          </button>
        </div>
      </div>
    </div>
  );
}