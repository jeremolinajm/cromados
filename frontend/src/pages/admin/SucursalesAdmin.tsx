import { useEffect, useMemo, useState } from "react";
import { AdminApi, type SucursalDTO, resolveUrl } from "../../lib/adminApi";

type Mode =
  | { kind: "idle" }
  | { kind: "create" }
  | { kind: "edit"; id: number };

const EMPTY: SucursalDTO = { nombre: "", direccion: "" };

export default function SucursalesAdmin() {
  const [rows, setRows] = useState<SucursalDTO[]>([]);
  const [mode, setMode] = useState<Mode>({ kind: "idle" });
  const [form, setForm] = useState<SucursalDTO>({ ...EMPTY });
  const [file, setFile] = useState<File | null>(null);

  const [loading, setLoading] = useState(false);
  const [loadingTable, setLoadingTable] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const loadAll = async () => {
    setLoadingTable(true);
    setError(null);
    try {
      const page = await AdminApi.listSucursales(0, 200, "id,asc");
      const list = Array.isArray(page?.content) ? (page.content as SucursalDTO[]) : [];
      setRows(list);
    } catch (e: any) {
      setError(e?.message || "Error cargando sucursales");
      setRows([]);
    } finally {
      setLoadingTable(false);
    }
  };

  useEffect(() => {
    loadAll();
  }, []);

  const canSave = useMemo(
    () => form.nombre.trim() !== "" && form.direccion.trim() !== "",
    [form]
  );

  const resetForm = () => {
    setForm({ ...EMPTY });
    setFile(null);
    setMode({ kind: "idle" });
  };

  const startCreate = () => {
    setForm({ ...EMPTY });
    setFile(null);
    setMode({ kind: "create" });
  };

  const startEdit = (s: SucursalDTO) => {
    setForm({ id: s.id, nombre: s.nombre, direccion: s.direccion, fotoUrl: s.fotoUrl || "" });
    setFile(null);
    setMode({ kind: "edit", id: Number(s.id) });
  };

  const save = async () => {
    if (!canSave) return;
    setLoading(true);
    setError(null);
    try {
      let saved: SucursalDTO;
      if (mode.kind === "edit" && mode.id) {
        saved = await AdminApi.updateSucursal(mode.id, {
          nombre: form.nombre,
          direccion: form.direccion,
        });
      } else {
        saved = await AdminApi.createSucursal({
          nombre: form.nombre,
          direccion: form.direccion,
        });
      }

      if (file && saved?.id) {
        await AdminApi.uploadSucursalFoto(Number(saved.id), file);
      }

      resetForm();
      await loadAll();
    } catch (e: any) {
      setError(e?.message || "Error guardando sucursal");
    } finally {
      setLoading(false);
    }
  };

  const del = async (id?: number) => {
    if (!id) return;
    if (!confirm("¿Eliminar sucursal?")) return;
    setLoading(true);
    setError(null);
    try {
      await AdminApi.deleteSucursal(id);
      await loadAll();
    } catch (e: any) {
      setError(e?.message || "Error eliminando sucursal");
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="space-y-6">
      <header className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4">
        <div>
          <h1 className="text-2xl font-semibold">Sucursales</h1>
          <p className="text-slate-600 text-sm">Alta, edición, foto y baja.</p>
        </div>

        {mode.kind === "idle" && (
          <button
            onClick={startCreate}
            className="inline-flex items-center justify-center gap-2 rounded-xl bg-gradient-to-r from-fuchsia-600 to-fuchsia-700 px-4 py-2.5 text-white font-medium shadow-md hover:shadow-lg hover:from-fuchsia-700 hover:to-fuchsia-800 transition-all"
          >
            <svg className="w-5 h-5" fill="none" viewBox="0 0 24 24" stroke="currentColor">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 4v16m8-8H4" />
            </svg>
            Nueva sucursal
          </button>
        )}
      </header>

      {error && (
        <div className="rounded-lg border border-red-200 bg-red-50 px-3 py-2 text-red-700">
          {error}
        </div>
      )}

      {/* Formulario solo en create/edit */}
      {mode.kind !== "idle" && (
        <div className="rounded-2xl border border-slate-200 bg-white p-4 sm:p-6 shadow-sm">
          <div className="grid gap-4 sm:grid-cols-2">
            <label className="grid gap-2">
              <span className="text-sm font-medium text-slate-700">Nombre</span>
              <input
                className="w-full border border-slate-300 rounded-xl px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-fuchsia-500 focus:border-transparent"
                value={form.nombre}
                onChange={(e) => setForm({ ...form, nombre: e.target.value })}
                placeholder="Ej: Sucursal Centro"
              />
            </label>
            <label className="grid gap-2">
              <span className="text-sm font-medium text-slate-700">Dirección</span>
              <input
                className="w-full border border-slate-300 rounded-xl px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-fuchsia-500 focus:border-transparent"
                value={form.direccion}
                onChange={(e) => setForm({ ...form, direccion: e.target.value })}
                placeholder="Ej: Av. Principal 123"
              />
            </label>
            <label className="grid gap-2 sm:col-span-2">
              <span className="text-sm font-medium text-slate-700">Foto (.jpg/.png) — opcional</span>
              <input
                type="file"
                accept="image/*"
                onChange={(e) => setFile(e.target.files?.[0] || null)}
                className="block w-full text-sm text-slate-600 file:mr-4 file:py-2 file:px-4 file:rounded-lg file:border-0 file:text-sm file:font-medium file:bg-fuchsia-50 file:text-fuchsia-700 hover:file:bg-fuchsia-100 cursor-pointer"
              />
            </label>
          </div>

          <div className="mt-4 flex flex-col sm:flex-row items-stretch sm:items-center gap-2">
            <button
              onClick={save}
              disabled={!canSave || loading}
              className="rounded-xl bg-fuchsia-600 px-4 py-2 text-white disabled:bg-slate-300 disabled:cursor-not-allowed transition-colors"
            >
              {mode.kind === "edit" ? "Guardar cambios" : "Crear"}
            </button>
            <button
              onClick={resetForm}
              className="rounded-xl border border-slate-300 px-4 py-2 hover:bg-slate-50 transition-colors"
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
          {loadingTable && (
            <div className="p-4 text-slate-500 text-sm">Cargando…</div>
          )}

          {!loadingTable && rows.map((s) => (
            <div key={s.id} className="border-b last:border-b-0 p-3">
              <div className="flex items-start gap-3">
                {s.fotoUrl ? (
                  <img
                    src={resolveUrl(s.fotoUrl)}
                    alt={s.nombre}
                    className="h-16 w-16 object-cover rounded-xl border shrink-0"
                  />
                ) : (
                  <div className="h-16 w-16 rounded-xl bg-slate-200 grid place-items-center text-xs text-slate-600 shrink-0">
                    Sin foto
                  </div>
                )}

                <div className="flex-1 min-w-0">
                  <div className="font-medium truncate">{s.nombre}</div>
                  <div className="text-xs text-slate-500 mt-1">
                    📍 {s.direccion}
                  </div>
                  <div className="text-xs text-slate-400 mt-1">ID: {s.id}</div>

                  <div className="flex flex-wrap gap-2 mt-2">
                    <button
                      className="rounded-lg border px-2 py-1 text-xs hover:bg-slate-50"
                      onClick={() => startEdit(s)}
                    >
                      Editar
                    </button>
                    <button
                      className="rounded-lg border px-2 py-1 text-xs text-red-600 border-red-300 hover:bg-red-50"
                      onClick={() => del(Number(s.id))}
                    >
                      Eliminar
                    </button>
                  </div>
                </div>
              </div>
            </div>
          ))}

          {!loadingTable && rows.length === 0 && (
            <div className="p-8 text-center">
              <svg className="w-12 h-12 text-slate-300 mx-auto mb-3" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={1.5} d="M19 21V5a2 2 0 00-2-2H7a2 2 0 00-2 2v16m14 0h2m-2 0h-5m-9 0H3m2 0h5M9 7h1m-1 4h1m4-4h1m-1 4h1m-5 10v-5a1 1 0 011-1h2a1 1 0 011 1v5m-4 0h4" />
              </svg>
              <p className="text-sm text-slate-500 mb-2">Sin sucursales.</p>
              <button
                onClick={startCreate}
                className="text-xs text-fuchsia-600 hover:text-fuchsia-700 font-medium"
              >
                Crear primera sucursal
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
                <th className="px-4 py-3 text-left text-xs font-semibold uppercase">Dirección</th>
                <th className="px-4 py-3 text-right text-xs font-semibold uppercase">Acciones</th>
              </tr>
            </thead>
            <tbody>
              {loadingTable && (
                <tr>
                  <td colSpan={4} className="px-4 py-4 text-slate-500 text-center">
                    Cargando…
                  </td>
                </tr>
              )}

              {!loadingTable && rows.map((s) => (
                <tr key={s.id} className="border-t border-slate-200 hover:bg-slate-50">
                  <td className="px-4 py-3">
                    <span className="font-mono text-xs font-semibold">#{s.id}</span>
                  </td>
                  <td className="px-4 py-3">
                    <div className="font-medium">{s.nombre}</div>
                  </td>
                  <td className="px-4 py-3">
                    <div className="text-slate-600 text-sm">{s.direccion}</div>
                  </td>
                  <td className="px-4 py-3 text-right space-x-2">
                    <button
                      className="rounded-xl border px-3 py-1 hover:bg-slate-50"
                      onClick={() => startEdit(s)}
                    >
                      Editar
                    </button>
                    <button
                      className="rounded-xl border px-3 py-1 text-red-600 border-red-300 hover:bg-red-50"
                      onClick={() => del(Number(s.id))}
                    >
                      Eliminar
                    </button>
                  </td>
                </tr>
              ))}

              {!loadingTable && rows.length === 0 && (
                <tr>
                  <td colSpan={4} className="px-4 py-8 text-slate-500 text-center">
                    <div className="flex flex-col items-center gap-2">
                      <svg className="w-12 h-12 text-slate-300" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                        <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={1.5} d="M19 21V5a2 2 0 00-2-2H7a2 2 0 00-2 2v16m14 0h2m-2 0h-5m-9 0H3m2 0h5M9 7h1m-1 4h1m4-4h1m-1 4h1m-5 10v-5a1 1 0 011-1h2a1 1 0 011 1v5m-4 0h4" />
                      </svg>
                      <p className="text-sm">Sin sucursales.</p>
                      <button
                        onClick={startCreate}
                        className="mt-2 text-xs text-fuchsia-600 hover:text-fuchsia-700 font-medium"
                      >
                        Crear primera sucursal
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

