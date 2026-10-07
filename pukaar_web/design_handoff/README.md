# Handoff: Pukaar rescuer dashboard

## Overview
A desktop web app for district control rooms. Responders see every SOS on a map and in a priority list, open one, mark it attended (with who is going) and later resolved, read the public Disaster Relief chat, and send official broadcasts. The main user is a control-room responder on long shifts with two monitors. A viewer role (for example the DM) watches without changing anything.

Source docs: `docs/dashboard_plan.md` (architecture and data), `docs/screens.md` (D1–D5), `docs/design-tokens.md`, `docs/design-brief.md`. The Android app's handoff is in `design_handoff_pukaar_app/` and shares the same visual language.

## About the design files
`design/Pukaar Dashboard.dc.html` is a **design reference made in HTML**. Open it in a browser with `support.js` next to it. Recreate it in **React + TypeScript + Vite + Tailwind**, with **MapLibre GL JS** on OpenFreeMap tiles, the Firebase JS SDK, and the Pukaar tokens. Don't copy the HTML; it uses inline styles and a hand-drawn SVG in place of the real map.

Each frame has an id badge (1a, 1b…), and this README refers to frames by those ids.

## Fidelity
**High fidelity** at 1440 × 900 px. It must also work at 1280 px: there the map shrinks, the right column stays 400 px and the detail panel stays 440 px. In CSS, 1 px = 1 px.

## Decisions taken with the product owner
- **Attended uses primary indigo**, not warning amber. This differs from design-tokens.md, so update that file. Amber is kept for warnings only: low battery, stale location, lost connection.
- **Roles:** Viewer, Responder, Admin.
- **Viewers can see everything** (phone, medical notes, family contacts, notes and the feed) but can't act. Disabled controls always say why.
- **Responders and admins** can send broadcasts. Only admins see the Team tab.
- **Arrival paths:** Direct, Mesh (with hop count) and Radio.
- **"Send message to this person"** is kept in D3.
- **Resolved SOS are hidden by default.** The Resolved count chip in the top bar toggles them on.
- **"Who is going"** is free text with recent values offered as suggestions. There is no team list in v1.
- **SOS that arrived only through the mesh** can still reach family: the person's own phone texts them when it gets signal and reports the result back to the server.
- **Area** labels are "Block › Village", looked up from lat/lon against block and village boundaries.
- **Sample district:** Darbhanga, Bihar. **New-SOS sound** is on by default.

## Backend gaps (needed by these designs, not in the current API)
| Need | Suggested change |
|---|---|
| Who is going, and notes | `POST /v1/admin/sos/{id}/status` gains `assignee?: string` and `note?: string`. Add `POST /v1/admin/sos/{id}/notes` with `{ text }`. Store both in `notes[]` and `assignee` on `sos/{id}` |
| Undo / Reopen | Allow the status to go `attended → new` and `resolved → attended`, recorded in `history[]` |
| Send message to the person | `POST /v1/admin/sos/{id}/message` with `{ text }`, signed and delivered through gateways like status updates. Limit 200 bytes |
| Received time | `receivedAt` (server time). `time` is already the time the phone sent it |
| Area name | `area: { block, village }`, computed on write from lat/lon (district boundary GeoJSON) |
| Arrival by radio | Add `radio` to `via[]`, plus `radioNode?: string` |
| Contacts the phone texted itself | `smsResults[]` gains `by: 'server' \| 'phone'` |
| Location age | `locationAt` when the fix is older than the SOS time ("last known, 12 min old") |
| Broadcast reach | `broadcasts/{id}.reach` (number of phones that acknowledged), updated by gateways |
| Admin role | `admin` claim. Firestore rules: admin ⊇ responder ⊇ viewer. Add an admin function to list users, invite, set role and revoke |
| Rate limiting | Already an open item in the plan. Fake SOS would flood this list |

## Design tokens
Use `docs/design-tokens.md` exactly, with Mukta for all text and Material Symbols Rounded (weight 400, fill 0; fill 1 for chips, status and selected states).

