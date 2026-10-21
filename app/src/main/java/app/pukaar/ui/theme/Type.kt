package app.pukaar.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import com.bitchat.android.R

// Mukta is bundled in res/font so it works with no internet.
// (Downloadable Google Fonts would need a network connection on first launch.)
val Mukta = FontFamily(
    Font(R.font.mukta_regular, FontWeight.Normal),
    Font(R.font.mukta_medium, FontWeight.Medium),
    Font(R.font.mukta_semibold, FontWeight.SemiBold),
    Font(R.font.mukta_bold, FontWeight.Bold),
)

private fun style(size: TextUnit, line: TextUnit, weight: FontWeight) =
    TextStyle(fontFamily = Mukta, fontWeight = weight, fontSize = size, lineHeight = line)

// Sizes and weights from docs/design-tokens.md.
val PukaarTypography = Typography(
    displayLarge = style(57.sp, 64.sp, FontWeight.Normal),
    displayMedium = style(45.sp, 52.sp, FontWeight.Normal),
    displaySmall = style(36.sp, 44.sp, FontWeight.Normal),
    headlineLarge = style(32.sp, 40.sp, FontWeight.SemiBold),
    headlineMedium = style(28.sp, 36.sp, FontWeight.SemiBold),
    headlineSmall = style(24.sp, 32.sp, FontWeight.SemiBold),
    titleLarge = style(22.sp, 28.sp, FontWeight.SemiBold),
    titleMedium = style(16.sp, 24.sp, FontWeight.SemiBold),
    titleSmall = style(14.sp, 20.sp, FontWeight.SemiBold),
    bodyLarge = style(16.sp, 24.sp, FontWeight.Normal),
    bodyMedium = style(14.sp, 20.sp, FontWeight.Normal),
    bodySmall = style(12.sp, 16.sp, FontWeight.Normal),
    labelLarge = style(14.sp, 20.sp, FontWeight.SemiBold),
    labelMedium = style(12.sp, 16.sp, FontWeight.SemiBold),
    labelSmall = style(11.sp, 16.sp, FontWeight.SemiBold),
)

// Extra sizes used by custom components in the designs (handoff README).
object PukaarTextStyles {
    val countdown = style(112.sp, 112.sp, FontWeight.SemiBold)
    val compassDistance = style(56.sp, 64.sp, FontWeight.Medium)
    val sosButtonLabel = style(44.sp, 48.sp, FontWeight.Bold)
    val sosButtonHint = style(15.sp, 20.sp, FontWeight.Medium)
    val deliveryState = style(13.sp, 18.sp, FontWeight.SemiBold)
}
