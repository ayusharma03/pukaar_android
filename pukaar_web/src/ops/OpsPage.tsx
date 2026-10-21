// D2 Live operations: map, incident list / relief feed, top bar, filters, connection states, and
// the D3 detail panel. All writes go through the Cloud Function (lib/api.ts), optimistically, with
// Undo for 10 s and a queue while offline.
import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import { useAuth } from '../auth/auth';
import * as api from '../lib/api';
import { CONTROL_ROOM, NEEDS, STATUS_LABEL, areaLabel, canAct, people } from '../lib/format';
import { DEFAULT_FILTERS, describeFilters, isFiltered, matchesFilters, matchesSearch, type Filters } from '../lib/filters';
import { byPriority } from '../lib/priority';
import { useNow } from '../lib/time';
import type { ChatMessage, Sos, Status } from '../lib/types';
import { Button, EmptyState, Icon, Kbd, Spinner, cx } from '../ui/atoms';
import { useToasts } from '../ui/toasts';
import { ConnectionBanner, FilterBar, TopBar } from './Bars';
import { IncidentList } from './IncidentList';
import { OpsMap, type MapControl } from './OpsMap';
import { ReliefFeed } from './ReliefFeed';
import { SosPanel, type PanelActions } from './SosPanel';
import { playChime, useOpsData } from './useOpsData';
import './ops.css';

/** A status change shown before the server confirms it. */
interface Pending {
  status: Status;
  assignee?: string;
  state: 'sending' | 'waiting';
  run: () => Promise<unknown>;
}

const SOUND_KEY = 'pukaar.sound';
const RECENT_KEY = 'pukaar.recentTeams';

function readLocal<T>(key: string, fallback: T): T {
  try {
    const v = localStorage.getItem(key);
    return v == null ? fallback : (JSON.parse(v) as T);
  } catch {
    return fallback;
  }
}
function writeLocal(key: string, v: unknown) {
  try {
    localStorage.setItem(key, JSON.stringify(v));
  } catch {
    /* not remembered in private windows */
  }
}

