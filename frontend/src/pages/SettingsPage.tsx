import { useEffect, useState } from 'react';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { KeyRound, Plus, ShieldCheck, UserCog } from 'lucide-react';
import { clsx } from 'clsx';
import { useAuth } from '../auth/AuthContext';
import { useAuditLogs, usePermissionCatalog, usePlayers, useSettings, useUsers } from '../lib/queries';
import { api } from '../lib/api';
import { Badge, Button, Card, CardHeader, ErrorBox, Field, Input, LoadingBlock, PageHeader, Pagination, Segmented, Select, Sheet, Toggle } from '../components/ui';
import { errorMessage, useToast } from '../components/Toast';
import { formatDateTime, rupiah } from '../lib/format';
import type { CourtSplitMethod, Permission, Role, Settings, UserAccount } from '../lib/types';

type Tab = 'club' | 'permissions' | 'accounts' | 'audit';

function useSaveSettings() {
  const qc = useQueryClient();
  const toast = useToast();
  return useMutation({
    mutationFn: (s: Settings) => api.put<Settings>('/api/settings', s),
    onSuccess: (s) => {
      qc.setQueryData(['settings'], s);
      qc.invalidateQueries({ queryKey: ['dashboard'] });
      toast('Pengaturan tersimpan');
    },
    onError: (e) => toast(errorMessage(e), 'error'),
  });
}

function ClubTab({ settings }: { settings: Settings }) {
  const [form, setForm] = useState(settings);
  useEffect(() => setForm(settings), [settings]);
  const save = useSaveSettings();
  const set = <K extends keyof Settings>(k: K, v: Settings[K]) => setForm((f) => ({ ...f, [k]: v }));
  const priceChanged = form.shuttlecockPrice !== settings.shuttlecockPrice || form.courtPricePerDay !== settings.courtPricePerDay;
  const validPrice = Number.isInteger(form.shuttlecockPrice) && form.shuttlecockPrice >= 0 && Number.isInteger(form.courtPricePerDay) && form.courtPricePerDay >= 0;

  return (
    <div className="space-y-4">
      <Card className="space-y-4 p-4 sm:p-5">
        <Field label="Nama klub"><Input value={form.clubName} maxLength={80} onChange={(e) => set('clubName', e.target.value)} /></Field>
        <div className="grid grid-cols-2 gap-3">
          <Field label="Harga kok per pemain per game (Rp)" hint={rupiah(form.shuttlecockPrice)}>
            <Input type="number" inputMode="numeric" min={0} step={500} value={form.shuttlecockPrice} onChange={(e) => set('shuttlecockPrice', Number(e.target.value))} />
          </Field>
          <Field label="Harga lapangan / hari (Rp)" hint={rupiah(form.courtPricePerDay)}>
            <Input type="number" inputMode="numeric" min={0} step={500} value={form.courtPricePerDay} onChange={(e) => set('courtPricePerDay', Number(e.target.value))} />
          </Field>
        </div>
        {priceChanged && <p className="rounded-xl bg-amber-50 px-3 py-2 text-sm text-amber-900">Harga baru hanya berlaku untuk game baru (dan tanggal bermain baru). Riwayat lama tidak berubah. Perubahan tercatat di audit log.</p>}
        <div className="grid gap-3 sm:grid-cols-2">
          <Field label="Zona waktu"><Select value={form.timezone} onChange={(e) => set('timezone', e.target.value)}>
            {['Asia/Jakarta', 'Asia/Makassar', 'Asia/Jayapura'].map((z) => <option key={z}>{z}</option>)}
          </Select></Field>
          <Field label="Jumlah lapangan" hint="Sistem dirancang untuk 1 lapangan"><Input value={form.courtCount} disabled /></Field>
        </div>
        <Field label="Metode pembagian biaya lapangan untuk iuran pribadi">
          <Select value={form.courtSplitMethod} onChange={(e) => set('courtSplitMethod', e.target.value as CourtSplitMethod)}>
            <option value="PER_PLAYER">Setiap pemain bayar harga lapangan penuh per hari</option>
            <option value="EQUAL_SPLIT">Bagi rata ke pemain unik per tanggal</option>
            <option value="CLUB_ONLY">Catat sebagai biaya klub saja</option>
          </Select>
        </Field>
        <p className="text-xs text-slate-500">Metode disimpan per tanggal saat game pertama hari itu selesai, sehingga laporan lama tetap konsisten.</p>
      </Card>
      <Card className="divide-y divide-slate-100 px-4 sm:px-5">
        <Toggle label="Pendaftaran mandiri pemain" description="Izinkan pemain membuat akun sendiri dari halaman login" checked={form.selfRegistrationEnabled} onChange={(v) => set('selfRegistrationEnabled', v)} />
        <Toggle label="Pencatatan pemenang & skor" description="Tampilkan pilihan pemenang saat menyelesaikan game" checked={form.scoreEnabled} onChange={(v) => set('scoreEnabled', v)} />
        <Toggle label="Koreksi wajib menyertakan alasan" description="Kebijakan koreksi data jumlah kok" checked={form.correctionRequiresReason} onChange={(v) => set('correctionRequiresReason', v)} />
        <Toggle label="User boleh ekspor CSV riwayatnya" description="Pengaturan ekspor laporan" checked={form.userExportEnabled} onChange={(v) => set('userExportEnabled', v)} />
      </Card>
      <Button size="lg" block className="sm:w-auto" loading={save.isPending} disabled={!validPrice || !form.clubName.trim()} onClick={() => save.mutate(form)}>Simpan Pengaturan</Button>
    </div>
  );
}

