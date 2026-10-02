package app.pukaar.ui

import android.bluetooth.BluetoothAdapter
import android.content.Intent
import android.location.Location
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import app.pukaar.PukaarIntents
import app.pukaar.data.Places
import app.pukaar.data.PukaarStore
import app.pukaar.device.DeviceStatus
import app.pukaar.device.Locations
import app.pukaar.gateway.Gateway
import app.pukaar.sos.MeshBridge
import app.pukaar.sos.SosManager
import app.pukaar.ui.screens.calling.CallingScreen
import app.pukaar.ui.screens.calling.emergencyNumbers
import app.pukaar.ui.screens.calling.placeCall
import app.pukaar.ui.screens.chat.ChatOutbox
import app.pukaar.ui.screens.chat.ChatRoute
import app.pukaar.ui.screens.chat.unreadCount
import app.pukaar.ui.screens.guides.ChecklistScreen
import app.pukaar.ui.screens.guides.GuideDetailScreen
import app.pukaar.ui.screens.guides.GuidesScreen
import app.pukaar.ui.screens.guides.checklist
import app.pukaar.ui.screens.guides.guide
import app.pukaar.ui.screens.home.HomeScreen
import app.pukaar.ui.screens.home.HomeState
import app.pukaar.ui.screens.map.DirectionRoute
import app.pukaar.ui.screens.map.LatLon
import app.pukaar.ui.screens.map.MapScreen
import app.pukaar.ui.screens.map.MapState
import app.pukaar.ui.screens.network.NetworkScreen
import app.pukaar.ui.screens.network.NetworkState
import app.pukaar.ui.screens.settings.AboutRoute
import app.pukaar.ui.screens.settings.ContactsRoute
import app.pukaar.ui.screens.settings.DistrictDialog
import app.pukaar.ui.screens.settings.ProfileRoute
import app.pukaar.ui.screens.settings.SettingsRoute
import app.pukaar.ui.screens.settings.SosSetupRoute
import app.pukaar.ui.screens.sos.SosCountdownRoute
import app.pukaar.ui.screens.sos.SosStatusRoute
import app.pukaar.ui.screens.sos.SosUpdateRoute
import app.pukaar.ui.theme.PukaarIcon
import app.pukaar.ui.theme.Sym
import com.bitchat.android.R
import com.bitchat.android.ui.ChatViewModel

object Routes {
    const val HOME = "home"
    const val CHAT = PukaarIntents.ROUTE_CHAT
    const val MAP = "map"
    const val GUIDES = "guides"
    const val GUIDE = "guide/{id}"
    const val CHECKLIST = "checklist/{id}"
    const val NETWORK = "network"
    const val CALLING = "calling"
    const val DIRECTION = "direction/{id}"
    const val SOS_COUNTDOWN = "sos/countdown"
    const val SOS_PRACTICE = "sos/practice"
    const val SOS_STATUS = PukaarIntents.ROUTE_SOS_STATUS
    const val SOS_UPDATE = "sos/update"
    const val SETTINGS = "settings"
    const val PROFILE = "settings/profile"
    const val CONTACTS = "settings/contacts"
    const val SOS_SETUP = "settings/sos-setup"
    const val ABOUT = "settings/about"
}

private data class Tab(val route: String, val icon: String, val label: Int)

private val Tabs = listOf(
    Tab(Routes.HOME, Sym.home, R.string.pk_tab_home),
    Tab(Routes.CHAT, Sym.forum, R.string.pk_tab_chat),
    Tab(Routes.MAP, Sym.map, R.string.pk_tab_map),
    Tab(Routes.GUIDES, Sym.menuBook, R.string.pk_tab_guides),
)

/**
 * Pukaar's main UI after onboarding: four bottom tabs (Home, Chat, Map, Guides) and the screens
 * they open. [pendingRoute] comes from notifications, the tile and widgets.
 */
