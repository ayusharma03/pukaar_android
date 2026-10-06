// Times are shown as "4 min ago", with the exact Indian time on hover (title attribute).

const IST = 'Asia/Kolkata';

/** "just now", "4 min ago", "2 h ago", "3 days ago". Inputs in Unix seconds. */
export function timeAgo(unixSec: number, nowMs: number = Date.now()): string {
  const s = Math.max(0, Math.floor(nowMs / 1000 - unixSec));
  if (s < 45) return 'just now';
  const m = Math.round(s / 60);
  if (m < 60) return `${m} min ago`;
  const h = Math.floor(m / 60);
  if (h < 24) return `${h} h ago`;
  const d = Math.floor(h / 24);
  return d === 1 ? '1 day ago' : `${d} days ago`;
}

/** "4:12 pm" in Indian time. */
export function clockTime(unixSec: number): string {
  return new Date(unixSec * 1000)
    .toLocaleTimeString('en-IN', { hour: 'numeric', minute: '2-digit', hour12: true, timeZone: IST })
    .toLowerCase();
}

/** "6 Oct 2026, 4:12:05 pm IST", for hover text. */
export function exactTime(unixSec: number): string {
  const s = new Date(unixSec * 1000).toLocaleString('en-IN', {
    day: 'numeric',
    month: 'short',
    year: 'numeric',
    hour: 'numeric',
    minute: '2-digit',
    second: '2-digit',
    hour12: true,
    timeZone: IST,
  });
  return `${s.replace(/\b(AM|PM)\b/, (x) => x.toLowerCase())} IST`;
}
