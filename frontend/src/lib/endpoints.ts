import { apiUrl } from './config'

export const endpoints = {
  sucursales: () => apiUrl('/sucursales'),
  barberos:   (sucursalId: string|number) => apiUrl(`/barberos?sucursalId=${sucursalId}`),
  servicios:  () => apiUrl('/servicios'),
  disponibilidad: (barberoId: string|number, fecha: string) =>
    apiUrl(`/disponibilidad?barberoId=${barberoId}&fecha=${fecha}`),
  reservas:   () => apiUrl('/reservas'),
  reservaById:(id: string|number) => apiUrl(`/reservas/${id}`),
  checkout:   () => apiUrl('/pagos/checkout'),
}

