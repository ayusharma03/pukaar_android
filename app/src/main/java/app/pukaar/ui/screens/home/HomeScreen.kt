package app.pukaar.ui.screens.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.pukaar.device.BatteryInfo
import app.pukaar.model.ConnectionStatus
import app.pukaar.model.ConnectionType
import app.pukaar.model.DeliveryStatus
import app.pukaar.sos.ActiveSos
import app.pukaar.sos.SosStage
import app.pukaar.ui.components.DeliveryState
import app.pukaar.ui.components.InfoBox
import app.pukaar.ui.components.OutlineButton
import app.pukaar.ui.components.PreviewTheme
import app.pukaar.ui.components.PrimaryButton
import app.pukaar.ui.components.PukaarCard
import app.pukaar.ui.components.PukaarPreviews
import app.pukaar.ui.components.RippleField
import app.pukaar.ui.components.SosButton
import app.pukaar.ui.components.connectionLabel
import app.pukaar.ui.screens.sos.sampleSos
import app.pukaar.ui.theme.PukaarDimens
import app.pukaar.ui.theme.PukaarIcon
import app.pukaar.ui.theme.Sym
import app.pukaar.ui.theme.status
import com.bitchat.android.R

data class HomeState(
    val connection: ConnectionStatus,
    val battery: BatteryInfo,
    val unreadChat: Int,
    val activeSos: ActiveSos?,
    val offlineDataMissing: Boolean,
)

/** Home (2h): the calm command centre. SOS is one tap away (NFR-1). */
@Composable
fun HomeScreen(
    state: HomeState,
    onSos: () -> Unit,
    onSosStatus: () -> Unit,
    onSafe: () -> Unit,
    onNetwork: () -> Unit,
    onSettings: () -> Unit,
    onChat: () -> Unit,
    onMap: () -> Unit,
    onCall: () -> Unit,
    onGuides: () -> Unit,
    onOfflineData: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        Header(onSettings)

        val sos = state.activeSos
        if (sos != null && !sos.closed) {
            ActiveSosCard(sos, onSosStatus, onSafe, Modifier.padding(PukaarDimens.space4))
        } else {
            Box(Modifier.fillMaxWidth().padding(vertical = PukaarDimens.space2), contentAlignment = Alignment.Center) {
                RippleField(state.connection) { SosButton(onClick = onSos) }
            }
        }

        StatusLine(state.connection, onNetwork)

        Row(
            Modifier.padding(horizontal = PukaarDimens.space4, vertical = PukaarDimens.space3),
            horizontalArrangement = Arrangement.spacedBy(PukaarDimens.space2),
        ) {
            ActionTile(Sym.forum, stringResource(R.string.pk_tab_chat), onChat, Modifier.weight(1f), badge = state.unreadChat)
            ActionTile(Sym.map, stringResource(R.string.pk_tab_map), onMap, Modifier.weight(1f))
            ActionTile(Sym.call, stringResource(R.string.pk_home_call), onCall, Modifier.weight(1f))
            ActionTile(Sym.menuBook, stringResource(R.string.pk_tab_guides), onGuides, Modifier.weight(1f))
        }

        Column(Modifier.padding(horizontal = PukaarDimens.space4), verticalArrangement = Arrangement.spacedBy(PukaarDimens.space3)) {
            if (state.battery.low) {
                InfoBox(batteryIcon(state.battery.level), stringResource(R.string.pk_home_battery_low, state.battery.level), MaterialTheme.status.warning)
            }
            if (state.offlineDataMissing) {
                Box(Modifier.clip(MaterialTheme.shapes.medium).clickable(onClick = onOfflineData)) {
                    InfoBox(Sym.download, stringResource(R.string.pk_home_offline_missing), MaterialTheme.status.warning)
                }
            }
        }
        Box(Modifier.size(PukaarDimens.space4))
    }
}

