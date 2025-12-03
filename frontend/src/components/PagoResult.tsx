// src/pages/PagoResult.tsx
import { useMemo, useState } from "react";
import { useLocation, Link } from "react-router-dom";

type Kind = "success" | "pending" | "failure";

export default function PagoResult({ kind }: { kind: Kind }) {
  const q = new URLSearchParams(useLocation().search);
  const paymentId  = q.get("payment_id") || q.get("collection_id") || "-";
  const status     = q.get("status") || q.get("collection_status") || kind;
  const preference = q.get("preference_id") || "-";
  const orderId    = q.get("merchant_order_id") || "-";

  const [showDetails, setShowDetails] = useState(false);

  const title = useMemo(() => {
    if (kind === "success") return "¡Pago aprobado!";
    if (kind === "pending") return "Pago pendiente";
    return "No pudimos procesar el pago";
  }, [kind]);

  const subtitle = useMemo(() => {
    if (kind === "success") {
      return "Tu pago se aprobó correctamente. En minutos confirmamos tu turno por WhatsApp.";
    }
    if (kind === "pending") {
      return "Tu pago quedó en revisión. Cuando se apruebe, te confirmamos el turno por WhatsApp.";
    }
    return "Podés intentar nuevamente. Si el problema persiste, escribinos por WhatsApp.";
  }, [kind]);

  return (
    <div className="max-w-xl mx-auto p-6">
      <h1 className="text-2xl font-semibold mb-2">{title}</h1>
      <p className="text-slate-700 mb-4">{subtitle}</p>

      {/* Acciones principales para el cliente */}
      <div className="mt-4 flex flex-wrap gap-2">
        <Link to="/" className="rounded-lg border px-4 py-2">
          Volver al inicio
        </Link>
      </div>

      {/* Detalles técnicos (opcional) */}
      <div className="mt-6">
        <button
          className="text-sm text-slate-600 underline"
          onClick={() => setShowDetails((v) => !v)}
        >
          {showDetails ? "Ocultar detalles" : "Ver detalles de la operación"}
        </button>

        {showDetails && (
          <div className="mt-2 rounded-lg border p-3 text-sm space-y-1 bg-white">
            <div>
              <span className="text-slate-500">Estado:</span> <b>{status}</b>
            </div>
            <div>
              <span className="text-slate-500">Payment ID:</span> {paymentId}
            </div>
            <div>
              <span className="text-slate-500">Preference:</span> {preference}
            </div>
            <div>
              <span className="text-slate-500">Order:</span> {orderId}
            </div>
            <p className="text-xs text-slate-500 pt-2">
              Nota: el turno se confirma automáticamente por un webhook de Mercado Pago.
              Si no lo ves de inmediato, puede tardar unos minutos.
            </p>
          </div>
        )}
      </div>
    </div>
  );
}
