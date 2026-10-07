// Firestore documents as the server writes them (server/functions/src/core.js). Some fields only
// arrive from newer phones (hops, radioNode, locationAt, phone-reported texts, broadcast reach),
// so the UI must work when they're missing.

export type Status = 'new' | 'attended' | 'resolved';
export type Need = 'Injured' | 'Trapped' | 'NeedWater' | 'NeedMedicine' | 'ChildOrElderly';
export type Via = 'direct' | 'mesh' | 'radio';
export type Role = 'viewer' | 'responder' | 'admin';

export interface Contact {
  name: string;
  phone: string;
}

export interface SmsResult {
  name?: string;
  phone: string;
  ok: boolean;
  /** Who texted them: the server (Twilio) or the person's own phone */
  by?: 'server' | 'phone';
  at?: number;
}

/**
 * One event. Status changes carry the operator who made them (by, uid) and who is going (assignee).
 * 'safe' is the person tapping "I'm safe now", 'message' a control-room message to them, 'sms' their
 * phone reporting it texted family. Entries from the phone have by ''.
 */
export interface HistoryEntry {
  status: Status | 'safe' | 'message' | 'sms';
  time: number;
  by: string;
  uid?: string;
  assignee?: string;
  text?: string;
}

export interface Note {
  at: number;
  text: string;
  by: string;
  uid?: string;
}

export interface OutboxMessage {
  id: string;
  time: number;
  from: string;
  text: string;
}

/** sos/{id}. Times are Unix seconds. */
export interface Sos {
  id: string;
  seq: number;
  status: Status;
  statusTime: number;
  /** What the person's phone shows as "on it": who is going, or the operator */
  by: string;
  /** When the phone sent it */
  time: number;
  createdAt: number;
  receivedAt?: number;
  updatedAt?: number;
  lat: number | null;
  lon: number | null;
  accuracyM: number | null;
  /** Set when the fix is older than the SOS */
  locationAt?: number;
  area?: { block: string; village: string };
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
  hops?: number;
  relayedBy: string[];
  radioNode?: string;
  smsSent: boolean;
  smsResults?: SmsResult[];
  safeAt?: number;
  assignee?: string;
  notes?: Note[];
  outbox?: OutboxMessage[];
  history: HistoryEntry[];
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
  /** Different phones that reported showing it (an estimate) */
  reach?: number;
  sentBy?: { by: string; uid: string };
}
