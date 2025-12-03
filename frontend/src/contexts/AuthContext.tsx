// src/contexts/AuthContext.tsx
import { createContext, useContext, useState, useEffect, useCallback } from 'react';
import type { ReactNode } from 'react';
import * as authApi from '../lib/authApi';

type AuthContextType = {
  isAuthenticated: boolean;
  isLoading: boolean;
  user?: string;
  login: (username: string, password: string) => Promise<void>;
  logout: () => Promise<void>;
};

const AuthContext = createContext<AuthContextType | null>(null);

export function AuthProvider({ children }: { children: ReactNode }) {
  const [isAuthenticated, setIsAuthenticated] = useState(false);
  const [isLoading, setIsLoading] = useState(true);
  const [user, setUser] = useState<string | undefined>();

  // ✅ Verificar autenticación al montar
  const checkAuth = useCallback(async () => {
  console.log('[AuthContext] Verificando autenticación...');
  console.log('[AuthContext] Cookies actuales:', document.cookie);
  
  try {
    const result = await authApi.me();
    console.log('[AuthContext] /me response:', result);
    
    setIsAuthenticated(result.ok === true);
    setUser(result.user);
    
    if (!result.ok) {
      console.log('[AuthContext] No autenticado, limpiando cookies...');
      authApi.clearAllAuthCookies();
    }
  } catch (err) {
    console.error('[AuthContext] Error verificando auth:', err);
    setIsAuthenticated(false);
    setUser(undefined);
    authApi.clearAllAuthCookies();
  } finally {
    setIsLoading(false);
  }
}, []);

  useEffect(() => {
    checkAuth();
  }, [checkAuth]);

  // ✅ Login delegado a authApi
  async function login(username: string, password: string) {
    try {
      const result = await authApi.login(username, password);
      
      if (result.ok) {
        setIsAuthenticated(true);
      } else {
        throw new Error('Login falló');
      }
    } catch (error) {
      throw error;
    }
  }

  // ✅ Logout delegado a authApi
  async function logout() {
  console.log('[AuthContext] Logout iniciado');
  
  try {
    // Ejecutar logout (ahora limpia cookies primero)
    await authApi.logout();
  } catch (error) {
    console.error('[AuthContext] Error en logout (ignorado):', error);
  } finally {
    // ✅ SIEMPRE actualizar estado, incluso si hubo error
    setIsAuthenticated(false);
    setUser(undefined);
    
    console.log('[AuthContext] Estado actualizado: no autenticado');
  }
}

  return (
    <AuthContext.Provider value={{ isAuthenticated, isLoading, user, login, logout }}>
      {children}
    </AuthContext.Provider>
  );
}

export function useAuth() {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error('useAuth debe usarse dentro de AuthProvider');
  return ctx;
}