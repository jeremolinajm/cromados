// frontend/src/lib/config.ts
// Defaults seguros para dev si no hay envs definidas
const DEF_API_URL = 'http://localhost:8080'
const DEF_API_BASE = 'api'

export const API_URL  = (import.meta.env.VITE_API_URL  || DEF_API_URL).replace(/\/+$/, '')
export const API_BASE = (import.meta.env.VITE_API_BASE || DEF_API_BASE).replace(/\/+$/, '')

/** Construye URL: http://host[:port][/base]/ruta  */
export function apiUrl(path: string) {
  const clean = path.startsWith('/') ? path : `/${path}`
  const base  = API_BASE ? `/${API_BASE.replace(/^\//, '')}` : ''
  return `${API_URL}${base}${clean}`
}

