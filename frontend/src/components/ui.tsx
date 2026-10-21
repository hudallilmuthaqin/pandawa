import { clsx } from 'clsx';
import { Loader2, X, Inbox } from 'lucide-react';
import { useEffect, type ButtonHTMLAttributes, type InputHTMLAttributes, type ReactNode, type SelectHTMLAttributes } from 'react';
import type { MatchStatus } from '../lib/types';

// ------------------------------------------------------------------ Button

type Variant = 'primary' | 'secondary' | 'ghost' | 'danger' | 'success' | 'navy';
const variants: Record<Variant, string> = {
  primary: 'bg-brand-500 text-white hover:bg-brand-600 active:bg-brand-700 shadow-sm shadow-brand-500/30',
  secondary: 'bg-white text-slate-800 ring-1 ring-slate-200 hover:bg-slate-50 active:bg-slate-100',
  ghost: 'text-slate-600 hover:bg-slate-100 active:bg-slate-200',
  danger: 'bg-red-600 text-white hover:bg-red-700 active:bg-red-800',
  success: 'bg-emerald-600 text-white hover:bg-emerald-700 active:bg-emerald-800 shadow-sm shadow-emerald-600/30',
  navy: 'bg-navy-900 text-white hover:bg-navy-800 active:bg-navy-950',
};
const sizes = {
  sm: 'h-9 px-3 text-sm rounded-lg gap-1.5',
  md: 'h-11 px-4 text-sm rounded-xl gap-2',
  lg: 'h-14 px-5 text-base rounded-2xl gap-2',
  xl: 'h-20 px-6 text-xl rounded-2xl gap-3',
};

export function Button({
  variant = 'primary', size = 'md', loading, className, children, disabled, block, ...rest
}: ButtonHTMLAttributes<HTMLButtonElement> & { variant?: Variant; size?: keyof typeof sizes; loading?: boolean; block?: boolean }) {
  return (
    <button
      type="button"
      {...rest}
      disabled={disabled || loading}
      className={clsx(
        'inline-flex select-none items-center justify-center font-semibold transition-colors disabled:cursor-not-allowed disabled:opacity-50',
        'focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-brand-500',
        variants[variant], sizes[size], block && 'w-full', className,
      )}
    >
      {loading && <Loader2 className="size-4 animate-spin" />}
      {children}
    </button>
  );
}

export function IconButton({ className, label, ...rest }: ButtonHTMLAttributes<HTMLButtonElement> & { label: string }) {
  return (
    <button
      type="button"
      aria-label={label}
      title={label}
      {...rest}
      className={clsx('inline-flex size-10 items-center justify-center rounded-xl text-slate-600 hover:bg-slate-100 active:bg-slate-200 disabled:opacity-40', className)}
    />
  );
}

// ------------------------------------------------------------------ Card

export function Card({ className, children, ...rest }: { className?: string; children: ReactNode } & React.HTMLAttributes<HTMLDivElement>) {
  return <div {...rest} className={clsx('rounded-2xl bg-white shadow-sm ring-1 ring-slate-200/70', className)}>{children}</div>;
}

export function CardHeader({ title, subtitle, action, icon }: { title: ReactNode; subtitle?: ReactNode; action?: ReactNode; icon?: ReactNode }) {
  return (
    <div className="flex items-start justify-between gap-3 px-4 pt-4 pb-2 sm:px-5">
      <div className="flex min-w-0 items-center gap-2.5">
        {icon && <div className="flex size-9 shrink-0 items-center justify-center rounded-xl bg-brand-50 text-brand-600">{icon}</div>}
        <div className="min-w-0">
          <h2 className="truncate text-base font-bold text-navy-900">{title}</h2>
          {subtitle && <p className="text-xs text-slate-500">{subtitle}</p>}
        </div>
      </div>
      {action}
    </div>
  );
}

export function StatCard({ label, value, hint, icon, tone = 'default' }: {
  label: string; value: ReactNode; hint?: ReactNode; icon?: ReactNode; tone?: 'default' | 'brand' | 'navy';
}) {
  return (
    <div className={clsx('rounded-2xl p-4 ring-1',
      tone === 'brand' && 'bg-brand-500 text-white ring-brand-500',
      tone === 'navy' && 'bg-navy-900 text-white ring-navy-900',
      tone === 'default' && 'bg-white ring-slate-200/70 shadow-sm')}>
      <div className="flex items-center justify-between gap-2">
        <p className={clsx('text-xs font-medium', tone === 'default' ? 'text-slate-500' : 'text-white/80')}>{label}</p>
        {icon && <span className={clsx('[&>svg]:size-4', tone === 'default' ? 'text-brand-500' : 'text-white/80')}>{icon}</span>}
      </div>
      <p className="tabular mt-1.5 text-xl font-extrabold tracking-tight sm:text-2xl">{value}</p>
      {hint && <p className={clsx('mt-0.5 text-xs', tone === 'default' ? 'text-slate-500' : 'text-white/75')}>{hint}</p>}
    </div>
  );
}

