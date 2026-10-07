# Handoff: Pukaar Android app

## Overview
Pukaar lets people in a disaster send an SOS and talk to each other when there is no network. Messages pass phone to phone over Bluetooth mesh and LoRa radio until a phone with internet uploads them. This package covers every screen of the Android app (not the rescuer dashboard), plus the logo, app icon and Android system surfaces.

The product docs are in `docs/`: `design-brief.md`, `requirements.md`, `screens.md` and `design-tokens.md`. They are the source of truth for behaviour. This README describes what the designs add on top of them.

## About the design files
The files in `design/` are **design references made in HTML**. They show the intended look and content. They are not production code to copy. Recreate them in **Jetpack Compose with Material 3**, which is the stack named in `design-tokens.md`, using Compose's own components and patterns.

- `design/Pukaar App Screens.html` is a standalone file that works offline. Open it in any browser. This is the easiest one to review.
- `design/Pukaar App v2.dc.html` is the editable source. It needs `support.js` next to it.
- `design/Pukaar App v1 (explorations).dc.html` holds the first round of explorations. Home directions 1a, 1b and 1c are there. **1c was chosen.**

Each screen has an id badge (2a, 2b…). This README uses those ids.

## Fidelity
**High fidelity.** Colours, type, spacing, radii and copy are final for English, apart from the items under "Open items". Frames are 360 × 800 dp, and 1 CSS px in the files equals 1 dp. Long screens (2i SOS status, 2r calling, 2u checklist, 2v settings, 2w contacts) are drawn at their full scroll height.

## Global rules (apply on every screen)
- **Theme:** dark by default. Light and "Sunlight" (light high contrast) are also available. Dynamic colour is **off**.
- **SOS red is reserved.** `sosFill #D32F2F` with white text is used only on SOS controls, the countdown and SOS cards. Form errors use `error`, never SOS red.
- **Connection status** appears at the top of every main screen as a pill (`meshContainer` background, mesh icon, "Connected to 4 phones"). See the five states below.
- **SOS is always reachable:** the big button on Home, a red "SOS" pill in the top app bar on Chat and Guides, and a red "SOS" button above the bottom sheet on Map.
- **Words and icons together.** Primary actions always have a text label.
- **Touch targets:** at least 48 dp. Primary buttons are 56 dp. The SOS countdown Cancel button is 72 dp. The Home SOS button is 160 dp across.
- **Text:** body text is at least 16 sp. Never use fixed-height text boxes, because Hindi labels run 30 to 50% longer. Layouts must work at 1.3× font scale. No all-caps anywhere.
- **Depth** comes from `surfaceContainer*` levels, not shadows. The only glow is on the SOS button: `0 0 32dp` at `rgba(211,47,47,.45)`.

## Design tokens (as used in the designs)
The full tables are in `docs/design-tokens.md`. The Compose colour schemes are in `compose/PukaarColors.kt`.

Short names used in the HTML → Material 3 roles:
`--p` primary · `--op` onPrimary · `--pc` primaryContainer · `--opc` onPrimaryContainer · `--sc` secondaryContainer · `--osc2` onSecondaryContainer · `--bg` surface/background · `--on` onSurface · `--onv` onSurfaceVariant · `--ol` outline · `--olv` outlineVariant · `--c0…--c4` surfaceContainerLowest…Highest · `--mesh/--meshc/--omesh` mesh / meshContainer / onMeshContainer · `--radio…`, `--ok…` (confirmed), `--warn…`, `--sos…` likewise.

- **Font:** Mukta 400/500/600/700, used for everything.
- **Icons:** Material Symbols Rounded, weight 400, opsz 24. Fill 0 by default; fill 1 for the selected nav item and active states.
- **Radii:** chips 8, small cards 12, cards and tiles 16, sheets 28 (top corners), pills and buttons fully rounded, phone frame 24 (frame only).
- **Spacing:** 4 dp grid. Screen side margins 16, card padding 14–16, gap between cards 12.

Type sizes used:
| Use | Size/line (sp) | Weight |
|---|---|---|
| Top app bar title | 22/28 | 600 |
| Screen title (onboarding H1) | 28/36 | 600 |
| Large status ("Help has been notified") | 36/44 | 400 |
| Compass distance | 56/64 | 500 |
| Countdown number | 112/112 | 600 |
| Card / list title | 16–18/22–24 | 600 |
| Body | 16/24 | 400 |
| Secondary | 14/20 | 400 |
| Caption / timestamp | 12–13/16–18 | 400 |
| Buttons | 14–16 | 600 |
| Nav labels | 12/16 | 600 |

