# Design tokens

The visual foundation for Pukaar. Every screen, component and illustration uses these values. Read this alongside the [design brief](design-brief.md) and [screens](screens.md).

The base colour roles come from a Material Theme Builder export (Material 3). The five status colours were generated with the same Material 3 tonal method so they sit in the same system. The app is built in Jetpack Compose with Material 3, so designs should use Material 3 components and colour roles wherever possible.

## Summary

| Token group | Value |
|---|---|
| Design system | Material 3, dynamic colour off |
| Primary colour | Indigo blue, `#4A5C92` light, `#B4C5FF` dark |
| Status colours | SOS red, mesh teal, radio violet, confirmed green, warning amber |
| Font | Mukta for everything (supports Hindi and English) |
| Icons | Material Symbols Rounded, plus a few custom icons |
| Default theme | Dark |
| Other themes | Light, and a high-contrast light "Sunlight mode" |
| Base frame | 360 × 800 dp Android phone |

## Colour

### Rules

- **Dynamic colour is off.** The app never takes colours from the user's wallpaper. SOS red must always look the same.
- **Primary** is for main actions, the selected navigation item, links and brand moments.
- **Secondary** is for quieter controls: filter chips, secondary buttons, tags.
- **Tertiary is identical to primary in this export.** Don't use it as a separate accent. Use the status colours instead.
- **Error** is for form validation and failures only, never for SOS.
- **SOS red is reserved.** It appears only on the SOS button, the SOS countdown, active SOS status and SOS items in lists or maps. If red appears elsewhere, it stops meaning "emergency".
- **Surfaces carry depth.** Use the `surfaceContainer` levels for cards and sheets instead of shadows.
- **Every status colour is paired with an icon and words.** Colour alone never carries meaning.

### Status colours

### SOS

The SOS button, active SOS states and SOS cards only. Never for ordinary errors.

| Token | Light | Dark |
|---|---|---|
| `sos` | `#BA1A20` | `#FFB3AC` |
| `onSos` | `#FFFFFF` | `#680008` |
| `sosContainer` | `#FFDAD6` | `#930010` |
| `onSosContainer` | `#930010` | `#FFDAD6` |

### Mesh

Bluetooth mesh status: peer count, "Relayed", mesh icons.

| Token | Light | Dark |
|---|---|---|
| `mesh` | `#006A6A` | `#4CDADA` |
| `onMesh` | `#FFFFFF` | `#003737` |
| `meshContainer` | `#6FF7F6` | `#004F4F` |
| `onMeshContainer` | `#004F4F` | `#6FF7F6` |

### Radio

LoRa radio status: "Sent by radio", radio device, radio icons.

| Token | Light | Dark |
|---|---|---|
| `radio` | `#6F48B2` | `#D4BBFF` |
| `onRadio` | `#FFFFFF` | `#3F0F81` |
| `radioContainer` | `#EBDCFF` | `#572E99` |
| `onRadioContainer` | `#572E99` | `#EBDCFF` |

### Confirmed

Good news: Online, "Help notified", "Rescuer attending", "Resolved", ticked checklist items.

| Token | Light | Dark |
|---|---|---|
| `confirmed` | `#1B6D24` | `#88D982` |
| `onConfirmed` | `#FFFFFF` | `#003909` |
| `confirmedContainer` | `#A3F69C` | `#005312` |
| `onConfirmedContainer` | `#005312` | `#A3F69C` |

### Warning

Caution: isolated state, low battery, no GPS, offline data missing, compass calibration.

| Token | Light | Dark |
|---|---|---|
| `warning` | `#835400` | `#FFB956` |
| `onWarning` | `#FFFFFF` | `#462B00` |
| `warningContainer` | `#FFDDB5` | `#643F00` |
| `onWarningContainer` | `#643F00` | `#FFDDB5` |

### The SOS button

The main SOS button uses one fixed fill in both themes so it always looks like an emergency control:

| Token | Value | Notes |
|---|---|---|
| `sosFill` | `#D32F2F` | Same in light and dark |
| `onSosFill` | `#FFFFFF` | Contrast 5.0:1 |

The full-screen SOS countdown uses `sosFill` as its background.

### Where status colours apply

| Thing | Colour |
|---|---|
| Connection: Online | Confirmed |
| Connection: Mesh, "Connected to 4 phones" | Mesh |
| Connection: Radio connected | Radio |
| Connection: Isolated, "No connection" | Warning |
| Connection: Gateway, "Helping 3 people" | Confirmed, as a small badge |
| Delivery: Sending | `onSurfaceVariant` (neutral) |
| Delivery: Relayed | Mesh |
| Delivery: Sent by radio | Radio |
| Delivery: Help notified | Confirmed |
| Delivery: Rescuer attending | Confirmed |
| Delivery: Resolved | Confirmed, quieter (container) |
| Dashboard SOS: New | SOS |
| Dashboard SOS: Attended | Warning |
| Dashboard SOS: Resolved | Confirmed |
| Low battery, no GPS, missing offline data | Warning |