// ------------------------------------------------------------------ Badges

const statusStyle: Record<MatchStatus, string> = {
  WAITING: 'bg-slate-100 text-slate-700 ring-slate-200',
  READY: 'bg-sky-50 text-sky-700 ring-sky-200',
  IN_PROGRESS: 'bg-brand-50 text-brand-700 ring-brand-200',
  COMPLETED: 'bg-emerald-50 text-emerald-700 ring-emerald-200',
  CANCELLED: 'bg-red-50 text-red-700 ring-red-200',
};
const statusLabel: Record<MatchStatus, string> = {
  WAITING: 'Menunggu', READY: 'Siap Main', IN_PROGRESS: 'Berlangsung', COMPLETED: 'Selesai', CANCELLED: 'Dibatalkan',
};

export function StatusBadge({ status }: { status: MatchStatus }) {
  return (
    <span className={clsx('inline-flex items-center gap-1 rounded-full px-2.5 py-0.5 text-xs font-semibold ring-1', statusStyle[status])}>
      {status === 'IN_PROGRESS' && <span className="size-1.5 animate-pulse rounded-full bg-brand-500" />}
      {statusLabel[status]}
    </span>
  );
}

export function Badge({ children, tone = 'slate' }: { children: ReactNode; tone?: 'slate' | 'green' | 'red' | 'orange' | 'navy' }) {
  const tones = {
    slate: 'bg-slate-100 text-slate-700', green: 'bg-emerald-50 text-emerald-700', red: 'bg-red-50 text-red-700',
    orange: 'bg-brand-50 text-brand-700', navy: 'bg-navy-50 text-navy-700',
  };
  return <span className={clsx('inline-flex items-center rounded-full px-2 py-0.5 text-xs font-semibold', tones[tone])}>{children}</span>;
}

// ------------------------------------------------------------------ Form controls

export function Field({ label, error, hint, children }: { label: string; error?: string; hint?: ReactNode; children: ReactNode }) {
  return (
    <label className="block">
      <span className="mb-1.5 block text-sm font-medium text-slate-700">{label}</span>
      {children}
      {error ? <span className="mt-1 block text-xs text-red-600">{error}</span> : hint ? <span className="mt-1 block text-xs text-slate-500">{hint}</span> : null}
    </label>
  );
}

const inputCls = 'block h-11 w-full min-w-0 rounded-xl border-0 bg-white px-3.5 text-base text-slate-900 ring-1 ring-slate-300 placeholder:text-slate-400 focus:ring-2 focus:ring-brand-500 focus:outline-none sm:text-sm disabled:bg-slate-50';

export function Input({ className, ...rest }: InputHTMLAttributes<HTMLInputElement>) {
  return <input {...rest} className={clsx(inputCls, className)} />;
}

export function Select({ className, children, ...rest }: SelectHTMLAttributes<HTMLSelectElement>) {
  return <select {...rest} className={clsx(inputCls, 'pr-8', className)}>{children}</select>;
}

export function Textarea({ className, ...rest }: React.TextareaHTMLAttributes<HTMLTextAreaElement>) {
  return <textarea {...rest} className={clsx(inputCls, 'h-auto min-h-20 py-2.5', className)} />;
}

export function Toggle({ checked, onChange, label, description }: { checked: boolean; onChange: (v: boolean) => void; label: string; description?: string }) {
  return (
    <button type="button" role="switch" aria-checked={checked} onClick={() => onChange(!checked)}
      className="flex w-full items-center justify-between gap-4 rounded-xl px-1 py-2.5 text-left">
      <span>
        <span className="block text-sm font-medium text-slate-800">{label}</span>
        {description && <span className="block text-xs text-slate-500">{description}</span>}
      </span>
      <span className={clsx('relative inline-flex h-7 w-12 shrink-0 rounded-full transition-colors', checked ? 'bg-brand-500' : 'bg-slate-300')}>
        <span className={clsx('absolute top-0.5 size-6 rounded-full bg-white shadow transition-transform', checked ? 'translate-x-5.5' : 'translate-x-0.5')} />
      </span>
    </button>
  );
}

// ------------------------------------------------------------------ Sheet (bottom sheet on phones, dialog on desktop)

