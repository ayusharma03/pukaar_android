// D3 SOS detail: a 440 px side panel over the list. Status block (new, attending form, attended,
// resolved, marked safe, viewer), what they sent, person, location, how it arrived, family texted,
// message to the person, team notes, history. Mesh-only SOS say clearly that contact details
// didn't arrive, never show empty fields.
import { useEffect, useRef, useState, type ReactNode } from 'react';
import {
  RADIO_LIMIT_BYTES,
  areaLabel,
  byteLength,
  canAct,
  coords,
  duration,
  mapsLink,
  people,
  phoneLabel,
  viaLabel,
} from '../lib/format';
import { clockTime } from '../lib/time';
import type { HistoryEntry, Role, Sos } from '../lib/types';
import { Battery, Button, Clock, Icon, IconButton, Kbd, MeshIcon, Needs, RadioIcon, StatusChip, cx } from '../ui/atoms';
import { MiniMap } from './OpsMap';

export interface PanelActions {
  attend: (assignee: string, note: string) => void;
  resolve: () => void;
  reopen: () => void;
  backToNew: () => void;
  addNote: (text: string) => Promise<boolean>;
  message: (text: string) => Promise<boolean>;
}

export function SosPanel({
  sos,
  role,
  now,
  attending,
  setAttending,
  recentAssignees,
  pending,
  actions,
  onClose,
  onPrev,
  onNext,
}: {
  sos: Sos;
  role: Role | null;
  now: number;
  attending: boolean;
  setAttending: (on: boolean) => void;
  recentAssignees: string[];
  pending?: 'sending' | 'waiting';
  actions: PanelActions;
  onClose: () => void;
  onPrev?: () => void;
  onNext?: () => void;
}) {
  const scroller = useRef<HTMLDivElement>(null);
  useEffect(() => {
    scroller.current?.scrollTo({ top: 0 });
  }, [sos.id]);
  const acts = canAct(role);
  const firstName = sos.name.split(' ')[0] || 'them';

  return (
    <aside
      aria-label={`SOS ${sos.id}`}
      className="absolute top-0 right-0 bottom-0 z-20 flex w-detail-panel flex-col border-l border-outline-variant bg-surface-container motion-safe:animate-[panel-in_250ms_cubic-bezier(0.2,0,0,1)]"
    >
      <header className="flex h-14 shrink-0 items-center gap-2 border-b border-outline-variant px-2">
        <IconButton icon="close" label="Close (Esc)" onClick={onClose} />
        <div className="min-w-0 flex-1 leading-tight">
          <h2 className="text-panel-title font-semibold">SOS {sos.id}</h2>
          <p className="truncate text-caption text-on-surface-variant">{areaLabel(sos)}</p>
        </div>
        <IconButton icon="keyboard_arrow_up" label="Previous SOS (K)" onClick={onPrev} disabled={!onPrev} />
        <IconButton icon="keyboard_arrow_down" label="Next SOS (J)" onClick={onNext} disabled={!onNext} />
      </header>

      <div ref={scroller} className="min-h-0 flex-1 overflow-y-auto">
        <StatusBlock
          sos={sos}
          role={role}
          now={now}
          attending={attending}
          setAttending={setAttending}
          recentAssignees={recentAssignees}
          pending={pending}
          actions={actions}
        />

        <Section title="What they sent">
          {sos.message ? <p className="text-body">“{sos.message}”</p> : <p className="text-list text-on-surface-variant">No message, just the SOS.</p>}
          <div className="flex flex-wrap items-center gap-x-3 gap-y-1">
            <span className="text-card-title font-semibold">{people(sos.people || 1)}</span>
            <Needs flags={sos.flags} />
          </div>
          <Facts>
            <Fact label="Battery">
              <Battery value={sos.battery} />
            </Fact>
            {sos.time ? (
              <Fact label="Sent">
                <Clock time={sos.time} /> on their phone
              </Fact>
            ) : null}
            <Fact label="Received">
              <Clock time={sos.receivedAt ?? sos.createdAt} />
              {sos.time ? ` · ${transit(sos)}` : ''}
            </Fact>
          </Facts>
        </Section>

        <Section title="Person">
          <PersonSection sos={sos} />
        </Section>

        <Section title="Location">
          <LocationSection sos={sos} now={now} />
        </Section>

        <Section title="How it arrived">
          <ArrivalSection sos={sos} />
        </Section>

        <Section title="Family texted">
          <FamilySection sos={sos} />
        </Section>

        <Section title="Send message to this person">
          <Composer
            key={`m-${sos.id}`}
            placeholder={`Message ${firstName}`}
            sendLabel="Send"
            icon="send"
            limitBytes={RADIO_LIMIT_BYTES}
            disabledReason={acts ? undefined : 'You have view-only access. Ask an admin for responder access to send messages.'}
            onSend={actions.message}
          />
          <p className="text-caption text-on-surface-variant">
            Goes back through gateways and the mesh. Max {RADIO_LIMIT_BYTES} bytes. It may take minutes to arrive.
          </p>
          {(sos.outbox ?? [])
            .slice()
            .reverse()
            .map((m) => (
              <div key={m.id} className="rounded-card bg-surface-container-high p-3">
                <p className="text-list">{m.text}</p>
                <p className="text-caption text-on-surface-variant">
                  {m.from} · <Clock time={m.time} />
                </p>
              </div>
            ))}
        </Section>

        <Section title="Team notes">
          {(sos.notes ?? []).map((n, i) => (
            <div key={i} className="rounded-card bg-surface-container-high p-3">
              <p className="text-list">{n.text}</p>
              <p className="text-caption text-on-surface-variant">
                {n.by || 'Control room'} · <Clock time={n.at} />
              </p>
            </div>
          ))}
          <Composer
            key={`n-${sos.id}`}
            placeholder="Add a note for the team"
            sendLabel="Add note"
            icon="note_add"
            disabledReason={acts ? undefined : 'Notes are read-only for viewers'}
            onSend={actions.addNote}
          />
        </Section>

        <Section title="History" last>
          <Timeline sos={sos} />
        </Section>
      </div>
    </aside>
  );
}

