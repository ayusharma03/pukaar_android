package app.pukaar.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

// From docs/design-tokens.md. Dynamic colour must stay OFF.

val PukaarDark = darkColorScheme(
    primary = Color(0xFFB4C5FF), onPrimary = Color(0xFF1A2E60),
    primaryContainer = Color(0xFF324478), onPrimaryContainer = Color(0xFFDBE1FF),
    secondary = Color(0xFFC1C6DD), onSecondary = Color(0xFF2A3042),
    secondaryContainer = Color(0xFF414659), onSecondaryContainer = Color(0xFFDDE1F9),
    tertiary = Color(0xFFB4C5FF), onTertiary = Color(0xFF1A2E60),
    tertiaryContainer = Color(0xFF324478), onTertiaryContainer = Color(0xFFDBE1FF),
    error = Color(0xFFFFB4AB), onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A), onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF121318), onBackground = Color(0xFFE3E2E9),
    surface = Color(0xFF121318), onSurface = Color(0xFFE3E2E9),
    surfaceVariant = Color(0xFF45464F), onSurfaceVariant = Color(0xFFC5C6D0),
    outline = Color(0xFF8F909A), outlineVariant = Color(0xFF45464F),
    scrim = Color(0xFF000000),
    inverseSurface = Color(0xFFE3E2E9), inverseOnSurface = Color(0xFF2F3036), inversePrimary = Color(0xFF4A5C92),
    surfaceDim = Color(0xFF121318), surfaceBright = Color(0xFF38393F),
    surfaceContainerLowest = Color(0xFF0D0E13), surfaceContainerLow = Color(0xFF1A1B21),
    surfaceContainer = Color(0xFF1E1F25), surfaceContainerHigh = Color(0xFF292A2F),
    surfaceContainerHighest = Color(0xFF33343A),
)

val PukaarLight = lightColorScheme(
    primary = Color(0xFF4A5C92), onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFDBE1FF), onPrimaryContainer = Color(0xFF324478),
    secondary = Color(0xFF585E72), onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFDDE1F9), onSecondaryContainer = Color(0xFF414659),
    tertiary = Color(0xFF4A5C92), onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFDBE1FF), onTertiaryContainer = Color(0xFF324478),
    error = Color(0xFFBA1A1A), onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6), onErrorContainer = Color(0xFF93000A),
    background = Color(0xFFFAF8FF), onBackground = Color(0xFF1A1B21),
    surface = Color(0xFFFAF8FF), onSurface = Color(0xFF1A1B21),
    surfaceVariant = Color(0xFFE2E2EC), onSurfaceVariant = Color(0xFF45464F),
    outline = Color(0xFF757680), outlineVariant = Color(0xFFC5C6D0),
    scrim = Color(0xFF000000),
    inverseSurface = Color(0xFF2F3036), inverseOnSurface = Color(0xFFF1F0F7), inversePrimary = Color(0xFFB4C5FF),
    surfaceDim = Color(0xFFDAD9E0), surfaceBright = Color(0xFFFAF8FF),
    surfaceContainerLowest = Color(0xFFFFFFFF), surfaceContainerLow = Color(0xFFF4F3FA),
    surfaceContainer = Color(0xFFEEEDF4), surfaceContainerHigh = Color(0xFFE8E7EF),
    surfaceContainerHighest = Color(0xFFE3E2E9),
)


// Sunlight mode: light high-contrast scheme from docs/design-tokens.md.
val PukaarSunlight = lightColorScheme(
    primary = Color(0xFF15295C),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFF35477B),
    onPrimaryContainer = Color(0xFFFFFFFF),
    secondary = Color(0xFF262C3D),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFF43495C),
    onSecondaryContainer = Color(0xFFFFFFFF),
    tertiary = Color(0xFF15295C),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFF35477B),
    onTertiaryContainer = Color(0xFFFFFFFF),
    error = Color(0xFF600004),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFF98000A),
    onErrorContainer = Color(0xFFFFFFFF),
    background = Color(0xFFFAF8FF),
    onBackground = Color(0xFF1A1B21),
    surface = Color(0xFFFAF8FF),
    onSurface = Color(0xFF000000),
    surfaceVariant = Color(0xFFE2E2EC),
    onSurfaceVariant = Color(0xFF000000),
    outline = Color(0xFF2A2C34),
    outlineVariant = Color(0xFF474951),
    scrim = Color(0xFF000000),
    inverseSurface = Color(0xFF2F3036),
    inverseOnSurface = Color(0xFFFFFFFF),
    inversePrimary = Color(0xFFB4C5FF),
    surfaceDim = Color(0xFFB9B8BF),
    surfaceBright = Color(0xFFFAF8FF),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF1F0F7),
    surfaceContainer = Color(0xFFE3E2E9),
    surfaceContainerHigh = Color(0xFFD4D3DB),
    surfaceContainerHighest = Color(0xFFC6C6CD),
)

@Immutable
data class StatusColor(val main: Color, val onMain: Color, val container: Color, val onContainer: Color)

@Immutable
data class PukaarStatus(
    val sos: StatusColor, val mesh: StatusColor, val radio: StatusColor,
    val confirmed: StatusColor, val warning: StatusColor,
    val sosFill: Color = Color(0xFFD32F2F), val onSosFill: Color = Color.White,
)

val PukaarStatusDark = PukaarStatus(
    sos = StatusColor(Color(0xFFFFB3AC), Color(0xFF680008), Color(0xFF930010), Color(0xFFFFDAD6)),
    mesh = StatusColor(Color(0xFF4CDADA), Color(0xFF003737), Color(0xFF004F4F), Color(0xFF6FF7F6)),
    radio = StatusColor(Color(0xFFD4BBFF), Color(0xFF3F0F81), Color(0xFF572E99), Color(0xFFEBDCFF)),
    confirmed = StatusColor(Color(0xFF88D982), Color(0xFF003909), Color(0xFF005312), Color(0xFFA3F69C)),
    warning = StatusColor(Color(0xFFFFB956), Color(0xFF462B00), Color(0xFF643F00), Color(0xFFFFDDB5)),
)

val PukaarStatusLight = PukaarStatus(
    sos = StatusColor(Color(0xFFBA1A20), Color(0xFFFFFFFF), Color(0xFFFFDAD6), Color(0xFF930010)),
    mesh = StatusColor(Color(0xFF006A6A), Color(0xFFFFFFFF), Color(0xFF6FF7F6), Color(0xFF004F4F)),
    radio = StatusColor(Color(0xFF6F48B2), Color(0xFFFFFFFF), Color(0xFFEBDCFF), Color(0xFF572E99)),
    confirmed = StatusColor(Color(0xFF1B6D24), Color(0xFFFFFFFF), Color(0xFFA3F69C), Color(0xFF005312)),
    warning = StatusColor(Color(0xFF835400), Color(0xFFFFFFFF), Color(0xFFFFDDB5), Color(0xFF643F00)),
)

val LocalPukaarStatus = staticCompositionLocalOf { PukaarStatusDark }
