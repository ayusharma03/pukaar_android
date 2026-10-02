package app.pukaar.ui.screens.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import app.pukaar.ui.theme.status
import android.location.Location
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import app.pukaar.data.BloodGroupUnknown
import app.pukaar.data.CountdownChoices
import app.pukaar.data.MaxEmergencyContacts
import app.pukaar.data.OfflineItemKind
import app.pukaar.data.OfflineRegion
import app.pukaar.data.Places
import app.pukaar.data.PukaarSettings
import app.pukaar.data.PukaarStore
import app.pukaar.data.ShakeSensitivity
import app.pukaar.device.Locations
import app.pukaar.sos.SosDetails
import app.pukaar.sos.SosManager
import app.pukaar.system.SosWidgets
import app.pukaar.system.requestAddSosTile
import app.pukaar.ui.components.ChoiceChips
import app.pukaar.ui.components.InfoBox
import app.pukaar.ui.components.ListRow
import app.pukaar.ui.components.OutlineButton
import app.pukaar.ui.components.PreviewTheme
import app.pukaar.ui.components.PrimaryButton
import app.pukaar.ui.components.PukaarCard
import app.pukaar.ui.components.PukaarPreviews
import app.pukaar.ui.components.PukaarTopBar
import app.pukaar.ui.components.SectionHeader
import app.pukaar.ui.components.TonalButton
import app.pukaar.ui.screens.network.RadioUnavailableDialog
import app.pukaar.ui.theme.PukaarDimens
import app.pukaar.ui.theme.PukaarIcon
import app.pukaar.ui.theme.PukaarThemeMode
import app.pukaar.ui.theme.Sym
import com.bitchat.android.R
import com.bitchat.android.ui.LanguagePreferenceManager

/** The offline area as it stands: places and guides ship with the app; map tiles aren't available yet. */
fun offlineRegion(context: Context): OfflineRegion {
    val places = Places.load(context)
    return OfflineRegion(
        id = "bundled",
        name = places.region.ifBlank { context.getString(R.string.pk_offline_unknown_region) },
        items = listOf(
            app.pukaar.data.OfflineItem(OfflineItemKind.Map, 142, saved = false),
            app.pukaar.data.OfflineItem(OfflineItemKind.Places, 3, saved = places.places.isNotEmpty()),
            app.pukaar.data.OfflineItem(OfflineItemKind.Guides, 8, saved = true),
        ),
    )
}

/** Dialog shown wherever a map download is offered: honest about what this build can do. */
@Composable
fun MapDownloadUnavailableDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.pk_offline_unavailable_title)) },
        text = { Text(stringResource(R.string.pk_offline_unavailable_body)) },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.pk_ok)) } },
    )
}

fun currentLanguageIsHindi(): Boolean = LanguagePreferenceManager.currentLanguageTag().startsWith("hi")

