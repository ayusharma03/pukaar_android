// Small shared pieces: icons, buttons, status chip, needs, arrival, battery, times, empty state.
// Colour always comes with an icon and a word (docs/design-tokens.md).
import type { ButtonHTMLAttributes, ReactNode } from 'react';
import { NEEDS, STATUS_ICON, STATUS_LABEL, mainVia, viaLabel } from '../lib/format';
import { clockTime, exactTime, timeAgo } from '../lib/time';
import type { Need, Sos, Status, Via } from '../lib/types';

export function cx(...parts: (string | false | null | undefined)[]) {
  return parts.filter(Boolean).join(' ');
}

/** Material Symbols Rounded. `fill` for chips, status and selected states. */
export function Icon({ name, size = 20, fill, className }: { name: string; size?: number; fill?: boolean; className?: string }) {
  return (
    <span aria-hidden className={cx('icon', fill && 'icon-fill', className)} style={{ fontSize: size }}>
      {name}
    </span>
  );
}

/** The app's custom mesh icon (three phones linked by arcs). */
export function MeshIcon({ size = 20, className }: { size?: number; className?: string }) {
  return (
    <svg aria-hidden width={size} height={size} viewBox="0 0 24 24" className={className} fill="currentColor" stroke="currentColor">
      <circle cx="5" cy="17" r="2.2" stroke="none" />
      <circle cx="19" cy="17" r="2.2" stroke="none" />
      <circle cx="12" cy="6" r="2.2" stroke="none" />
      <g fill="none" strokeWidth="2" strokeLinecap="round">
        <path d="M6.5 13.5Q8 9 10 7.5" />
        <path d="M17.5 13.5Q16 9 14 7.5" />
        <path d="M8 18.5Q12 20.5 16 18.5" />
      </g>
    </svg>
  );
}

/** The app's custom LoRa radio icon (antenna with waves). */
export function RadioIcon({ size = 20, className }: { size?: number; className?: string }) {
  return (
    <svg aria-hidden width={size} height={size} viewBox="0 0 24 24" className={className} fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round">
      <path d="M12 10v11" />
      <circle cx="12" cy="8" r="2" fill="currentColor" stroke="none" />
      <path d="M8.5 4.5a5 5 0 0 0 0 7" />
      <path d="M15.5 4.5a5 5 0 0 1 0 7" />
    </svg>
  );
}

/** The Pukaar mark (P-ripple), drawn in currentColor. */
export function PukaarMark({ size = 24 }: { size?: number }) {
  return (
    <svg aria-hidden width={size} height={size} viewBox="21 21 66 66">
      <g transform="translate(54 54) scale(0.86) translate(-54 -54)" fill="none" stroke="currentColor" strokeWidth="8.5" strokeLinecap="round">
        <circle cx="56" cy="46" r="8.5" fill="currentColor" stroke="none" />
        <path d="M39 46A17 17 0 1 1 63.18 61.41" />
        <path d="M39 46V76" />
        <circle cx="74.35" cy="72.21" r="6.5" fill="currentColor" stroke="none" />
      </g>
    </svg>
  );
}

type Variant = 'filled' | 'tonal' | 'outlined' | 'text' | 'sos';

const VARIANT: Record<Variant, string> = {
  filled: 'bg-primary text-on-primary hover:brightness-110',
  tonal: 'bg-secondary-container text-on-secondary-container hover:brightness-110',
  outlined: 'border border-outline text-primary hover:bg-surface-container-high',
  text: 'text-primary hover:bg-surface-container-high',
  sos: 'bg-sos-fill text-on-sos-fill hover:brightness-110',
};

export function Button({
  variant = 'filled',
  icon,
  children,
  className,
  kbd,
  ...rest
}: ButtonHTMLAttributes<HTMLButtonElement> & { variant?: Variant; icon?: string; kbd?: string }) {
  return (
    <button
      type="button"
      {...rest}
      className={cx(
        'inline-flex h-10 items-center justify-center gap-2 rounded-full px-4 text-list font-semibold transition-colors',
        'focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary',
        'disabled:cursor-not-allowed disabled:opacity-40 disabled:hover:brightness-100',
        VARIANT[variant],
        className,
      )}
    >
      {icon && <Icon name={icon} size={18} />}
      {children}
      {kbd && <Kbd>{kbd}</Kbd>}
    </button>
  );
}

export function IconButton({ icon, label, active, className, ...rest }: ButtonHTMLAttributes<HTMLButtonElement> & { icon: string; label: string; active?: boolean }) {
  return (
    <button
      type="button"
      aria-label={label}
      title={label}
      {...rest}
      className={cx(
        'inline-flex size-10 items-center justify-center rounded-full text-on-surface-variant transition-colors hover:bg-surface-container-high',
        'focus-visible:outline-2 focus-visible:outline-primary disabled:opacity-40',
        active && 'bg-secondary-container text-on-secondary-container',
        className,
      )}
    >
      <Icon name={icon} size={20} fill={active} />
    </button>
  );
}

export function Kbd({ children }: { children: ReactNode }) {
  return (
    <kbd className="inline-flex min-w-5 items-center justify-center rounded-chip-sm border border-current/40 px-1 font-sans text-caption opacity-80">
      {children}
    </kbd>
  );
}

