import { useEffect, useRef, useState } from 'react';
import { Link } from 'react-router-dom';
import { CheckCircle2, Clock, Feather, Minus, Play, Plus, Radio, XCircle, Trophy } from 'lucide-react';
import { clsx } from 'clsx';
import { useAuth } from '../auth/AuthContext';
import { useMatchActions, useQueue, useSettings } from '../lib/queries';
import { Button, Card, EmptyState, ErrorBox, Field, Input, LoadingBlock, PageHeader, Sheet, Textarea } from '../components/ui';
import { Team, Versus } from '../components/MatchCard';
import { errorMessage, useToast } from '../components/Toast';
import { duration, formatTime, rupiah, teamLabel } from '../lib/format';
import type { Match } from '../lib/types';

/**
 * Elapsed seconds since `startedAt`, ticking every second locally. The clock is
 * corrected with the backend's serverTime, so all phones show the same duration
 * without polling every second.
 */
export function useElapsed(startedAt: string | null | undefined) {
  const { data } = useQueue();
  const offsetRef = useRef(0);
  useEffect(() => {
    if (data?.serverTime) offsetRef.current = new Date(data.serverTime).getTime() - Date.now();
  }, [data?.serverTime]);
  const [now, setNow] = useState(() => Date.now());
  useEffect(() => {
    if (!startedAt) return;
    const t = setInterval(() => setNow(Date.now()), 1000);
    return () => clearInterval(t);
  }, [startedAt]);
  if (!startedAt) return null;
  return Math.max(0, Math.floor((now + offsetRef.current - new Date(startedAt).getTime()) / 1000));
}

function vibrate() {
  try {
    navigator.vibrate?.(25);
  } catch {
    /* not supported */
  }
}

function CompleteSheet({ match, open, onClose }: { match: Match; open: boolean; onClose: () => void }) {
  const { complete } = useMatchActions();
  const { data: settings } = useSettings();
  const toast = useToast();
  const elapsed = useElapsed(match.startedAt);
  const [winner, setWinner] = useState<1 | 2 | null>(null);
  const [score, setScore] = useState('');
  const [notes, setNotes] = useState('');

  return (
    <Sheet open={open} onClose={onClose} title="Selesaikan pertandingan?"
      footer={<div className="flex gap-2">
        <Button variant="secondary" size="lg" className="flex-1" onClick={onClose}>Kembali</Button>
        <Button variant="success" size="lg" className="flex-[2]" loading={complete.isPending} onClick={() => complete.mutate(
          { id: match.id, expectedShuttlecockCount: match.shuttlecockCount, winnerTeam: winner, score, notes },
          {
            onSuccess: () => {
              toast('Game selesai & tersimpan di riwayat');
              onClose();
            },
            onError: (e) => toast(errorMessage(e), 'error'),
          },
        )}><CheckCircle2 className="size-5" /> Ya, Selesai</Button>
      </div>}>
      <div className="space-y-4">
        <ErrorBox error={complete.error} />
        <div className="rounded-2xl bg-slate-50 p-4"><Versus match={match} /></div>
        <dl className="grid grid-cols-2 gap-3 text-sm">
          <div className="rounded-xl bg-white p-3 ring-1 ring-slate-200"><dt className="text-xs text-slate-500">Durasi</dt><dd className="tabular text-lg font-bold">{duration(elapsed)}</dd></div>
          <div className="rounded-xl bg-white p-3 ring-1 ring-slate-200"><dt className="text-xs text-slate-500">Jumlah kok</dt><dd className="tabular text-lg font-bold">{match.shuttlecockCount}</dd></div>
          <div className="rounded-xl bg-white p-3 ring-1 ring-slate-200"><dt className="text-xs text-slate-500">Harga kok / pemain</dt><dd className="tabular text-lg font-bold">{rupiah(match.shuttlecockPrice)}</dd></div>
          <div className="rounded-xl bg-brand-50 p-3 ring-1 ring-brand-200"><dt className="text-xs text-brand-700">Total biaya kok</dt><dd className="tabular text-lg font-extrabold text-brand-700">{rupiah(match.shuttlecockCost)}</dd></div>
        </dl>
        {match.shuttlecockCount === 0 && <p className="rounded-xl bg-amber-50 px-3 py-2 text-sm text-amber-800">Belum ada kok tercatat. Pastikan jumlahnya benar.</p>}
        {settings?.scoreEnabled && (
          <div className="space-y-3">
            <p className="text-sm font-medium text-slate-700">Pemenang (opsional)</p>
            <div className="grid grid-cols-2 gap-2">
              {([1, 2] as const).map((t) => (
                <button key={t} type="button" onClick={() => setWinner(winner === t ? null : t)}
                  className={clsx('min-h-12 rounded-xl px-3 py-2 text-sm font-semibold ring-1', winner === t ? 'bg-emerald-600 text-white ring-emerald-600' : 'bg-white ring-slate-200')}>
                  {winner === t && <Trophy className="mr-1 inline size-4" />}{teamLabel(t === 1 ? match.team1 : match.team2)}
                </button>
              ))}
            </div>
            <Field label="Skor (opsional)"><Input placeholder="cth. 21-18" value={score} maxLength={60} onChange={(e) => setScore(e.target.value)} /></Field>
          </div>
        )}
        <Field label="Catatan (opsional)"><Textarea value={notes} maxLength={500} onChange={(e) => setNotes(e.target.value)} /></Field>
      </div>
    </Sheet>
  );
}