@Composable
private fun Header(onSettings: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .heightIn(min = 64.dp)
            .padding(start = PukaarDimens.space4, end = PukaarDimens.space1),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(PukaarDimens.space2),
    ) {
        Icon(painterResource(R.drawable.ic_pukaar_mark), null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(32.dp))
        Text(stringResource(R.string.pk_app_name_short), style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f).semantics { heading() })
        IconButton(onClick = onSettings) {
            PukaarIcon(Sym.settings, stringResource(R.string.pk_settings_title), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** "Connected to 4 phones" plus a one-line explanation. Tapping opens Network. */
@Composable
private fun StatusLine(connection: ConnectionStatus, onClick: () -> Unit) {
    val s = MaterialTheme.status
    val color = when (connection.type) {
        ConnectionType.Online, ConnectionType.Gateway -> s.confirmed.main
        ConnectionType.Mesh -> s.mesh.main
        ConnectionType.Radio -> s.radio.main
        ConnectionType.Isolated -> s.warning.main
    }
    val explanation = stringResource(
        when (connection.type) {
            ConnectionType.Online -> R.string.pk_home_explain_online
            ConnectionType.Gateway -> R.string.pk_home_explain_gateway
            ConnectionType.Mesh -> R.string.pk_home_explain_mesh
            ConnectionType.Radio -> R.string.pk_home_explain_radio
            ConnectionType.Isolated -> R.string.pk_home_explain_isolated
        },
    )
    Column(
        Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = PukaarDimens.space4, vertical = PukaarDimens.space2),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            when (connection.type) {
                ConnectionType.Mesh -> Icon(painterResource(R.drawable.ic_mesh), null, tint = color, modifier = Modifier.size(20.dp))
                ConnectionType.Radio -> Icon(painterResource(R.drawable.ic_radio), null, tint = color, modifier = Modifier.size(20.dp))
                ConnectionType.Isolated -> PukaarIcon(Sym.signalCellularOff, null, size = 20.dp, tint = color)
                else -> PukaarIcon(Sym.wifi, null, size = 20.dp, tint = color)
            }
            Text(
                connectionLabel(connection),
                style = MaterialTheme.typography.titleMedium.copy(fontSize = MaterialTheme.typography.titleMedium.fontSize * 1.125f),
                color = color,
                textAlign = TextAlign.Center,
            )
        }
        Text(explanation, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
    }
}

@Composable
private fun ActionTile(icon: String, label: String, onClick: () -> Unit, modifier: Modifier = Modifier, badge: Int = 0) {
    val badgeText = if (badge > 99) "99+" else badge.toString()
    val description = if (badge > 0) stringResource(R.string.pk_home_tile_unread, label, badge) else label
    Box(
        modifier
            .heightIn(min = PukaarDimens.homeTileMinHeight)
            .clip(MaterialTheme.shapes.large)
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics(mergeDescendants = true) { contentDescription = description },
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = PukaarDimens.space1, vertical = 10.dp)
                .align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            PukaarIcon(icon, null, size = 32.dp, tint = MaterialTheme.colorScheme.primary)
            Text(label, style = MaterialTheme.typography.titleSmall, textAlign = TextAlign.Center)
        }
        if (badge > 0) {
            Text(
                badgeText,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onPrimary,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 6.dp, end = 8.dp)
                    .heightIn(min = 20.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary)
                    .padding(horizontal = 6.dp),
            )
        }
    }
}

/** Replaces the SOS button while an SOS is active (screens.md §2). */
@Composable
private fun ActiveSosCard(sos: ActiveSos, onOpen: () -> Unit, onSafe: () -> Unit, modifier: Modifier = Modifier) {
    val s = MaterialTheme.status
    PukaarCard(modifier.fillMaxWidth(), color = s.sos.container, contentColor = s.sos.onContainer, onClick = onOpen) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(PukaarDimens.space2)) {
            Box(Modifier.size(36.dp).clip(CircleShape).background(s.sosFill), contentAlignment = Alignment.Center) {
                PukaarIcon(Sym.sos, null, size = 22.dp, tint = s.onSosFill)
            }
            Text(stringResource(R.string.pk_home_sos_active), style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
        }
        Box(Modifier.clip(MaterialTheme.shapes.small).background(MaterialTheme.colorScheme.surface).padding(horizontal = 8.dp, vertical = 4.dp)) {
            DeliveryState(sos.stage.toDelivery(sos.relayPeers))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(PukaarDimens.space2)) {
            OutlineButton(stringResource(R.string.pk_home_see_status), onOpen, Modifier.weight(1f))
            PrimaryButton(
                stringResource(R.string.pk_sos_im_safe_short),
                onSafe,
                Modifier.weight(1f),
                icon = Sym.verifiedUser,
                containerColor = s.confirmed.main,
                contentColor = s.confirmed.onMain,
                height = PukaarDimens.minTarget,
            )
        }
    }
}

fun SosStage.toDelivery(relayPeers: Int): DeliveryStatus = when (this) {
    SosStage.Sending -> DeliveryStatus.Sending
    SosStage.Relayed -> DeliveryStatus.Relayed(relayPeers.takeIf { it > 0 })
    SosStage.SentByRadio -> DeliveryStatus.SentByRadio
    SosStage.HelpNotified -> DeliveryStatus.HelpNotified
    SosStage.RescuerAttending -> DeliveryStatus.RescuerAttending
    SosStage.Resolved -> DeliveryStatus.Resolved
}

fun batteryIcon(level: Int) = when {
    level >= 60 -> Sym.battery5Bar
    else -> Sym.battery3Bar
}

@PukaarPreviews
@Composable
private fun HomePreview() = PreviewTheme {
    HomeScreen(
        HomeState(ConnectionStatus.mesh(4, 18), BatteryInfo(18, false), 3, null, false),
        {}, {}, {}, {}, {}, {}, {}, {}, {}, {},
    )
}

@PukaarPreviews
@Composable
private fun HomeSosActivePreview() = PreviewTheme {
    HomeScreen(
        HomeState(ConnectionStatus.Isolated, BatteryInfo(64, false), 0, sampleSos(SosStage.Relayed), true),
        {}, {}, {}, {}, {}, {}, {}, {}, {}, {},
    )
}
