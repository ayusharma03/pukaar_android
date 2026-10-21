package app.pukaar.debug

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import app.pukaar.sos.Packets
import app.pukaar.sos.SafePacket
import app.pukaar.sos.SosFlag
import app.pukaar.sos.SosPacket
import com.bitchat.android.model.BitchatMessage
import com.bitchat.android.services.AppStateStore
import java.util.Date
import java.util.UUID

/**
 * Debug builds only: pretend another phone sent something, for demos and testing on one device.
 *
 *   adb shell am broadcast -a app.pukaar.DEMO -p app.pukaar --es kind sos [--es name Ramesh] [--ef lat 26.155] [--ef lon 85.902] [--ei people 3] [--es id ab12cd34]
 *   adb shell am broadcast -a app.pukaar.DEMO -p app.pukaar --es kind message --es text "Water near the temple" [--ef lat 26.15 --ef lon 85.90] [--es name Sunita]
 *   adb shell am broadcast -a app.pukaar.DEMO -p app.pukaar --es kind safe --es id ab12cd34
 */
class PukaarDemoReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val name = intent.getStringExtra("name") ?: "Ramesh K."
        val lat = intent.getFloatExtra("lat", Float.NaN).takeUnless { it.isNaN() }?.toDouble()
        val lon = intent.getFloatExtra("lon", Float.NaN).takeUnless { it.isNaN() }?.toDouble()
        val content = when (intent.getStringExtra("kind")) {
            "sos" -> SosPacket(
                id = intent.getStringExtra("id") ?: UUID.randomUUID().toString().take(8),
                seq = 1,
                lat = lat ?: 26.1555,
                lon = lon ?: 85.9015,
                accuracyM = 15,
                timeSec = System.currentTimeMillis() / 1000,
                battery = 18,
                people = intent.getIntExtra("people", 2),
                flags = setOf(SosFlag.Injured, SosFlag.Trapped),
                name = name,
                message = intent.getStringExtra("text") ?: "",
            ).encode()
            "safe" -> SafePacket(intent.getStringExtra("id") ?: return, System.currentTimeMillis() / 1000).encode()
            "message" -> {
                val text = intent.getStringExtra("text") ?: "Hello from a nearby phone"
                if (lat != null && lon != null) Packets.withLocation(text, lat, lon) else text
            }
            else -> return
        }
        AppStateStore.addPublicMessage(
            BitchatMessage(sender = name, content = content, timestamp = Date(), senderPeerID = "demo" + name.hashCode().toUInt().toString(16)),
        )
        Log.i("PukaarDemo", "Injected: $content")
    }
}
