import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { api, newRequestId, periodQs, qs } from './api';
import type {
  DailyReport, DashboardReport, Match, MatchDetail, MonthlyReport, Page, PeriodValue, Player, PlayerStatistics,
  QueueState, RangeReport, Settings, PermissionInfo, UserAccount, AuditEntry, PlayerStatus,
} from './types';

/** Live data (queue, current game) refreshes every few seconds without reloading the page. */
const LIVE_INTERVAL = 4000;

export const keys = {
  queue: ['queue'] as const,
  settings: ['settings'] as const,
};

export function useQueue() {
  return useQuery({
    queryKey: keys.queue,
    queryFn: () => api.get<QueueState>('/api/queue'),
    refetchInterval: LIVE_INTERVAL,
    refetchIntervalInBackground: false,
  });
}

export function useSettings() {
  return useQuery({ queryKey: keys.settings, queryFn: () => api.get<Settings>('/api/settings'), staleTime: 60_000 });
}

export function usePermissionCatalog() {
  return useQuery({
    queryKey: ['permissions'],
    queryFn: () => api.get<{ permissions: PermissionInfo[] }>('/api/settings/permissions'),
    staleTime: Infinity,
  });
}

export function usePlayers(params: { q?: string; status?: PlayerStatus | ''; page?: number; size?: number } = {}) {
  return useQuery({
    queryKey: ['players', params],
    queryFn: () => api.get<Page<Player>>('/api/players' + qs({ q: params.q, status: params.status || undefined, page: params.page ?? 0, size: params.size ?? 200 })),
    placeholderData: (prev) => prev,
  });
}

export function usePlayer(id: number | undefined) {
  return useQuery({ queryKey: ['player', id], queryFn: () => api.get<Player>(`/api/players/${id}`), enabled: !!id });
}

export function usePlayerStats(id: number | undefined, from?: string, to?: string) {
  return useQuery({
    queryKey: ['player-stats', id, from, to],
    queryFn: () => api.get<PlayerStatistics>(`/api/players/${id}/statistics` + qs({ from, to })),
    enabled: !!id,
    placeholderData: (prev) => prev,
  });
}

export function useMyStats(period: PeriodValue, enabled = true) {
  return useQuery({
    queryKey: ['me-stats', period],
    queryFn: () => api.get<PlayerStatistics>('/api/reports/me' + periodQs(period)),
    enabled,
    placeholderData: (prev) => prev,
    retry: false,
  });
}

export function useDashboard(period: PeriodValue) {
  return useQuery({
    queryKey: ['dashboard', period],
    queryFn: () => api.get<DashboardReport>('/api/reports/dashboard' + periodQs(period)),
    refetchInterval: 15_000,
    placeholderData: (prev) => prev,
  });
}

export function useRangeReport(period: PeriodValue) {
  return useQuery({
    queryKey: ['range', period],
    queryFn: () => api.get<RangeReport>('/api/reports/range' + periodQs(period)),
    placeholderData: (prev) => prev,
  });
}

export function useDailyReport(date: string) {
  return useQuery({
    queryKey: ['daily', date],
    queryFn: () => api.get<DailyReport>('/api/reports/daily' + qs({ date })),
    placeholderData: (prev) => prev,
  });
}

export function useMonthlyReport(month: string) {
  return useQuery({
    queryKey: ['monthly', month],
    queryFn: () => api.get<MonthlyReport>('/api/reports/monthly' + qs({ month })),
    placeholderData: (prev) => prev,
  });
}

export function useHistory(params: { from?: string; to?: string; playerId?: number; q?: string; page: number; size?: number }) {
  return useQuery({
    queryKey: ['history', params],
    queryFn: () => api.get<Page<Match>>('/api/matches/history' + qs({ ...params, size: params.size ?? 20 })),
    placeholderData: (prev) => prev,
  });
}

export function useMatchDetail(id: number | null) {
  return useQuery({ queryKey: ['match', id], queryFn: () => api.get<MatchDetail>(`/api/matches/${id}`), enabled: id != null });
}

export function useUsers() {
  return useQuery({ queryKey: ['users'], queryFn: () => api.get<UserAccount[]>('/api/users') });
}

export function useAuditLogs(page: number, entityType = '') {
  return useQuery({
    queryKey: ['audit', page, entityType],
    queryFn: () => api.get<Page<AuditEntry>>('/api/audit-logs' + qs({ page, size: 20, entityType })),
    placeholderData: (prev) => prev,
  });
}

// ------------------------------------------------------------------ mutations

/** Invalidate everything derived from matches after a court action. */
function useInvalidateMatches() {
  const qc = useQueryClient();
  return () => {
    qc.invalidateQueries({ queryKey: keys.queue });
    ['dashboard', 'range', 'daily', 'monthly', 'history', 'me-stats', 'player-stats', 'players', 'match'].forEach((k) =>
      qc.invalidateQueries({ queryKey: [k] }),
    );
  };
}

export function useMatchActions() {
  const qc = useQueryClient();
  const invalidate = useInvalidateMatches();

  // Responses may arrive out of order on slow phones: never replace the current game with an older version.
  const setCurrent = (m: Match) =>
    qc.setQueryData<QueueState>(keys.queue, (old) => {
      if (!old) return old;
      if (old.current && old.current.id === m.id && old.current.version > m.version) return old;
      return { ...old, current: m.status === 'IN_PROGRESS' ? m : null };
    });

  const createMatch = useMutation({
    mutationFn: (body: { team1PlayerIds: number[]; team2PlayerIds: number[] }) => api.post<Match>('/api/queue', body),
    onSuccess: invalidate,
  });
  const start = useMutation({
    mutationFn: (id: number) => api.post<Match>(`/api/matches/${id}/start`),
    onSuccess: (m) => {
      setCurrent(m);
      invalidate();
    },
  });
  const move = useMutation({
    mutationFn: ({ id, position }: { id: number; position: number }) => api.patch<Match[]>(`/api/queue/${id}/position`, { position }),
    onSuccess: invalidate,
  });
  const remove = useMutation({
    mutationFn: (id: number) => api.del<void>(`/api/queue/${id}`),
    onSuccess: invalidate,
  });
  const shuttle = useMutation({
    // requestId is generated once per tap and reused on automatic retry → never double-counted.
    mutationFn: ({ id, delta, reason, requestId }: { id: number; delta: number; reason?: string; requestId: string }) =>
      api.post<Match>(`/api/matches/${id}/shuttlecocks`, { delta, reason, requestId }),
    retry: 2,
    onSuccess: (m) => setCurrent(m),
  });
  const complete = useMutation({
    mutationFn: ({ id, ...body }: { id: number; expectedShuttlecockCount: number; winnerTeam?: number | null; score?: string; notes?: string }) =>
      api.post<Match>(`/api/matches/${id}/complete`, body),
    onSuccess: invalidate,
  });
  const cancel = useMutation({
    mutationFn: ({ id, reason }: { id: number; reason?: string }) => api.post<Match>(`/api/matches/${id}/cancel`, { reason }),
    onSuccess: invalidate,
  });
  const correct = useMutation({
    mutationFn: ({ id, shuttlecockCount, reason }: { id: number; shuttlecockCount: number; reason: string }) =>
      api.post<Match>(`/api/matches/${id}/corrections`, { shuttlecockCount, reason }),
    onSuccess: invalidate,
  });
  return { createMatch, start, move, remove, shuttle, complete, cancel, correct, newRequestId };
}