function CancelSheet({ match, open, onClose }: { match: Match; open: boolean; onClose: () => void }) {
  const { cancel } = useMatchActions();
  const toast = useToast();
  const [reason, setReason] = useState('');
  return (
    <Sheet open={open} onClose={onClose} title="Batalkan game?"
      footer={<Button variant="danger" size="lg" block loading={cancel.isPending} disabled={reason.trim().length < 3}
        onClick={() => cancel.mutate({ id: match.id, reason }, { onSuccess: () => { toast('Game dibatalkan'); onClose(); }, onError: (e) => toast(errorMessage(e), 'error') })}>
        Batalkan Game</Button>}>
      <p className="mb-3 text-sm text-slate-600">Game yang dibatalkan tidak masuk statistik, biaya kok, maupun biaya lapangan. Tindakan ini tercatat di audit log.</p>
      <Field label="Alasan pembatalan"><Input value={reason} onChange={(e) => setReason(e.target.value)} placeholder="cth. salah input pemain" /></Field>
    </Sheet>
  );
}

function LiveMatch({ match }: { match: Match }) {
  const { can } = useAuth();
  const { shuttle, newRequestId } = useMatchActions();
  const toast = useToast();
  const elapsed = useElapsed(match.startedAt);
  const [pending, setPending] = useState(0);
  const [completing, setCompleting] = useState(false);
  const [cancelling, setCancelling] = useState(false);
  const [correcting, setCorrecting] = useState(false);
  const [reason, setReason] = useState('');

  const count = match.shuttlecockCount + pending;

  const send = (delta: number, why?: string) => {
    vibrate();
    setPending((p) => p + delta);
    shuttle.mutate({ id: match.id, delta, reason: why, requestId: newRequestId() }, {
      onError: (e) => toast(errorMessage(e), 'error'),
      onSettled: () => setPending((p) => p - delta),
    });
  };

  return (
    <div className="space-y-4">
      <Card className="overflow-hidden">
        <div className="bg-navy-900 px-4 pt-4 pb-5 text-white sm:px-6">
          <div className="mb-4 flex items-center justify-between">
            <span className="flex items-center gap-2 text-xs font-bold tracking-wider text-brand-300"><Radio className="size-4 animate-pulse" /> GAME #{match.id} BERLANGSUNG</span>
            <span className="flex items-center gap-1 text-xs text-navy-100/70"><Clock className="size-3.5" /> Mulai {formatTime(match.startedAt)}</span>
          </div>
          <div className="flex items-center gap-3 [&_p]:text-lg [&_p]:text-white">
            <Team players={match.team1} />
            <span className="shrink-0 rounded-lg bg-brand-500 px-2.5 py-1 text-sm font-black">VS</span>
            <Team players={match.team2} align="right" />
          </div>
          <p className="tabular mt-5 text-center text-5xl font-black tracking-tight">{duration(elapsed)}</p>
          <p className="text-center text-xs text-navy-100/60">durasi game</p>
        </div>
        <div className="grid grid-cols-3 divide-x divide-slate-100 text-center">
          <div className="p-3"><p className="text-xs text-slate-500">Kok terpakai</p><p className="tabular text-3xl font-black text-navy-900">{count}</p></div>
          <div className="p-3"><p className="text-xs text-slate-500">Harga / pemain</p><p className="tabular mt-1.5 text-base font-bold">{rupiah(match.shuttlecockPrice)}</p></div>
          <div className="p-3"><p className="text-xs text-slate-500">Biaya kok</p><p className="tabular mt-1.5 text-base font-extrabold text-brand-600">{rupiah(match.shuttlecockCost)}</p></div>
        </div>
      </Card>

      {can('SHUTTLE_RECORD') && (
        <div className="flex gap-3">
          {can('SHUTTLE_CORRECT') && (
            <Button variant="secondary" size="xl" className="w-24 shrink-0" aria-label="Koreksi -1 kok" disabled={count <= 0} onClick={() => setCorrecting(true)}>
              <Minus className="size-7" />
            </Button>
          )}
          <Button size="xl" className="flex-1" onClick={() => send(1)}>
            <Plus className="size-8" /><Feather className="size-6" /> +1 KOK
          </Button>
        </div>
      )}

      {can('MATCH_COMPLETE') && (
        <Button variant="success" size="xl" block onClick={() => setCompleting(true)} disabled={pending !== 0}>
          <CheckCircle2 className="size-7" /> SELESAI
        </Button>
      )}
      {pending !== 0 && <p className="text-center text-xs text-slate-500">Menyimpan pencatatan kok…</p>}

      {can('MATCH_CANCEL') && (
        <button type="button" onClick={() => setCancelling(true)} className="mx-auto flex items-center gap-1.5 py-2 text-sm font-medium text-red-600">
          <XCircle className="size-4" /> Batalkan game
        </button>
      )}

      <CompleteSheet match={match} open={completing} onClose={() => setCompleting(false)} />
      <CancelSheet match={match} open={cancelling} onClose={() => setCancelling(false)} />
      <Sheet open={correcting} onClose={() => setCorrecting(false)} title="Koreksi jumlah kok (−1)"
        footer={<Button size="lg" block variant="navy" disabled={reason.trim().length < 3} onClick={() => {
          send(-1, reason.trim());
          setReason('');
          setCorrecting(false);
        }}>Kurangi 1 kok</Button>}>
        <p className="mb-3 text-sm text-slate-600">Jumlah saat ini <b>{count}</b> kok → menjadi <b>{Math.max(0, count - 1)}</b>. Koreksi tercatat di audit log.</p>
        <Field label="Alasan koreksi"><Input value={reason} onChange={(e) => setReason(e.target.value)} placeholder="cth. salah tekan" /></Field>
      </Sheet>
    </div>
  );
}

