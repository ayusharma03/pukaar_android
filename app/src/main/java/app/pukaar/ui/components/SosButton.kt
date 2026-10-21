package app.pukaar.ui.components

import android.provider.Settings
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import app.pukaar.ui.theme.PukaarDimens
import app.pukaar.ui.theme.PukaarTextStyles
import app.pukaar.ui.theme.PukaarTheme
import app.pukaar.ui.theme.PukaarThemeMode
import app.pukaar.ui.theme.status
import com.bitchat.android.R

/** Outer halo size. The button and its halos take this much space. */
val SosButtonOuterSize = 232.dp
private val InnerHaloSize = 196.dp
private val GlowRadius = 32.dp

/**
 * The Home SOS button: a 160 dp `sosFill` circle with "SOS" and "Tap for help",
 * a soft red glow, and two halos (196 dp at 13%, 232 dp at 7%) that pulse about every 2 s.
 * The halos are static when Android's "Remove animations" is on.
 */
@Composable
fun SosButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    animate: Boolean = rememberAnimationsEnabled(),
) {
    val fill = MaterialTheme.status.sosFill
    val onFill = MaterialTheme.status.onSosFill
    val description = stringResource(R.string.pk_sos_button_description)

    val pulse = if (animate) {
        val transition = rememberInfiniteTransition(label = "sosHalo")
        val value by transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(1000, easing = FastOutSlowInEasing), RepeatMode.Reverse),
            label = "sosHaloPulse",
        )
        value
    } else {
        0f
    }

    Box(
        modifier = modifier
            .size(SosButtonOuterSize)
            .clearAndSetSemantics {
                contentDescription = description
                role = Role.Button
                onClick { onClick(); true }
            },
        contentAlignment = Alignment.Center,
    ) {
        // Outer halo, 7% red, breathing slightly.
        Box(
            Modifier
                .fillMaxSize()
                .scale(1f - 0.04f * (1f - pulse))
                .clip(CircleShape)
                .background(fill.copy(alpha = 0.07f + 0.03f * pulse)),
        )
        // Inner halo, 13% red.
        Box(
            Modifier
                .size(InnerHaloSize)
                .scale(1f - 0.03f * (1f - pulse))
                .clip(CircleShape)
                .background(fill.copy(alpha = 0.13f + 0.04f * pulse)),
        )
        // Glow (box-shadow 0 0 32dp rgba(211,47,47,.45) in the designs) and the button itself.
        Box(
            Modifier
                .size(PukaarDimens.sosButtonSize)
                .drawBehind {
                    val glow = GlowRadius.toPx()
                    drawCircle(
                        brush = Brush.radialGradient(
                            0.0f to fill.copy(alpha = 0.45f),
                            (size.minDimension / 2 / (size.minDimension / 2 + glow)) to fill.copy(alpha = 0.45f),
                            1.0f to Color.Transparent,
                            center = center,
                            radius = size.minDimension / 2 + glow,
                        ),
                        radius = size.minDimension / 2 + glow,
                    )
                }
                .clip(CircleShape)
                .background(fill)
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                Modifier.padding(horizontal = PukaarDimens.space3),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(stringResource(R.string.pk_sos), style = PukaarTextStyles.sosButtonLabel, color = onFill)
                Text(
                    stringResource(R.string.pk_sos_tap_for_help),
                    style = PukaarTextStyles.sosButtonHint,
                    color = onFill,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

/** False when the user turned on "Remove animations" (animator duration scale 0). */
@Composable
fun rememberAnimationsEnabled(): Boolean {
    if (LocalInspectionMode.current) return true
    val resolver = LocalContext.current.contentResolver
    return remember(resolver) {
        Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) != 0f
    }
}

@Composable
private fun SosButtonSample() {
    Surface(color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxSize()) {
        Box(contentAlignment = Alignment.Center) {
            SosButton(onClick = {}, animate = false)
        }
    }
}

@Preview(name = "Dark", widthDp = 360, heightDp = 800)
@Composable
private fun SosButtonDark() = PukaarTheme(PukaarThemeMode.Dark) { SosButtonSample() }

@Preview(name = "Light", widthDp = 360, heightDp = 800)
@Composable
private fun SosButtonLight() = PukaarTheme(PukaarThemeMode.Light) { SosButtonSample() }

@Preview(name = "Hindi 1.3x", widthDp = 360, heightDp = 800, locale = "hi", fontScale = 1.3f)
@Composable
private fun SosButtonHindi() = PukaarTheme(PukaarThemeMode.Dark) { SosButtonSample() }