### Base colour roles (standard contrast)

These are the default light and dark schemes.

| Role | Light | Dark |
|---|---|---|
| `primary` | `#4A5C92` | `#B4C5FF` |
| `onPrimary` | `#FFFFFF` | `#1A2E60` |
| `primaryContainer` | `#DBE1FF` | `#324478` |
| `onPrimaryContainer` | `#324478` | `#DBE1FF` |
| `secondary` | `#585E72` | `#C1C6DD` |
| `onSecondary` | `#FFFFFF` | `#2A3042` |
| `secondaryContainer` | `#DDE1F9` | `#414659` |
| `onSecondaryContainer` | `#414659` | `#DDE1F9` |
| `tertiary` | `#4A5C92` | `#B4C5FF` |
| `onTertiary` | `#FFFFFF` | `#1A2E60` |
| `tertiaryContainer` | `#DBE1FF` | `#324478` |
| `onTertiaryContainer` | `#324478` | `#DBE1FF` |
| `error` | `#BA1A1A` | `#FFB4AB` |
| `onError` | `#FFFFFF` | `#690005` |
| `errorContainer` | `#FFDAD6` | `#93000A` |
| `onErrorContainer` | `#93000A` | `#FFDAD6` |
| `background` | `#FAF8FF` | `#121318` |
| `onBackground` | `#1A1B21` | `#E3E2E9` |
| `surface` | `#FAF8FF` | `#121318` |
| `onSurface` | `#1A1B21` | `#E3E2E9` |
| `surfaceVariant` | `#E2E2EC` | `#45464F` |
| `onSurfaceVariant` | `#45464F` | `#C5C6D0` |
| `outline` | `#757680` | `#8F909A` |
| `outlineVariant` | `#C5C6D0` | `#45464F` |
| `scrim` | `#000000` | `#000000` |
| `inverseSurface` | `#2F3036` | `#E3E2E9` |
| `inverseOnSurface` | `#F1F0F7` | `#2F3036` |
| `inversePrimary` | `#B4C5FF` | `#4A5C92` |
| `surfaceDim` | `#DAD9E0` | `#121318` |
| `surfaceBright` | `#FAF8FF` | `#38393F` |
| `surfaceContainerLowest` | `#FFFFFF` | `#0D0E13` |
| `surfaceContainerLow` | `#F4F3FA` | `#1A1B21` |
| `surfaceContainer` | `#EEEDF4` | `#1E1F25` |
| `surfaceContainerHigh` | `#E8E7EF` | `#292A2F` |
| `surfaceContainerHighest` | `#E3E2E9` | `#33343A` |

### Sunlight mode (high contrast)

Use the light high-contrast scheme for Sunlight mode, for use outdoors in bright light. The dark high-contrast scheme is available for users who turn on high contrast in Android's accessibility settings.

| Role | Light high contrast | Dark high contrast |
|---|---|---|
| `primary` | `#15295C` | `#EDEFFF` |
| `onPrimary` | `#FFFFFF` | `#000000` |
| `primaryContainer` | `#35477B` | `#AFC1FD` |
| `onPrimaryContainer` | `#FFFFFF` | `#000928` |
| `secondary` | `#262C3D` | `#EDEFFF` |
| `onSecondary` | `#FFFFFF` | `#000000` |
| `secondaryContainer` | `#43495C` | `#BDC2D9` |
| `onSecondaryContainer` | `#FFFFFF` | `#050A1B` |
| `tertiary` | `#15295C` | `#EDEFFF` |
| `onTertiary` | `#FFFFFF` | `#000000` |
| `tertiaryContainer` | `#35477B` | `#AFC1FD` |
| `onTertiaryContainer` | `#FFFFFF` | `#000928` |
| `error` | `#600004` | `#FFECE9` |
| `onError` | `#FFFFFF` | `#000000` |
| `errorContainer` | `#98000A` | `#FFAEA4` |
| `onErrorContainer` | `#FFFFFF` | `#220001` |
| `background` | `#FAF8FF` | `#121318` |
| `onBackground` | `#1A1B21` | `#E3E2E9` |
| `surface` | `#FAF8FF` | `#121318` |
| `onSurface` | `#000000` | `#FFFFFF` |
| `surfaceVariant` | `#E2E2EC` | `#45464F` |
| `onSurfaceVariant` | `#000000` | `#FFFFFF` |
| `outline` | `#2A2C34` | `#EFEFFA` |
| `outlineVariant` | `#474951` | `#C1C2CC` |
| `scrim` | `#000000` | `#000000` |
| `inverseSurface` | `#2F3036` | `#E3E2E9` |
| `inverseOnSurface` | `#FFFFFF` | `#000000` |
| `inversePrimary` | `#B4C5FF` | `#33467A` |
| `surfaceDim` | `#B9B8BF` | `#121318` |
| `surfaceBright` | `#FAF8FF` | `#4F5056` |
| `surfaceContainerLowest` | `#FFFFFF` | `#000000` |
| `surfaceContainerLow` | `#F1F0F7` | `#1E1F25` |
| `surfaceContainer` | `#E3E2E9` | `#2F3036` |
| `surfaceContainerHigh` | `#D4D3DB` | `#3A3B41` |
| `surfaceContainerHighest` | `#C6C6CD` | `#46464C` |

