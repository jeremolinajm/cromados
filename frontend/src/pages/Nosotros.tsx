// src/pages/Nosotros.tsx
export default function Nosotros() {
  return (
    <div className="min-h-screen bg-white">
      {/* Hero corto */}
      <section className="max-w-5xl mx-auto px-4 sm:px-6 pt-6 sm:pt-8 pb-6 sm:pb-8 text-center">
        <h1 className="text-3xl sm:text-4xl lg:text-5xl font-bold tracking-tight">
          Conocé <span className="text-indigo-600">Cromados</span>
        </h1>
        <p className="mt-3 sm:mt-4 text-base sm:text-lg text-slate-600 max-w-2xl mx-auto px-2">
          Una barbería de barrio con corazón, detalle y oficio. Un lugar pensado para disfrutar el momento.
        </p>
      </section>

      {/* Dueños / fundadores */}
      <section className="max-w-7xl mx-auto px-4 sm:px-6 py-8 sm:py-10">
        <div className="grid gap-4 sm:gap-6 lg:grid-cols-[1fr_1fr_1.5fr] items-start">
          {/* Foto vertical */}
          <div className="rounded-2xl overflow-hidden bg-slate-200 aspect-[3/4]">
            <img
              src="/fundadores-main.jpg"
              alt="Fundadores de Cromados"
              className="w-full h-full object-cover"
              onError={(e) => {
                (e.currentTarget as HTMLImageElement).src = "/placeholder-wide.jpg";
              }}
            />
          </div>

          {/* Video vertical en bucle sin controles */}
          <div className="rounded-2xl overflow-hidden bg-slate-200 aspect-[3/4]">
            <video
              className="w-full h-full object-cover"
              autoPlay
              loop
              muted
              playsInline
            >
              <source src="/video-cromados.mp4" type="video/mp4" />
            </video>
          </div>

          {/* Texto de presentación a la derecha */}
          <div className="px-2">
            <h2 className="text-xl sm:text-2xl font-semibold">Quiénes somos</h2>
            <p className="mt-3 sm:mt-4 text-sm sm:text-base text-slate-700 leading-relaxed">
              Somos <span className="font-medium">Facu y Bruno</span>, fundadores de Cromados.
              Empezamos con una idea simple: ofrecer una experiencia de barbería honesta, moderna y cómoda,
              donde cada visita se sienta como una pausa en el día.
            </p>
            <p className="mt-3 text-sm sm:text-base text-slate-700 leading-relaxed">
              En nuestro espacio vas a encontrar <span className="font-medium">técnica, escucha y detalle</span>.
              Creemos en el trato cercano y en construir una relación a largo plazo con cada cliente.
            </p>

            {/* Datos breves / highlights */}
            <div className="mt-5 sm:mt-6 grid grid-cols-3 gap-2 sm:gap-3">
              <div className="rounded-xl sm:rounded-2xl border bg-white p-3 sm:p-4">
                <div className="text-xs sm:text-sm text-slate-500">Desde</div>
                <div className="text-lg sm:text-xl font-semibold">2017</div>
              </div>
              <div className="rounded-xl sm:rounded-2xl border bg-white p-3 sm:p-4">
                <div className="text-xs sm:text-sm text-slate-500">Especialidad</div>
                <div className="text-sm sm:text-xl font-semibold leading-tight">Fades & Barbas</div>
              </div>
              <div className="rounded-xl sm:rounded-2xl border bg-white p-3 sm:p-4">
                <div className="text-xs sm:text-sm text-slate-500">Ciudad</div>
                <div className="text-sm sm:text-xl font-semibold leading-tight">Alta Gracia</div>
              </div>
            </div>

            {/* Valores */}
            <div className="mt-5 sm:mt-6">
              <h3 className="font-semibold text-base sm:text-lg">Nuestros valores</h3>
              <ul className="mt-3 space-y-2 text-sm sm:text-base text-slate-700">
                <li>• Atención personalizada y honesta.</li>
                <li>• Puntualidad y respeto por tu tiempo.</li>
                <li>• Detalle técnico en cada corte y barba.</li>
                <li>• Ambiente cálido y relajado.</li>
              </ul>
            </div>
          </div>
        </div>
      </section>

      {/* Franja de texto (opcional) */}
      <section className="px-4 sm:px-6">
        <div className="max-w-4xl mx-auto rounded-2xl sm:rounded-3xl bg-indigo-50 border border-indigo-100 p-5 sm:p-6 text-center">
          <p className="text-sm sm:text-base text-slate-700 leading-relaxed">
            "Cromados es nuestro lugar en el mundo. Queremos que también sea el tuyo cada vez que te sientes en la silla."
          </p>
        </div>
      </section>

      {/* CTA final */}
      <section className="py-12 sm:py-16 text-center px-4">
        <h2 className="text-xl sm:text-2xl font-semibold mb-3 sm:mb-4">¿Listo para tu próximo look?</h2>
        <p className="text-sm sm:text-base text-slate-600 mb-5 sm:mb-6 max-w-md mx-auto">
          Reservá tu turno online y viví la experiencia Cromados.
        </p>
        <a
          href="/turnos"
          className="inline-block rounded-xl bg-indigo-600 px-6 py-3 text-sm sm:text-base text-white font-semibold hover:bg-indigo-700 transition"
        >
          Reservar turno
        </a>
      </section>
    </div>
  );
}
