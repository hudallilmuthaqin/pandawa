import { useState } from 'react';
import { Link } from 'react-router-dom';
import { Download, CalendarDays, Feather, Trophy, Users, Wallet, LandPlot } from 'lucide-react';
import type { DailyRow, PlayerRow, Summary, CourtSplitMethod } from '../lib/types';
import { formatDay, num, rupiah } from '../lib/format';
import { downloadCsv } from '../lib/api';
import { Button, Card, CardHeader, EmptyState, StatCard } from './ui';
import { useToast, errorMessage } from './Toast';

export function SummaryCards({ s }: { s: Summary }) {
  return (
    <div className="grid grid-cols-2 gap-3 lg:grid-cols-4">
      <StatCard tone="navy" label="Total pengeluaran klub" value={rupiah(s.totalExpense)} hint="Kok + lapangan" icon={<Wallet />} />
      <StatCard label="Game selesai" value={num(s.totalGames)} hint={`${num(s.uniquePlayers)} pemain unik`} icon={<Trophy />} />
      <StatCard label="Kok terpakai" value={num(s.totalShuttlecocks)} hint={rupiah(s.shuttlecockCost)} icon={<Feather />} />
      <StatCard label="Biaya lapangan" value={rupiah(s.courtCost)} hint={`${num(s.playDays)} hari bermain`} icon={<LandPlot />} />
    </div>
  );
}

/** Exact formula breakdown shown under the totals. */
export function ExpenseBreakdown({ s }: { s: Summary }) {
  return (
    <Card className="p-4 sm:p-5">
      <p className="mb-3 text-sm font-bold text-navy-900">Rincian pengeluaran klub</p>
      <dl className="space-y-2 text-sm">
        <div className="flex justify-between gap-3"><dt className="text-slate-600">Biaya kok klub <span className="text-slate-400">({num(s.totalShuttlecocks)} kok, harga snapshot per game)</span></dt><dd className="tabular font-semibold">{rupiah(s.shuttlecockCost)}</dd></div>
        <div className="flex justify-between gap-3"><dt className="text-slate-600">Biaya lapangan <span className="text-slate-400">({num(s.playDays)} hari bermain)</span></dt><dd className="tabular font-semibold">{rupiah(s.courtCost)}</dd></div>
        <div className="flex justify-between gap-3 border-t border-dashed border-slate-200 pt-2 text-base"><dt className="font-bold text-navy-900">Total pengeluaran</dt><dd className="tabular font-extrabold text-brand-600">{rupiah(s.totalExpense)}</dd></div>
      </dl>
    </Card>
  );
}

export function ExportButton({ path, label = 'Ekspor CSV' }: { path: string; label?: string }) {
  const [busy, setBusy] = useState(false);
  const toast = useToast();
  return (
    <Button variant="secondary" size="sm" loading={busy} onClick={async () => {
      setBusy(true);
      try {
        await downloadCsv(path);
      } catch (e) {
        toast(errorMessage(e), 'error');
      } finally {
        setBusy(false);
      }
    }}><Download className="size-4" />{label}</Button>
  );
}

export const COURT_METHOD_NOTE: Record<CourtSplitMethod, string> = {
  EQUAL_SPLIT: 'Biaya lapangan dibagi rata ke pemain unik per tanggal.',
  PER_PLAYER: 'Setiap pemain bayar harga lapangan per hari secara penuh (sekali per tanggal bermain).',
  CLUB_ONLY: 'Biaya lapangan hanya dicatat sebagai pengeluaran klub.',
};

export function SplitMethodNote({ method }: { method: CourtSplitMethod }) {
  return (
    <p className="text-xs text-slate-500">
      Biaya kok pemain = harga kok × jumlah game yang diikuti (berapa pun kok yang dipakai).{' '}
      {COURT_METHOD_NOTE[method]}
    </p>
  );
}

