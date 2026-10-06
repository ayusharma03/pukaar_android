// D2 top bar (56 px), filter bar (48 px) and lost-connection banner (48 px).
import { useEffect, useRef, useState, type ReactNode, type RefObject } from 'react';
import { NavLink } from 'react-router-dom';
import { useAuth } from '../auth/auth';
import { DISTRICT, NEEDS, ROLE_LABEL, STATUS_ICON, STATUS_LABEL, initials } from '../lib/format';
import { TIME_LABEL, type Filters, type TimeRange } from '../lib/filters';
import { clockTime, exactTime } from '../lib/time';
import type { Need, Status, Via } from '../lib/types';
import { Button, Icon, IconButton, Kbd, PukaarMark, cx } from '../ui/atoms';
import { useTheme } from '../ui/theme';
import type { Connection } from './useOpsData';

const COUNT_STYLE: Record<Status, string> = {
  new: 'bg-sos-container text-on-sos-container',
  attended: 'bg-primary-container text-on-primary-container',
  resolved: 'bg-confirmed-container text-on-confirmed-container',
};

export function TopBar({
  counts,
  statuses,
  onToggleStatus,
  search,
  onSearch,
  searchRef,
  connection,
  now,
  soundOn,
  onToggleSound,
}: {
  counts: Record<Status, number | null>;
  statuses: Set<Status>;
  onToggleStatus: (s: Status) => void;
  search: string;
  onSearch: (q: string) => void;
  searchRef: RefObject<HTMLInputElement | null>;
  connection: Connection;
  now: number;
  soundOn: boolean;
  onToggleSound: () => void;
}) {
  const { theme, toggle } = useTheme();
  const { role } = useAuth();
  return (
    <header className="flex h-topbar shrink-0 items-center gap-3 border-b border-outline-variant bg-surface-container-low px-4">
      <div className="flex items-center gap-2.5">
        <span className="flex size-8 items-center justify-center rounded-chip bg-primary-container text-on-primary-container">
          <PukaarMark size={22} />
        </span>
        <div className="leading-tight">
          <p className="text-card-title font-semibold">Pukaar</p>
          <p className="text-caption whitespace-nowrap text-on-surface-variant">{DISTRICT} control room</p>
        </div>
      </div>
      <nav className="flex h-full items-center gap-1" aria-label="Sections">
        <TabLink to="/">Operations</TabLink>
        {role === 'admin' && <TabLink to="/team">Team</TabLink>}
      </nav>
      <div className="flex items-center gap-1.5" role="group" aria-label="Counts by status (click to show or hide)">
        {(['new', 'attended', 'resolved'] as Status[]).map((s) => (
          <button
            key={s}
            type="button"
            aria-pressed={statuses.has(s)}
            title={`${statuses.has(s) ? 'Hide' : 'Show'} ${STATUS_LABEL[s].toLowerCase()} SOS`}
            onClick={() => onToggleStatus(s)}
            className={cx(
              'flex h-8 items-center gap-1.5 rounded-chip px-2.5 transition-opacity',
              statuses.has(s) ? COUNT_STYLE[s] : 'border border-outline-variant text-on-surface-variant opacity-80',
            )}
          >
            <Icon name={STATUS_ICON[s]} size={16} fill={statuses.has(s)} />
            <span className="text-list">{STATUS_LABEL[s]}</span>
            <span className="text-count font-bold">{counts[s] ?? '–'}</span>
          </button>
        ))}
      </div>
      <label className="relative ml-auto flex h-10 w-search min-w-44 shrink items-center rounded-full bg-surface-container-high px-3 focus-within:ring-2 focus-within:ring-primary">
        <Icon name="search" size={20} className="text-on-surface-variant" />
        <input
          ref={searchRef}
          value={search}
          onChange={(e) => onSearch(e.target.value)}
          onKeyDown={(e) => {
            if (e.key === 'Escape') {
              onSearch('');
              e.currentTarget.blur();
            }
          }}
          placeholder="Search name, village, phone"
          aria-label="Search SOS by name, village, phone or id"
          className="min-w-0 flex-1 bg-transparent px-2 text-list text-on-surface outline-none placeholder:text-on-surface-variant"
        />
        {search ? (
          <button type="button" aria-label="Clear search" onClick={() => onSearch('')} className="text-on-surface-variant">
            <Icon name="close" size={18} />
          </button>
        ) : (
          <Kbd>/</Kbd>
        )}
      </label>
      <LiveIndicator connection={connection} now={now} />
      <div className="flex items-center">
        <IconButton icon={soundOn ? 'volume_up' : 'volume_off'} label={soundOn ? 'Sound on for new SOS (M)' : 'Sound off (M)'} onClick={onToggleSound} />
        <IconButton icon={theme === 'dark' ? 'light_mode' : 'dark_mode'} label={theme === 'dark' ? 'Light theme' : 'Dark theme'} onClick={toggle} />
      </div>
      <UserMenu />
    </header>
  );
}

