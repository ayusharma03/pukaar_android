// Incident list order (docs/dashboard-plan.md §4, handoff "Sort"):
// new before attended (resolved last), more people first, Injured or Trapped first,
// lower battery first, then oldest first.
import type { Sos, Status } from './types';

const STATUS_RANK: Record<Status, number> = { new: 0, attended: 1, resolved: 2 };

const urgent = (s: Sos) => s.flags.includes('Injured') || s.flags.includes('Trapped');

export function comparePriority(a: Sos, b: Sos): number {
  return (
    STATUS_RANK[a.status] - STATUS_RANK[b.status] ||
    (b.people || 1) - (a.people || 1) ||
    Number(urgent(b)) - Number(urgent(a)) ||
    (a.battery ?? 101) - (b.battery ?? 101) ||
    (a.time || 0) - (b.time || 0)
  );
}

export function byPriority(list: readonly Sos[]): Sos[] {
  return [...list].sort(comparePriority);
}
