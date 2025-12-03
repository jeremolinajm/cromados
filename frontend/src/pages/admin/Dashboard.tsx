// src/pages/admin/Dashboard.tsx
import { useEffect, useState } from "react";
import { Link, useNavigate } from "react-router-dom";
import { AdminApi, PublicApi } from "../../lib/adminApi";

type TurnoAdminDTO = {
  id: number;
  clienteNombre: string;
  clienteEmail?: string | null;
  clienteTelefono?: string | null;
  sucursalId: number;
  barberoId: number;
  tipoCorteId: number;
  fecha: string; // yyyy-MM-dd
  hora: string;  // HH:mm
  estado: "PENDIENTE" | "RESERVADO" | "CONFIRMADO" | "CANCELADO" | "BLOQUEADO" | string;
  pagoConfirmado?: boolean | null;
  barberoNombre?: string;
  servicioNombre?: string;
  montoPagado?: number;
  montoEfectivo?: number;
};

export default function Dashboard() {
  const navigate = useNavigate();

  const [totalSucursales, setTotalSucursales] = useState(0);
  const [totalBarberos, setTotalBarberos] = useState(0);
  const [turnosVigentes, setTurnosVigentes] = useState(0);
  const [turnosRecientes, setTurnosRecientes] = useState<TurnoAdminDTO[]>([]);
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);
  const [barberoName, setBarberoName] = useState<Record<number, string>>({});
  const [tipoCorteName, setTipoCorteName] = useState<Record<number, string>>({});

  useEffect(() => {
    let mounted = true;
    (async () => {
      setLoading(true);
      setError(null);
      try {
        const [suc, barPage, turnos, countVigentes, barberos, servicios] = await Promise.all([
          AdminApi.sucursales().catch(() => []),
          AdminApi.listBarberos(0, 1).catch(() => ({ content: [], totalElements: 0 } as any)),
          AdminApi.listUltimosTurnos(10).catch(() => [] as TurnoAdminDTO[]),
          AdminApi.countTurnosVigentes().catch(() => 0),
          PublicApi.barberos().catch(() => []),
          PublicApi.servicios().catch(() => []),
        ]);

        if (!mounted) return;
        setTotalSucursales(Array.isArray(suc) ? suc.length : 0);

        const totalB =
          typeof (barPage as any)?.totalElements === "number"
            ? (barPage as any).totalElements
            : Array.isArray((barPage as any)?.content)
            ? (barPage as any).content.length
            : 0;
        setTotalBarberos(totalB);
        setTurnosVigentes(countVigentes);

        // Mapear barberos
        const bmap: Record<number, string> = {};
        (Array.isArray(barberos) ? barberos : []).forEach((b: any) => {
          if (b?.id != null) bmap[Number(b.id)] = String(b.nombre || `#${b.id}`);
        });
        setBarberoName(bmap);

        // Mapear servicios
        const smap: Record<number, string> = {};
        (Array.isArray(servicios) ? servicios : []).forEach((s: any) => {
          if (s?.id != null) smap[Number(s.id)] = String(s.nombre || `#${s.id}`);
        });
        setTipoCorteName(smap);

        setTurnosRecientes(Array.isArray(turnos) ? turnos : []);
      } catch (e: any) {
        if (!mounted) return;
        setError(e?.message || "Error cargando dashboard");
      } finally {
        if (mounted) setLoading(false);
      }
    })();
    return () => {
      mounted = false;
    };
  }, []);

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

  return (
    <div className="space-y-6 lg:space-y-8">
      {/* Header - Responsive */}
      <header className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4">
        <div>
          <h1 className="text-2xl lg:text-3xl font-bold bg-gradient-to-r from-slate-900 to-slate-600 bg-clip-text text-transparent">
            Panel de Administración
          </h1>
          <p className="text-slate-600 text-sm lg:text-base mt-1">Resumen general y accesos rápidos</p>
        </div>
        <Link
          to="/"
          className="inline-flex items-center justify-center rounded-xl border border-slate-300 px-4 py-2 text-sm font-medium hover:bg-slate-50 transition-colors"
        >
          <svg className="w-4 h-4 mr-2" fill="none" viewBox="0 0 24 24" stroke="currentColor">
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M10 19l-7-7m0 0l7-7m-7 7h18" />
          </svg>
          Ver sitio
        </Link>
      </header>

      {/* Accesos rápidos - Responsive */}
      <section className="grid gap-3 sm:grid-cols-2 lg:grid-cols-4">
        <button
          onClick={() => navigate("/admin/barberos?new=1")}
          className="group rounded-2xl border border-slate-200 p-5 text-left hover:border-fuchsia-300 hover:shadow-md transition-all bg-white"
        >
          <div className="flex items-center justify-between mb-3">
            <div className="w-10 h-10 rounded-xl bg-fuchsia-100 flex items-center justify-center group-hover:bg-fuchsia-200 transition-colors">
              <svg className="w-5 h-5 text-fuchsia-600" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M16 7a4 4 0 11-8 0 4 4 0 018 0zM12 14a7 7 0 00-7 7h14a7 7 0 00-7-7z" />
              </svg>
            </div>
            <span className="text-xs font-medium text-fuchsia-600 bg-fuchsia-50 px-2 py-1 rounded-full">Acceso rápido</span>
          </div>
          <div className="text-lg font-semibold text-slate-900 mb-1">Nuevo barbero</div>
          <div className="text-slate-500 text-sm">Agregar un nuevo barbero al sistema</div>
        </button>

        <button
          onClick={() => navigate("/admin/servicios?new=1")}
          className="group rounded-2xl border border-slate-200 p-5 text-left hover:border-purple-300 hover:shadow-md transition-all bg-white"
        >
          <div className="flex items-center justify-between mb-3">
            <div className="w-10 h-10 rounded-xl bg-purple-100 flex items-center justify-center group-hover:bg-purple-200 transition-colors">
              <svg className="w-5 h-5 text-purple-600" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M14.121 14.121L19 19m-7-7l7-7m-7 7l-2.879 2.879M12 12L9.121 9.121m0 5.758a3 3 0 10-4.243 4.243 3 3 0 004.243-4.243zm0-5.758a3 3 0 10-4.243-4.243 3 3 0 004.243 4.243z" />
              </svg>
            </div>
            <span className="text-xs font-medium text-purple-600 bg-purple-50 px-2 py-1 rounded-full">Acceso rápido</span>
          </div>
          <div className="text-lg font-semibold text-slate-900 mb-1">Nuevo servicio</div>
          <div className="text-slate-500 text-sm">Crear un tipo de corte o servicio</div>
        </button>

        <button
          onClick={() => navigate("/admin/sucursales?new=1")}
          className="group rounded-2xl border border-slate-200 p-5 text-left hover:border-blue-300 hover:shadow-md transition-all bg-white"
        >
          <div className="flex items-center justify-between mb-3">
            <div className="w-10 h-10 rounded-xl bg-blue-100 flex items-center justify-center group-hover:bg-blue-200 transition-colors">
              <svg className="w-5 h-5 text-blue-600" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M19 21V5a2 2 0 00-2-2H7a2 2 0 00-2 2v16m14 0h2m-2 0h-5m-9 0H3m2 0h5M9 7h1m-1 4h1m4-4h1m-1 4h1m-5 10v-5a1 1 0 011-1h2a1 1 0 011 1v5m-4 0h4" />
              </svg>
            </div>
            <span className="text-xs font-medium text-blue-600 bg-blue-50 px-2 py-1 rounded-full">Acceso rápido</span>
          </div>
          <div className="text-lg font-semibold text-slate-900 mb-1">Nueva sucursal</div>
          <div className="text-slate-500 text-sm">Registrar una nueva ubicación</div>
        </button>

        <button
          onClick={() => navigate("/admin/calculadora")}
          className="group rounded-2xl border border-slate-200 p-5 text-left hover:border-emerald-300 hover:shadow-md transition-all bg-white sm:col-span-2 lg:col-span-1"
        >
          <div className="flex items-center justify-between mb-3">
            <div className="w-10 h-10 rounded-xl bg-emerald-100 flex items-center justify-center group-hover:bg-emerald-200 transition-colors">
              <svg className="w-5 h-5 text-emerald-600" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M9 7h6m0 10v-3m-3 3h.01M9 17h.01M9 14h.01M12 14h.01M15 11h.01M12 11h.01M9 11h.01M7 21h10a2 2 0 002-2V5a2 2 0 00-2-2H7a2 2 0 00-2 2v14a2 2 0 002 2z" />
              </svg>
            </div>
            <span className="text-xs font-medium text-emerald-600 bg-emerald-50 px-2 py-1 rounded-full">Herramienta</span>
          </div>
          <div className="text-lg font-semibold text-slate-900 mb-1">Calculadora de Pagos</div>
          <div className="text-slate-500 text-sm">Calcular pagos semanales a barberos</div>
        </button>
      </section>

      {/* Métricas - Responsive */}
      <section className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
        <div className="rounded-2xl border border-slate-200 p-6 bg-gradient-to-br from-white to-slate-50 hover:shadow-md transition-shadow">
          <div className="flex items-center justify-between mb-4">
            <div className="text-slate-500 text-sm font-medium">Sucursales</div>
            <div className="w-10 h-10 rounded-xl bg-blue-100 flex items-center justify-center">
              <svg className="w-5 h-5 text-blue-600" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M19 21V5a2 2 0 00-2-2H7a2 2 0 00-2 2v16m14 0h2m-2 0h-5m-9 0H3m2 0h5M9 7h1m-1 4h1m4-4h1m-1 4h1m-5 10v-5a1 1 0 011-1h2a1 1 0 011 1v5m-4 0h4" />
              </svg>
            </div>
          </div>
          <div className="text-3xl lg:text-4xl font-bold text-slate-900 mb-3">{totalSucursales}</div>
          <Link to="/admin/sucursales" className="inline-flex items-center text-blue-600 text-sm font-medium hover:text-blue-700 transition-colors">
            Ver sucursales
            <svg className="w-4 h-4 ml-1" fill="none" viewBox="0 0 24 24" stroke="currentColor">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M9 5l7 7-7 7" />
            </svg>
          </Link>
        </div>

        <div className="rounded-2xl border border-slate-200 p-6 bg-gradient-to-br from-white to-slate-50 hover:shadow-md transition-shadow">
          <div className="flex items-center justify-between mb-4">
            <div className="text-slate-500 text-sm font-medium">Barberos</div>
            <div className="w-10 h-10 rounded-xl bg-fuchsia-100 flex items-center justify-center">
              <svg className="w-5 h-5 text-fuchsia-600" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M17 20h5v-2a3 3 0 00-5.356-1.857M17 20H7m10 0v-2c0-.656-.126-1.283-.356-1.857M7 20H2v-2a3 3 0 015.356-1.857M7 20v-2c0-.656.126-1.283.356-1.857m0 0a5.002 5.002 0 019.288 0M15 7a3 3 0 11-6 0 3 3 0 016 0zm6 3a2 2 0 11-4 0 2 2 0 014 0zM7 10a2 2 0 11-4 0 2 2 0 014 0z" />
              </svg>
            </div>
          </div>
          <div className="text-3xl lg:text-4xl font-bold text-slate-900 mb-3">{totalBarberos}</div>
          <Link to="/admin/barberos" className="inline-flex items-center text-fuchsia-600 text-sm font-medium hover:text-fuchsia-700 transition-colors">
            Ver barberos
            <svg className="w-4 h-4 ml-1" fill="none" viewBox="0 0 24 24" stroke="currentColor">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M9 5l7 7-7 7" />
            </svg>
          </Link>
        </div>

        <div className="rounded-2xl border border-slate-200 p-6 bg-gradient-to-br from-white to-slate-50 hover:shadow-md transition-shadow sm:col-span-2 lg:col-span-1">
          <div className="flex items-center justify-between mb-4">
            <div className="text-slate-500 text-sm font-medium">Turnos vigentes</div>
            <div className="w-10 h-10 rounded-xl bg-green-100 flex items-center justify-center">
              <svg className="w-5 h-5 text-green-600" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M8 7V3m8 4V3m-9 8h10M5 21h14a2 2 0 002-2V7a2 2 0 00-2-2H5a2 2 0 00-2 2v12a2 2 0 002 2z" />
              </svg>
            </div>
          </div>
          <div className="text-3xl lg:text-4xl font-bold text-slate-900 mb-3">
            {turnosVigentes}
          </div>
          <Link to="/admin/turnos" className="inline-flex items-center text-green-600 text-sm font-medium hover:text-green-700 transition-colors">
            Ver todos los turnos
            <svg className="w-4 h-4 ml-1" fill="none" viewBox="0 0 24 24" stroke="currentColor">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M9 5l7 7-7 7" />
            </svg>
          </Link>
        </div>
      </section>

      {/* Últimos turnos - Mejorado y Responsive */}
      <section className="rounded-2xl border border-slate-200 bg-white shadow-sm overflow-hidden">
        <div className="px-4 sm:px-6 py-4 border-b bg-gradient-to-r from-slate-50 to-white flex items-center justify-between">
          <div>
            <h2 className="text-lg font-semibold text-slate-900">Últimos 10 Turnos</h2>
            <p className="text-sm text-slate-500 mt-0.5">Turnos confirmados y bloqueados más recientes (ordenados por ID)</p>
          </div>
          <Link
            to="/admin/turnos"
            className="hidden sm:inline-flex items-center text-sm font-medium text-fuchsia-600 hover:text-fuchsia-700"
          >
            Ver todos
            <svg className="w-4 h-4 ml-1" fill="none" viewBox="0 0 24 24" stroke="currentColor">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M9 5l7 7-7 7" />
            </svg>
          </Link>
        </div>

        {error && (
          <div className="px-4 sm:px-6 py-3 bg-red-50 border-b border-red-200">
            <p className="text-sm text-red-700">{error}</p>
          </div>
        )}

        {loading && (
          <div className="px-4 sm:px-6 py-12 text-center">
            <div className="inline-flex items-center justify-center w-12 h-12 rounded-full bg-slate-100 mb-3">
              <svg className="animate-spin w-6 h-6 text-slate-600" fill="none" viewBox="0 0 24 24">
                <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4"></circle>
                <path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4zm2 5.291A7.962 7.962 0 014 12H0c0 3.042 1.135 5.824 3 7.938l3-2.647z"></path>
              </svg>
            </div>
            <p className="text-slate-500">Cargando turnos...</p>
          </div>
        )}

        {!loading && Array.isArray(turnosRecientes) && turnosRecientes.length === 0 && (
          <div className="px-4 sm:px-6 py-12 text-center">
            <div className="inline-flex items-center justify-center w-12 h-12 rounded-full bg-slate-100 mb-3">
              <svg className="w-6 h-6 text-slate-400" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M8 7V3m8 4V3m-9 8h10M5 21h14a2 2 0 002-2V7a2 2 0 00-2-2H5a2 2 0 00-2 2v12a2 2 0 002 2z" />
              </svg>
            </div>
            <p className="text-slate-500">No hay turnos recientes</p>
          </div>
        )}

        {/* Tabla desktop - hidden en mobile */}
        {!loading && turnosRecientes.length > 0 && (
          <div className="hidden lg:block overflow-x-auto">
            <table className="min-w-full text-sm">
              <thead className="bg-slate-50 border-b border-slate-200">
                <tr>
                  <th className="px-4 py-3 text-left text-xs font-semibold text-slate-600 uppercase tracking-wider">ID</th>
                  <th className="px-4 py-3 text-left text-xs font-semibold text-slate-600 uppercase tracking-wider">Cliente</th>
                  <th className="px-4 py-3 text-left text-xs font-semibold text-slate-600 uppercase tracking-wider">Barbero</th>
                  <th className="px-4 py-3 text-left text-xs font-semibold text-slate-600 uppercase tracking-wider">Servicio</th>
                  <th className="px-4 py-3 text-left text-xs font-semibold text-slate-600 uppercase tracking-wider">Fecha</th>
                  <th className="px-4 py-3 text-left text-xs font-semibold text-slate-600 uppercase tracking-wider">Hora</th>
                  <th className="px-4 py-3 text-left text-xs font-semibold text-slate-600 uppercase tracking-wider">Monto</th>
                  <th className="px-4 py-3 text-left text-xs font-semibold text-slate-600 uppercase tracking-wider">Estado</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-100">
                {turnosRecientes.map((t) => {
                  const horaDisplay = t.hora === "00:00:00" || t.hora === "00:00" ? "FH" : t.hora.slice(0, 5);
                  const monto = t.montoPagado || t.montoEfectivo || 0;

                  return (
                    <tr key={t.id} className="hover:bg-slate-50 transition-colors">
                      <td className="px-4 py-3">
                        <span className="font-mono text-xs font-semibold text-slate-900">#{t.id}</span>
                      </td>
                      <td className="px-4 py-3">
                        <div className="font-medium text-slate-900">{t.clienteNombre}</div>
                        {t.clienteTelefono && (
                          <div className="text-xs text-slate-500">{t.clienteTelefono}</div>
                        )}
                      </td>
                      <td className="px-4 py-3 text-slate-700">
                        {t.barberoNombre || barberoName[t.barberoId] || `#${t.barberoId}`}
                      </td>
                      <td className="px-4 py-3 text-slate-700">
                        {t.servicioNombre || tipoCorteName[t.tipoCorteId] || `#${t.tipoCorteId}`}
                      </td>
                      <td className="px-4 py-3 whitespace-nowrap text-slate-700">
                        {new Date(t.fecha).toLocaleDateString('es-AR', { day: '2-digit', month: '2-digit' })}
                      </td>
                      <td className="px-4 py-3 whitespace-nowrap">
                        {horaDisplay === "FH" ? (
                          <span className="inline-flex items-center rounded-full bg-purple-100 px-2 py-1 text-xs font-medium text-purple-800">
                            FH
                          </span>
                        ) : (
                          <span className="text-slate-700">{horaDisplay}</span>
                        )}
                      </td>
                      <td className="px-4 py-3 whitespace-nowrap">
                        {monto > 0 ? (
                          <span className="font-semibold text-slate-900">${monto}</span>
                        ) : (
                          <span className="text-slate-400">-</span>
                        )}
                      </td>
                      <td className="px-4 py-3 whitespace-nowrap">
                        {getEstadoBadge(t.estado, t.pagoConfirmado)}
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
        )}

        {/* Cards mobile/tablet */}
        {!loading && turnosRecientes.length > 0 && (
          <div className="lg:hidden divide-y divide-slate-100">
            {turnosRecientes.map((t) => {
              const horaDisplay = t.hora === "00:00:00" || t.hora === "00:00" ? "FH" : t.hora.slice(0, 5);
              const monto = t.montoPagado || t.montoEfectivo || 0;

              return (
                <div key={t.id} className="p-4 hover:bg-slate-50 transition-colors">
                  <div className="flex items-start justify-between mb-3">
                    <div className="flex-1">
                      <div className="flex items-center gap-2 mb-1">
                        <span className="font-mono text-xs font-semibold text-slate-500">#{t.id}</span>
                        {getEstadoBadge(t.estado, t.pagoConfirmado)}
                      </div>
                      <div className="font-semibold text-slate-900 mb-1">{t.clienteNombre}</div>
                      {t.clienteTelefono && (
                        <div className="text-sm text-slate-500">{t.clienteTelefono}</div>
                      )}
                    </div>
                    {monto > 0 && (
                      <div className="text-right">
                        <div className="text-lg font-bold text-slate-900">${monto}</div>
                        <div className="text-xs text-slate-500">Monto</div>
                      </div>
                    )}
                  </div>

                  <div className="grid grid-cols-2 gap-3 text-sm">
                    <div>
                      <div className="text-xs text-slate-500 mb-1">Barbero</div>
                      <div className="font-medium text-slate-700">
                        {t.barberoNombre || barberoName[t.barberoId] || `#${t.barberoId}`}
                      </div>
                    </div>
                    <div>
                      <div className="text-xs text-slate-500 mb-1">Servicio</div>
                      <div className="font-medium text-slate-700">
                        {t.servicioNombre || tipoCorteName[t.tipoCorteId] || `#${t.tipoCorteId}`}
                      </div>
                    </div>
                    <div>
                      <div className="text-xs text-slate-500 mb-1">Fecha</div>
                      <div className="font-medium text-slate-700">
                        {new Date(t.fecha).toLocaleDateString('es-AR', {
                          day: '2-digit',
                          month: 'short',
                          year: 'numeric'
                        })}
                      </div>
                    </div>
                    <div>
                      <div className="text-xs text-slate-500 mb-1">Hora</div>
                      <div className="font-medium text-slate-700">
                        {horaDisplay === "FH" ? (
                          <span className="inline-flex items-center rounded-full bg-purple-100 px-2 py-0.5 text-xs font-medium text-purple-800">
                            FH
                          </span>
                        ) : (
                          horaDisplay
                        )}
                      </div>
                    </div>
                  </div>
                </div>
              );
            })}
          </div>
        )}

        {/* Link móvil */}
        {!loading && turnosRecientes.length > 0 && (
          <div className="lg:hidden border-t border-slate-200 bg-slate-50">
            <Link
              to="/admin/turnos"
              className="flex items-center justify-center px-4 py-3 text-sm font-medium text-fuchsia-600 hover:text-fuchsia-700"
            >
              Ver todos los turnos
              <svg className="w-4 h-4 ml-1" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M9 5l7 7-7 7" />
              </svg>
            </Link>
          </div>
        )}
      </section>
    </div>
  );
}
