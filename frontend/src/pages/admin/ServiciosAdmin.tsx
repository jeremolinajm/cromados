// src/pages/admin/ServiciosAdmin.tsx
import { useEffect, useMemo, useState } from "react";
import AdminApi, { type ServicioDTO, type BarberoDTO } from "../../lib/adminApi";

type Mode = { kind: "idle" } | { kind: "create" } | { kind: "edit"; id: number };

type FormState = {
  nombre: string;
  precio: string;       // string mientras se tipea
  duracionMin: string;  // string mientras se tipea
  sesiones: string;     // string mientras se tipea
  descripcion: string;
  adicional: boolean;   // ✅ NUEVO: Checkbox para marcar como adicional
  barberosHabilitadosIds: number[]; // ✅ IDs de barberos que pueden ofrecer este servicio
};

const emptyForm: FormState = {
  nombre: "",
  precio: "",
  duracionMin: "",
  sesiones: "1",
  descripcion: "",
  adicional: false,     // ✅ Por defecto NO es adicional
  barberosHabilitadosIds: [], // ✅ Por defecto todos pueden ofrecer el servicio
};

const onlyPosInt = (s: string) => /^(?:[1-9]\d*)$/.test(s); // >=1

export default function ServiciosAdmin() {
  const [items, setItems] = useState<ServicioDTO[]>([]);
  const [barberos, setBarberos] = useState<BarberoDTO[]>([]);
  const [loading, setLoading] = useState(false);
  const [mode, setMode] = useState<Mode>({ kind: "idle" });
  const [form, setForm] = useState<FormState>(emptyForm);
  const [error, setError] = useState<string | null>(null);

  const load = async () => {
    setLoading(true);
    setError(null);
    try {
      const page = await AdminApi.listServicios(0, 100, "id,asc");
      // ✅ Asignar directo; content ya es ServicioDTO[]
      setItems(page.content);
      // Si querés fallback ultra seguro:
      // setItems(Array.isArray(page.content) ? page.content : ([] as ServicioDTO[]));
    } catch (e: any) {
      setError(e?.message || "Error cargando servicios");
    } finally {
      setLoading(false);
    }
  };

  const loadBarberos = async () => {
    try {
      const page = await AdminApi.listBarberos(0, 500, "nombre,asc");
      setBarberos(page.content || []);
    } catch (e: any) {
      console.error("Error cargando barberos:", e);
    }
  };

  useEffect(() => {
    load();
    loadBarberos();
  }, []);

  const startCreate = () => {
    setForm(emptyForm);
    setMode({ kind: "create" });
    setError(null);
  };

  const startEdit = (it: ServicioDTO) => {
    setForm({
      nombre: it.nombre ?? "",
      precio: String(it.precio ?? ""),
      duracionMin: String(it.duracionMin ?? ""),
      sesiones: String((it as any).sesiones ?? "1"),
      descripcion: it.descripcion ?? "",
      adicional: it.adicional ?? false, // ✅ Cargar valor de adicional
      barberosHabilitadosIds: (it as any).barberosHabilitadosIds ?? [], // ✅ Cargar barberos habilitados
    });
    setMode({ kind: "edit", id: it.id! });
    setError(null);
  };

  const cancel = () => {
    setMode({ kind: "idle" });
    setForm(emptyForm);
    setError(null);
  };

  const canSave = useMemo(() => {
    return (
      form.nombre.trim().length > 1 &&
      onlyPosInt(form.precio) &&
      onlyPosInt(form.duracionMin) &&
      onlyPosInt(form.sesiones) && Number(form.sesiones) >= 1
    );
  }, [form]);

  const save = async () => {
    if (!canSave) return;
    setLoading(true);
    setError(null);

    // ✅ Payload exacto que pide AdminApi (sesiones y adicional obligatorios)
    const dtoPayload: {
      nombre: string;
      precio: number;
      duracionMin: number;
      descripcion?: string | "";
      sesiones: number;
      adicional: boolean;
      barberosHabilitadosIds?: number[];
    } = {
      nombre: form.nombre.trim(),
      precio: Number(form.precio),
      duracionMin: Number(form.duracionMin),
      sesiones: Number(form.sesiones),
      descripcion: form.descripcion?.trim() || "",
      adicional: form.adicional, // ✅ Incluir en el payload
      barberosHabilitadosIds: form.barberosHabilitadosIds, // ✅ Incluir barberos habilitados
    };

    try {
      if (mode.kind === "create") {
        await AdminApi.createServicio(dtoPayload);
      } else if (mode.kind === "edit") {
        await AdminApi.updateServicio(mode.id, dtoPayload);
      }
      await load();
      cancel();
    } catch (e: any) {
      setError(e?.message || "Error guardando servicio");
    } finally {
      setLoading(false);
    }
  };

  const toggleActivo = async (id: number) => {
    setLoading(true);
    setError(null);
    try {
      await AdminApi.toggleServicioActivo(id);
      await load();
    } catch (e: any) {
      setError(e?.message || "Error cambiando estado del servicio");
    } finally {
      setLoading(false);
    }
  };

  const removeOne = async (id: number) => {
    if (!confirm("¿Eliminar servicio?")) return;
    setLoading(true);
    setError(null);
    try {
      await AdminApi.deleteServicio(id);
      await load();
    } catch (e: any) {
      setError(e?.message || "Error eliminando servicio");
    } finally {
      setLoading(false);
    }
  };

  const onNumChange =
    (field: keyof FormState) => (e: React.ChangeEvent<HTMLInputElement>) => {
      const v = e.target.value;
      if (v === "" || onlyPosInt(v)) {
        setForm((f) => ({ ...f, [field]: v }));
      }
    };

  return (
    <div className="space-y-6">
      <header className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-semibold">Servicios</h1>
          <p className="text-slate-600">Alta, edición y baja de servicios.</p>
        </div>
        {mode.kind === "idle" && (
          <button
            onClick={startCreate}
            className="inline-flex items-center gap-2 rounded-xl bg-gradient-to-r from-indigo-600 to-indigo-700 px-4 py-2.5 text-white font-medium shadow-md hover:shadow-lg hover:from-indigo-700 hover:to-indigo-800 transition-all"
          >
            <svg className="w-5 h-5" fill="none" viewBox="0 0 24 24" stroke="currentColor">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 4v16m8-8H4" />
            </svg>
            Nuevo servicio
          </button>
        )}
      </header>

      {error && (
        <div className="rounded-lg border border-red-200 bg-red-50 px-4 py-2 text-red-700">
          {error}
        </div>
      )}

      {mode.kind !== "idle" && (
        <div className="rounded-2xl border border-slate-200 bg-white p-4 shadow-sm">
          <div className="grid md:grid-cols-2 gap-4">
            <div>
              <label className="block text-sm font-medium mb-1">Nombre</label>
              <input
                className="w-full rounded-lg border px-3 py-2"
                value={form.nombre}
                onChange={(e) => setForm({ ...form, nombre: e.target.value })}
                placeholder="Ej. Corte clásico"
              />
            </div>
            <div>
              <label className="block text-sm font-medium mb-1">Precio (ARS)</label>
              <input
                className="w-full rounded-lg border px-3 py-2"
                value={form.precio}
                onChange={onNumChange("precio")}
                placeholder="Ej. 5000"
                inputMode="numeric"
              />
            </div>
            <div>
              <label className="block text-sm font-medium mb-1">Duración (min)</label>
              <input
                className="w-full rounded-lg border px-3 py-2"
                value={form.duracionMin}
                onChange={onNumChange("duracionMin")}
                placeholder="Ej. 30"
                inputMode="numeric"
              />
            </div>
            <div>
              <label className="block text-sm font-medium mb-1">Sesiones</label>
              <input
                className="w-full rounded-lg border px-3 py-2"
                value={form.sesiones}
                onChange={onNumChange("sesiones")}
                placeholder="1"
                inputMode="numeric"
              />
            </div>
            <div className="md:col-span-2">
              <label className="block text-sm font-medium mb-1">Descripción</label>
              <textarea
                className="w-full rounded-lg border px-3 py-2"
                rows={3}
                value={form.descripcion}
                onChange={(e) =>
                  setForm({ ...form, descripcion: e.target.value })
                }
                placeholder="Opcional"
              />
            </div>
            <div className="md:col-span-2">
              <label className="flex items-center gap-2 cursor-pointer">
                <input
                  type="checkbox"
                  checked={form.adicional}
                  onChange={(e) => setForm({ ...form, adicional: e.target.checked })}
                  className="rounded border-slate-300 text-indigo-600 focus:ring-indigo-500"
                />
                <span className="text-sm font-medium">
                  Es un servicio adicional
                  <span className="block text-xs text-slate-500 font-normal">
                    Los servicios adicionales se pueden agregar a otros servicios en la misma reserva (ej: Lavado con Corte)
                  </span>
                </span>
              </label>
            </div>
            <div className="md:col-span-2">
              <label className="block text-sm font-medium mb-2">
                Barberos habilitados
                <span className="block text-xs text-slate-500 font-normal mt-1">
                  Si no seleccionás ninguno, todos los barberos podrán ofrecer este servicio. Si seleccionás algunos, solo esos barberos podrán ofrecerlo.
                </span>
              </label>
              <div className="flex flex-wrap gap-2">
                {barberos.map((b) => (
                  <label key={b.id} className="flex items-center gap-2 cursor-pointer rounded-lg border px-3 py-2 hover:bg-slate-50">
                    <input
                      type="checkbox"
                      checked={form.barberosHabilitadosIds.includes(b.id!)}
                      onChange={(e) => {
                        if (e.target.checked) {
                          setForm({ ...form, barberosHabilitadosIds: [...form.barberosHabilitadosIds, b.id!] });
                        } else {
                          setForm({ ...form, barberosHabilitadosIds: form.barberosHabilitadosIds.filter(id => id !== b.id!) });
                        }
                      }}
                      className="rounded border-slate-300 text-indigo-600 focus:ring-indigo-500"
                    />
                    <span className="text-sm">{b.nombre}</span>
                  </label>
                ))}
              </div>
            </div>
          </div>

          <div className="flex gap-2 mt-4">
            <button
              disabled={!canSave || loading}
              onClick={save}
              className="rounded-lg bg-indigo-600 px-4 py-2 text-white disabled:opacity-40"
            >
              Guardar
            </button>
            <button
              disabled={loading}
              onClick={cancel}
              className="rounded-lg border px-4 py-2"
            >
              Cancelar
            </button>
          </div>
        </div>
      )}

      {/* Tabla responsive */}
      <div className="rounded-2xl border border-slate-200 bg-white shadow-sm">
        {/* Vista móvil: cards */}
        <div className="block md:hidden">
          {loading && (
            <div className="p-4 text-slate-500 text-sm">Cargando…</div>
          )}

          {!loading && items.map((it) => (
            <div key={it.id} className="border-b last:border-b-0 p-3">
              <div className="flex items-start justify-between gap-3">
                <div className="flex-1 min-w-0">
                  <div className="font-medium text-base mb-1">{it.nombre}</div>

                  <div className="flex items-center gap-3 text-xs text-slate-600 mb-2">
                    <span className="font-semibold text-indigo-600">ARS ${it.precio}</span>
                    <span>⏱️ {it.duracionMin} min</span>
                    <span>📅 {(it as any).sesiones ?? 1} sesión{(it as any).sesiones > 1 ? 'es' : ''}</span>
                  </div>

                  {it.descripcion && (
                    <div className="text-xs text-slate-500 mb-2 line-clamp-2">
                      {it.descripcion}
                    </div>
                  )}

                  <div className="text-xs text-slate-400">ID: {it.id}</div>

                  <div className="flex flex-wrap gap-2 mt-2">
                    <button
                      onClick={() => startEdit(it)}
                      className="rounded-lg border px-2 py-1 text-xs hover:bg-slate-50"
                    >
                      Editar
                    </button>
                    <button
                      onClick={() => it.id && toggleActivo(it.id)}
                      className={`rounded-lg border px-2 py-1 text-xs ${
                        (it as any).activo === false
                          ? 'text-green-600 border-green-300 hover:bg-green-50'
                          : 'text-orange-600 border-orange-300 hover:bg-orange-50'
                      }`}
                    >
                      {(it as any).activo === false ? 'Activar' : 'Desactivar'}
                    </button>
                    <button
                      onClick={() => it.id && removeOne(it.id)}
                      className="rounded-lg border px-2 py-1 text-xs text-red-600 border-red-300 hover:bg-red-50"
                    >
                      Eliminar
                    </button>
                  </div>
                </div>
              </div>
            </div>
          ))}

          {!loading && items.length === 0 && (
            <div className="p-8 text-center">
              <svg className="w-12 h-12 text-slate-300 mx-auto mb-3" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={1.5} d="M19 21V5a2 2 0 00-2-2H7a2 2 0 00-2 2v16m14 0h2m-2 0h-5m-9 0H3m2 0h5M9 7h1m-1 4h1m4-4h1m-1 4h1m-5 10v-5a1 1 0 011-1h2a1 1 0 011 1v5m-4 0h4" />
              </svg>
              <p className="text-sm text-slate-500 mb-2">Sin servicios.</p>
              <button
                onClick={startCreate}
                className="text-xs text-indigo-600 hover:text-indigo-700 font-medium"
              >
                Crear primer servicio
              </button>
            </div>
          )}
        </div>

        {/* Vista desktop: tabla */}
        <div className="hidden md:block overflow-x-auto">
          <table className="min-w-full text-sm">
            <thead className="bg-slate-50 text-slate-700">
              <tr>
                <th className="px-4 py-3 text-left text-xs font-semibold uppercase">ID</th>
                <th className="px-4 py-3 text-left text-xs font-semibold uppercase">Nombre</th>
                <th className="px-4 py-3 text-left text-xs font-semibold uppercase">Precio</th>
                <th className="px-4 py-3 text-left text-xs font-semibold uppercase">Duración</th>
                <th className="px-4 py-3 text-left text-xs font-semibold uppercase">Sesiones</th>
                <th className="px-4 py-3 text-left text-xs font-semibold uppercase">Descripción</th>
                <th className="px-4 py-3 text-right text-xs font-semibold uppercase">Acciones</th>
              </tr>
            </thead>
            <tbody>
              {loading && (
                <tr>
                  <td colSpan={7} className="px-4 py-4 text-slate-500 text-center">
                    Cargando…
                  </td>
                </tr>
              )}

              {!loading && items.map((it) => (
                <tr key={it.id} className="border-t border-slate-200 hover:bg-slate-50">
                  <td className="px-4 py-3">
                    <span className="font-mono text-xs font-semibold">#{it.id}</span>
                  </td>
                  <td className="px-4 py-3">
                    <div className="font-medium">{it.nombre}</div>
                  </td>
                  <td className="px-4 py-3">
                    <span className="font-semibold text-indigo-600">ARS ${it.precio}</span>
                  </td>
                  <td className="px-4 py-3">{it.duracionMin} min</td>
                  <td className="px-4 py-3">{(it as any).sesiones ?? 1}</td>
                  <td className="px-4 py-3">
                    <div className="text-slate-600 text-sm max-w-xs truncate">
                      {it.descripcion || "-"}
                    </div>
                  </td>
                  <td className="px-4 py-3 text-right space-x-2">
                    <button
                      onClick={() => startEdit(it)}
                      className="rounded-xl border px-3 py-1 hover:bg-slate-50"
                    >
                      Editar
                    </button>
                    <button
                      onClick={() => it.id && toggleActivo(it.id)}
                      className={`rounded-xl border px-3 py-1 ${
                        (it as any).activo === false
                          ? 'text-green-600 border-green-300 hover:bg-green-50'
                          : 'text-orange-600 border-orange-300 hover:bg-orange-50'
                      }`}
                    >
                      {(it as any).activo === false ? 'Activar' : 'Desactivar'}
                    </button>
                    <button
                      onClick={() => it.id && removeOne(it.id)}
                      className="rounded-xl border px-3 py-1 text-red-600 border-red-300 hover:bg-red-50"
                    >
                      Eliminar
                    </button>
                  </td>
                </tr>
              ))}

              {!loading && items.length === 0 && (
                <tr>
                  <td colSpan={7} className="px-4 py-8 text-slate-500 text-center">
                    <div className="flex flex-col items-center gap-2">
                      <svg className="w-12 h-12 text-slate-300" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                        <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={1.5} d="M19 21V5a2 2 0 00-2-2H7a2 2 0 00-2 2v16m14 0h2m-2 0h-5m-9 0H3m2 0h5M9 7h1m-1 4h1m4-4h1m-1 4h1m-5 10v-5a1 1 0 011-1h2a1 1 0 011 1v5m-4 0h4" />
                      </svg>
                      <p className="text-sm">Sin servicios.</p>
                      <button
                        onClick={startCreate}
                        className="mt-2 text-xs text-indigo-600 hover:text-indigo-700 font-medium"
                      >
                        Crear primer servicio
                      </button>
                    </div>
                  </td>
                </tr>
              )}
            </tbody>
          </table>
        </div>
      </div>
    </div>
  );
}