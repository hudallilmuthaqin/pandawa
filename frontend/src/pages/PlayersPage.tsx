import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { ChevronRight, Pencil, Plus, Search, UserPlus } from 'lucide-react';
import { usePlayers } from '../lib/queries';
import { api, ApiError } from '../lib/api';
import { Badge, Button, Card, EmptyState, ErrorBox, Field, IconButton, Input, LoadingBlock, PageHeader, Pagination, Segmented, Select, Sheet } from '../components/ui';
import { useToast } from '../components/Toast';
import { formatDate, todayLocal } from '../lib/format';
import type { Player, PlayerStatus } from '../lib/types';

const schema = z.object({
  fullName: z.string().trim().min(1, 'Nama lengkap wajib diisi').max(100, 'Maksimal 100 karakter'),
  nickname: z.string().trim().min(1, 'Nickname wajib diisi').max(40, 'Maksimal 40 karakter'),
  phone: z.string().trim().max(30).regex(/^$|^[0-9+\-\s()]{6,30}$/, 'Nomor telepon tidak valid'),
  registeredOn: z.string().min(1, 'Tanggal wajib diisi'),
  status: z.enum(['ACTIVE', 'INACTIVE']),
});
type Form = z.infer<typeof schema>;

export function PlayerFormSheet({ open, onClose, player }: { open: boolean; onClose: () => void; player?: Player | null }) {
  const qc = useQueryClient();
  const toast = useToast();
  const { register, handleSubmit, reset, setError, formState: { errors } } = useForm<Form>({ resolver: zodResolver(schema) });
  useEffect(() => {
    if (open) reset({
      fullName: player?.fullName ?? '', nickname: player?.nickname ?? '', phone: player?.phone ?? '',
      registeredOn: player?.registeredOn ?? todayLocal(), status: player?.status ?? 'ACTIVE',
    });
  }, [open, player, reset]);
  const save = useMutation({
    mutationFn: (v: Form) => player ? api.put<Player>(`/api/players/${player.id}`, v) : api.post<Player>('/api/players', v),
    onSuccess: (p) => {
      qc.invalidateQueries({ queryKey: ['players'] });
      qc.invalidateQueries({ queryKey: ['player', p.id] });
      toast(player ? 'Data pemain diperbarui' : `${p.nickname} terdaftar`);
      onClose();
    },
    onError: (e) => {
      if (e instanceof ApiError && e.code === 'DUPLICATE_PLAYER') setError('nickname', { message: e.message });
    },
  });
  return (
    <Sheet open={open} onClose={onClose} title={player ? 'Edit Pemain' : 'Daftarkan Pemain'}
      footer={<Button size="lg" block loading={save.isPending} onClick={handleSubmit((v) => save.mutate(v))}>{player ? 'Simpan Perubahan' : 'Daftarkan'}</Button>}>
      <form className="space-y-4" onSubmit={(e) => e.preventDefault()}>
        {save.error && !(save.error instanceof ApiError && save.error.code === 'DUPLICATE_PLAYER') && <ErrorBox error={save.error} />}
        <Field label="Nama lengkap" error={errors.fullName?.message}><Input {...register('fullName')} /></Field>
        <Field label="Nickname / nama panggilan" error={errors.nickname?.message} hint="Unik — dipakai sebagai identitas di antrean & statistik"><Input {...register('nickname')} /></Field>
        <Field label="Nomor telepon (opsional)" error={errors.phone?.message}><Input type="tel" inputMode="tel" {...register('phone')} /></Field>
        <div className="grid grid-cols-2 gap-3">
          <Field label="Tanggal daftar" error={errors.registeredOn?.message}><Input type="date" {...register('registeredOn')} /></Field>
          <Field label="Status"><Select {...register('status')}><option value="ACTIVE">Aktif</option><option value="INACTIVE">Nonaktif</option></Select></Field>
        </div>
        <p className="text-xs text-slate-500">Akun login untuk pemain dibuat di Pengaturan → Akun.</p>
      </form>
    </Sheet>
  );
}

export default function PlayersPage() {
  const [q, setQ] = useState('');
  const [debounced, setDebounced] = useState('');
  const [status, setStatus] = useState<PlayerStatus | ''>('');
  const [page, setPage] = useState(0);
  const [editing, setEditing] = useState<Player | null | undefined>(undefined);
  useEffect(() => {
    const t = setTimeout(() => { setDebounced(q); setPage(0); }, 250);
    return () => clearTimeout(t);
  }, [q]);
  const { data, isLoading, error } = usePlayers({ q: debounced, status, page, size: 30 });

  return (
    <div className="space-y-4">
      <PageHeader title="Daftar Pemain" subtitle={data ? `${data.totalElements} pemain` : undefined}
        action={<Button onClick={() => setEditing(null)}><UserPlus className="size-4" /> Tambah Pemain</Button>} />
      <div className="flex flex-col gap-2 sm:flex-row">
        <div className="relative flex-1">
          <Search className="pointer-events-none absolute top-3 left-3 size-5 text-slate-400" />
          <Input className="pl-10" placeholder="Cari nama atau nickname…" value={q} onChange={(e) => setQ(e.target.value)} />
        </div>
        <Segmented value={status} onChange={(v) => { setStatus(v); setPage(0); }}
          options={[{ value: '', label: 'Semua' }, { value: 'ACTIVE', label: 'Aktif' }, { value: 'INACTIVE', label: 'Nonaktif' }]} />
      </div>
      <ErrorBox error={error} />
      {isLoading ? <LoadingBlock /> : (
        <Card>
          {data?.content.length === 0 ? <EmptyState title="Tidak ada pemain" action={<Button onClick={() => setEditing(null)}><Plus className="size-4" /> Tambah</Button>} /> : (
            <ul className="divide-y divide-slate-100">
              {data?.content.map((p) => (
                <li key={p.id} className="flex items-center gap-3 px-4 py-3 sm:px-5">
                  <div className="flex size-11 shrink-0 items-center justify-center rounded-full bg-brand-50 font-bold text-brand-700">{p.nickname.slice(0, 2).toUpperCase()}</div>
                  <Link to={`/players/${p.id}`} className="min-w-0 flex-1">
                    <p className="flex items-center gap-2 font-bold text-navy-900">
                      <span className="truncate">{p.nickname}</span>
                      {p.status === 'INACTIVE' && <Badge tone="red">Nonaktif</Badge>}
                      {p.username && <Badge tone="navy">@{p.username}</Badge>}
                    </p>
                    <p className="truncate text-xs text-slate-500">{p.fullName} · daftar {formatDate(p.registeredOn)}</p>
                  </Link>
                  <div className="text-right">
                    <p className="tabular text-lg font-extrabold text-navy-900">{p.totalGames}</p>
                    <p className="text-[11px] text-slate-500">main</p>
                  </div>
                  <IconButton label="Edit" onClick={() => setEditing(p)}><Pencil className="size-4" /></IconButton>
                  <Link to={`/players/${p.id}`} className="hidden text-slate-400 sm:block"><ChevronRight className="size-5" /></Link>
                </li>
              ))}
            </ul>
          )}
        </Card>
      )}
      {data && <Pagination page={data.page} totalPages={data.totalPages} onChange={setPage} />}
      <PlayerFormSheet open={editing !== undefined} player={editing} onClose={() => setEditing(undefined)} />
    </div>
  );
}
