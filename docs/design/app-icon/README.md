# Pukaar app icon (option 1b, P-ripple)

The caller dot sits inside a ring that, with the stem, forms a P. The node past the ring is the next phone that picks up the call.
Background #324478, foreground #DBE1FF (from design-tokens.md).

## Android (drop `android/res` into `app/src/main/res`)
- `mipmap-anydpi-v26/ic_launcher.xml` and `ic_launcher_round.xml`: adaptive icon with background, foreground and monochrome (themed icon, Android 13+).
- `drawable/ic_launcher_foreground.xml`: 108 dp vector, mark kept inside the 66 dp safe zone.
- `drawable/ic_launcher_monochrome.xml`: themed icon layer.
- `values/ic_launcher_background.xml`: background colour.
- `mipmap-*/ic_launcher.png` and `ic_launcher_round.png`: fallback PNGs for older Android, 48 dp at each density.
- `drawable/ic_stat_pukaar.xml` (vector) and `drawable-*/ic_stat_pukaar.png`: white notification icon, 24 dp. Use it with `setSmallIcon(R.drawable.ic_stat_pukaar)`.

## Other
- `play-store/pukaar-icon-512.png`: full-bleed square. Google Play applies its own mask.
- `svg/`: source vectors: foreground, background, full bleed, the three masks, monochrome, notification, and `pukaar-mark.svg` (uses currentColor, for in-app use).
- `png/`: 1024 px previews of each mask, plus the 432 px foreground layer.
