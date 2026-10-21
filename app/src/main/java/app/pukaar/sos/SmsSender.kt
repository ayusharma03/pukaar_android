package app.pukaar.sos

import android.Manifest
import android.app.PendingIntent
import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.telephony.SmsManager
import androidx.core.content.ContextCompat
import java.util.concurrent.atomic.AtomicInteger

/** Sends SOS, safe and test SMS directly from this phone when there is a tower signal (FR-10). */
object SmsSender {
    private val requestCodes = AtomicInteger(4100)

    fun canSend(context: Context): Boolean =
        context.packageManager.hasSystemFeature(PackageManager.FEATURE_TELEPHONY_MESSAGING) &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED

    /**
     * Sends [text] to [phone]. [onSent] reports whether the network accepted it; [onDelivered]
     * fires later if the carrier returns a delivery report (not all do).
     */
    fun send(
        context: Context,
        phone: String,
        text: String,
        onSent: (Boolean) -> Unit = {},
        onDelivered: () -> Unit = {},
    ) {
        if (!canSend(context)) {
            onSent(false)
            return
        }
        val app = context.applicationContext
        val code = requestCodes.incrementAndGet()
        val sentAction = "app.pukaar.SMS_SENT.$code"
        val deliveredAction = "app.pukaar.SMS_DELIVERED.$code"

        register(app, sentAction) { result -> onSent(result == Activity.RESULT_OK) }
        register(app, deliveredAction) { result -> if (result == Activity.RESULT_OK) onDelivered() }

        val flags = PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE
        val sentIntent = PendingIntent.getBroadcast(app, code, Intent(sentAction).setPackage(app.packageName), flags)
        val deliveredIntent = PendingIntent.getBroadcast(app, code, Intent(deliveredAction).setPackage(app.packageName), flags)

        try {
            val sms = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                app.getSystemService(SmsManager::class.java)
            } else {
                @Suppress("DEPRECATION")
                SmsManager.getDefault()
            }
            val parts = sms.divideMessage(text)
            if (parts.size == 1) {
                sms.sendTextMessage(phone, null, text, sentIntent, deliveredIntent)
            } else {
                // Report on the last part; earlier parts get no callbacks.
                val sent = ArrayList<PendingIntent?>(List(parts.size) { null }).apply { this[lastIndex] = sentIntent }
                val delivered = ArrayList<PendingIntent?>(List(parts.size) { null }).apply { this[lastIndex] = deliveredIntent }
                sms.sendMultipartTextMessage(phone, null, parts, sent, delivered)
            }
        } catch (e: Exception) {
            onSent(false)
        }
    }

    private fun register(context: Context, action: String, onResult: (Int) -> Unit) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context, intent: Intent) {
                runCatching { context.unregisterReceiver(this) }
                onResult(resultCode)
            }
        }
        ContextCompat.registerReceiver(context, receiver, IntentFilter(action), ContextCompat.RECEIVER_NOT_EXPORTED)
    }
}