function PermissionsTab({ settings }: { settings: Settings }) {
  const { data } = usePermissionCatalog();
  const [perms, setPerms] = useState<Permission[]>(settings.userPermissions);
  useEffect(() => setPerms(settings.userPermissions), [settings]);
  const save = useSaveSettings();
  if (!data) return <LoadingBlock />;
  return (
    <div className="space-y-4">
      <Card>
        <CardHeader title="Izin role USER" icon={<ShieldCheck className="size-5" />} subtitle="Berlaku langsung untuk semua akun pemain" />
        <ul className="divide-y divide-slate-100 px-4 pb-2 sm:px-5">
          {data.permissions.map((p) => (
            <li key={p.permission}>
              {p.grantableToUser ? (
                <Toggle label={p.description} description={p.permission} checked={perms.includes(p.permission)}
                  onChange={(v) => setPerms((xs) => v ? [...xs, p.permission] : xs.filter((x) => x !== p.permission))} />
              ) : (
                <div className="flex items-center justify-between gap-4 py-2.5">
                  <span><span className="block text-sm font-medium text-slate-500">{p.description}</span><span className="text-xs text-slate-400">{p.permission}</span></span>
                  <Badge tone="navy">Admin saja</Badge>
                </div>
              )}
            </li>
          ))}
        </ul>
      </Card>
      <Button size="lg" block className="sm:w-auto" loading={save.isPending} onClick={() => save.mutate({ ...settings, userPermissions: perms })}>Simpan Izin</Button>
    </div>
  );
}

function UserSheet({ open, onClose, account }: { open: boolean; onClose: () => void; account: UserAccount | null }) {
  const qc = useQueryClient();
  const toast = useToast();
  const players = usePlayers({ size: 200 });
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [role, setRole] = useState<Role>('USER');
  const [enabled, setEnabled] = useState(true);
  const [playerId, setPlayerId] = useState('');
  useEffect(() => {
    if (!open) return;
    setUsername(account?.username ?? '');
    setPassword('');
    setRole(account?.role ?? 'USER');
    setEnabled(account?.enabled ?? true);
    setPlayerId(account?.playerId ? String(account.playerId) : '');
  }, [open, account]);
  const done = (msg: string) => {
    qc.invalidateQueries({ queryKey: ['users'] });
    qc.invalidateQueries({ queryKey: ['players'] });
    toast(msg);
    onClose();
  };
  const save = useMutation({
    mutationFn: () => account
      ? api.put(`/api/users/${account.id}`, { role, enabled, playerId: playerId ? Number(playerId) : null })
      : api.post('/api/users', { username, password, role, playerId: playerId ? Number(playerId) : null }),
    onSuccess: () => done(account ? 'Akun diperbarui' : 'Akun dibuat'),
  });
  const reset = useMutation({
    mutationFn: () => api.post(`/api/users/${account!.id}/reset-password`, { newPassword: password }),
    onSuccess: () => done('Password direset'),
  });
  const invalid = !account && (!/^[a-zA-Z0-9._@-]{3,60}$/.test(username) || password.length < 8);
  return (
    <Sheet open={open} onClose={onClose} title={account ? `Akun @${account.username}` : 'Buat Akun'}
      footer={<Button size="lg" block disabled={invalid} loading={save.isPending} onClick={() => save.mutate()}>Simpan</Button>}>
      <div className="space-y-4">
        <ErrorBox error={save.error ?? reset.error} />
        {!account && <>
          <Field label="Username" hint="3-60 karakter: huruf, angka, . _ @ -"><Input autoCapitalize="none" value={username} onChange={(e) => setUsername(e.target.value)} /></Field>
          <Field label="Password awal" hint="Minimal 8 karakter"><Input type="password" autoComplete="new-password" value={password} onChange={(e) => setPassword(e.target.value)} /></Field>
        </>}
        <Field label="Role"><Select value={role} onChange={(e) => setRole(e.target.value as Role)}><option value="USER">User (pemain)</option><option value="ADMIN">Admin</option></Select></Field>
        <Field label="Profil pemain" hint="Hubungkan akun dengan data pemain agar statistik pribadi tampil">
          <Select value={playerId} onChange={(e) => setPlayerId(e.target.value)}>
            <option value="">— Tidak terhubung —</option>
            {players.data?.content.map((p) => <option key={p.id} value={p.id}>{p.nickname} ({p.fullName})</option>)}
          </Select>
        </Field>
        {account && <Toggle label="Akun aktif" checked={enabled} onChange={setEnabled} />}
        {account && (
          <div className="space-y-2 rounded-2xl bg-slate-50 p-4">
            <p className="flex items-center gap-2 text-sm font-semibold text-slate-700"><KeyRound className="size-4" /> Reset password</p>
            <Input type="password" autoComplete="new-password" placeholder="Password baru (min. 8)" value={password} onChange={(e) => setPassword(e.target.value)} />
            <Button variant="secondary" block disabled={password.length < 8} loading={reset.isPending} onClick={() => reset.mutate()}>Reset password</Button>
          </div>
        )}
      </div>
    </Sheet>
  );
}