/** Settings (2v). */
@Composable
fun SettingsRoute(
    onBack: () -> Unit,
    onProfile: () -> Unit,
    onContacts: () -> Unit,
    onSosSetup: () -> Unit,
    onAbout: () -> Unit,
) {
    val context = LocalContext.current
    val settings by PukaarStore.settings.collectAsState()
    val profile by PukaarStore.profile.collectAsState()
    val contacts by PukaarStore.contacts.collectAsState()
    var dialog by remember { mutableStateOf<String?>(null) }
    val region = remember { offlineRegion(context) }

    Column(Modifier.fillMaxSize()) {
        PukaarTopBar(stringResource(R.string.pk_settings_title), onBack = onBack)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            ListRow(
                stringResource(R.string.pk_settings_language),
                subtitle = stringResource(if (currentLanguageIsHindi()) R.string.pk_lang_hindi else R.string.pk_lang_english),
                icon = Sym.translate,
                onClick = { dialog = "language" },
                trailing = { Text(stringResource(R.string.pk_change), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary) },
            )
            val blood = profile.bloodGroup?.takeIf { it != BloodGroupUnknown }
            ListRow(
                stringResource(R.string.pk_settings_profile),
                subtitle = listOfNotNull(profile.name.ifBlank { null }, blood).joinToString(" · ").ifBlank { stringResource(R.string.pk_settings_profile_empty) },
                icon = Sym.person,
                onClick = onProfile,
                trailing = { Chevron() },
            )
            ListRow(
                stringResource(R.string.pk_settings_contacts),
                subtitle = stringResource(R.string.pk_contacts_count, contacts.size, MaxEmergencyContacts),
                icon = Sym.contacts,
                onClick = onContacts,
                trailing = { Chevron() },
            )

            Group(stringResource(R.string.pk_settings_sos_triggers))
            SwitchRow(
                stringResource(R.string.pk_settings_shake), stringResource(R.string.pk_settings_shake_hint), Sym.vibration,
                settings.shakeEnabled,
            ) { on -> PukaarStore.updateSettings { it.copy(shakeEnabled = on) } }
            if (settings.shakeEnabled) {
                Column(Modifier.padding(start = 56.dp, end = PukaarDimens.space4, bottom = PukaarDimens.space2)) {
                    Text(stringResource(R.string.pk_settings_sensitivity), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    ChoiceChips(
                        ShakeSensitivity.entries, settings.shakeSensitivity,
                        label = { stringResource(sensitivityLabel(it)) },
                        onSelect = { s -> PukaarStore.updateSettings { it.copy(shakeSensitivity = s) } },
                    )
                }
            }
            ListRow(
                stringResource(R.string.pk_settings_countdown),
                subtitle = pluralStringResource(R.plurals.pk_seconds, settings.countdownSeconds, settings.countdownSeconds),
                icon = Sym.timer,
                onClick = { dialog = "countdown" },
                trailing = { Chevron() },
            )
            ListRow(
                stringResource(R.string.pk_settings_tile_widget),
                subtitle = stringResource(R.string.pk_settings_tile_widget_hint),
                icon = Sym.widgets,
                onClick = onSosSetup,
                trailing = { Chevron() },
            )

            Group(stringResource(R.string.pk_settings_offline))
            ListRow(
                region.name,
                subtitle = stringResource(R.string.pk_settings_offline_saved, region.totalMb - region.missingMb),
                icon = Sym.map,
            )
            ListRow(
                stringResource(R.string.pk_settings_offline_download),
                subtitle = stringResource(R.string.pk_settings_offline_needs_internet),
                icon = Sym.addLocationAlt,
                onClick = { dialog = "download" },
                trailing = { Chevron() },
            )

            Group(stringResource(R.string.pk_settings_radio))
            ListRow(
                stringResource(R.string.pk_settings_radio_none),
                subtitle = stringResource(R.string.pk_settings_radio_hint),
                iconPainter = R.drawable.ic_radio,
                trailing = { TonalButton(stringResource(R.string.pk_settings_radio_pair), { dialog = "radio" }) },
            )

            Group(stringResource(R.string.pk_settings_battery))
            SwitchRow(
                stringResource(R.string.pk_settings_battery_auto), stringResource(R.string.pk_settings_battery_auto_hint), Sym.batterySaver,
                settings.batterySaverAuto,
            ) { on -> PukaarStore.updateSettings { it.copy(batterySaverAuto = on) } }
            SwitchRow(
                stringResource(R.string.pk_settings_battery_now), null, Sym.energySavingsLeaf,
                settings.batterySaverNow,
            ) { on -> PukaarStore.updateSettings { it.copy(batterySaverNow = on) } }

            Group(stringResource(R.string.pk_settings_theme))
            Column(Modifier.padding(horizontal = PukaarDimens.space4), verticalArrangement = Arrangement.spacedBy(PukaarDimens.space2)) {
                ChoiceChips(
                    listOf(PukaarThemeMode.Dark, PukaarThemeMode.Light, PukaarThemeMode.Sunlight, PukaarThemeMode.System),
                    settings.theme,
                    label = { stringResource(themeLabel(it)) },
                    onSelect = { mode -> PukaarStore.updateSettings { it.copy(theme = mode) } },
                )
                Text(stringResource(R.string.pk_settings_theme_hint), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            Group(stringResource(R.string.pk_settings_calling))
            ListRow(
                stringResource(R.string.pk_call_district),
                subtitle = settings.districtControlNumber.ifBlank { stringResource(R.string.pk_call_district_set) },
                icon = Sym.supportAgent,
                onClick = { dialog = "district" },
                trailing = { Chevron() },
            )

            Box(Modifier.size(PukaarDimens.space3))
            ListRow(
                stringResource(R.string.pk_settings_about),
                subtitle = stringResource(R.string.pk_settings_about_hint, app.pukaar.PUKAAR_VERSION),
                icon = Sym.info,
                onClick = onAbout,
                trailing = { Chevron() },
            )
            Box(Modifier.navigationBarsPadding().size(PukaarDimens.space4))
        }
    }

    when (dialog) {
        "language" -> LanguageDialog { dialog = null }
        "countdown" -> CountdownDialog(settings.countdownSeconds, { dialog = null }) { s -> PukaarStore.updateSettings { it.copy(countdownSeconds = s) } }
        "download" -> MapDownloadUnavailableDialog { dialog = null }
        "radio" -> RadioUnavailableDialog { dialog = null }
        "district" -> DistrictDialog(settings.districtControlNumber, { dialog = null }) { n -> PukaarStore.updateSettings { it.copy(districtControlNumber = n) } }
    }
}

@Composable
private fun Chevron() = PukaarIcon(Sym.chevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)

@Composable
private fun Group(title: String) = SectionHeader(title, Modifier.padding(horizontal = PukaarDimens.space4, vertical = PukaarDimens.space2))

@Composable
private fun SwitchRow(title: String, subtitle: String?, icon: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    ListRow(
        title,
        subtitle = subtitle,
        icon = icon,
        modifier = Modifier.selectable(selected = checked, role = Role.Switch) { onChange(!checked) },
        trailing = { Switch(checked = checked, onCheckedChange = null) },
    )
}

fun sensitivityLabel(s: ShakeSensitivity) = when (s) {
    ShakeSensitivity.Low -> R.string.pk_low
    ShakeSensitivity.Medium -> R.string.pk_medium
    ShakeSensitivity.High -> R.string.pk_high
}

fun themeLabel(mode: PukaarThemeMode) = when (mode) {
    PukaarThemeMode.Dark -> R.string.pk_theme_dark
    PukaarThemeMode.Light -> R.string.pk_theme_light
    PukaarThemeMode.Sunlight -> R.string.pk_theme_sunlight
    PukaarThemeMode.System -> R.string.pk_theme_system
}

@Composable
fun LanguageDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.pk_settings_language)) },
        text = {
            Column {
                listOf("hi" to "हिन्दी", "en" to "English").forEach { (tag, label) ->
                    val selected = if (tag == "hi") currentLanguageIsHindi() else !currentLanguageIsHindi()
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = 56.dp).selectable(selected, role = Role.RadioButton) {
                            LanguagePreferenceManager.setLanguage(tag)
                            onDismiss()
                        },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = selected, onClick = null)
                        Text(label, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(start = PukaarDimens.space3))
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.pk_cancel)) } },
    )
}