function transit(sos: Sos) {
  const seconds = (sos.receivedAt ?? sos.createdAt) - sos.time;
  if (seconds <= 5) return 'instantly';
  return `${duration(seconds)} in transit`;
}

// ---------- Status block (six variants, handoff sheet 1z) ----------

function StatusBlock({
  sos,
  role,
  now,
  attending,
  setAttending,
  recentAssignees,
  pending,
  actions,
}: {
  sos: Sos;
  role: Role | null;
  now: number;
  attending: boolean;
  setAttending: (on: boolean) => void;
  recentAssignees: string[];
  pending?: 'sending' | 'waiting';
  actions: PanelActions;
}) {
  const acts = canAct(role);
  const lastChange = [...sos.history].reverse().find((h) => h.status === sos.status);
  const waiting = Math.max(0, now / 1000 - (sos.time || sos.createdAt));

  return (
    <section className="flex flex-col gap-3 border-b border-outline-variant p-4">
      <div className="flex items-center gap-2">
        <StatusChip status={sos.status} large safe={!!sos.safeAt} />
        {pending && (
          <span className="flex items-center gap-1 text-caption text-warning">
            <Icon name="schedule" size={14} />
            {pending === 'waiting' ? 'Waiting to send: no connection' : 'Sending…'}
          </span>
        )}
      </div>

      {sos.safeAt && sos.status !== 'resolved' && (
        <div className="flex gap-3 rounded-card bg-confirmed-container p-3 text-on-confirmed-container">
          <Icon name="verified_user" size={20} fill />
          <div>
            <p className="text-list font-semibold">Marked safe by the person at {clockTime(sos.safeAt)}.</p>
            <p className="text-list">They tapped “I'm safe now” on their phone. Check with the team, then resolve.</p>
          </div>
        </div>
      )}

      {attending && acts ? (
        <AttendForm recent={recentAssignees} onConfirm={actions.attend} onCancel={() => setAttending(false)} name={sos.name} />
      ) : (
        <>
          {sos.status === 'new' && <p className="text-list text-on-surface-variant">No one assigned · waiting {duration(waiting)}</p>}
          {sos.status === 'attended' && (
            <div className="flex flex-col gap-1">
              <p className="flex items-center gap-2 text-card-title font-semibold">
                <Icon name="groups" size={20} />
                {sos.assignee || sos.by || 'Team not named'}
              </p>
              {lastChange && (
                <p className="text-caption text-on-surface-variant">
                  Assigned <Clock time={lastChange.time} />
                  {lastChange.by && ` by ${lastChange.by}`}
                  {acts && (
                    <>
                      {' · '}
                      <button type="button" className="font-semibold text-primary hover:underline" onClick={() => setAttending(true)}>
                        Change
                      </button>
                    </>
                  )}
                </p>
              )}
            </div>
          )}
          {sos.status === 'resolved' && lastChange && (
            <p className="text-list text-on-surface-variant">
              Marked resolved{lastChange.by && ` by ${lastChange.by}`} · <Clock time={lastChange.time} />. Taken off the open list.
            </p>
          )}

          <div className="flex flex-wrap gap-2">
            {sos.status === 'new' && (
              <>
                <Button icon="directions_run" kbd="A" disabled={!acts} onClick={() => setAttending(true)} variant={sos.safeAt ? 'tonal' : 'filled'}>
                  Mark attended
                </Button>
                <Button icon="check_circle" kbd="R" disabled={!acts} onClick={actions.resolve} variant={sos.safeAt ? 'filled' : 'outlined'}>
                  Mark resolved
                </Button>
              </>
            )}
            {sos.status === 'attended' && (
              <>
                <Button icon="check_circle" kbd="R" disabled={!acts} onClick={actions.resolve}>
                  Mark resolved
                </Button>
                <Button icon="undo" variant="text" disabled={!acts} onClick={actions.backToNew}>
                  Back to new
                </Button>
              </>
            )}
            {sos.status === 'resolved' && (
              <Button icon="restart_alt" variant="outlined" disabled={!acts} onClick={actions.reopen}>
                Reopen
              </Button>
            )}
          </div>
        </>
      )}

      {!acts && (
        <p className="flex items-start gap-2 rounded-card bg-surface-container-high p-3 text-list text-on-surface-variant">
          <Icon name="visibility" size={18} />
          You have view-only access. Ask an admin for responder access to change status or send messages.
        </p>
      )}
    </section>
  );
}

