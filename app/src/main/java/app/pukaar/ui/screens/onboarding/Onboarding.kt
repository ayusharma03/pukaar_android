package app.pukaar.ui.screens.onboarding

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import app.pukaar.data.PukaarStore
import app.pukaar.ui.components.InfoBox
import app.pukaar.ui.components.OutlineButton
import app.pukaar.ui.components.PlainButton
import app.pukaar.ui.components.PreviewTheme
import app.pukaar.ui.components.PrimaryButton
import app.pukaar.ui.components.PukaarCard
import app.pukaar.ui.components.PukaarPreviews
import app.pukaar.ui.components.StepDots
import app.pukaar.ui.components.Tag
import app.pukaar.ui.components.TonalButton
import app.pukaar.ui.screens.settings.ContactsEditor
import app.pukaar.ui.screens.settings.ProfileForm
import app.pukaar.ui.screens.settings.SosSetupContent
import app.pukaar.ui.screens.settings.rememberOfflineArea
import app.pukaar.map.OfflineMaps
import app.pukaar.ui.screens.sos.SosCountdownRoute
import app.pukaar.ui.theme.PukaarDimens
import app.pukaar.ui.theme.PukaarIcon
import app.pukaar.ui.theme.Sym
import app.pukaar.ui.theme.status
import com.bitchat.android.R
import com.bitchat.android.onboarding.PermissionManager
import com.bitchat.android.ui.LanguagePreferenceManager

private const val STEPS = 7 // dots count steps 1.2 to 1.8; the language step has none

/** First-launch onboarding (screens.md §1, handoff 2m to 2g). Calls [onFinished] at the end. */
@Composable
fun PukaarOnboarding(onFinished: () -> Unit) {
    // Survives the activity being recreated when the language changes.
    var step by rememberSaveable { mutableIntStateOf(0) }
    var practicing by rememberSaveable { mutableStateOf(false) }
    var practiceDone by remember { mutableStateOf(false) }

    if (practicing) {
        SosCountdownRoute(
            practice = true,
            onCancel = { practicing = false },
            onSent = { practicing = false },
            onPracticeDone = { practicing = false; practiceDone = true },
        )
        return
    }

    BackHandler(enabled = step > 0) { step-- }
    val next: () -> Unit = { step++ }
    val back: () -> Unit = { step-- }

    when (step) {
        0 -> LanguageStep(onPick = { tag ->
            if (!LanguagePreferenceManager.currentLanguageTag().startsWith(tag)) LanguagePreferenceManager.setLanguage(tag)
            step = 1
        })
        1 -> WelcomeStep(back, next)
        2 -> HowItWorksStep(back, next)
        3 -> PermissionsStep(back, next)
        4 -> DetailsStep(back, next)
        5 -> ContactsStep(back, next)
        6 -> OfflineStep(back, next)
        else -> SetupStep(back, onPractice = { practicing = true }, onFinish = {
            PukaarStore.setOnboardingDone()
            onFinished()
        })
    }

    if (practiceDone) {
        AlertDialog(
            onDismissRequest = { practiceDone = false },
            icon = { PukaarIcon(Sym.checkCircle, null) },
            title = { Text(stringResource(R.string.pk_practice_done_title)) },
            text = { Text(stringResource(R.string.pk_practice_done_body)) },
            confirmButton = { TextButton(onClick = { practiceDone = false }) { Text(stringResource(R.string.pk_ok)) } },
        )
    }
}

/** Common frame: back + dots, scrollable body, bottom actions. */
@Composable
private fun StepFrame(
    index: Int,
    onBack: (() -> Unit)?,
    title: String?,
    subtitle: String? = null,
    actions: @Composable ColumnScope.() -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
        Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = PukaarDimens.space1), verticalAlignment = Alignment.CenterVertically) {
            if (onBack != null) IconButton(onClick = onBack) { PukaarIcon(Sym.arrowBack, stringResource(R.string.pk_back)) }
            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) { if (index > 0) StepDots(index, STEPS) }
            Box(Modifier.size(48.dp))
        }
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = PukaarDimens.space4),
            verticalArrangement = Arrangement.spacedBy(PukaarDimens.space3),
        ) {
            if (title != null) Text(title, style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(top = PukaarDimens.space2).semantics { heading() })
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            content()
            Box(Modifier.size(PukaarDimens.space2))
        }
        Column(Modifier.padding(PukaarDimens.space4), verticalArrangement = Arrangement.spacedBy(PukaarDimens.space2), content = actions)
    }
}

