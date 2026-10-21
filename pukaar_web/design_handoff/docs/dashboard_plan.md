# Rescuer dashboard: plan

A short brief for building the control-room dashboard (screens D1 to D5 in [screens.md](screens.md)) in its own VS Code project. It covers what already exists, how the dashboard fits in, the suggested stack, and where Nostr stands.

## 1. What already exists

**Android app** (`app/`, Kotlin, Jetpack Compose, a fork of bitchat-android):

- **SOS:** a 5-second countdown, then people count, needs, message, location and battery are sent as a small packet (at most 200 bytes) over the Bluetooth mesh. The app resends when new phones come in range, texts emergency contacts directly when there is signal, and uploads straight to the server when it has internet.
- **Disaster Relief chat:** one public group chat over the mesh. Messages can carry a location. Official messages from the control room show with a badge.
- **Gateway:** any phone with internet uploads what it has heard (SOS, contacts, chat, safe updates) to the server. It also brings the server's replies back into the mesh, so a person with no internet still sees "Help has been notified" and "A rescuer is on it".
- **Security:** status updates and official messages are signed by the server (Ed25519), and phones ignore anything unsigned. Emergency-contact numbers travel sealed to the server's key (X25519 and ChaCha20-Poly1305), so relaying phones can't read them.
- **Also built:** offline map (MapLibre with OpenFreeMap tiles), nearby SOS on the map with directions, emergency calling, guides and checklists, Hindi and English, a Quick Settings tile, widgets and shake-to-SOS.

**Server** (`server/functions/`, Firebase Cloud Functions on Node):

- Implements the gateway contract in [protocol.md](protocol.md) §2.
- Stores everything in Firestore: `sos/{id}`, `messages/{id}`, `broadcasts/{id}`.
- Texts family through Twilio once an SOS and its contacts are both in, and retries failed texts every 5 minutes.
- Has two responder routes (below) and 16 tests. `npm run local` runs it on a laptop.

## 2. How the dashboard fits in

```
Phones ──mesh──▶ Gateway phone ──HTTPS──▶ Cloud Function `api` ──▶ Firestore
                                                ▲                      │
                          status changes,       │                      │ live updates
                          broadcasts (signed)   │                      ▼
                                         Dashboard (browser) ◀── Firestore listeners
```

- **Reads come from Firestore directly**, using the Firebase JS SDK's live listeners (`onSnapshot`). New SOS appear within a second, with no polling. The Firestore rules already allow reads for signed-in users whose role claim is `viewer` or `responder`.
- **Writes go through the Cloud Function, never straight to Firestore.** The function signs what phones will receive and keeps the history. The dashboard calls it with the user's Firebase ID token in an `Authorization: Bearer <token>` header:
  - `POST /v1/admin/sos/{id}/status` with `{ "status": "attended" | "resolved", "by": "District Control Room" }` sends a status change back to the person (D3).
  - `POST /v1/admin/broadcasts` with `{ "from": "...", "text": "..." }` sends an official message into the Disaster Relief chat (D4).
- **Accounts (D1, D5)** use Firebase Auth with email and password. Roles are custom claims, set with the Admin SDK: `setCustomUserClaims(uid, { role: 'responder' })`. Viewers can read; only responders can change status or broadcast.

## 3. Suggested stack

| Need | Choice | Why |
|---|---|---|
| App | **React + TypeScript + Vite** | Fast to build in VS Code, and easy to hand around a team |
| Data and auth | **Firebase JS SDK** (Auth, Firestore) | Same project as the server; live listeners for free |
| Map | **MapLibre GL JS** with OpenFreeMap tiles | Same map engine and tiles as the app, no API key |
| UI | **Tailwind CSS** or MUI, using the Pukaar tokens from [design-tokens.md](design-tokens.md), with the Mukta font | Matches the app's look; SOS red stays reserved for SOS |
| Hosting | **Firebase Hosting** | One `firebase deploy` alongside the functions |
| Local dev | **Firebase Emulator Suite** (Auth, Firestore, Functions) | The whole stack runs offline on a laptop |

Suggested layout: `dashboard/` at the repo root, next to `server/`, so the Firebase project, rules and functions are shared.

## 4. Data the dashboard reads

`sos/{id}` (one document per SOS; the latest update wins):