function AttendForm({ recent, onConfirm, onCancel, name }: { recent: string[]; onConfirm: (who: string, note: string) => void; onCancel: () => void; name: string }) {
  const [who, setWho] = useState('');
  const [note, setNote] = useState('');
  const whoRef = useRef<HTMLInputElement>(null);
  useEffect(() => {
    whoRef.current?.focus();
  }, []);
  const submit = () => onConfirm(who.trim(), note.trim());
  return (
    <form
      className="flex flex-col gap-3 rounded-card bg-surface-container-high p-3"
      onSubmit={(e) => {
        e.preventDefault();
        submit();
      }}
      onKeyDown={(e) => {
        if (e.key === 'Escape') {
          e.stopPropagation();
          onCancel();
        }
      }}
    >
      <p className="text-card-title font-semibold">Marking as attended</p>
      <label className="flex flex-col gap-1">
        <span className="text-list font-semibold text-on-surface-variant">Who is going</span>
        <input
          ref={whoRef}
          value={who}
          onChange={(e) => setWho(e.target.value)}
          list="pk-recent-teams"
          placeholder="For example NDRF team 3"
          className="h-10 rounded-chip border border-outline bg-surface-container-lowest px-3 text-body outline-none focus:border-primary focus:ring-1 focus:ring-primary"
        />
        <datalist id="pk-recent-teams">
          {recent.map((r) => (
            <option key={r} value={r} />
          ))}
        </datalist>
      </label>
      {recent.length > 0 && (
        <div className="flex flex-wrap items-center gap-1.5 text-caption text-on-surface-variant">
          Recent:
          {recent.slice(0, 4).map((r) => (
            <button
              key={r}
              type="button"
              onClick={() => setWho(r)}
              className="rounded-chip border border-outline-variant px-2 py-0.5 text-list text-on-surface hover:bg-surface-container-highest"
            >
              {r}
            </button>
          ))}
        </div>
      )}
      <label className="flex flex-col gap-1">
        <span className="text-list font-semibold text-on-surface-variant">Note · optional</span>
        <textarea
          value={note}
          onChange={(e) => setNote(e.target.value)}
          rows={2}
          onKeyDown={(e) => {
            if (e.key === 'Enter' && (e.metaKey || e.ctrlKey)) {
              e.preventDefault();
              submit();
            }
          }}
          className="resize-none rounded-chip border border-outline bg-surface-container-lowest px-3 py-2 text-list outline-none focus:border-primary focus:ring-1 focus:ring-primary"
        />
      </label>
      <div className="flex gap-2">
        <Button type="submit" icon="directions_run" kbd="↵">
          Confirm attended
        </Button>
        <Button variant="text" onClick={onCancel} kbd="Esc">
          Cancel
        </Button>
      </div>
      <p className="text-caption text-on-surface-variant">
        {name ? `${name}'s phone` : 'Their phone'} will show “A rescuer is on it” once the signed update reaches it.
      </p>
    </form>
  );
}

