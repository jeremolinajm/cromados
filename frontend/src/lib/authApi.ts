// src/lib/authApi.ts
import { RAW_URL } from './adminApi';

// ================== Utilidades de Cookies ==================

/**
 * Lee el valor de una cookie por nombre
 */
export function getCookie(name: string): string | null {
  const cookies = document.cookie.split(';');
  for (const cookie of cookies) {
    const [key, value] = cookie.trim().split('=');
    if (key === name) {
      return decodeURIComponent(value);
    }
  }
  
  // Fallback a localStorage
  const backup = localStorage.getItem(`_${name.toLowerCase()}_backup`);
  if (backup) {
    return backup;
  }

  return null;
}

/**
 * Lee específicamente la cookie CSRF
 */
export function getCsrfCookie(): string {
  // Intentar primero desde cookie (ideal)
  const fromCookie = getCookie('CSRF');
  if (fromCookie) return fromCookie;
  
  // Fallback: localStorage (si cookies están bloqueadas)
  const fromStorage = localStorage.getItem('_csrf_backup');
  if (fromStorage) {
    return fromStorage;
  }

  return '';
}

/**
 * Limpia una cookie específica (EXCEPTO CSRF hasta después del logout)
 */
export function clearCookie(name: string): void {
  const configs = [
    `${name}=; Path=/; Expires=Thu, 01 Jan 1970 00:00:00 GMT`,
    `${name}=; Path=/; Expires=Thu, 01 Jan 1970 00:00:00 GMT; SameSite=Lax`,
    `${name}=; Path=/; Expires=Thu, 01 Jan 1970 00:00:00 GMT; SameSite=None; Secure`,
  ];
  
  configs.forEach(config => {
    document.cookie = config;
  });
}

/**
 * Limpia todas las cookies de autenticación
 */
export function clearAllAuthCookies(): void {
  const authCookies = ['ACCESS', 'REFRESH', 'CSRF'];
  const domains = [
    '', // dominio actual
    window.location.hostname,
    `.${window.location.hostname}`,
  ];
  const paths = ['/', '/admin', '/auth'];
  
  authCookies.forEach(cookieName => {
    // Estrategia 1: Limpieza simple
    document.cookie = `${cookieName}=; Path=/; Expires=Thu, 01 Jan 1970 00:00:00 GMT`;
    
    // Estrategia 2: Con SameSite
    document.cookie = `${cookieName}=; Path=/; Expires=Thu, 01 Jan 1970 00:00:00 GMT; SameSite=Lax`;
    document.cookie = `${cookieName}=; Path=/; Expires=Thu, 01 Jan 1970 00:00:00 GMT; SameSite=None; Secure`;
    
    // Estrategia 3: Múltiples dominios y paths
    domains.forEach(domain => {
      paths.forEach(path => {
        const domainPart = domain ? `; Domain=${domain}` : '';
        document.cookie = `${cookieName}=; Path=${path}${domainPart}; Expires=Thu, 01 Jan 1970 00:00:00 GMT`;
        document.cookie = `${cookieName}=; Path=${path}${domainPart}; Expires=Thu, 01 Jan 1970 00:00:00 GMT; SameSite=Lax`;
        document.cookie = `${cookieName}=; Path=${path}${domainPart}; Expires=Thu, 01 Jan 1970 00:00:00 GMT; SameSite=None; Secure`;
      });
    });
  });
}

// ================== Endpoints de Autenticación ==================

/**
 * Login: autentica usuario y establece cookies
 */
