import SocialLinks from '../components/SocialLinks'
import { Link } from 'react-router-dom'

export default function Inicio() {
  return (
    <section className="relative min-h-screen w-full grid place-items-center overflow-hidden">
      {/* VIDEO DE FONDO - 100% de la pantalla */}
      <video
        className="absolute inset-0 w-full h-full object-cover object-center
                   [filter:grayscale(var(--hero-gray,0.8))_blur(var(--hero-blur,3px))]"
        src="/media/Video-optimized.mp4"
        autoPlay
        muted
        loop
        playsInline
        preload="metadata"
        aria-hidden="true"
      />

      {/* OVERLAYS: oscurecer + degradado para mejor legibilidad */}
      <div className="absolute inset-0 bg-black/45"></div>
      <div className="absolute inset-0 pointer-events-none bg-gradient-to-b from-black/10 via-transparent to-black/60"></div>

      {/* CONTENIDO */}
      <div className="relative z-10 text-center text-white px-4">
        <h1 className="text-4xl md:text-5xl font-bold mb-4 leading-tight">
          Bienvenidos a <span className="text-fuchsia-400">Cromados</span>
        </h1>
        <p className="text-lg text-white/90 max-w-2xl mx-auto mb-8">
          Tu estilo, nuestro compromiso. Reservá tu turno fácil y rápido.
        </p>
        <div className="flex flex-col sm:flex-row items-center justify-center gap-4">
          <Link
            to="/turnos"
            className="px-6 py-3 bg-fuchsia-600 hover:bg-fuchsia-700 rounded-xl font-semibold shadow-lg transition-all"
          >
            Reservar Turno
          </Link>
          <SocialLinks />
        </div>
      </div>

      {/* COPYRIGHT sobre el video */}
      <div className="absolute bottom-0 left-0 right-0 py-6 text-center z-20">
        <p className="text-xs sm:text-sm text-white/90">
          &copy; {new Date().getFullYear()} <span className="font-semibold">Cromados</span>. Todos los derechos reservados.
        </p>
      </div>
    </section>
  )
}