// ---------- Sections ----------

function Section({ title, children, last }: { title: string; children: ReactNode; last?: boolean }) {
  return (
    <section className={cx('flex flex-col gap-3 p-4', !last && 'border-b border-outline-variant')}>
      <h3 className="text-card-title font-semibold">{title}</h3>
      {children}
    </section>
  );
}

function Facts({ children }: { children: ReactNode }) {
  return <dl className="grid grid-cols-[120px_1fr] gap-x-3 gap-y-2 text-list">{children}</dl>;
}

function Fact({ label, children }: { label: string; children: ReactNode }) {
  return (
    <>
      <dt className="text-on-surface-variant">{label}</dt>
      <dd className="min-w-0">{children}</dd>
    </>
  );
}

function hasContactDetails(sos: Sos) {
  return !!(sos.phone || sos.bloodGroup || sos.medicalNotes || sos.contacts.length);
}

function PersonSection({ sos }: { sos: Sos }) {
  const [copied, setCopied] = useState(false);
  if (!hasContactDetails(sos))
    return (
      <>
        <div className="flex gap-3 rounded-card bg-surface-container-high p-3">
          <Icon name="info" size={20} className="text-mesh" />
          <div className="flex flex-col gap-1">
            <p className="text-list font-semibold">Contact details didn't arrive with this SOS</p>
            <p className="text-list text-on-surface-variant">
              It came only through other phones, so it carries the name, needs and location. Phone, blood group and family contacts are sent
              separately and haven't reached the server yet. They'll appear here if they do.
            </p>
          </div>
        </div>
        {sos.name && (
          <Facts>
            <Fact label="Name">{sos.name}</Fact>
          </Facts>
        )}
      </>
    );
  return (
    <Facts>
      <Fact label="Name">{sos.name || 'Not given'}</Fact>
      {sos.phone && (
        <Fact label="Phone">
          <span className="flex items-center gap-2">
            <a href={`tel:${sos.phone}`} className="text-primary hover:underline">
              {phoneLabel(sos.phone)}
            </a>
            <button
              type="button"
              onClick={() => {
                navigator.clipboard?.writeText(sos.phone!).then(() => {
                  setCopied(true);
                  setTimeout(() => setCopied(false), 2000);
                });
              }}
              className="inline-flex items-center gap-1 rounded-chip px-1.5 text-caption font-semibold text-primary hover:bg-surface-container-high"
            >
              <Icon name={copied ? 'check' : 'content_copy'} size={14} />
              {copied ? 'Copied' : 'Copy'}
            </button>
          </span>
        </Fact>
      )}
      <Fact label="Blood group">{sos.bloodGroup || "Don't know"}</Fact>
      <Fact label="Medical notes">{sos.medicalNotes || 'None given'}</Fact>
    </Facts>
  );
}

