package app.pukaar.ui.screens.chat

import android.text.format.DateFormat
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.pukaar.model.ConnectionStatus
import app.pukaar.model.ConnectionType
import app.pukaar.model.DeliveryStatus
import app.pukaar.sos.SosPacket
import app.pukaar.ui.components.ConnectionPill
import app.pukaar.ui.components.DeliveryState
import app.pukaar.ui.components.PreviewTheme
import app.pukaar.ui.components.PukaarPreviews
import app.pukaar.ui.components.PukaarTopBar
import app.pukaar.ui.components.SosPill
import app.pukaar.ui.theme.PukaarDimens
import app.pukaar.ui.theme.PukaarIcon
import app.pukaar.ui.theme.PukaarTextStyles
import app.pukaar.ui.theme.Sym
import app.pukaar.ui.theme.status
import com.bitchat.android.R
import java.util.Date

/** One row in the Disaster Relief feed. */
sealed interface ChatItem {
    val key: String
    val time: Long

    data class Official(override val key: String, val from: String, val text: String, override val time: Long) : ChatItem
    data class Sos(
        override val key: String,
        val packet: SosPacket,
        val distanceM: Float?,
        val own: Boolean,
        override val time: Long,
        val state: app.pukaar.sos.NearbySosState? = null,
    ) : ChatItem
    data class Safe(override val key: String, val name: String, override val time: Long) : ChatItem
    data class Message(
        override val key: String,
        val sender: String,
        val text: String,
        val distanceM: Float?,
        val hasLocation: Boolean,
        val own: Boolean,
        val delivery: DeliveryStatus?,
        override val time: Long,
        /** Attached location (FR-8), opened on the map when tapped. */
        val location: Pair<Double, Double>? = null,
    ) : ChatItem
}

data class ChatState(
    val items: List<ChatItem>,
    val connection: ConnectionStatus,
    val radioPaired: Boolean,
)

private const val RADIO_LIMIT_BYTES = 200

