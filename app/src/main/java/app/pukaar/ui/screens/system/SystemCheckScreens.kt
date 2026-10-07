package app.pukaar.ui.screens.system

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.pukaar.ui.components.InfoBox
import app.pukaar.ui.components.ListRow
import app.pukaar.ui.components.OutlineButton
import app.pukaar.ui.components.PlainButton
import app.pukaar.ui.components.PreviewTheme
import app.pukaar.ui.components.PrimaryButton
import app.pukaar.ui.components.PukaarCard
import app.pukaar.ui.components.PukaarPreviews
import app.pukaar.ui.theme.PukaarDimens
import app.pukaar.ui.theme.PukaarIcon
import app.pukaar.ui.theme.Sym
import app.pukaar.ui.theme.status
import com.bitchat.android.R
import com.bitchat.android.onboarding.BatteryOptimizationStatus
import com.bitchat.android.onboarding.BluetoothStatus
import com.bitchat.android.onboarding.LocationStatus
import com.bitchat.android.onboarding.PermissionCategory
import com.bitchat.android.onboarding.PermissionType

/**
 * Pukaar-styled versions of bitchat's start-up checks (Bluetooth, location, battery, permissions,
 * background location, starting, error). MainActivity shows these instead of bitchat's screens;
 * bitchat's state machine and managers are unchanged.
 */
@Composable
private fun CheckFrame(
    icon: String,
    title: String,
    body: String,
    loading: Boolean = false,
    actions: @Composable ColumnScope.() -> Unit,
    extra: @Composable ColumnScope.() -> Unit = {},
) {
    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = PukaarDimens.space4),
            verticalArrangement = Arrangement.spacedBy(PukaarDimens.space4),
        ) {
            Box(Modifier.padding(top = PukaarDimens.space7).size(72.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer), contentAlignment = Alignment.Center) {
                if (loading) CircularProgressIndicator(Modifier.size(36.dp), color = MaterialTheme.colorScheme.onPrimaryContainer)
                else PukaarIcon(icon, null, size = 36.dp, tint = MaterialTheme.colorScheme.onPrimaryContainer)
            }
            Text(title, style = MaterialTheme.typography.headlineMedium, modifier = Modifier.semantics { heading() })
            Text(body, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            extra()
        }
        Column(Modifier.padding(PukaarDimens.space4), verticalArrangement = Arrangement.spacedBy(PukaarDimens.space2), content = actions)
    }
}

@Composable
fun PukaarBluetoothCheck(status: BluetoothStatus, loading: Boolean, onEnable: () -> Unit, onRetry: () -> Unit, onSkip: () -> Unit) {
    if (status == BluetoothStatus.NOT_SUPPORTED) {
        CheckFrame(Sym.bluetooth, stringResource(R.string.pk_check_bt_unsupported_title), stringResource(R.string.pk_check_bt_unsupported_body), actions = {
            PrimaryButton(stringResource(R.string.pk_continue), onSkip, Modifier.fillMaxWidth())
        })
        return
    }
    CheckFrame(Sym.bluetooth, stringResource(R.string.pk_check_bt_title), stringResource(R.string.pk_check_bt_body), loading, actions = {
        PrimaryButton(stringResource(R.string.pk_check_bt_turn_on), onEnable, Modifier.fillMaxWidth(), enabled = !loading)
        OutlineButton(stringResource(R.string.pk_check_again), onRetry, Modifier.fillMaxWidth())
        PlainButton(stringResource(R.string.pk_check_bt_skip), onSkip, Modifier.fillMaxWidth())
    }) {
        InfoBox(Sym.warning, stringResource(R.string.pk_check_bt_impact), MaterialTheme.status.warning)
    }
}

@Composable
fun PukaarLocationCheck(status: LocationStatus, loading: Boolean, onEnable: () -> Unit, onRetry: () -> Unit) {
    val unavailable = status == LocationStatus.NOT_AVAILABLE
    CheckFrame(
        Sym.locationOn,
        stringResource(if (unavailable) R.string.pk_check_loc_unavailable_title else R.string.pk_check_loc_title),
        stringResource(if (unavailable) R.string.pk_check_loc_unavailable_body else R.string.pk_check_loc_body),
        loading,
        actions = {
            if (!unavailable) PrimaryButton(stringResource(R.string.pk_check_loc_turn_on), onEnable, Modifier.fillMaxWidth(), enabled = !loading)
            OutlineButton(stringResource(R.string.pk_check_again), onRetry, Modifier.fillMaxWidth())
        },
    ) {
        if (!unavailable) InfoBox(Sym.info, stringResource(R.string.pk_check_loc_why), null)
    }
}

