import { useEffect, useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import { History, Search, SlidersHorizontal, Wrench } from 'lucide-react';
import { useAuth } from '../auth/AuthContext';
import { useHistory, useMatchActions, useMatchDetail, usePlayers, useSettings } from '../lib/queries';
import { qs } from '../lib/api';
import { Button, Card, EmptyState, ErrorBox, Field, Input, LoadingBlock, PageHeader, Pagination, Select, Sheet, StatusBadge } from '../components/ui';
import { Versus } from '../components/MatchCard';
import { ExportButton } from '../components/ReportBlocks';
import { errorMessage, useToast } from '../components/Toast';
import { durationShort, formatDate, formatDateTime, formatTime, rupiah, todayLocal } from '../lib/format';

const EVENT_LABEL: Record<string, string> = {
  CREATED: 'Masuk antrean', MOVED: 'Urutan diubah', STARTED: 'Dimulai', SHUTTLE_ADDED: '+ kok', SHUTTLE_CORRECTED: 'Koreksi kok',
  COMPLETED: 'Selesai', CANCELLED: 'Dibatalkan', COMPLETED_CORRECTION: 'Koreksi setelah selesai',
};

function MatchDetailSheet({ id, onClose }: { id: number | null; onClose: () => void }) {
  const { data, isLoading, error } = useMatchDetail(id);
  const { can } = useAuth();
  const { correct } = useMatchActions();
  const toast = useToast();
  const [count, setCount] = useState('');
  const [reason, setReason] = useState('');
  const [showCorrect, setShowCorrect] = useState(false);
  const m = data?.match;

  return (
    <Sheet open={id != null} onClose={() => { setShowCorrect(false); onClose(); }} title={`Detail Game #${id ?? ''}`}>
      {isLoading ? <LoadingBlock /> : error ? <ErrorBox error={error} /> : m && (
        <div className="space-y-4">
          <div className="rounded-2xl bg-slate-50 p-4"><Versus match={m} /></div>
          <dl className="tabular grid grid-cols-2 gap-x-4 gap-y-3 text-sm">
            <div><dt className="text-xs text-slate-500">Tanggal</dt><dd className="font-semibold">{formatDate(m.playDate)}</dd></div>
            <div><dt className="text-xs text-slate-500">Status</dt><dd><StatusBadge status={m.status} /></dd></div>
            <div><dt className="text-xs text-slate-500">Mulai – selesai</dt><dd className="font-semibold">{formatTime(m.startedAt)} – {formatTime(m.completedAt)}</dd></div>
            <div><dt className="text-xs text-slate-500">Durasi</dt><dd className="font-semibold">{durationShort(m.durationSeconds)}</dd></div>
            <div><dt className="text-xs text-slate-500">Jumlah kok</dt><dd className="font-semibold">{m.shuttlecockCount}</dd></div>
            <div><dt className="text-xs text-slate-500">Harga kok (saat main)</dt><dd className="font-semibold">{rupiah(m.shuttlecockPrice)}</dd></div>
            <div><dt className="text-xs text-slate-500">Total biaya kok</dt><dd className="font-extrabold text-brand-600">{rupiah(m.shuttlecockCost)}</dd></div>
            <div><dt className="text-xs text-slate-500">Skor</dt><dd className="font-semibold">{m.score ?? '-'}</dd></div>
            <div><dt className="text-xs text-slate-500">Dimulai oleh</dt><dd className="font-semibold">{m.startedBy ?? '-'}</dd></div>
            <div><dt className="text-xs text-slate-500">Diselesaikan oleh</dt><dd className="font-semibold">{m.completedBy ?? '-'}</dd></div>
          </dl>
          {m.notes && <p className="rounded-xl bg-amber-50 px-3 py-2 text-sm text-amber-900">{m.notes}</p>}
          <div>
            <p className="mb-2 text-sm font-bold text-navy-900">Log kejadian</p>
            <ol className="space-y-1.5 border-l-2 border-slate-200 pl-3 text-sm">
              {data!.events.map((e) => (
                <li key={e.id}>
                  <span className="font-semibold text-slate-800">{EVENT_LABEL[e.type] ?? e.type}</span>
                  {e.delta != null && <span className="text-slate-600"> ({e.delta > 0 ? '+' : ''}{e.delta}{e.countAfter != null && ` → ${e.countAfter}`})</span>}
                  {e.reason && <span className="text-slate-500"> · {e.reason}</span>}
                  <span className="block text-xs text-slate-400">{formatDateTime(e.createdAt)} · {e.actor}</span>
                </li>
              ))}
            </ol>
          </div>
          {can('MATCH_CORRECT') && m.status === 'COMPLETED' && (
            !showCorrect ? <Button variant="secondary" block onClick={() => { setCount(String(m.shuttlecockCount)); setShowCorrect(true); }}><Wrench className="size-4" /> Koreksi jumlah kok</Button> : (
              <div className="space-y-3 rounded-2xl bg-slate-50 p-4">
                <p className="text-xs text-slate-500">Koreksi tercatat di audit log. Harga snapshot ({rupiah(m.shuttlecockPrice)}) tidak berubah.</p>
                <div className="grid grid-cols-[6rem_1fr] gap-2">
                  <Field label="Jumlah kok"><Input type="number" min={0} max={99} inputMode="numeric" value={count} onChange={(e) => setCount(e.target.value)} /></Field>
                  <Field label="Alasan"><Input value={reason} onChange={(e) => setReason(e.target.value)} /></Field>
                </div>
                <Button block loading={correct.isPending} disabled={reason.trim().length < 3 || count === ''} onClick={() => correct.mutate(
                  { id: m.id, shuttlecockCount: Number(count), reason: reason.trim() },
                  { onSuccess: () => { toast('Koreksi tersimpan'); setShowCorrect(false); setReason(''); }, onError: (e) => toast(errorMessage(e), 'error') },
                )}>Simpan koreksi</Button>
              </div>
            )
          )}
        </div>
      )}
    </Sheet>
  );
}

export default function HistoryPage() {
  const { isAdmin, user } = useAuth();
  const { data: settings } = useSettings();
  const [params] = useSearchParams();
  const [filters, setFilters] = useState({ from: '', to: '', playerId: params.get('playerId') ?? '', q: '' });
  const [draft, setDraft] = useState(filters);
  const [page, setPage] = useState(0);
  const [showFilters, setShowFilters] = useState(false);
  const [detail, setDetail] = useState<number | null>(null);
  const players = usePlayers({ size: 200 });
  useEffect(() => setPage(0), [filters]);

  const invalid = !!draft.from && !!draft.to && draft.to < draft.from;
  const { data, isLoading, error } = useHistory({
    from: filters.from || undefined, to: filters.to || undefined,
    playerId: filters.playerId ? Number(filters.playerId) : undefined, q: filters.q || undefined, page,
  });
  const canExport = isAdmin || settings?.userExportEnabled;
  const exportFrom = filters.from || todayLocal().slice(0, 8) + '01';
  const exportTo = filters.to || todayLocal();

  return (
    <div className="space-y-4">
      <PageHeader title={isAdmin ? 'Riwayat Game' : 'Riwayat Saya'} subtitle={data ? `${data.totalElements} game selesai` : undefined}
        action={<>
          <Button variant="secondary" size="sm" onClick={() => setShowFilters((v) => !v)}><SlidersHorizontal className="size-4" /> Filter</Button>
          {canExport && <ExportButton path={'/api/reports/export' + qs({ type: 'matches', period: 'CUSTOM', from: exportFrom, to: exportTo })} />}
        </>} />

      {showFilters && (
        <Card className="space-y-3 p-4">
          <div className="grid grid-cols-2 gap-2">
            <Field label="Dari tanggal"><Input type="date" value={draft.from} onChange={(e) => setDraft({ ...draft, from: e.target.value })} /></Field>
            <Field label="Sampai tanggal"><Input type="date" value={draft.to} onChange={(e) => setDraft({ ...draft, to: e.target.value })} /></Field>
          </div>
          {invalid && <p className="text-xs text-red-600">Tanggal akhir tidak boleh lebih awal dari tanggal mulai.</p>}
          {isAdmin && (
            <div className="grid gap-2 sm:grid-cols-2">
              <Field label="Pemain">
                <Select value={draft.playerId} onChange={(e) => setDraft({ ...draft, playerId: e.target.value })}>
                  <option value="">Semua pemain</option>
                  {players.data?.content.map((p) => <option key={p.id} value={p.id}>{p.nickname}</option>)}
                </Select>
              </Field>
              <Field label="Cari nama">
                <div className="relative"><Search className="pointer-events-none absolute top-3 left-3 size-5 text-slate-400" />
                  <Input className="pl-10" value={draft.q} onChange={(e) => setDraft({ ...draft, q: e.target.value })} placeholder="nickname / nama" /></div>
              </Field>
            </div>
          )}
          <div className="flex gap-2">
            <Button variant="ghost" className="flex-1" onClick={() => { const empty = { from: '', to: '', playerId: '', q: '' }; setDraft(empty); setFilters(empty); }}>Reset</Button>
            <Button className="flex-[2]" disabled={invalid} onClick={() => setFilters(draft)}>Terapkan</Button>
          </div>
        </Card>
      )}

      <ErrorBox error={error} />
      {isLoading ? <LoadingBlock /> : data?.content.length === 0 ? (
        <Card><EmptyState icon={<History className="size-6" />} title="Belum ada riwayat" description="Game yang selesai akan muncul di sini." /></Card>
      ) : (
        <div className="grid gap-3 md:grid-cols-2">
          {data?.content.map((m) => (
            <button key={m.id} type="button" onClick={() => setDetail(m.id)} className="rounded-2xl bg-white p-4 text-left shadow-sm ring-1 ring-slate-200/70 hover:ring-brand-300">
              <div className="mb-2 flex items-center justify-between text-xs text-slate-500">
                <span>{formatDate(m.playDate, { weekday: 'short', day: 'numeric', month: 'short', year: 'numeric' })} · {formatTime(m.startedAt)}–{formatTime(m.completedAt)}</span>
                <span>#{m.id}</span>
              </div>
              <Versus match={m} highlight={user?.playerId} />
              <div className="tabular mt-3 flex flex-wrap justify-between gap-2 border-t border-slate-100 pt-2.5 text-sm">
                <span className="text-slate-600">{durationShort(m.durationSeconds)}{m.score && ` · ${m.score}`}</span>
                <span className="font-semibold text-slate-800">{m.shuttlecockCount} kok · {m.team1.length + m.team2.length} pemain × {rupiah(m.shuttlecockPrice)} = <span className="text-brand-600">{rupiah(m.shuttlecockCost)}</span></span>
              </div>
            </button>
          ))}
        </div>
      )}
      {data && <Pagination page={data.page} totalPages={data.totalPages} onChange={setPage} />}
      <MatchDetailSheet id={detail} onClose={() => setDetail(null)} />
    </div>
  );
}
