const rupiahFmt = new Intl.NumberFormat('id-ID', { style: 'currency', currency: 'IDR', maximumFractionDigits: 0 });
const numberFmt = new Intl.NumberFormat('id-ID');
export const CLUB_TZ = 'Asia/Jakarta';

export const rupiah = (v: number | null | undefined) => rupiahFmt.format(v ?? 0).replace(/\s/g, '');
export const num = (v: number | null | undefined) => numberFmt.format(v ?? 0);

export function formatDate(iso: string | null | undefined, opts: Intl.DateTimeFormatOptions = { day: 'numeric', month: 'short', year: 'numeric' }) {
  if (!iso) return '-';
  // Plain local dates (YYYY-MM-DD) are rendered as-is, without timezone shifting.
  const d = iso.length === 10 ? new Date(iso + 'T00:00:00') : new Date(iso);
  return new Intl.DateTimeFormat('id-ID', iso.length === 10 ? opts : { ...opts, timeZone: CLUB_TZ }).format(d);
}

export function formatDay(iso: string) {
  return formatDate(iso, { weekday: 'short', day: 'numeric', month: 'short' });
}

export function formatTime(iso: string | null | undefined) {
  if (!iso) return '-';
  return new Intl.DateTimeFormat('id-ID', { hour: '2-digit', minute: '2-digit', timeZone: CLUB_TZ }).format(new Date(iso));
}

export function formatDateTime(iso: string | null | undefined) {
  if (!iso) return '-';
  return new Intl.DateTimeFormat('id-ID', {
    day: 'numeric', month: 'short', hour: '2-digit', minute: '2-digit', timeZone: CLUB_TZ,
  }).format(new Date(iso));
}

export function duration(seconds: number | null | undefined) {
  if (seconds == null || seconds < 0) return '-';
  const h = Math.floor(seconds / 3600);
  const m = Math.floor((seconds % 3600) / 60);
  const s = Math.floor(seconds % 60);
  const mm = String(m).padStart(2, '0');
  const ss = String(s).padStart(2, '0');
  return h > 0 ? `${h}:${mm}:${ss}` : `${mm}:${ss}`;
}

export function durationShort(seconds: number | null | undefined) {
  if (seconds == null) return '-';
  const m = Math.round(seconds / 60);
  return m >= 60 ? `${Math.floor(m / 60)}j ${m % 60}m` : `${m} mnt`;
}

/** Today's date in the club timezone as YYYY-MM-DD. */
export function todayLocal(): string {
  return new Intl.DateTimeFormat('en-CA', { timeZone: CLUB_TZ }).format(new Date());
}

export function currentMonth(): string {
  return todayLocal().slice(0, 7);
}

export function teamLabel(team: { nickname: string }[]) {
  return team.map((p) => p.nickname).join(' - ');
}

/** Fills missing dates with zero rows so charts show continuous days (max ~92 days). */
export function fillDates<T extends { date: string }>(rows: T[], from: string, to: string, empty: (date: string) => T): T[] {
  const start = new Date(from + 'T00:00:00Z');
  const end = new Date(to + 'T00:00:00Z');
  const days = Math.round((end.getTime() - start.getTime()) / 86400000) + 1;
  if (days > 92 || days <= 0) return rows;
  const map = new Map(rows.map((r) => [r.date, r]));
  const out: T[] = [];
  for (let i = 0; i < days; i++) {
    const d = new Date(start.getTime() + i * 86400000).toISOString().slice(0, 10);
    out.push(map.get(d) ?? empty(d));
  }
  return out;
}

/** Client-side mirror of the backend period presets (week starts Monday). */
export function resolvePeriod(v: { period: string; from?: string; to?: string }): { from: string; to: string } {
  const today = todayLocal();
  const d = new Date(today + 'T00:00:00Z');
  const iso = (x: Date) => x.toISOString().slice(0, 10);
  switch (v.period) {
    case 'TODAY':
      return { from: today, to: today };
    case 'WEEK': {
      const dow = (d.getUTCDay() + 6) % 7; // Monday = 0
      const start = new Date(d.getTime() - dow * 86400000);
      return { from: iso(start), to: iso(new Date(start.getTime() + 6 * 86400000)) };
    }
    case 'MONTH': {
      const start = new Date(Date.UTC(d.getUTCFullYear(), d.getUTCMonth(), 1));
      const end = new Date(Date.UTC(d.getUTCFullYear(), d.getUTCMonth() + 1, 0));
      return { from: iso(start), to: iso(end) };
    }
    default:
      return { from: v.from ?? today, to: v.to ?? today };
  }
}
