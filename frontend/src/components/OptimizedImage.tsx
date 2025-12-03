/**
 * Componente de imagen optimizada con soporte para srcset y lazy loading.
 * Genera automáticamente las URLs de las diferentes versiones (400px, 800px, 1200px)
 * basándose en la convención de nombres del backend.
 */

interface OptimizedImageProps {
  /** URL base de la imagen (ej: "/uploads/barberos/barbero-1-123456.webp") */
  src: string;
  /** Texto alternativo para accesibilidad */
  alt: string;
  /** Clases CSS adicionales */
  className?: string;
  /** Ancho de la imagen en píxeles (para evitar CLS - Cumulative Layout Shift) */
  width?: number;
  /** Alto de la imagen en píxeles (para evitar CLS) */
  height?: number;
  /** Si debe usar lazy loading (default: true) */
  loading?: "lazy" | "eager";
  /** Función que se ejecuta si hay error al cargar */
  onError?: (e: React.SyntheticEvent<HTMLImageElement, Event>) => void;
}

/**
 * Componente de imagen optimizada con srcset responsivo.
 *
 * Genera automáticamente las diferentes versiones de la imagen:
 * - 400px para móviles (thumbnail)
 * - 800px para tablets (medium)
 * - 1200px para desktop (large)
 *
 * Ejemplo de uso:
 * ```tsx
 * <OptimizedImage
 *   src="/uploads/barberos/barbero-1-123456.webp"
 *   alt="Foto del barbero"
 *   width={800}
 *   height={800}
 *   className="rounded-full"
 * />
 * ```
 */
export default function OptimizedImage({
  src,
  alt,
  className = "",
  width,
  height,
  loading = "lazy",
  onError,
}: OptimizedImageProps) {
  // Si la imagen ya tiene un sufijo de tamaño (-400, -800, -1200), usar directamente
  if (src.match(/-(400|800|1200)\.(webp|jpg|jpeg|png)$/)) {
    return (
      <img
        src={src}
        alt={alt}
        className={className}
        width={width}
        height={height}
        loading={loading}
        onError={onError}
      />
    );
  }

  // Generar versiones de la imagen
  const { thumbnail, medium, large, original } = generateImageVersions(src);

  return (
    <img
      src={original}
      srcSet={`
        ${thumbnail} 400w,
        ${medium} 800w,
        ${large} 1200w
      `}
      sizes="(max-width: 640px) 400px, (max-width: 1024px) 800px, 1200px"
      alt={alt}
      className={className}
      width={width}
      height={height}
      loading={loading}
      onError={onError}
    />
  );
}

/**
 * Genera las URLs de las diferentes versiones de una imagen.
 *
 * Convención de nombres:
 * - Original: barbero-1-123456.jpg
 * - Thumbnail: barbero-1-123456-400.jpg
 * - Medium: barbero-1-123456-800.jpg
 * - Large: barbero-1-123456-1200.jpg
 */
function generateImageVersions(src: string) {
  // Remover extensión y obtener base
  const lastDot = src.lastIndexOf('.');
  const base = src.substring(0, lastDot);
  const ext = src.substring(lastDot);

  // Usar JPG optimizado
  const jpgExt = ext.toLowerCase().match(/\.(jpg|jpeg)$/) ? ext : '.jpg';

  return {
    thumbnail: `${base}-400${jpgExt}`,
    medium: `${base}-800${jpgExt}`,
    large: `${base}-1200${jpgExt}`,
    original: `${base}${jpgExt}`,
  };
}

/**
 * Hook para generar las URLs de las versiones de imagen.
 * Útil si necesitas las URLs directamente sin renderizar el componente.
 */
export function useImageVersions(src: string) {
  return generateImageVersions(src);
}
