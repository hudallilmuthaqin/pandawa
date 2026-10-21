import { useMemo, useState } from 'react';
import { clsx } from 'clsx';
import { Search, X } from 'lucide-react';
import { usePlayers } from '../lib/queries';
import { Input, Spinner } from './ui';

export type Slots = [number | null, number | null, number | null, number | null];

/**
 * Four-slot picker: Pasangan 1 (slot 0,1) vs Pasangan 2 (slot 2,3).
 * Tapping a player fills the active (or next empty) slot; a player can never occupy two slots.
 */
export function PlayerPicker({ slots, onChange }: { slots: Slots; onChange: (s: Slots) => void }) {
  const [q, setQ] = useState('');
  const [active, setActive] = useState<number | null>(null);
  const { data, isLoading } = usePlayers({ status: 'ACTIVE', size: 200 });
  const players = useMemo(() => data?.content ?? [], [data]);
  const byId = useMemo(() => new Map(players.map((p) => [p.id, p])), [players]);
  const filtered = players.filter((p) => !q || p.nickname.toLowerCase().includes(q.toLowerCase()) || p.fullName.toLowerCase().includes(q.toLowerCase()));

  const pick = (id: number) => {
    const next = [...slots] as Slots;
    const existing = next.indexOf(id);
    if (existing >= 0) {
      next[existing] = null;
      onChange(next);
      return;
    }
    const target = active != null ? active : next.indexOf(null);
    if (target < 0) return;
    next[target] = id;
    onChange(next);
    const following = next.indexOf(null);
    setActive(following >= 0 ? following : null);
  };

  const slot = (i: number) => {
    const p = slots[i] != null ? byId.get(slots[i]!) : null;
    return (
      <button key={i} type="button" onClick={() => {
        if (p) {
          const next = [...slots] as Slots;
          next[i] = null;
          onChange(next);
          setActive(i);
        } else setActive(i);
      }}
        className={clsx('flex h-12 min-w-0 items-center justify-between gap-1 rounded-xl px-3 text-left text-sm font-semibold ring-1 transition',
          p ? 'bg-navy-900 text-white ring-navy-900' : 'border-2 border-dashed border-slate-300 bg-white text-slate-400 ring-0',
          active === i && 'ring-2 ring-brand-500')}>
        <span className="truncate">{p ? p.nickname : `Pemain ${(i % 2) + 1}`}</span>
        {p && <X className="size-4 shrink-0 opacity-70" />}
      </button>
    );
  };

  return (
    <div className="space-y-4">
      <div className="grid grid-cols-[1fr_auto_1fr] items-center gap-2">
        <div className="space-y-2"><p className="text-xs font-semibold text-slate-500">PASANGAN 1</p>{slot(0)}{slot(1)}</div>
        <span className="mt-6 rounded-lg bg-brand-500 px-2 py-1 text-xs font-black text-white">VS</span>
        <div className="space-y-2"><p className="text-right text-xs font-semibold text-slate-500">PASANGAN 2</p>{slot(2)}{slot(3)}</div>
      </div>
      <div className="relative">
        <Search className="pointer-events-none absolute top-3 left-3 size-5 text-slate-400" />
        <Input className="pl-10" placeholder="Cari pemain…" value={q} onChange={(e) => setQ(e.target.value)} />
      </div>
      {isLoading ? <div className="flex justify-center py-4"><Spinner /></div> : (
        <div className="flex flex-wrap gap-2">
          {filtered.map((p) => {
            const idx = slots.indexOf(p.id);
            return (
              <button key={p.id} type="button" onClick={() => pick(p.id)}
                className={clsx('h-11 rounded-full px-4 text-sm font-semibold ring-1 transition',
                  idx >= 0 ? 'bg-brand-500 text-white ring-brand-500' : 'bg-white text-slate-700 ring-slate-200 hover:ring-brand-300')}>
                {p.nickname}{idx >= 0 && <span className="ml-1.5 text-xs opacity-80">P{idx < 2 ? 1 : 2}</span>}
              </button>
            );
          })}
          {filtered.length === 0 && <p className="text-sm text-slate-500">Tidak ada pemain aktif yang cocok.</p>}
        </div>
      )}
    </div>
  );
}
