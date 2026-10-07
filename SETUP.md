# Pukaar Android: setup

This package adds the Pukaar theme, fonts, icons and logo to a fork of bitchat-android. Follow the steps in order.

## What's in this package

| Path | What it is |
|---|---|
| `app/src/main/java/app/pukaar/ui/theme/PukaarColors.kt` | Dark, light and Sunlight colour schemes, plus the five status colours (from the design handoff) |
| `.../Type.kt` | Mukta typography, all Material 3 sizes, plus the custom sizes (countdown, compass, SOS button) |
| `.../Theme.kt` | `PukaarTheme`, shapes, spacing and touch-target sizes, and `MaterialTheme.status` |
| `.../Icons.kt` | `PukaarIcon` and `Sym`: the 80 Material Symbols Rounded icons used in the designs |
| `.../ThemeGallery.kt` | Preview-only screen to check that everything is wired up |
| `app/src/main/res/font/` | Mukta (4 weights) and two small icon fonts (outlined and filled), all bundled so they work offline |
| `app/src/main/res/drawable/ic_pukaar_mark.xml` | The logo mark. Tint it with `primary` |
| `.../ic_launcher_background.xml`, `_foreground.xml`, `_monochrome.xml` | The Pukaar adaptive app icon. These replace bitchat's files of the same name |
| `.../ic_stat_pukaar.xml` | Notification small icon |
| `.../ic_mesh.xml`, `ic_radio.xml` | The two custom icons from the designs |
| `tools/make_icon_fonts.sh` | Rebuilds the icon fonts when you need a new icon |
| `licenses/` | Mukta (OFL) and Material Symbols (Apache 2.0). Credit both in Settings > About |

## 1. Install Android Studio

Install the latest stable Android Studio. bitchat-android uses a recent Android SDK (compile SDK 37) and Gradle plugin, so accept any update prompts during the first sync.

## 2. Fork, clone and run bitchat unchanged

1. Fork `permissionlesstech/bitchat-android` on GitHub into your team account and rename the fork `pukaar-android`.
2. Clone it and open the folder in Android Studio. Wait for Gradle sync to finish (the first one downloads a lot).
3. Run it on two or three real phones (Developer options > USB debugging, or Wireless debugging). Emulators can't test Bluetooth.
4. Check that phones find each other and chat. **Do this before changing anything**, so you know the base works.

The repo also contains a `wear` module for smartwatches. Ignore it; run the `app` configuration.

## 3. Branches

- `main`: kept close to upstream bitchat, so you can pull its fixes later.
- `develop`: Pukaar work. Everyone branches from here (`feature/home-screen`, `feature/sos-countdown`, and so on) and merges back with pull requests.

## 4. Rebrand

In `app/build.gradle.kts`, change:

```kotlin
applicationId = "app.pukaar"
```

Leave `namespace = "com.bitchat.android"` as it is for now. Changing it touches every file and makes pulling bitchat updates painful. New Pukaar code lives in the `app.pukaar` package and imports `com.bitchat.android.R`.

In `app/src/main/res/values/strings.xml`, set `app_name` to `Pukaar`. In `values-hi/strings.xml`, set it to `पुकार`. Search the other `values-*` folders for `app_name` too.

## 5. Add this package

Copy the `app/` folder from this package over the repo's `app/` folder. It only adds new files, except the three `ic_launcher_*` drawables, which it replaces on purpose. Also copy `tools/` and `licenses/` to the repo root.

## 6. Switch the app to the Pukaar theme

In `app/src/main/java/com/bitchat/android/MainActivity.kt`:

```kotlin
// remove
import com.bitchat.android.ui.theme.BitchatTheme
// add
import app.pukaar.ui.theme.PukaarTheme
```

and inside `setContent { ... }` replace `BitchatTheme {` with `PukaarTheme {`.

`PukaarTheme` also provides bitchat's palette, so bitchat's existing screens keep working while you replace them one by one. They will now use Pukaar colours and Mukta.

## 7. Check it worked

1. Sync and build.
2. Open `ThemeGallery.kt` and look at the Preview pane. You should see the logo, Hindi and English text in Mukta, the status colours, icons and the SOS button, in Dark, Light and Sunlight.
3. Run on a phone. The launcher should show the Pukaar icon and name.

## 8. Put the design handoff in the repo

