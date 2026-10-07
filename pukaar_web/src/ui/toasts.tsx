// Toasts: bottom-left over the map, 8 s, one action with a key hint, never modal, never steal focus.
import { createContext, useCallback, useContext, useRef, useState, type ReactNode } from 'react';
import { Icon, Kbd, cx } from './atoms';

export interface Toast {
  id: number;
  text: string;
  detail?: string;
  icon?: string;
  tone?: 'normal' | 'error';
  action?: { label: string; kbd?: string; run: () => void };
  ms?: number;
}

type ShowToast = (t: Omit<Toast, 'id'>) => number;

const ToastContext = createContext<{ show: ShowToast; dismiss: (id: number) => void }>({ show: () => 0, dismiss: () => {} });

export function ToastProvider({ children }: { children: ReactNode }) {
  const [toasts, setToasts] = useState<Toast[]>([]);
  const nextId = useRef(1);
  const dismiss = useCallback((id: number) => setToasts((list) => list.filter((t) => t.id !== id)), []);
  const show = useCallback<ShowToast>(
    (t) => {
      const id = nextId.current++;
      setToasts((list) => [...list.slice(-2), { ...t, id }]);
      setTimeout(() => dismiss(id), t.ms ?? 8000);
      return id;
    },
    [dismiss],
  );
  return (
    <ToastContext.Provider value={{ show, dismiss }}>
      {children}
      <div aria-live="polite" className="pointer-events-none fixed bottom-14 left-4 z-50 flex w-toast flex-col gap-2">
        {toasts.map((t) => (
          <div
            key={t.id}
            className={cx(
              'pointer-events-auto flex items-start gap-3 rounded-card bg-inverse-surface px-4 py-3 text-inverse-on-surface',
              'motion-safe:animate-[toast-in_200ms_ease-out]',
            )}
          >
            <Icon name={t.icon ?? (t.tone === 'error' ? 'error' : 'info')} size={20} fill className={t.tone === 'error' ? 'text-inverse-primary' : ''} />
            <div className="min-w-0 flex-1">
              <p className="text-list font-semibold">{t.text}</p>
              {t.detail && <p className="text-caption opacity-80">{t.detail}</p>}
            </div>
            {t.action && (
              <button
                type="button"
                className="flex shrink-0 items-center gap-1.5 rounded-full px-2 py-1 text-list font-semibold text-inverse-primary hover:bg-inverse-on-surface/10"
                onClick={() => {
                  t.action!.run();
                  dismiss(t.id);
                }}
              >
                {t.action.label}
                {t.action.kbd && <Kbd>{t.action.kbd}</Kbd>}
              </button>
            )}
          </div>
        ))}
      </div>
    </ToastContext.Provider>
  );
}

export const useToasts = () => useContext(ToastContext);
