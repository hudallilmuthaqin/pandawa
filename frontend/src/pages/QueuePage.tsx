import { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { ArrowDown, ArrowUp, Play, Plus, Radio, Trash2, ListOrdered } from 'lucide-react';
import { useAuth } from '../auth/AuthContext';
import { useMatchActions, useQueue } from '../lib/queries';
import { Button, EmptyState, ErrorBox, IconButton, LoadingBlock, PageHeader, Sheet } from '../components/ui';
import { MatchCard, Versus } from '../components/MatchCard';
import { PlayerPicker, type Slots } from '../components/PlayerPicker';
import { errorMessage, useToast } from '../components/Toast';
import { duration } from '../lib/format';
import { useElapsed } from './LivePage';
import type { Match } from '../lib/types';

export function AddMatchSheet({ open, onClose }: { open: boolean; onClose: () => void }) {
  const [slots, setSlots] = useState<Slots>([null, null, null, null]);
  const { createMatch } = useMatchActions();
  const toast = useToast();
  const complete = slots.every((s) => s != null);
  const close = () => {
    setSlots([null, null, null, null]);
    createMatch.reset();
    onClose();
  };
  return (
    <Sheet open={open} onClose={close} title="Tambah Pertandingan"
      footer={<Button size="lg" block disabled={!complete} loading={createMatch.isPending} onClick={() => {
        createMatch.mutate({ team1PlayerIds: [slots[0]!, slots[1]!], team2PlayerIds: [slots[2]!, slots[3]!] }, {
          onSuccess: (m) => {
            toast(`Masuk antrean #${m.queuePosition}`);
            close();
          },
        });
      }}><Plus className="size-5" /> Masukkan ke Antrean</Button>}>
      <ErrorBox error={createMatch.error} />
      <div className={createMatch.error ? 'mt-3' : ''}><PlayerPicker slots={slots} onChange={setSlots} /></div>
    </Sheet>
  );
}

function CurrentBanner({ match }: { match: Match }) {
  const elapsed = useElapsed(match.startedAt);
  return (
    <Link to="/live" className="block rounded-2xl bg-navy-900 p-4 text-white shadow-lg">
      <div className="mb-3 flex items-center justify-between">
        <span className="flex items-center gap-2 text-xs font-bold tracking-wider text-brand-300"><Radio className="size-4 animate-pulse" /> SEDANG BERLANGSUNG</span>
        <span className="tabular text-sm font-semibold">{duration(elapsed)} · {match.shuttlecockCount} kok</span>
      </div>
      <div className="[&_p]:text-white"><Versus match={match} /></div>
    </Link>
  );
}

export default function QueuePage() {
  const { data, isLoading, error } = useQueue();
  const { user, can, isAdmin } = useAuth();
  const { start, move, remove } = useMatchActions();
  const toast = useToast();
  const navigate = useNavigate();
  const [adding, setAdding] = useState(false);
  const [confirmDelete, setConfirmDelete] = useState<Match | null>(null);

  if (isLoading) return <LoadingBlock />;
  if (error) return <ErrorBox error={error} />;
  const queue = data?.queue ?? [];
  const busy = !!data?.current;

  const onStart = (m: Match) => start.mutate(m.id, {
    onSuccess: () => {
      toast('Game dimulai!');
      navigate('/live');
    },
    onError: (e) => toast(errorMessage(e), 'error'),
  });

  return (
    <div className="space-y-4 pb-16 sm:pb-0">
      <PageHeader title="Jadwal & Queue" subtitle="1 lapangan · pertandingan dimainkan berurutan"
        action={can('QUEUE_CREATE') && <span className="hidden sm:block"><Button onClick={() => setAdding(true)}><Plus className="size-4" /> Tambah</Button></span>} />

      {data?.current && <CurrentBanner match={data.current} />}

      {queue.length === 0 ? (
        <EmptyState icon={<ListOrdered className="size-6" />} title="Antrean kosong" description="Tambahkan pertandingan 2 vs 2 untuk mulai bermain."
          action={can('QUEUE_CREATE') && <Button onClick={() => setAdding(true)}><Plus className="size-4" /> Tambah Pertandingan</Button>} />
      ) : (
        <div className="grid gap-3 md:grid-cols-2">
          {queue.map((m, i) => {
            const canStart = can('MATCH_START') && !busy && (m.status === 'READY' || isAdmin);
            const canManage = can('QUEUE_MANAGE');
            const canDelete = canManage || (m.createdById === user?.id && can('QUEUE_CREATE'));
            return (
              <MatchCard key={m.id} match={m} highlight={user?.playerId}
                meta={m.status === 'READY' && <span className="font-semibold text-sky-700">{busy ? 'Berikutnya setelah game berjalan selesai' : 'Siap dimainkan sekarang'}</span>}
                actions={<>
                  {canStart && (
                    <Button variant={m.status === 'READY' ? 'success' : 'secondary'} size={m.status === 'READY' ? 'lg' : 'md'} className="flex-1"
                      loading={start.isPending && start.variables === m.id} onClick={() => onStart(m)}>
                      <Play className="size-5" /> MULAI
                    </Button>
                  )}
                  {canManage && <>
                    <IconButton label="Naikkan" disabled={i === 0 || move.isPending} className="ring-1 ring-slate-200"
                      onClick={() => move.mutate({ id: m.id, position: i }, { onError: (e) => toast(errorMessage(e), 'error') })}><ArrowUp className="size-5" /></IconButton>
                    <IconButton label="Turunkan" disabled={i === queue.length - 1 || move.isPending} className="ring-1 ring-slate-200"
                      onClick={() => move.mutate({ id: m.id, position: i + 2 }, { onError: (e) => toast(errorMessage(e), 'error') })}><ArrowDown className="size-5" /></IconButton>
                  </>}
                  {canDelete && <IconButton label="Hapus" className="text-red-600 ring-1 ring-slate-200" onClick={() => setConfirmDelete(m)}><Trash2 className="size-5" /></IconButton>}
                </>} />
            );
          })}
        </div>
      )}

      {can('QUEUE_CREATE') && (
        <button type="button" onClick={() => setAdding(true)} aria-label="Tambah pertandingan"
          className="fixed right-4 bottom-24 z-30 flex size-14 items-center justify-center rounded-2xl bg-brand-500 text-white shadow-xl shadow-brand-500/40 active:bg-brand-600 sm:hidden">
          <Plus className="size-7" />
        </button>
      )}

      <AddMatchSheet open={adding} onClose={() => setAdding(false)} />

      <Sheet open={!!confirmDelete} onClose={() => setConfirmDelete(null)} title="Hapus dari antrean?"
        footer={<div className="flex gap-2">
          <Button variant="secondary" size="lg" className="flex-1" onClick={() => setConfirmDelete(null)}>Batal</Button>
          <Button variant="danger" size="lg" className="flex-1" loading={remove.isPending} onClick={() => confirmDelete && remove.mutate(confirmDelete.id, {
            onSuccess: () => {
              toast('Pertandingan dihapus dari antrean');
              setConfirmDelete(null);
            },
            onError: (e) => toast(errorMessage(e), 'error'),
          })}>Hapus</Button>
        </div>}>
        {confirmDelete && <div className="rounded-2xl bg-slate-50 p-4"><Versus match={confirmDelete} /></div>}
        <p className="mt-3 text-sm text-slate-500">Pertandingan yang belum dimulai akan dibatalkan dan tidak masuk statistik.</p>
      </Sheet>
    </div>
  );
}
