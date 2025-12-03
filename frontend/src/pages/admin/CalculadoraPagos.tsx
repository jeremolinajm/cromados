import { useState } from "react";
import AdminApi from "../../lib/adminApi";

type PagoBarbero = {
  barberoId: number;
  barberoNombre: string;
  cantidadTurnos: number;

  // MONTOS BRUTOS (informativos - cómo pagaron los clientes)
  montoAppBruto: number;           // Total que entró por Mercado Pago
  montoTransferenciaBruto: number; // Total que entró por transferencia al alias del negocio
  montoEfectivoBruto: number;      // Total que entró en efectivo a caja
  totalBruto: number;              // Suma de todos los montos brutos

  // CÁLCULO PARA EL BARBERO
  comision50: number;              // 50% del total bruto (lo que le corresponde)
  cantidadBonos: number;           // Cantidad de bonos ganados (1 por cada 10 turnos/día)
  montoBonus: number;              // Monto del bonus (cantidadBonos * 50% precio id=1)
  totalAPagar: number;             // comision50 + montoBonus

  detalleServicios: DetalleServicio[];
  bonosPorDia: Record<string, number>; // Detalle de bonos por día (para mostrar en UI)
};

type DetalleServicio = {
  servicioNombre: string;
  cantidad: number;
  precioUnitario: number;
  subtotal: number;
};