## Custom components
1. **SOS button (Home):** a 160 dp circle in `#D32F2F` with "SOS" (44/48, 700, white) and "Tap for help" (15/20, 500) under it. Two halo discs sit behind it: 196 dp at 13% red and 232 dp at 7% red. The halos pulse slowly, about 2 s per cycle, and show static when "Remove animations" is on.
2. **Ripple field (Home 2h):** a dashed ring in `mesh` colour, 300 dp across (1.5 dp stroke, 70% opacity), around the SOS button. Each nearby phone is a 22 dp node (`meshContainer` fill, 2 dp `mesh` border, smartphone icon 14) placed on that ring. The legend under the Home frame shows how the ring changes for each state:
   - **Online:** solid `confirmed` ring.
   - **Mesh:** dashed `mesh` ring with a node for each phone.
   - **Radio:** solid `radio` ring with an antenna badge at the top.
   - **Isolated:** dashed `warning` ring with no nodes.
   - **Gateway:** solid `confirmed` ring with a "+3" badge.
3. **Connection pill:** 32–40 dp tall and fully rounded, with an icon and a label. The colour pairs are in `design-tokens.md` under "Where status colours apply".
4. **Delivery state** under your own chat bubbles: an icon plus words (13 sp, 600), for example "Help notified · 4:09 pm" in `confirmed` or "Relayed through 3 phones" in `mesh`.
5. **Hops path (SOS status 2i):** 44 dp circular nodes joined by 2 dp dotted lines: Your phone (primary container) → 2 phones (mesh) → Phone with internet (confirmed) → Rescuers (confirmed).
6. **Compass (2q):** a 280 dp dial on `surfaceContainerLow` with ticks every 5° (long ticks at 30° and 90°) and N at the top in `onSurface`. The arrow is a kite shape: front half `primary`, back half `primaryContainer`. It rotates to the bearing of the shelter.
7. **Custom icons:** inline SVGs on a 24 dp grid with 2 dp rounded strokes. Mesh is three dots joined by arcs; Radio is an antenna with waves. The source for both is in `design/Pukaar App v2.dc.html`; search for `M6.5 13.5` (mesh) or `M12 10v11` (radio).

## Screens
**Onboarding** (dots at the top show step n of 7, with a back button):
- **2m 1.1 Language:** large mark, then two 88 dp language buttons (हिन्दी, English), then "More languages coming".
- **2c 1.2 Welcome:** illustration area (placeholder) and two lines of copy, then Continue.
- **2n 1.3 How it works:** three cards with hop diagrams, plus a note that rescuers can read the Disaster Relief chat (NFR-9).
- **2o 1.4 Permissions:** one card per permission. Allowed shows a green "Allowed". Denied shows a warning box explaining what breaks, with "Try again".
- **2d 1.5 Your details:** outlined text fields, blood group chips (single choice) and medical notes.
- **2e 1.6 Emergency contacts:** contact cards, "Pick from contacts" (tonal) and "Add manually" (outlined) buttons, "2 of 5 added", then Skip and Continue.
- **2f 1.7 Offline area:** detected region with a map preview, the three downloads with their sizes, then "Download 153 MB" and "Later".
- **2g 1.8 SOS setup:** Quick Settings tile, widget and shake (with a switch), then Practice SOS (runs the countdown and sends nothing), then Finish.

**Core:**
- **2h Home:** header (mark and "Pukaar", Settings) → ripple field with SOS → mesh status line → four tiles (Chat with an unread badge, Map, Call, Guides) at least 88 dp tall → battery card in warning colours → bottom nav (Home, Chat, Map, Guides).
- **2k SOS countdown:** red full screen. The ring shows the time left (8 dp stroke). Under it: people stepper (48 dp buttons), flag chips (selected = white fill with red text and a check), and "Add message", which pauses the countdown. The Cancel button is 72 dp, white, radius 28.
- **2i SOS status:** state hero card in confirmed colours → hops path → timeline → what was sent (with "Update details") → which family members were told → tip → "I'm safe now" (confirmed fill, kept at the bottom while scrolling). Hero copy for each state comes from the tone-of-voice table in `design-brief.md`.
- **2l Disaster Relief chat:** app bar with the reachable phone count, info icon and SOS pill → connection pill → feed: official message (primary container with a "verified" badge), others' bubbles (surfaceContainerHigh), SOS card (sosContainer), your own bubbles (primary) with their delivery state → quick reply chips → composer (attach location, text field, "Send" with label).
- **2j Network:** a 200 dp ring visual with the peer count → rows for Internet, Bluetooth mesh, Radio device and Helping others, each with an on/off tag → messages waiting → "Pair a radio device" (radio container colours).