function TabLink({ to, children }: { to: string; children: ReactNode }) {
  return (
    <NavLink
      to={to}
      end
      className={({ isActive }) =>
        cx(
          'flex h-full items-center border-b-2 px-3 text-list font-semibold',
          isActive ? 'border-primary text-on-surface' : 'border-transparent text-on-surface-variant hover:text-on-surface',
        )
      }
    >
      {children}
    </NavLink>
  );
}

export function LiveIndicator({ connection, now }: { connection: Connection; now: number }) {
  if (connection.live) {
    const ago = connection.lastSyncAt ? Math.max(0, Math.round((now - connection.lastSyncAt) / 1000)) : 0;
    return (
      <span className="flex shrink-0 items-center gap-2 text-caption whitespace-nowrap" title="Connected to the server: new SOS appear within a second">
        <span className="relative flex size-2.5">
          <span className="absolute inset-0 rounded-full bg-confirmed motion-safe:animate-ping motion-safe:[animation-iteration-count:3]" />
          <span className="relative size-2.5 rounded-full bg-confirmed" />
        </span>
        <span className="leading-tight">
          <span className="block font-semibold text-confirmed">Live</span>
          <span className="text-on-surface-variant">{ago < 60 ? `updated ${ago}s ago` : `updated ${Math.round(ago / 60)} min ago`}</span>
        </span>
      </span>
    );
  }
  const since = connection.lostAt ? Math.floor(connection.lostAt / 1000) : null;
  return (
    <span className="flex shrink-0 items-center gap-2 text-caption whitespace-nowrap" title={since ? `No server connection since ${exactTime(since)}` : 'Connecting'}>
      <Icon name="cloud_off" size={18} className="text-warning" />
      <span className="leading-tight">
        <span className="block font-semibold text-warning">{since ? 'Not live' : 'Connecting…'}</span>
        {since && <span className="text-on-surface-variant">data from {clockTime(since)}</span>}
      </span>
    </span>
  );
}

function UserMenu() {
  const { name, role, signOut, user } = useAuth();
  const [open, setOpen] = useState(false);
  const ref = useRef<HTMLDivElement>(null);
  useEffect(() => {
    if (!open) return;
    const close = (e: MouseEvent) => !ref.current?.contains(e.target as Node) && setOpen(false);
    document.addEventListener('mousedown', close);
    return () => document.removeEventListener('mousedown', close);
  }, [open]);
  return (
    <div ref={ref} className="relative">
      <button
        type="button"
        aria-expanded={open}
        aria-haspopup="menu"
        onClick={() => setOpen((o) => !o)}
        className="flex items-center gap-2 rounded-full py-1 pr-2 pl-1 hover:bg-surface-container-high"
      >
        <span className="flex size-8 items-center justify-center rounded-full bg-secondary-container text-caption font-bold text-on-secondary-container">
          {initials(name)}
        </span>
        <span className="text-left leading-tight">
          <span className="block max-w-36 truncate text-list font-semibold">{name}</span>
          <span className="text-caption text-on-surface-variant">{role ? ROLE_LABEL[role] : ''}</span>
        </span>
        <Icon name="expand_more" size={18} className="text-on-surface-variant" />
      </button>
      {open && (
        <div role="menu" className="absolute top-12 right-0 z-40 w-64 rounded-card bg-surface-container-high p-2 shadow-popover">
          <p className="px-3 py-2 text-caption text-on-surface-variant">{user?.email}</p>
          <button
            type="button"
            role="menuitem"
            onClick={() => signOut()}
            className="flex w-full items-center gap-2 rounded-chip px-3 py-2 text-list hover:bg-surface-container-highest"
          >
            <Icon name="logout" size={18} />
            Sign out
          </button>
        </div>
      )}
    </div>
  );
}

const VIA_LABEL: Record<Via, string> = { direct: 'Direct', mesh: 'Mesh', radio: 'Radio' };

export function FilterBar({
  filters,
  onChange,
  blocks,
  summary,
  showChat,
  onToggleChat,
  onClear,
  canClear,
}: {
  filters: Filters;
  onChange: (f: Filters) => void;
  blocks: string[];
  summary: string;
  showChat: boolean;
  onToggleChat: () => void;
  onClear: () => void;
  canClear: boolean;
}) {
  return (
    <div className="flex h-filterbar shrink-0 items-center gap-2 border-b border-outline-variant bg-surface px-4">
      <Icon name="filter_list" size={20} className="text-on-surface-variant" />
      <Select
        label="Time"
        value={filters.timeRange}
        options={(Object.keys(TIME_LABEL) as TimeRange[]).map((k) => ({ value: k, label: TIME_LABEL[k] }))}
        onChange={(v) => onChange({ ...filters, timeRange: v as TimeRange })}
        active={filters.timeRange !== '24h'}
      />
      <Select
        label="Area"
        value={filters.block ?? ''}
        options={[{ value: '', label: 'All blocks' }, ...blocks.map((b) => ({ value: b, label: `${b} › all villages` }))]}
        onChange={(v) => onChange({ ...filters, block: v || undefined })}
        active={!!filters.block}
      />
      <NeedsSelect value={filters.needs} onChange={(needs) => onChange({ ...filters, needs })} />
      <Select
        label="Arrived"
        value={filters.via ?? ''}
        options={[{ value: '', label: 'Any way' }, ...(['direct', 'mesh', 'radio'] as Via[]).map((v) => ({ value: v, label: VIA_LABEL[v] }))]}
        onChange={(v) => onChange({ ...filters, via: (v || undefined) as Via | undefined })}
        active={!!filters.via}
      />
      {canClear && (
        <Button variant="text" icon="filter_alt_off" onClick={onClear} className="h-8">
          Clear filters
        </Button>
      )}
      <button
        type="button"
        aria-pressed={showChat}
        onClick={onToggleChat}
        className={cx(
          'flex h-8 items-center gap-1.5 rounded-chip border px-2.5 text-list',
          showChat ? 'border-transparent bg-secondary-container text-on-secondary-container' : 'border-outline-variant text-on-surface-variant',
        )}
      >
        <Icon name={showChat ? 'check' : 'chat_bubble'} size={16} />
        Chat on map
      </button>
      <p className="ml-auto text-list text-on-surface-variant">{summary}</p>
    </div>
  );
}