@Composable
private fun CountdownDialog(current: Int, onDismiss: () -> Unit, onPick: (Int) -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.pk_settings_countdown)) },
        text = {
            Column {
                CountdownChoices.forEach { s ->
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = 56.dp).selectable(s == current, role = Role.RadioButton) { onPick(s); onDismiss() },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = s == current, onClick = null)
                        Text(pluralStringResource(R.plurals.pk_seconds, s, s), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(start = PukaarDimens.space3))
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.pk_cancel)) } },
    )
}

@Composable
fun DistrictDialog(current: String, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var number by remember { mutableStateOf(current) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.pk_call_district)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(PukaarDimens.space2)) {
                Text(stringResource(R.string.pk_settings_district_hint), style = MaterialTheme.typography.bodyMedium)
                OutlinedTextField(number, { number = it }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone))
            }
        },
        confirmButton = { TextButton(onClick = { onSave(number); onDismiss() }) { Text(stringResource(R.string.pk_save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.pk_cancel)) } },
    )
}

/** Settings > Profile. */
@Composable
fun ProfileRoute(onBack: () -> Unit) {
    val saved by PukaarStore.profile.collectAsState()
    var profile by remember { mutableStateOf(saved) }
    Column(Modifier.fillMaxSize()) {
        PukaarTopBar(stringResource(R.string.pk_settings_profile), onBack = onBack)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = PukaarDimens.space4), verticalArrangement = Arrangement.spacedBy(PukaarDimens.space3)) {
            Text(stringResource(R.string.pk_profile_why), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            ProfileForm(profile, { profile = it })
        }
        PrimaryButton(stringResource(R.string.pk_save), { PukaarStore.saveProfile(profile); onBack() }, Modifier.fillMaxWidth().navigationBarsPadding().padding(PukaarDimens.space4))
    }
}

