export type ID = number | string

export interface Sucursal {
  id: ID
  nombre: string
  direccion?: string
  activa?: boolean
}

export interface Barbero {
  id: ID
  nombre: string
  bio?: string
  fotoUrl?: string
  instagram?: string
  facebook?: string
  activo?: boolean
  telefono?: string
  
}

export interface Servicio {
  id: ID
  nombre: string
  precio: number
  descripcion?: string
  duracionMin?: number
  activo?: boolean
}

export interface ReservaRequest {
  sucursalId: ID
  servicioId: ID
  barberoId: ID
  fecha: string
  hora: string
  clienteNombre: string
  clienteTelefono: string
  clienteEdad: number
  // Opcionales por ahora:
  clienteEmail?: string
  clienteDni?: string
}

export interface ReservaResponse {
  id: ID
  estado: 'PENDIENTE_PAGO' | 'PAGADA' | 'CANCELADA'
}

