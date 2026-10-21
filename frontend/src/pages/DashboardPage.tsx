import { useState } from 'react';
import { Link } from 'react-router-dom';
import { Activity, ArrowRight, Feather, ListOrdered, Radio, Tag, Trophy, UserCheck, Users, Wallet, LandPlot } from 'lucide-react';
import { useAuth } from '../auth/AuthContext';
import { useDashboard, useMyStats, useQueue } from '../lib/queries';
import { Card, CardHeader, EmptyState, ErrorBox, LoadingBlock, PageHeader, StatCard } from '../components/ui';
import { MatchCard, Versus } from '../components/MatchCard';
import { PeriodPicker } from '../components/PeriodPicker';
import { COURT_METHOD_NOTE, ChartCard, ExpenseBreakdown, PlayerRecap } from '../components/ReportBlocks';
import { CountChart, ExpenseChart, withEmptyDays } from '../components/Charts';
import { duration, durationShort, formatDate, formatDateTime, num, rupiah } from '../lib/format';
import { useElapsed } from './LivePage';
import type { Match, PeriodValue } from '../lib/types';

function LiveCard({ match }: { match: Match | null | undefined }) {
  const elapsed = useElapsed(match?.startedAt);
  return (
    <Card>
      <CardHeader title="Game berlangsung" icon={<Radio className="size-5" />} action={<Link to="/live" className="text-sm font-semibold text-brand-600">Buka</Link>} />
      <div className="px-4 pb-4 sm:px-5">
        {match ? (
          <div className="rounded-2xl bg-navy-900 p-4 text-white [&_p]:text-white">
            <Versus match={match} />
            <div className="tabular mt-3 flex justify-between text-sm text-navy-100/80">
              <span>{duration(elapsed)}</span><span>{match.shuttlecockCount} kok · {rupiah(match.shuttlecockCost)}</span>
            </div>
          </div>
        ) : <p className="py-4 text-center text-sm text-slate-500">Lapangan sedang kosong.</p>}
      </div>
    </Card>
  );
}

function NextUp({ queue, highlight }: { queue: Match[]; highlight?: number | null }) {
  return (
    <Card>
      <CardHeader title="Antrean berikutnya" icon={<ListOrdered className="size-5" />} subtitle={`${queue.length} pertandingan menunggu`}
        action={<Link to="/queue" className="text-sm font-semibold text-brand-600">Semua</Link>} />
      <div className="space-y-2 px-4 pb-4 sm:px-5">
        {queue.length === 0 ? <p className="py-4 text-center text-sm text-slate-500">Antrean kosong.</p>
          : queue.slice(0, 3).map((m) => <MatchCard key={m.id} match={m} highlight={highlight} className="shadow-none" />)}
      </div>
    </Card>
  );
}

function AdminDashboard() {
  const [period, setPeriod] = useState<PeriodValue>({ period: 'TODAY' });
  const { data, isLoading, error } = useDashboard(period);
  const { data: queue } = useQueue();

  return (
    <div className="space-y-4">
      <PageHeader title="Dashboard" subtitle={data ? `${formatDate(data.from)} – ${formatDate(data.to)}` : undefined} />
      <PeriodPicker value={period} onChange={setPeriod} />
      {error && <ErrorBox error={error} />}
      {isLoading || !data ? <LoadingBlock /> : (
        <>
          <div className="grid gap-4 lg:grid-cols-2">
            <LiveCard match={queue?.current ?? data.queue.current} />
            <NextUp queue={queue?.queue ?? data.queue.queue} />
          </div>

          <div className="grid grid-cols-2 gap-3 lg:grid-cols-3 xl:grid-cols-5">
            <StatCard tone="navy" label="Total pengeluaran" value={rupiah(data.summary.totalExpense)} hint="Kok klub + lapangan" icon={<Wallet />} />
            <StatCard label="Game selesai" value={num(data.summary.totalGames)} icon={<Trophy />} />
            <StatCard label="Kok terpakai" value={num(data.summary.totalShuttlecocks)} hint={rupiah(data.summary.shuttlecockCost)} icon={<Feather />} />
            <StatCard label="Biaya lapangan" value={rupiah(data.summary.courtCost)} hint={`${data.summary.playDays} hari bermain`} icon={<LandPlot />} />
            <StatCard label="Pemain aktif periode" value={num(data.activePlayersInPeriod)} hint={`dari ${num(data.totalRegisteredPlayers)} terdaftar`} icon={<UserCheck />} />
            <StatCard label="Pemain terdaftar" value={num(data.totalRegisteredPlayers)} hint={`${num(data.activeRegisteredPlayers)} berstatus aktif`} icon={<Users />} />
            <StatCard label="Harga kok / pemain / game" value={rupiah(data.currentShuttlecockPrice)} icon={<Tag />} />
            <StatCard label="Harga lapangan / hari" value={rupiah(data.currentCourtPricePerDay)} icon={<Tag />} />
            <StatCard label="Biaya kok klub" value={rupiah(data.summary.shuttlecockCost)} hint="Dihitung sekali per game" icon={<Feather />} />
          </div>

          <div className="grid gap-4 lg:grid-cols-2">
            <ChartCard title="Game selesai per hari"><CountChart data={withEmptyDays(data.daily, data.from, data.to)} dataKey="games" name="Game" /></ChartCard>
            <ChartCard title="Pengeluaran per hari" subtitle="Biaya kok + biaya lapangan"><ExpenseChart data={withEmptyDays(data.daily, data.from, data.to)} /></ChartCard>
          </div>

          <div className="grid gap-4 lg:grid-cols-[1fr_1.4fr]">
            <ExpenseBreakdown s={data.summary} />
            <Card>
              <CardHeader title="Aktivitas terbaru" icon={<Activity className="size-5" />} />
              <ul className="divide-y divide-slate-100 pb-2">
                {data.recentActivity.length === 0 && <li className="px-5 py-4 text-sm text-slate-500">Belum ada aktivitas.</li>}
                {data.recentActivity.map((a) => (
                  <li key={a.id} className="px-4 py-2.5 sm:px-5">
                    <p className="text-sm text-slate-800">{a.summary}</p>
                    <p className="text-xs text-slate-400">{formatDateTime(a.createdAt)} · {a.actorUsername}</p>
                  </li>
                ))}
              </ul>
            </Card>
          </div>

          <PlayerRecap rows={data.players} method={data.courtSplitMethod} title="Rekap bermain per pemain" />
        </>
      )}
    </div>
  );
}