@Composable
fun PukaarBatteryCheck(status: BatteryOptimizationStatus, loading: Boolean, onDisable: () -> Unit, onRetry: () -> Unit, onSkip: () -> Unit) {
    CheckFrame(Sym.batterySaver, stringResource(R.string.pk_check_battery_title), stringResource(R.string.pk_check_battery_body), loading, actions = {
        if (status == BatteryOptimizationStatus.ENABLED) {
            PrimaryButton(stringResource(R.string.pk_check_battery_allow), onDisable, Modifier.fillMaxWidth(), enabled = !loading)
            OutlineButton(stringResource(R.string.pk_check_again), onRetry, Modifier.fillMaxWidth())
        }
        PlainButton(stringResource(R.string.pk_skip_for_now), onSkip, Modifier.fillMaxWidth())
    }) {
        PukaarCard(padding = 0.dp) {
            ListRow(stringResource(R.string.pk_check_battery_point1), icon = Sym.checkCircle, iconTint = MaterialTheme.status.confirmed.main)
            ListRow(stringResource(R.string.pk_check_battery_point2), icon = Sym.checkCircle, iconTint = MaterialTheme.status.confirmed.main)
        }
    }
}

/** bitchat asks again for permissions that are still missing (for example after they were revoked). */
@Composable
fun PukaarPermissionsCheck(categories: List<PermissionCategory>, onContinue: () -> Unit) {
    val missing = categories.filterNot { it.isGranted }.ifEmpty { categories }
    CheckFrame(Sym.verifiedUser, stringResource(R.string.pk_check_perm_title), stringResource(R.string.pk_check_perm_body), actions = {
        PrimaryButton(stringResource(R.string.pk_continue), onContinue, Modifier.fillMaxWidth())
    }) {
        PukaarCard(padding = 0.dp) {
            missing.forEach { c ->
                val (icon, title, why) = permissionLabels(c.type)
                ListRow(stringResource(title), subtitle = stringResource(why), icon = icon)
            }
        }
    }
}

private fun permissionLabels(type: PermissionType): Triple<String, Int, Int> = when (type) {
    PermissionType.NEARBY_DEVICES, PermissionType.WIFI_AWARE -> Triple(Sym.bluetooth, R.string.pk_perm_nearby, R.string.pk_perm_nearby_why)
    PermissionType.PRECISE_LOCATION, PermissionType.BACKGROUND_LOCATION -> Triple(Sym.locationOn, R.string.pk_perm_location, R.string.pk_perm_location_why)
    PermissionType.NOTIFICATIONS -> Triple(Sym.notifications, R.string.pk_perm_notifications, R.string.pk_perm_notifications_why)
    PermissionType.MICROPHONE -> Triple(Sym.chat, R.string.pk_perm_microphone, R.string.pk_perm_microphone_why)
    PermissionType.BATTERY_OPTIMIZATION -> Triple(Sym.batterySaver, R.string.pk_check_battery_title, R.string.pk_check_battery_point1)
    PermissionType.OTHER -> Triple(Sym.settings, R.string.pk_perm_other, R.string.pk_perm_other_why)
}

@Composable
fun PukaarBackgroundLocationCheck(onContinue: () -> Unit, onRetry: () -> Unit, onSkip: () -> Unit) {
    CheckFrame(Sym.locationOn, stringResource(R.string.pk_check_bg_title), stringResource(R.string.pk_check_bg_body), actions = {
        PrimaryButton(stringResource(R.string.pk_check_bg_allow), onContinue, Modifier.fillMaxWidth())
        OutlineButton(stringResource(R.string.pk_check_again), onRetry, Modifier.fillMaxWidth())
        PlainButton(stringResource(R.string.pk_skip_for_now), onSkip, Modifier.fillMaxWidth())
    }) {
        InfoBox(Sym.info, stringResource(R.string.pk_check_bg_how), null)
        InfoBox(Sym.verifiedUser, stringResource(R.string.pk_check_bg_privacy), MaterialTheme.status.confirmed)
    }
}

@Composable
fun PukaarStarting() {
    Column(
        Modifier.fillMaxSize().padding(PukaarDimens.space5),
        verticalArrangement = Arrangement.spacedBy(PukaarDimens.space4, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(painterResource(R.drawable.ic_pukaar_mark), null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(88.dp))
        CircularProgressIndicator()
        Text(stringResource(R.string.pk_check_starting), style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
    }
}

@Composable
fun PukaarStartError(detail: String, onRetry: () -> Unit, onOpenSettings: () -> Unit) {
    CheckFrame(Sym.warning, stringResource(R.string.pk_check_error_title), stringResource(R.string.pk_check_error_body), actions = {
        PrimaryButton(stringResource(R.string.pk_try_again), onRetry, Modifier.fillMaxWidth())
        OutlineButton(stringResource(R.string.pk_check_open_settings), onOpenSettings, Modifier.fillMaxWidth())
    }) {
        // bitchat's message is English and technical; shown small for support.
        if (detail.isNotBlank()) Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@PukaarPreviews
@Composable
private fun BluetoothCheckPreview() = PreviewTheme { PukaarBluetoothCheck(BluetoothStatus.DISABLED, false, {}, {}, {}) }

@PukaarPreviews
@Composable
private fun BatteryCheckPreview() = PreviewTheme { PukaarBatteryCheck(BatteryOptimizationStatus.ENABLED, false, {}, {}, {}) }

@PukaarPreviews
@Composable
private fun BackgroundLocationPreview() = PreviewTheme { PukaarBackgroundLocationCheck({}, {}, {}) }

@PukaarPreviews
@Composable
private fun StartingPreview() = PreviewTheme { PukaarStarting() }