### Medium contrast

Available for completeness. Not used by default.

| Role | Light medium contrast | Dark medium contrast |
|---|---|---|
| `primary` | `#203367` | `#D2DBFF` |
| `onPrimary` | `#FFFFFF` | `#0D2255` |
| `primaryContainer` | `#596BA2` | `#7D8FC8` |
| `onPrimaryContainer` | `#FFFFFF` | `#000000` |
| `secondary` | `#303648` | `#D7DBF3` |
| `onSecondary` | `#FFFFFF` | `#202536` |
| `secondaryContainer` | `#676C81` | `#8B90A5` |
| `onSecondaryContainer` | `#FFFFFF` | `#000000` |
| `tertiary` | `#203367` | `#D2DBFF` |
| `onTertiary` | `#FFFFFF` | `#0D2255` |
| `tertiaryContainer` | `#596BA2` | `#7D8FC8` |
| `onTertiaryContainer` | `#FFFFFF` | `#000000` |
| `error` | `#740006` | `#FFD2CC` |
| `onError` | `#FFFFFF` | `#540003` |
| `errorContainer` | `#CF2C27` | `#FF5449` |
| `onErrorContainer` | `#FFFFFF` | `#000000` |
| `background` | `#FAF8FF` | `#121318` |
| `onBackground` | `#1A1B21` | `#E3E2E9` |
| `surface` | `#FAF8FF` | `#121318` |
| `onSurface` | `#101116` | `#FFFFFF` |
| `surfaceVariant` | `#E2E2EC` | `#45464F` |
| `onSurfaceVariant` | `#34363E` | `#DBDBE6` |
| `outline` | `#51525B` | `#B1B1BB` |
| `outlineVariant` | `#6B6C75` | `#8F9099` |
| `scrim` | `#000000` | `#000000` |
| `inverseSurface` | `#2F3036` | `#E3E2E9` |
| `inverseOnSurface` | `#F1F0F7` | `#292A2F` |
| `inversePrimary` | `#B4C5FF` | `#33467A` |
| `surfaceDim` | `#C6C6CD` | `#121318` |
| `surfaceBright` | `#FAF8FF` | `#43444A` |
| `surfaceContainerLowest` | `#FFFFFF` | `#06070C` |
| `surfaceContainerLow` | `#F4F3FA` | `#1C1D23` |
| `surfaceContainer` | `#E8E7EF` | `#27282D` |
| `surfaceContainerHigh` | `#DDDCE3` | `#313238` |
| `surfaceContainerHighest` | `#D2D1D8` | `#3C3D43` |

## Typography

**Mukta** for every text style, in both Hindi and English. It is a Google Font that covers Devanagari and Latin in one family, so mixed text looks consistent.

Weights in use: Regular 400, Medium 500, SemiBold 600, Bold 700.

| Style | Size / line height (sp) | Weight | Typical use |
|---|---|---|---|
| displayLarge | 57 / 64 | 400 | SOS countdown number |
| displayMedium | 45 / 52 | 400 | Compass distance ("850 m") |
| displaySmall | 36 / 44 | 400 | Large status ("Help notified") |
| headlineLarge | 32 / 40 | 600 | Rarely used |
| headlineMedium | 28 / 36 | 600 | Screen titles on key screens |
| headlineSmall | 24 / 32 | 600 | Section titles, dialog titles |
| titleLarge | 22 / 28 | 600 | Top app bar titles |
| titleMedium | 16 / 24 | 600 | Card titles, list item titles |
| titleSmall | 14 / 20 | 600 | Small headers |
| bodyLarge | 16 / 24 | 400 | **Default body text** |
| bodyMedium | 14 / 20 | 400 | Secondary text only, never instructions |
| bodySmall | 12 / 16 | 400 | Timestamps, captions |
| labelLarge | 14 / 20 | 600 | Buttons |
| labelMedium | 12 / 16 | 600 | Chips, badges |
| labelSmall | 11 / 16 | 600 | Navigation labels |

Rules:

