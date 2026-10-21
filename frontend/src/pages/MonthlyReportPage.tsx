import { useSearchParams } from 'react-router-dom';
import { Trophy } from 'lucide-react';
import { useMonthlyReport, useSettings } from '../lib/queries';
import { qs } from '../lib/api';
import { Card, CardHeader, ErrorBox, Input, LoadingBlock, PageHeader, StatCard } from '../components/ui';
import { ChartCard, DailyRecap, ExpenseBreakdown, ExportButton, PlayerRecap, SummaryCards } from '../components/ReportBlocks';
import { CountChart, ExpenseChart, withEmptyDays } from '../components/Charts';
import { currentMonth, formatDate, num } from '../lib/format';

export default function MonthlyReportPage() {
  const [params, setParams] = useSearchParams();
  const month = params.get('month') ?? currentMonth();
  const { data, isLoading, error } = useMonthlyReport(month);
  const { data: settings } = useSettings();
  const days = data ? withEmptyDays(data.daily, data.from, data.to) : [];
  const exportQs = (type: string) => '/api/reports/export' + qs({ type, period: 'CUSTOM', from: data?.from, to: data?.to });
  return (
    <div className="space-y-4">
      <PageHeader title="Rekap Bulanan" subtitle={formatDate(month + '-01', { month: 'long', year: 'numeric' })}
        action={data && <ExportButton path={exportQs('daily')} />} />
      <Input type="month" value={month} onChange={(e) => e.target.value && setParams({ month: e.target.value })} className="sm:max-w-xs" />
      <ErrorBox error={error} />
      {isLoading || !data ? (!error && <LoadingBlock />) : (
        <>
          <SummaryCards s={data.summary} />
          <div className="grid grid-cols-2 gap-3">
            <StatCard label="Rata-rata game / hari bermain" value={Number(data.averageGamesPerPlayDay).toLocaleString('id-ID')} />
            <StatCard label="Total hari bermain" value={num(data.summary.playDays)} />
          </div>
          <ExpenseBreakdown s={data.summary} />
          <div className="grid gap-4 lg:grid-cols-2">
            <ChartCard title="Aktivitas harian" subtitle="Game selesai per hari"><CountChart data={days} dataKey="games" name="Game" /></ChartCard>
            <ChartCard title="Pengeluaran per hari"><ExpenseChart data={days} /></ChartCard>
          </div>
          <Card>
            <CardHeader title="Pemain paling aktif" icon={<Trophy className="size-5" />} />
            <ol className="space-y-2 px-4 pb-4 sm:px-5">
              {data.mostActive.length === 0 && <li className="text-sm text-slate-500">Belum ada data.</li>}
              {data.mostActive.map((p, i) => (
                <li key={p.playerId} className="flex items-center gap-3">
                  <span className="flex size-8 items-center justify-center rounded-lg bg-brand-50 text-sm font-black text-brand-700">{i + 1}</span>
                  <span className="flex-1 font-semibold text-navy-900">{p.nickname}</span>
                  <span className="tabular text-sm text-slate-600"><b className="text-navy-900">{p.games}</b> game · {p.playDays} hari</span>
                </li>
              ))}
            </ol>
          </Card>
          <DailyRecap rows={data.daily} title="Rekap biaya per tanggal" exportPath={exportQs('daily')} />
          <PlayerRecap rows={data.players} method={settings?.courtSplitMethod ?? 'EQUAL_SPLIT'} exportPath={exportQs('players')} />
        </>
      )}
    </div>
  );
}
