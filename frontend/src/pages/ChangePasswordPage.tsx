import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import { useState } from 'react';
import { KeyRound } from 'lucide-react';
import { useAuth } from '../auth/AuthContext';
import { api } from '../lib/api';
import { Button, Card, CardHeader, ErrorBox, Field, Input, PageHeader } from '../components/ui';
import { passwordChangedDialog, useSuccessDialog } from '../components/SuccessDialog';

const schema = z.object({
  currentPassword: z.string().min(1, 'Password lama wajib diisi'),
  newPassword: z.string().min(8, 'Password minimal 8 karakter').max(72, 'Password maksimal 72 karakter'),
  confirmPassword: z.string(),
}).refine((v) => v.newPassword === v.confirmPassword, { path: ['confirmPassword'], message: 'Password tidak sama' })
  .refine((v) => v.newPassword !== v.currentPassword, { path: ['newPassword'], message: 'Password baru sama dengan password lama' });
type Form = z.infer<typeof schema>;

export default function ChangePasswordPage() {
  const { user, logout } = useAuth();
  const showSuccess = useSuccessDialog();
  const [error, setError] = useState<unknown>(null);
  const { register, handleSubmit, formState: { errors, isSubmitting } } = useForm<Form>({ resolver: zodResolver(schema) });

  return (
    <div className="space-y-4">
      <PageHeader title="Ganti Password" subtitle={`Akun @${user?.username}`} />
      <Card className="max-w-lg">
        <CardHeader title="Password baru" icon={<KeyRound className="size-5" />}
          subtitle="Setelah diganti, semua perangkat akan keluar dan Anda perlu masuk lagi dengan password baru." />
        <form className="space-y-4 px-4 pb-5 sm:px-5" onSubmit={handleSubmit(async (v) => {
          setError(null);
          try {
            await api.post('/api/auth/change-password', { currentPassword: v.currentPassword, newPassword: v.newPassword });
          } catch (e) {
            setError(e);
            return;
          }
          // The backend revoked every token of this user, including the current one: log out once the dialog closes.
          showSuccess(passwordChangedDialog(logout));
        })}>
          <ErrorBox error={error} />
          {/* Hidden username lets password managers save the new password for the right account. */}
          <input type="text" autoComplete="username" value={user?.username ?? ''} readOnly hidden />
          <Field label="Password lama" error={errors.currentPassword?.message}>
            <Input type="password" autoComplete="current-password" {...register('currentPassword')} />
          </Field>
          <Field label="Password baru" hint="Minimal 8 karakter" error={errors.newPassword?.message}>
            <Input type="password" autoComplete="new-password" {...register('newPassword')} />
          </Field>
          <Field label="Ulangi password baru" error={errors.confirmPassword?.message}>
            <Input type="password" autoComplete="new-password" {...register('confirmPassword')} />
          </Field>
          <Button type="submit" size="lg" block loading={isSubmitting}>Ganti Password</Button>
        </form>
      </Card>
    </div>
  );
}