Map these roles to Tailwind as CSS variables on `:root[data-theme=dark|light]`: `primary, onPrimary, primaryContainer, onPrimaryContainer, secondaryContainer, onSecondaryContainer, surface, onSurface, onSurfaceVariant, outline, outlineVariant, surfaceContainerLowest…Highest, inverseSurface, inverseOnSurface, error, errorContainer`, plus the four roles for each status: `sos*, mesh*, radio*, confirmed*, warning*`. `sosFill #D32F2F` is the same in both themes.

Key uses:
- **Top bar:** `surfaceContainerLow`. **Map base:** `surfaceContainerLowest` outside the district and `surfaceContainerLow` inside. **Right column:** `surfaceContainerLow`. **Detail panel:** `surfaceContainer`. **Group headers:** `surfaceContainer`. **Selected row:** `surfaceContainerHigh` with a 3 px inset bar in `onSurface`.
- **Map style:** water is `primaryContainer` (rivers solid, floodplain at 35–45%); roads are `surfaceContainerHigh/Highest`; block boundaries are dashed `outlineVariant`; labels are `onSurfaceVariant`, 12 px. Build it as a custom MapLibre style JSON with the same values for each theme.
- **Type (px):**
  - Body 16/24; in lists 14/20; captions 13/18.
  - Card title 16/22, weight 600.
  - Panel title 17/22, weight 600; screen title 28/36, weight 600; dialog title 24/32, weight 600.
  - Counts in the top bar 17, weight 700.
- **Radii:** chips 6–8, cards and toasts 12, sections 16, dialogs 28, buttons fully rounded.
- No shadows, except the menu popover (`0 4px 16px rgba(0,0,0,.3)`).

## Layout (D2)
- **Top bar, 56 px.** Left to right:
  - logo tile (32 px) with "Pukaar / Darbhanga control room"
  - tabs: Operations, and Team (admins only)
  - status count chips (New, Attended, Resolved), which also act as toggles
  - search (250 px, `/` to focus)
  - live indicator
  - sound and theme toggles
  - user name and role
- **Filter bar, 48 px:** Time, Area (Block › Village), Needs, Arrived, Clear filters, and a "Chat on map" toggle. A result summary sits on the right ("17 open · Resolved hidden · sorted by priority").
- **Body:** the map fills the space. The right column is 400 px with tabs (Incidents | Relief feed), the list, and a 40 px footer of keyboard hints.
- **Detail panel:** 440 px, overlaid on the right edge, covering the list and 40 px of the map.

## Components
- **Map pin.** The number inside is the people count, and the shape and colour carry the status together.
  - **New:** circle in `sosFill` with a 2 px `surface` border and white bold count. Size by people: 26 (1–2), 32 (3–5), 38 (6+).
  - **Injured or trapped:** a halo disc 14 px larger, at `rgba(211,47,47,.3)`.
  - **Just arrived:** two rings (+26 and +44 px) that pulse twice over about 2 s, then are removed.
  - **Attended:** a rounded square (radius 8) in `primary`, with the count in `onPrimary`.
  - **Resolved:** a 20 px `confirmedContainer` circle with a check.
  - **Selected:** a 3 px `onSurface` ring, plus a label card above in `surfaceContainerHigh`.
  - **Cluster:** 42 / 48 / 56 px. The outer ring is a conic split of New, Attended and Resolved; the inner disc is `surfaceContainerHigh` with the total.
  - **Chat marker:** a 22 px rounded square in `surfaceContainerHighest` with a 1 px `outline` border. On hover it becomes 28 px in `primary`.
  - Use MapLibre symbol or HTML markers. Cluster with a `cluster: true` source and `clusterProperties` that count each status.
- **Status chip:** 24 px (32 px when large), with a filled icon and a word.
  - New: `sosContainer`, icon `e911_emergency`.
  - Attended: `primaryContainer`, icon `directions_run`.
  - Resolved: `confirmedContainer`, icon `check_circle`.
  - Marked safe: `confirmedContainer`, icon `verified_user`.