export function PlayerRecap({ rows, exportPath, method, title = 'Rekap per pemain' }: { rows: PlayerRow[]; exportPath?: string; method: CourtSplitMethod; title?: string }) {
  const showCourt = method !== 'CLUB_ONLY' || rows.some((r) => r.courtShare > 0);
  return (
    <Card>
      <CardHeader title={title} icon={<Users className="size-5" />} subtitle={`${rows.length} pemain`} action={exportPath && <ExportButton path={exportPath} />} />
      <div className="px-4 pb-1 sm:px-5"><SplitMethodNote method={method} /></div>
      {rows.length === 0 ? <EmptyState title="Belum ada game selesai" description="Belum ada data pada periode ini." /> : (
        <>
          {/* phones: cards */}
          <ul className="divide-y divide-slate-100 md:hidden">
            {rows.map((r) => (
              <li key={r.playerId} className="px-4 py-3">
                <div className="flex items-center justify-between gap-2">
                  <Link to={`/players/${r.playerId}`} className="truncate font-bold text-navy-900">{r.nickname}</Link>
                  <span className="tabular text-base font-extrabold text-brand-600">{rupiah(r.totalEstimate)}</span>
                </div>
                <div className="mt-1 flex flex-wrap gap-x-4 gap-y-0.5 text-xs text-slate-500">
                  <span><b className="text-slate-700">{r.games}</b> main</span>
                  <span><b className="text-slate-700">{r.shuttlecocks}</b> kok · {rupiah(r.shuttlecockCost)}</span>
                  {showCourt && <span>Lapangan {rupiah(r.courtShare)}</span>}
                  {(r.wins > 0 || r.losses > 0) && <span>{r.wins}M / {r.losses}K</span>}
                </div>
              </li>
            ))}
          </ul>
          {/* tablet & desktop: table */}
          <div className="hidden overflow-x-auto md:block">
            <table className="w-full text-sm">
              <thead className="bg-slate-50 text-left text-xs font-semibold text-slate-500 uppercase">
                <tr>
                  <th className="px-5 py-2.5">Nama</th><th className="px-3 py-2.5 text-right">Main</th><th className="px-3 py-2.5 text-right">Alokasi kok</th>
                  <th className="px-3 py-2.5 text-right">Biaya kok</th>{showCourt && <th className="px-3 py-2.5 text-right">Lapangan</th>}
                  <th className="px-5 py-2.5 text-right">Total estimasi</th>
                </tr>
              </thead>
              <tbody className="tabular divide-y divide-slate-100">
                {rows.map((r) => (
                  <tr key={r.playerId} className="hover:bg-slate-50">
                    <td className="px-5 py-2.5"><Link to={`/players/${r.playerId}`} className="font-semibold text-navy-900 hover:text-brand-600">{r.nickname}</Link>
                      {r.fullName && <span className="block text-xs text-slate-400">{r.fullName}</span>}</td>
                    <td className="px-3 py-2.5 text-right">{r.games}</td>
                    <td className="px-3 py-2.5 text-right">{r.shuttlecocks}</td>
                    <td className="px-3 py-2.5 text-right">{rupiah(r.shuttlecockCost)}</td>
                    {showCourt && <td className="px-3 py-2.5 text-right">{rupiah(r.courtShare)}</td>}
                    <td className="px-5 py-2.5 text-right font-bold text-navy-900">{rupiah(r.totalEstimate)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </>
      )}
    </Card>
  );
}

export function DailyRecap({ rows, exportPath, title = 'Rekap per tanggal' }: { rows: DailyRow[]; exportPath?: string; title?: string }) {
  const total = rows.reduce((s, r) => s + r.totalExpense, 0);
  return (
    <Card>
      <CardHeader title={title} icon={<CalendarDays className="size-5" />} subtitle={`${rows.length} hari bermain`} action={exportPath && <ExportButton path={exportPath} />} />
      {rows.length === 0 ? <EmptyState title="Belum ada hari bermain" description="Tanggal tanpa game selesai tidak dikenakan biaya lapangan." /> : (
        <>
          <ul className="divide-y divide-slate-100 md:hidden">
            {rows.map((d) => (
              <li key={d.date} className="px-4 py-3">
                <div className="flex items-center justify-between">
                  <Link to={`/reports/daily?date=${d.date}`} className="font-bold text-navy-900">{formatDay(d.date)}</Link>
                  <span className="tabular font-extrabold text-navy-900">{rupiah(d.totalExpense)}</span>
                </div>
                <div className="mt-1 flex flex-wrap gap-x-4 text-xs text-slate-500">
                  <span><b className="text-slate-700">{d.games}</b> game</span>
                  <span><b className="text-slate-700">{d.shuttlecocks}</b> kok · {rupiah(d.shuttlecockCost)}</span>
                  <span>Lapangan {rupiah(d.courtCost)}</span>
                </div>
              </li>
            ))}
          </ul>
          <div className="hidden overflow-x-auto md:block">
            <table className="w-full text-sm">
              <thead className="bg-slate-50 text-left text-xs font-semibold text-slate-500 uppercase">
                <tr><th className="px-5 py-2.5">Tanggal</th><th className="px-3 py-2.5 text-right">Game</th><th className="px-3 py-2.5 text-right">Kok</th>
                  <th className="px-3 py-2.5 text-right">Biaya kok</th><th className="px-3 py-2.5 text-right">Lapangan</th><th className="px-5 py-2.5 text-right">Total</th></tr>
              </thead>
              <tbody className="tabular divide-y divide-slate-100">
                {rows.map((d) => (
                  <tr key={d.date} className="hover:bg-slate-50">
                    <td className="px-5 py-2.5"><Link to={`/reports/daily?date=${d.date}`} className="font-semibold text-navy-900 hover:text-brand-600">{formatDay(d.date)}</Link></td>
                    <td className="px-3 py-2.5 text-right">{d.games}</td>
                    <td className="px-3 py-2.5 text-right">{d.shuttlecocks}</td>
                    <td className="px-3 py-2.5 text-right">{rupiah(d.shuttlecockCost)}</td>
                    <td className="px-3 py-2.5 text-right">{rupiah(d.courtCost)}</td>
                    <td className="px-5 py-2.5 text-right font-bold">{rupiah(d.totalExpense)}</td>
                  </tr>
                ))}
              </tbody>
              <tfoot className="tabular bg-slate-50 font-bold"><tr><td className="px-5 py-2.5" colSpan={5}>Total</td><td className="px-5 py-2.5 text-right">{rupiah(total)}</td></tr></tfoot>
            </table>
          </div>
        </>
      )}
    </Card>
  );
}

export function ChartCard({ title, subtitle, children }: { title: string; subtitle?: string; children: React.ReactNode }) {
  return (
    <Card className="p-4 sm:p-5">
      <p className="text-sm font-bold text-navy-900">{title}</p>
      {subtitle && <p className="text-xs text-slate-500">{subtitle}</p>}
      <div className="mt-3">{children}</div>
    </Card>
  );
}


