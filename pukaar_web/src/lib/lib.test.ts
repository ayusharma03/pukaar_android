import { describe, expect, it } from 'vitest';
import { byPriority } from './priority';
import { clockTime, timeAgo } from './time';
import type { Sos } from './types';

const sos = (id: string, over: Partial<Sos>): Sos => ({
  id, seq: 1, status: 'new', statusTime: 0, by: '', time: 1000, createdAt: 1000,
  lat: null, lon: null, accuracyM: null, people: 1, flags: [], name: '', message: '',
  battery: 50, contacts: [], via: ['mesh'], relayedBy: [], smsSent: false, history: [],
  ...over,
});

describe('byPriority', () => {
  it('orders by status, people, urgent flags, battery, then oldest', () => {
    const list = [
      sos('attended', { status: 'attended', people: 9 }),
      sos('old', { time: 500 }),
      sos('young', { time: 900 }),
      sos('lowBattery', { battery: 5, time: 900 }),
      sos('injured', { flags: ['Injured'] }),
      sos('family', { people: 4 }),
      sos('resolved', { status: 'resolved', people: 20 }),
    ];
    expect(byPriority(list).map((s) => s.id)).toEqual(['family', 'injured', 'lowBattery', 'old', 'young', 'attended', 'resolved']);
  });

  it('puts unknown battery after known battery', () => {
    expect(byPriority([sos('unknown', { battery: null }), sos('known', { battery: 90 })]).map((s) => s.id)).toEqual(['known', 'unknown']);
  });
});

describe('time', () => {
  const now = 1_760_000_000_000;
  it('says how long ago', () => {
    expect(timeAgo(now / 1000 - 10, now)).toBe('just now');
    expect(timeAgo(now / 1000 - 240, now)).toBe('4 min ago');
    expect(timeAgo(now / 1000 - 7200, now)).toBe('2 h ago');
    expect(timeAgo(now / 1000 - 86400 * 3, now)).toBe('3 days ago');
  });
  it('shows Indian time', () => {
    // 2025-10-09T08:53:20Z is 2:23 pm IST
    expect(clockTime(now / 1000)).toBe('2:23 pm');
  });
});