- **Needs:** icon plus word. Injured (`personal_injury`) and Trapped (`crisis_alert`) are shown in `onSurface` at weight 600. Water (`water_drop`), Medicine (`medication`) and Child or elderly (`elderly`) are shown in `onSurfaceVariant`.
- **Arrival:** Direct (`cloud_upload`, neutral), Mesh · n hops (mesh icon, `mesh` colour), Radio (radio icon, `radio` colour). The custom SVGs are the same as in the app.
- **Battery:** at 20% or below, the `battery_alert` icon and the value in `warning`. Above that, a battery icon with 1–6 bars in `onSurfaceVariant`.
- **Incident row:** padding 12/16 with a 20 px rank gutter. It has three lines:
  1. status chip, time since sent, battery
  2. "N people" (16/600) with the needs
  3. area, with arrival (or the assigned team when attended) on the right

  A just-arrived row has a `rgba(211,47,47,.14)` background that fades over 60 s, and its time is shown in `sos` colour. A stale row is shown at 60% opacity.
- **Detail panel:** a header with close, the SOS id, the area and J/K navigation, followed by these sections:
  - status block
  - what they sent
  - person
  - location (with a mini map at zoom ×3)
  - how it arrived (hops path, uploaded by)
  - family texted
  - send message to this person
  - team notes
  - history timeline

  The status block has six variants: new, attending form, attended, resolved, marked safe and viewer (component sheet 1z).
- **Mesh-only SOS:** a calm info card in `surfaceContainerHigh` with an info icon in `mesh`, saying "Contact details didn't arrive with this SOS". Never show empty fields.
- **Toast:** `inverseSurface`, radius 12, 440 px wide, placed bottom-left over the map. It shows for 8 s and has one action with a key hint. It never covers the list and is never modal.
- **Banner:** a 48 px `warningContainer` strip under the filter bar, used for lost connection, with "Retry now".
- **Dialog:** radius 28 in `surfaceContainerHigh` over a scrim. Use it only for removing access and changing roles.
- **Empty states:** a ripple-ring icon, a title (18/600) and one line of text.

## Screens
- **D1 Login (1x–1z region):**
  - Normal: a centred card over faint ripple rings.
  - Error: shown under the password field in `error`, with no SOS red.
  - Signed in but no role: "Check again" and "Sign out".
- **D2 Live operations (1a):** the default frame. **States (1h–1k):**
  - Empty: list empty state, with "Sound is on".
  - 150+: clusters, a hint pill and a virtualised list.
  - Lost connection: banner, amber live indicator, faded rows and a "Map and list as of 4:12 pm" pill.
  - Filtered to nothing: pins dimmed, "Clear filters".
- **D3 SOS detail (1b, 1l–1o):**
  - direct with partial family texting
  - mesh-only without contact details
  - marked safe by the person
  - viewer, with actions disabled and the reason shown
- **Core flow (1c–1g):** arrival → open → attend form → attended → resolved. The transition notes are under each frame.
- **D4 Relief feed (1p–1q):** the right-column tab, newest first, with official messages in `primaryContainer` and a verified badge.
  - Location chips fly the map to the message.
  - Incident pins dim while the feed is open.
  - The composer has a byte counter (200-byte radio limit). "Review broadcast" swaps in the confirmation card, which ends with "Send to everyone".
- **D5 Team (1r–1t):** invite by email with a role, plus role descriptions. The staff table shows name, email, role picker, last active and remove. Changing a role and removing access are both confirmed in a dialog. Pending invites show "Resend".
- **Light theme:** D2 and D3.
- **Component sheet:** all of the above parts in one frame.

## Interactions
- **Live data:** Firestore `onSnapshot` on `sos` (open ones, plus resolved ones only when that toggle is on) and on `messages`. The live indicator shows "updated Ns ago" from the last snapshot; if no snapshot arrives for more than 30 s, or the connection is lost, it switches to the stale state.
- **New SOS:**
  - insert it at its priority position, without moving the user's scroll or focus
  - play the chime once (unless sound is muted) and show the toast
  - the count chip ticks up, and the pin pulses
  - never open a modal or take focus
