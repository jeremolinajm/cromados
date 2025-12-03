// src/App.tsx
import { Suspense, lazy } from "react";
import { Routes, Route, Navigate, useLocation } from "react-router-dom";
import Navbar from "./components/Navbar";
import Footer from "./components/Footer";
import PagoResult from "./components/PagoResult";

// Páginas públicas
const HomePage = lazy(() => import("./pages/Inicio"));
const TurnosPage = lazy(() => import("./pages/Turnos"));
const BarberosPage = lazy(() => import("./pages/Barberos"));
const NosotrosPage = lazy(() => import("./pages/Nosotros"));
const PrivacidadPage = lazy(() => import("./pages/Privacidad"));

// Admin: componente propio (NO lib/adminApi)
const AdminApp = lazy(() => import("./pages/admin/AdminApp"));

function PublicLayout({ children }: { children: React.ReactNode }) {
  const location = useLocation();
  const isHome = location.pathname === "/";

  return (
    <div className="min-h-screen flex flex-col">
      <Navbar />
      {/* Sin padding-top en home para que el video ocupe toda la pantalla */}
      <main className={`flex-1 ${isHome ? '' : 'pt-20'}`}>{children}</main>
      {/* Footer solo en páginas que no son home (home tiene copyright integrado) */}
      {!isHome && <Footer />}
    </div>
  );
}

export default function App() {
  const location = useLocation();
  const isAdmin = location.pathname.startsWith("/admin");

  return (
    <Suspense fallback={<div className="p-6 text-center">Cargando…</div>}>
      <Routes>
        {/* Rutas públicas con layout + Navbar */}
        <Route
          path="/"
          element={
            <PublicLayout>
              <HomePage />
            </PublicLayout>
          }
        />
        <Route
          path="/turnos"
          element={
            <PublicLayout>
              <TurnosPage />
            </PublicLayout>
          }
        />
        <Route
          path="/barberos"
          element={
            <PublicLayout>
              <BarberosPage />
            </PublicLayout>
          }
        />
        <Route
          path="/nosotros"
          element={
            <PublicLayout>
              <NosotrosPage />
            </PublicLayout>
          }
        />
        <Route
          path="/privacidad"
          element={
            <PublicLayout>
              <PrivacidadPage />
            </PublicLayout>
          }
        />

        {/* Panel admin con su propio layout */}
        <Route path="/admin/*" element={<AdminApp />} />

        {/* 404 */}
        <Route path="*" element={<Navigate to={isAdmin ? "/admin" : "/"} replace />} />
        <Route path="/pago/success" element={<PagoResult kind="success" />} />
        <Route path="/pago/pending" element={<PagoResult kind="pending" />} />
        <Route path="/pago/failure" element={<PagoResult kind="failure" />} />
      </Routes>
    </Suspense>
  );
}