export function OpsPage() {
  const { role, name } = useAuth();
  const acts = canAct(role);
  const { show } = useToasts();
  const now = useNow(1000);
  const [params, setParams] = useSearchParams();
  const selectedId = params.get('sos') ?? undefined;

  const [statuses, setStatuses] = useState<Set<Status>>(new Set(['new', 'attended']));
  const [filters, setFilters] = useState<Filters>(DEFAULT_FILTERS);
  const [search, setSearch] = useState('');
  const [rightTab, setRightTab] = useState<'incidents' | 'feed'>('incidents');
  const [showChat, setShowChat] = useState(false);
  const [hoveredMsg, setHoveredMsg] = useState<string>();
  const [soundOn, setSoundOn] = useState<boolean>(() => readLocal(SOUND_KEY, true));
  const [focusedId, setFocusedId] = useState<string>();
  const [attending, setAttending] = useState(false);
  const [showKeys, setShowKeys] = useState(false);
  const [pending, setPending] = useState<Map<string, Pending>>(new Map());
  const [recentTeams, setRecentTeams] = useState<string[]>(() => readLocal(RECENT_KEY, []));
  const searchRef = useRef<HTMLInputElement>(null);
  const mapControl = useRef<MapControl | null>(null);
  const soundRef = useRef(soundOn);
  soundRef.current = soundOn;
  const openRef = useRef<(id: string) => void>(() => {});

  const onArrived = useCallback(
    (list: Sos[]) => {
      if (soundRef.current) playChime();
      const s = list[0]!;
      show({
        icon: 'e911_emergency',
        text: list.length > 1 ? `${list.length} new SOS` : `New SOS · ${people(s.people || 1)}${s.flags.length ? ` · ${NEEDS.filter((n) => s.flags.includes(n.key)).map((n) => n.label).join(', ')}` : ''}`,
        detail: list.length > 1 ? undefined : `${areaLabel(s)}`,
        action: { label: 'Open', kbd: 'O', run: () => openRef.current(s.id) },
      });
      lastArrived.current = s.id;
    },
    [show],
  );
  const lastArrived = useRef<string | undefined>(undefined);

  const data = useOpsData({ showResolved: statuses.has('resolved'), onArrived });
  const nowSec = Math.floor(now / 1000);

  // Optimistic changes on top of the live data.
  const allSos = useMemo(
    () =>
      data.sos.map((s) => {
        const p = pending.get(s.id);
        if (!p) return s;
        return { ...s, status: p.status, assignee: p.status === 'new' ? '' : (p.assignee ?? s.assignee), statusTime: nowSec };
      }),
    // nowSec changes every second; the optimistic statusTime doesn't need to follow it.
    // eslint-disable-next-line react-hooks/exhaustive-deps
    [data.sos, pending],
  );

  const visibleStatus = useMemo(() => allSos.filter((s) => statuses.has(s.status)), [allSos, statuses]);
  const matchIds = useMemo(
    () => new Set(visibleStatus.filter((s) => matchesFilters(s, filters, nowSec) && matchesSearch(s, search)).map((s) => s.id)),
    // Re-check time filters once a minute, not every second.
    // eslint-disable-next-line react-hooks/exhaustive-deps
    [visibleStatus, filters, search, Math.floor(nowSec / 60)],
  );
  const list = useMemo(() => byPriority(visibleStatus.filter((s) => matchIds.has(s.id))), [visibleStatus, matchIds]);
  const selected = allSos.find((s) => s.id === selectedId);
  const blocks = useMemo(() => [...new Set(allSos.map((s) => s.area?.block).filter(Boolean) as string[])].sort(), [allSos]);

  const counts: Record<Status, number | null> = {
    new: allSos.filter((s) => s.status === 'new').length,
    attended: allSos.filter((s) => s.status === 'attended').length,
    resolved: statuses.has('resolved') ? allSos.filter((s) => s.status === 'resolved').length : data.resolvedCount,
  };
  const openCount = counts.new! + counts.attended!;
  const filtered = isFiltered(filters) || !!search.trim();

  const summary = filtered
    ? `${list.length} of ${visibleStatus.length} ${statuses.has('resolved') ? 'shown' : 'open'} match`
    : `${list.length} ${statuses.has('resolved') ? 'shown' : 'open'} · ${statuses.has('resolved') ? 'Resolved shown' : 'Resolved hidden'} · sorted by priority`;

  // ---------- open / close / navigate ----------

  const open = useCallback(
    (id: string) => {
      const s = allSos.find((x) => x.id === id);
      mapControl.current?.saveView();
      setParams((p) => {
        const n = new URLSearchParams(p);
        n.set('sos', id);
        return n;
      });
      setFocusedId(id);
      setAttending(false);
      setRightTab('incidents');
      if (s?.lat != null && s.lon != null) mapControl.current?.flyTo(s.lat, s.lon, 14);
    },
    [allSos, setParams],
  );
  openRef.current = open;

  const close = useCallback(() => {
    setParams((p) => {
      const n = new URLSearchParams(p);
      n.delete('sos');
      return n;
    });
    setAttending(false);
    mapControl.current?.restoreView();
  }, [setParams]);

  // A shared link (?sos=ID) flies to the SOS once it has loaded.
  const flewTo = useRef<string | undefined>(undefined);
  useEffect(() => {
    if (!selected || flewTo.current === selected.id) return;
    flewTo.current = selected.id;
    if (selected.lat != null && selected.lon != null) mapControl.current?.flyTo(selected.lat, selected.lon, 14);
  }, [selected]);

  const step = useCallback(
    (dir: 1 | -1) => {
      const ids = list.map((s) => s.id);
      if (!ids.length) return;
      const current = selectedId ?? focusedId;
      const i = current ? ids.indexOf(current) : -1;
      const next = ids[Math.min(ids.length - 1, Math.max(0, i + dir))]!;
      if (selectedId) open(next);
      else setFocusedId(next);
    },
    [list, selectedId, focusedId, open],
  );

  // ---------- writes ----------

  const runPending = useCallback(
    async (id: string, p: Pending, success: () => void) => {
      setPending((m) => new Map(m).set(id, { ...p, state: 'sending' }));
      try {
        await p.run();
        setPending((m) => {
          const n = new Map(m);
          n.delete(id);
          return n;
        });
        success();
      } catch (e) {
        if (e instanceof api.ApiError && e.offline) {
          // Keep it and send when the connection is back.
          setPending((m) => new Map(m).set(id, { ...p, state: 'waiting' }));
          show({ icon: 'schedule', text: 'Waiting to send', detail: "No connection. It'll be sent when the server is back." });
          return;
        }
        setPending((m) => {
          const n = new Map(m);
          n.delete(id);
          return n;
        });
        show({ tone: 'error', text: "Couldn't change the status", detail: e instanceof Error ? e.message : String(e) });
      }
    },
    [show],
  );

  // Retry queued changes when the connection comes back.
  useEffect(() => {
    if (!data.connection.live) return;
    for (const [id, p] of pending) if (p.state === 'waiting') runPending(id, p, () => show({ icon: 'wifi', text: 'Back online', detail: 'Queued changes sent.' }));
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [data.connection.live]);

  const changeStatus = useCallback(
    (sos: Sos, status: Status, opts: { assignee?: string; note?: string; undoable?: boolean } = {}) => {
      if (!acts) {
        show({ icon: 'visibility', text: 'You have view-only access', detail: 'Ask an admin for responder access to change status.' });
        return;
      }
      const prev = { status: sos.status, assignee: sos.assignee ?? '' };
      const p: Pending = { status, assignee: opts.assignee, state: 'sending', run: () => api.setStatus(sos.id, status, { assignee: opts.assignee, note: opts.note }) };
      runPending(sos.id, p, () => {
        if (opts.undoable === false) return;
        const who = sos.name ? `${sos.name}'s phone` : 'Their phone';
        const undo = () => changeStatus({ ...sos, status, assignee: opts.assignee ?? sos.assignee }, prev.status, { assignee: prev.assignee, undoable: false });
        undoRef.current = { until: Date.now() + 10_000, run: undo };
        show({
          icon: status === 'resolved' ? 'check_circle' : 'directions_run',
          text: status === 'attended' ? `Marked attended${opts.assignee ? ` · ${opts.assignee}` : ''}` : status === 'resolved' ? 'Marked resolved' : `Moved to ${STATUS_LABEL[status].toLowerCase()}`,
          detail: status === 'attended' ? `${who} will show “A rescuer is on it” when the update reaches it` : status === 'resolved' ? 'Taken off the open list.' : undefined,
          action: { label: 'Undo', kbd: 'Z', run: undo },
          ms: 10_000,
        });
      });
    },
    [acts, runPending, show],
  );
  const undoRef = useRef<{ until: number; run: () => void } | null>(null);

  const rememberTeam = (who: string) => {
    if (!who) return;
    const next = [who, ...recentTeams.filter((t) => t !== who)].slice(0, 8);
    setRecentTeams(next);
    writeLocal(RECENT_KEY, next);
  };

  const panelActions: PanelActions | null = selected
    ? {
        attend: (assignee, note) => {
          rememberTeam(assignee);
          setAttending(false);
          changeStatus(selected, 'attended', { assignee, note });
        },
        resolve: () => changeStatus(selected, 'resolved'),
        reopen: () => changeStatus(selected, 'attended'),
        backToNew: () => changeStatus(selected, 'new'),
        addNote: async (text) => {
          try {
            await api.addNote(selected.id, text);
            return true;
          } catch (e) {
            show({ tone: 'error', text: "Couldn't add the note", detail: e instanceof Error ? e.message : String(e) });
            return false;
          }
        },
        message: async (text) => {
          try {
            await api.messagePerson(selected.id, text);
            show({ icon: 'send', text: 'Message queued for delivery', detail: 'It goes out with the next status update gateways fetch.' });
            return true;
          } catch (e) {
            show({ tone: 'error', text: "Couldn't send the message", detail: e instanceof Error ? e.message : String(e) });
            return false;
          }
        },
      }
    : null;

  const sendBroadcast = async (text: string) => {
    try {
      await api.sendBroadcast(CONTROL_ROOM, text);
      show({ icon: 'campaign', text: 'Broadcast sent', detail: 'Phones get it as gateways sync. Reach counts up as they report back.' });
      return true;
    } catch (e) {
      show({ tone: 'error', text: "Couldn't send the broadcast", detail: e instanceof Error ? e.message : String(e) });
      return false;
    }
  };

  const locateMessage = (m: ChatMessage) => {
    if (m.lat == null || m.lon == null) return;
    setShowChat(true);
    setHoveredMsg(m.id);
    mapControl.current?.flyTo(m.lat, m.lon, 14);
  };

  // ---------- keyboard ----------

  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      const el = e.target as HTMLElement;
      const typing = el.isContentEditable || ['INPUT', 'TEXTAREA', 'SELECT'].includes(el.tagName);
      if (e.key === 'Escape') {
        if (showKeys) setShowKeys(false);
        else if (attending) setAttending(false);
        else if (selectedId) close();
        return;
      }
      if (typing || e.metaKey || e.ctrlKey || e.altKey) return;
      const viewOnly = () => show({ icon: 'visibility', text: 'You have view-only access', detail: 'Ask an admin for responder access.' });
      switch (e.key) {
        case 'j':
        case 'J':
          step(1);
          break;
        case 'k':
        case 'K':
          step(-1);
          break;
        case 'Enter':
          if (focusedId && !selectedId) open(focusedId);
          break;
        case 'a':
        case 'A':
          if (!selected) return;
          if (!acts) return viewOnly();
          if (selected.status !== 'resolved') setAttending(true);
          break;
        case 'r':
        case 'R':
          if (!selected) return;
          if (!acts) return viewOnly();
          if (selected.status !== 'resolved') changeStatus(selected, 'resolved');
          break;
        case 'z':
        case 'Z':
          if (!acts) return viewOnly();
          if (undoRef.current && undoRef.current.until > Date.now()) {
            undoRef.current.run();
            undoRef.current = null;
          }
          break;
        case 'o':
        case 'O':
          if (lastArrived.current) open(lastArrived.current);
          break;
        case '/':
          e.preventDefault();
          searchRef.current?.focus();
          break;
        case 'f':
        case 'F':
          setRightTab((t) => (t === 'feed' ? 'incidents' : 'feed'));
          break;
        case 'm':
        case 'M':
          setSoundOn((s) => {
            writeLocal(SOUND_KEY, !s);
            return !s;
          });
          break;
        case '?':
          setShowKeys((s) => !s);
          break;
        default:
          return;
      }
      e.preventDefault();
    };
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  }, [step, open, close, selected, selectedId, focusedId, acts, attending, showKeys, changeStatus, show]);

  const toggleStatus = (s: Status) =>
    setStatuses((cur) => {
      const n = new Set(cur);
      if (n.has(s)) n.delete(s);
      else n.add(s);
      return n;
    });

  const stale = !data.connection.live && !!data.connection.lostAt;

  return (
    <div className="flex h-full flex-col">
      <TopBar
        counts={counts}
        statuses={statuses}
        onToggleStatus={toggleStatus}
        search={search}
        onSearch={setSearch}
        searchRef={searchRef}
        connection={data.connection}
        now={now}
        soundOn={soundOn}
        onToggleSound={() =>
          setSoundOn((s) => {
            writeLocal(SOUND_KEY, !s);
            return !s;
          })
        }
      />
      <FilterBar
        filters={filters}
        onChange={setFilters}
        blocks={blocks}
        summary={summary}
        showChat={showChat}
        onToggleChat={() => setShowChat((c) => !c)}
        onClear={() => {
          setFilters(DEFAULT_FILTERS);
          setSearch('');
        }}
        canClear={filtered}
      />
      <ConnectionBanner connection={data.connection} onRetry={data.retry} />

      <div className="relative flex min-h-0 flex-1">
        <div className="relative min-w-0 flex-1">
          <OpsMap
            sos={allSos.filter((s) => statuses.has(s.status))}
            matchIds={matchIds}
            selectedId={selectedId}
            freshIds={data.freshIds}
            dimIncidents={rightTab === 'feed'}
            messages={data.messages}
            showChat={showChat || rightTab === 'feed'}
            hoveredMessageId={hoveredMsg}
            onSelect={open}
            onMessageClick={() => setRightTab('feed')}
            control={mapControl}
          />
          {stale && data.connection.lostAt && (
            <span className="absolute top-14 left-1/2 flex -translate-x-1/2 items-center gap-1.5 rounded-full bg-warning-container px-3 py-1 text-caption font-semibold text-on-warning-container">
              <Icon name="history" size={16} />
              Map and list as of {new Date(data.connection.lostAt).toLocaleTimeString('en-IN', { hour: 'numeric', minute: '2-digit', timeZone: 'Asia/Kolkata' }).toLowerCase()}
            </span>
          )}
          {list.length >= 100 && rightTab === 'incidents' && (
            <span className="absolute top-14 left-1/2 flex -translate-x-1/2 items-center gap-1.5 rounded-full bg-surface-container-high px-3 py-1 text-caption text-on-surface-variant">
              <Icon name="zoom_in" size={16} />
              Zoom in or click a cluster to see single SOS. Rings show the mix of New, Attended and Resolved.
            </span>
          )}
        </div>

        <aside className="flex w-right-col shrink-0 flex-col border-l border-outline-variant bg-surface-container-low" aria-label="Incidents and relief feed">
          <div className="flex h-12 shrink-0 border-b border-outline-variant" role="tablist">
            <Tab active={rightTab === 'incidents'} onClick={() => setRightTab('incidents')} label="Incidents" badge={String(list.length)} />
            <Tab active={rightTab === 'feed'} onClick={() => setRightTab('feed')} label="Relief feed" badge={data.messages.length ? String(data.messages.length) : undefined} />
          </div>
          {/* Every state fills the column so the shortcut footer stays at the bottom. */}
          <div className="flex min-h-0 flex-1 flex-col">
          {rightTab === 'feed' ? (
            <ReliefFeed messages={data.messages} broadcasts={data.broadcasts} role={role} onLocate={locateMessage} onHover={setHoveredMsg} onSend={sendBroadcast} />
          ) : data.error ? (
            <EmptyState icon="error" title="Couldn't load SOS">
              {data.error}
            </EmptyState>
          ) : data.loading ? (
            <Spinner label="Loading SOS…" />
          ) : list.length === 0 ? (
            filtered ? (
              <EmptyState
                icon="filter_alt_off"
                title="No incidents match these filters"
                action={
                  <Button
                    variant="tonal"
                    icon="filter_alt_off"
                    onClick={() => {
                      setFilters(DEFAULT_FILTERS);
                      setSearch('');
                    }}
                  >
                    Clear filters
                  </Button>
                }
              >
                {describeFilters(filters, search)}. {visibleStatus.length} SOS are outside these filters.
              </EmptyState>
            ) : (
              <EmptyState icon="notifications_active" title={openCount === 0 ? 'No SOS yet' : 'Nothing to show'}>
                {openCount === 0
                  ? 'New SOS show up here and on the map within a second of reaching the server. A chime plays when one arrives.'
                  : 'Turn on New or Attended in the top bar to see SOS.'}
                <span className="mt-2 flex items-center justify-center gap-1 font-semibold">
                  <Icon name={soundOn ? 'volume_up' : 'volume_off'} size={16} />
                  {soundOn ? 'Sound is on' : 'Sound is off'}
                </span>
              </EmptyState>
            )
          ) : (
            <IncidentList
              list={list}
              selectedId={selectedId}
              focusedId={focusedId}
              freshIds={data.freshIds}
              stale={stale}
              now={now}
              pending={new Map([...pending].map(([id, p]) => [id, p.state]))}
              onOpen={open}
            />
          )}
          </div>
          <footer className="flex h-list-footer shrink-0 items-center gap-3 border-t border-outline-variant px-4 text-caption text-on-surface-variant">
            <span className="flex items-center gap-1">
              <Kbd>J</Kbd>
              <Kbd>K</Kbd> Move
            </span>
            <span className="flex items-center gap-1">
              <Kbd>↵</Kbd> Open
            </span>
            <span className="flex items-center gap-1">
              <Kbd>A</Kbd> Attend
            </span>
            <span className="flex items-center gap-1">
              <Kbd>R</Kbd> Resolve
            </span>
            <button type="button" className="ml-auto flex items-center gap-1 hover:text-on-surface" onClick={() => setShowKeys(true)}>
              <Kbd>?</Kbd> All
            </button>
          </footer>
        </aside>

        {selected && panelActions && (
          <SosPanel
            sos={selected}
            role={role}
            now={now}
            attending={attending}
            setAttending={setAttending}
            recentAssignees={recentTeams}
            pending={pending.get(selected.id)?.state}
            actions={panelActions}
            onClose={close}
            onPrev={list.findIndex((s) => s.id === selected.id) > 0 ? () => step(-1) : undefined}
            onNext={list.findIndex((s) => s.id === selected.id) < list.length - 1 ? () => step(1) : undefined}
          />
        )}
      </div>
      {showKeys && <ShortcutSheet onClose={() => setShowKeys(false)} />}
      <span className="sr-only" aria-live="polite">
        {name}
      </span>
    </div>
  );
}

