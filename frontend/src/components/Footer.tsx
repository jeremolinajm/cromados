import { useLocation, Link } from "react-router-dom";

export default function Footer() {
  const currentYear = new Date().getFullYear();
  const location = useLocation();

  // Usar texto blanco en la página de inicio (que tiene video de fondo oscuro)
  const isHome = location.pathname === "/";
  const textColor = isHome ? "text-white/90" : "text-gray-900";
  const nameColor = isHome ? "text-white" : "text-black";
  const linkColor = isHome ? "text-white/80 hover:text-white" : "text-gray-600 hover:text-gray-900";

  return (
    <footer className="py-6 text-center">
      <p className={`text-xs sm:text-sm ${textColor} mb-2`}>
        &copy; {currentYear} <span className={`font-semibold ${nameColor}`}>Cromados</span>. Todos los derechos reservados.
      </p>
      <p className="text-xs">
        <Link to="/privacidad" className={`${linkColor} underline transition-colors`}>
          Política de Privacidad
        </Link>
      </p>
    </footer>
  );
}

