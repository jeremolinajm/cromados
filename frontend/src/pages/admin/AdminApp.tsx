// src/pages/admin/AdminApp.tsx
import { Routes, Route, Navigate } from "react-router-dom";
import { useAuth } from "../../contexts/AuthContext";
import AdminLayout from "./AdminLayout";
import Dashboard from "./Dashboard";
import BarberosAdmin from "./BarberosAdmin";
import SucursalesAdmin from "./SucursalesAdmin";
import ServiciosAdmin from "./ServiciosAdmin";
import HorariosBarberoAdmin from "./HorariosBarberoAdmin";
import TurnosAdmin from "./TurnosAdmin";
import CalculadoraPagos from "./CalculadoraPagos";
import LoginAdmin from "./LoginAdmin";

export default function AdminApp() {
  const { isAuthenticated, isLoading } = useAuth();

  if (isLoading) {
    return (
      <div className="min-h-screen grid place-items-center">
        <div className="text-center">
          <div className="text-lg font-semibold">Cargando...</div>
        </div>
      </div>
    );
  }

  if (!isAuthenticated) {
    return <LoginAdmin />;
  }

  return (
    <Routes>
      <Route element={<AdminLayout />}>
        <Route index element={<Dashboard />} />
        <Route path="barberos" element={<BarberosAdmin />} />
        <Route path="sucursales" element={<SucursalesAdmin />} />
        <Route path="servicios" element={<ServiciosAdmin />} />
        <Route path="horarios" element={<HorariosBarberoAdmin />} />
        <Route path="turnos" element={<TurnosAdmin />} />
        <Route path="calculadora" element={<CalculadoraPagos />} />
      </Route>
      <Route path="*" element={<Navigate to="/admin" replace />} />
    </Routes>
  );
}