export default function CalculadoraPagos() {
  const [desde, setDesde] = useState("");
  const [hasta, setHasta] = useState("");
  const [pagos, setPagos] = useState<PagoBarbero[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  // Calcular semana actual (lunes a domingo)
  const setSemanActual = () => {
    const hoy = new Date();
    const diaSemana = hoy.getDay(); // 0=domingo, 1=lunes, ..., 6=sábado

    // Calcular lunes de esta semana
    const lunes = new Date(hoy);
    const diffToMonday = diaSemana === 0 ? -6 : 1 - diaSemana; // Si es domingo, retroceder 6 días
    lunes.setDate(hoy.getDate() + diffToMonday);

    // Calcular domingo de esta semana
    const domingo = new Date(lunes);
    domingo.setDate(lunes.getDate() + 6);

    setDesde(lunes.toISOString().split('T')[0]);
    setHasta(domingo.toISOString().split('T')[0]);
  };

  // Calcular semana pasada
  const setSemanaAnterior = () => {
    const hoy = new Date();
    const diaSemana = hoy.getDay();

    // Calcular lunes de esta semana
    const lunesEstaSemana = new Date(hoy);
    const diffToMonday = diaSemana === 0 ? -6 : 1 - diaSemana;
    lunesEstaSemana.setDate(hoy.getDate() + diffToMonday);

    // Retroceder 7 días para lunes de semana pasada
    const lunesPasado = new Date(lunesEstaSemana);
    lunesPasado.setDate(lunesEstaSemana.getDate() - 7);

    // Domingo de semana pasada
    const domingoPasado = new Date(lunesPasado);
    domingoPasado.setDate(lunesPasado.getDate() + 6);

    setDesde(lunesPasado.toISOString().split('T')[0]);
    setHasta(domingoPasado.toISOString().split('T')[0]);
  };

  // Calcular solo hoy
  const setHoy = () => {
    const hoy = new Date();
    const fechaHoy = hoy.toISOString().split('T')[0];
    setDesde(fechaHoy);
    setHasta(fechaHoy);
  };

  const calcular = async () => {
    if (!desde || !hasta) {
      setError("Debe seleccionar ambas fechas");
      return;
    }

    setLoading(true);
    setError(null);
    setPagos([]);

    try {
      const resultado = await AdminApi.calcularPagosBarberos(desde, hasta);
      setPagos(resultado);

      if (resultado.length === 0) {
        setError("No se encontraron turnos confirmados en el rango seleccionado");
      }
    } catch (e: any) {
      setError(e?.message || "Error calculando pagos");
    } finally {
      setLoading(false);
    }
  };

  const formatCurrency = (value: number) => {
    return new Intl.NumberFormat('es-AR', {
      style: 'currency',
      currency: 'ARS',
      minimumFractionDigits: 0
    }).format(value);
  };

  const totalGeneral = pagos.reduce((sum, p) => sum + p.totalAPagar, 0);

  return (
    <div className="space-y-6">
      <header>
        <h1 className="text-2xl font-semibold">Calculadora de Pagos</h1>
        <p className="text-slate-600">Calcula cuánto pagar a cada barbero por período.</p>
      </header>

      {/* Selector de fechas */}
      <div className="rounded-2xl border border-slate-200 bg-white p-6 shadow-sm">
        <div className="grid md:grid-cols-5 gap-4 items-end">
          <div>
            <label className="block text-sm font-medium mb-1">Desde</label>
            <input
              type="date"
              value={desde}
              onChange={(e) => setDesde(e.target.value)}
              className="w-full rounded-lg border px-3 py-2"
            />
          </div>
          <div>
            <label className="block text-sm font-medium mb-1">Hasta</label>
            <input
              type="date"
              value={hasta}
              onChange={(e) => setHasta(e.target.value)}
              className="w-full rounded-lg border px-3 py-2"
            />
          </div>
          <button
            onClick={setHoy}
            className="rounded-lg border border-fuchsia-300 bg-fuchsia-50 px-3 py-2 text-sm text-fuchsia-700 hover:bg-fuchsia-100 transition"
          >
            Hoy
          </button>
          <div className="flex gap-2">
            <button
              onClick={setSemanaAnterior}
              className="flex-1 rounded-lg border border-fuchsia-300 bg-fuchsia-50 px-3 py-2 text-sm text-fuchsia-700 hover:bg-fuchsia-100 transition"
            >
              Semana pasada
            </button>
            <button
              onClick={setSemanActual}
              className="flex-1 rounded-lg border border-fuchsia-300 bg-fuchsia-50 px-3 py-2 text-sm text-fuchsia-700 hover:bg-fuchsia-100 transition"
            >
              Semana actual
            </button>
          </div>
          <button
            onClick={calcular}
            disabled={!desde || !hasta || loading}
            className="rounded-lg bg-gradient-to-r from-fuchsia-600 to-fuchsia-700 px-4 py-2.5 text-white font-medium shadow-md hover:shadow-lg disabled:opacity-40 transition"
          >
            {loading ? "Calculando..." : "Calcular"}
          </button>
        </div>
      </div>

      {error && (
        <div className="rounded-lg border border-red-200 bg-red-50 px-4 py-3 text-red-700">
          {error}
        </div>
      )}

      {/* Resultados */}
      {pagos.length > 0 && (
        <div className="space-y-4">
          {/* Total general */}
          <div className="rounded-2xl border-2 border-fuchsia-200 bg-gradient-to-br from-fuchsia-50 to-fuchsia-100 p-6">
            <div className="text-center">
              <div className="text-sm text-fuchsia-600 font-medium mb-1">Total General</div>
              <div className="text-4xl font-bold text-fuchsia-900">{formatCurrency(totalGeneral)}</div>
              <div className="text-sm text-fuchsia-600 mt-2">
                {pagos.reduce((sum, p) => sum + p.cantidadTurnos, 0)} turnos • {pagos.length} barbero(s)
              </div>
            </div>
          </div>

          {/* Detalle por barbero */}
          {pagos.map((pago) => (
            <div key={pago.barberoId} className="rounded-2xl border border-slate-200 bg-white shadow-sm overflow-hidden">
              {/* Header del barbero */}
              <div className="bg-gradient-to-r from-slate-50 to-slate-100 px-6 py-4 border-b">
                <div className="flex items-center justify-between">
                  <div>
                    <h3 className="text-lg font-semibold text-slate-900">{pago.barberoNombre}</h3>
                    <p className="text-sm text-slate-600">{pago.cantidadTurnos} turno(s)</p>
                  </div>
                  <div className="text-right">
                    <div className="text-2xl font-bold text-fuchsia-600">{formatCurrency(pago.totalAPagar)}</div>
                    <div className="text-xs text-slate-500">Total a pagar</div>
                  </div>
                </div>
              </div>

              {/* Ingresos brutos informativos */}
              <div className="px-6 py-4 bg-slate-50 border-b">
                <h4 className="text-sm font-semibold text-slate-700 mb-3">Ingresos brutos por medio de pago (informativos)</h4>
                <div className="grid grid-cols-1 md:grid-cols-4 gap-3">
                  <div className="bg-white rounded-lg border border-amber-200 p-3">
                    <div className="flex items-center gap-2 mb-1">
                      <svg className="w-4 h-4 text-amber-600" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                        <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 18h.01M8 21h8a2 2 0 002-2V5a2 2 0 00-2-2H8a2 2 0 00-2 2v14a2 2 0 002 2z" />
                      </svg>
                      <span className="text-xs font-medium text-slate-600">APP</span>
                    </div>
                    <div className="text-lg font-bold text-amber-600">{formatCurrency(pago.montoAppBruto)}</div>
                    <div className="text-xs text-slate-500">Mercado Pago</div>
                  </div>
                  <div className="bg-white rounded-lg border border-blue-200 p-3">
                    <div className="flex items-center gap-2 mb-1">
                      <svg className="w-4 h-4 text-blue-600" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                        <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M3 10h18M7 15h1m4 0h1m-7 4h12a3 3 0 003-3V8a3 3 0 00-3-3H6a3 3 0 00-3 3v8a3 3 0 003 3z" />
                      </svg>
                      <span className="text-xs font-medium text-slate-600">Transferencia</span>
                    </div>
                    <div className="text-lg font-bold text-blue-600">{formatCurrency(pago.montoTransferenciaBruto)}</div>
                    <div className="text-xs text-slate-500">Al alias del negocio</div>
                  </div>
                  <div className="bg-white rounded-lg border border-green-200 p-3">
                    <div className="flex items-center gap-2 mb-1">
                      <svg className="w-4 h-4 text-green-600" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                        <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M17 9V7a2 2 0 00-2-2H5a2 2 0 00-2 2v6a2 2 0 002 2h2m2 4h10a2 2 0 002-2v-6a2 2 0 00-2-2H9a2 2 0 00-2 2v6a2 2 0 002 2zm7-5a2 2 0 11-4 0 2 2 0 014 0z" />
                      </svg>
                      <span className="text-xs font-medium text-slate-600">Efectivo</span>
                    </div>
                    <div className="text-lg font-bold text-green-600">{formatCurrency(pago.montoEfectivoBruto)}</div>
                    <div className="text-xs text-slate-500">A caja</div>
                  </div>
                  <div className="bg-gradient-to-br from-slate-100 to-slate-200 rounded-lg border border-slate-300 p-3">
                    <div className="flex items-center gap-2 mb-1">
                      <svg className="w-4 h-4 text-slate-700" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                        <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M9 7h6m0 10v-3m-3 3h.01M9 17h.01M9 14h.01M12 14h.01M15 11h.01M12 11h.01M9 11h.01M7 21h10a2 2 0 002-2V5a2 2 0 00-2-2H7a2 2 0 00-2 2v14a2 2 0 002 2z" />
                      </svg>
                      <span className="text-xs font-medium text-slate-700">Total Bruto</span>
                    </div>
                    <div className="text-lg font-bold text-slate-900">{formatCurrency(pago.totalBruto)}</div>
                    <div className="text-xs text-slate-600">Suma de todos</div>
                  </div>
                </div>
              </div>

              {/* Cálculo de comisión */}
              <div className="px-6 py-4 bg-fuchsia-50 border-b">
                <div className="flex items-center justify-between">
                  <div>
                    <h4 className="text-sm font-semibold text-fuchsia-900 mb-1">Comisión del barbero (50%)</h4>
                    <p className="text-xs text-fuchsia-600">
                      {formatCurrency(pago.totalBruto)} × 50% = {formatCurrency(pago.comision50)}
                    </p>
                  </div>
                  <div className="text-right">
                    <div className="text-2xl font-bold text-fuchsia-600">{formatCurrency(pago.comision50)}</div>
                  </div>
                </div>
              </div>

              {/* Bonus por volumen */}
              {pago.cantidadBonos > 0 && (
                <div className="px-6 py-4 bg-gradient-to-r from-purple-50 to-pink-50 border-b">
                  <div className="flex items-center justify-between mb-3">
                    <div>
                      <div className="flex items-center gap-2 mb-1">
                        <svg className="w-5 h-5 text-purple-600" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                          <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 8c-1.657 0-3 .895-3 2s1.343 2 3 2 3 .895 3 2-1.343 2-3 2m0-8c1.11 0 2.08.402 2.599 1M12 8V7m0 1v8m0 0v1m0-1c-1.11 0-2.08-.402-2.599-1M21 12a9 9 0 11-18 0 9 9 0 0118 0z" />
                        </svg>
                        <h4 className="text-sm font-semibold text-purple-700">Bonus por volumen diario</h4>
                      </div>
                      <p className="text-xs text-purple-600">
                        {pago.cantidadBonos} bono{pago.cantidadBonos > 1 ? 's' : ''} ganado{pago.cantidadBonos > 1 ? 's' : ''} (1 por cada 10 turnos/día)
                      </p>
                    </div>
                    <div className="text-right">
                      <div className="text-2xl font-bold text-purple-600">+{formatCurrency(pago.montoBonus)}</div>
                      <div className="text-xs text-purple-500">50% × corte id=1</div>
                    </div>
                  </div>
                  {/* Detalle de bonos por día */}
                  {pago.bonosPorDia && Object.keys(pago.bonosPorDia).length > 0 && (
                    <div className="mt-3 pt-3 border-t border-purple-200">
                      <p className="text-xs font-medium text-purple-700 mb-2">Detalle por día:</p>
                      <div className="flex flex-wrap gap-2">
                        {Object.entries(pago.bonosPorDia).map(([fecha, cantidad]) => (
                          <div key={fecha} className="bg-white rounded-lg border border-purple-200 px-3 py-1.5">
                            <span className="text-xs font-medium text-purple-900">{fecha}</span>
                            <span className="text-xs text-purple-600 ml-2">→ {cantidad} bono{cantidad > 1 ? 's' : ''}</span>
                          </div>
                        ))}
                      </div>
                    </div>
                  )}
                </div>
              )}

              {/* Detalle de servicios */}
              <div className="p-6">
                <h4 className="text-sm font-semibold text-slate-700 mb-3">Detalle por servicio</h4>
                <div className="space-y-2">
                  {pago.detalleServicios.map((detalle, idx) => (
                    <div key={idx} className="flex items-center justify-between py-2 border-b last:border-b-0">
                      <div className="flex-1">
                        <div className="font-medium text-slate-900">{detalle.servicioNombre}</div>
                        <div className="text-xs text-slate-500">
                          {detalle.cantidad} × {formatCurrency(detalle.precioUnitario)}
                        </div>
                      </div>
                      <div className="text-right">
                        <div className="font-semibold text-slate-900">{formatCurrency(detalle.subtotal)}</div>
                      </div>
                    </div>
                  ))}
                </div>
              </div>
            </div>
          ))}
        </div>
      )}

      {/* Estado vacío */}
      {!loading && pagos.length === 0 && !error && (
        <div className="rounded-2xl border border-slate-200 bg-white p-12 text-center">
          <svg className="w-16 h-16 text-slate-300 mx-auto mb-4" fill="none" viewBox="0 0 24 24" stroke="currentColor">
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={1.5} d="M9 7h6m0 10v-3m-3 3h.01M9 17h.01M9 14h.01M12 14h.01M15 11h.01M12 11h.01M9 11h.01M7 21h10a2 2 0 002-2V5a2 2 0 00-2-2H7a2 2 0 00-2 2v14a2 2 0 002 2z" />
          </svg>
          <p className="text-slate-500 mb-1">Selecciona un rango de fechas</p>
          <p className="text-sm text-slate-400">y haz clic en "Calcular" para ver los resultados</p>
        </div>
      )}
    </div>
  );
}
