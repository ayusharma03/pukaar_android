package app.pukaar.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import com.bitchat.android.ui.theme.DarkBitchatPalette
import com.bitchat.android.ui.theme.LightBitchatPalette
import com.bitchat.android.ui.theme.LocalBitchatPalette

enum class PukaarThemeMode { Dark, Light, Sunlight, System }

val PukaarShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

/** Spacing and touch-target sizes from docs/design-tokens.md. */
object PukaarDimens {
    val space1 = 4.dp
    val space2 = 8.dp
    val space3 = 12.dp
    val space4 = 16.dp   // screen side margins, card padding
    val space5 = 24.dp
    val space6 = 32.dp
    val space7 = 48.dp

    val minTarget = 48.dp
    val primaryButtonHeight = 56.dp
    val countdownCancelHeight = 72.dp
    val sosButtonSize = 160.dp
    val callCardHeight = 96.dp
    val homeTileMinHeight = 88.dp
}

/**
 * Pukaar's theme. Dynamic colour is intentionally not supported:
 * SOS red and the status colours must look the same on every phone.
 * Dark is the default, as in the design brief.
 */
@Composable
fun PukaarTheme(
    mode: PukaarThemeMode = PukaarThemeMode.Dark,
    content: @Composable () -> Unit,
) {
    val dark = when (mode) {
        PukaarThemeMode.Dark -> true
        PukaarThemeMode.Light, PukaarThemeMode.Sunlight -> false
        PukaarThemeMode.System -> isSystemInDarkTheme()
    }
    val colorScheme = when {
        mode == PukaarThemeMode.Sunlight -> PukaarSunlight
        dark -> PukaarDark
        else -> PukaarLight
    }
    val status = if (dark) PukaarStatusDark else PukaarStatusLight

    // Light or dark system bar icons to match the theme (the app draws edge to edge).
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !dark
                isAppearanceLightNavigationBars = !dark
            }
        }
    }

    CompositionLocalProvider(
        LocalPukaarStatus provides status,
        // Keeps bitchat's existing screens working until they are replaced.
        LocalBitchatPalette provides if (dark) DarkBitchatPalette else LightBitchatPalette,
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = PukaarTypography,
            shapes = PukaarShapes,
            content = content,
        )
    }
}

/** Status colours: MaterialTheme.status.mesh.main, MaterialTheme.status.sosFill, and so on. */
val MaterialTheme.status: PukaarStatus
    @Composable @ReadOnlyComposable
    get() = LocalPukaarStatus.current
