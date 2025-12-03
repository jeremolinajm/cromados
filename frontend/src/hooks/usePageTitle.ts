import { useEffect } from 'react'
import { useLocation } from 'react-router-dom'

const MAP: Record<string, string> = {
  '/': 'Inicio',
  '/turnos': 'Turnos',
  '/barberos': 'Barberos',
  '/nosotros': 'Nosotros',
}

export function usePageTitle(suffix = 'Cromados') {
  const { pathname } = useLocation()
  useEffect(() => {
    const base = MAP[pathname] || 'Cromados'
    document.title = base === 'Cromados' ? base : `${base} — ${suffix}`
  }, [pathname, suffix])
}

