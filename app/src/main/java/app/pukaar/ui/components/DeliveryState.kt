package app.pukaar.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import app.pukaar.model.DeliveryStatus
import app.pukaar.ui.theme.PukaarDimens
import app.pukaar.ui.theme.PukaarIcon
import app.pukaar.ui.theme.PukaarTextStyles
import app.pukaar.ui.theme.PukaarTheme
import app.pukaar.ui.theme.PukaarThemeMode
import app.pukaar.ui.theme.Sym
import app.pukaar.ui.theme.status
import com.bitchat.android.R

/**
 * Delivery state under the user's own chat bubbles and on SOS cards: an icon plus words,
 * for example "Help notified · 4:09 pm". Fades between states in 250 ms.
 *
 * @param time already formatted for the user's locale, or null to show the state alone.
 */
@Composable
fun DeliveryState(
    status: DeliveryStatus,
    modifier: Modifier = Modifier,
    time: String? = null,
) {
    AnimatedContent(
        targetState = status,
        modifier = modifier,
        transitionSpec = { fadeIn(tween(250)) togetherWith fadeOut(tween(250)) },
        label = "deliveryState",
    ) { state ->
        val look = deliveryLook(state)
        val label = deliveryLabel(state)
        val text = if (time != null) stringResource(R.string.pk_delivery_with_time, label, time) else label

        Row(
            modifier = if (look.background != null) {
                Modifier
                    .clip(MaterialTheme.shapes.extraSmall)
                    .background(look.background)
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            } else {
                Modifier
            },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(PukaarDimens.space1),
        ) {
            when (val icon = look.icon) {
                is DeliveryIcon.Symbol -> PukaarIcon(icon.name, null, size = 16.dp, tint = look.color, filled = icon.filled)
                is DeliveryIcon.Drawable ->
                    Icon(painterResource(icon.res), null, tint = look.color, modifier = Modifier.size(16.dp))
            }
            Text(text, style = PukaarTextStyles.deliveryState, color = look.color)
        }
    }
}

private sealed interface DeliveryIcon {
    data class Symbol(val name: String, val filled: Boolean = false) : DeliveryIcon
    data class Drawable(val res: Int) : DeliveryIcon
}

private data class DeliveryLook(val icon: DeliveryIcon, val color: Color, val background: Color? = null)

@Composable
private fun deliveryLook(status: DeliveryStatus): DeliveryLook {
    val s = MaterialTheme.status
    return when (status) {
        DeliveryStatus.Sending -> DeliveryLook(DeliveryIcon.Symbol(Sym.schedule), MaterialTheme.colorScheme.onSurfaceVariant)
        is DeliveryStatus.Relayed -> DeliveryLook(DeliveryIcon.Drawable(R.drawable.ic_mesh), s.mesh.main)
        DeliveryStatus.SentByRadio -> DeliveryLook(DeliveryIcon.Drawable(R.drawable.ic_radio), s.radio.main)
        DeliveryStatus.HelpNotified -> DeliveryLook(DeliveryIcon.Symbol(Sym.checkCircle), s.confirmed.main)
        DeliveryStatus.RescuerAttending -> DeliveryLook(DeliveryIcon.Symbol(Sym.verifiedUser, filled = true), s.confirmed.main)
        // "Confirmed, quieter": container colours instead of the bright main colour.
        DeliveryStatus.Resolved ->
            DeliveryLook(DeliveryIcon.Symbol(Sym.checkCircle, filled = true), s.confirmed.onContainer, s.confirmed.container)
    }
}

@Composable
private fun deliveryLabel(status: DeliveryStatus): String = when (status) {
    DeliveryStatus.Sending -> stringResource(R.string.pk_delivery_sending)
    is DeliveryStatus.Relayed -> status.hops
        ?.let { pluralStringResource(R.plurals.pk_delivery_relayed, it, it) }
        ?: stringResource(R.string.pk_delivery_relayed_plain)
    DeliveryStatus.SentByRadio -> stringResource(R.string.pk_delivery_radio)
    DeliveryStatus.HelpNotified -> stringResource(R.string.pk_delivery_help_notified)
    DeliveryStatus.RescuerAttending -> stringResource(R.string.pk_delivery_attending)
    DeliveryStatus.Resolved -> stringResource(R.string.pk_delivery_resolved)
}

@Composable
private fun AllStates() {
    Surface(color = MaterialTheme.colorScheme.surface) {
        Column(Modifier.padding(PukaarDimens.space4), verticalArrangement = Arrangement.spacedBy(PukaarDimens.space3)) {
            DeliveryState(DeliveryStatus.Sending)
            DeliveryState(DeliveryStatus.Relayed(3))
            DeliveryState(DeliveryStatus.Relayed())
            DeliveryState(DeliveryStatus.SentByRadio, time = "4:08 pm")
            DeliveryState(DeliveryStatus.HelpNotified, time = "4:09 pm")
            DeliveryState(DeliveryStatus.RescuerAttending, time = "4:21 pm")
            DeliveryState(DeliveryStatus.Resolved, time = "5:02 pm")
        }
    }
}

@Preview(name = "Dark", widthDp = 360, heightDp = 800)
@Composable
private fun DeliveryStateDark() = PukaarTheme(PukaarThemeMode.Dark) { AllStates() }

@Preview(name = "Light", widthDp = 360, heightDp = 800)
@Composable
private fun DeliveryStateLight() = PukaarTheme(PukaarThemeMode.Light) { AllStates() }

@Preview(name = "Hindi 1.3x", widthDp = 360, heightDp = 800, locale = "hi", fontScale = 1.3f)
@Composable
private fun DeliveryStateHindi() = PukaarTheme(PukaarThemeMode.Dark) { AllStates() }
