// src/types.ts
export type ID = number | string

export interface Servicio {
  id: ID
  nombre: string
  precio: number
  descripcion?: string
  duracionMin?: number
  activo?: boolean
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

export interface Turno {
  id: ID
  servicioId: ID
  barberoId: ID
  fechaHora: string // ISO
  clienteNombre: string
  clienteTelefono?: string
  estado?: 'RESERVADO' | 'CANCELADO' | 'COMPLETADO'
}

export interface ReservaRequest {
  servicioId: ID
  barberoId: ID
  fecha: string   // "YYYY-MM-DD"
  hora: string    // "HH:mm"
  clienteNombre: string
  clienteTelefono?: string
}