function UserDashboard() {
  const { user } = useAuth();
  const [period, setPeriod] = useState<PeriodValue>({ period: 'MONTH' });
  const { data: queue } = useQueue();
  const stats = useMyStats(period, !!user?.playerId);
  const s = stats.data;

  return (
    <div className="space-y-4">
      <PageHeader title={`Halo, ${user?.playerNickname ?? user?.username}!`} subtitle="Ringkasan bermain Anda" />
      <div className="grid gap-4 lg:grid-cols-2">
        <LiveCard match={queue?.current} />
        <NextUp queue={queue?.queue ?? []} highlight={user?.playerId} />
      </div>

      {!user?.playerId ? (
        <Card><EmptyState title="Akun belum terhubung ke profil pemain" description="Hubungi admin agar akun Anda dihubungkan dengan data pemain." /></Card>
      ) : (
        <>
          <PeriodPicker value={period} onChange={setPeriod} />
          {stats.error && <ErrorBox error={stats.error} />}
          {!s ? <LoadingBlock /> : (
            <>
              <div className="grid grid-cols-2 gap-3 lg:grid-cols-4">
                <StatCard tone="brand" label="Estimasi biaya pribadi" value={rupiah(s.period.totalEstimate)} hint={`Kok ${rupiah(s.period.shuttlecockCost)} · Lap. ${rupiah(s.period.courtShare)}`} icon={<Wallet />} />
                <StatCard label="Main (periode)" value={num(s.period.games)} hint={`${s.period.playDays} hari bermain`} icon={<Trophy />} />
                <StatCard label="Kok di game Anda" value={num(s.period.shuttlecocks)} hint={rupiah(s.period.shuttlecockCost)} icon={<Feather />} />
                <StatCard label="Total main sepanjang waktu" value={num(s.allTimeGames)} hint={s.scoreEnabled ? `${s.allTime.wins} menang · ${s.allTime.losses} kalah` : undefined} icon={<Activity />} />
              </div>
              <p className="text-xs text-slate-500">
                Biaya kok pribadi = harga kok × jumlah game yang Anda ikuti (harga saat game dimainkan, berapa pun kok yang dipakai).
                {' '}{COURT_METHOD_NOTE[s.courtSplitMethod]}
              </p>
              <Card>
                <CardHeader title="Riwayat bermain terbaru" icon={<Activity className="size-5" />} action={<Link to="/history" className="flex items-center gap-1 text-sm font-semibold text-brand-600">Semua <ArrowRight className="size-4" /></Link>} />
                <ul className="divide-y divide-slate-100 pb-2">
                  {s.recentMatches.length === 0 && <li className="px-5 py-6 text-center text-sm text-slate-500">Belum ada game selesai.</li>}
                  {s.recentMatches.map((m) => (
                    <li key={m.id} className="px-4 py-3 sm:px-5">
                      <div className="flex justify-between text-xs text-slate-500">
                        <span>{formatDate(m.playDate)} · {durationShort(m.durationSeconds)}</span>
                        <span className="tabular font-semibold text-slate-700">{m.shuttlecockCount} kok · {rupiah(m.shuttlecockCost)}</span>
                      </div>
                      <div className="mt-1.5"><Versus match={m} highlight={user.playerId} /></div>
                    </li>
                  ))}
                </ul>
              </Card>
            </>
          )}
        </>
      )}
    </div>
  );
}

export default function DashboardPage() {
  const { isAdmin } = useAuth();
  return isAdmin ? <AdminDashboard /> : <UserDashboard />;
}