- **Sort:**
  1. status (new before attended)
  2. people, descending
  3. has Injured or Trapped
  4. battery, ascending
  5. time, oldest first
- **Open:**
  - The panel slides in over 250 ms, and the map flies to the pin over 600 ms (`flyTo`, zoom about 14).
  - The URL updates (`?sos=9KWD`) so a link can be shared.
  - Esc closes the panel and restores the previous view.
- **Attend:** A, or the button, opens the inline form. Focus goes to "Who is going". Enter confirms, and the change is optimistic with Undo for 10 s (Z). If the request fails, roll back and show an error toast.
- **Resolve:** R, one step, with Undo. Reopen sets the SOS back to Attended.
- **Marked safe:** when `safeAt` is set, show the banner in the status block and make "Mark resolved" the primary action.
- **While offline:** queue status changes, show the row as "Waiting to send", and send them when the connection is back.
- **Viewer:** action controls render disabled, with the reason. The keyboard shortcuts A, R and Z show a toast explaining the user has view-only access.
- **Motion:** 200–300 ms colour fades for status changes. Only new pins pulse, then they stop. Respect `prefers-reduced-motion` (no pulse, no fly animation).

### Keyboard
`J`/`K` next/previous · `Enter` open · `Esc` close · `A` attend · `R` resolve · `Z` undo · `/` search · `F` relief feed · `M` sound · `?` shortcut sheet. These are disabled while typing in a field, except `Esc` and `⌘/Ctrl+Enter` (send).

## Suggested front-end state
```ts
type Status = 'new' | 'attended' | 'resolved';
type Need = 'Injured' | 'Trapped' | 'NeedWater' | 'NeedMedicine' | 'ChildOrElderly';
interface Sos {
  id: string; seq: number; status: Status; statusTime: number; by?: string;
  time: number; receivedAt?: number; lat: number | null; lon: number | null;
  accuracyM?: number; locationAt?: number; area?: { block: string; village: string };
  people: number; flags: Need[]; name?: string; message?: string; battery?: number;
  phone?: string; bloodGroup?: string; medicalNotes?: string; contacts?: Contact[];
  via: ('direct' | 'mesh' | 'radio')[]; hops?: number; relayedBy: string[]; radioNode?: string;
  smsResults?: { name: string; phone: string; result: 'sent' | 'failed' | 'retrying'; by: 'server' | 'phone'; at: number }[];
  safeAt?: number; assignee?: string; notes?: { by: string; at: number; text: string }[];
  history: { at: number; text: string; by?: string }[];
}
interface UiState {
  role: 'viewer' | 'responder' | 'admin';
  connection: { live: boolean; lastSnapshotAt: number };
  filters: { statuses: Set<Status>; timeRange: '1h' | '6h' | '24h' | 'all'; area?: string; needs?: Need[]; via?: string };
  search: string; selectedId?: string; rightTab: 'incidents' | 'feed';
  attendDraft?: { assignee: string; note: string };
  pendingWrites: { id: string; action: 'attend' | 'resolve' | 'reopen' | 'note'; payload: unknown }[];
  freshIds: Map<string, number>; // id → arrival time, for the 60 s highlight
  soundOn: boolean; theme: 'dark' | 'light';
  mapView: { center: [number, number]; zoom: number }; prevMapView?: UiState['mapView'];
}
```
Suggested components: `TopBar`, `StatusCounts`, `FilterBar`, `OpsMap` (MapLibre + markers + clusters), `IncidentList` (virtualised with react-virtual), `IncidentRow`, `SosPanel` with section components, `StatusBlock`, `AttendForm`, `ReliefFeed`, `BroadcastComposer`, `Toasts`, `ConnectionBanner`, `TeamPage`, `ConfirmDialog`.

## Files
- `design/Pukaar Dashboard.dc.html` and `design/support.js`: the design reference.
- `docs/`: the source docs.
- The app's assets (logo mark and icon) are in the app handoff, under `pukaar_icon_1b/`.
