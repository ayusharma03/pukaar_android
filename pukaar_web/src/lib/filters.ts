// Filter bar and search (D2). Filtered-out SOS stay on the map, dimmed; the list only shows matches.
import type { Need, Sos, Via } from './types';
import { areaLabel } from './format';

export type TimeRange = '1h' | '6h' | '24h' | 'all';

export interface Filters {
  timeRange: TimeRange;
  /** Block name, or undefined for all blocks */
  block?: string;
  needs: Need[];
  via?: Via;
}

export const DEFAULT_FILTERS: Filters = { timeRange: '24h', needs: [] };

export const TIME_LABEL: Record<TimeRange, string> = {
  '1h': 'Last hour',
  '6h': 'Last 6 hours',
  '24h': 'Last 24 hours',
  all: 'All time',
};
const TIME_SECONDS: Record<TimeRange, number> = { '1h': 3600, '6h': 21600, '24h': 86400, all: Infinity };

export function isFiltered(f: Filters) {
  return f.timeRange !== DEFAULT_FILTERS.timeRange || !!f.block || f.needs.length > 0 || !!f.via;
}

export function matchesFilters(s: Sos, f: Filters, nowSec: number) {
  if (nowSec - (s.time || s.createdAt) > TIME_SECONDS[f.timeRange]) return false;
  if (f.block && s.area?.block !== f.block) return false;
  if (f.needs.length && !f.needs.some((n) => s.flags.includes(n))) return false;
  if (f.via && !s.via.includes(f.via)) return false;
  return true;
}

/** Name, village or block, phone, SOS id, or words from the message. */
export function matchesSearch(s: Sos, q: string) {
  const query = q.trim().toLowerCase();
  if (!query) return true;
  const digits = query.replace(/\D/g, '');
  const hay = [s.name, areaLabel(s), s.id, s.message, s.assignee ?? ''].join(' ').toLowerCase();
  if (hay.includes(query)) return true;
  return digits.length >= 4 && [s.phone, ...s.contacts.map((c) => c.phone)].some((p) => p?.replace(/\D/g, '').includes(digits));
}

/** Short summary of the active filters, for the "filtered to nothing" state. */
export function describeFilters(f: Filters, search: string) {
  const parts: string[] = [];
  if (f.block) parts.push(`Area: ${f.block}`);
  if (f.needs.length) parts.push(`Needs: ${f.needs.length} selected`);
  if (f.via) parts.push(`Arrived: ${f.via}`);
  if (f.timeRange !== DEFAULT_FILTERS.timeRange) parts.push(TIME_LABEL[f.timeRange]);
  if (search.trim()) parts.push(`Search: “${search.trim()}”`);
  return parts.join(' · ');
}
