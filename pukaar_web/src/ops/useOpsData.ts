// Live data for D2-D4: Firestore listeners (never polling) on open SOS, resolved SOS when shown,
// the Disaster Relief chat and broadcasts, plus connection state for the live indicator and banner.
import {
  collection,
  disableNetwork,
  enableNetwork,
  getCountFromServer,
  limit,
  onSnapshot,
  orderBy,
  query,
  where,
  type FirestoreError,
} from 'firebase/firestore';
import { useCallback, useEffect, useRef, useState } from 'react';
import { db } from '../lib/firebase';
import type { Broadcast, ChatMessage, Sos } from '../lib/types';

export interface Connection {
  /** Listeners are in sync with the server */
  live: boolean;
  /** Last time a snapshot came from the server (ms) */
  lastSyncAt: number | null;
  /** When the connection was lost (ms), for "data from 4:12 pm" */
  lostAt: number | null;
  attempts: number;
}

export interface OpsData {
  loading: boolean;
  error: string | null;
  sos: Sos[];
  resolvedCount: number | null;
  messages: ChatMessage[];
  broadcasts: Broadcast[];
  connection: Connection;
  /** id -> arrival time (ms), for the 60 s highlight and the pin pulse */
  freshIds: Map<string, number>;
  retry: () => void;
}

function describe(e: FirestoreError) {
  if (e.code === 'permission-denied') return "Your account can't read SOS data. Ask your district admin for access.";
  if (e.code === 'unavailable') return "Couldn't reach the server.";
  return `Couldn't load data (${e.code}).`;
}

const FRESH_MS = 60_000;