- **Body text is 16 sp minimum** for anything the user must read in an emergency.
- **Devanagari needs room.** Hindi has marks above and below the line, and Hindi labels run 30 to 50% longer than English. Never set fixed-height text boxes or rely on single-line labels for Hindi.
- **Respect the system font size.** Layouts must still work at 1.3× text scale.
- **No all-caps.** It doesn't exist in Hindi and hurts reading in English.

## Shape

Material 3 shape scale, used as follows:

| Token | Radius | Use |
|---|---|---|
| extraSmall | 4 dp | Small badges |
| small | 8 dp | Chips, text fields |
| medium | 12 dp | Small cards, snackbars |
| large | 16 dp | Cards, action tiles on Home |
| extraLarge | 28 dp | Bottom sheets, dialogs, the SOS countdown cancel button |
| full | Pill or circle | Buttons, connection status pill, SOS button (circle) |

## Spacing

A 4 dp grid.

| Token | Value | Use |
|---|---|---|
| space-1 | 4 dp | Icon to label |
| space-2 | 8 dp | Inside chips, between related items |
| space-3 | 12 dp | Inside list items |
| space-4 | 16 dp | **Screen side margins**, card padding |
| space-5 | 24 dp | Between sections |
| space-6 | 32 dp | Around the SOS button |
| space-7 | 48 dp | Large separations, top of full-screen states |

## Touch targets

| Element | Minimum size |
|---|---|
| Any tappable element | 48 × 48 dp |
| Primary actions (send, call, confirm) | 56 dp tall |
| Home SOS button | 160 dp diameter, with ripple rings outside it |
| SOS countdown Cancel | Full width, 72 dp tall |
| Emergency calling cards | 96 dp tall |

## Elevation

Use Material 3 tonal elevation: higher layers use lighter `surfaceContainer` levels in dark mode and darker ones in light mode. Avoid drop shadows, except a soft glow on the SOS ripple rings.

## Icons

- **Material Symbols Rounded**, weight 400, optical size 24, grade 0.
- Outlined (fill 0) by default, filled (fill 1) for the selected navigation item and active states.
- Sizes: 24 dp standard, 20 dp inside chips, 32 to 48 dp for Home action tiles and calling cards.

Custom icons needed, drawn to match Material Symbols Rounded (same stroke weight and corner style):

| Icon | Meaning |
|---|---|
| Mesh | Phones connected over Bluetooth, for example three dots linked by arcs |
| Radio | LoRa radio, for example an antenna with waves |
| Gateway | A phone passing messages to the internet |
| Shelter | A safe-house symbol, distinct from a normal home icon |
| Pukaar mark | The logo: a call spreading outward as rings |

## Motion

Keep motion rare and purposeful, to save battery and reduce stress.

- **SOS ripple rings:** slow pulse (about 2 s per cycle) on the Home SOS button and during the countdown. The only continuous animation in the app.
- **State changes** (for example Relayed to Help notified): a short 200 to 300 ms fade or colour change.
- **Screen transitions:** Material 3 defaults.
- When Android's "Remove animations" setting is on, show static rings instead.

## Android frame and system UI

| Item | Value |
|---|---|
| Design frame | 360 × 800 dp. Check key screens at 412 × 915 dp too |
| Orientation | Portrait only |
| Edge-to-edge | Yes. Content draws behind the status and navigation bars |
| Status bar | 24 dp tall (taller on phones with a camera cutout) |
| Navigation | Gesture bar, about 24 dp at the bottom. Leave room for 3-button navigation (48 dp) too |
| Top app bar | 64 dp |
| Bottom navigation bar | 80 dp, four items: Home, Chat, Map, Guides |
| Bottom sheets | Drag handle, extraLarge top corners |

### System surfaces

| Surface | Constraints |
|---|---|
| Quick Settings tile | Single-colour 24 dp icon, label "SOS", optional subtitle. Android colours the tile itself |
| Home and lock-screen widget | Sizes 2 × 1 and 2 × 2 cells. Uses the system's rounded widget corners. Must work on light and dark wallpapers |
| Notifications | Single-colour small icon (24 dp, white on transparent). Up to 3 action buttons |
| App icon | Adaptive icon: 108 dp canvas, keep the logo inside the central 66 dp safe zone. Also provide a single-colour version for themed icons |

## Dashboard (web)

The rescuer dashboard uses the same colour roles, status colours, Mukta font and icon set.

| Item | Value |
|---|---|
| Design frame | 1440 × 900 px desktop, must work at 1280 px |
| Themes | Light and dark, chosen by the user |
| Base text | 16 px body, 14 px allowed in dense tables |
| Map | Takes most of the screen, incident list on the right (about 400 px wide) |
| Status on map pins | SOS (new), Warning (attended), Confirmed (resolved) |