| Field | Meaning |
|---|---|
| `id`, `seq` | SOS id; `seq` goes up with each update from the person |
| `lat`, `lon`, `accuracyM` | Location, or null if the phone had no fix |
| `time` | When the SOS started (Unix seconds) |
| `people`, `flags[]` | People with them; needs: `Injured`, `Trapped`, `NeedWater`, `NeedMedicine`, `ChildOrElderly` |
| `name`, `message`, `battery` | From the phone |
| `phone`, `bloodGroup`, `medicalNotes`, `contacts[]` | Only when the phone uploaded directly, or the sealed contacts packet arrived |
| `via[]`, `relayedBy[]` | How it arrived: `direct` or `mesh`, and the gateway phones that relayed it |
| `status`, `statusTime`, `by` | `new`, `attended` or `resolved`, when, and by whom |
| `smsSent`, `smsResults[]` | Whether family has been texted |
| `safeAt` | Set when the person tapped "I'm safe now" |
| `history[]` | Every status change, with time and who made it |

`messages/{id}` holds the chat (`sender`, `text`, `time`, optional `lat`/`lon`). `broadcasts/{id}` holds official messages sent from the dashboard.

Priority order for the incident list (screens.md D2): new before attended, then more people, then the Injured or Trapped flags, then low battery, then oldest first.

## 5. Build order

1. **Login (D1):** Firebase Auth email sign-in, then show the user's role.
2. **Live operations (D2):** map plus incident list from `sos`, with counts by status and filters by status, time and area.
3. **SOS detail (D3):** a side panel with Mark attended / Mark resolved (through the API), contact details, location with a Google Maps link, history and notes.
4. **Disaster Relief feed (D4):** `messages` newest first, plus a composer that calls the broadcasts route.
5. **Team (D5):** list users and set roles. This needs a small admin function, because custom claims can only be set server-side.

To test it end to end, run the emulator suite, build the app pointing at the Functions emulator, send an SOS, and watch it appear on the dashboard.

## 6. Nostr: should the dashboard use it?

**Short answer: not for v1. Use the HTTPS and Firestore path above.**

What bitchat uses Nostr for: messaging over the internet through public Nostr relays. That covers location ("geohash") channels, and private messages when two people aren't in Bluetooth range. Pukaar inherited that code, and it still starts in the background at app launch (Nostr relays over Tor), but no Pukaar screen uses it.

Why not build the dashboard on it:

- **No delivery guarantee or accountability.** Relays are run by strangers and can drop or delay events. For SOS we need a server we control that stores every SOS once, records who acted, and texts family.
- **Privacy.** Relays are public. Anything posted there (location, names, numbers) is readable by anyone, which is why the app already seals contact numbers before sending them anywhere.
- **Auth.** Roles, accounts and an audit trail come free with Firebase; on Nostr they'd all have to be built.
- **What we already have covers the hard case.** A phone with internet already uploads for everyone nearby (the gateway), so Nostr adds no reach the server path lacks.

Where Nostr could help later, as an extra and not a replacement:

- **A Nostr bridge.** A small server process subscribes to geohash channels for the affected district (for example the 5-character geohash covering Darbhanga) and feeds any Pukaar packets it sees into the same Cloud Function. That would catch SOS from phones that have internet but can't reach our server, for example if it's blocked. The packets are the same text format (`PKSOS1|…`), so the parsing already exists.
- **Mirroring official broadcasts** to the same geohash channel, so bitchat users on the internet see them too. They are already signed, so they can be verified.

**Decision for the app team:** because Pukaar doesn't use bitchat's Nostr features, we can switch the Nostr and Tor background runtime off in Pukaar to save battery and data. The Bluetooth mesh, which is what Pukaar relies on, doesn't depend on it. That's a small change and is listed as a follow-up.

## 7. Open items before the dashboard goes live

- Create the Firebase project (Blaze plan), deploy `server/`, and set the secrets: signing and sealing keys, plus Twilio (see `server/README.md`).
- Build the app with the deployed URL and the two public keys.
- Add rate limiting on the gateway routes. They are anonymous by design, so someone could post fake SOS.
- Agree on how the dashboard shows SOS that arrived only through other phones (`via: ["mesh"]`), since these won't have contact details unless the sealed contacts packet also arrived.
