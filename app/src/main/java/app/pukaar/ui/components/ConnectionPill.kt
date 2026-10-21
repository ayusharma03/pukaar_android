package app.pukaar.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import app.pukaar.model.ConnectionStatus
import app.pukaar.model.ConnectionType
import app.pukaar.ui.theme.PukaarDimens
import app.pukaar.ui.theme.PukaarIcon
import app.pukaar.ui.theme.PukaarTextStyles
import app.pukaar.ui.theme.PukaarTheme
import app.pukaar.ui.theme.PukaarThemeMode
import app.pukaar.ui.theme.StatusColor
import app.pukaar.ui.theme.Sym
import app.pukaar.ui.theme.status
import com.bitchat.android.R

/**
 * Connection status pill shown at the top of every main screen.
 * Colour pairs from design-tokens.md "Where status colours apply"; always icon plus words.
 *
 * @param onClick opens Network when set. The tappable area grows to 48 dp; the pill itself stays 32 dp.
 */
@Composable
fun ConnectionPill(
    status: ConnectionStatus,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    val colors = statusColorFor(status.type)
    val container by animateColorAsState(colors.container, tween(250), label = "pillContainer")
    val content by animateColorAsState(colors.onContainer, tween(250), label = "pillContent")

    val tapModifier = if (onClick != null) {
        Modifier
            .heightIn(min = PukaarDimens.minTarget)
            .clip(CircleShape)
            .clickable(role = Role.Button, onClick = onClick)
    } else {
        Modifier
    }

    Row(modifier.then(tapModifier), verticalAlignment = Alignment.CenterVertically) {
        Row(
            modifier = Modifier
                .heightIn(min = 32.dp)
                .clip(CircleShape)
                .background(container)
                .padding(horizontal = PukaarDimens.space3, vertical = PukaarDimens.space1),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            ConnectionIcon(status.type, content)
            Text(connectionLabel(status), style = PukaarTextStyles.deliveryState, color = content)
        }
    }
}

@Composable
private fun ConnectionIcon(type: ConnectionType, tint: androidx.compose.ui.graphics.Color) {
    when (type) {
        ConnectionType.Mesh ->
            Icon(painterResource(R.drawable.ic_mesh), null, tint = tint, modifier = Modifier.size(16.dp))
        ConnectionType.Radio ->
            Icon(painterResource(R.drawable.ic_radio), null, tint = tint, modifier = Modifier.size(16.dp))
        ConnectionType.Online -> PukaarIcon(Sym.wifi, null, size = 16.dp, tint = tint)
        ConnectionType.Gateway -> PukaarIcon(Sym.volunteerActivism, null, size = 16.dp, tint = tint)
        ConnectionType.Isolated -> PukaarIcon(Sym.signalCellularOff, null, size = 16.dp, tint = tint)
    }
}

@Composable
private fun statusColorFor(type: ConnectionType): StatusColor {
    val s = MaterialTheme.status
    return when (type) {
        ConnectionType.Online, ConnectionType.Gateway -> s.confirmed
        ConnectionType.Mesh -> s.mesh
        ConnectionType.Radio -> s.radio
        ConnectionType.Isolated -> s.warning
    }
}

@Composable
fun connectionLabel(status: ConnectionStatus): String = when (status.type) {
    ConnectionType.Online -> stringResource(R.string.pk_conn_online)
    ConnectionType.Mesh -> pluralStringResource(R.plurals.pk_conn_mesh, status.peers, status.peers)
    ConnectionType.Radio -> stringResource(R.string.pk_conn_radio)
    ConnectionType.Isolated -> stringResource(R.string.pk_conn_isolated)
    ConnectionType.Gateway ->
        pluralStringResource(R.plurals.pk_conn_gateway, status.gatewayHelping, status.gatewayHelping)
}

@Composable
private fun AllPills() {
    Surface(color = MaterialTheme.colorScheme.surface) {
        Column(Modifier.padding(PukaarDimens.space4), verticalArrangement = Arrangement.spacedBy(PukaarDimens.space3)) {
            ConnectionPill(ConnectionStatus.Online)
            ConnectionPill(ConnectionStatus.mesh(4), onClick = {})
            ConnectionPill(ConnectionStatus.mesh(1))
            ConnectionPill(ConnectionStatus.Radio)
            ConnectionPill(ConnectionStatus.Isolated)
            ConnectionPill(ConnectionStatus.gateway(3))
        }
    }
}

@Preview(name = "Dark", widthDp = 360, heightDp = 800)
@Composable
private fun ConnectionPillDark() = PukaarTheme(PukaarThemeMode.Dark) { AllPills() }

@Preview(name = "Light", widthDp = 360, heightDp = 800)
@Composable
private fun ConnectionPillLight() = PukaarTheme(PukaarThemeMode.Light) { AllPills() }

@Preview(name = "Hindi 1.3x", widthDp = 360, heightDp = 800, locale = "hi", fontScale = 1.3f)
@Composable
private fun ConnectionPillHindi() = PukaarTheme(PukaarThemeMode.Dark) { AllPills() }