export function Sheet({ open, onClose, title, children, footer }: {
  open: boolean; onClose: () => void; title: ReactNode; children: ReactNode; footer?: ReactNode;
}) {
  useEffect(() => {
    if (!open) return;
    const onKey = (e: KeyboardEvent) => e.key === 'Escape' && onClose();
    document.addEventListener('keydown', onKey);
    const prev = document.body.style.overflow;
    document.body.style.overflow = 'hidden';
    return () => {
      document.removeEventListener('keydown', onKey);
      document.body.style.overflow = prev;
    };
  }, [open, onClose]);
  if (!open) return null;
  return (
    <div className="fixed inset-0 z-50 flex items-end justify-center sm:items-center sm:p-4" role="dialog" aria-modal="true">
      <div className="animate-fade absolute inset-0 bg-navy-950/50" onClick={onClose} />
      <div className="animate-sheet relative flex max-h-[92dvh] w-full flex-col rounded-t-3xl bg-white shadow-xl sm:max-w-lg sm:rounded-3xl">
        <div className="mx-auto mt-2 h-1.5 w-10 rounded-full bg-slate-200 sm:hidden" />
        <div className="flex items-center justify-between gap-3 px-5 pt-3 pb-2 sm:pt-5">
          <h3 className="text-lg font-bold text-navy-900">{title}</h3>
          <IconButton label="Tutup" onClick={onClose}><X className="size-5" /></IconButton>
        </div>
        <div className="flex-1 overflow-y-auto px-5 pb-4">{children}</div>
        {footer && <div className="pb-safe border-t border-slate-100 px-5 pt-3 pb-4">{footer}</div>}
      </div>
    </div>
  );
}

// ------------------------------------------------------------------ Feedback

export function Spinner({ className }: { className?: string }) {
  return <Loader2 className={clsx('size-6 animate-spin text-brand-500', className)} />;
}

export function LoadingBlock() {
  return <div className="flex justify-center py-16"><Spinner /></div>;
}

export function EmptyState({ title, description, icon, action }: { title: string; description?: string; icon?: ReactNode; action?: ReactNode }) {
  return (
    <div className="flex flex-col items-center px-6 py-10 text-center">
      <div className="mb-3 flex size-12 items-center justify-center rounded-2xl bg-slate-100 text-slate-400">{icon ?? <Inbox className="size-6" />}</div>
      <p className="font-semibold text-slate-800">{title}</p>
      {description && <p className="mt-1 max-w-sm text-sm text-slate-500">{description}</p>}
      {action && <div className="mt-4">{action}</div>}
    </div>
  );
}

export function ErrorBox({ error }: { error: unknown }) {
  if (!error) return null;
  const msg = error instanceof Error ? error.message : 'Terjadi kesalahan';
  return <div className="rounded-xl bg-red-50 px-4 py-3 text-sm text-red-700 ring-1 ring-red-200">{msg}</div>;
}

export function PageHeader({ title, subtitle, action }: { title: string; subtitle?: ReactNode; action?: ReactNode }) {
  return (
    <div className="mb-4 flex flex-wrap items-end justify-between gap-3">
      <div className="min-w-0">
        <h1 className="text-xl font-extrabold tracking-tight text-navy-900 sm:text-2xl">{title}</h1>
        {subtitle && <p className="mt-0.5 text-sm text-slate-500">{subtitle}</p>}
      </div>
      {action && <div className="flex flex-wrap gap-2">{action}</div>}
    </div>
  );
}

export function Pagination({ page, totalPages, onChange }: { page: number; totalPages: number; onChange: (p: number) => void }) {
  if (totalPages <= 1) return null;
  return (
    <div className="flex items-center justify-between gap-2 pt-3">
      <Button variant="secondary" size="sm" disabled={page <= 0} onClick={() => onChange(page - 1)}>Sebelumnya</Button>
      <span className="text-sm text-slate-500">Hal. {page + 1} / {totalPages}</span>
      <Button variant="secondary" size="sm" disabled={page >= totalPages - 1} onClick={() => onChange(page + 1)}>Berikutnya</Button>
    </div>
  );
}

export function Segmented<T extends string>({ value, onChange, options }: { value: T; onChange: (v: T) => void; options: { value: T; label: string }[] }) {
  return (
    <div className="flex w-full gap-1 overflow-x-auto rounded-xl bg-slate-200/70 p-1 sm:w-auto">
      {options.map((o) => (
        <button key={o.value} type="button" onClick={() => onChange(o.value)}
          className={clsx('h-9 flex-1 shrink-0 rounded-lg px-3 text-sm font-semibold whitespace-nowrap transition-colors sm:flex-none',
            value === o.value ? 'bg-white text-navy-900 shadow-sm' : 'text-slate-600 hover:text-slate-900')}>
          {o.label}
        </button>
      ))}
    </div>
  );
}