function Tab({ active, onClick, label, badge }: { active: boolean; onClick: () => void; label: string; badge?: string }) {
  return (
    <button
      type="button"
      role="tab"
      aria-selected={active}
      onClick={onClick}
      className={cx(
        'flex flex-1 items-center justify-center gap-2 border-b-2 text-list font-semibold',
        active ? 'border-primary text-on-surface' : 'border-transparent text-on-surface-variant hover:text-on-surface',
      )}
    >
      {label}
      {badge && <span className="rounded-full bg-surface-container-highest px-2 text-caption">{badge}</span>}
    </button>
  );
}

const SHORTCUTS: [string, string][] = [
  ['J / K', 'Next / previous SOS'],
  ['Enter', 'Open the focused SOS'],
  ['Esc', 'Close the panel, back to the previous view'],
  ['A', 'Mark attended (who is going)'],
  ['R', 'Mark resolved'],
  ['Z', 'Undo the last change (10 s)'],
  ['O', 'Open the newest SOS'],
  ['/', 'Search'],
  ['F', 'Relief feed'],
  ['M', 'Sound on or off'],
  ['?', 'This list'],
];

function ShortcutSheet({ onClose }: { onClose: () => void }) {
  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-scrim/50" onClick={onClose}>
      <div role="dialog" aria-label="Keyboard shortcuts" className="w-[420px] rounded-dialog bg-surface-container-high p-6" onClick={(e) => e.stopPropagation()}>
        <h2 className="mb-4 text-dialog-title font-semibold">Keyboard shortcuts</h2>
        <dl className="grid grid-cols-[96px_1fr] gap-x-4 gap-y-2 text-list">
          {SHORTCUTS.map(([k, v]) => (
            <div key={k} className="contents">
              <dt>
                <Kbd>{k}</Kbd>
              </dt>
              <dd>{v}</dd>
            </div>
          ))}
        </dl>
        <p className="mt-4 text-caption text-on-surface-variant">Shortcuts are off while typing in a field, except Esc and Ctrl + Enter.</p>
        <div className="mt-4 flex justify-end">
          <Button variant="text" onClick={onClose}>
            Close
          </Button>
        </div>
      </div>
    </div>
  );
}
