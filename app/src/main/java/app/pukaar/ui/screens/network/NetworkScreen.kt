package app.pukaar.ui.screens.network

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.pukaar.model.ConnectionStatus
import app.pukaar.ui.components.InfoBox
import app.pukaar.ui.components.ListRow
import app.pukaar.ui.components.PreviewTheme
import app.pukaar.ui.components.PrimaryButton
import app.pukaar.ui.components.PukaarCard
import app.pukaar.ui.components.PukaarPreviews
import app.pukaar.ui.components.PukaarTopBar
import app.pukaar.ui.components.Tag
import app.pukaar.ui.components.TonalButton
import app.pukaar.ui.theme.PukaarDimens
import app.pukaar.ui.theme.PukaarIcon
import app.pukaar.ui.theme.Sym
import app.pukaar.ui.theme.status
import com.bitchat.android.R
import kotlin.math.cos
import kotlin.math.sin

data class NetworkState(
    val connection: ConnectionStatus,
    val internet: Boolean,
    val bluetoothOn: Boolean,
    val radioPaired: Boolean,
    val gatewayConfigured: Boolean,
    val waiting: Int,
)

/** Network (2j): the connection in more detail. */
@Composable
fun NetworkScreen(state: NetworkState, onBack: () -> Unit, onEnableBluetooth: () -> Unit) {
    val s = MaterialTheme.status
    var showRadio by remember { mutableStateOf(false) }
    val c = state.connection
    Column(Modifier.fillMaxSize()) {
        PukaarTopBar(stringResource(R.string.pk_network_title), onBack = onBack)
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = PukaarDimens.space4),
            verticalArrangement = Arrangement.spacedBy(PukaarDimens.space3),
        ) {
            PeerRing(c.peers, Modifier.align(Alignment.CenterHorizontally))
            if (c.peers > 0) {
                Text(
                    pluralStringResource(R.plurals.pk_network_reach, c.reachable, c.reachable, c.peers),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            PukaarCard(padding = 0.dp) {
                ListRow(
                    stringResource(R.string.pk_network_internet),
                    subtitle = stringResource(if (state.internet) R.string.pk_network_available else R.string.pk_network_not_available),
                    icon = if (state.internet) Sym.wifi else Sym.wifiOff,
                    trailing = { OnOff(state.internet) },
                )
                ListRow(
                    stringResource(R.string.pk_network_mesh),
                    subtitle = if (state.bluetoothOn) pluralStringResource(R.plurals.pk_network_mesh_detail, c.peers, c.peers, c.reachable)
                    else stringResource(R.string.pk_network_bluetooth_off),
                    iconPainter = R.drawable.ic_mesh,
                    iconTint = s.mesh.main,
                    trailing = {
                        if (state.bluetoothOn) OnOff(true) else TonalButton(stringResource(R.string.pk_turn_on), onEnableBluetooth)
                    },
                )
                ListRow(
                    stringResource(R.string.pk_network_radio),
                    subtitle = stringResource(if (state.radioPaired) R.string.pk_network_radio_paired else R.string.pk_network_radio_not_paired),
                    iconPainter = R.drawable.ic_radio,
                    iconTint = s.radio.main,
                    trailing = { Tag(stringResource(if (state.radioPaired) R.string.pk_on else R.string.pk_none), if (state.radioPaired) s.radio else null) },
                )
                val helping = c.gatewayHelping
                ListRow(
                    stringResource(R.string.pk_network_helping),
                    subtitle = when {
                        !state.gatewayConfigured -> stringResource(R.string.pk_network_helping_no_server)
                        !state.internet -> stringResource(R.string.pk_network_helping_starts)
                        helping > 0 -> pluralStringResource(R.plurals.pk_network_helping_count, helping, helping)
                        else -> stringResource(R.string.pk_network_helping_ready)
                    },
                    icon = Sym.volunteerActivism,
                    trailing = { OnOff(state.gatewayConfigured && state.internet) },
                )
            }

            if (state.waiting > 0) {
                InfoBox(Sym.scheduleSend, pluralStringResource(R.plurals.pk_network_waiting, state.waiting, state.waiting), null)
            }
        }
        PrimaryButton(
            stringResource(R.string.pk_network_pair_radio),
            { showRadio = true },
            Modifier.fillMaxWidth().padding(PukaarDimens.space4),
            containerColor = s.radio.container,
            contentColor = s.radio.onContainer,
        )
    }
    if (showRadio) RadioUnavailableDialog { showRadio = false }
}

@Composable
fun RadioUnavailableDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.pk_radio_unavailable_title)) },
        text = { Text(stringResource(R.string.pk_radio_unavailable_body)) },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.pk_ok)) } },
    )
}

@Composable
private fun OnOff(on: Boolean) {
    Tag(stringResource(if (on) R.string.pk_on else R.string.pk_off), if (on) MaterialTheme.status.confirmed else null)
}

/** 200 dp ring with the nearby phone count and a node per phone. */
@Composable
private fun PeerRing(peers: Int, modifier: Modifier = Modifier) {
    val s = MaterialTheme.status
    val description = pluralStringResource(R.plurals.pk_network_nearby_count, peers, peers)
    Box(modifier.size(220.dp).semantics(mergeDescendants = true) { contentDescription = description }, contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(200.dp).clearAndSetSemantics { }) {
            drawCircle(s.mesh.main.copy(alpha = 0.6f), size.minDimension / 2 - 1.dp.toPx(), style = Stroke(1.5.dp.toPx()))
        }
        Box(Modifier.size(140.dp).clip(CircleShape).background(s.mesh.container), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(peers.toString(), style = MaterialTheme.typography.displayMedium, color = s.mesh.onContainer)
                Text(stringResource(R.string.pk_network_phones_nearby), style = MaterialTheme.typography.bodyMedium, color = s.mesh.onContainer, textAlign = TextAlign.Center)
            }
        }
        val count = peers.coerceIn(0, 8)
        repeat(count) { i ->
            val a = Math.toRadians(-90.0 + i * 360.0 / count)
            Box(
                Modifier
                    .offset(x = (100 * cos(a)).dp, y = (100 * sin(a)).dp)
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(s.mesh.container)
                    .border(2.dp, s.mesh.main, CircleShape),
                contentAlignment = Alignment.Center,
            ) { PukaarIcon(Sym.smartphone, null, size = 16.dp, tint = s.mesh.onContainer) }
        }
    }
}

@PukaarPreviews
@Composable
private fun NetworkPreview() = PreviewTheme {
    NetworkScreen(NetworkState(ConnectionStatus.mesh(4, 18), false, true, false, true, 2), {}, {})
}