export function useOpsData({ showResolved, onArrived }: { showResolved: boolean; onArrived: (list: Sos[]) => void }): OpsData {
  const [open, setOpen] = useState<Sos[] | null>(null);
  const [resolved, setResolved] = useState<Sos[]>([]);
  const [resolvedCount, setResolvedCount] = useState<number | null>(null);
  const [messages, setMessages] = useState<ChatMessage[]>([]);
  const [broadcasts, setBroadcasts] = useState<Broadcast[]>([]);
  const [error, setError] = useState<string | null>(null);
  const [connection, setConnection] = useState<Connection>({ live: false, lastSyncAt: null, lostAt: null, attempts: 0 });
  const [freshIds, setFreshIds] = useState<Map<string, number>>(new Map());
  const onArrivedRef = useRef(onArrived);
  onArrivedRef.current = onArrived;

  const markSync = useCallback((fromCache: boolean) => {
    setConnection((c) => {
      const online = navigator.onLine;
      if (!fromCache && online) return { live: true, lastSyncAt: Date.now(), lostAt: null, attempts: 0 };
      return c.live || c.lostAt == null ? { ...c, live: false, lostAt: c.lostAt ?? c.lastSyncAt ?? Date.now() } : c;
    });
  }, []);

  // Open SOS: always live.
  useEffect(() => {
    let first = true;
    const q = query(collection(db, 'sos'), where('status', 'in', ['new', 'attended']));
    return onSnapshot(
      q,
      { includeMetadataChanges: true },
      (snap) => {
        setError(null);
        markSync(snap.metadata.fromCache);
        if (snap.metadata.hasPendingWrites) return;
        const list = snap.docs.map((d) => d.data() as Sos);
        setOpen(list);
        // New SOS after the first load: highlight, pulse, chime, toast.
        if (!first && !snap.metadata.fromCache) {
          const arrived = snap
            .docChanges()
            .filter((c) => c.type === 'added' && (c.doc.data() as Sos).status === 'new' && (c.doc.data() as Sos).history?.length <= 1)
            .map((c) => c.doc.data() as Sos);
          if (arrived.length) {
            const t = Date.now();
            setFreshIds((m) => new Map([...m, ...arrived.map((s) => [s.id, t] as const)]));
            onArrivedRef.current(arrived);
          }
        }
        if (!snap.metadata.fromCache) first = false;
      },
      (e) => setError(describe(e)),
    );
  }, [markSync]);

  // Resolved SOS: listened to only when shown; otherwise just counted (for the top bar chip).
  useEffect(() => {
    if (!showResolved) {
      setResolved([]);
      let stop = false;
      const count = () =>
        getCountFromServer(query(collection(db, 'sos'), where('status', '==', 'resolved')))
          .then((r) => !stop && setResolvedCount(r.data().count))
          .catch(() => {});
      count();
      const t = setInterval(count, 30_000);
      return () => {
        stop = true;
        clearInterval(t);
      };
    }
    const q = query(collection(db, 'sos'), where('status', '==', 'resolved'), orderBy('statusTime', 'desc'), limit(500));
    return onSnapshot(
      q,
      (snap) => {
        const list = snap.docs.map((d) => d.data() as Sos);
        setResolved(list);
        setResolvedCount(list.length);
      },
      (e) => setError(describe(e)),
    );
  }, [showResolved]);

  // Keep the resolved count right when something is resolved or reopened while it's hidden.
  const openIds = open?.map((s) => s.id).join(',');
  useEffect(() => {
    if (showResolved || openIds === undefined) return;
    getCountFromServer(query(collection(db, 'sos'), where('status', '==', 'resolved')))
      .then((r) => setResolvedCount(r.data().count))
      .catch(() => {});
  }, [openIds, showResolved]);

  useEffect(() => {
    const q = query(collection(db, 'messages'), orderBy('time', 'desc'), limit(300));
    return onSnapshot(q, (snap) => setMessages(snap.docs.map((d) => d.data() as ChatMessage)), () => {});
  }, []);

  useEffect(() => {
    const q = query(collection(db, 'broadcasts'), orderBy('time', 'desc'), limit(50));
    return onSnapshot(q, (snap) => setBroadcasts(snap.docs.map((d) => d.data() as Broadcast)), () => {});
  }, []);

  // Browser offline/online events react faster than Firestore's own detection.
  useEffect(() => {
    const off = () => markSync(true);
    const on = () => retry();
    window.addEventListener('offline', off);
    window.addEventListener('online', on);
    return () => {
      window.removeEventListener('offline', off);
      window.removeEventListener('online', on);
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [markSync]);

  // Drop highlights after 60 s.
  useEffect(() => {
    if (!freshIds.size) return;
    const t = setTimeout(() => {
      const cutoff = Date.now() - FRESH_MS;
      setFreshIds((m) => new Map([...m].filter(([, at]) => at > cutoff)));
    }, 5_000);
    return () => clearTimeout(t);
  }, [freshIds]);

  const retry = useCallback(() => {
    setConnection((c) => ({ ...c, attempts: c.attempts + 1 }));
    disableNetwork(db)
      .then(() => enableNetwork(db))
      .catch(() => {});
  }, []);

  return {
    loading: open === null && !error,
    error,
    sos: [...(open ?? []), ...resolved],
    resolvedCount,
    messages,
    broadcasts,
    connection,
    freshIds,
    retry,
  };
}

/** A short two-note chime for new SOS (Web Audio, no file to load). */
export function playChime() {
  try {
    const ctx = new AudioContext();
    const notes = [880, 1175];
    notes.forEach((f, i) => {
      const o = ctx.createOscillator();
      const g = ctx.createGain();
      o.type = 'sine';
      o.frequency.value = f;
      const t = ctx.currentTime + i * 0.18;
      g.gain.setValueAtTime(0.0001, t);
      g.gain.exponentialRampToValueAtTime(0.25, t + 0.02);
      g.gain.exponentialRampToValueAtTime(0.0001, t + 0.35);
      o.connect(g).connect(ctx.destination);
      o.start(t);
      o.stop(t + 0.4);
    });
    setTimeout(() => ctx.close(), 1000);
  } catch {
    /* audio blocked until the user interacts with the page */
  }
}