const STATUS_STYLE: Record<Status, string> = {
  new: 'bg-sos-container text-on-sos-container',
  attended: 'bg-primary-container text-on-primary-container',
  resolved: 'bg-confirmed-container text-on-confirmed-container',
};

export function StatusChip({ status, large, safe }: { status: Status; large?: boolean; safe?: boolean }) {
  const label = safe && status !== 'resolved' ? 'Marked safe' : STATUS_LABEL[status];
  const icon = safe && status !== 'resolved' ? 'verified_user' : STATUS_ICON[status];
  const style = safe && status !== 'resolved' ? STATUS_STYLE.resolved : STATUS_STYLE[status];
  return (
    <span className={cx('inline-flex shrink-0 items-center gap-1 rounded-chip px-2 font-semibold', large ? 'h-8 text-list' : 'h-6 text-caption', style)}>
      <Icon name={icon} size={large ? 18 : 16} fill />
      {label}
    </span>
  );
}

export function NeedTag({ need }: { need: Need }) {
  const n = NEEDS.find((x) => x.key === need);
  if (!n) return null;
  return (
    <span className={cx('inline-flex items-center gap-1 text-list', n.strong ? 'font-semibold text-on-surface' : 'text-on-surface-variant')}>
      <Icon name={n.icon} size={16} fill={n.strong} />
      {n.label}
    </span>
  );
}

export function Needs({ flags }: { flags: Need[] }) {
  const ordered = NEEDS.filter((n) => flags.includes(n.key));
  if (!ordered.length) return null;
  return (
    <span className="flex flex-wrap items-center gap-x-3 gap-y-1">
      {ordered.map((n) => (
        <NeedTag key={n.key} need={n.key} />
      ))}
    </span>
  );
}

export function ViaTag({ sos, via }: { sos: Sos; via?: Via }) {
  const v = via ?? mainVia(sos);
  const label = viaLabel(sos, v);
  if (v === 'mesh')
    return (
      <span className="inline-flex items-center gap-1 text-list text-mesh">
        <MeshIcon size={16} />
        {label}
      </span>
    );
  if (v === 'radio')
    return (
      <span className="inline-flex items-center gap-1 text-list text-radio">
        <RadioIcon size={16} />
        {label}
      </span>
    );
  return (
    <span className="inline-flex items-center gap-1 text-list text-on-surface-variant">
      <Icon name="cloud_upload" size={16} />
      {label}
    </span>
  );
}

/** At 20% or below: warning icon and colour. Above: bars in onSurfaceVariant. */
export function Battery({ value }: { value: number | null }) {
  if (value == null)
    return (
      <span className="inline-flex items-center gap-1 text-list text-on-surface-variant" title="Battery not reported">
        <Icon name="battery_unknown" size={16} />?
      </span>
    );
  if (value <= 20)
    return (
      <span className="inline-flex items-center gap-1 text-list font-semibold text-warning" title={`Battery ${value}%, low`}>
        <Icon name="battery_alert" size={16} fill />
        {value}%
      </span>
    );
  const bars = Math.min(6, Math.max(1, Math.round(value / 16.7)));
  return (
    <span className="inline-flex items-center gap-1 text-list text-on-surface-variant" title={`Battery ${value}%`}>
      <Icon name={bars === 6 ? 'battery_full' : `battery_${bars}_bar`} size={16} />
      {value}%
    </span>
  );
}

/** "4 min ago" with the exact Indian time on hover. */
export function TimeAgo({ time, now, className }: { time: number; now: number; className?: string }) {
  return (
    <time dateTime={new Date(time * 1000).toISOString()} title={exactTime(time)} className={className}>
      {timeAgo(time, now)}
    </time>
  );
}

/** "4:12 pm" with the exact time on hover. */
export function Clock({ time, className }: { time: number; className?: string }) {
  return (
    <time dateTime={new Date(time * 1000).toISOString()} title={exactTime(time)} className={className}>
      {clockTime(time)}
    </time>
  );
}

/** Ripple-ring icon, title and one line (handoff "Empty states"). */
export function EmptyState({ icon, title, children, action }: { icon: string; title: string; children?: ReactNode; action?: ReactNode }) {
  return (
    <div className="flex flex-col items-center gap-3 px-8 py-12 text-center">
      <span className="relative flex size-20 items-center justify-center">
        <span className="absolute inset-0 rounded-full border border-outline-variant" />
        <span className="absolute inset-3 rounded-full border border-outline-variant" />
        <span className="flex size-10 items-center justify-center rounded-full bg-surface-container-high text-primary">
          <Icon name={icon} size={22} />
        </span>
      </span>
      <h3 className="text-empty-title font-semibold">{title}</h3>
      {children && <p className="max-w-80 text-list text-on-surface-variant">{children}</p>}
      {action}
    </div>
  );
}

export function Spinner({ label }: { label: string }) {
  return (
    <div role="status" className="flex items-center justify-center gap-3 p-8 text-on-surface-variant">
      <span className="size-5 animate-spin rounded-full border-2 border-outline-variant border-t-primary" />
      {label}
    </div>
  );
}
