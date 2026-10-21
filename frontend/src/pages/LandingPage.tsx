import { Link } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import {
  ListOrdered, Zap, Receipt, BarChart3, CalendarRange, Smartphone,
  ArrowRight, CheckCircle2, ShieldCheck, Users, ChevronDown,
} from 'lucide-react';
import { api } from '../lib/api';
import { Button } from '../components/ui';

const FEATURES = [
  {
    icon: ListOrdered,
    title: 'Antrean 2 vs 2',
    desc: 'Susun antrean pemain otomatis, lihat siapa siap main berikutnya tanpa dicatat manual.',
  },
  {
    icon: Zap,
    title: 'Game Berlangsung',
    desc: 'Tombol besar MULAI, +1 KOK, dan SELESAI — mudah dijangkau satu tangan saat di lapangan.',
  },
  {
    icon: Receipt,
    title: 'Biaya Kok & Lapangan',
    desc: 'Biaya kok dan sewa lapangan dihitung otomatis per pemain, sesuai jumlah game yang dimainkan.',
  },
  {
    icon: BarChart3,
    title: 'Statistik Pemain',
    desc: 'Riwayat permainan dan statistik tiap pemain tersimpan rapi, bisa dilihat kapan saja.',
  },
  {
    icon: CalendarRange,
    title: 'Rekap Harian & Bulanan',
    desc: 'Laporan harian, bulanan, hingga rentang tanggal untuk kebutuhan kas dan administrasi klub.',
  },
  {
    icon: Smartphone,
    title: 'Mobile-First',
    desc: 'Didesain untuk dipakai langsung dari HP di pinggir lapangan, bukan dari balik meja.',
  },
];

const STEPS = [
  { title: 'Daftarkan pemain', desc: 'Admin menambahkan pemain klub satu kali di awal.' },
  { title: 'Susun antrean', desc: 'Pilih 4 pemain, antrean 2 vs 2 terbentuk otomatis.' },
  { title: 'Main & catat kok', desc: 'Mulai game, tambahkan kok terpakai dengan satu ketukan.' },
  { title: 'Selesai, lihat rekap', desc: 'Biaya dan statistik terhitung sendiri, siap untuk laporan.' },
];

