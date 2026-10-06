// D4 Disaster Relief feed: the public chat as received, newest first, with official broadcasts
// marked and verified. Location chips fly the map there; hovering highlights the marker. Responders
// and admins get a composer with a byte counter and a confirmation step naming who it reaches.
import { useMemo, useState } from 'react';
import { CONTROL_ROOM, DISTRICT, RADIO_LIMIT_BYTES, byteLength, canAct } from '../lib/format';
import type { Broadcast, ChatMessage, Role } from '../lib/types';
import { Button, Clock, EmptyState, Icon, Kbd, cx } from '../ui/atoms';

type Item = { kind: 'chat'; msg: ChatMessage; time: number } | { kind: 'official'; b: Broadcast; time: number };

export function ReliefFeed({
  messages,
  broadcasts,
  role,
  onLocate,
  onHover,
  onSend,
}: {
  messages: ChatMessage[];
  broadcasts: Broadcast[];
  role: Role | null;
  onLocate: (m: ChatMessage) => void;
  onHover: (id?: string) => void;
  onSend: (text: string) => Promise<boolean>;
}) {
  const items = useMemo<Item[]>(
    () =>
      [
        ...messages.map((msg) => ({ kind: 'chat' as const, msg, time: msg.time ?? 0 })),
        ...broadcasts.map((b) => ({ kind: 'official' as const, b, time: b.time })),
      ].sort((a, b) => b.time - a.time),
    [messages, broadcasts],
  );
  const reach = Math.max(0, ...broadcasts.map((b) => b.reach ?? 0));

  return (
    <div className="flex min-h-0 flex-1 flex-col">
      <div className="flex items-center justify-between border-b border-outline-variant px-4 py-2 text-caption text-on-surface-variant">
        <span className="flex items-center gap-1.5">
          <Icon name="public" size={16} />
          Public chat as received · newest first
        </span>
        <span>Rescuers can read this</span>
      </div>
      <div className="min-h-0 flex-1 overflow-y-auto">
        {items.length === 0 ? (
          <EmptyState icon="forum" title="No messages yet">
            Messages from the Disaster Relief chat show up here as gateway phones upload them.
          </EmptyState>
        ) : (
          <ul>
            {items.map((it) =>
              it.kind === 'official' ? (
                <li key={`b-${it.b.id}`} className="border-b border-outline-variant bg-primary-container p-4 text-on-primary-container">
                  <p className="flex items-center gap-1.5 text-list font-semibold">
                    <Icon name="verified" size={18} fill />
                    Official · {it.b.from}
                    <Clock time={it.b.time} className="ml-auto text-caption font-normal opacity-80" />
                  </p>
                  <p className="mt-1 text-body">{it.b.text}</p>
                  {(it.b.sentBy?.by || it.b.reach != null) && (
                    <p className="mt-1 text-caption opacity-80">
                      {it.b.sentBy?.by && `Sent by ${it.b.sentBy.by}`}
                      {it.b.sentBy?.by && it.b.reach != null && ' · '}
                      {it.b.reach != null && (it.b.reach > 0 ? `reached ~${it.b.reach.toLocaleString('en-IN')} phones so far` : 'no phones have reported it yet')}
                    </p>
                  )}
                </li>
              ) : (
                <li
                  key={`m-${it.msg.id}`}
                  className="border-b border-outline-variant p-4 hover:bg-surface-container"
                  onMouseEnter={() => it.msg.lat != null && onHover(it.msg.id)}
                  onMouseLeave={() => onHover(undefined)}
                >
                  <p className="flex items-center gap-2 text-list">
                    <span className="font-semibold">{it.msg.sender || 'Someone'}</span>
                    {it.msg.time != null && <Clock time={it.msg.time} className="ml-auto text-caption text-on-surface-variant" />}
                  </p>
                  <p className="mt-0.5 text-body">{it.msg.text}</p>
                  {it.msg.lat != null && it.msg.lon != null && (
                    <button
                      type="button"
                      onClick={() => onLocate(it.msg)}
                      className="mt-1.5 inline-flex items-center gap-1 rounded-chip bg-surface-container-high px-2 py-0.5 text-caption font-semibold text-primary hover:bg-surface-container-highest"
                    >
                      <Icon name="location_on" size={14} />
                      Show on map
                      <Icon name="arrow_outward" size={14} />
                    </button>
                  )}
                </li>
              ),
            )}
          </ul>
        )}
      </div>
      {canAct(role) ? (
        <BroadcastComposer reach={reach} onSend={onSend} />
      ) : (
        <p className="flex items-start gap-2 border-t border-outline-variant p-4 text-list text-on-surface-variant">
          <Icon name="visibility" size={18} />
          You have view-only access. Ask an admin for responder access to send official broadcasts.
        </p>
      )}
    </div>
  );
}