/** Disaster Relief chat (2l). */
@Composable
fun ChatScreen(
    state: ChatState,
    onSend: (text: String, attachLocation: Boolean) -> Unit,
    onSos: () -> Unit,
    onNetwork: () -> Unit,
    onOpenOnMap: (String) -> Unit = {},
) {
    var showInfo by remember { mutableStateOf(false) }
    var draft by remember { mutableStateOf("") }
    var attachLocation by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()

    LaunchedEffect(state.items.size) {
        if (state.items.isNotEmpty()) listState.animateScrollToItem(state.items.lastIndex)
    }

    Column(Modifier.fillMaxSize().imePadding()) {
        PukaarTopBar(
            title = stringResource(R.string.pk_chat_title),
            subtitle = pluralStringResource(R.plurals.pk_chat_subtitle, state.connection.reachable, state.connection.reachable),
            actions = {
                IconButton(onClick = { showInfo = true }) { PukaarIcon(Sym.info, stringResource(R.string.pk_chat_info)) }
                SosPill(onSos)
            },
        )
        ConnectionPill(state.connection, Modifier.padding(horizontal = PukaarDimens.space4), onClick = onNetwork)

        if (state.items.isEmpty()) {
            Column(
                Modifier.weight(1f).fillMaxWidth().padding(PukaarDimens.space6),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                PukaarIcon(Sym.forum, null, size = 48.dp, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(stringResource(R.string.pk_chat_empty_title), style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center, modifier = Modifier.padding(top = PukaarDimens.space3))
                Text(
                    stringResource(if (state.connection.type == ConnectionType.Isolated) R.string.pk_chat_empty_isolated else R.string.pk_chat_empty_body),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(PukaarDimens.space4),
                verticalArrangement = Arrangement.spacedBy(PukaarDimens.space3),
            ) {
                items(state.items, key = { it.key }) { item ->
                    when (item) {
                        is ChatItem.Official -> OfficialCard(item)
                        is ChatItem.Sos -> SosCard(item) {
                            if (item.packet.lat != null) onOpenOnMap("sos:${item.packet.id}")
                        }
                        is ChatItem.Safe -> SafeLine(item)
                        is ChatItem.Message -> {
                            val openLocation = { item.location?.let { onOpenOnMap("loc:${it.first},${it.second}") } ?: Unit }
                            if (item.own) OwnBubble(item, openLocation) else OtherBubble(item, openLocation)
                        }
                    }
                }
            }
        }

        QuickReplies { draft = it }
        Composer(
            draft = draft,
            onDraft = { draft = it },
            attachLocation = attachLocation,
            onToggleLocation = { attachLocation = !attachLocation },
            sendByRadio = state.connection.type == ConnectionType.Isolated && state.radioPaired,
            onSend = {
                if (draft.isNotBlank()) {
                    onSend(draft.trim(), attachLocation)
                    draft = ""
                    attachLocation = false
                }
            },
        )
    }

    if (showInfo) {
        AlertDialog(
            onDismissRequest = { showInfo = false },
            icon = { PukaarIcon(Sym.visibility, null) },
            title = { Text(stringResource(R.string.pk_chat_info_title)) },
            text = { Text(stringResource(R.string.pk_chat_info_body)) },
            confirmButton = { TextButton(onClick = { showInfo = false }) { Text(stringResource(R.string.pk_ok)) } },
        )
    }
}

@Composable
private fun timeText(time: Long): String = DateFormat.getTimeFormat(LocalContext.current).format(Date(time))

@Composable
private fun OfficialCard(item: ChatItem.Official) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .background(MaterialTheme.colorScheme.primaryContainer)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(PukaarDimens.space1),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(PukaarDimens.space1)) {
            PukaarIcon(Sym.verified, null, size = 18.dp, tint = MaterialTheme.colorScheme.primary, filled = true)
            Text(stringResource(R.string.pk_chat_official, item.from), style = PukaarTextStyles.deliveryState, color = MaterialTheme.colorScheme.onPrimaryContainer)
        }
        Text(item.text, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onPrimaryContainer)
        Text(timeText(item.time), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.align(Alignment.End))
    }
}

@Composable
private fun SosCard(item: ChatItem.Sos, onOpen: () -> Unit) {
    val s = MaterialTheme.status
    val p = item.packet
    Row(
        Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .background(s.sos.container)
            .clickable(enabled = p.lat != null && !item.own, role = Role.Button, onClick = onOpen)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(PukaarDimens.space3),
    ) {
        Box(Modifier.size(36.dp).clip(RoundedCornerShape(18.dp)).background(s.sosFill), contentAlignment = Alignment.Center) {
            PukaarIcon(Sym.sos, null, size = 22.dp, tint = s.onSosFill)
        }
        Column(Modifier.weight(1f)) {
            val name = if (item.own) stringResource(R.string.pk_chat_you) else p.name
            Text(stringResource(R.string.pk_chat_sos_from, name), style = MaterialTheme.typography.titleMedium, color = s.sos.onContainer)
            val flags = p.flags.map { stringResource(app.pukaar.sos.SosManager.flagLabel(it)) }
            Text(
                (listOf(pluralStringResource(R.plurals.pk_people_count, p.people, p.people)) + flags).joinToString(" · "),
                style = MaterialTheme.typography.bodyLarge,
                color = s.sos.onContainer,
            )
            if (p.message.isNotBlank()) Text("“${p.message}”", style = MaterialTheme.typography.bodyLarge, color = s.sos.onContainer)
            val meta = buildList {
                item.distanceM?.let { add(stringResource(R.string.pk_distance_away, formatDistance(it))) }
                add(stringResource(R.string.pk_chat_battery, p.battery))
                add(timeText(item.time))
            }
            Text(meta.joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = s.sos.onContainer)
            when (item.state) {
                app.pukaar.sos.NearbySosState.Safe -> app.pukaar.ui.components.Tag(stringResource(R.string.pk_chat_sos_safe), s.confirmed)
                app.pukaar.sos.NearbySosState.Attending -> app.pukaar.ui.components.Tag(stringResource(R.string.pk_sos_hero_attending), s.confirmed)
                app.pukaar.sos.NearbySosState.Resolved -> app.pukaar.ui.components.Tag(stringResource(R.string.pk_delivery_resolved), s.confirmed)
                else -> if (!item.own && p.lat != null) {
                    Text(stringResource(R.string.pk_chat_sos_open_map), style = PukaarTextStyles.deliveryState, color = s.sos.onContainer)
                }
            }
        }
    }
}