Create `docs/design/` and add:
- `Pukaar App Screens.html` (the standalone review file)
- `Pukaar App v2.dc.html` and `support.js` (editable source)
- the handoff `README.md`, renamed `HANDOFF.md`

and add `design-brief.md`, `requirements.md`, `screens.md` and `design-tokens.md` to `docs/`. Everyone, and Claude Code, can then read the designs from inside the repo.

## 9. Where new code goes

```
app/src/main/java/app/pukaar/
├── ui/theme/          (this package)
├── ui/components/     SosButton, ConnectionPill, DeliveryState, RippleField, HopsPath, Compass
├── ui/screens/        home/, sos/, chat/, network/, map/, guides/, calling/, settings/, onboarding/
├── ui/PukaarNavHost.kt
├── sos/               SOS packet, retries, acknowledgements
└── gateway/           upload to Firebase when online
```

## 10. Build order

1. **Components**, each with `@Preview`s in dark and light: SosButton, ConnectionPill, DeliveryState. The handoff README ("Custom components") has exact sizes.
2. **Home (2h)** with fake data, then **SOS countdown (2k)** and **SOS status (2i)**.
3. **Navigation**: a `PukaarNavHost` with the four bottom tabs, set as the root in `MainActivity`.
4. **Chat (2l)**: restyle around bitchat's existing chat logic and view model rather than rewriting the messaging.
5. Then Network, Map and Direction, Calling, Guides, Settings, Onboarding, and the system surfaces.

Build screens with fake data first and connect real data after. That way designers and mesh developers aren't blocked on each other.

## Using icons

```kotlin
PukaarIcon(Sym.nightShelter, contentDescription = "Shelter")
PukaarIcon(Sym.home, contentDescription = null, filled = true)   // selected nav item
Icon(painterResource(R.drawable.ic_mesh), "Mesh", tint = MaterialTheme.status.mesh.main)
```

To add an icon from fonts.google.com/icons: add its name to `tools/icon-names.txt`, run `tools/make_icon_fonts.sh` (needs `pip install fonttools`), and paste the constant it prints into `Sym`.

## Using status colours

```kotlin
val s = MaterialTheme.status
Box(Modifier.background(s.mesh.container)) { Text("Connected to 4 phones", color = s.mesh.onContainer) }
Button(colors = ButtonDefaults.buttonColors(containerColor = s.sosFill, contentColor = s.onSosFill)) { ... }
```

Never use `colorScheme.error` for SOS, and never use SOS red for anything else.

## Signing and releases

**Debug builds** are signed with the shared Pukaar debug key in `app/pukaar-debug.p12` (password `android`). It is not secret. Because everyone uses the same key, a debug APK built on any teammate's laptop can update another teammate's install. If a phone has a Pukaar build signed with an older key, uninstall it once.

**Release builds** use your own private key, which must never be committed. Create it once and keep a backup, because every future update must be signed with the same key:

```
keytool -genkeypair -keystore pukaar-release.p12 -storetype PKCS12 -alias pukaar -keyalg RSA -keysize 4096 -validity 10950 -dname "CN=Pukaar, O=<your team>, C=IN"
```

Put these in `~/.gradle/gradle.properties` (your user folder, not the repo):

```
PUKAAR_RELEASE_STORE_FILE=C:/path/to/pukaar-release.p12
PUKAAR_RELEASE_STORE_PASSWORD=...
PUKAAR_RELEASE_KEY_ALIAS=pukaar
PUKAAR_RELEASE_KEY_PASSWORD=...
PUKAAR_GITHUB_RELEASE_CERT_SHA256=<SHA-256 of the release certificate>
```

Get the SHA-256 with `keytool -list -v -keystore pukaar-release.p12`. Then `./gradlew :app:assembleRelease` produces a signed APK.

**In-app updates** check the latest release of `github.com/ayusharma03/pukaar_android`. Upload the signed universal APK to a GitHub release with the name `pukaar-android-universal.apk`, and tag it `v<versionName>`. The app only installs updates signed by the certificate in `PUKAAR_GITHUB_RELEASE_CERT_SHA256`.

**Mesh compatibility:** Pukaar keeps bitchat's Bluetooth service UUID, so Pukaar and bitchat phones relay each other's messages. bitchat's private-chat and location-chat notifications are switched off in Pukaar, because Pukaar has no screens for them.