function LocationSection({ sos, now }: { sos: Sos; now: number }) {
  if (sos.lat == null || sos.lon == null)
    return (
      <p className="flex items-start gap-2 text-list text-on-surface-variant">
        <Icon name="location_off" size={18} className="text-warning" />
        No location yet: their phone had no GPS fix when it sent the SOS. It sends one as soon as it gets a fix.
      </p>
    );
  const stale = sos.locationAt ? Math.round((now / 1000 - sos.locationAt) / 60) : null;
  return (
    <>
      <Facts>
        <Fact label="Coordinates">{coords(sos.lat, sos.lon)}</Fact>
        <Fact label="Accuracy">
          {stale != null ? (
            <span>
              <span className="font-semibold text-warning">Last known, {stale} min old</span>
              {sos.accuracyM != null && ` · ±${Math.round(sos.accuracyM)} m`}
            </span>
          ) : sos.accuracyM != null ? (
            `±${Math.round(sos.accuracyM)} m · GPS`
          ) : (
            'Not reported'
          )}
        </Fact>
      </Facts>
      <a
        href={mapsLink(sos.lat, sos.lon)}
        target="_blank"
        rel="noreferrer"
        className="inline-flex w-fit items-center gap-1.5 text-list font-semibold text-primary hover:underline"
      >
        <Icon name="open_in_new" size={16} />
        Open in Google Maps
      </a>
      <MiniMap lat={sos.lat} lon={sos.lon} status={sos.status} />
    </>
  );
}

function ArrivalSection({ sos }: { sos: Sos }) {
  const transitSec = sos.time ? (sos.receivedAt ?? sos.createdAt) - sos.time : null;
  const t = transitSec != null ? ` · ${duration(transitSec)}` : '';
  return (
    <div className="flex flex-col gap-3">
      <div className="flex flex-col gap-1.5">
        {sos.via.includes('direct') && (
          <span className="flex items-center gap-2 text-list">
            <Icon name="cloud_upload" size={18} className="text-on-surface-variant" />
            {viaLabel(sos, 'direct')}
            {!sos.via.includes('mesh') && t}
          </span>
        )}
        {sos.via.includes('mesh') && (
          <span className="flex items-center gap-2 text-list text-mesh">
            <MeshIcon size={18} />
            {viaLabel(sos, 'mesh')}
            {t}
          </span>
        )}
        {sos.via.includes('radio') && (
          <span className="flex items-center gap-2 text-list text-radio">
            <RadioIcon size={18} />
            Radio{sos.radioNode && ` · node ${sos.radioNode}`}
          </span>
        )}
      </div>
      <div className="flex items-center gap-2 text-caption text-on-surface-variant" aria-label="Path">
        <PathStep icon="smartphone" label="Their phone" />
        {sos.via.includes('mesh') && (
          <>
            <PathLine />
            <PathStep mesh label={sos.hops != null ? `${Math.max(0, sos.hops - 1)} ${sos.hops - 1 === 1 ? 'phone' : 'phones'}` : 'Phones'} />
            <PathLine />
            <PathStep icon="send_to_mobile" label="Gateway" />
          </>
        )}
        {sos.via.includes('radio') && !sos.via.includes('mesh') && (
          <>
            <PathLine />
            <PathStep radio label="Radio" />
          </>
        )}
        <PathLine />
        <PathStep icon="cloud_done" label="Server" />
      </div>
      <Facts>
        <Fact label="Uploaded by">
          {sos.relayedBy.length ? sos.relayedBy.join(', ') : sos.via.includes('direct') ? 'Their own phone, mobile data' : 'Not recorded'}
        </Fact>
      </Facts>
    </div>
  );
}

