package app.pukaar.ui.screens.sos

import android.text.format.DateFormat
import android.text.format.DateUtils
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.pukaar.sos.ActiveSos
import app.pukaar.sos.FamilyNotice
import app.pukaar.sos.FamilyStatus
import app.pukaar.sos.SosDetails
import app.pukaar.sos.SosEvent
import app.pukaar.sos.SosFlag
import app.pukaar.sos.SosLocation
import app.pukaar.sos.SosManager
import app.pukaar.sos.SosStage
import app.pukaar.ui.components.HopNode
import app.pukaar.ui.components.HopsPath
import app.pukaar.ui.components.InfoBox
import app.pukaar.ui.components.OutlineButton
import app.pukaar.ui.components.PreviewTheme
import app.pukaar.ui.components.PrimaryButton
import app.pukaar.ui.components.PukaarCard
import app.pukaar.ui.components.PukaarPreviews
import app.pukaar.ui.components.PukaarTopBar
import app.pukaar.ui.components.SectionHeader
import app.pukaar.ui.theme.PukaarDimens
import app.pukaar.ui.theme.PukaarIcon
import app.pukaar.ui.theme.StatusColor
import app.pukaar.ui.theme.Sym
import app.pukaar.ui.theme.status
import com.bitchat.android.R
import kotlinx.coroutines.delay
import java.util.Date
import java.util.Locale
import kotlin.math.abs

@Composable
fun SosStatusRoute(onBack: () -> Unit, onUpdateDetails: () -> Unit, onAddContacts: () -> Unit) {
    val sos by SosManager.active.collectAsState()
    val current = sos
    if (current == null) {
        LaunchedEffect(Unit) { onBack() }
        return
    }
    var confirmSafe by remember { mutableStateOf(false) }
    SosStatusScreen(
        sos = current,
        onBack = onBack,
        onUpdateDetails = onUpdateDetails,
        onAddContacts = onAddContacts,
        onSafe = { confirmSafe = true },
        onDone = {
            SosManager.dismiss()
            onBack()
        },
    )
    if (confirmSafe) {
        AlertDialog(
            onDismissRequest = { confirmSafe = false },
            title = { Text(stringResource(R.string.pk_sos_safe_confirm_title)) },
            text = { Text(stringResource(R.string.pk_sos_safe_confirm_body)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmSafe = false
                    SosManager.markSafe()
                }) { Text(stringResource(R.string.pk_sos_safe_confirm_yes)) }
            },
            dismissButton = { TextButton(onClick = { confirmSafe = false }) { Text(stringResource(R.string.pk_not_yet)) } },
        )
    }
}

@Composable
fun SosStatusScreen(
    sos: ActiveSos,
    onBack: () -> Unit,
    onUpdateDetails: () -> Unit,
    onAddContacts: () -> Unit,
    onSafe: () -> Unit,
    onDone: () -> Unit,
) {
    val s = MaterialTheme.status
    Column(Modifier.fillMaxSize()) {
        PukaarTopBar(stringResource(R.string.pk_sos_status_title), onBack = onBack)
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = PukaarDimens.space4),
            verticalArrangement = Arrangement.spacedBy(PukaarDimens.space3),
        ) {
            HeroCard(sos)

            SectionHeader(stringResource(R.string.pk_sos_how_travelled))
            HopsPath(hopNodes(sos))

            SectionHeader(stringResource(R.string.pk_sos_what_happened))
            Timeline(sos.events)

            Row(verticalAlignment = Alignment.CenterVertically) {
                SectionHeader(stringResource(R.string.pk_sos_what_you_sent), Modifier.weight(1f))
                if (!sos.closed) OutlineButton(stringResource(R.string.pk_sos_update_details), onUpdateDetails, icon = Sym.edit)
            }
            WhatYouSent(sos)

            SectionHeader(stringResource(R.string.pk_sos_family_told))
            FamilyList(sos.family, onAddContacts)

            if (!sos.closed) InfoBox(Sym.lightbulb, stringResource(R.string.pk_sos_tip), null)
            Box(Modifier.size(PukaarDimens.space2))
        }
        // "I'm safe now" stays at the bottom while scrolling.
        Surface(color = MaterialTheme.colorScheme.surface) {
            Box(Modifier.navigationBarsPadding().padding(PukaarDimens.space4)) {
                if (sos.closed) {
                    PrimaryButton(stringResource(R.string.pk_done), onDone, Modifier.fillMaxWidth())
                } else {
                    PrimaryButton(
                        stringResource(R.string.pk_sos_im_safe_now),
                        onSafe,
                        Modifier.fillMaxWidth(),
                        icon = Sym.verifiedUser,
                        containerColor = s.confirmed.main,
                        contentColor = s.confirmed.onMain,
                    )
                }
            }
        }
    }
}

