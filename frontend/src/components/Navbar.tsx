import { useState, useEffect } from "react";
import { NavLink } from "react-router-dom";

const links = [
  { to: "/", label: "Inicio" },
  { to: "/barberos", label: "Barberos" },
  { to: "/nosotros", label: "Nosotros" },
];

export default function Navbar() {
  const [open, setOpen] = useState(false);
  const [scrolled, setScrolled] = useState(false);

  // Detectar scroll para efecto glassmorphism dinámico
  useEffect(() => {
    const handleScroll = () => {
      setScrolled(window.scrollY > 20);
    };
    window.addEventListener("scroll", handleScroll);
    return () => window.removeEventListener("scroll", handleScroll);
  }, []);

  const base = "px-4 py-2 rounded-full font-medium transition-all duration-300 text-sm";
  const active = "text-white bg-white/20";
  const inactive = "text-gray-300 hover:text-white hover:bg-white/10";

  return (
    <header className="fixed top-0 inset-x-0 z-50 text-white">
      <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 pt-4 lg:pt-6">
        {/* Desktop: Navbar flotante estilo pill */}
        <div className="hidden lg:flex items-center justify-center">
          <nav className={`flex items-center gap-1 px-2 py-2 rounded-full transition-all duration-300 ${
            scrolled
              ? "bg-black/90 backdrop-blur-xl shadow-2xl"
              : "bg-black/70 backdrop-blur-md"
          }`}>
            {links.map((l) => (
              <NavLink
                key={l.to}
                to={l.to}
                end={l.to === "/"}
                className={({ isActive }) =>
                  `${base} ${isActive ? active : inactive}`
                }
              >
                {l.label}
              </NavLink>
            ))}
            <NavLink
              to="/turnos"
              className="ml-1 px-5 py-2 rounded-full font-semibold bg-fuchsia-600 hover:bg-fuchsia-700 text-white transition-all duration-300 text-sm shadow-lg shadow-fuchsia-500/30"
            >
              Reservar Turno
            </NavLink>
          </nav>
        </div>

        {/* Mobile: Navbar compacto */}
        <div className={`lg:hidden flex items-center justify-between px-4 py-3 rounded-full transition-all duration-300 ${
          scrolled
            ? "bg-black/90 backdrop-blur-xl shadow-2xl"
            : "bg-black/70 backdrop-blur-md"
        }`}>
          {/* Logo */}
          <NavLink to="/" className="flex items-center" onClick={() => setOpen(false)}>
            <img
              src="/logo.png"
              alt="Cromados"
              className="h-9 w-9 rounded-lg object-cover"
              onError={(e) => {
                (e.currentTarget as HTMLImageElement).src = "/placeholder-avatar.png";
              }}
            />
          </NavLink>

          {/* Botón hamburguesa */}
          <button
            type="button"
            aria-label="Abrir menú"
            aria-expanded={open}
            onClick={() => setOpen((v) => !v)}
            className="inline-flex items-center justify-center w-9 h-9 rounded-full hover:bg-white/10 focus:outline-none transition-all duration-200"
          >
            <svg
              className={`${open ? "hidden" : "block"} h-5 w-5`}
              viewBox="0 0 24 24"
              fill="none"
              stroke="currentColor"
              strokeWidth="2"
              strokeLinecap="round"
            >
              <path d="M4 6h16M4 12h16M4 18h16" />
            </svg>
            <svg
              className={`${open ? "block" : "hidden"} h-5 w-5`}
              viewBox="0 0 24 24"
              fill="none"
              stroke="currentColor"
              strokeWidth="2"
              strokeLinecap="round"
            >
              <path d="M6 18L18 6M6 6l12 12" />
            </svg>
          </button>
        </div>
      </div>

      {/* Menú móvil desplegable - dropdown compacto */}
      {open && (
        <div className="lg:hidden px-4 sm:px-6 mt-3">
          <div className={`rounded-2xl overflow-hidden shadow-2xl transition-all duration-300 ${
            scrolled
              ? "bg-black/90 backdrop-blur-xl"
              : "bg-black/80 backdrop-blur-lg"
          }`}>
            <nav className="py-2 px-2">
              <ul className="space-y-1">
                {links.map((l) => (
                  <li key={l.to}>
                    <NavLink
                      to={l.to}
                      end={l.to === "/"}
                      onClick={() => setOpen(false)}
                      className={({ isActive }) =>
                        `block px-4 py-2 rounded-full font-medium transition-all duration-200 text-center ${
                          isActive
                            ? "text-white bg-white/20"
                            : "text-gray-300 hover:text-white hover:bg-white/10"
                        }`
                      }
                    >
                      {l.label}
                    </NavLink>
                  </li>
                ))}

                {/* Separador */}
                <li className="px-4 py-2">
                  <div className="border-t border-white/10"></div>
                </li>

                {/* Botón CTA en mobile */}
                <li className="px-4 pb-2">
                  <NavLink
                    to="/turnos"
                    onClick={() => setOpen(false)}
                    className="block px-6 py-3 rounded-xl font-semibold bg-fuchsia-600 hover:bg-fuchsia-700 text-white text-center shadow-lg shadow-fuchsia-500/30 transition-all duration-300"
                  >
                    Reservar Turno
                  </NavLink>
                </li>
              </ul>
            </nav>
          </div>
        </div>
      )}
    </header>
  );
}
