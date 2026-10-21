import { useSearchParams } from 'react-router-dom';
import { ChevronLeft, ChevronRight, History } from 'lucide-react';
import { useDailyReport, useSettings } from '../lib/queries';
import { qs } from '../lib/api';
import { Card, CardHeader, EmptyState, ErrorBox, IconButton, Input, LoadingBlock, PageHeader } from '../components/ui';
import { ExpenseBreakdown, ExportButton, PlayerRecap, SummaryCards } from '../components/ReportBlocks';
import { Versus } from '../components/MatchCard';
import { durationShort, formatDate, formatTime, rupiah, todayLocal } from '../lib/format';

function shift(date: string, days: number) {
  const d = new Date(date + 'T00:00:00Z');
  return new Date(d.getTime() + days * 86400000).toISOString().slice(0, 10);
}

export default function DailyReportPage() {
  const [params, setParams] = useSearchParams();
  const date = params.get('date') ?? todayLocal();
  const setDate = (d: string) => d && setParams({ date: d });
  const { data, isLoading, error } = useDailyReport(date);
  const { data: settings } = useSettings();
  return (
    <div className="space-y-4">
      <PageHeader title="Rekap Harian" subtitle={formatDate(date, { weekday: 'long', day: 'numeric', month: 'long', year: 'numeric' })}
        action={<ExportButton path={'/api/reports/export' + qs({ type: 'matches', period: 'CUSTOM', from: date, to: date })} />} />
      <div className="flex items-center gap-2">
        <IconButton label="Hari sebelumnya" className="ring-1 ring-slate-200 bg-white" onClick={() => setDate(shift(date, -1))}><ChevronLeft className="size-5" /></IconButton>
        <Input type="date" value={date} onChange={(e) => setDate(e.target.value)} className="flex-1" />
        <IconButton label="Hari berikutnya" className="ring-1 ring-slate-200 bg-white" onClick={() => setDate(shift(date, 1))}><ChevronRight className="size-5" /></IconButton>
      </div>
      <ErrorBox error={error} />
      {isLoading || !data ? (!error && <LoadingBlock />) : (
        <>
          <SummaryCards s={data.summary} />
          <ExpenseBreakdown s={data.summary} />
          <Card>
            <CardHeader title="Daftar game" icon={<History className="size-5" />} subtitle={`${data.matches.length} game selesai`} />
            {data.matches.length === 0 ? <EmptyState title="Tidak ada game selesai" description="Tanggal tanpa game selesai tidak dikenakan biaya lapangan." /> : (
              <ul className="divide-y divide-slate-100 pb-2">
                {data.matches.map((m) => (
                  <li key={m.id} className="px-4 py-3 sm:px-5">
                    <div className="tabular flex justify-between text-xs text-slate-500">
                      <span>{formatTime(m.startedAt)}–{formatTime(m.completedAt)} · {durationShort(m.durationSeconds)}</span>
                      <span className="font-semibold text-slate-700">{m.shuttlecockCount} kok · {rupiah(m.shuttlecockCost)}</span>
                    </div>
                    <div className="mt-1.5"><Versus match={m} /></div>
                  </li>
                ))}
              </ul>
            )}
          </Card>
          <PlayerRecap rows={data.players} method={data.day?.courtSplitMethod ?? settings?.courtSplitMethod ?? 'EQUAL_SPLIT'}
            exportPath={'/api/reports/export' + qs({ type: 'players', period: 'CUSTOM', from: date, to: date })} />
        </>
      )}
    </div>
  );
}
