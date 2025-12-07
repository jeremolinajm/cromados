/**
 * RAW_URL: host base del backend (sin /api).
 */
export const RAW_URL = (import.meta.env.VITE_API_URL || "http://localhost:8080")
  .toString()
  .replace(/\/+$/, "");

export const BASE = `${RAW_URL}/api`;

// ⬅️ HELPER CSRF
function getCsrfCookie(): string {
  // Importar desde authApi para usar la lógica con fallback
  const fromCookie = document.cookie
    .split(";")
    .map(s => s.trim())
    .find(x => x.startsWith("CSRF="))
    ?.split("=")[1];
  
  if (fromCookie) return fromCookie;
  
  // Fallback a localStorage
  return localStorage.getItem('_csrf_backup') || '';
}

// ⬅️ HEADERS CON CSRF
function authHeaders(): Record<string, string> {
  const csrf = getCsrfCookie();
  return {
    'Content-Type': 'application/json',
    ...(csrf ? { 'X-CSRF-Token': csrf } : {})
  };
}

export async function jsonOrThrow(res: Response) {
  if (!res.ok) {
    let msg = `HTTP ${res.status}`;
    try { msg += ` – ${await res.text()}`; } catch {}
    throw new Error(msg);
  }
  const ct = res.headers.get("content-type") || "";
  return ct.includes("application/json") ? res.json() : res.text();
}

function toIsoDate(d: Date) {
  const y = d.getFullYear();
  const m = String(d.getMonth() + 1).padStart(2, "0");
  const day = String(d.getDate()).padStart(2, "0");
  return `${y}-${m}-${day}`;
}

