import { useEffect, useMemo, useState } from "react";
import { BASE, RAW_URL, resolveUrl } from "../lib/adminApi";
import OptimizedImage from "../components/OptimizedImage";

type Barbero = {
  id: number;
  nombre: string;
  sucursalId: number;
  fotoUrl?: string | null;
  instagram?: string | null;
  facebook?: string | null;
};

type Sucursal = { id: number; nombre: string };

export default function Barberos() {
  const [barberos, setBarberos] = useState<Barbero[]>([]);
  const [sucursales, setSucursales] = useState<Sucursal[]>([]);
  const [filtro, setFiltro] = useState<number | "all">("all");
  const [loading, setLoading] = useState(true);
  const [err, setErr] = useState<string | null>(null);

  async function getJson<T>(url: string): Promise<T> {
    const res = await fetch(url);
    if (!res.ok) throw new Error(`HTTP ${res.status}`);
    return res.json();
  }

  useEffect(() => {
    let alive = true;
    (async () => {
      setLoading(true);
      setErr(null);
      try {
        const [bs, ss] = await Promise.all([
          getJson<any>(`${BASE}/barberos`),
          getJson<any>(`${BASE}/sucursales`),
        ]);
        const listB: Barbero[] = Array.isArray(bs?.content) ? bs.content : (Array.isArray(bs) ? bs : []);
        const listS: Sucursal[] = Array.isArray(ss) ? ss : [];
        if (!alive) return;
        setBarberos(listB);
        setSucursales(listS);
      } catch (e: any) {
        if (!alive) return;
        setErr(e?.message || "Error cargando barberos");
      } finally {
        if (alive) setLoading(false);
      }
    })();
    return () => { alive = false; };
  }, []);

  const nombreSucursal = (id: number) =>
    sucursales.find(s => s.id === id)?.nombre || "—";

  const imgSrc = (b: Barbero) => {
    if (b.fotoUrl) return `${resolveUrl(b.fotoUrl)}?ts=${b.fotoUrl.length}`;
    return `${RAW_URL}/barberos/${b.id}/foto`;
  };

  // Lista a mostrar según filtro
  const listaFiltrada = useMemo(() => {
    if (filtro === "all") return [...barberos].sort((a, b) => a.nombre.localeCompare(b.nombre));
    return barberos.filter(b => b.sucursalId === filtro);
  }, [barberos, filtro]);

  return (
    <div className="max-w-6xl mx-auto px-4 py-8 space-y-6">
      {/* Header responsive */}
      <header className="space-y-4">
        <div>
          <h1 className="text-3xl font-bold">Nuestros barberos</h1>
          <p className="text-slate-600">
            {filtro === "all" ? "Todas las sucursales" : nombreSucursal(Number(filtro))} — {listaFiltrada.length} barbero(s)
          </p>
        </div>

        {/* Filtro moderno con botones tipo pill */}
        <div className="flex flex-wrap gap-2">
          <button
            onClick={() => setFiltro("all")}
            className={`px-4 py-2 rounded-full font-medium text-sm transition-all duration-200 ${
              filtro === "all"
                ? "bg-fuchsia-600 text-white shadow-md"
                : "bg-slate-100 text-slate-700 hover:bg-slate-200"
            }`}
          >
            Todas
          </button>
          {sucursales.map(s => (
            <button
              key={s.id}
              onClick={() => setFiltro(s.id)}
              className={`px-4 py-2 rounded-full font-medium text-sm transition-all duration-200 ${
                filtro === s.id
                  ? "bg-fuchsia-600 text-white shadow-md"
                  : "bg-slate-100 text-slate-700 hover:bg-slate-200"
              }`}
            >
              {s.nombre}
            </button>
          ))}
        </div>
      </header>

      {err && (
        <div className="rounded-lg border border-red-200 bg-red-50 px-3 py-2 text-red-700">
          {err}
        </div>
      )}
      {loading && <p className="text-slate-500">Cargando…</p>}

      {/* UNA grilla para “Todas” */}
      {!loading && filtro === "all" && (
        <div className="grid gap-5 grid-cols-1 sm:grid-cols-2 md:grid-cols-3 xl:grid-cols-4">
          {listaFiltrada.map(b => (
            <CardBarbero
              key={b.id}
              b={b}
              nombreSucursal={nombreSucursal}
              imgSrc={imgSrc(b)}
            />
          ))}
          {listaFiltrada.length === 0 && (
            <p className="text-slate-500">No hay barberos para mostrar.</p>
          )}
        </div>
      )}

      {/* Una sucursal específica */}
      {!loading && filtro !== "all" && (
        <>
          <h2 className="text-xl font-semibold">{nombreSucursal(Number(filtro))}</h2>
          {listaFiltrada.length === 0 ? (
            <p className="text-slate-500">Sin barberos en esta sucursal.</p>
          ) : (
            <div className="grid gap-5 grid-cols-1 sm:grid-cols-2 md:grid-cols-3 xl:grid-cols-4">
              {listaFiltrada.map(b => (
                <CardBarbero
                  key={b.id}
                  b={b}
                  nombreSucursal={nombreSucursal}
                  imgSrc={imgSrc(b)}
                />
              ))}
            </div>
          )}
        </>
      )}
    </div>
  );
}

/** Tarjeta responsive del barbero */
function CardBarbero({
  b,
  nombreSucursal,
}: {
  b: Barbero;
  nombreSucursal: (id: number) => string;
  imgSrc: string;
}) {
  return (
    <article className="rounded-2xl border bg-white p-5 shadow-sm">
      {/* En móvil: columna (img arriba). Desde sm: fila (img izquierda). */}
      <div className="flex flex-col items-center gap-4">
        {b.fotoUrl ? (
          <OptimizedImage
            src={resolveUrl(b.fotoUrl)!}
            alt={`Foto de ${b.nombre}`}
            className="h-28 w-28 md:h-32 md:w-32 rounded-full object-cover border shrink-0"
            width={128}
            height={128}
            loading="lazy"
          />
        ) : (
          <div className="h-28 w-28 md:h-32 md:w-32 rounded-full bg-slate-200 grid place-items-center text-xs text-slate-600 border shrink-0">
            Sin foto
          </div>
        )}
        <div className="text-center w-full">
          <h3 className="font-semibold text-base md:text-lg leading-snug mb-1">{b.nombre}</h3>
          <p className="text-slate-500 text-sm mb-2">{nombreSucursal(b.sucursalId)}</p>
          <div className="flex flex-wrap justify-center gap-x-4 gap-y-1 text-sm">
            {b.instagram && (
              <a
                href={b.instagram}
                target="_blank"
                rel="noreferrer"
                className="text-fuchsia-600 hover:underline"
              >
                Instagram
              </a>
            )}
            {b.facebook && (
              <a
                href={b.facebook}
                target="_blank"
                rel="noreferrer"
                className="text-fuchsia-600 hover:underline"
              >
                Facebook
              </a>
            )}
          </div>
        </div>
      </div>
    </article>
  );
}