**Map:**
- **2p Map:** offline map (custom style: surface tones, water in `primaryContainer` at 60%, roads in surfaceContainerHigh/Highest) → overlay with pill, search and layer chips → place pins (shelter = night_shelter, hospital, police; the selected pin is 44 dp in primary with a label) → my-location button and SOS button → bottom sheet with the nearest shelter, "Show direction" and "Call".
- **2q Direction:** destination card → compass → distance → straight-line warning → mini map → Stop. The screen stays on at reduced brightness.

**Help:**
- **2r Emergency calling:** no-signal warning box with "Send SOS instead" → grid of six 96 dp call cards (112 highlighted in primary container) → emergency contacts with Call buttons.
- **2s Guides:** disaster cards → checklist cards with progress bars.
- **2t Guide detail:** Before/During/After tabs, numbered steps with an illustration slot for each, source line at the bottom, and a "Large text" control.
- **2u Checklist:** progress bar → grouped items with tick boxes (ticked = `confirmed` fill) → Reset.

**Settings:**
- **2v Settings:** language, profile, contacts, SOS triggers (shake switch, sensitivity choice, countdown length), offline data, radio, battery saver, theme (Dark / Light / Sunlight / System), about.
- **2w Emergency contacts:** contact cards with test status → add → preview of the exact SMS.

**System surfaces:**
- **2x Lock screen:** 2 × 2 widget (SOS and connection) → active SOS notification with an "I'm safe" action → official message notification → persistent mesh notification with an "SOS" action.
- **2y Quick Settings and home screen:** SOS tile, small 2 × 1 and medium 2 × 2 widgets, and the app icon on the launcher.

**Brand:**
- **2a Logo:** the dot is the person calling, the open rings are the call spreading, and the node on the outer ring is the next phone. `assets/pukaar-mark.svg` uses `currentColor`; it is `primary` on the surface colour.
- **2b App icon:** adaptive icon with background `#324478` and foreground mark `#DBE1FF`, kept inside the 66 dp safe zone. The themed (single-colour) icon and the notification icon (white on transparent) follow the same mark.

## Interactions and state
- **Navigation map:** `screens.md` → "App navigation". The bottom bar has four tabs. Settings and Network open from Home; Calling and the SOS countdown open from Home.
- **SOS:** tap SOS → 5 s countdown (length can be changed) → SOS status. Cancel returns to where the user was. "Add message" pauses the countdown. People count and flags can be changed without stopping it.
- **Delivery states:** Sending (neutral) → Relayed (mesh) or Sent by radio (radio) → Help notified (confirmed) → Rescuer attending (confirmed) → Resolved (confirmed container). Fade between them in 200–300 ms.
- **Connection state** drives the pill, the Home ring and the chat composer. When isolated with a radio paired, Send becomes "Send by radio".
- **Suggested state:** `connection {type, peers, reachable, gatewayHelping}`, `sos {active, state, timeline[], path[], people, flags[], message, location, contacts[]}`, `messages[]` (each with `deliveryState`), `profile`, `contacts[≤5]`, `offlineRegions[]`, `radio {paired, signal}`, `settings {theme, shake, sensitivity, countdown, batterySaver, language}`, `checklists`.
- **Motion:** only the SOS halos animate continuously. Use Material 3 default screen transitions.

## Open items
- **Hindi:** every screen still needs a Hindi version, and the Hindi copy must be checked by a native speaker.
- **Guide steps:** the wording in 2t is a sample. Replace it with the official NDMA text.
- **Illustrations:** onboarding 2c and the guide steps have placeholders. They should be drawn in the brand style, with Indian settings.
- **SOS status states:** still searching, rescuer attending, resolved and cancelled are specified in `screens.md` but not drawn yet.
- **Sunlight mode:** only specified by its colour scheme; no screens are drawn in it yet.
- **Dashboard:** the rescuer dashboard (D1–D5) is not part of this package.

## Files
- `design/Pukaar App Screens.html`: standalone, offline review file.
- `design/Pukaar App v2.dc.html` and `design/support.js`: editable design source.
- `design/Pukaar App v1 (explorations).dc.html`: Home explorations 1a–1c.
- `assets/pukaar-mark.svg`: logo mark.
- `compose/PukaarColors.kt`: light and dark `ColorScheme`s plus the status colours.
- `docs/*.md`: the original product docs.
