// Writes to the Cloud Function routes (server/functions/src/http.js) with the user's ID token.
// The server signs what phones receive and records history, so the dashboard never writes Firestore.
import { auth, apiUrl } from './firebase';
import type { Status } from './types';

export class ApiError extends Error {
  constructor(message: string, readonly status: number) {
    super(message);
  }
}

async function post<T>(path: string, body: unknown): Promise<T> {
  const user = auth.currentUser;
  if (!user) throw new ApiError('Not signed in', 401);
  const token = await user.getIdToken();
  let res: Response;
  try {
    res = await fetch(apiUrl + path, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${token}` },
      body: JSON.stringify(body),
    });
  } catch {
    throw new ApiError('Could not reach the server', 0);
  }
  const data = await res.json().catch(() => ({}));
  if (!res.ok) throw new ApiError(data.error || `Server error (${res.status})`, res.status);
  return data as T;
}

/** POST /v1/admin/sos/{id}/status: sent back to the person as a signed update. */
export function setStatus(id: string, status: Status, by: string) {
  return post<{ id: string; status: Status; time: number; by: string }>(
    `/v1/admin/sos/${encodeURIComponent(id)}/status`,
    { status, by },
  );
}

/** POST /v1/admin/broadcasts: an official message into the Disaster Relief chat. */
export function sendBroadcast(from: string, text: string) {
  return post<{ id: string; from: string; text: string; time: number }>('/v1/admin/broadcasts', { from, text });
}
