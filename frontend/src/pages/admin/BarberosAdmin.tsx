import { useEffect, useMemo, useState } from "react";
import { AdminApi, type BarberoDTO, type SucursalDTO, resolveUrl} from "../../lib/adminApi";
import { useNavigate } from "react-router-dom";

type Mode =
  | { kind: "idle" }
  | { kind: "create" }
  | { kind: "edit"; id: number; currentFotoUrl?: string | null };

const EMPTY: BarberoDTO & { telefono?: string } & { telegramChatId?: string } = {
  nombre: "",
  sucursalId: 0,
  instagram: "",
  facebook: "",
  telefono: "",
  telegramChatId: "",
};

export default function BarberosAdmin() {
  const [rows, setRows] = useState<(BarberoDTO & { telefono?: string } & { telegramChatId?: string })[]>([]);
  const [sucursales, setSucursales] = useState<SucursalDTO[]>([]);
  const [mode, setMode] = useState<Mode>({ kind: "idle" });
  const [form, setForm] = useState<BarberoDTO & { telefono?: string } & { telegramChatId?: string }>({ ...EMPTY });
  const [file, setFile] = useState<File | null>(null);
  const [preview, setPreview] = useState<string | null>(null);

  const [loading, setLoading] = useState(false);
  const [loadingTable, setLoadingTable] = useState(false);
  const [error, setError] = useState<string | null>(null);
  
  const navigate = useNavigate();

  const loadAll = async () => {
    setLoadingTable(true);
    setError(null);
    try {
      const [barPage, sucs] = await Promise.all([
        AdminApi.listBarberos(0, 200, "id,asc"),
        AdminApi.sucursales(),
      ]);
      const list = Array.isArray(barPage?.content) ? (barPage.content as (BarberoDTO & { telefono?: string })[]): [];
      setRows(list);
      setSucursales(Array.isArray(sucs) ? sucs : []);
    } catch (e: any) {
      setError(e?.message || "Error cargando barberos");
      setRows([]);
    } finally {
      setLoadingTable(false);
    }
  };

  useEffect(() => {
    loadAll();
    return () => {
      if (preview) URL.revokeObjectURL(preview);
    };
  }, []);

  const canSave = useMemo(
    () => form.nombre.trim() !== "" && Number(form.sucursalId) > 0,
    [form]
  );

  const onSelectFile = (f: File | null) => {
    setFile(f);
    if (preview) URL.revokeObjectURL(preview);
    setPreview(f ? URL.createObjectURL(f) : null);
  };

  const resetForm = () => {
    setForm({ ...EMPTY });
    onSelectFile(null);
    setMode({ kind: "idle" });
  };

  const startCreate = () => {
    setForm({ ...EMPTY });
    onSelectFile(null);
    setMode({ kind: "create" });
  };

  const startEdit = (b: BarberoDTO & { telefono?: string } & { telegramChatId?: string }) => {
    setForm({
      id: b.id,
      nombre: b.nombre || "",
      sucursalId: b.sucursalId || 0,
      instagram: b.instagram || "",
      facebook: b.facebook || "",
      telefono: (b as any).telefono || "",
      fotoUrl: b.fotoUrl || "",
      telegramChatId: String(b.telegramChatId || ""),
    });
    onSelectFile(null);
    setPreview(null);
    setMode({ kind: "edit", id: Number(b.id), currentFotoUrl: b.fotoUrl || null });
  };

  const normalizePhone = (s?: string) =>
    (s ?? "").replace(/\s+/g, " ").trim();

  const save = async () => {
    if (!canSave) return;
    setLoading(true);
    setError(null);
    try {
      const telefono = normalizePhone(form.telefono);
      
      // ✅ Preparar payload correctamente
      const chatIdRaw = form.telegramChatId?.trim();
      const chatIdNumber = chatIdRaw && /^\d+$/.test(chatIdRaw) 
        ? Number(chatIdRaw) 
        : null;

      const payload = {
        nombre: form.nombre,
        sucursalId: Number(form.sucursalId),
        instagram: form.instagram || "",
        facebook: form.facebook || "",
        telefono: telefono || "",
        telegramChatId: chatIdNumber
      };

      console.log('[BarberosAdmin] Guardando con payload:', payload);

      let saved: BarberoDTO;

      if (mode.kind === "edit" && mode.id) {
        saved = await AdminApi.updateBarbero(mode.id, payload as any);
      } else {
        saved = await AdminApi.createBarbero(payload as any);
      }

      if (file && saved?.id) {
        await AdminApi.uploadBarberoFoto(Number(saved.id), file);
      }

      resetForm();
      await loadAll();
    } catch (e: any) {
      console.error('[BarberosAdmin] Error:', e);
      setError(e?.message || "Error guardando barbero");
    } finally {
      setLoading(false);
    }
  };

  const del = async (id?: number) => {
    if (!id) return;
    if (!confirm("¿Eliminar barbero?")) return;
    setLoading(true);
    setError(null);
    try {
      await AdminApi.deleteBarbero(id);
      await loadAll();
    } catch (e: any) {
      setError(e?.message || "Error eliminando barbero");
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="space-y-4 sm:space-y-6">
      {/* Header responsive */}
      <header className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-3">
        <div>
          <h1 className="text-xl sm:text-2xl font-semibold">Barberos</h1>
          <p className="text-sm sm:text-base text-slate-600">Alta, edición, foto y baja.</p>
        </div>
        {mode.kind === "idle" && (
          <button
            onClick={startCreate}
            className="inline-flex items-center gap-2 rounded-xl bg-gradient-to-r from-fuchsia-600 to-fuchsia-700 px-4 py-2.5 text-white font-medium shadow-md hover:shadow-lg hover:from-fuchsia-700 hover:to-fuchsia-800 transition-all"
          >
            <svg className="w-5 h-5" fill="none" viewBox="0 0 24 24" stroke="currentColor">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 4v16m8-8H4" />
            </svg>
            Nuevo barbero
          </button>
        )}
      </header>

      {error && (
        <div className="rounded-lg border border-red-200 bg-red-50 px-3 py-2 text-sm text-red-700">
          {error}
        </div>
      )}

      {/* Formulario responsive */}
      {mode.kind !== "idle" && (
        <div className="rounded-2xl border border-slate-200 bg-white p-3 sm:p-4 shadow-sm">
          <div className="grid gap-3 grid-cols-1 sm:grid-cols-2">
            <label className="grid gap-1">
              <span className="text-sm text-slate-600">Nombre</span>
              <input
                className="border rounded-xl px-3 py-2 text-sm sm:text-base"
                value={form.nombre}
                onChange={(e) => setForm({ ...form, nombre: e.target.value })}
              />
            </label>

            <label className="grid gap-1">
              <span className="text-sm text-slate-600">Sucursal</span>
              <select
                className="border rounded-xl px-3 py-2 text-sm sm:text-base"
                value={form.sucursalId || 0}
                onChange={(e) =>
                  setForm({ ...form, sucursalId: Number(e.target.value) })
                }
              >
                <option value={0}>Seleccionar…</option>
                {sucursales.map((s) => (
                  <option key={s.id} value={s.id}>
                    {s.nombre}
                  </option>
                ))}
              </select>
            </label>

            <label className="grid gap-1">
              <span className="text-sm text-slate-600">Teléfono (WhatsApp)</span>
              <input
                type="tel"
                className="border rounded-xl px-3 py-2 text-sm sm:text-base"
                value={form.telefono ?? ""}
                onChange={(e) => setForm({ ...form, telefono: e.target.value })}
                placeholder="+54 9 351 123 4567"
              />
              <span className="text-xs text-slate-500">
                Formato internacional (E.164), ej. +549351...
              </span>
            </label>

            <label className="grid gap-1">
              <span className="text-sm text-slate-600">Telegram Chat ID</span>
              <input
                type="text"
                className="border rounded-xl px-3 py-2 text-sm sm:text-base"
                value={form.telegramChatId || ""}
                onChange={(e) => {
                  // Solo permitir dígitos
                  const val = e.target.value.replace(/\D/g, "");
                  setForm({ ...form, telegramChatId: val });
                }}
                placeholder="123456789"
              />
              <span className="text-xs text-slate-500">
                ID numérico del chat (enviar /start al bot)
              </span>
            </label>

            <label className="grid gap-1">
              <span className="text-sm text-slate-600">Instagram (opcional)</span>
              <input
                className="border rounded-xl px-3 py-2 text-sm sm:text-base"
                value={form.instagram || ""}
                onChange={(e) =>
                  setForm({ ...form, instagram: e.target.value })
                }
                placeholder="@usuario"
              />
            </label>
            
            <label className="grid gap-1">
              <span className="text-sm text-slate-600">Facebook (opcional)</span>
              <input
                className="border rounded-xl px-3 py-2 text-sm sm:text-base"
                value={form.facebook || ""}
                onChange={(e) =>
                  setForm({ ...form, facebook: e.target.value })
                }
              />
            </label>
            <label className="grid gap-2 sm:col-span-2">
              <span className="text-sm font-medium text-slate-700">Foto (.jpg/.png) — opcional</span>
              <input
                type="file"
                accept="image/*"
                onChange={(e) => onSelectFile(e.target.files?.[0] || null)}
                className="block w-full text-sm text-slate-600 file:mr-4 file:py-2 file:px-4 file:rounded-lg file:border-0 file:text-sm file:font-medium file:bg-fuchsia-50 file:text-fuchsia-700 hover:file:bg-fuchsia-100 cursor-pointer"
              />

              {(() => {
                const fotoSrc =
                  preview ??
                  (mode.kind === "edit" && mode.currentFotoUrl
                    ? resolveUrl(mode.currentFotoUrl)
                    : null);

                if (!fotoSrc) return null;

                return (
                  <div className="mt-2">
                    <img
                      src={fotoSrc}
                      alt="preview"
                      className="h-24 w-24 sm:h-32 sm:w-32 object-cover rounded-xl border"
                    />
                  </div>
                );
              })()}
            </label>
          </div>

          <div className="mt-4 flex flex-col sm:flex-row items-stretch sm:items-center gap-2">
            <button
              onClick={save}
              disabled={!canSave || loading}
              className="rounded-xl bg-fuchsia-600 px-4 py-2 text-white disabled:bg-slate-300 text-sm sm:text-base"
            >
              {mode.kind === "edit" ? "Guardar cambios" : "Crear"}
            </button>
            <button
              onClick={resetForm}
              className="rounded-xl border border-slate-300 px-4 py-2 text-sm sm:text-base"
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
          
          {!loadingTable && rows.map((b) => (
            <div key={b.id} className="border-b last:border-b-0 p-3">
              <div className="flex items-start gap-3">
                {b.fotoUrl ? (
                  <img
                    src={resolveUrl(b.fotoUrl)!}
                    alt={b.nombre}
                    className="h-16 w-16 object-cover rounded-full border shrink-0"
                  />
                ) : (
                  <div className="h-16 w-16 rounded-full bg-slate-200 grid place-items-center text-xs text-slate-600 shrink-0">
                    Sin foto
                  </div>
                )}
                
                <div className="flex-1 min-w-0">
                  <div className="font-medium truncate">{b.nombre}</div>
                  <div className="text-xs text-slate-500">
                    {sucursales.find((s) => s.id === b.sucursalId)?.nombre || "-"}
                  </div>
                  {(b as any).telefono && (
                    <div className="text-xs text-slate-500 mt-1">
                      📱 {(b as any).telefono}
                    </div>
                  )}
                  {b.telegramChatId && (
                    <div className="text-xs text-slate-500">
                      💬 {b.telegramChatId}
                    </div>
                  )}
                  
                  <div className="flex flex-wrap gap-2 mt-2">
                    <button
                      className="rounded-lg border px-2 py-1 text-xs"
                      onClick={() => startEdit(b)}
                    >
                      Editar
                    </button>
                    <button
                      className="rounded-lg border px-2 py-1 text-xs"
                      onClick={() => navigate(`/admin/horarios?barberoId=${Number(b.id)}`)}
                    >
                      Horarios
                    </button>
                    <button
                      className="rounded-lg border px-2 py-1 text-xs text-red-600 border-red-300"
                      onClick={() => del(Number(b.id))}
                    >
                      Eliminar
                    </button>
                  </div>
                </div>
              </div>
            </div>
          ))}

          {!loadingTable && rows.length === 0 && (
            <div className="p-4 text-slate-500 text-sm">Sin barberos.</div>
          )}
        </div>

        {/* Vista desktop: tabla */}
        <div className="hidden md:block overflow-x-auto">
          <table className="min-w-full text-sm">
            <thead className="text-slate-600">
              <tr>
                <th className="px-4 py-2 text-left">ID</th>
                <th className="px-4 py-2 text-left">Foto</th>
                <th className="px-4 py-2 text-left">Nombre</th>
                <th className="px-4 py-2 text-left">Sucursal</th>
                <th className="px-4 py-2 text-left">Teléfono</th>
                <th className="px-4 py-2 text-left">Telegram</th>
                <th className="px-4 py-2"></th>
              </tr>
            </thead>
            <tbody>
              {loadingTable && (
                <tr>
                  <td colSpan={7} className="px-4 py-4 text-slate-500">
                    Cargando…
                  </td>
                </tr>
              )}

              {!loadingTable && rows.map((b) => (
                <tr key={b.id} className="border-t border-slate-200">
                  <td className="px-4 py-2">{b.id}</td>
                  <td className="px-4 py-2">
                    {b.fotoUrl ? (
                      <img
                        src={resolveUrl(b.fotoUrl)!}
                        alt={b.nombre}
                        className="h-12 w-12 object-cover rounded-full border"
                      />
                    ) : (
                      <div className="h-12 w-12 rounded-full bg-slate-200 grid place-items-center text-xs text-slate-600">
                        Sin foto
                      </div>
                    )}
                  </td>
                  <td className="px-4 py-2">{b.nombre}</td>
                  <td className="px-4 py-2">
                    {sucursales.find((s) => s.id === b.sucursalId)?.nombre || "-"}
                  </td>
                  <td className="px-4 py-2">{(b as any).telefono ?? "–"}</td>
                  <td className="px-4 py-2">{b.telegramChatId || "–"}</td>
                  <td className="px-4 py-2 text-right space-x-2">
                    <button
                      className="rounded-xl border px-3 py-1"
                      onClick={() => startEdit(b)}
                    >
                      Editar
                    </button>
                    <button
                      className="rounded-xl border px-3 py-1"
                      onClick={() => navigate(`/admin/horarios?barberoId=${Number(b.id)}`)}
                    >
                      Horarios
                    </button>
                    <button
                      className="rounded-xl border px-3 py-1 text-red-600 border-red-300"
                      onClick={() => del(Number(b.id))}
                    >
                      Eliminar
                    </button>
                  </td>
                </tr>
              ))}

              {!loadingTable && rows.length === 0 && (
                <tr>
                  <td colSpan={7} className="px-4 py-4 text-slate-500">
                    Sin barberos.
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