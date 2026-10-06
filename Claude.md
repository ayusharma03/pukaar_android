# Pukaar (पुकार): project guide for Claude

Pukaar is an Android app for disaster areas. People send messages and SOS alerts with no internet, no mobile data and no SIM. Messages hop phone to phone over Bluetooth mesh until any phone with internet uploads them to a rescuer dashboard. LoRa radio through Meshtastic is the last fallback. This is a hackathon build.

This repo is a fork of bitchat-android, which provides the Bluetooth mesh.

## Where things are

| Need | Read |
|---|---|
| How to set up and the build order | `SETUP.md` |
| What the app must do | `docs/requirements.md` |
| Users, principles, tone of voice | `docs/design-brief.md` |
| Every screen and its states | `docs/screens.md` |
| Colours, type, spacing, sizes | `docs/design-tokens.md` |
| Exact look of each screen | `docs/design/HANDOFF.md` (screen ids 2a to 2y) and `docs/design/Pukaar App v2.dc.html` |
| Message formats, fallback rules | `docs/protocol.md` |
| System design | `docs/architecture.md` |
| Rescuer dashboard (web, separate project) | `pukaar_web/CLAUDE.md`, `docs/dashboard-plan.md` |

`docs/design/Pukaar App Screens.html` is large (about 1.8 MB). Don't read it whole; search it or use the v2 file instead.

## Stack

Kotlin, Jetpack Compose, Material 3, Navigation Compose. Backend is Firebase (Firestore, Cloud Functions, Auth). Maps are MapLibre Native. SMS via Twilio.

## Rules

- **New Pukaar code goes in the `app.pukaar` package** (`app/src/main/java/app/pukaar/`). Import resources from `com.bitchat.android.R`.
- **Don't change `namespace = "com.bitchat.android"`** or rename bitchat packages. We want to keep pulling bitchat updates.
- **Change bitchat's own files only when needed,** and keep those changes small. Say which bitchat files you changed and why.
- **Ask before deleting** any bitchat feature or file.
- **Use the theme, never hard-coded values:** colours from `MaterialTheme.colorScheme` and `MaterialTheme.status`, text from `MaterialTheme.typography` or `PukaarTextStyles`, sizes from `PukaarDimens`.
- **SOS red (`MaterialTheme.status.sosFill`) is only for SOS.** Never use `colorScheme.error` for SOS, and never use SOS red for anything else.
- **Icons:** `PukaarIcon(Sym.X, contentDescription)`. Custom icons: `R.drawable.ic_mesh`, `ic_radio`, `ic_pukaar_mark`. Primary actions always have a text label too.
- **All user-facing text goes in string resources,** with English in `values/strings.xml` and Hindi in `values-hi/strings.xml`. Prefix new keys with `pk_`. Mark Hindi strings you write as needing review by a native speaker.
- **Layouts must handle long Hindi text and 1.3× font scale.** No fixed-height text boxes. No all-caps.
- **Touch targets:** at least 48 dp. Primary buttons 56 dp.
- **Every component and screen gets `@Preview`s** in dark and light, at 360 × 800 dp.
- **Build screens with fake data first,** using simple state classes, then connect real data.
- **Dark is the default theme.** Dynamic colour stays off.
- Bluetooth can't be tested on emulators. Tell me when something needs testing on real phones.

## Commands

```
./gradlew :app:assembleDebug      # build
./gradlew :app:lint               # lint
./gradlew :app:testDebugUnitTest  # unit tests
```

The `wear` module is bitchat's smartwatch app. Ignore it.

`pukaar_web/` is the rescuer dashboard, a separate web project with its own rules (`pukaar_web/CLAUDE.md`), CI workflow and deploy. Android work never needs to touch it, and the Android rules above (Compose, `pk_` strings, previews) don't apply there.

## Working style

- Work in small steps: one component or one screen at a time, then build.
- Before writing a screen, read its section in `HANDOFF.md` and `screens.md`, and list what you're going to build.
- When the designs and the docs disagree, follow `HANDOFF.md` for visuals and `screens.md` and `requirements.md` for behaviour, and tell me about the conflict.