@Composable
fun PukaarNavHost(chatViewModel: ChatViewModel, pendingRoute: String?, onRouteHandled: () -> Unit) {
    val nav = rememberNavController()
    val backStack by nav.currentBackStackEntryAsState()
    val current = backStack?.destination?.route
    val showBar = Tabs.any { it.route == current }
    val context = LocalContext.current

    LaunchedEffect(pendingRoute) {
        if (pendingRoute != null) {
            nav.navigate(pendingRoute) { launchSingleTop = true }
            onRouteHandled()
        }
    }
    // Keep bitchat's nickname in step with the Pukaar profile, so chat shows the user's name.
    val profile by PukaarStore.profile.collectAsState()
    LaunchedEffect(profile.name) {
        if (profile.name.isNotBlank() && chatViewModel.nickname.value != profile.name) chatViewModel.setNickname(profile.name)
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        bottomBar = { if (showBar) BottomBar(nav, current) },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(bottom = padding.calculateBottomPadding())) {
            NavHost(nav, startDestination = Routes.HOME) {
                composable(Routes.HOME) { HomeRoute(nav, chatViewModel) }
                composable(Routes.CHAT) {
                    ChatRoute(chatViewModel, onSos = { nav.navigate(Routes.SOS_COUNTDOWN) }, onNetwork = { nav.navigate(Routes.NETWORK) })
                }
                composable(Routes.MAP) { MapRoute(nav) }
                composable(Routes.GUIDES) {
                    val connection by DeviceStatus.connection.collectAsState()
                    val checked by PukaarStore.checklists.collectAsState()
                    GuidesScreen(
                        connection, checked,
                        onSos = { nav.navigate(Routes.SOS_COUNTDOWN) },
                        onNetwork = { nav.navigate(Routes.NETWORK) },
                        onGuide = { nav.navigate("guide/$it") },
                        onChecklist = { nav.navigate("checklist/$it") },
                    )
                }
                composable(Routes.GUIDE) { entry ->
                    val def = entry.arguments?.getString("id")?.let { guide(it) }
                    if (def == null) LaunchedEffect(Unit) { nav.popBackStack() } else GuideDetailScreen(def) { nav.popBackStack() }
                }
                composable(Routes.CHECKLIST) { entry ->
                    val def = entry.arguments?.getString("id")?.let { checklist(it) }
                    val checked by PukaarStore.checklists.collectAsState()
                    if (def == null) LaunchedEffect(Unit) { nav.popBackStack() } else ChecklistScreen(
                        def, checked[def.id].orEmpty(),
                        onBack = { nav.popBackStack() },
                        onToggle = { item, on -> PukaarStore.setChecked(def.id, item, on) },
                        onReset = { PukaarStore.resetChecklist(def.id) },
                    )
                }
                composable(Routes.NETWORK) { NetworkRoute(nav) }
                composable(Routes.CALLING) { CallingRoute(nav) }
                composable(Routes.DIRECTION) { entry ->
                    val place = entry.arguments?.getString("id")?.let { Places.byId(context, it) }
                    if (place == null) LaunchedEffect(Unit) { nav.popBackStack() }
                    else DirectionRoute(place, onBack = { nav.popBackStack() }, onChange = { nav.popBackStack() })
                }
                composable(Routes.SOS_COUNTDOWN) {
                    SosCountdownRoute(
                        practice = false,
                        onCancel = { nav.popBackStack() },
                        onSent = { nav.navigate(Routes.SOS_STATUS) { popUpTo(Routes.SOS_COUNTDOWN) { inclusive = true } } },
                    )
                }
                composable(Routes.SOS_PRACTICE) {
                    var done by remember { mutableStateOf(false) }
                    if (!done) {
                        SosCountdownRoute(practice = true, onCancel = { nav.popBackStack() }, onSent = {}, onPracticeDone = { done = true })
                    } else {
                        PracticeDoneDialog { nav.popBackStack() }
                    }
                }
                composable(Routes.SOS_STATUS) {
                    SosStatusRoute(
                        onBack = { if (!nav.popBackStack()) nav.navigate(Routes.HOME) },
                        onUpdateDetails = { nav.navigate(Routes.SOS_UPDATE) },
                        onAddContacts = { nav.navigate(Routes.CONTACTS) },
                    )
                }
                composable(Routes.SOS_UPDATE) { SosUpdateRoute { nav.popBackStack() } }
                composable(Routes.SETTINGS) {
                    SettingsRoute(
                        onBack = { nav.popBackStack() },
                        onProfile = { nav.navigate(Routes.PROFILE) },
                        onContacts = { nav.navigate(Routes.CONTACTS) },
                        onSosSetup = { nav.navigate(Routes.SOS_SETUP) },
                        onAbout = { nav.navigate(Routes.ABOUT) },
                    )
                }
                composable(Routes.PROFILE) { ProfileRoute { nav.popBackStack() } }
                composable(Routes.CONTACTS) { ContactsRoute { nav.popBackStack() } }
                composable(Routes.SOS_SETUP) { SosSetupRoute(onBack = { nav.popBackStack() }, onPractice = { nav.navigate(Routes.SOS_PRACTICE) }) }
                composable(Routes.ABOUT) { AboutRoute { nav.popBackStack() } }
            }
        }
    }
}