@Composable
private fun SafeLine(item: ChatItem.Safe) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
        PukaarIcon(Sym.verifiedUser, null, size = 16.dp, tint = MaterialTheme.status.confirmed.main)
        Text(
            " " + stringResource(R.string.pk_chat_safe_line, item.name, timeText(item.time)),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun OtherBubble(item: ChatItem.Message, onOpenLocation: () -> Unit) {
    Column(
        Modifier
            .widthIn(max = 300.dp)
            .clip(RoundedCornerShape(4.dp, 16.dp, 16.dp, 16.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(item.sender, style = PukaarTextStyles.deliveryState, color = MaterialTheme.colorScheme.primary)
        Text(item.text, style = MaterialTheme.typography.bodyLarge)
        if (item.hasLocation) LocationChip(item.distanceM, onOpenLocation)
        Text(timeText(item.time), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.align(Alignment.End))
    }
}

@Composable
private fun OwnBubble(item: ChatItem.Message, onOpenLocation: () -> Unit) {
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(PukaarDimens.space1)) {
        Column(
            Modifier
                .widthIn(max = 300.dp)
                .clip(RoundedCornerShape(16.dp, 4.dp, 16.dp, 16.dp))
                .background(MaterialTheme.colorScheme.primary)
                .padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            Text(item.text, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onPrimary)
            if (item.hasLocation) {
                Row(Modifier.clickable(role = Role.Button, onClick = onOpenLocation), verticalAlignment = Alignment.CenterVertically) {
                    PukaarIcon(Sym.locationOn, null, size = 16.dp, tint = MaterialTheme.colorScheme.onPrimary)
                    Text(stringResource(R.string.pk_chat_location_shared), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onPrimary)
                }
            }
        }
        item.delivery?.let { DeliveryState(it, time = timeText(item.time)) }
    }
}

@Composable
private fun LocationChip(distanceM: Float?, onClick: () -> Unit) {
    Row(
        Modifier
            .clip(MaterialTheme.shapes.small)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(PukaarDimens.space1),
    ) {
        PukaarIcon(Sym.locationOn, null, size = 16.dp, tint = MaterialTheme.colorScheme.primary)
        Text(
            distanceM?.let { stringResource(R.string.pk_distance_away, formatDistance(it)) } ?: stringResource(R.string.pk_chat_location_shared),
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

@Composable
private fun QuickReplies(onPick: (String) -> Unit) {
    val replies = listOf(
        stringResource(R.string.pk_chat_reply_safe),
        stringResource(R.string.pk_chat_reply_help),
        stringResource(R.string.pk_chat_reply_water),
    )
    LazyRow(
        contentPadding = PaddingValues(horizontal = PukaarDimens.space4),
        horizontalArrangement = Arrangement.spacedBy(PukaarDimens.space2),
    ) {
        items(replies) { reply ->
            Box(
                Modifier
                    .heightIn(min = PukaarDimens.minTarget)
                    .padding(vertical = PukaarDimens.space1)
                    .clip(MaterialTheme.shapes.small)
                    .background(MaterialTheme.colorScheme.secondaryContainer)
                    .clickable(role = Role.Button) { onPick(reply) }
                    .padding(horizontal = PukaarDimens.space3, vertical = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(reply, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSecondaryContainer)
            }
        }
    }
}

@Composable
private fun Composer(
    draft: String,
    onDraft: (String) -> Unit,
    attachLocation: Boolean,
    onToggleLocation: () -> Unit,
    sendByRadio: Boolean,
    onSend: () -> Unit,
) {
    val bytes = draft.toByteArray(Charsets.UTF_8).size
    Surface(color = MaterialTheme.colorScheme.surfaceContainer) {
        Column(Modifier.fillMaxWidth().padding(horizontal = PukaarDimens.space2, vertical = PukaarDimens.space2)) {
            if (attachLocation) {
                Text(
                    stringResource(R.string.pk_chat_location_attached),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = PukaarDimens.space3, bottom = PukaarDimens.space1),
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(PukaarDimens.space1)) {
                IconButton(onClick = onToggleLocation) {
                    PukaarIcon(
                        Sym.addLocationAlt,
                        stringResource(if (attachLocation) R.string.pk_chat_location_remove else R.string.pk_chat_location_add),
                        tint = if (attachLocation) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        filled = attachLocation,
                    )
                }
                OutlinedTextField(
                    value = draft,
                    onValueChange = onDraft,
                    modifier = Modifier.weight(1f),
                    placeholder = { Text(stringResource(R.string.pk_chat_placeholder)) },
                    shape = RoundedCornerShape(24.dp),
                    maxLines = 4,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    supportingText = if (bytes > RADIO_LIMIT_BYTES - 50) {
                        { Text(stringResource(R.string.pk_chat_radio_bytes, RADIO_LIMIT_BYTES - bytes)) }
                    } else null,
                )
                Button(
                    onClick = onSend,
                    enabled = draft.isNotBlank(),
                    modifier = Modifier.heightIn(min = PukaarDimens.minTarget),
                    shape = CircleShape,
                    colors = ButtonDefaults.buttonColors(),
                    contentPadding = PaddingValues(horizontal = PukaarDimens.space3),
                ) {
                    PukaarIcon(Sym.send, null, size = 18.dp)
                    Text(" " + stringResource(if (sendByRadio) R.string.pk_chat_send_radio else R.string.pk_chat_send), style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}

fun formatDistance(m: Float): String = if (m < 1000) "${m.toInt()} m" else String.format(java.util.Locale.getDefault(), "%.1f km", m / 1000f)

@PukaarPreviews
@Composable
private fun ChatPreview() = PreviewTheme {
    val now = System.currentTimeMillis()
    ChatScreen(
        ChatState(
            items = listOf(
                ChatItem.Official("o", "District Control Room", "Boats are going to Rampur school from 4 pm. If you are on a roof, stay where you can be seen.", now - 1_000_000),
                ChatItem.Message("m1", "Sunita Devi", "Water is up to the first floor near the temple. We are on the roof.", 1200f, true, false, null, now - 600_000),
                ChatItem.Sos("s", SosPacket("x", 1, 1.0, 1.0, 10, now / 1000, 18, 2, setOf(app.pukaar.sos.SosFlag.Injured, app.pukaar.sos.SosFlag.Trapped), "Ramesh K.", ""), 800f, false, now - 400_000),
                ChatItem.Message("m2", "Imran", "पुल पर पानी है, उधर मत जाइए।", null, false, false, null, now - 300_000),
                ChatItem.Message("m3", "Me", "We have drinking water for 10 people at the panchayat office.", null, false, true, DeliveryStatus.HelpNotified, now - 200_000),
                ChatItem.Message("m4", "Me", "Is the NH 27 bridge open?", null, false, true, DeliveryStatus.Relayed(3), now - 100_000),
            ),
            connection = ConnectionStatus.mesh(4, 18),
            radioPaired = false,
        ),
        { _, _ -> }, {}, {},
    )
}
