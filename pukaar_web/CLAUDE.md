# pukaar_web: rescuer dashboard, rules for Claude

The control-room web app (screens D1 to D5). It lives in this repo next to the Android app but is a
separate project: its own `package.json`, its own CI workflow, its own deploy. Nothing here is part
of the Android build or the Android release.

The repo-level rules in `../CLAUDE.md` still apply where they make sense (theme only, SOS red only
for SOS, no all-caps, ask before deleting). Android-only rules (Compose, `pk_` strings, `@Preview`)
don't apply here.

## Read first

| Need | Read |
|---|---|
| Architecture, data, Nostr decision | `../docs/dashboard-plan.md` |
| What each screen does | `../docs/screens.md`, D1 to D5 |
| Exact look, components, interactions, keyboard | `design_handoff/README.md` and `design_handoff/design/Pukaar Dashboard.dc.html` (open in a browser with `support.js` next to it) |
| Colours, Mukta, status colours | `../docs/design-tokens.md` (as CSS variables in `src/theme/tokens.css`) |
| Gateway contract | `../docs/protocol.md` §2 |
| Server routes | `../server/functions/src/http.js`, `core.js` |
| Build steps, one prompt per screen | `docs/build-prompts.md` |

`design_handoff/design/Pukaar Dashboard Screens.html` is about 2.9 MB. Search it, don't read it whole.

When the handoff and the docs disagree: `design_handoff/README.md` wins for visuals, `screens.md`
and the plan for behaviour. Say so when you hit a conflict.

## Data rules

- **Reads come from Firestore with live listeners (`onSnapshot`).** No polling.
- **Writes NEVER go to Firestore directly.** Status changes, notes, broadcasts and messages go
  through the Cloud Function routes (`src/lib/api.ts`) with the user's Firebase ID token in
  `Authorization: Bearer`. The server signs what phones receive and keeps the history. Firestore
  rules deny all client writes anyway.
- **No secrets in the dashboard.** No service-account keys, no server private keys, no Twilio.
  Firebase web config comes from `VITE_*` environment variables: `.env.local` (gitignored) for a
  real project, `.env.emulator` (committed, demo values only) for the emulators. `.env.example`
  lists them.
- Document shapes are in `src/lib/types.ts`. Fields marked "proposed" aren't written by the server
  yet; the UI must work without them.

## Look

- Colours only from the Tailwind theme in `src/index.css` (backed by `src/theme/tokens.css`). The
  default Tailwind palette, radii and shadows are switched off on purpose. No raw hex in components.
- **SOS red (`sos-*`, `sos-fill`) is only for SOS.** Form errors use `error`. Attended uses
  `primary`, resolved uses `confirmed`, `warning` is for low battery, stale location and lost
  connection.
- Light and dark themes, set by `data-theme` on `<html>` and chosen by the user. Dark is the default.
- Mukta for all text, bundled from npm (`@fontsource/mukta`), never from a CDN: control rooms may
  have poor internet. Icons are Material Symbols Rounded (`material-symbols`), outlined by default,
  filled for chips, status and selected states.
- Design frame 1440 × 900; must work at 1280 wide (map shrinks, right column stays 400, detail panel
  440).
- No all-caps. No shadows except the menu popover.
- Colour never carries meaning alone: always an icon and a word too.

## Behaviour

- Times: "4 min ago", with the exact Indian time on hover (`src/lib/time.ts`, Asia/Kolkata).
- Every screen handles loading, empty, error and lost connection states.
- New SOS never open a modal or take focus.
- Respect `prefers-reduced-motion`.
- Keyboard shortcuts are off while typing in a field (except Esc and Ctrl/⌘+Enter).

## Stack

React + TypeScript + Vite, Tailwind CSS v4, Firebase JS SDK (Auth, Firestore), MapLibre GL JS with
OpenFreeMap tiles, `@tanstack/react-virtual` for long lists, Vitest.

## Commands (run in `pukaar_web/`)

```
npm start            # emulators + seed + dashboard, all in one (http://localhost:5173)
npm run emulators    # just the Firebase emulators (Auth, Firestore, Functions)
npm run seed         # reload fake data: 20 SOS. seed:150 for many, seed:empty for none
npm run typecheck
npm test
npm run build
```

Seeded logins (password `pukaar123`): `viewer@`, `responder@`, `admin@`, `norole@pukaar.test`.

## Working style

- One screen at a time, then check it in the browser with the seed data.
- Build screens against the seed data first, then handle the real-data edge cases.
- If a screen needs a server change, add it to `../server/functions` with tests, following its
  style, and say so.
