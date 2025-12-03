import { useEffect, useState } from "react";

type Props = {
  open: boolean;
  onCancel: () => void;
  onConfirm: (data: { nombre: string; telefono: string; edad: number }) => void;
};

export default function DatosClienteModal({ open, onCancel, onConfirm }: Props) {
  const [nombre, setNombre] = useState("");
  const [countryCode, setCountryCode] = useState("+54"); // 🆕 Código de país (default Argentina)
  const [telefono, setTelefono] = useState("");
  const [edad, setEdad] = useState<number | "">("");

  // bloquear scroll detrás del modal
  useEffect(() => {
    if (open) document.body.style.overflow = "hidden";
    return () => { document.body.style.overflow = ""; };
  }, [open]);

  if (!open) return null;

  const confirmar = () => {
    const e = typeof edad === "string" ? parseInt(edad || "0", 10) : edad;
    if (!nombre.trim()) return alert("Ingresá tu nombre");
    if (!telefono.trim()) return alert("Ingresá tu teléfono");
    if (!e || e < 3) return alert("Ingresá una edad válida (≥ 4)");

    // 🆕 Combinar código de país + número (sin + duplicado)
    let telefonoCompleto = countryCode + telefono.replace(/^\+/, "").replace(/\s+/g, "");

    // 🇦🇷 Argentina: insertar el 9 después del +54 para WhatsApp
    if (countryCode === "+54") {
      telefonoCompleto = "+549" + telefono.replace(/^\+/, "").replace(/\s+/g, "");
    }

    onConfirm({ nombre: nombre.trim(), telefono: telefonoCompleto, edad: e });
  };

  return (
    <div className="fixed inset-0 z-[1000]">
      {/* Backdrop que bloquea toda interacción detrás */}
      <div className="absolute inset-0 bg-black/50 backdrop-blur-sm" onClick={onCancel} />
      <div className="absolute inset-0 flex items-end sm:items-center justify-center p-0 sm:p-4">
        <div className="w-full max-w-md rounded-t-3xl sm:rounded-2xl bg-white shadow-2xl p-5 sm:p-6 max-h-[90vh] overflow-y-auto">
          <h2 className="text-lg sm:text-xl font-semibold mb-4 sm:mb-5">Tus datos</h2>

          <label className="block text-sm font-medium mb-1.5">Nombre</label>
          <input
            className="w-full rounded-lg border border-gray-300 px-3 py-2.5 mb-3 text-base focus:outline-none focus:ring-2 focus:ring-fuchsia-500 focus:border-transparent"
            value={nombre}
            onChange={(e) => setNombre(e.target.value)}
            placeholder="Nombre y apellido"
          />

          <label className="block text-sm font-medium mb-1.5">Teléfono</label>
          <div className="flex gap-2 mb-3">
            {/* Selector de código de país */}
            <select
              className="w-[110px] flex-shrink-0 rounded-lg border border-gray-300 px-2 py-2.5 text-base focus:outline-none focus:ring-2 focus:ring-fuchsia-500 focus:border-transparent bg-white"
              value={countryCode}
              onChange={(e) => setCountryCode(e.target.value)}
            >
              <option value="+54">AR +54</option>
              <option value="+598">UY +598</option>
              <option value="+595">PY +595</option>
              <option value="+56">CL +56</option>
              <option value="+55">BR +55</option>
              <option value="+1">US +1</option>
              <option value="+34">ES +34</option>
              <option value="+52">MX +52</option>
              <option value="+51">PE +51</option>
              <option value="+57">CO +57</option>
            </select>
            {/* Input de número */}
            <input
              className="flex-1 min-w-0 rounded-lg border border-gray-300 px-3 py-2.5 text-base focus:outline-none focus:ring-2 focus:ring-fuchsia-500 focus:border-transparent"
              value={telefono}
              onChange={(e) => setTelefono(e.target.value)}
              placeholder={countryCode === "+54" ? "11 1234 5678" : "número"}
              type="tel"
            />
          </div>

          <label className="block text-sm font-medium mb-1.5">Edad</label>
          <input
            type="number"
            className="w-full rounded-lg border border-gray-300 px-3 py-2.5 mb-4 sm:mb-5 text-base focus:outline-none focus:ring-2 focus:ring-fuchsia-500 focus:border-transparent"
            value={edad}
            onChange={(e) => setEdad(e.target.value === "" ? "" : Number(e.target.value))}
            placeholder="18"
            min={3}
          />

          <div className="flex flex-col-reverse sm:flex-row items-stretch sm:items-center justify-center gap-2 sm:gap-3">
            <button
              className="px-4 py-2.5 rounded-lg border border-gray-300 shadow-sm hover:bg-gray-50 transition-colors font-medium"
              onClick={onCancel}
            >
              Cancelar
            </button>
            <button
              className="px-4 py-2.5 rounded-lg bg-green-600 hover:bg-green-700 text-white shadow-lg hover:shadow-xl transition-all font-semibold"
              onClick={confirmar}
            >
              Pagar con Mercado Pago
            </button>
          </div>
        </div>
      </div>
    </div>
  );
}