// 1.1 Language (2m)
@Composable
fun LanguageStep(onPick: (String) -> Unit) {
    Column(
        Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(PukaarDimens.space5),
        verticalArrangement = Arrangement.spacedBy(PukaarDimens.space4, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(painterResource(R.drawable.ic_pukaar_mark), null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(96.dp))
        Text("पुकार · Pukaar", style = MaterialTheme.typography.headlineMedium)
        Box(Modifier.size(PukaarDimens.space3))
        LanguageButton("अ", "हिन्दी") { onPick("hi") }
        LanguageButton("A", "English") { onPick("en") }
        Text(stringResource(R.string.pk_onb_more_languages), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun LanguageButton(glyph: String, label: String, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 88.dp)
            .clip(MaterialTheme.shapes.large)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = PukaarDimens.space5),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(PukaarDimens.space4),
    ) {
        Box(Modifier.size(48.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer), contentAlignment = Alignment.Center) {
            Text(glyph, style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
        }
        Text(label, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
        PukaarIcon(Sym.arrowForward, null, tint = MaterialTheme.colorScheme.primary)
    }
}

// 1.2 Welcome (2c)
@Composable
private fun WelcomeStep(onBack: () -> Unit, onNext: () -> Unit) {
    StepFrame(1, onBack, null, actions = { PrimaryButton(stringResource(R.string.pk_continue), onNext, Modifier.fillMaxWidth()) }) {
        // Illustration placeholder: a family on a rooftop, a call rippling out (handoff open item).
        Box(
            Modifier.fillMaxWidth().aspectRatio(1.1f).clip(MaterialTheme.shapes.large).background(MaterialTheme.colorScheme.surfaceContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(painterResource(R.drawable.ic_pukaar_mark), null, tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f), modifier = Modifier.size(120.dp))
        }
        Text(stringResource(R.string.pk_onb_welcome_title), style = MaterialTheme.typography.headlineMedium)
        Text(stringResource(R.string.pk_onb_welcome_body), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

// 1.3 How it works (2n)
@Composable
private fun HowItWorksStep(onBack: () -> Unit, onNext: () -> Unit) {
    StepFrame(2, onBack, stringResource(R.string.pk_onb_how_title), actions = { PrimaryButton(stringResource(R.string.pk_continue), onNext, Modifier.fillMaxWidth()) }) {
        HowCard(listOf(Sym.smartphone, Sym.smartphone, Sym.smartphone), stringResource(R.string.pk_onb_how_1_title), stringResource(R.string.pk_onb_how_1_body))
        HowCard(listOf(Sym.smartphone, Sym.cloudDone), stringResource(R.string.pk_onb_how_2_title), stringResource(R.string.pk_onb_how_2_body))
        HowCard(listOf(Sym.cloudDone, Sym.locationOn), stringResource(R.string.pk_onb_how_3_title), stringResource(R.string.pk_onb_how_3_body))
        InfoBox(Sym.visibility, stringResource(R.string.pk_onb_how_visible), MaterialTheme.status.warning)
    }
}

@Composable
private fun HowCard(icons: List<String>, title: String, body: String) {
    PukaarCard(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(PukaarDimens.space1)) {
            icons.forEachIndexed { i, icon ->
                if (i > 0) Text("···", color = MaterialTheme.status.mesh.main, style = MaterialTheme.typography.titleMedium)
                Box(Modifier.size(40.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer), contentAlignment = Alignment.Center) {
                    PukaarIcon(icon, null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
                }
            }
        }
        Text(title, style = MaterialTheme.typography.titleMedium)
        Text(body, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

// 1.4 Permissions (2o)
private data class PermissionItem(val key: String, val icon: String, val title: Int, val why: Int, val impact: Int, val permissions: List<String>)

private fun permissionItems(context: Context): List<PermissionItem> {
    val location = listOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
    val nearby = PermissionManager(context).getRequiredPermissions().filterNot { it in location }
    return listOfNotNull(
        PermissionItem("nearby", Sym.bluetooth, R.string.pk_perm_nearby, R.string.pk_perm_nearby_why, R.string.pk_perm_nearby_impact, nearby),
        PermissionItem("location", Sym.locationOn, R.string.pk_perm_location, R.string.pk_perm_location_why, R.string.pk_perm_location_impact, location),
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            PermissionItem("notifications", Sym.notifications, R.string.pk_perm_notifications, R.string.pk_perm_notifications_why, R.string.pk_perm_notifications_impact, listOf(Manifest.permission.POST_NOTIFICATIONS))
        } else null,
        PermissionItem("sms", Sym.sms, R.string.pk_perm_sms, R.string.pk_perm_sms_why, R.string.pk_perm_sms_impact, listOf(Manifest.permission.SEND_SMS)),
        PermissionItem("phone", Sym.call, R.string.pk_perm_phone, R.string.pk_perm_phone_why, R.string.pk_perm_phone_impact, listOf(Manifest.permission.CALL_PHONE)),
    )
}

private fun granted(context: Context, perms: List<String>) =
    perms.all { ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED }

@Composable
private fun PermissionsStep(onBack: () -> Unit, onNext: () -> Unit) {
    val context = LocalContext.current
    val items = remember { permissionItems(context) }
    // key → null (not asked yet), true (allowed), false (denied)
    val results = remember { mutableStateMapOf<String, Boolean?>().apply { items.forEach { put(it.key, if (granted(context, it.permissions)) true else null) } } }
    var asking by remember { mutableStateOf<PermissionItem?>(null) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        asking?.let { item ->
            // Location counts as allowed with approximate location too.
            results[item.key] = if (item.key == "location") it.values.any { granted -> granted } || granted(context, item.permissions) else granted(context, item.permissions)
        }
        asking = null
    }
    fun request(item: PermissionItem) {
        val activity = context as? Activity
        val blocked = results[item.key] == false && activity != null &&
            item.permissions.none { ActivityCompat.shouldShowRequestPermissionRationale(activity, it) }
        if (blocked) {
            // Android won't ask again: open this app's settings page.
            context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)))
        } else {
            asking = item
            launcher.launch(item.permissions.toTypedArray())
        }
    }

    StepFrame(3, onBack, stringResource(R.string.pk_onb_perm_title), actions = {
        PrimaryButton(stringResource(R.string.pk_continue), {
            PermissionManager(context).markOnboardingComplete()
            onNext()
        }, Modifier.fillMaxWidth())
    }) {
        items.forEach { item ->
            PukaarCard(Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(PukaarDimens.space3)) {
                    PukaarIcon(item.icon, null, tint = MaterialTheme.colorScheme.primary)
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(item.title), style = MaterialTheme.typography.titleMedium)
                        Text(stringResource(item.why), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    when (results[item.key]) {
                        true -> Row(verticalAlignment = Alignment.CenterVertically) {
                            PukaarIcon(Sym.checkCircle, null, size = 18.dp, tint = MaterialTheme.status.confirmed.main)
                            Text(" " + stringResource(R.string.pk_allowed), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.status.confirmed.main)
                        }
                        null -> TonalButton(stringResource(R.string.pk_allow), { request(item) })
                        false -> Unit
                    }
                }
                if (results[item.key] == false) {
                    InfoBox(Sym.warning, stringResource(item.impact), MaterialTheme.status.warning, action = {
                        OutlineButton(stringResource(R.string.pk_try_again), { request(item) })
                    })
                }
            }
        }
    }
}

// 1.5 Your details (2d)
@Composable
private fun DetailsStep(onBack: () -> Unit, onNext: () -> Unit) {
    val saved by PukaarStore.profile.collectAsState()
    var profile by remember { mutableStateOf(saved) }
    StepFrame(4, onBack, stringResource(R.string.pk_onb_details_title), stringResource(R.string.pk_onb_details_body), actions = {
        PrimaryButton(stringResource(R.string.pk_continue), { PukaarStore.saveProfile(profile); onNext() }, Modifier.fillMaxWidth())
    }) {
        ProfileForm(profile, { profile = it })
    }
}

// 1.6 Emergency contacts (2e)
@Composable
private fun ContactsStep(onBack: () -> Unit, onNext: () -> Unit) {
    val contacts by PukaarStore.contacts.collectAsState()
    StepFrame(5, onBack, stringResource(R.string.pk_onb_contacts_title), stringResource(R.string.pk_onb_contacts_body), actions = {
        Row(horizontalArrangement = Arrangement.spacedBy(PukaarDimens.space2)) {
            PlainButton(stringResource(R.string.pk_skip_for_now), onNext, Modifier.weight(1f))
            PrimaryButton(stringResource(R.string.pk_continue), onNext, Modifier.weight(1f), enabled = contacts.isNotEmpty())
        }
    }) {
        ContactsEditor(contacts, showTests = false)
    }
}

// 1.7 Offline area (2f)
@Composable
private fun OfflineStep(onBack: () -> Unit, onNext: () -> Unit) {
    val context = LocalContext.current
    val area = rememberOfflineArea()
    val mapState by OfflineMaps.state.collectAsState()
    val internet by app.pukaar.device.DeviceStatus.internet.collectAsState()
    val onWifi = remember { isOnWifi(context) }
    val places = remember { app.pukaar.data.Places.load(context) }
    StepFrame(6, onBack, stringResource(R.string.pk_onb_offline_title), stringResource(R.string.pk_onb_offline_body), actions = {
        Row(horizontalArrangement = Arrangement.spacedBy(PukaarDimens.space2)) {
            when (mapState) {
                is OfflineMaps.State.Saved -> PrimaryButton(stringResource(R.string.pk_continue), onNext, Modifier.fillMaxWidth())
                // The download keeps going in the background.
                is OfflineMaps.State.Downloading -> PrimaryButton(stringResource(R.string.pk_onb_offline_continue_bg), onNext, Modifier.fillMaxWidth())
                else -> {
                    PlainButton(stringResource(R.string.pk_later), { PukaarStore.setOfflinePending(true); onNext() }, Modifier.weight(1f))
                    PrimaryButton(
                        stringResource(R.string.pk_onb_offline_download, area?.estimateMb ?: 0),
                        { area?.let { OfflineMaps.download(context, it.bounds, it.name) } },
                        Modifier.weight(2f),
                        icon = Sym.download,
                        enabled = internet && area != null,
                    )
                }
            }
        }
    }) {
        PukaarCard(Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(PukaarDimens.space1)) {
                PukaarIcon(Sym.myLocation, null, size = 18.dp, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(stringResource(R.string.pk_onb_offline_detected), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(area?.name ?: "…", style = MaterialTheme.typography.titleLarge)
        }
        PukaarCard(Modifier.fillMaxWidth(), padding = PukaarDimens.space3) {
            OfflineItemRow(Sym.map, R.string.pk_offline_map, R.string.pk_offline_map_detail) {
                when (val st = mapState) {
                    is OfflineMaps.State.Saved -> Tag(stringResource(R.string.pk_offline_saved), MaterialTheme.status.confirmed)
                    is OfflineMaps.State.Downloading -> Text("${st.percent}%", style = MaterialTheme.typography.labelLarge)
                    else -> Text(stringResource(R.string.pk_offline_about_mb, area?.estimateMb ?: 0), style = MaterialTheme.typography.labelLarge)
                }
            }
            (mapState as? OfflineMaps.State.Downloading)?.let {
                androidx.compose.material3.LinearProgressIndicator(progress = { it.percent / 100f }, modifier = Modifier.fillMaxWidth())
            }
            OfflineItemRow(Sym.nightShelter, R.string.pk_offline_places, R.string.pk_offline_places_detail) {
                if (places.places.isNotEmpty()) Tag(stringResource(R.string.pk_offline_saved), MaterialTheme.status.confirmed)
            }
            OfflineItemRow(Sym.menuBook, R.string.pk_offline_guides, R.string.pk_offline_guides_detail) {
                Tag(stringResource(R.string.pk_offline_saved), MaterialTheme.status.confirmed)
            }
        }
        when {
            mapState is OfflineMaps.State.Failed -> InfoBox(Sym.warning, stringResource(R.string.pk_offline_failed), MaterialTheme.status.warning)
            !internet -> InfoBox(Sym.wifiOff, stringResource(R.string.pk_onb_offline_no_internet), MaterialTheme.status.warning)
            onWifi -> InfoBox(Sym.wifi, stringResource(R.string.pk_onb_offline_wifi), null)
        }
    }
}

@Composable
private fun OfflineItemRow(icon: String, title: Int, detail: Int, trailing: @Composable () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = PukaarDimens.space1), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(PukaarDimens.space3)) {
        PukaarIcon(icon, null, tint = MaterialTheme.colorScheme.primary)
        Column(Modifier.weight(1f)) {
            Text(stringResource(title), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(detail), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        trailing()
    }
}

private fun isOnWifi(context: Context): Boolean = runCatching {
    val cm = context.getSystemService(android.net.ConnectivityManager::class.java)
    cm.getNetworkCapabilities(cm.activeNetwork)?.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI) == true
}.getOrDefault(false)

// 1.8 SOS setup (2g)
@Composable
private fun SetupStep(onBack: () -> Unit, onPractice: () -> Unit, onFinish: () -> Unit) {
    val settings by PukaarStore.settings.collectAsState()
    StepFrame(7, onBack, stringResource(R.string.pk_onb_setup_title), stringResource(R.string.pk_onb_setup_body), actions = {
        PrimaryButton(stringResource(R.string.pk_finish), onFinish, Modifier.fillMaxWidth())
    }) {
        SosSetupContent(settings, onPractice)
    }
}

@PukaarPreviews
@Composable
private fun LanguagePreview() = PreviewTheme { LanguageStep {} }

@PukaarPreviews
@Composable
private fun WelcomePreview() = PreviewTheme { WelcomeStep({}, {}) }

@PukaarPreviews
@Composable
private fun HowItWorksPreview() = PreviewTheme { HowItWorksStep({}, {}) }
