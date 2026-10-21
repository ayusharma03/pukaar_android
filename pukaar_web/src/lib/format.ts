// Labels and small formatters shared by the screens.
import type { Need, Role, Sos, Status, Via } from './types';

/** The district this dashboard serves (handoff: sample district Darbhanga, Bihar). */
export const DISTRICT = 'Darbhanga';
export const CONTROL_ROOM = 'District Control Room';
export const DISTRICT_CENTER: [number, number] = [86.03, 26.08];
export const DISTRICT_ZOOM = 9.4;

/** Messages over the mesh and radio must fit in one radio packet. */
export const RADIO_LIMIT_BYTES = 200;
export const byteLength = (s: string) => new TextEncoder().encode(s).length;

export const STATUS_LABEL: Record<Status, string> = { new: 'New', attended: 'Attended', resolved: 'Resolved' };
export const STATUS_ICON: Record<Status, string> = { new: 'e911_emergency', attended: 'directions_run', resolved: 'check_circle' };

export const NEEDS: { key: Need; label: string; icon: string; strong: boolean }[] = [
  { key: 'Trapped', label: 'Trapped', icon: 'crisis_alert', strong: true },
  { key: 'Injured', label: 'Injured', icon: 'personal_injury', strong: true },
  { key: 'ChildOrElderly', label: 'Child or elderly', icon: 'elderly', strong: false },
  { key: 'NeedMedicine', label: 'Medicine', icon: 'medication', strong: false },
  { key: 'NeedWater', label: 'Water', icon: 'water_drop', strong: false },
];

export const ROLE_LABEL: Record<Role, string> = { viewer: 'Viewer', responder: 'Responder', admin: 'Admin' };
export const canAct = (role: Role | null | undefined) => role === 'responder' || role === 'admin';

export const isUrgent = (s: Sos) => s.flags.includes('Injured') || s.flags.includes('Trapped');

export function people(n: number) {
  return n === 1 ? '1 person' : `${n} people`;
}

/** "Kiratpur › Jhagarua", "Kiratpur", or "Area unknown". */
export function areaLabel(s: Pick<Sos, 'area'>) {
  if (!s.area?.block) return 'Area unknown';
  return s.area.village ? `${s.area.block} › ${s.area.village}` : s.area.block;
}

/** The main arrival path for a row: radio beats mesh beats direct (the hardest path it took). */
export function mainVia(s: Sos): Via {
  if (s.via.includes('radio')) return 'radio';
  if (s.via.includes('mesh') && !s.via.includes('direct')) return 'mesh';
  return s.via.includes('direct') ? 'direct' : 'mesh';
}

export function viaLabel(s: Sos, via: Via = mainVia(s)) {
  if (via === 'direct') return 'Direct';
  if (via === 'radio') return 'Radio';
  return s.hops != null ? `Mesh · ${s.hops} ${s.hops === 1 ? 'hop' : 'hops'}` : 'Mesh';
}

/** "26.0412° N, 86.1287° E" */
export function coords(lat: number, lon: number) {
  return `${Math.abs(lat).toFixed(4)}° ${lat >= 0 ? 'N' : 'S'}, ${Math.abs(lon).toFixed(4)}° ${lon >= 0 ? 'E' : 'W'}`;
}

/** "+91 98450 12345" for Indian mobile numbers, otherwise as given. */
export function phoneLabel(p: string) {
  const d = p.replace(/[^\d+]/g, '');
  const m = d.match(/^(?:\+?91|0)?([6-9]\d{4})(\d{5})$/);
  return m ? `+91 ${m[1]} ${m[2]}` : p;
}

export function initials(name: string) {
  return (
    name
      .split(/\s+/)
      .filter(Boolean)
      .slice(0, 2)
      .map((w) => w[0]!.toUpperCase())
      .join('') || '?'
  );
}

/** "1 min", "1 h 5 min" */
export function duration(seconds: number) {
  const s = Math.max(0, Math.round(seconds));
  if (s < 60) return s < 2 ? 'under 1 s' : `${s} s`;
  const m = Math.round(s / 60);
  if (m < 60) return `${m} min`;
  const h = Math.floor(m / 60);
  return m % 60 ? `${h} h ${m % 60} min` : `${h} h`;
}

export function mapsLink(lat: number, lon: number) {
  return `https://maps.google.com/?q=${lat.toFixed(5)},${lon.toFixed(5)}`;
}
