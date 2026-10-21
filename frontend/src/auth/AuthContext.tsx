import { createContext, useCallback, useContext, useEffect, useMemo, useState, type ReactNode } from 'react';
import { useQueryClient } from '@tanstack/react-query';
import { api, getToken, setToken, setUnauthorizedHandler } from '../lib/api';
import type { LoginResponse, Me, Permission } from '../lib/types';

interface AuthState {
  user: Me | null;
  loading: boolean;
  login: (username: string, password: string) => Promise<void>;
  register: (body: Record<string, string>) => Promise<void>;
  logout: () => Promise<void>;
  refresh: () => Promise<void>;
  can: (p: Permission) => boolean;
  isAdmin: boolean;
}

const AuthContext = createContext<AuthState | null>(null);

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<Me | null>(null);
  const [loading, setLoading] = useState(true);
  const qc = useQueryClient();

  const clear = useCallback(() => {
    setToken(null);
    setUser(null);
    qc.clear();
  }, [qc]);

  const refresh = useCallback(async () => {
    if (!getToken()) {
      setUser(null);
      return;
    }
    try {
      setUser(await api.get<Me>('/api/auth/me'));
    } catch {
      clear();
    }
  }, [clear]);

  useEffect(() => {
    setUnauthorizedHandler(clear);
    refresh().finally(() => setLoading(false));
  }, [refresh, clear]);

  const value = useMemo<AuthState>(() => ({
    user,
    loading,
    isAdmin: user?.role === 'ADMIN',
    can: (p) => !!user && (user.role === 'ADMIN' || user.permissions.includes(p)),
    refresh,
    login: async (username, password) => {
      const res = await api.post<LoginResponse>('/api/auth/login', { username, password });
      setToken(res.token);
      qc.clear();
      setUser(res.user);
    },
    register: async (body) => {
      const res = await api.post<LoginResponse>('/api/auth/register', body);
      setToken(res.token);
      qc.clear();
      setUser(res.user);
    },
    logout: async () => {
      try {
        await api.post('/api/auth/logout');
      } finally {
        clear();
      }
    },
  }), [user, loading, refresh, clear, qc]);

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth() {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error('useAuth outside AuthProvider');
  return ctx;
}
