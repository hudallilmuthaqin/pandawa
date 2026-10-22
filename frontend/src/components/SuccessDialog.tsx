import { createContext, useCallback, useContext, useEffect, useRef, useState, type ReactNode } from 'react';
import { Button } from './ui';

interface SuccessOptions {
  title: string;
  message?: string;
  actionLabel?: string;
  /** Closes by itself after this many ms (0 = only by the button). */
  autoCloseMs?: number;
  /** Runs once when the dialog closes, by the button or the timer. */
  onClose?: () => void;
}

const SuccessDialogContext = createContext<(opts: SuccessOptions) => void>(() => {});

/**
 * Mounted above the routes, so the dialog stays visible even when what it announces ends the session
 * (e.g. a password change revokes the token and the app falls back to the login page underneath).
 */
export function SuccessDialogProvider({ children }: { children: ReactNode }) {
  const [opts, setOpts] = useState<SuccessOptions | null>(null);
  const show = useCallback((o: SuccessOptions) => setOpts(o), []);
  return (
    <SuccessDialogContext.Provider value={show}>
      {children}
      {opts && <SuccessDialog key={opts.title + opts.message} {...opts} onClose={() => { setOpts(null); opts.onClose?.(); }} />}
    </SuccessDialogContext.Provider>
  );
}

export const useSuccessDialog = () => useContext(SuccessDialogContext);

/** Shown after the signed-in user's own password changed; {@code logout} runs when it closes. */
export function passwordChangedDialog(logout: () => Promise<void>): SuccessOptions {
  return {
    title: 'Password berhasil diganti',
    message: 'Demi keamanan, Anda akan keluar dari semua perangkat. Silakan masuk lagi dengan password baru.',
    actionLabel: 'Masuk Lagi',
    autoCloseMs: 4000,
    onClose: () => { void logout(); },
  };
}

function SuccessDialog({ title, message, actionLabel = 'OK', autoCloseMs = 3000, onClose }: SuccessOptions) {
  const closed = useRef(false);
  // Kept in a ref so a re-render with a new callback does not restart the auto-close timer.
  const onCloseRef = useRef(onClose);
  onCloseRef.current = onClose;
  const close = useCallback(() => {
    if (closed.current) return;
    closed.current = true;
    onCloseRef.current?.();
  }, []);

  useEffect(() => {
    const onKey = (e: KeyboardEvent) => e.key === 'Escape' && close();
    window.addEventListener('keydown', onKey);
    const timer = autoCloseMs > 0 ? window.setTimeout(close, autoCloseMs) : undefined;
    return () => {
      window.removeEventListener('keydown', onKey);
      window.clearTimeout(timer);
    };
  }, [autoCloseMs, close]);

  return (
    <div className="fixed inset-0 z-[70] flex items-center justify-center p-4">
      <div className="animate-fade absolute inset-0 bg-navy-950/60 backdrop-blur-sm" />
      <div role="alertdialog" aria-modal="true" aria-labelledby="success-title" aria-describedby="success-message"
        className="animate-pop relative w-full max-w-sm overflow-hidden rounded-3xl bg-white px-6 pt-8 pb-6 text-center shadow-2xl">
        <svg viewBox="0 0 52 52" className="mx-auto size-20" aria-hidden="true">
          <circle className="success-ring" cx="26" cy="26" r="24" fill="none" strokeWidth="3" />
          <path className="success-check" fill="none" strokeWidth="4" strokeLinecap="round" strokeLinejoin="round" d="M15 27l7 7 15-16" />
        </svg>
        <h2 id="success-title" className="mt-4 text-lg font-extrabold text-navy-900">{title}</h2>
        {message && <p id="success-message" className="mt-1.5 text-sm text-slate-600">{message}</p>}
        <Button autoFocus size="lg" block className="mt-6" onClick={close}>{actionLabel}</Button>
        {autoCloseMs > 0 && (
          <div className="absolute inset-x-0 bottom-0 h-1 bg-slate-100">
            <div className="success-countdown h-full bg-emerald-500" style={{ animationDuration: `${autoCloseMs}ms` }} />
          </div>
        )}
      </div>
    </div>
  );
}