export function resolveUrl(path?: string | null): string {
  if (!path) return "";
  if (/^https?:\/\//i.test(path)) return path;
  if (path.startsWith("/")) return `${RAW_URL}${path}`;
  return `${RAW_URL}/${path}`;
}

export type Page<T> = {
  content: T[];
  totalElements: number;
  totalPages: number;
  number: number;
  size: number;
};

export type SucursalDTO = {
  id?: number;
  nombre: string;
  direccion: string;
  fotoUrl?: string | null;
};

export type BarberoDTO = {
  id?: number;
  nombre: string;
  sucursalId: number;
  fotoUrl?: string | null;
  instagram?: string | null;
  facebook?: string | null;
  telefono?: string | null;
};

export type ServicioDTO = {
  id?: number;
  nombre: string;
  precio: number;
  duracionMin: number;
  descripcion?: string | null;
  sesiones?: number;
  adicional?: boolean; // ✅ NUEVO: Marca si es servicio adicional
};

export type TurnoAdminDTO = {
  id: number;
  clienteNombre: string;
  clienteEmail?: string | null;
  clienteTelefono?: string | null;
  sucursalId: number;
  barberoId: number;
  tipoCorteId: number;
  fecha: string;
  hora: string;
  estado: "PENDIENTE" | "RESERVADO" | "CONFIRMADO" | "CANCELADO" | string;
  pagoConfirmado?: boolean | null;
  barberoNombre?: string;
  servicioNombre?: string;
};

export type HorarioBarberoDTO = {
  diaSemana: number;
  inicio: string;
  fin: string;
};

export type DiaExcepcionalBarberoDTO = {
  id?: number;
  barberoId?: number;
  fecha: string; // YYYY-MM-DD
  inicio: string; // HH:mm
  fin: string; // HH:mm
};

async function getPublic<T>(path: string): Promise<T> {
  const res = await fetch(`${BASE}${path}`);
  return jsonOrThrow(res);
}

export const PublicApi = {
  barberos() {
    return getPublic<any>("/barberos");
  },
  sucursales() {
    return getPublic<SucursalDTO[]>("/sucursales");
  },
  servicios() {
    return getPublic<ServicioDTO[]>("/servicios");
  },
  disponibilidad(barberoId: number, fecha: string) {
    return getPublic<string[]>(`/disponibilidad?barberoId=${barberoId}&fecha=${fecha}`);
  },
};

export const AdminApi = {
  async sucursales(): Promise<SucursalDTO[]> {
    const res = await fetch(`${BASE}/sucursales`);
    return jsonOrThrow(res);
  },
  async serviciosPublic(): Promise<ServicioDTO[]> {
    const res = await fetch(`${BASE}/servicios`);
    return jsonOrThrow(res);
  },

  async listBarberos(page = 0, size = 50, sort = "id,asc"): Promise<Page<BarberoDTO>> {
    const res = await fetch(
      `${RAW_URL}/admin/barberos?page=${page}&size=${size}&sort=${encodeURIComponent(sort)}`,
      { credentials: 'include', headers: authHeaders() }
    );
    return jsonOrThrow(res);
  },
  
  async createBarbero(dto: any): Promise<BarberoDTO> {
    const res = await fetch(`${RAW_URL}/admin/barberos`, {
      method: "POST",
      credentials: 'include',
      headers: authHeaders(),
      body: JSON.stringify(dto),
    });
    return jsonOrThrow(res);
  },
  
  async updateBarbero(id: number, dto: any): Promise<BarberoDTO> {
    const res = await fetch(`${RAW_URL}/admin/barberos/${id}`, {
      method: "PUT",
      credentials: 'include',
      headers: authHeaders(),
      body: JSON.stringify(dto),
    });
    return jsonOrThrow(res);
  },
  
  async deleteBarbero(id: number): Promise<void> {
    const res = await fetch(`${RAW_URL}/admin/barberos/${id}`, { 
      method: "DELETE", 
      credentials: 'include',
      headers: authHeaders()
    });
    if (!res.ok && res.status !== 204) await jsonOrThrow(res);
  },
  
  async uploadBarberoFoto(id: number, file: File): Promise<BarberoDTO> {
    const fd = new FormData(); 
    fd.append("file", file);
    const csrf = getCsrfCookie();
    const res = await fetch(`${RAW_URL}/admin/barberos/${id}/foto`, {
      method: "POST", 
      credentials: 'include',
      headers: csrf ? { 'X-CSRF-Token': csrf } : {},
      body: fd,
    });
    return jsonOrThrow(res);
  },

  async horariosDeBarbero(barberoId: number): Promise<HorarioBarberoDTO[]> {
    const res = await fetch(`${RAW_URL}/admin/horarios-barbero/${barberoId}`, { 
      credentials: 'include',
      headers: authHeaders()
    });
    return jsonOrThrow(res);
  },
  
  async upsertHorarioBarbero(barberoId: number, diaSemana: number, inicio: string, fin: string) {
    const res = await fetch(`${RAW_URL}/admin/horarios-barbero/${barberoId}/${diaSemana}`, {
      method: "PUT",
      credentials: 'include',
      headers: authHeaders(),
      body: JSON.stringify({ inicio, fin }),
    });
    return jsonOrThrow(res);
  },
  
  async deleteHorarioBarbero(barberoId: number, diaSemana: number): Promise<void> {
    const res = await fetch(`${RAW_URL}/admin/horarios-barbero/${barberoId}/${diaSemana}`, {
      method: "DELETE",
      credentials: 'include',
      headers: authHeaders()
    });
    if (!res.ok && res.status !== 204) await jsonOrThrow(res);
  },

  async listDiasExcepcionales(barberoId: number): Promise<DiaExcepcionalBarberoDTO[]> {
    const res = await fetch(`${RAW_URL}/admin/horarios-barbero/${barberoId}/excepcionales`, {
      credentials: 'include',
      headers: authHeaders()
    });
    return jsonOrThrow(res);
  },

  async agregarDiaExcepcional(barberoId: number, fecha: string, inicio: string, fin: string): Promise<DiaExcepcionalBarberoDTO> {
    const res = await fetch(`${RAW_URL}/admin/horarios-barbero/${barberoId}/excepcionales`, {
      method: "POST",
      credentials: 'include',
      headers: authHeaders(),
      body: JSON.stringify({ fecha, inicio, fin }),
    });
    return jsonOrThrow(res);
  },

  async eliminarDiaExcepcional(barberoId: number, id: number): Promise<void> {
    const res = await fetch(`${RAW_URL}/admin/horarios-barbero/${barberoId}/excepcionales/${id}`, {
      method: "DELETE",
      credentials: 'include',
      headers: authHeaders()
    });
    if (!res.ok && res.status !== 204) await jsonOrThrow(res);
  },

  async listSucursales(page = 0, size = 50, sort = "id,asc"): Promise<Page<SucursalDTO>> {
    const res = await fetch(
      `${RAW_URL}/admin/sucursales?page=${page}&size=${size}&sort=${encodeURIComponent(sort)}`,
      { credentials: 'include', headers: authHeaders() }
    );
    return jsonOrThrow(res);
  },
  
  async createSucursal(dto: any): Promise<SucursalDTO> {
    const res = await fetch(`${RAW_URL}/admin/sucursales`, {
      method: "POST",
      credentials: 'include',
      headers: authHeaders(),
      body: JSON.stringify(dto),
    });
    return jsonOrThrow(res);
  },
  
  async updateSucursal(id: number, dto: any): Promise<SucursalDTO> {
    const res = await fetch(`${RAW_URL}/admin/sucursales/${id}`, {
      method: "PUT",
      credentials: 'include',
      headers: authHeaders(),
      body: JSON.stringify(dto),
    });
    return jsonOrThrow(res);
  },
  
  async deleteSucursal(id: number): Promise<void> {
    const res = await fetch(`${RAW_URL}/admin/sucursales/${id}`, { 
      method: "DELETE", 
      credentials: 'include',
      headers: authHeaders()
    });
    if (!res.ok && res.status !== 204) await jsonOrThrow(res);
  },
  
  async uploadSucursalFoto(id: number, file: File): Promise<SucursalDTO> {
    const fd = new FormData(); 
    fd.append("file", file);
    const csrf = getCsrfCookie();
    const res = await fetch(`${RAW_URL}/admin/sucursales/${id}/foto`, {
      method: "POST", 
      credentials: 'include',
      headers: csrf ? { 'X-CSRF-Token': csrf } : {},
      body: fd,
    });
    return jsonOrThrow(res);
  },

  async listServicios(page = 0, size = 50, sort = "id,asc"): Promise<Page<ServicioDTO>> {
    const res = await fetch(
      `${RAW_URL}/admin/servicios?page=${page}&size=${size}&sort=${encodeURIComponent(sort)}`,
      { credentials: 'include', headers: authHeaders() }
    );
    return jsonOrThrow(res);
  },
  
  async createServicio(dto: any): Promise<ServicioDTO> {
    const res = await fetch(`${RAW_URL}/admin/servicios`, {
      method: "POST",
      credentials: 'include',
      headers: authHeaders(),
      body: JSON.stringify(dto),
    });
    return jsonOrThrow(res);
  },
  
  async updateServicio(id: number, dto: any): Promise<ServicioDTO> {
    const res = await fetch(`${RAW_URL}/admin/servicios/${id}`, {
      method: "PUT",
      credentials: 'include',
      headers: authHeaders(),
      body: JSON.stringify(dto),
    });
    return jsonOrThrow(res);
  },
  
  async toggleServicioActivo(id: number): Promise<ServicioDTO> {
    const res = await fetch(`${RAW_URL}/admin/servicios/${id}/toggle-activo`, {
      method: "PATCH",
      credentials: 'include',
      headers: authHeaders()
    });
    return jsonOrThrow(res);
  },

  async deleteServicio(id: number): Promise<void> {
    const res = await fetch(`${RAW_URL}/admin/servicios/${id}`, {
      method: "DELETE",
      credentials: 'include',
      headers: authHeaders()
    });
    if (!res.ok && res.status !== 204) await jsonOrThrow(res);
  },

  async calcularPagosBarberos(desde: string, hasta: string): Promise<any[]> {
    const url = new URL(`${RAW_URL}/admin/calculadora/pagos`);
    url.searchParams.set('desde', desde);
    url.searchParams.set('hasta', hasta);
    const res = await fetch(url.toString(), {
      method: "GET",
      credentials: 'include',
      headers: authHeaders()
    });
    return jsonOrThrow(res);
  },

  async listTurnos(params: any): Promise<Page<TurnoAdminDTO>> {
    const { desde, hasta, page = 0, size = 20, sort } = params;
    const u = new URL(`${RAW_URL}/admin/turnos`);
    u.searchParams.set("desde", desde);
    u.searchParams.set("hasta", hasta);
    u.searchParams.set("page", String(page));
    u.searchParams.set("size", String(size));
    if (sort) u.searchParams.set("sort", sort); // 🔧 Añadir parámetro sort

    const res = await fetch(u.toString(), { 
      credentials: 'include',
      headers: authHeaders()
    });
    const json = await jsonOrThrow(res);

    const mapped: TurnoAdminDTO[] = (json.items || []).map((t: any) => ({
      id: Number(t.id),
      clienteNombre: t.clienteNombre ?? t.cliente_nombre ?? "",
      clienteEmail: t.clienteEmail ?? t.cliente_email ?? null,
      clienteTelefono: t.clienteTelefono ?? t.cliente_telefono ?? null,
      sucursalId: t.sucursalId ?? t.sucursal_id ?? t.sucursal?.id,
      barberoId: t.barberoId ?? t.barbero_id ?? t.barbero?.id,
      tipoCorteId: t.tipoCorteId ?? t.servicioId ?? t.tipo_corte_id ?? t.tipoCorte?.id,
      fecha: t.fecha,
      hora: t.hora,
      estado: t.estado,
      pagoConfirmado: !!t.pagoConfirmado,
      barberoNombre: t.barberoNombre ?? t.barbero_nombre ?? t.barbero?.nombre,
      servicioNombre: t.servicioNombre ?? t.servicio_nombre ?? t.tipoCorte?.nombre,
      // 🆕 Nuevos campos de pago
      montoPagado: t.montoPagado ?? t.monto_pagado ?? null,
      senia: t.senia ?? false,
      montoEfectivo: t.montoEfectivo ?? t.monto_efectivo ?? null,
      // 🆕 Adicionales
      adicionales: t.adicionales ?? "",
    }));

    return {
      content: mapped,
      totalElements: json.total ?? 0,
      totalPages: json.pages ?? 1,
      number: json.page ?? page,
      size,
    };
  },

  async proximos(dias = 7, limit = 8): Promise<TurnoAdminDTO[]> {
    const u = new URL(`${RAW_URL}/admin/turnos/proximos`);
    u.searchParams.set("dias", String(dias));
    u.searchParams.set("limit", String(limit));
    const res = await fetch(u.toString(), { 
      credentials: 'include',
      headers: authHeaders()
    });
    const json = await jsonOrThrow(res);
    return (json.items || []).map((t: any) => ({
      id: t.id,
      clienteNombre: t.clienteNombre ?? t.cliente_nombre,
      clienteEmail: t.clienteEmail ?? t.cliente_email ?? null,
      clienteTelefono: t.clienteTelefono ?? t.cliente_telefono ?? null,
      sucursalId: t.sucursalId ?? t.sucursal_id ?? t.sucursal?.id,
      barberoId: t.barberoId ?? t.barbero_id ?? t.barbero?.id,
      tipoCorteId: t.tipoCorteId ?? t.tipo_corte_id ?? t.tipoCorte?.id ?? t.servicio?.id,
      fecha: t.fecha,
      hora: t.hora,
      estado: t.estado,
      pagoConfirmado: t.pagoConfirmado ?? t.pago_confirmado ?? null,
      barberoNombre: t.barberoNombre ?? t.barbero?.nombre,
      tipoCorteNombre: t.servicioNombre ?? t.tipoCorte?.nombre,
      adicionales: t.adicionales ?? "",
    }));
  },

  async countBarberos(): Promise<number> {
    const page = await this.listBarberos(0, 1, "id,asc");
    return page?.totalElements ?? 0;
  },
  
  async countSucursales(): Promise<number> {
    const page = await this.listSucursales(0, 1, "id,asc");
    return page?.totalElements ?? 0;
  },
  
  async countServicios(): Promise<number> {
    const page = await this.listServicios(0, 1, "id,asc");
    return page?.totalElements ?? 0;
  },
  
  async listTurnosRecientes(limit = 5) {
    const today = new Date();
    today.setHours(0, 0, 0, 0);
    const plus30 = new Date(today);
    plus30.setDate(today.getDate() + 30);

    const page = await this.listTurnos({
      desde: toIsoDate(today),
      hasta: toIsoDate(plus30),
      page: 0,
      size: limit,
    });
    return page?.content ?? [];
  },

  // ✅ NUEVO: Últimos turnos (ordenados por ID desc, sin filtro de fecha)
  async listUltimosTurnos(limit = 10) {
    const u = new URL(`${RAW_URL}/admin/turnos/ultimos`);
    u.searchParams.set("limit", String(limit));
    const res = await fetch(u.toString(), {
      credentials: 'include',
      headers: authHeaders()
    });
    const json = await jsonOrThrow(res);
    return (json.items || []).map((t: any) => ({
      id: t.id,
      clienteNombre: t.clienteNombre ?? t.cliente_nombre,
      clienteEmail: t.clienteEmail ?? t.cliente_email ?? null,
      clienteTelefono: t.clienteTelefono ?? t.cliente_telefono ?? null,
      sucursalId: t.sucursalId ?? t.sucursal_id ?? t.sucursal?.id,
      barberoId: t.barberoId ?? t.barbero_id ?? t.barbero?.id,
      tipoCorteId: t.tipoCorteId ?? t.tipo_corte_id ?? t.tipoCorte?.id ?? t.servicio?.id,
      fecha: t.fecha,
      hora: t.hora,
      estado: t.estado,
      pagoConfirmado: t.pagoConfirmado ?? t.pago_confirmado ?? null,
      barberoNombre: t.barberoNombre ?? t.barbero?.nombre,
      servicioNombre: t.servicioNombre ?? t.tipoCorte?.nombre,
      montoPagado: t.montoPagado ?? t.monto_pagado ?? null,
      montoEfectivo: t.montoEfectivo ?? t.monto_efectivo ?? null,
    }));
  },

  // ✅ NUEVO: Contar turnos vigentes
  async countTurnosVigentes(): Promise<number> {
    const res = await fetch(`${RAW_URL}/admin/turnos/count-vigentes`, {
      credentials: 'include',
      headers: authHeaders()
    });
    const json = await jsonOrThrow(res);
    return json?.count ?? 0;
  },
};

export default AdminApi;