private data class Hero(val title: Int, val body: Int, val colors: StatusColor?, val icon: String)

@Composable
private fun heroFor(sos: ActiveSos): Hero {
    val s = MaterialTheme.status
    return when {
        sos.safeAt != null -> Hero(R.string.pk_sos_hero_safe, R.string.pk_sos_hero_safe_body, s.confirmed, Sym.verifiedUser)
        else -> when (sos.stage) {
            SosStage.Sending -> Hero(R.string.pk_sos_hero_sending, R.string.pk_sos_hero_sending_body, s.warning, Sym.schedule)
            SosStage.Relayed -> Hero(R.string.pk_sos_hero_relayed, R.string.pk_sos_hero_relayed_body, s.mesh, Sym.sendToMobile)
            SosStage.SentByRadio -> Hero(R.string.pk_delivery_radio, R.string.pk_sos_hero_relayed_body, s.radio, Sym.sendToMobile)
            SosStage.HelpNotified -> Hero(R.string.pk_sos_hero_notified, R.string.pk_sos_hero_notified_body, s.confirmed, Sym.checkCircle)
            SosStage.RescuerAttending -> Hero(R.string.pk_sos_hero_attending, R.string.pk_sos_hero_attending_body, s.confirmed, Sym.verifiedUser)
            SosStage.Resolved -> Hero(R.string.pk_sos_hero_resolved, R.string.pk_sos_hero_resolved_body, s.confirmed, Sym.checkCircle)
        }
    }
}

@Composable
private fun HeroCard(sos: ActiveSos) {
    val hero = heroFor(sos)
    val context = LocalContext.current
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { delay(30_000); now = System.currentTimeMillis() } }
    val time = DateFormat.getTimeFormat(context).format(Date(sos.startedAt))
    val ago = DateUtils.getRelativeTimeSpanString(sos.startedAt, now, DateUtils.MINUTE_IN_MILLIS).toString()
    PukaarCard(Modifier.fillMaxWidth(), color = hero.colors?.container ?: MaterialTheme.colorScheme.surfaceContainerHigh, contentColor = hero.colors?.onContainer ?: MaterialTheme.colorScheme.onSurface) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(PukaarDimens.space1)) {
            PukaarIcon(hero.icon, null, size = 18.dp)
            Text(stringResource(R.string.pk_sos_sent_at, time, ago), style = MaterialTheme.typography.labelLarge)
        }
        Text(stringResource(hero.title), style = MaterialTheme.typography.displaySmall)
        Text(stringResource(hero.body), style = MaterialTheme.typography.bodyLarge)
        sos.handledBy?.takeIf { it.isNotBlank() }?.let { Text(it, style = MaterialTheme.typography.titleSmall) }
    }
}

@Composable
private fun hopNodes(sos: ActiveSos): List<HopNode> {
    val s = MaterialTheme.status
    // Lit only if nearby phones really took it; a direct upload skips the mesh.
    val relayed = sos.relayPeers > 0
    val server = sos.stage.ordinal >= SosStage.HelpNotified.ordinal
    val meshLabel = if (sos.relayPeers > 0) pluralStringResource(R.plurals.pk_phones_short, sos.relayPeers, sos.relayPeers)
    else stringResource(R.string.pk_sos_hop_nearby)
    return listOf(
        HopNode(stringResource(R.string.pk_sos_hop_you), icon = Sym.smartphone, colors = null, reached = true),
        if (sos.stage == SosStage.SentByRadio) HopNode(stringResource(R.string.pk_sos_hop_radio), painter = R.drawable.ic_radio, colors = s.radio, reached = true)
        else HopNode(meshLabel, painter = R.drawable.ic_mesh, colors = s.mesh, reached = relayed),
        HopNode(stringResource(R.string.pk_sos_hop_internet), icon = Sym.sendToMobile, colors = s.confirmed, reached = server),
        HopNode(stringResource(R.string.pk_sos_hop_rescuers), icon = Sym.cloudDone, colors = s.confirmed, reached = server),
    )
}

