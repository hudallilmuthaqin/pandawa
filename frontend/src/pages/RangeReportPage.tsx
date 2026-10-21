import { useState } from 'react';
import { useRangeReport } from '../lib/queries';
import { periodQs } from '../lib/api';
import { ErrorBox, LoadingBlock, PageHeader, StatCard } from '../components/ui';
import { PeriodPicker } from '../components/PeriodPicker';
import { ChartCard, DailyRecap, ExpenseBreakdown, ExportButton, PlayerRecap, SummaryCards } from '../components/ReportBlocks';
import { CountChart, ExpenseChart, withEmptyDays } from '../components/Charts';
import { formatDate, num, rupiah } from '../lib/format';
import type { PeriodValue } from '../lib/types';

export default function RangeReportPage() {
  const [period, setPeriod] = useState<PeriodValue>({ period: 'MONTH' });
  const { data, isLoading, error, isFetching } = useRangeReport(period);
  const days = data ? withEmptyDays(data.daily, data.from, data.to) : [];
  return (
    <div className="space-y-4">
      <PageHeader title="Rekap Range Date" subtitle={data ? `${formatDate(data.from)} – ${formatDate(data.to)}${isFetching ? ' · memuat…' : ''}` : undefined}
        action={<ExportButton path={'/api/reports/export' + periodQs(period, { type: 'matches' })} label="CSV Game" />} />
      <PeriodPicker value={period} onChange={setPeriod} />
      <ErrorBox error={error} />
      {isLoading || !data ? (!error && <LoadingBlock />) : (
        <>
          <SummaryCards s={data.summary} />
          <div className="grid grid-cols-3 gap-3">
            <StatCard label="Pemain unik" value={num(data.summary.uniquePlayers)} />
            <StatCard label="Hari bermain" value={num(data.summary.playDays)} />
            <StatCard label="Biaya kok klub" value={rupiah(data.summary.shuttlecockCost)} />
          </div>
          <ExpenseBreakdown s={data.summary} />
          <div className="grid gap-4 lg:grid-cols-2">
            <ChartCard title="Jumlah game per hari"><CountChart data={days} dataKey="games" name="Game" /></ChartCard>
            <ChartCard title="Pemakaian kok per hari"><CountChart data={days} dataKey="shuttlecocks" name="Kok" /></ChartCard>
          </div>
          <ChartCard title="Pengeluaran per hari" subtitle="Biaya kok + biaya lapangan = total pengeluaran harian">
            <ExpenseChart data={days} height={260} />
          </ChartCard>
          <PlayerRecap rows={data.players} method={data.currentCourtSplitMethod} exportPath={'/api/reports/export' + periodQs(period, { type: 'players' })} />
          <DailyRecap rows={data.daily} exportPath={'/api/reports/export' + periodQs(period, { type: 'daily' })} />
        </>
      )}
    </div>
  );
}
