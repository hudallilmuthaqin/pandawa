import { useState } from 'react';
import { Feather, LandPlot, Wallet } from 'lucide-react';
import { useRangeReport, useSettings } from '../lib/queries';
import { periodQs } from '../lib/api';
import { Card, ErrorBox, LoadingBlock, PageHeader, StatCard } from '../components/ui';
import { PeriodPicker } from '../components/PeriodPicker';
import { ChartCard, DailyRecap, ExpenseBreakdown } from '../components/ReportBlocks';
import { ExpenseChart, withEmptyDays } from '../components/Charts';
import { formatDate, num, rupiah } from '../lib/format';
import type { PeriodValue } from '../lib/types';

export default function ExpensesPage() {
  const [period, setPeriod] = useState<PeriodValue>({ period: 'MONTH' });
  const { data, isLoading, error } = useRangeReport(period);
  const { data: settings } = useSettings();
  return (
    <div className="space-y-4">
      <PageHeader title="Pengeluaran" subtitle={data ? `${formatDate(data.from)} – ${formatDate(data.to)}` : undefined} />
      <PeriodPicker value={period} onChange={setPeriod} />
      <ErrorBox error={error} />
      {isLoading || !data ? (!error && <LoadingBlock />) : (
        <>
          <div className="grid grid-cols-1 gap-3 sm:grid-cols-3">
            <StatCard tone="navy" label="Total pengeluaran klub" value={rupiah(data.summary.totalExpense)} icon={<Wallet />} />
            <StatCard label="Biaya kok klub" value={rupiah(data.summary.shuttlecockCost)} hint={`${num(data.summary.totalShuttlecocks)} kok dari ${num(data.summary.totalGames)} game`} icon={<Feather />} />
            <StatCard label="Biaya lapangan" value={rupiah(data.summary.courtCost)} hint={`${num(data.summary.playDays)} hari × harga per hari`} icon={<LandPlot />} />
          </div>
          <ExpenseBreakdown s={data.summary} />
          <ChartCard title="Pengeluaran per hari" subtitle="Biaya kok + biaya lapangan"><ExpenseChart data={withEmptyDays(data.daily, data.from, data.to)} height={260} /></ChartCard>
          {settings && (
            <Card className="p-4 text-sm text-slate-600 sm:p-5">
              <p className="mb-1 font-bold text-navy-900">Aturan perhitungan</p>
              <ul className="list-disc space-y-1 pl-5">
                <li>Biaya kok per game = jumlah pemain × harga kok saat game dimulai (saat ini {rupiah(settings.shuttlecockPrice)} per pemain), berapa pun kok yang dipakai.</li>
                {settings.courtSplitMethod === 'PER_PLAYER'
                  ? <li>Biaya lapangan {rupiah(settings.courtPricePerDay)} dibayar <b>setiap pemain, sekali per tanggal</b> bermain, berapa pun jumlah game-nya (biaya lapangan klub = harga × jumlah pemain hari itu).</li>
                  : <li>Biaya lapangan {rupiah(settings.courtPricePerDay)} dikenakan <b>sekali per tanggal</b> yang memiliki minimal satu game selesai, berapa pun jumlah game-nya.</li>}
                <li>Tanggal ditentukan dengan zona waktu {settings.timezone}.</li>
                <li>Total pengeluaran = biaya kok klub + biaya lapangan.</li>
              </ul>
            </Card>
          )}
          <DailyRecap rows={data.daily} exportPath={'/api/reports/export' + periodQs(period, { type: 'daily' })} />
        </>
      )}
    </div>
  );
}
