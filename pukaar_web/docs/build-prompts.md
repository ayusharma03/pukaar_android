Prompt 1: set up and plan (no UI yet)
We're building the Pukaar rescuer dashboard: a web app for district control
rooms. Read these first, carefully:
- CLAUDE.md (project rules)
- docs/dashboard-plan.md (the plan for this work: follow it)
- docs/screens.md, sections D1 to D5 (what each screen does)
- docs/design-tokens.md (colours, Mukta font, status colours, dashboard sizes)
- docs/protocol.md section 2 (the gateway contract)
- server/functions/ (the existing Cloud Functions, routes, tests)
- the Firestore security rules and firebase.json

Then:
1. Create pukaar_web/ at the repo root (was dashboard/): React + TypeScript + Vite, Firebase JS
   SDK (Auth, Firestore), MapLibre GL JS with OpenFreeMap tiles, Tailwind.
   Wire it into the existing firebase.json for Hosting and the Emulator Suite.
2. Create pukaar_web/CLAUDE.md with the rules for this part of the project:
   - Reads come from Firestore with live listeners (onSnapshot).
   - Writes NEVER go to Firestore directly. Status changes and broadcasts go
     through the Cloud Function routes with the user's Firebase ID token
     (Authorization: Bearer). The server signs what phones receive.
   - Never put service-account keys or secrets in the dashboard. Firebase web
     config comes from environment variables (.env.local, gitignored), with a
     .env.example committed.
   - Colours, font and status colours come from design-tokens.md as Tailwind
     theme tokens. SOS red is only for SOS. Light and dark themes. Mukta font,
     bundled locally.
   - Design frame 1440 x 900, must work at 1280 wide.
   - Show times as "4 min ago" plus the exact time on hover; Indian time zone.
   - Every screen handles loading, empty, error and "lost connection" states.
3. Set up a seed script that loads realistic fake data into the Firestore
   emulator: about 20 SOS in different states, flags, battery levels and
   arrival paths (direct and mesh), spread around one district, plus chat
   messages and two users (one viewer, one responder) with role claims.
   Use Indian names and places.
4. Don't build any screens yet. Show me the folder structure, the Tailwind
   tokens you created, and how to run everything locally (emulators + seed +
   dashboard) in one or two commands. Tell me anything in the plan or server
   code that looks wrong or missing before we build.
Then one prompt per screen

Wait for each to work in the browser before sending the next.

2. Login (D1)

Build D1 Login from screens.md: email and password with Firebase Auth,
Pukaar branding with "Rescuer dashboard", and the user's role shown after
login. Users without a viewer or responder role see a clear "no access"
message. Protect every other route.

3. Live operations (D2), the main screen

Build D2 Live operations from screens.md and docs/dashboard-plan.md:
- MapLibre map filling most of the screen, SOS pins coloured by status
  (new = SOS, attended = warning, resolved = confirmed), clustered when
  zoomed out; located chat messages as smaller markers.
- Incident list on the right (about 400px), sorted by the priority order in
  the plan. Each row: status, time since sent, people, flags as icon + label,
  battery, area, and how it arrived (direct or mesh).
- Top bar: counts by status, filters (status, time, area), search, theme
  toggle, user menu.
- New SOS: subtle highlight and optional sound, never a blocking popup.
- Clicking a pin or row selects the same incident in both.
Test it with the seed data, including the no-incidents and 100+ incidents
cases (add a seed option for 150 SOS).

4. SOS detail (D3)

Build D3 SOS detail as a side panel: status with Mark attended / Mark
resolved buttons (only for responders, calling the Cloud Function route),
person and contact details, location with accuracy and a Google Maps link,
mini map, needs, battery, times, arrival path and relaying gateways, SMS
status for family, "I'm safe" if safeAt is set, full history, and notes.
SOS that arrived only through the mesh may have no contact details: show
that clearly instead of empty fields. Show errors from the API to the user.

5. Disaster Relief feed (D4)

Build D4: the Disaster Relief chat from `messages`, newest first, with
location chips that jump to the map, and official broadcasts styled with a
badge. Responders get a composer that calls the broadcasts route. Warn
before sending that the message goes to everyone in the area.

6. Team (D5)

Build D5 Team and accounts. Setting roles needs a server-side admin
function: add it to server/functions with tests, following the existing
code style, callable only by an admin claim. Then build the screen: list
staff with roles, invite by email, change role, remove access. Tell me
how to make the first admin.

7. End-to-end test

Write docs/dashboard-testing.md: how to run the emulators, point a debug
build of the Android app at the Functions emulator from a real phone on the
same Wi-Fi, send an SOS with no internet through a second phone, and watch
it appear on the dashboard and get marked attended. Then check the
dashboard against screens.md D1 to D5 and list anything missing.