@Composable
private fun BottomBar(nav: NavHostController, current: String?) {
    NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainer) {
        Tabs.forEach { tab ->
            val selected = current == tab.route
            NavigationBarItem(
                selected = selected,
                onClick = {
                    nav.navigate(tab.route) {
                        popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
                icon = { PukaarIcon(tab.icon, null, filled = selected) },
                label = { Text(stringResource(tab.label), style = MaterialTheme.typography.labelMedium) },
                colors = NavigationBarItemDefaults.colors(indicatorColor = MaterialTheme.colorScheme.secondaryContainer),
            )
        }
    }
}

@Composable
private fun HomeRoute(nav: NavHostController, chatViewModel: ChatViewModel) {
    val context = LocalContext.current
    val connection by DeviceStatus.connection.collectAsState()
    val battery by DeviceStatus.battery.collectAsState()
    val sos by SosManager.active.collectAsState()
    val offlinePending by PukaarStore.offlinePending.collectAsState()
    val messages by chatViewModel.messages.collectAsState()
    val nickname by chatViewModel.nickname.collectAsState()
    val seenAt by PukaarStore.chatSeenAt.collectAsState()
    val myPeer = remember { MeshBridge.myPeerId(context) }
    var confirmSafe by remember { mutableStateOf(false) }

    HomeScreen(
        state = HomeState(connection, battery, unreadCount(messages, seenAt, myPeer, nickname), sos, offlinePending),
        onSos = { nav.navigate(Routes.SOS_COUNTDOWN) },
        onSosStatus = { nav.navigate(Routes.SOS_STATUS) },
        onSafe = { confirmSafe = true },
        onNetwork = { nav.navigate(Routes.NETWORK) },
        onSettings = { nav.navigate(Routes.SETTINGS) },
        onChat = { nav.navigate(Routes.CHAT) { popUpTo(nav.graph.findStartDestination().id) { saveState = true }; launchSingleTop = true; restoreState = true } },
        onMap = { nav.navigate(Routes.MAP) { popUpTo(nav.graph.findStartDestination().id) { saveState = true }; launchSingleTop = true; restoreState = true } },
        onCall = { nav.navigate(Routes.CALLING) },
        onGuides = { nav.navigate(Routes.GUIDES) { popUpTo(nav.graph.findStartDestination().id) { saveState = true }; launchSingleTop = true; restoreState = true } },
        onOfflineData = { nav.navigate(Routes.SETTINGS) },
    )
    if (confirmSafe) SafeConfirmDialog(onDismiss = { confirmSafe = false }) { SosManager.markSafe() }
}

@Composable
private fun MapRoute(nav: NavHostController) {
    val context = LocalContext.current
    val set = remember { Places.load(context) }
    val connection by DeviceStatus.connection.collectAsState()
    val mapState by app.pukaar.map.OfflineMaps.state.collectAsState()
    var me by remember { mutableStateOf<Location?>(null) }
    LaunchedEffect(Unit) { Locations.updates(context, 5_000).collect { me = it } }
    MapScreen(
        MapState(
            places = set.places,
            sampleData = set.sample,
            region = set.region,
            me = me?.let { LatLon(it.latitude, it.longitude) },
            meAccuracyM = me?.takeIf { it.hasAccuracy() }?.accuracy,
            connection = connection,
            mapSaved = mapState is app.pukaar.map.OfflineMaps.State.Saved,
        ),
        onSos = { nav.navigate(Routes.SOS_COUNTDOWN) },
        onNetwork = { nav.navigate(Routes.NETWORK) },
        onDirection = { nav.navigate("direction/${it.id}") },
        onCall = { placeCall(context, it) },
    )
}

@Composable
private fun NetworkRoute(nav: NavHostController) {
    val context = LocalContext.current
    val connection by DeviceStatus.connection.collectAsState()
    val internet by DeviceStatus.internet.collectAsState()
    val bluetooth by DeviceStatus.bluetoothOn.collectAsState()
    val radio by DeviceStatus.radioPaired.collectAsState()
    val outbox by ChatOutbox.waiting.collectAsState()
    val sos by SosManager.active.collectAsState()
    val waiting = outbox.size + if (sos?.let { !it.closed && it.stage == app.pukaar.sos.SosStage.Sending } == true) 1 else 0
    val enableBt = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { }
    NetworkScreen(
        NetworkState(connection, internet, bluetooth, radio, Gateway.configured, waiting),
        onBack = { nav.popBackStack() },
        onEnableBluetooth = {
            runCatching { enableBt.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)) }
                .onFailure { runCatching { context.startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS)) } }
        },
    )
}

