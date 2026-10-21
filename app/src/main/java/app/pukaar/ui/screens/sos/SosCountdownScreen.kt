package app.pukaar.ui.screens.sos

import android.location.Location
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.pukaar.data.PukaarStore
import app.pukaar.device.Locations
import app.pukaar.sos.SosDetails
import app.pukaar.sos.SosFlag
import app.pukaar.sos.SosManager
import app.pukaar.sos.SosPacket
import app.pukaar.ui.components.CountdownRing
import app.pukaar.ui.components.PreviewTheme
import app.pukaar.ui.components.PukaarPreviews
import app.pukaar.ui.theme.PukaarDimens
import app.pukaar.ui.theme.PukaarIcon
import app.pukaar.ui.theme.Sym
import app.pukaar.ui.theme.status
import com.bitchat.android.R
import kotlinx.coroutines.delay

/** Where the location stands while the countdown runs. */
sealed interface LocationState {
    data object Searching : LocationState
    data class Found(val accuracyM: Int?) : LocationState
    data object NoPermission : LocationState
    data object Unavailable : LocationState
}

/**
 * Stateful SOS countdown (2k): fetches location, runs the timer and sends when it reaches 0.
 * Cancel returns to where the user was. In practice mode nothing is sent.
 */
@Composable
fun SosCountdownRoute(
    practice: Boolean,
    onCancel: () -> Unit,
    onSent: () -> Unit,
    onPracticeDone: () -> Unit = onCancel,
) {
    val context = LocalContext.current
    val seconds = PukaarStore.settings.value.countdownSeconds
    var location by remember { mutableStateOf<Location?>(null) }
    var locationState by remember { mutableStateOf<LocationState>(LocationState.Searching) }

    LaunchedEffect(Unit) {
        if (!Locations.hasPermission(context)) {
            locationState = LocationState.NoPermission
            return@LaunchedEffect
        }
        val loc = Locations.current(context, 15_000)
        location = loc
        locationState = if (loc != null) LocationState.Found(if (loc.hasAccuracy()) loc.accuracy.toInt() else null) else LocationState.Unavailable
    }
    KeepScreenOn()

    SosCountdownScreen(
        totalSeconds = seconds,
        practice = practice,
        locationState = locationState,
        messageBudget = SosPacket.messageBudget(PukaarStore.profile.value.name),
        onCancel = onCancel,
        onFinished = { details ->
            if (practice) onPracticeDone() else {
                SosManager.start(details, location)
                onSent()
            }
        },
    )
}