export default function LandingPage() {
  const { data: pub } = useQuery({
    queryKey: ['public-settings'],
    queryFn: () => api.get<{ clubName: string; selfRegistrationEnabled: boolean }>('/api/settings/public'),
  });
  const clubName = pub?.clubName || 'PB PANDAWA';

  return (
    <div className="bg-navy-900 text-white">
      {/* Nav + Hero fill exactly one screen */}
      <div className="flex min-h-dvh flex-col">
        <header className="mx-auto flex w-full max-w-6xl shrink-0 items-center justify-between px-5 py-5 sm:px-8">
          <div className="flex items-center gap-2.5">
            <div className="flex size-9 items-center justify-center rounded-xl bg-brand-500 text-lg font-black shadow-lg shadow-brand-500/30">P</div>
            <span className="text-base font-extrabold tracking-wide">{clubName}</span>
          </div>
          <div className="flex items-center gap-2">
            <Link to="/login"><Button variant="ghost" className="text-white hover:bg-white/10 active:bg-white/15">Masuk</Button></Link>
            {pub?.selfRegistrationEnabled && (
              <Link to="/register"><Button variant="primary">Daftar</Button></Link>
            )}
          </div>
        </header>

        {/* Hero */}
        <section className="relative flex flex-1 flex-col items-center justify-center overflow-hidden px-5 py-10 text-center">
          <div className="absolute -top-32 left-1/2 size-96 -translate-x-1/2 rounded-full bg-brand-500/20 blur-3xl" />
          <div className="relative mx-auto max-w-2xl">
            <span className="inline-flex items-center gap-1.5 rounded-full bg-white/10 px-3 py-1 text-xs font-semibold text-navy-100 ring-1 ring-white/15">
              <ShieldCheck className="size-3.5" /> Manajemen klub badminton, satu lapangan
            </span>
            <h1 className="mt-5 text-3xl font-black tracking-tight sm:text-5xl">
              Kelola antrean, kok, dan kas klub <span className="text-brand-400">dari HP</span>
            </h1>
            <p className="mt-4 text-base text-navy-100/80 sm:text-lg">
              {clubName} memudahkan antrean 2 vs 2, pencatatan kok, biaya lapangan, serta rekap harian dan bulanan —
              tanpa catatan kertas atau grup chat yang berantakan.
            </p>
            <div className="mt-8 flex flex-wrap items-center justify-center gap-3">
              <Link to="/login">
                <Button size="lg" className="gap-2">Masuk ke akun <ArrowRight className="size-4" /></Button>
              </Link>
              {pub?.selfRegistrationEnabled && (
                <Link to="/register">
                  <Button size="lg" variant="secondary" className="bg-white/10 text-white ring-white/20 hover:bg-white/15">
                    Daftar sebagai pemain
                  </Button>
                </Link>
              )}
            </div>
          </div>
          <a
            href="#fitur"
            aria-label="Lihat fitur"
            onClick={(e) => {
              e.preventDefault();
              document.getElementById('fitur')?.scrollIntoView({ behavior: 'smooth' });
            }}
            className="absolute bottom-6 flex flex-col items-center gap-1 text-navy-100/50 transition-colors hover:text-navy-100"
          >
            <span className="text-xs font-medium">Lihat fitur</span>
            <ChevronDown className="size-5" />
          </a>
        </section>
      </div>

      {/* Content on light background */}
      <div id="fitur" className="rounded-t-3xl bg-slate-50 text-slate-900">
        {/* Features */}
        <section className="mx-auto max-w-6xl px-5 py-14 sm:px-8 sm:py-20">
          <div className="mx-auto max-w-xl text-center">
            <h2 className="text-2xl font-extrabold tracking-tight text-navy-900 sm:text-3xl">Semua yang dibutuhkan satu lapangan</h2>
            <p className="mt-2 text-sm text-slate-500 sm:text-base">Dirancang berdasarkan alur main sehari-hari, bukan fitur yang berlebihan.</p>
          </div>
          <div className="mt-10 grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-3">
            {FEATURES.map(({ icon: Icon, title, desc }) => (
              <div key={title} className="rounded-2xl bg-white p-5 shadow-sm ring-1 ring-slate-200/70">
                <div className="flex size-10 items-center justify-center rounded-xl bg-brand-50 text-brand-600">
                  <Icon className="size-5" />
                </div>
                <h3 className="mt-3.5 text-sm font-bold text-navy-900">{title}</h3>
                <p className="mt-1 text-sm text-slate-500">{desc}</p>
              </div>
            ))}
          </div>
        </section>

        {/* How it works */}
        <section className="bg-white px-5 py-14 sm:px-8 sm:py-20">
          <div className="mx-auto max-w-6xl">
            <div className="mx-auto max-w-xl text-center">
              <h2 className="text-2xl font-extrabold tracking-tight text-navy-900 sm:text-3xl">Cara kerjanya</h2>
              <p className="mt-2 text-sm text-slate-500 sm:text-base">Dari antrean sampai rekap kas, hanya butuh beberapa ketukan.</p>
            </div>
            <div className="mt-10 grid grid-cols-1 gap-6 sm:grid-cols-2 lg:grid-cols-4">
              {STEPS.map((step, i) => (
                <div key={step.title} className="relative rounded-2xl bg-slate-50 p-5 ring-1 ring-slate-200/70">
                  <span className="flex size-8 items-center justify-center rounded-full bg-navy-900 text-sm font-bold text-white">{i + 1}</span>
                  <h3 className="mt-3.5 text-sm font-bold text-navy-900">{step.title}</h3>
                  <p className="mt-1 text-sm text-slate-500">{step.desc}</p>
                </div>
              ))}
            </div>
          </div>
        </section>

        {/* Trust strip */}
        <section className="px-5 py-10 sm:px-8">
          <div className="mx-auto flex max-w-6xl flex-wrap items-center justify-center gap-x-8 gap-y-3 text-sm font-medium text-slate-500">
            <span className="flex items-center gap-1.5"><CheckCircle2 className="size-4 text-emerald-600" /> Biaya dihitung otomatis</span>
            <span className="flex items-center gap-1.5"><CheckCircle2 className="size-4 text-emerald-600" /> Riwayat tersimpan rapi</span>
            <span className="flex items-center gap-1.5"><CheckCircle2 className="size-4 text-emerald-600" /> Akses berbasis peran</span>
            <span className="flex items-center gap-1.5"><Users className="size-4 text-navy-700" /> Dipakai tiap sesi latihan</span>
          </div>
        </section>

        {/* CTA */}
        <section className="px-5 pb-16 sm:px-8">
          <div className="mx-auto max-w-4xl rounded-3xl bg-navy-900 px-6 py-10 text-center text-white sm:px-10 sm:py-14">
            <h2 className="text-xl font-extrabold tracking-tight sm:text-2xl">Siap rapikan sesi main klub Anda?</h2>
            <p className="mt-2 text-sm text-navy-100/80 sm:text-base">Masuk dengan akun yang sudah diberikan admin, atau daftar jika pendaftaran mandiri dibuka.</p>
            <div className="mt-6 flex flex-wrap items-center justify-center gap-3">
              <Link to="/login"><Button size="lg" className="gap-2">Masuk sekarang <ArrowRight className="size-4" /></Button></Link>
              {pub?.selfRegistrationEnabled && (
                <Link to="/register">
                  <Button size="lg" variant="secondary" className="bg-white/10 text-white ring-white/20 hover:bg-white/15">Daftar sebagai pemain</Button>
                </Link>
              )}
            </div>
          </div>
        </section>

        <footer className="border-t border-slate-200 px-5 py-6 text-center text-xs text-slate-400">
          {clubName} · Badminton Club Management
        </footer>
      </div>
    </div>
  );
}