/** Emergency contacts with test status and the exact SMS preview (2w). */
@Composable
fun ContactsRoute(onBack: () -> Unit) {
    val context = LocalContext.current
    val contacts by PukaarStore.contacts.collectAsState()
    val profile by PukaarStore.profile.collectAsState()
    val location by produceState<Location?>(null) { value = Locations.lastKnown(context) }
    Column(Modifier.fillMaxSize()) {
        PukaarTopBar(stringResource(R.string.pk_settings_contacts), onBack = onBack)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = PukaarDimens.space4), verticalArrangement = Arrangement.spacedBy(PukaarDimens.space3)) {
            ContactsEditor(contacts, showTests = true)
            SectionHeader(stringResource(R.string.pk_contacts_preview_title))
            val name = profile.name.ifBlank { context.getString(R.string.pk_someone) }
            val text = SosManager.buildSmsText(context, name, SosDetails(people = 1), location?.latitude, location?.longitude, System.currentTimeMillis(), app.pukaar.device.DeviceStatus.batteryNow())
            PukaarCard {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(PukaarDimens.space2)) {
                    PukaarIcon(Sym.sms, null, size = 18.dp, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(stringResource(R.string.pk_contacts_preview_from, name), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(text, style = MaterialTheme.typography.bodyLarge)
            }
            Text(stringResource(R.string.pk_contacts_preview_note), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Box(Modifier.navigationBarsPadding().size(PukaarDimens.space4))
        }
    }
}

/**
 * SOS setup (2g): Quick Settings tile, widget, shake and a practice countdown. Shared by onboarding
 * (with Finish) and Settings.
 */
@Composable
fun SosSetupContent(settings: PukaarSettings, onPractice: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var tileResult by remember { mutableStateOf<Boolean?>(null) }
    var widgetResult by remember { mutableStateOf<Boolean?>(null) }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(PukaarDimens.space3)) {
        PukaarCard(padding = 0.dp) {
            ListRow(
                stringResource(R.string.pk_setup_tile),
                subtitle = stringResource(if (tileResult == false) R.string.pk_setup_tile_manual else R.string.pk_setup_tile_hint),
                icon = Sym.toggleOn,
                trailing = {
                    if (tileResult == true) PukaarIcon(Sym.checkCircle, stringResource(R.string.pk_added), tint = MaterialTheme.colorScheme.primary)
                    else TonalButton(stringResource(R.string.pk_add), { requestAddSosTile(context) { tileResult = it } })
                },
            )
            ListRow(
                stringResource(R.string.pk_setup_widget),
                subtitle = stringResource(if (widgetResult == false) R.string.pk_setup_widget_manual else R.string.pk_setup_widget_hint),
                icon = Sym.widgets,
                trailing = {
                    if (widgetResult == true) PukaarIcon(Sym.checkCircle, stringResource(R.string.pk_added), tint = MaterialTheme.colorScheme.primary)
                    else TonalButton(stringResource(R.string.pk_add), { widgetResult = SosWidgets.requestPin(context, medium = true) })
                },
            )
            ListRow(
                stringResource(R.string.pk_settings_shake),
                subtitle = stringResource(R.string.pk_settings_shake_hint),
                icon = Sym.vibration,
                modifier = Modifier.selectable(settings.shakeEnabled, role = Role.Switch) {
                    PukaarStore.updateSettings { it.copy(shakeEnabled = !settings.shakeEnabled) }
                },
                trailing = { Switch(checked = settings.shakeEnabled, onCheckedChange = null) },
            )
        }
        if (settings.shakeEnabled) FullScreenAlertCheck()
        InfoBox(Sym.timer, pluralStringResource(R.plurals.pk_setup_countdown_note, settings.countdownSeconds, settings.countdownSeconds), null)
        OutlineButton(stringResource(R.string.pk_setup_practice), onPractice, Modifier.fillMaxWidth(), icon = Sym.playArrow)
        Text(stringResource(R.string.pk_setup_practice_hint), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/**
 * Android 14+ makes full-screen alerts opt-in. Without it, shaking a locked phone only posts a
 * notification instead of opening the SOS countdown, so offer the setting here.
 */
@Composable
private fun FullScreenAlertCheck() {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) return
    val context = LocalContext.current
    val lifecycle = androidx.lifecycle.compose.LocalLifecycleOwner.current.lifecycle
    var allowed by remember { mutableStateOf(canUseFullScreen(context)) }
    androidx.compose.runtime.DisposableEffect(lifecycle) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) allowed = canUseFullScreen(context)
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    if (allowed) return
    InfoBox(
        Sym.warning,
        stringResource(R.string.pk_setup_fullscreen_body),
        MaterialTheme.status.warning,
        title = stringResource(R.string.pk_setup_fullscreen_title),
        action = {
            TonalButton(stringResource(R.string.pk_allow), {
                runCatching {
                    context.startActivity(
                        Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, Uri.fromParts("package", context.packageName, null))
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    )
                }
            })
        },
    )
}