@Composable
fun SosCountdownScreen(
    totalSeconds: Int,
    practice: Boolean,
    locationState: LocationState,
    messageBudget: Int,
    onCancel: () -> Unit,
    onFinished: (SosDetails) -> Unit,
    runTimer: Boolean = !LocalInspectionMode.current,
) {
    val s = MaterialTheme.status
    val red = s.sosFill
    val white = s.onSosFill
    var details by remember { mutableStateOf(SosDetails()) }
    var editingMessage by remember { mutableStateOf(false) }
    var remainingMs by remember { mutableFloatStateOf(totalSeconds * 1000f) }
    val haptics = LocalHapticFeedback.current
    val currentDetails by rememberUpdatedState(details)
    val finish by rememberUpdatedState(onFinished)

    LaunchedEffect(editingMessage, runTimer) {
        if (!runTimer || editingMessage) return@LaunchedEffect
        var lastWhole = kotlin.math.ceil(remainingMs / 1000f).toInt()
        while (remainingMs > 0) {
            delay(100)
            remainingMs -= 100f
            val whole = kotlin.math.ceil(remainingMs / 1000f).toInt()
            if (whole != lastWhole) {
                lastWhole = whole
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            }
        }
        finish(currentDetails)
    }

    val secondsLeft = kotlin.math.ceil(remainingMs / 1000f).toInt().coerceAtLeast(0)
    Column(
        Modifier
            .fillMaxSize()
            .background(red)
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(
            Modifier.padding(start = PukaarDimens.space5, end = PukaarDimens.space5, top = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(PukaarDimens.space1),
        ) {
            Text(
                stringResource(if (practice) R.string.pk_countdown_title_practice else R.string.pk_countdown_title),
                style = MaterialTheme.typography.headlineSmall,
                color = white,
                textAlign = TextAlign.Center,
            )
            LocationLine(locationState, white)
        }

        val description = stringResource(R.string.pk_countdown_seconds_left, secondsLeft)
        Box(
            Modifier
                .padding(vertical = PukaarDimens.space5)
                .semantics {
                    contentDescription = description
                    liveRegion = LiveRegionMode.Polite
                },
            contentAlignment = Alignment.Center,
        ) {
            CountdownRing(progress = remainingMs / (totalSeconds * 1000f), number = secondsLeft, color = white)
        }

        SosDetailsEditor(
            details = details,
            onChange = { details = it },
            onRed = true,
            editingMessage = editingMessage,
            onEditMessage = { editingMessage = it },
            messageBudget = messageBudget,
            modifier = Modifier.padding(horizontal = PukaarDimens.space4),
        )

        Box(Modifier.heightIn(min = PukaarDimens.space4))
        if (editingMessage) {
            Button(
                onClick = { finish(details) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = PukaarDimens.space4, vertical = PukaarDimens.space2)
                    .heightIn(min = PukaarDimens.primaryButtonHeight),
                shape = CircleShape,
                colors = ButtonDefaults.buttonColors(containerColor = Color.Black.copy(alpha = 0.25f), contentColor = white),
            ) {
                PukaarIcon(Sym.send, null, size = 20.dp)
                Box(Modifier.width(PukaarDimens.space2))
                Text(stringResource(if (practice) R.string.pk_countdown_finish_practice else R.string.pk_countdown_send_now), style = MaterialTheme.typography.titleMedium)
            }
        }
        Button(
            onClick = onCancel,
            modifier = Modifier
                .fillMaxWidth()
                .padding(PukaarDimens.space4)
                .heightIn(min = PukaarDimens.countdownCancelHeight),
            shape = RoundedCornerShape(28.dp),
            colors = ButtonDefaults.buttonColors(containerColor = white, contentColor = red),
        ) {
            PukaarIcon(Sym.close, null, size = 28.dp)
            Box(Modifier.width(PukaarDimens.space2))
            Text(stringResource(R.string.pk_cancel), style = MaterialTheme.typography.headlineSmall)
        }
    }
}

@Composable
private fun LocationLine(state: LocationState, color: Color) {
    val text = when (state) {
        LocationState.Searching -> stringResource(R.string.pk_countdown_location_searching)
        is LocationState.Found -> state.accuracyM?.let { stringResource(R.string.pk_countdown_location_found, it) }
            ?: stringResource(R.string.pk_countdown_location_found_plain)
        LocationState.NoPermission -> stringResource(R.string.pk_countdown_location_denied)
        LocationState.Unavailable -> stringResource(R.string.pk_countdown_location_none)
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(PukaarDimens.space1)) {
        PukaarIcon(if (state is LocationState.Found) Sym.myLocation else Sym.locationOn, null, size = 18.dp, tint = color)
        Text(text, style = MaterialTheme.typography.bodyLarge.copy(fontSize = MaterialTheme.typography.bodyLarge.fontSize * 0.94f), color = color, textAlign = TextAlign.Center)
    }
}

/**
 * People stepper, flag chips and the optional message. Shared by the countdown (white on red)
 * and "Update details" (normal surface).
 */
@Composable
fun SosDetailsEditor(
    details: SosDetails,
    onChange: (SosDetails) -> Unit,
    onRed: Boolean,
    editingMessage: Boolean,
    onEditMessage: (Boolean) -> Unit,
    messageBudget: Int,
    modifier: Modifier = Modifier,
) {
    val s = MaterialTheme.status
    val fg = if (onRed) s.onSosFill else MaterialTheme.colorScheme.onSurface
    val panel = if (onRed) Color.Black.copy(alpha = 0.2f) else MaterialTheme.colorScheme.surfaceContainer
    val selectedBg = if (onRed) s.onSosFill else MaterialTheme.colorScheme.secondaryContainer
    val selectedFg = if (onRed) s.sosFill else MaterialTheme.colorScheme.onSecondaryContainer
    val outline = if (onRed) s.onSosFill else MaterialTheme.colorScheme.outline

    Column(
        modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .background(panel)
            .padding(horizontal = PukaarDimens.space4, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(PukaarDimens.space3),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.pk_countdown_people), style = MaterialTheme.typography.titleMedium, color = fg, modifier = Modifier.weight(1f))
            StepperButton(Sym.remove, stringResource(R.string.pk_countdown_people_less), fg, enabled = details.people > 1) {
                onChange(details.copy(people = details.people - 1))
            }
            Text(
                details.people.toString(),
                style = MaterialTheme.typography.headlineMedium,
                color = fg,
                textAlign = TextAlign.Center,
                modifier = Modifier.width(44.dp),
            )
            StepperButton(Sym.add, stringResource(R.string.pk_countdown_people_more), fg, enabled = details.people < 99) {
                onChange(details.copy(people = details.people + 1))
            }
        }

        FlowRow(horizontalArrangement = Arrangement.spacedBy(PukaarDimens.space2), verticalArrangement = Arrangement.spacedBy(PukaarDimens.space2)) {
            SosFlagOrder.forEach { flag ->
                val selected = flag in details.flags
                Row(
                    Modifier
                        .heightIn(min = PukaarDimens.minTarget)
                        .clip(MaterialTheme.shapes.small)
                        .then(if (selected) Modifier.background(selectedBg) else Modifier.border(1.5.dp, outline, MaterialTheme.shapes.small))
                        .clickable(role = Role.Checkbox) {
                            onChange(details.copy(flags = if (selected) details.flags - flag else details.flags + flag))
                        }
                        .padding(start = if (selected) PukaarDimens.space2 else PukaarDimens.space3, end = PukaarDimens.space3),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(PukaarDimens.space1),
                ) {
                    if (selected) PukaarIcon(Sym.check, null, size = 18.dp, tint = selectedFg)
                    Text(stringResource(SosManager.flagLabel(flag)), style = MaterialTheme.typography.labelLarge, color = if (selected) selectedFg else fg)
                }
            }
        }

        if (!editingMessage && details.message.isBlank()) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = PukaarDimens.minTarget)
                    .clip(MaterialTheme.shapes.small)
                    .clickable(role = Role.Button) { onEditMessage(true) },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(PukaarDimens.space2),
            ) {
                PukaarIcon(Sym.edit, null, tint = fg)
                Text(stringResource(R.string.pk_countdown_add_message), style = MaterialTheme.typography.titleMedium, color = fg)
                if (onRed) Text(stringResource(R.string.pk_countdown_add_message_hint), style = MaterialTheme.typography.bodyMedium, color = fg.copy(alpha = 0.85f))
            }
        } else {
            val used = details.message.toByteArray(Charsets.UTF_8).size
            OutlinedTextField(
                value = details.message,
                onValueChange = { text ->
                    onChange(details.copy(message = app.pukaar.sos.Packets.truncateBytes(text, messageBudget)))
                },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.pk_countdown_message_label)) },
                supportingText = {
                    Text(stringResource(R.string.pk_countdown_message_bytes, (messageBudget - used).coerceAtLeast(0)))
                },
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                colors = if (onRed) {
                    OutlinedTextFieldDefaults.colors(
                        focusedTextColor = fg, unfocusedTextColor = fg,
                        focusedBorderColor = fg, unfocusedBorderColor = fg.copy(alpha = 0.7f),
                        focusedLabelColor = fg, unfocusedLabelColor = fg.copy(alpha = 0.85f),
                        cursorColor = fg,
                        focusedSupportingTextColor = fg.copy(alpha = 0.85f), unfocusedSupportingTextColor = fg.copy(alpha = 0.85f),
                    )
                } else {
                    OutlinedTextFieldDefaults.colors()
                },
                minLines = 2,
            )
        }
    }
}

/** Order of the flag chips in the design. */
val SosFlagOrder = listOf(SosFlag.Trapped, SosFlag.ChildOrElderly, SosFlag.Injured, SosFlag.NeedWater, SosFlag.NeedMedicine)

@Composable
private fun StepperButton(icon: String, description: String, color: Color, enabled: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .size(PukaarDimens.minTarget)
            .clip(CircleShape)
            .border(2.dp, color.copy(alpha = if (enabled) 1f else 0.4f), CircleShape)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        PukaarIcon(icon, null, tint = color.copy(alpha = if (enabled) 1f else 0.4f))
    }
}

/** Keeps the screen on while composed (countdown, direction). */
@Composable
fun KeepScreenOn() {
    val view = LocalView.current
    androidx.compose.runtime.DisposableEffect(view) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }
}

@PukaarPreviews
@Composable
private fun CountdownPreview() = PreviewTheme {
    SosCountdownScreen(
        totalSeconds = 5,
        practice = false,
        locationState = LocationState.Found(12),
        messageBudget = 120,
        onCancel = {},
        onFinished = {},
        runTimer = false,
    )
}