@Composable
private fun Timeline(events: List<SosEvent>) {
    val context = LocalContext.current
    val s = MaterialTheme.status
    Column(verticalArrangement = Arrangement.spacedBy(PukaarDimens.space3)) {
        events.forEach { event ->
            val color = when (event.stage) {
                SosStage.Sending -> MaterialTheme.colorScheme.onSurfaceVariant
                SosStage.Relayed -> s.mesh.main
                SosStage.SentByRadio -> s.radio.main
                else -> s.confirmed.main
            }
            Row(horizontalArrangement = Arrangement.spacedBy(PukaarDimens.space3)) {
                Box(Modifier.padding(top = 6.dp).size(12.dp).clip(CircleShape).background(color))
                Column(Modifier.weight(1f)) {
                    Text(stringResource(stageTitle(event.stage)), style = MaterialTheme.typography.titleMedium)
                    val detail = when {
                        event.stage == SosStage.Relayed && event.detail?.toIntOrNull() != null -> {
                            val n = event.detail.toInt()
                            pluralStringResource(R.plurals.pk_sos_passed_through, n, n)
                        }
                        else -> event.detail
                    }
                    if (!detail.isNullOrBlank()) Text(detail, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(DateFormat.getTimeFormat(context).format(Date(event.at)), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

fun stageTitle(stage: SosStage) = when (stage) {
    SosStage.Sending -> R.string.pk_sos_hero_sending
    SosStage.Relayed -> R.string.pk_delivery_relayed_plain
    SosStage.SentByRadio -> R.string.pk_delivery_radio
    SosStage.HelpNotified -> R.string.pk_sos_hero_notified
    SosStage.RescuerAttending -> R.string.pk_sos_hero_attending
    SosStage.Resolved -> R.string.pk_delivery_resolved
}

@Composable
private fun WhatYouSent(sos: ActiveSos) {
    PukaarCard(Modifier.fillMaxWidth()) {
        val loc = sos.location
        SentRow(Sym.locationOn, if (loc != null) formatCoords(loc.lat, loc.lon) else stringResource(R.string.pk_sos_no_location),
            loc?.accuracyM?.let { stringResource(R.string.pk_sos_accuracy, it) })
        val flags = sos.details.flags.map { stringResource(SosManager.flagLabel(it)) }
        SentRow(Sym.group, (listOf(pluralStringResource(R.plurals.pk_people_count, sos.details.people, sos.details.people)) + flags).joinToString(" · "), null)
        if (sos.details.message.isNotBlank()) SentRow(Sym.chat, "“${sos.details.message}”", null)
    }
}

@Composable
private fun SentRow(icon: String, text: String, secondary: String?) {
    Row(horizontalArrangement = Arrangement.spacedBy(PukaarDimens.space3)) {
        PukaarIcon(icon, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Column {
            Text(text, style = MaterialTheme.typography.bodyLarge)
            if (secondary != null) Text(secondary, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

fun formatCoords(lat: Double, lon: Double): String =
    String.format(Locale.getDefault(), "%.4f° %s, %.4f° %s", abs(lat), if (lat >= 0) "N" else "S", abs(lon), if (lon >= 0) "E" else "W")

@Composable
private fun FamilyList(family: List<FamilyNotice>, onAddContacts: () -> Unit) {
    val s = MaterialTheme.status
    if (family.isEmpty()) {
        InfoBox(Sym.contacts, stringResource(R.string.pk_sos_no_contacts), null, action = {
            OutlineButton(stringResource(R.string.pk_add_contacts), onAddContacts, icon = Sym.personAdd)
        })
        return
    }
    PukaarCard(Modifier.fillMaxWidth(), padding = PukaarDimens.space3) {
        family.forEach { notice ->
            val (icon, text, color) = when (notice.status) {
                FamilyStatus.SentDirect -> Triple(Sym.check, stringResource(R.string.pk_family_sent_direct), s.confirmed.main)
                FamilyStatus.SentByServer -> Triple(Sym.check, stringResource(R.string.pk_family_sent_server), s.confirmed.main)
                FamilyStatus.Sending -> Triple(Sym.schedule, stringResource(R.string.pk_family_sending), MaterialTheme.colorScheme.onSurfaceVariant)
                FamilyStatus.Waiting -> Triple(Sym.schedule, stringResource(R.string.pk_family_waiting), MaterialTheme.colorScheme.onSurfaceVariant)
                FamilyStatus.Failed -> Triple(Sym.schedule, stringResource(R.string.pk_family_retry), s.warning.main)
            }
            Row(Modifier.fillMaxWidth().padding(vertical = PukaarDimens.space1), verticalAlignment = Alignment.CenterVertically) {
                Text(notice.name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(PukaarDimens.space1)) {
                    PukaarIcon(icon, null, size = 16.dp, tint = color)
                    Text(text, style = app.pukaar.ui.theme.PukaarTextStyles.deliveryState, color = color)
                }
            }
        }
    }
}

/** "Update details": edit people, flags and message, then send the SOS again. */
@Composable
fun SosUpdateRoute(onBack: () -> Unit) {
    val sos by SosManager.active.collectAsState()
    val current = sos ?: run {
        LaunchedEffect(Unit) { onBack() }
        return
    }
    var details by remember { mutableStateOf(current.details) }
    var editing by remember { mutableStateOf(current.details.message.isNotBlank()) }
    Column(Modifier.fillMaxSize()) {
        PukaarTopBar(stringResource(R.string.pk_sos_update_details), onBack = onBack)
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = PukaarDimens.space4),
            verticalArrangement = Arrangement.spacedBy(PukaarDimens.space3),
        ) {
            Text(stringResource(R.string.pk_sos_update_body), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            SosDetailsEditor(
                details = details,
                onChange = { details = it },
                onRed = false,
                editingMessage = editing,
                onEditMessage = { editing = it },
                messageBudget = app.pukaar.sos.SosPacket.messageBudget(app.pukaar.data.PukaarStore.profile.value.name),
            )
        }
        Box(Modifier.navigationBarsPadding().padding(PukaarDimens.space4)) {
            PrimaryButton(stringResource(R.string.pk_sos_send_update), {
                SosManager.updateDetails(details)
                onBack()
            }, Modifier.fillMaxWidth(), icon = Sym.send)
        }
    }
}

internal fun sampleSos(stage: SosStage = SosStage.HelpNotified): ActiveSos {
    val t = System.currentTimeMillis() - 120_000
    return ActiveSos(
        id = "a1b2c3d4", seq = 1, startedAt = t,
        details = SosDetails(4, setOf(SosFlag.Trapped, SosFlag.ChildOrElderly), "On the roof of the blue house behind the Shiv temple."),
        location = SosLocation(25.9814, 85.6721, 12, t), battery = 30, stage = stage,
        events = listOf(
            SosEvent(SosStage.Sending, t), SosEvent(SosStage.Relayed, t + 20_000, "2"),
            SosEvent(SosStage.HelpNotified, t + 110_000, "District Control Room, Darbhanga"),
        ),
        relayPeers = 2,
        family = listOf(
            FamilyNotice("1", "Suresh Kumar", "", FamilyStatus.SentByServer),
            FamilyNotice("2", "Priya Singh", "", FamilyStatus.Waiting),
        ),
        handledBy = "District Control Room, Darbhanga",
    )
}

@PukaarPreviews
@Composable
private fun SosStatusPreview() = PreviewTheme {
    SosStatusScreen(sampleSos(), {}, {}, {}, {}, {})
}