private fun canUseFullScreen(context: Context): Boolean =
    Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE ||
        context.getSystemService(android.app.NotificationManager::class.java)?.canUseFullScreenIntent() != false

@Composable
fun SosSetupRoute(onBack: () -> Unit, onPractice: () -> Unit) {
    val settings by PukaarStore.settings.collectAsState()
    Column(Modifier.fillMaxSize()) {
        PukaarTopBar(stringResource(R.string.pk_setup_title), onBack = onBack)
        SosSetupContent(settings, onPractice, Modifier.verticalScroll(rememberScrollState()).padding(horizontal = PukaarDimens.space4))
    }
}

/** About: version, credits and open-source licences (SETUP.md asks to credit Mukta and Material Symbols). */
@Composable
fun AboutRoute(onBack: () -> Unit) {
    val context = LocalContext.current
    var licence by remember { mutableStateOf<String?>(null) }
    Column(Modifier.fillMaxSize()) {
        PukaarTopBar(stringResource(R.string.pk_settings_about), onBack = onBack)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = PukaarDimens.space4), verticalArrangement = Arrangement.spacedBy(PukaarDimens.space3)) {
            Text(stringResource(R.string.pk_about_version, app.pukaar.PUKAAR_VERSION), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.pk_about_body), style = MaterialTheme.typography.bodyLarge)
            SectionHeader(stringResource(R.string.pk_about_credits))
            PukaarCard(padding = 0.dp) {
                ListRow(stringResource(R.string.pk_about_bitchat), subtitle = stringResource(R.string.pk_about_bitchat_licence), icon = Sym.forum)
                ListRow("Mukta", subtitle = "SIL Open Font License 1.1", icon = Sym.translate, onClick = { licence = "OFL-Mukta.txt" }, trailing = { Chevron() })
                ListRow("Material Symbols", subtitle = "Apache License 2.0", icon = Sym.widgets, onClick = { licence = "Apache-2.0-Material-Symbols.txt" }, trailing = { Chevron() })
            }
        }
    }
    licence?.let { file ->
        val text = remember(file) { runCatching { context.assets.open("pukaar/licenses/$file").bufferedReader().use { it.readText() } }.getOrDefault("") }
        AlertDialog(
            onDismissRequest = { licence = null },
            text = { Column(Modifier.verticalScroll(rememberScrollState())) { Text(text, style = MaterialTheme.typography.bodySmall) } },
            confirmButton = { TextButton(onClick = { licence = null }) { Text(stringResource(R.string.pk_ok)) } },
        )
    }
}

@PukaarPreviews
@Composable
private fun SosSetupPreview() = PreviewTheme {
    SosSetupContent(PukaarSettings(), {}, Modifier.padding(PukaarDimens.space4))
}

@PukaarPreviews
@Composable
private fun SettingsPreview() = PreviewTheme { SettingsRoute({}, {}, {}, {}, {}) }

@PukaarPreviews
@Composable
private fun ProfilePreview() = PreviewTheme { ProfileRoute {} }

@PukaarPreviews
@Composable
private fun AboutPreview() = PreviewTheme { AboutRoute {} }
