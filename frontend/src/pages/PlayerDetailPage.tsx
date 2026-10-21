import { useState } from 'react';
import { Link, Navigate, useParams } from 'react-router-dom';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { Activity, ArrowLeft, CalendarDays, Feather, Pencil, Trophy, Wallet } from 'lucide-react';
import { Bar, BarChart, CartesianGrid, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts';
import { useAuth } from '../auth/AuthContext';
import { usePlayer, usePlayerStats } from '../lib/queries';
import { api } from '../lib/api';
import { Badge, Button, Card, CardHeader, ErrorBox, LoadingBlock, PageHeader, Segmented, StatCard } from '../components/ui';
import { PeriodPicker } from '../components/PeriodPicker';
import { Versus } from '../components/MatchCard';
import { SERIES } from '../components/Charts';
import { errorMessage, useToast } from '../components/Toast';
import { PlayerFormSheet } from './PlayersPage';
import { durationShort, formatDate, num, resolvePeriod, rupiah } from '../lib/format';
import type { CountPoint, PeriodValue, Player } from '../lib/types';

function FrequencyChart({ points, label }: { points: CountPoint[]; label: (k: string) => string }) {
  if (points.length === 0) return <p className="py-8 text-center text-sm text-slate-500">Belum ada game pada periode ini.</p>;
  return (
    <ResponsiveContainer width="100%" height={180}>
      <BarChart data={points} margin={{ top: 8, right: 4, left: -24, bottom: 0 }}>
        <CartesianGrid vertical={false} stroke="#eef2f6" />
        <XAxis dataKey="key" tickFormatter={label} tick={{ fontSize: 11, fill: '#64748b' }} tickLine={false} axisLine={{ stroke: '#cbd5e1' }} minTickGap={12} />
        <YAxis allowDecimals={false} tick={{ fontSize: 11, fill: '#64748b' }} tickLine={false} axisLine={false} />
        <Tooltip formatter={(v) => [`${v} game`, 'Main']} labelFormatter={(k) => label(String(k))} cursor={{ fill: 'rgba(15,27,61,0.05)' }} />
        <Bar isAnimationActive={false} dataKey="games" fill={SERIES.shuttle} radius={[4, 4, 0, 0]} maxBarSize={28} />
      </BarChart>
    </ResponsiveContainer>
  );
}

function PlayerDetail({ id }: { id: number }) {
  const { isAdmin } = useAuth();
  const [period, setPeriod] = useState<PeriodValue>({ period: 'MONTH' });
  const [freq, setFreq] = useState<'day' | 'week' | 'month'>('day');
  const [editing, setEditing] = useState(false);
  const range = resolvePeriod(period);
  const player = usePlayer(id);
  const stats = usePlayerStats(id, range.from, range.to);
  const qc = useQueryClient();
  const toast = useToast();
  const toggle = useMutation({
    mutationFn: (p: Player) => api.patch<Player>(`/api/players/${p.id}/status`, { status: p.status === 'ACTIVE' ? 'INACTIVE' : 'ACTIVE' }),
    onSuccess: (p) => {
      qc.invalidateQueries({ queryKey: ['player', id] });
      qc.invalidateQueries({ queryKey: ['players'] });
      toast(p.status === 'ACTIVE' ? 'Pemain diaktifkan' : 'Pemain dinonaktifkan (riwayat tetap tersimpan)');
    },
    onError: (e) => toast(errorMessage(e), 'error'),
  });

  if (player.isLoading) return <LoadingBlock />;
  if (player.error) return <ErrorBox error={player.error} />;
  const p = player.data!;
  const s = stats.data;
  const points = s ? (freq === 'day' ? s.perDay : freq === 'week' ? s.perWeek : s.perMonth) : [];
  const labelFor = (k: string) => freq === 'month' ? formatDate(k + '-01', { month: 'short', year: '2-digit' })
    : freq === 'week' ? 'Mg ' + formatDate(k, { day: 'numeric', month: 'short' }) : formatDate(k, { day: 'numeric', month: 'short' });

  return (
    <div className="space-y-4">
      {isAdmin && <Link to="/players" className="inline-flex items-center gap-1 text-sm font-medium text-slate-500"><ArrowLeft className="size-4" /> Daftar pemain</Link>}
      <Card className="p-4 sm:p-5">
        <div className="flex items-center gap-4">
          <div className="flex size-16 shrink-0 items-center justify-center rounded-2xl bg-brand-500 text-2xl font-black text-white">{p.nickname.slice(0, 2).toUpperCase()}</div>
          <div className="min-w-0 flex-1">
            <h1 className="truncate text-xl font-extrabold text-navy-900">{p.nickname}</h1>
            <p className="truncate text-sm text-slate-500">{p.fullName}</p>
            <div className="mt-1 flex flex-wrap gap-1.5">
              <Badge tone={p.status === 'ACTIVE' ? 'green' : 'red'}>{p.status === 'ACTIVE' ? 'Aktif' : 'Nonaktif'}</Badge>
              <Badge>Daftar {formatDate(p.registeredOn)}</Badge>
              {p.username && <Badge tone="navy">@{p.username}</Badge>}
              {p.phone && <Badge>{p.phone}</Badge>}
            </div>
          </div>
        </div>
        {isAdmin && (
          <div className="mt-4 flex gap-2">
            <Button variant="secondary" size="sm" onClick={() => setEditing(true)}><Pencil className="size-4" /> Edit</Button>
            <Button variant={p.status === 'ACTIVE' ? 'ghost' : 'primary'} size="sm" loading={toggle.isPending} onClick={() => toggle.mutate(p)}>
              {p.status === 'ACTIVE' ? 'Nonaktifkan' : 'Aktifkan'}
            </Button>
          </div>
        )}
      </Card>

      <PeriodPicker value={period} onChange={setPeriod} />
      {stats.error && <ErrorBox error={stats.error} />}
      {!s ? <LoadingBlock /> : (
        <>
          <div className="grid grid-cols-2 gap-3 lg:grid-cols-4">
            <StatCard tone="navy" label="Total main sepanjang waktu" value={num(s.allTimeGames)} icon={<Activity />} />
            <StatCard label="Main periode ini" value={num(s.period.games)} hint={`${s.period.playDays} hari bermain`} icon={<CalendarDays />} />
            <StatCard label="Alokasi kok" value={num(s.period.shuttlecocks)} hint={rupiah(s.period.shuttlecockCost)} icon={<Feather />} />
            <StatCard tone="brand" label="Estimasi biaya pribadi" value={rupiah(s.period.totalEstimate)}
              hint={s.courtSplitMethod !== 'CLUB_ONLY' ? `Lapangan ${rupiah(s.period.courtShare)}` : 'Lapangan: kas klub'} icon={<Wallet />} />
          </div>
          {s.scoreEnabled && (s.allTime.wins + s.allTime.losses > 0) && (
            <Card className="flex items-center gap-4 p-4">
              <Trophy className="size-8 text-brand-500" />
              <div className="tabular flex-1 text-sm">
                <p className="font-bold text-navy-900">{s.period.wins} menang · {s.period.losses} kalah <span className="font-normal text-slate-500">(periode)</span></p>
                <p className="text-slate-500">Sepanjang waktu: {s.allTime.wins} M / {s.allTime.losses} K
                  {s.allTime.wins + s.allTime.losses > 0 && ` · ${Math.round((s.allTime.wins / (s.allTime.wins + s.allTime.losses)) * 100)}% menang`}</p>
              </div>
            </Card>
          )}
          <Card className="p-4 sm:p-5">
            <div className="mb-3 flex flex-wrap items-center justify-between gap-2">
              <p className="text-sm font-bold text-navy-900">Frekuensi bermain</p>
              <Segmented value={freq} onChange={setFreq} options={[{ value: 'day', label: 'Harian' }, { value: 'week', label: 'Mingguan' }, { value: 'month', label: 'Bulanan' }]} />
            </div>
            <FrequencyChart points={points} label={labelFor} />
          </Card>
          <Card>
            <CardHeader title="Riwayat pertandingan" icon={<Activity className="size-5" />}
              action={<Link to={isAdmin ? `/history?playerId=${id}` : '/history'} className="text-sm font-semibold text-brand-600">Semua</Link>} />
            <ul className="divide-y divide-slate-100 pb-2">
              {s.recentMatches.length === 0 && <li className="px-5 py-6 text-center text-sm text-slate-500">Belum ada game selesai.</li>}
              {s.recentMatches.map((m) => (
                <li key={m.id} className="px-4 py-3 sm:px-5">
                  <div className="flex justify-between text-xs text-slate-500">
                    <span>{formatDate(m.playDate)} · {durationShort(m.durationSeconds)}</span>
                    <span className="tabular font-semibold text-slate-700">{m.shuttlecockCount} kok · {rupiah(m.shuttlecockCost)}</span>
                  </div>
                  <div className="mt-1.5"><Versus match={m} highlight={id} /></div>
                </li>
              ))}
            </ul>
          </Card>
        </>
      )}
      {isAdmin && <PlayerFormSheet open={editing} player={p} onClose={() => setEditing(false)} />}
    </div>
  );
}

export default function PlayerDetailPage() {
  const { id } = useParams();
  const { isAdmin, user } = useAuth();
  // Users may only view their own player profile.
  if (!isAdmin && user?.playerId !== Number(id)) return <Navigate to="/me" replace />;
  return <PlayerDetail key={id} id={Number(id)} />;
}

export function MyProfilePage() {
  const { user } = useAuth();
  if (!user?.playerId) return <PageHeader title="Profil" subtitle="Akun Anda belum terhubung dengan profil pemain." />;
  return <PlayerDetail id={user.playerId} />;
}
