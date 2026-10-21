// Writes to the Cloud Function routes (server/functions/src/http.js) with the user's ID token.
// The server signs what phones receive and records history, so the dashboard never writes Firestore.
import { auth, apiUrl } from './firebase';
import type { Note, OutboxMessage, Status } from './types';

export class ApiError extends Error {
  constructor(
    message: string,
    readonly status: number,
  ) {
    super(message);
  }
  /** True when the request never reached the server (worth queueing and retrying). */
  get offline() {
    return this.status === 0;
  }
}

async function post<T>(path: string, body: unknown): Promise<T> {
  const user = auth.currentUser;
  if (!user) throw new ApiError('You are signed out. Sign in again.', 401);
  const token = await user.getIdToken();
  let res: Response;
  try {
    res = await fetch(apiUrl + path, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${token}` },
      body: JSON.stringify(body),
    });
  } catch {
    throw new ApiError("Couldn't reach the server", 0);
  }
  const data = await res.json().catch(() => ({}));
  if (!res.ok) {
    const message =
      res.status === 403 ? 'Your account can only view. Ask an admin for responder access.' : data.error || `Server error (${res.status})`;
    throw new ApiError(message, res.status);
  }
  return data as T;
}

const sosPath = (id: string, rest: string) => `/v1/admin/sos/${encodeURIComponent(id)}/${rest}`;

/** Status change, sent back to the person as a signed update. Also used for undo and reopen. */
export function setStatus(id: string, status: Status, opts: { assignee?: string; note?: string } = {}) {
  return post<{ id: string; status: Status; time: number; by: string }>(sosPath(id, 'status'), { status, ...opts });
}

export function addNote(id: string, text: string) {
  return post<Note>(sosPath(id, 'notes'), { text });
}

/** A message back to the person through gateways and the mesh (200 bytes at most). */
export function messagePerson(id: string, text: string) {
  return post<OutboxMessage>(sosPath(id, 'message'), { text });
}

/** An official message into the Disaster Relief chat (200 bytes at most). */
export function sendBroadcast(from: string, text: string) {
  return post<{ id: string; from: string; text: string; time: number }>('/v1/admin/broadcasts', { from, text });
}
