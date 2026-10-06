# pukaar_web: Pukaar rescuer dashboard

Web app for district control rooms: every SOS on a map and in a priority list, mark attended and
resolved, read the Disaster Relief chat, send official broadcasts. Plan: [`../docs/dashboard-plan.md`](../docs/dashboard-plan.md).
Design: [`design_handoff/README.md`](design_handoff/README.md).

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
│   └── lib/               Firebase, API client, types, priority sort, time formatting
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
