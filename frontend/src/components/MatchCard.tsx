import { clsx } from 'clsx';
import type { Match, PlayerRef } from '../lib/types';
import { StatusBadge } from './ui';
import { Trophy } from 'lucide-react';

export function Team({ players, highlight, winner, align = 'left' }: { players: PlayerRef[]; highlight?: number | null; winner?: boolean; align?: 'left' | 'right' }) {
  return (
    <div className={clsx('min-w-0 flex-1', align === 'right' && 'text-right')}>
      {players.map((p) => (
        <p key={p.id} className={clsx('truncate text-[15px] font-bold leading-snug',
          p.id === highlight ? 'text-brand-600' : 'text-navy-900')}>{p.nickname}</p>
      ))}
      {winner && <span className={clsx('mt-0.5 inline-flex items-center gap-1 text-xs font-semibold text-emerald-700', align === 'right' && 'justify-end')}><Trophy className="size-3" /> Menang</span>}
    </div>
  );
}

export function Versus({ match, highlight }: { match: Match; highlight?: number | null }) {
  return (
    <div className="flex items-center gap-3">
      <Team players={match.team1} highlight={highlight} winner={match.winnerTeam === 1} />
      <span className="shrink-0 rounded-lg bg-navy-900 px-2 py-1 text-xs font-black tracking-wider text-white">VS</span>
      <Team players={match.team2} highlight={highlight} winner={match.winnerTeam === 2} align="right" />
    </div>
  );
}

export function MatchCard({ match, highlight, actions, meta, className }: {
  match: Match; highlight?: number | null; actions?: React.ReactNode; meta?: React.ReactNode; className?: string;
}) {
  return (
    <div className={clsx('rounded-2xl bg-white p-4 shadow-sm ring-1', match.status === 'READY' ? 'ring-sky-300' : 'ring-slate-200/70', className)}>
      <div className="mb-3 flex items-center justify-between gap-2">
        <div className="flex items-center gap-2">
          {match.queuePosition != null && (
            <span className="flex size-7 items-center justify-center rounded-lg bg-brand-500 text-sm font-black text-white">{match.queuePosition}</span>
          )}
          <span className="text-xs font-medium text-slate-400">Game #{match.id}</span>
        </div>
        <StatusBadge status={match.status} />
      </div>
      <Versus match={match} highlight={highlight} />
      {meta && <div className="mt-3 border-t border-slate-100 pt-3 text-sm text-slate-600">{meta}</div>}
      {actions && <div className="mt-3 flex flex-wrap gap-2">{actions}</div>}
    </div>
  );
}
