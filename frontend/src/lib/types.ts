export type Role = 'ADMIN' | 'USER';
export type Permission =
  | 'QUEUE_CREATE' | 'QUEUE_MANAGE' | 'MATCH_START' | 'SHUTTLE_RECORD' | 'SHUTTLE_CORRECT' | 'MATCH_COMPLETE'
  | 'MATCH_CANCEL' | 'MATCH_CORRECT' | 'PLAYER_MANAGE' | 'REPORT_VIEW' | 'SETTINGS_MANAGE' | 'USER_MANAGE' | 'AUDIT_VIEW';

export interface Me {
  id: number;
  username: string;
  role: Role;
  playerId: number | null;
  playerNickname: string | null;
  playerFullName: string | null;
  permissions: Permission[];
}

export interface LoginResponse { token: string; expiresAt: string; user: Me }

export interface Page<T> { content: T[]; page: number; size: number; totalElements: number; totalPages: number }

export type PlayerStatus = 'ACTIVE' | 'INACTIVE';

export interface Player {
  id: number;
  fullName: string;
  nickname: string;
  phone: string | null;
  status: PlayerStatus;
  registeredOn: string;
  username: string | null;
  totalGames: number;
  createdAt: string;
}

export interface PlayerRef { id: number; nickname: string; fullName: string; status: PlayerStatus }

export type MatchStatus = 'WAITING' | 'READY' | 'IN_PROGRESS' | 'COMPLETED' | 'CANCELLED';

export interface Match {
  id: number;
  status: MatchStatus;
  queuePosition: number | null;
  team1: PlayerRef[];
  team2: PlayerRef[];
  shuttlecockCount: number;
  shuttlecockPrice: number;
  priceSnapshot: boolean;
  shuttlecockCost: number;
  playDate: string | null;
  createdAt: string;
  startedAt: string | null;
  completedAt: string | null;
  cancelledAt: string | null;
  durationSeconds: number | null;
  winnerTeam: 1 | 2 | null;
  score: string | null;
  notes: string | null;
  cancelReason: string | null;
  createdBy: string | null;
  startedBy: string | null;
  completedBy: string | null;
  cancelledBy: string | null;
  createdById: number;
  version: number;
}

export interface QueueState { serverTime: string; current: Match | null; next: Match | null; queue: Match[] }

export interface MatchEvent {
  id: number; type: string; delta: number | null; countAfter: number | null; reason: string | null;
  actor: string | null; createdAt: string;
}
export interface MatchDetail { match: Match; events: MatchEvent[]; serverTime: string }

export type CourtSplitMethod = 'EQUAL_SPLIT' | 'CLUB_ONLY' | 'PER_PLAYER';

export interface Settings {
  clubName: string;
  shuttlecockPrice: number;
  courtPricePerDay: number;
  courtCount: number;
  timezone: string;
  courtSplitMethod: CourtSplitMethod;
  selfRegistrationEnabled: boolean;
  scoreEnabled: boolean;
  correctionRequiresReason: boolean;
  userExportEnabled: boolean;
  userPermissions: Permission[];
  version: number;
  updatedAt: string;
}

export interface PermissionInfo { permission: Permission; description: string; grantableToUser: boolean }

export interface Summary {
  totalGames: number;
  uniquePlayers: number;
  totalShuttlecocks: number;
  shuttlecockCost: number;
  playDays: number;
  courtCost: number;
  totalExpense: number;
}

export interface DailyRow {
  date: string;
  games: number;
  uniquePlayers: number;
  shuttlecocks: number;
  shuttlecockCost: number;
  courtCost: number;
  courtSplitMethod: CourtSplitMethod;
  totalExpense: number;
}

export interface PlayerRow {
  playerId: number;
  nickname: string;
  fullName: string | null;
  games: number;
  playDays: number;
  shuttlecocks: number;
  shuttlecockCost: number;
  courtShare: number;
  totalEstimate: number;
  wins: number;
  losses: number;
}

export interface RangeReport {
  from: string; to: string; summary: Summary; daily: DailyRow[]; players: PlayerRow[];
  currentCourtSplitMethod: CourtSplitMethod; allocationNote: string;
}

export interface DailyReport { date: string; summary: Summary; day: DailyRow | null; players: PlayerRow[]; matches: Match[] }

export interface MonthlyReport {
  month: string; from: string; to: string; summary: Summary; averageGamesPerPlayDay: number;
  daily: DailyRow[]; players: PlayerRow[]; mostActive: PlayerRow[];
}

export interface AuditEntry {
  id: number; createdAt: string; actorUsername: string | null; action: string; entityType: string;
  entityId: number | null; summary: string; details: string | null;
}

export interface DashboardReport {
  from: string; to: string;
  totalRegisteredPlayers: number; activeRegisteredPlayers: number; activePlayersInPeriod: number;
  currentShuttlecockPrice: number; currentCourtPricePerDay: number; courtSplitMethod: CourtSplitMethod;
  summary: Summary; daily: DailyRow[]; players: PlayerRow[]; queue: QueueState; recentActivity: AuditEntry[];
}

export interface CountPoint { key: string; games: number }

export interface PlayerStatistics {
  player: PlayerRef; from: string; to: string; allTimeGames: number; allTime: PlayerRow; period: PlayerRow;
  perDay: CountPoint[]; perWeek: CountPoint[]; perMonth: CountPoint[]; scoreEnabled: boolean;
  courtSplitMethod: CourtSplitMethod; recentMatches: Match[];
}

export interface UserAccount {
  id: number; username: string; role: Role; enabled: boolean; playerId: number | null; playerNickname: string | null;
  lastLoginAt: string | null; createdAt: string;
}

export type PeriodPreset = 'TODAY' | 'WEEK' | 'MONTH' | 'CUSTOM';
export interface PeriodValue { period: PeriodPreset; from?: string; to?: string }
