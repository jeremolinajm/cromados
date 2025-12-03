import { useEffect, useState } from "react";
import { useToast } from "../components/Toast";
import { endpoints } from "../lib/endpoints";
import { GET } from "../lib/http";
import { resolveUrl } from "../lib/adminApi";
import OptimizedImage from "./OptimizedImage";

type Sucursal = { id: number; nombre: string; direccion: string; fotoUrl?: string | null };

type Props = {
  onSelect: (sucursalId: number) => void;
};

export default function IntroSucursales({ onSelect }: Props) {
  const toast = useToast();
  const [loading, setLoading] = useState(true);
  const [sucursales, setSucursales] = useState<Sucursal[]>([]);

  useEffect(() => {
    (async () => {
      try {
        const data = await GET<Sucursal[]>(endpoints.sucursales());
        setSucursales(Array.isArray(data) ? data : []);
      } catch {
        toast("No se pudieron cargar las sucursales.");
      } finally {
        setLoading(false);
      }
    })();
  }, [toast]);

  return (
    <div className="fixed inset-0 z-40 bg-black/50">
      <div className="absolute inset-0 overflow-y-auto pt-24 pb-8">
        <div className="mx-auto max-w-6xl px-4">
          {loading ? (
            <div className="text-white/90">Cargando sucursales…</div>
          ) : (
            <div className={`grid gap-5 ${sucursales.length === 1 ? 'grid-cols-1 max-w-xl mx-auto' : 'md:grid-cols-2'}`}>
              {sucursales.map((s) => (
                <div key={s.id} className="rounded-2xl overflow-hidden bg-white shadow-sm">
                  {/* imagen grande 16:9 */}
                  <div className="aspect-[16/9] bg-slate-100">
                    {s.fotoUrl ? (
                      <OptimizedImage
                        src={resolveUrl(s.fotoUrl)}
                        alt={`Foto de ${s.nombre}`}
                        className="h-full w-full object-cover"
                        width={1200}
                        height={675}
                        loading="lazy"
                        onError={(e) => {
                          (e.currentTarget as HTMLImageElement).src = "/placeholder-wide.jpg";
                        }}
                      />
                    ) : (
                      <div className="h-full w-full object-cover bg-slate-200 grid place-items-center text-s text-slate-600 border shrink-0">
                        Sin foto
                      </div>
                    )}
                  </div>

                  <div className="p-5">
                    <h3 className="text-xl font-semibold">{s.nombre}</h3>
                    <p className="text-slate-600 mt-1">{s.direccion}</p>

                    <div className="mt-4 flex gap-3">
                      <a
                        className="rounded-lg border px-3 py-2"
                        href={`https://www.google.com/maps/search/?api=1&query=${encodeURIComponent(
                          s.direccion || s.nombre
                        )}`}
                        target="_blank"
                        rel="noreferrer"
                      >
                        Cómo llegar
                      </a>
                      <button
                        className="rounded-lg bg-fuchsia-600 px-3 py-2 text-white"
                        onClick={() => onSelect(s.id)}
                      >
                        Elegir esta sucursal
                      </button>
                    </div>
                  </div>
                </div>
              ))}
              {sucursales.length === 0 && (
                <div className="rounded-xl bg-white p-4">No hay sucursales disponibles.</div>
              )}
            </div>
          )}
        </div>
      </div>
    </div>
  );
}