@Composable
private fun CallingRoute(nav: NavHostController) {
    val context = LocalContext.current
    val signal by DeviceStatus.cellSignal.collectAsState()
    val contacts by PukaarStore.contacts.collectAsState()
    val settings by PukaarStore.settings.collectAsState()
    var editDistrict by remember { mutableStateOf(false) }
    CallingScreen(
        hasSignal = signal,
        numbers = emergencyNumbers(settings.districtControlNumber),
        contacts = contacts,
        onBack = { nav.popBackStack() },
        onCall = { placeCall(context, it) },
        onSos = { nav.navigate(Routes.SOS_COUNTDOWN) },
        onSetDistrict = { editDistrict = true },
    )
    if (editDistrict) DistrictDialog(settings.districtControlNumber, { editDistrict = false }) { n -> PukaarStore.updateSettings { it.copy(districtControlNumber = n) } }
}

@Composable
fun SafeConfirmDialog(onDismiss: () -> Unit, onConfirm: () -> Unit) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.pk_sos_safe_confirm_title)) },
        text = { Text(stringResource(R.string.pk_sos_safe_confirm_body)) },
        confirmButton = { androidx.compose.material3.TextButton(onClick = { onConfirm(); onDismiss() }) { Text(stringResource(R.string.pk_sos_safe_confirm_yes)) } },
        dismissButton = { androidx.compose.material3.TextButton(onClick = onDismiss) { Text(stringResource(R.string.pk_not_yet)) } },
    )
}

@Composable
private fun PracticeDoneDialog(onDismiss: () -> Unit) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        icon = { PukaarIcon(Sym.checkCircle, null) },
        title = { Text(stringResource(R.string.pk_practice_done_title)) },
        text = { Text(stringResource(R.string.pk_practice_done_body)) },
        confirmButton = { androidx.compose.material3.TextButton(onClick = onDismiss) { Text(stringResource(R.string.pk_ok)) } },
    )
}
