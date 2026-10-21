import { Suspense, useState } from 'react';
import { NavLink, Outlet, useLocation } from 'react-router-dom';
import { clsx } from 'clsx';
import {
  LayoutDashboard, Users, ListOrdered, Radio, History, Wallet, CalendarDays, CalendarRange, CalendarSearch,
  Settings, UserRound, Menu, LogOut, X,
} from 'lucide-react';
import { useAuth } from '../auth/AuthContext';
import { useQueue, useSettings } from '../lib/queries';
import type { Permission } from '../lib/types';
import { LoadingBlock } from '../components/ui';

interface NavItem { to: string; label: string; icon: typeof Users; admin?: boolean; userOnly?: boolean; perm?: Permission; end?: boolean }

const NAV: NavItem[] = [
  { to: '/', label: 'Dashboard', icon: LayoutDashboard, end: true },
  { to: '/players', label: 'Daftar Pemain', icon: Users, admin: true },
  { to: '/queue', label: 'Jadwal & Queue', icon: ListOrdered },
  { to: '/live', label: 'Game Berlangsung', icon: Radio },
  { to: '/history', label: 'Riwayat Game', icon: History },
  { to: '/expenses', label: 'Pengeluaran', icon: Wallet, admin: true },
  { to: '/reports/daily', label: 'Rekap Harian', icon: CalendarDays, admin: true },
  { to: '/reports/monthly', label: 'Rekap Bulanan', icon: CalendarRange, admin: true },
  { to: '/reports/range', label: 'Rekap Range Date', icon: CalendarSearch, admin: true },
  { to: '/me', label: 'Detail Pemain (Saya)', icon: UserRound, userOnly: true },
  { to: '/settings', label: 'Pengaturan', icon: Settings, admin: true },
];

function Brand({ name }: { name: string }) {
  return (
    <div className="flex items-center gap-2.5">
      <div className="flex size-9 items-center justify-center rounded-xl bg-brand-500 text-lg font-black text-white shadow-md shadow-brand-500/40">P</div>
      <div className="leading-tight">
        <p className="text-sm font-black tracking-wide text-white">{name.toUpperCase()}</p>
        <p className="text-[11px] text-navy-100/70">Badminton Club</p>
      </div>
    </div>
  );
}