function PathStep({ icon, label, mesh, radio }: { icon?: string; label: string; mesh?: boolean; radio?: boolean }) {
  return (
    <span className="flex flex-col items-center gap-1">
      <span className={cx('flex size-8 items-center justify-center rounded-full bg-surface-container-high', mesh && 'text-mesh', radio && 'text-radio')}>
        {mesh ? <MeshIcon size={18} /> : radio ? <RadioIcon size={18} /> : <Icon name={icon!} size={18} />}
      </span>
      {label}
    </span>
  );
}

function PathLine() {
  return <span className="mb-5 h-px flex-1 bg-outline-variant" />;
}

function FamilySection({ sos }: { sos: Sos }) {
  const results = sos.smsResults ?? [];
  const nameFor = (phone: string, name?: string) => name || sos.contacts.find((c) => c.phone === phone)?.name || 'Contact';
  if (!results.length && !sos.contacts.length)
    return (
      <p className="text-list text-on-surface-variant">
        The server has no contacts for this SOS. If their phone gets signal, it texts family itself and reports back here.
      </p>
    );
  if (!results.length)
    return (
      <ul className="flex flex-col gap-2">
        {sos.contacts.map((c) => (
          <li key={c.phone} className="flex items-center justify-between text-list">
            <span>
              <span className="font-semibold">{c.name || 'Contact'}</span>
              <span className="block text-caption text-on-surface-variant">{phoneLabel(c.phone)}</span>
            </span>
            <span className="flex items-center gap-1 text-on-surface-variant">
              <Icon name="schedule" size={16} />
              Waiting to text
            </span>
          </li>
        ))}
      </ul>
    );
  return (
    <ul className="flex flex-col gap-2">
      {results.map((r, i) => (
        <li key={`${r.phone}-${i}`} className="flex items-center justify-between gap-3 text-list">
          <span className="min-w-0">
            <span className="font-semibold">{nameFor(r.phone, r.name)}</span>
            <span className="block text-caption text-on-surface-variant">
              {phoneLabel(r.phone)}
              {r.by === 'phone' ? ' · texted from their phone' : ''}
            </span>
          </span>
          {r.ok ? (
            <span className="flex shrink-0 items-center gap-1 text-confirmed">
              <Icon name="check_circle" size={16} fill />
              Sent{r.at ? ` · ${clockTime(r.at)}` : ''}
            </span>
          ) : (
            <span className="flex shrink-0 items-center gap-1 text-warning">
              <Icon name="error" size={16} fill />
              {r.by === 'server' && !sos.smsSent ? 'Failed · retrying' : 'Failed'}
              {r.at ? ` · ${clockTime(r.at)}` : ''}
            </span>
          )}
        </li>
      ))}
    </ul>
  );
}

// ---------- History ----------

interface TimelineItem {
  time: number;
  title: string;
  detail?: string;
  icon: string;
}

function describe(h: HistoryEntry, i: number, sos: Sos): TimelineItem | null {
  const by = h.by ? `${h.by} · ` : '';
  switch (h.status) {
    case 'new':
      return i === 0
        ? { time: h.time, title: 'Received by the server', detail: sos.via.includes('direct') ? 'Uploaded directly from their phone' : `Uploaded by ${sos.relayedBy[0] || 'a gateway phone'}`, icon: 'cloud_done' }
        : { time: h.time, title: 'Moved back to new', detail: h.by ? `by ${h.by}` : undefined, icon: 'undo' };
    case 'attended':
      return { time: h.time, title: `Marked attended${h.assignee ? ` · ${h.assignee}` : ''}`, detail: h.by ? `by ${h.by}` : undefined, icon: 'directions_run' };
    case 'resolved':
      return { time: h.time, title: 'Marked resolved', detail: h.by ? `by ${h.by}` : undefined, icon: 'check_circle' };
    case 'safe':
      return { time: h.time, title: 'Marked safe', detail: '“I\'m safe now” on their phone', icon: 'verified_user' };
    case 'message':
      return { time: h.time, title: 'Message sent to them', detail: `${by}“${h.text ?? ''}”`, icon: 'send' };
    case 'sms':
      return { time: h.time, title: h.text || 'Family texted from their phone', icon: 'sms' };
    default:
      return null;
  }
}