export default function LivePage() {
  const { data, isLoading, error } = useQueue();
  const { can, isAdmin } = useAuth();
  const { start } = useMatchActions();
  const toast = useToast();

  if (isLoading) return <LoadingBlock />;
  if (error) return <ErrorBox error={error} />;

  return (
    <div className="mx-auto max-w-xl space-y-4">
      <PageHeader title="Game Berlangsung" />
      {data?.current ? <LiveMatch match={data.current} /> : (
        <Card>
          <EmptyState icon={<Radio className="size-6" />} title="Lapangan kosong" description={data?.next ? 'Pertandingan berikutnya siap dimulai.' : 'Belum ada pertandingan di antrean.'} />
          {data?.next && (
            <div className="space-y-3 px-4 pb-4">
              <div className="rounded-2xl bg-slate-50 p-4"><Versus match={data.next} /></div>
              {can('MATCH_START') && (
                <Button variant="success" size="xl" block loading={start.isPending}
                  onClick={() => start.mutate(data.next!.id, { onError: (e) => toast(errorMessage(e), 'error') })}>
                  <Play className="size-7" /> MULAI
                </Button>
              )}
            </div>
          )}
          {!data?.next && (isAdmin || can('QUEUE_CREATE')) && (
            <div className="px-4 pb-4"><Link to="/queue"><Button block variant="secondary">Buka antrean</Button></Link></div>
          )}
        </Card>
      )}
    </div>
  );
}