function Select({
  label,
  value,
  options,
  onChange,
  active,
}: {
  label: string;
  value: string;
  options: { value: string; label: string }[];
  onChange: (v: string) => void;
  active: boolean;
}) {
  return (
    <label
      className={cx(
        'relative flex h-8 items-center gap-1 rounded-chip border pr-1 pl-2.5 text-list',
        active ? 'border-transparent bg-secondary-container text-on-secondary-container' : 'border-outline-variant',
      )}
    >
      <span className="text-on-surface-variant">{label}</span>
      <select
        value={value}
        onChange={(e) => onChange(e.target.value)}
        className="max-w-48 cursor-pointer appearance-none truncate bg-transparent pr-5 font-semibold outline-none [field-sizing:content] [&>option]:bg-surface-container-high [&>option]:text-on-surface"
      >
        {options.map((o) => (
          <option key={o.value} value={o.value}>
            {o.label}
          </option>
        ))}
      </select>
      <Icon name="arrow_drop_down" size={20} className="pointer-events-none absolute right-1 text-on-surface-variant" />
    </label>
  );
}

function NeedsSelect({ value, onChange }: { value: Need[]; onChange: (v: Need[]) => void }) {
  const [open, setOpen] = useState(false);
  const ref = useRef<HTMLDivElement>(null);
  useEffect(() => {
    if (!open) return;
    const close = (e: MouseEvent) => !ref.current?.contains(e.target as Node) && setOpen(false);
    document.addEventListener('mousedown', close);
    return () => document.removeEventListener('mousedown', close);
  }, [open]);
  const label = value.length === 0 ? 'Any' : value.length === 1 ? NEEDS.find((n) => n.key === value[0])!.label : `${value.length} selected`;
  return (
    <div ref={ref} className="relative">
      <button
        type="button"
        aria-expanded={open}
        onClick={() => setOpen((o) => !o)}
        className={cx(
          'flex h-8 items-center gap-1 rounded-chip border pr-1 pl-2.5 text-list',
          value.length ? 'border-transparent bg-secondary-container text-on-secondary-container' : 'border-outline-variant',
        )}
      >
        <span className="text-on-surface-variant">Needs</span>
        <span className="font-semibold">{label}</span>
        <Icon name="arrow_drop_down" size={20} className="text-on-surface-variant" />
      </button>
      {open && (
        <div className="absolute top-10 left-0 z-40 w-56 rounded-card bg-surface-container-high p-2 shadow-popover" role="group" aria-label="Needs">
          {NEEDS.map((n) => (
            <label key={n.key} className="flex cursor-pointer items-center gap-2 rounded-chip px-2 py-1.5 text-list hover:bg-surface-container-highest">
              <input
                type="checkbox"
                checked={value.includes(n.key)}
                onChange={(e) => onChange(e.target.checked ? [...value, n.key] : value.filter((x) => x !== n.key))}
                className="size-4 accent-primary"
              />
              <Icon name={n.icon} size={18} />
              {n.label}
            </label>
          ))}
        </div>
      )}
    </div>
  );
}

export function ConnectionBanner({ connection, onRetry }: { connection: Connection; onRetry: () => void }) {
  if (connection.live || !connection.lostAt) return null;
  const at = clockTime(Math.floor(connection.lostAt / 1000));
  return (
    <div role="alert" className="flex h-banner shrink-0 items-center gap-3 bg-warning-container px-4 text-on-warning-container">
      <Icon name="cloud_off" size={20} />
      <p className="text-list">
        <span className="font-semibold">Lost connection to the server at {at}.</span> You're seeing data from then. New SOS won't appear until it's
        back.{connection.attempts > 0 && ` Reconnecting, attempt ${connection.attempts}…`}
      </p>
      <Button variant="text" onClick={onRetry} className="ml-auto h-8 text-on-warning-container">
        Retry now
      </Button>
    </div>
  );
}