function BroadcastComposer({ reach, onSend }: { reach: number; onSend: (text: string) => Promise<boolean> }) {
  const [text, setText] = useState('');
  const [reviewing, setReviewing] = useState(false);
  const [busy, setBusy] = useState(false);
  const bytes = byteLength(text.trim());
  const over = bytes > RADIO_LIMIT_BYTES;
  const canReview = !!text.trim() && !over;

  async function send() {
    setBusy(true);
    const ok = await onSend(text.trim());
    setBusy(false);
    if (ok) {
      setText('');
      setReviewing(false);
    }
  }

  if (reviewing)
    return (
      <div
        className="flex flex-col gap-3 border-t border-outline-variant bg-surface-container-high p-4"
        onKeyDown={(e) => {
          if (e.key === 'Escape') {
            e.stopPropagation();
            setReviewing(false);
          }
        }}
      >
        <p className="flex items-center gap-2 text-card-title font-semibold">
          <Icon name="campaign" size={20} />
          This goes to everyone in the area
        </p>
        <p className="text-list text-on-surface-variant">
          {reach > 0 ? `About ${reach.toLocaleString('en-IN')} phones` : 'Every Pukaar phone'} across {DISTRICT} will see it in the Disaster Relief chat,
          marked Official and signed as {CONTROL_ROOM}. It can't be unsent.
        </p>
        <blockquote className="rounded-card bg-surface-container-highest p-3 text-body">{text.trim()}</blockquote>
        <div className="flex gap-2">
          <Button autoFocus icon="campaign" onClick={send} disabled={busy}>
            {busy ? 'Sending…' : 'Send to everyone'}
          </Button>
          <Button variant="text" onClick={() => setReviewing(false)}>
            Back to edit
          </Button>
        </div>
      </div>
    );

  return (
    <div className="flex flex-col gap-2 border-t border-outline-variant p-4">
      <p className="flex items-center gap-1.5 text-list font-semibold">
        <Icon name="campaign" size={18} />
        Official broadcast
      </p>
      <textarea
        value={text}
        onChange={(e) => setText(e.target.value)}
        onKeyDown={(e) => {
          if (e.key === 'Enter' && (e.metaKey || e.ctrlKey) && canReview) {
            e.preventDefault();
            setReviewing(true);
          }
        }}
        rows={3}
        placeholder="For example: Relief camp open at Biraul panchayat bhawan with water, food and medicine."
        aria-label="Official broadcast"
        className="resize-none rounded-chip border border-outline bg-surface-container-lowest px-3 py-2 text-list outline-none focus:border-primary focus:ring-1 focus:ring-primary"
      />
      <div className="flex items-center gap-2">
        <span className={cx('text-caption', over ? 'font-semibold text-error' : 'text-on-surface-variant')}>
          {bytes} / {RADIO_LIMIT_BYTES} bytes · radio limit
        </span>
        <Button className="ml-auto h-9" disabled={!canReview} onClick={() => setReviewing(true)}>
          Review broadcast
        </Button>
        <Kbd>Ctrl ↵</Kbd>
      </div>
    </div>
  );
}
