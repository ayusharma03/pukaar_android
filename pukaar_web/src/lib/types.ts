// Firestore documents as the server writes them (server/functions/src/core.js), plus the fields the
// dashboard handoff asks the server to add. Fields marked "proposed" are NOT written by the server
// yet (pukaar_web/design_handoff/README.md, "Backend gaps"); the seed script fills them so screens
// can be built, and the UI must still work when they are missing.

export type Status = 'new' | 'attended' | 'resolved';
export type Need = 'Injured' | 'Trapped' | 'NeedWater' | 'NeedMedicine' | 'ChildOrElderly';
export type Via = 'direct' | 'mesh' | 'radio'; // 'radio' is proposed
export type Role = 'viewer' | 'responder' | 'admin'; // 'admin' is proposed

export interface Contact {
  name: string;
  phone: string;
}

export interface SmsResult {
  phone: string;
  ok: boolean;
  /** proposed: who texted them, the server (Twilio) or the person's own phone */
  by?: 'server' | 'phone';
  /** proposed (Unix seconds) */
  at?: number;
}

/** One status change. `status: 'safe'` is written when the person tapped "I'm safe now". */
export interface HistoryEntry {
  status: Status | 'safe';
  time: number;
  by: string;
}

export interface Note {
  by: string;
  at: number;
  text: string;
}

/** sos/{id}. Times are Unix seconds. */
export interface Sos {
  id: string;
  seq: number;
  status: Status;
  statusTime: number;
  by: string;
  /** When the phone sent it */
  time: number;
  /** When the server first saw it */
  createdAt: number;
  updatedAt?: number;
  lat: number | null;
  lon: number | null;
  accuracyM: number | null;
  people: number;
  flags: Need[];
  name: string;
  message: string;
  battery: number | null;
  phone?: string;
  bloodGroup?: string;
  medicalNotes?: string;
  contacts: Contact[];
  via: Via[];
  relayedBy: string[];
  smsSent: boolean;
  smsResults?: SmsResult[];
  safeAt?: number;
  history: HistoryEntry[];

  // proposed
  receivedAt?: number;
  locationAt?: number;
  area?: { block: string; village: string };
  hops?: number;
  radioNode?: string;
  assignee?: string;
  notes?: Note[];
}

/** messages/{id}: the Disaster Relief chat as gateways uploaded it. */
export interface ChatMessage {
  id: string;
  sender: string;
  text: string;
  time: number | null;
  lat: number | null;
  lon: number | null;
}

/** broadcasts/{id}: official messages sent from the dashboard. */
export interface Broadcast {
  id: string;
  from: string;
  text: string;
  time: number;
  /** proposed: phones that acknowledged it */
  reach?: number;
}