export function AppLayout() {
  const { user, isAdmin, logout } = useAuth();
  const { data: settings } = useSettings();
  const { data: queue } = useQueue();
  const [drawer, setDrawer] = useState(false);
  const location = useLocation();
  const live = !!queue?.current;
  const items = NAV.filter((n) => (n.admin ? isAdmin : true) && (n.userOnly ? !isAdmin && user?.playerId : true));
  const clubName = settings?.clubName ?? 'PB Pandawa';

  const sideLink = (n: NavItem) => (
    <NavLink key={n.to} to={n.to} end={n.end} onClick={() => setDrawer(false)}
      className={({ isActive }) => clsx('flex h-11 items-center gap-3 rounded-xl px-3 text-sm font-medium transition-colors',
        isActive ? 'bg-brand-500 text-white shadow-md shadow-brand-500/30' : 'text-navy-100/80 hover:bg-white/10 hover:text-white')}>
      <n.icon className="size-5 shrink-0" />
      <span className="flex-1 truncate">{n.label}</span>
      {n.to === '/live' && live && <span className="size-2 animate-pulse rounded-full bg-emerald-400" />}
    </NavLink>
  );

  const account = (
    <div className="border-t border-white/10 p-3">
      <div className="mb-2 flex items-center gap-3 px-2">
        <div className="flex size-9 items-center justify-center rounded-full bg-white/10 text-sm font-bold text-white">{user?.username.slice(0, 1).toUpperCase()}</div>
        <div className="min-w-0 flex-1">
          <p className="truncate text-sm font-semibold text-white">{user?.playerNickname ?? user?.username}</p>
          <p className="text-xs text-navy-100/60">{isAdmin ? 'Admin' : 'Pemain'}</p>
        </div>
      </div>
      <button type="button" onClick={() => logout()} className="flex h-10 w-full items-center gap-3 rounded-xl px-3 text-sm text-navy-100/80 hover:bg-white/10 hover:text-white">
        <LogOut className="size-4" /> Keluar
      </button>
    </div>
  );

  const bottom: { to: string; label: string; icon: typeof Users; end?: boolean }[] = [
    { to: '/', label: 'Beranda', icon: LayoutDashboard, end: true },
    { to: '/queue', label: 'Antrean', icon: ListOrdered },
    { to: '/live', label: 'Live', icon: Radio },
    { to: '/history', label: 'Riwayat', icon: History },
  ];

  return (
    <div className="min-h-dvh lg:flex">
      {/* Desktop sidebar */}
      <aside className="sticky top-0 hidden h-dvh w-64 shrink-0 flex-col bg-navy-900 lg:flex">
        <div className="px-5 py-5"><Brand name={clubName} /></div>
        <nav className="flex-1 space-y-1 overflow-y-auto px-3">{items.map(sideLink)}</nav>
        {account}
      </aside>

      {/* Mobile top bar */}
      <header className="sticky top-0 z-30 flex h-14 items-center justify-between bg-navy-900 px-4 lg:hidden">
        <Brand name={clubName} />
        {live && location.pathname !== '/live' && (
          <NavLink to="/live" className="flex items-center gap-1.5 rounded-full bg-brand-500 px-3 py-1.5 text-xs font-bold text-white">
            <span className="size-2 animate-pulse rounded-full bg-white" /> LIVE
          </NavLink>
        )}
      </header>

      <main className="min-w-0 flex-1 px-4 pt-4 pb-28 sm:px-6 lg:px-8 lg:pt-8 lg:pb-10">
        <div className="mx-auto max-w-6xl"><Suspense fallback={<LoadingBlock />}><Outlet /></Suspense></div>
      </main>

      {/* Mobile bottom navigation */}
      <nav className="pb-safe fixed inset-x-0 bottom-0 z-40 border-t border-slate-200 bg-white/95 backdrop-blur lg:hidden">
        <div className="grid grid-cols-5">
          {bottom.map((b) => (
            <NavLink key={b.to} to={b.to} end={b.end}
              className={({ isActive }) => clsx('relative flex h-16 flex-col items-center justify-center gap-0.5 text-[11px] font-semibold',
                isActive ? 'text-brand-600' : 'text-slate-500')}>
              {b.to === '/live' ? (
                <span className={clsx('-mt-6 flex size-14 items-center justify-center rounded-2xl text-white shadow-lg ring-4 ring-white',
                  live ? 'bg-brand-500 shadow-brand-500/40' : 'bg-navy-900')}>
                  <b.icon className={clsx('size-6', live && 'animate-pulse')} />
                </span>
              ) : <b.icon className="size-6" />}
              {b.label}
            </NavLink>
          ))}
          <button type="button" onClick={() => setDrawer(true)} className="flex h-16 flex-col items-center justify-center gap-0.5 text-[11px] font-semibold text-slate-500">
            <Menu className="size-6" /> Menu
          </button>
        </div>
      </nav>

      {/* Mobile drawer */}
      {drawer && (
        <div className="fixed inset-0 z-50 lg:hidden">
          <div className="animate-fade absolute inset-0 bg-navy-950/60" onClick={() => setDrawer(false)} />
          <div className="absolute inset-y-0 right-0 flex w-[82%] max-w-xs flex-col bg-navy-900">
            <div className="flex items-center justify-between px-4 py-4">
              <Brand name={clubName} />
              <button type="button" aria-label="Tutup menu" onClick={() => setDrawer(false)} className="flex size-10 items-center justify-center rounded-xl text-white hover:bg-white/10"><X className="size-5" /></button>
            </div>
            <nav className="flex-1 space-y-1 overflow-y-auto px-3">{items.map(sideLink)}</nav>
            <div className="pb-safe">{account}</div>
          </div>
        </div>
      )}
    </div>
  );
}