function AccountsTab() {
  const { data, isLoading, error } = useUsers();
  const { user } = useAuth();
  const [editing, setEditing] = useState<UserAccount | null | undefined>(undefined);
  return (
    <div className="space-y-3">
      <div className="flex justify-end"><Button onClick={() => setEditing(null)}><Plus className="size-4" /> Buat Akun</Button></div>
      <ErrorBox error={error} />
      {isLoading ? <LoadingBlock /> : (
        <Card>
          <ul className="divide-y divide-slate-100">
            {data?.map((u) => (
              <li key={u.id}>
                <button type="button" onClick={() => setEditing(u)} className="flex w-full items-center gap-3 px-4 py-3 text-left hover:bg-slate-50 sm:px-5">
                  <div className="flex size-10 items-center justify-center rounded-full bg-navy-50 text-navy-700"><UserCog className="size-5" /></div>
                  <div className="min-w-0 flex-1">
                    <p className="flex flex-wrap items-center gap-1.5 font-bold text-navy-900">@{u.username}
                      <Badge tone={u.role === 'ADMIN' ? 'orange' : 'slate'}>{u.role}</Badge>
                      {!u.enabled && <Badge tone="red">Nonaktif</Badge>}
                      {u.id === user?.id && <Badge tone="navy">Anda</Badge>}
                    </p>
                    <p className="text-xs text-slate-500">{u.playerNickname ? `Pemain: ${u.playerNickname}` : 'Tidak terhubung ke pemain'} · login terakhir {formatDateTime(u.lastLoginAt)}</p>
                  </div>
                </button>
              </li>
            ))}
          </ul>
        </Card>
      )}
      <UserSheet open={editing !== undefined} account={editing ?? null} onClose={() => setEditing(undefined)} />
    </div>
  );
}

function AuditTab() {
  const [page, setPage] = useState(0);
  const [entity, setEntity] = useState('');
  const { data, isLoading, error } = useAuditLogs(page, entity);
  return (
    <div className="space-y-3">
      <Segmented value={entity} onChange={(v) => { setEntity(v); setPage(0); }} options={[
        { value: '', label: 'Semua' }, { value: 'MATCH', label: 'Game' }, { value: 'SETTINGS', label: 'Harga' },
        { value: 'PLAYER', label: 'Pemain' }, { value: 'USER', label: 'Akun' },
      ]} />
      <ErrorBox error={error} />
      {isLoading ? <LoadingBlock /> : (
        <Card>
          <ul className="divide-y divide-slate-100">
            {data?.content.length === 0 && <li className="px-5 py-6 text-center text-sm text-slate-500">Belum ada log.</li>}
            {data?.content.map((a) => (
              <li key={a.id} className="px-4 py-3 sm:px-5">
                <div className="flex items-start justify-between gap-2">
                  <p className="text-sm text-slate-800">{a.summary}</p>
                  <Badge tone={/CANCEL|CORRECT|PRICE/.test(a.action) ? 'orange' : 'slate'}>{a.action}</Badge>
                </div>
                <p className="mt-0.5 text-xs text-slate-400">{formatDateTime(a.createdAt)} · {a.actorUsername}</p>
                {a.details && <pre className="mt-1.5 overflow-x-auto rounded-lg bg-slate-50 p-2 text-[11px] whitespace-pre-wrap break-all text-slate-600">{a.details}</pre>}
              </li>
            ))}
          </ul>
        </Card>
      )}
      {data && <Pagination page={data.page} totalPages={data.totalPages} onChange={setPage} />}
    </div>
  );
}

export default function SettingsPage() {
  const [tab, setTab] = useState<Tab>('club');
  const { data, isLoading, error } = useSettings();
  const tabs: { value: Tab; label: string }[] = [
    { value: 'club', label: 'Klub & Harga' }, { value: 'permissions', label: 'Izin User' },
    { value: 'accounts', label: 'Akun' }, { value: 'audit', label: 'Audit Log' },
  ];
  return (
    <div className="space-y-4">
      <PageHeader title="Pengaturan" />
      <div className={clsx('-mx-4 px-4 sm:mx-0 sm:px-0')}><Segmented value={tab} onChange={setTab} options={tabs} /></div>
      <ErrorBox error={error} />
      {isLoading || !data ? (!error && <LoadingBlock />) : (
        <>
          {tab === 'club' && <ClubTab settings={data} />}
          {tab === 'permissions' && <PermissionsTab settings={data} />}
          {tab === 'accounts' && <AccountsTab />}
          {tab === 'audit' && <AuditTab />}
        </>
      )}
    </div>
  );
}
