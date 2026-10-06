# pukaar_web: Pukaar rescuer dashboard

Web app for district control rooms: every SOS on a map and in a priority list, mark attended and
resolved, read the Disaster Relief chat, send official broadcasts. Plan: [`../docs/dashboard-plan.md`](../docs/dashboard-plan.md).
Design: [`design_handoff/README.md`](design_handoff/README.md).

## What's built

| Screen | State |
|---|---|
| D1 Login | Done: sign-in, wrong password, signed in without a role, forgot password |
| D2 Live operations | Done: map with pins and clusters, priority list, counts, filters, search, live indicator, lost connection, empty and filtered-to-nothing states, new-SOS chime, toast and highlight |
| D3 SOS detail | Done: every section, attend form with recent teams, resolve, reopen, back to new, Undo (Z), offline queue, viewer mode, mesh-only notice |
| D4 Relief feed | Done: chat and official broadcasts, location chips, composer with byte counter and confirmation |
| D5 Team | Placeholder: needs the admin function on the server |

Keyboard: `J`/`K` move, `Enter` open, `Esc` close, `A` attend, `R` resolve, `Z` undo, `O` open newest, `/` search, `F` feed, `M` sound, `?` all shortcuts.

## Run it locally

Needs Node 22+ and Java 21 (for the Firebase emulators). First time only:

```sh
npm --prefix ../server/functions install
npm install
```

Then:

```sh
npm start
```

That starts the Firebase emulators (Auth, Firestore, Functions with the real server code), loads
fake data, and serves the dashboard at http://localhost:5173. Emulator UI: http://127.0.0.1:4000.
Everything stays on your laptop (project `demo-pukaar`).

Sign in with password `pukaar123` as `viewer@pukaar.test`, `responder@pukaar.test`,
`admin@pukaar.test` or `norole@pukaar.test`.

Reload the data while it runs: `npm run seed` (20 SOS), `npm run seed:150`, `npm run seed:empty`.

## Layout

```
pukaar_web/
├── src/
│   ├── theme/tokens.css   design tokens as CSS variables (dark + light)
│   ├── index.css          Tailwind theme mapped onto the tokens
│   ├── auth/              sign-in, role, D1
│   ├── ops/               D2 to D4: map, list, detail panel, relief feed, live data
│   ├── team/              D5 (placeholder)
│   ├── ui/                atoms, theme, toasts
│   └── lib/               Firebase, API client, types, filters, priority sort, formatting
├── scripts/
│   ├── seed.mjs           fake data for the emulators
│   └── local-secrets.mjs  local server keys for the Functions emulator
├── design_handoff/        the dashboard design (reference only, not shipped)
└── docs/build-prompts.md  build order, one step per screen
```

The server (`../server/`) is shared with the Android app: the dashboard reads Firestore and writes
through its Cloud Function routes. `../server/firebase.json` holds the emulator config; this folder's own
`firebase.json` is Hosting only, so the dashboard deploys on its own.

## Deploy

Hosting deploys separately from the Android app, from the `Pukaar web` workflow (tag `web-v*` or run
it by hand), or locally with `npm run deploy` after `firebase login` and a `.env.local` for the real
project.
