// D2 incident list: virtualised (150+ SOS), grouped by status, sorted by priority, with a rank gutter.
// New arrivals are inserted in place without moving the user's scroll or focus.
import { useVirtualizer } from '@tanstack/react-virtual';
import { useEffect, useMemo, useRef } from 'react';
import { STATUS_LABEL, areaLabel, people } from '../lib/format';
import type { Sos, Status } from '../lib/types';
import { Battery, Icon, Needs, StatusChip, TimeAgo, ViaTag, cx } from '../ui/atoms';

type Row = { kind: 'header'; status: Status; count: number } | { kind: 'sos'; sos: Sos; rank: number };

export interface RowState {
  /** waiting to send (offline) or sending */
  pending?: 'sending' | 'waiting';
}

export function IncidentList({
  list,
  selectedId,
  focusedId,
  freshIds,
  stale,
  now,
  pending,
  onOpen,
}: {
  list: Sos[];
  selectedId?: string;
  focusedId?: string;
  freshIds: Map<string, number>;
  stale: boolean;
  now: number;
  pending: Map<string, RowState['pending']>;
  onOpen: (id: string) => void;
}) {
  const rows = useMemo<Row[]>(() => {
    const out: Row[] = [];
    let rank = 0;
    for (const status of ['new', 'attended', 'resolved'] as Status[]) {
      const group = list.filter((s) => s.status === status);
      if (!group.length) continue;
      out.push({ kind: 'header', status, count: group.length });
      group.forEach((sos) => out.push({ kind: 'sos', sos, rank: ++rank }));
    }
    return out;
  }, [list]);

  const scroller = useRef<HTMLDivElement>(null);
  const virtual = useVirtualizer({
    count: rows.length,
    getScrollElement: () => scroller.current,
    estimateSize: (i) => (rows[i]!.kind === 'header' ? 36 : 96),
    getItemKey: (i) => {
      const r = rows[i]!;
      return r.kind === 'header' ? `h-${r.status}` : r.sos.id;
    },
    overscan: 8,
  });

  // Keep the keyboard-focused or selected row in view.
  const target = focusedId ?? selectedId;
  useEffect(() => {
    if (!target) return;
    const i = rows.findIndex((r) => r.kind === 'sos' && r.sos.id === target);
    if (i >= 0) virtual.scrollToIndex(i, { align: 'auto' });
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [target]);

  return (
    <div ref={scroller} className="min-h-0 flex-1 overflow-y-auto" role="listbox" aria-label="Incidents, sorted by priority">
      <div style={{ height: virtual.getTotalSize(), position: 'relative' }}>
        {virtual.getVirtualItems().map((item) => {
          const row = rows[item.index]!;
          return (
            <div
              key={item.key}
              data-index={item.index}
              ref={virtual.measureElement}
              style={{ position: 'absolute', top: 0, left: 0, right: 0, transform: `translateY(${item.start}px)` }}
            >
              {row.kind === 'header' ? (
                <div className="flex h-9 items-center bg-surface-container px-4 text-list font-semibold text-on-surface-variant">
                  {STATUS_LABEL[row.status]} · {row.count}
                </div>
              ) : (
                <IncidentRow
                  sos={row.sos}
                  rank={row.rank}
                  selected={row.sos.id === selectedId}
                  focused={row.sos.id === focusedId}
                  fresh={freshIds.has(row.sos.id)}
                  stale={stale}
                  now={now}
                  pending={pending.get(row.sos.id)}
                  onOpen={onOpen}
                />
              )}
            </div>
          );
        })}
      </div>
    </div>
  );
}

function IncidentRow({
  sos,
  rank,
  selected,
  focused,
  fresh,
  stale,
  now,
  pending,
  onOpen,
}: {
  sos: Sos;
  rank: number;
  selected: boolean;
  focused: boolean;
  fresh: boolean;
  stale: boolean;
  now: number;
  pending?: RowState['pending'];
  onOpen: (id: string) => void;
}) {
  return (
    <button
      type="button"
      role="option"
      aria-selected={selected}
      onClick={() => onOpen(sos.id)}
      className={cx(
        'relative flex w-full gap-2 border-b border-outline-variant py-3 pr-4 pl-1 text-left transition-colors',
        selected ? 'bg-surface-container-high shadow-[inset_3px_0_0_var(--pk-on-surface)]' : 'hover:bg-surface-container',
        focused && !selected && 'outline-2 -outline-offset-2 outline-primary',
        fresh && !selected && 'pk-row-fresh',
        stale && 'opacity-60',
      )}
    >
      <span className="w-5 shrink-0 pt-0.5 text-right text-caption text-on-surface-variant">{rank}</span>
      <span className="flex min-w-0 flex-1 flex-col gap-1">
        <span className="flex items-center gap-2">
          <StatusChip status={sos.status} safe={!!sos.safeAt} />
          <TimeAgo time={sos.time || sos.createdAt} now={now} className={cx('text-list', fresh ? 'font-semibold text-sos' : 'text-on-surface-variant')} />
          {pending && (
            <span className="flex items-center gap-1 text-caption text-warning">
              <Icon name="schedule" size={14} />
              {pending === 'waiting' ? 'Waiting to send' : 'Sending…'}
            </span>
          )}
          <span className="ml-auto">
            <Battery value={sos.battery} />
          </span>
        </span>
        <span className="flex flex-wrap items-center gap-x-3 gap-y-1">
          <span className="text-card-title font-semibold">{people(sos.people || 1)}</span>
          <Needs flags={sos.flags} />
        </span>
        <span className="flex items-center gap-2 text-list text-on-surface-variant">
          <Icon name="location_on" size={16} />
          <span className="min-w-0 truncate">{areaLabel(sos)}</span>
          <span className="ml-auto shrink-0">
            {sos.status === 'attended' && sos.assignee ? (
              <span className="inline-flex items-center gap-1 text-on-surface-variant">
                <Icon name="groups" size={16} />
                {sos.assignee}
              </span>
            ) : (
              <ViaTag sos={sos} />
            )}
          </span>
        </span>
      </span>
    </button>
  );
}
