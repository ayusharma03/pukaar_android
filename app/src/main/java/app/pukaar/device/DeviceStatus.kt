package app.pukaar.device

import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.BatteryManager
import android.os.Build
import android.telephony.TelephonyManager
import app.pukaar.model.ConnectionStatus
import app.pukaar.model.ConnectionType
import com.bitchat.android.services.AppStateStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class BatteryInfo(val level: Int, val charging: Boolean) {
    val low: Boolean get() = level in 0 until 20 && !charging
}

/**
 * Live phone state that Pukaar screens and the SOS engine read: internet, mobile signal,
 * Bluetooth, battery, and the combined [connection] used by the pill, Home ring and composer.
 */
object DeviceStatus {
    private val _internet = MutableStateFlow(false)
    val internet: StateFlow<Boolean> = _internet.asStateFlow()

    /** True when a mobile tower is reachable, so SMS and calls can work (FR-10, FR-18). */
    private val _cellSignal = MutableStateFlow(false)
    val cellSignal: StateFlow<Boolean> = _cellSignal.asStateFlow()

    private val _bluetoothOn = MutableStateFlow(false)
    val bluetoothOn: StateFlow<Boolean> = _bluetoothOn.asStateFlow()

    private val _battery = MutableStateFlow(BatteryInfo(100, false))
    val battery: StateFlow<BatteryInfo> = _battery.asStateFlow()

    /** Paired LoRa radio. Meshtastic pairing is not built yet, so this stays false. */
    private val _radioPaired = MutableStateFlow(false)
    val radioPaired: StateFlow<Boolean> = _radioPaired.asStateFlow()

    /** People whose messages this phone uploaded recently, set by the gateway. */
    internal val gatewayHelping = MutableStateFlow(0)

    lateinit var connection: StateFlow<ConnectionStatus>
        private set

    private var started = false

    fun start(context: Context, scope: CoroutineScope) {
        if (started) return
        started = true
        val app = context.applicationContext

        connection = combine(
            internet,
            AppStateStore.directPeers,
            AppStateStore.peers,
            radioPaired,
            gatewayHelping,
        ) { online, direct, all, radio, helping ->
            val peers = direct.size
            val reachable = maxOf(all.size, peers)
            when {
                online && helping > 0 -> ConnectionStatus(ConnectionType.Gateway, peers, reachable, helping)
                online -> ConnectionStatus(ConnectionType.Online, peers, reachable)
                peers > 0 || reachable > 0 -> ConnectionStatus(ConnectionType.Mesh, peers.coerceAtLeast(1), reachable)
                radio -> ConnectionStatus(ConnectionType.Radio)
                else -> ConnectionStatus(ConnectionType.Isolated)
            }
        }.stateIn(scope, SharingStarted.Eagerly, ConnectionStatus.Isolated)

        watchInternet(app)
        watchBattery(app)
        scope.launch {
            // Signal strength and Bluetooth have no cheap permission-free callbacks on every API level; poll gently.
            while (true) {
                _cellSignal.value = readCellSignal(app)
                _bluetoothOn.value = readBluetooth(app)
                delay(10_000)
            }
        }
    }

    private fun watchInternet(context: Context) {
        val cm = context.getSystemService(ConnectivityManager::class.java) ?: return
        val validated = mutableSetOf<Network>()
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        runCatching {
            cm.registerNetworkCallback(request, object : ConnectivityManager.NetworkCallback() {
                override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
                    synchronized(validated) {
                        if (caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)) validated += network else validated -= network
                        _internet.value = validated.isNotEmpty()
                    }
                }

                override fun onLost(network: Network) {
                    synchronized(validated) {
                        validated -= network
                        _internet.value = validated.isNotEmpty()
                    }
                }
            })
        }
    }

    private fun watchBattery(context: Context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context?, intent: Intent?) {
                intent ?: return
                val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100).coerceAtLeast(1)
                val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
                val charging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
                if (level >= 0) _battery.value = BatteryInfo(level * 100 / scale, charging)
            }
        }
        context.registerReceiver(receiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED))?.let { receiver.onReceive(context, it) }
    }

    private fun readCellSignal(context: Context): Boolean {
        val tm = context.getSystemService(TelephonyManager::class.java) ?: return false
        if (tm.simState != TelephonyManager.SIM_STATE_READY) return false
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            (tm.signalStrength?.level ?: 0) > 0
        } else {
            tm.networkOperator.isNotEmpty()
        }
    }

    private fun readBluetooth(context: Context): Boolean = runCatching {
        context.getSystemService(BluetoothManager::class.java)?.adapter?.isEnabled == true
    }.getOrDefault(false)

    /** Current battery level, read synchronously (for SOS packets). */
    fun batteryNow(): Int = _battery.value.level
}