function Timeline({ sos }: { sos: Sos }) {
  const items: TimelineItem[] = [];
  if (sos.time) items.push({ time: sos.time, title: 'SOS sent from their phone', icon: 'e911_emergency' });
  sos.history.forEach((h, i) => {
    const item = describe(h, i, sos);
    if (item) items.push(item);
  });
  const serverTexts = (sos.smsResults ?? []).filter((r) => r.by !== 'phone' && r.at);
  if (serverTexts.length) {
    const ok = serverTexts.filter((r) => r.ok).length;
    items.push({
      time: serverTexts[0]!.at!,
      title: 'Family texts sent',
      detail: `${ok} delivered${serverTexts.length - ok ? `, ${serverTexts.length - ok} failed` : ''}`,
      icon: 'sms',
    });
  }
  (sos.notes ?? []).forEach((n) => items.push({ time: n.at, title: 'Note added', detail: `${n.by ? `${n.by}: ` : ''}${n.text}`, icon: 'sticky_note_2' }));
  items.sort((a, b) => a.time - b.time);
  return (
    <ol className="flex flex-col">
      {items.map((it, i) => (
        <li key={i} className="flex gap-3">
          <span className="flex flex-col items-center">
            <span className="flex size-7 items-center justify-center rounded-full bg-surface-container-high text-on-surface-variant">
              <Icon name={it.icon} size={16} />
            </span>
            {i < items.length - 1 && <span className="w-px flex-1 bg-outline-variant" />}
          </span>
          <span className="flex-1 pb-4">
            <span className="flex items-baseline justify-between gap-2">
              <span className="text-list font-semibold">{it.title}</span>
              <Clock time={it.time} className="shrink-0 text-caption text-on-surface-variant" />
            </span>
            {it.detail && <span className="block text-list text-on-surface-variant">{it.detail}</span>}
          </span>
        </li>
      ))}
    </ol>
  );
}

// ---------- Composer (message to person, team notes) ----------

function Composer({
  placeholder,
  sendLabel,
  icon,
  limitBytes,
  disabledReason,
  onSend,
}: {
  placeholder: string;
  sendLabel: string;
  icon: string;
  limitBytes?: number;
  disabledReason?: string;
  onSend: (text: string) => Promise<boolean>;
}) {
  const [text, setText] = useState('');
  const [busy, setBusy] = useState(false);
  const bytes = byteLength(text.trim());
  const over = limitBytes != null && bytes > limitBytes;
  const disabled = !!disabledReason;
  async function send() {
    if (!text.trim() || over || busy) return;
    setBusy(true);
    const ok = await onSend(text.trim());
    setBusy(false);
    if (ok) setText('');
  }
  return (
    <div className="flex flex-col gap-2">
      <textarea
        value={text}
        disabled={disabled}
        onChange={(e) => setText(e.target.value)}
        onKeyDown={(e) => {
          if (e.key === 'Enter' && (e.metaKey || e.ctrlKey)) {
            e.preventDefault();
            send();
          }
        }}
        rows={2}
        placeholder={disabled ? disabledReason : placeholder}
        aria-label={placeholder}
        className="resize-none rounded-chip border border-outline bg-surface-container-lowest px-3 py-2 text-list outline-none focus:border-primary focus:ring-1 focus:ring-primary disabled:opacity-60"
      />
      <div className="flex items-center gap-2">
        {limitBytes != null && (
          <span className={cx('text-caption', over ? 'font-semibold text-error' : 'text-on-surface-variant')}>
            {bytes} / {limitBytes} bytes
          </span>
        )}
        <Button variant="tonal" icon={icon} className="ml-auto h-9" disabled={disabled || !text.trim() || over || busy} onClick={send} title={disabledReason}>
          {busy ? 'Sending…' : sendLabel}
        </Button>
        {!disabled && <Kbd>Ctrl ↵</Kbd>}
      </div>
    </div>
  );
}