export async function login(user: string, pass: string): Promise<{ ok: boolean }> {
  const url = `${RAW_URL}/auth/login`;
  
  const response = await fetch(url, {
    method: "POST",
    credentials: "include",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ username: user, password: pass }),
  });

  if (!response.ok) {
    let errorMsg = 'Credenciales inválidas';
    try {
      const errorData = await response.json();
      errorMsg = errorData.error || errorMsg;
    } catch {
      errorMsg = await response.text().catch(() => errorMsg);
    }
    throw new Error(errorMsg);
  }

  const result = await response.json();

  // ✅ Guardar tokens en localStorage (funciona cross-origin)
  if (result.csrf) {
    localStorage.setItem('_csrf_backup', result.csrf);
  }
  if (result.access) {
    localStorage.setItem('_access_backup', result.access);
  }
  if (result.refresh) {
    localStorage.setItem('_refresh_backup', result.refresh);
  }

  return result;
}


/**
 * Refresh: renueva el access token usando el refresh token
 */
export async function refresh(): Promise<boolean> {
  const csrf = getCsrfCookie();
  
  if (!csrf) {
    return false;
  }

  const url = `${RAW_URL}/auth/refresh`;
  
  try {
    const response = await fetch(url, {
      method: "POST",
      credentials: "include",
      headers: { 
        "Content-Type": "application/json",
        "X-CSRF-Token": csrf 
      },
    });

    return response.ok;
  } catch (error) {
    return false;
  }
}

/**
 * Me: verifica si el usuario está autenticado
 */
export async function me(): Promise<{ ok: boolean; user?: string; roles?: string[] }> {
  const url = `${RAW_URL}/auth/me`;
  
  try {
    const response = await fetch(url, { 
      credentials: "include",
      headers: { "Content-Type": "application/json" }
    });

    if (!response.ok) {
      return { ok: false };
    }

    return response.json();
  } catch (error) {
    return { ok: false };
  }
}

/**
 * Logout: cierra sesión y limpia cookies
 * IMPORTANTE: Guarda CSRF antes de limpiar para poder hacer el request
 */
export async function logout(): Promise<void> {
  // ✅ 1. PRIMERO limpiar localmente (no esperar al servidor)
  clearAllAuthCookies();

  // ✅ 2. Intentar notificar al servidor (best effort)
  const csrf = getCsrfCookie();
  const url = `${RAW_URL}/auth/logout`;

  // No esperamos ni manejamos errores - es opcional
  fetch(url, {
    method: "POST",
    credentials: "include",
    headers: {
      "Content-Type": "application/json",
      ...(csrf ? { "X-CSRF-Token": csrf } : {})
    }
  }).catch(() => {
    // Ignorar errores de logout del servidor
  });
}
/**
 * Obtiene cookie CSRF del servidor (para cliente público)
 */
export async function ensureCsrf(baseUrl?: string): Promise<void> {
  const root = baseUrl ? baseUrl.replace(/\/+$/, "") : RAW_URL;
  const url = `${root}/auth/csrf`;

  try {
    const response = await fetch(url, {
      method: "GET",
      credentials: "include"
    });

    if (response.ok) {
      // Intentar leer el token de la respuesta JSON
      try {
        const data = await response.json();
        if (data.csrf) {
          // Guardar en localStorage como backup (para mobile con cookies bloqueadas)
          localStorage.setItem('_csrf_backup', data.csrf);
        }
      } catch {
        // Si no hay JSON, el token debe estar en la cookie
      }
    }
  } catch (error) {
    console.warn('[ensureCsrf] Error obteniendo CSRF:', error);
  }
}

// ================== Verificación de Estado ==================

/**
 * Verifica si hay tokens de sesión presentes
 */
export function hasAuthTokens(): boolean {
  const access = getCookie('ACCESS');
  const refresh = getCookie('REFRESH');
  return !!(access || refresh);
}

/**
 * Verifica si la sesión parece válida (tiene tokens)
 */
export function isLikelyAuthenticated(): boolean {
  return hasAuthTokens();
}

// ================== Export por defecto ==================

export default {
  login,
  logout,
  refresh,
  me,
  ensureCsrf,
  getCsrfCookie,
  getCookie,
  clearCookie,
  clearAllAuthCookies,
  hasAuthTokens,
  isLikelyAuthenticated,
};