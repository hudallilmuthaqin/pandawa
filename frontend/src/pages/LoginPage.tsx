import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import { Link } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { useState } from 'react';
import { useAuth } from '../auth/AuthContext';
import { api } from '../lib/api';
import { Button, ErrorBox, Field, Input } from '../components/ui';

const schema = z.object({
  username: z.string().trim().min(1, 'Username wajib diisi'),
  password: z.string().min(1, 'Password wajib diisi'),
});
type Form = z.infer<typeof schema>;

export function AuthShell({ children, title }: { children: React.ReactNode; title: string }) {
  return (
    <div className="flex min-h-dvh flex-col bg-navy-900">
      <div className="relative overflow-hidden px-6 pt-14 pb-10 text-center">
        <div className="absolute -top-24 -right-24 size-72 rounded-full bg-brand-500/20 blur-3xl" />
        <Link to="/" className="relative mb-4 inline-flex items-center gap-1 text-xs font-medium text-navy-100/60 hover:text-navy-100">
          ← Kembali ke beranda
        </Link>
        <div className="relative mx-auto mb-4 flex size-16 items-center justify-center rounded-2xl bg-brand-500 text-3xl font-black text-white shadow-xl shadow-brand-500/40">P</div>
        <h1 className="relative text-2xl font-black tracking-wide text-white">PB PANDAWA</h1>
        <p className="relative mt-1 text-sm text-navy-100/70">Badminton Club Management</p>
      </div>
      <div className="flex-1 rounded-t-3xl bg-slate-50 px-5 pt-7 pb-10 sm:mx-auto sm:mb-10 sm:w-full sm:max-w-md sm:flex-none sm:rounded-3xl sm:px-8">
        <h2 className="mb-5 text-lg font-bold text-navy-900">{title}</h2>
        {children}
      </div>
    </div>
  );
}

export default function LoginPage() {
  const { login } = useAuth();
  const [error, setError] = useState<unknown>(null);
  const { register, handleSubmit, formState: { errors, isSubmitting } } = useForm<Form>({ resolver: zodResolver(schema) });
  const { data: pub } = useQuery({
    queryKey: ['public-settings'],
    queryFn: () => api.get<{ clubName: string; selfRegistrationEnabled: boolean }>('/api/settings/public'),
  });

  return (
    <AuthShell title="Masuk ke akun Anda">
      <form className="space-y-4" onSubmit={handleSubmit(async (v) => {
        setError(null);
        try {
          await login(v.username, v.password);
        } catch (e) {
          setError(e);
        }
      })}>
        <ErrorBox error={error} />
        <Field label="Username" error={errors.username?.message}>
          <Input autoComplete="username" autoCapitalize="none" autoCorrect="off" spellCheck={false} {...register("username")} />
        </Field>
        <Field label="Password" error={errors.password?.message}>
          <Input type="password" autoComplete="current-password" autoCapitalize="none" autoCorrect="off" spellCheck={false} {...register("password")} />
        </Field>
        <Button type="submit" size="lg" block loading={isSubmitting}>Masuk</Button>
      </form>
      {pub?.selfRegistrationEnabled && (
        <p className="mt-6 text-center text-sm text-slate-600">
          Belum punya akun? <Link to="/register" className="font-semibold text-brand-600">Daftar sebagai pemain</Link>
        </p>
      )}
    </AuthShell>
  );
}
