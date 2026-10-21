import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import { Link, useNavigate } from 'react-router-dom';
import { useState } from 'react';
import { useAuth } from '../auth/AuthContext';
import { Button, ErrorBox, Field, Input } from '../components/ui';
import { AuthShell } from './LoginPage';

const schema = z.object({
  fullName: z.string().trim().min(1, 'Nama lengkap wajib diisi').max(100),
  nickname: z.string().trim().min(1, 'Nickname wajib diisi').max(40),
  phone: z.string().trim().max(30).regex(/^$|^[0-9+\-\s()]{6,30}$/, 'Nomor telepon tidak valid'),
  username: z.string().trim().regex(/^[a-zA-Z0-9._@-]{3,60}$/, 'Username 3-60 karakter (huruf, angka, . _ @ -)'),
  password: z.string().min(8, 'Password minimal 8 karakter').max(72),
});
type Form = z.infer<typeof schema>;

export default function RegisterPage() {
  const { register: signUp } = useAuth();
  const navigate = useNavigate();
  const [error, setError] = useState<unknown>(null);
  const { register, handleSubmit, formState: { errors, isSubmitting } } = useForm<Form>({ resolver: zodResolver(schema), defaultValues: { phone: '' } });
  return (
    <AuthShell title="Daftar sebagai pemain">
      <form className="space-y-4" onSubmit={handleSubmit(async (v) => {
        setError(null);
        try {
          await signUp(v);
          navigate('/');
        } catch (e) {
          setError(e);
        }
      })}>
        <ErrorBox error={error} />
        <Field label="Nama lengkap" error={errors.fullName?.message}><Input {...register('fullName')} /></Field>
        <Field label="Nickname / nama panggilan" error={errors.nickname?.message} hint="Harus unik, dipakai di antrean & statistik"><Input {...register('nickname')} /></Field>
        <Field label="Nomor telepon (opsional)" error={errors.phone?.message}><Input type="tel" inputMode="tel" {...register('phone')} /></Field>
        <Field label="Username" error={errors.username?.message}><Input autoCapitalize="none" autoComplete="username" {...register('username')} /></Field>
        <Field label="Password" error={errors.password?.message}><Input type="password" autoComplete="new-password" {...register('password')} /></Field>
        <Button type="submit" size="lg" block loading={isSubmitting}>Daftar</Button>
      </form>
      <p className="mt-6 text-center text-sm text-slate-600">Sudah punya akun? <Link to="/login" className="font-semibold text-brand-600">Masuk</Link></p>
    </AuthShell>
  );
}
