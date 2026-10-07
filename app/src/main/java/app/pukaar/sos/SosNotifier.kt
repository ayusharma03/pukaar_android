package app.pukaar.sos

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.text.format.DateFormat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import app.pukaar.PukaarIntents
import app.pukaar.system.SosActivity
import com.bitchat.android.MainActivity
import com.bitchat.android.R
import java.util.Date

/** SOS, nearby-SOS, shake and official-message notifications (screens.md §13, handoff 2x). */
object SosNotifier {
    private const val CHANNEL_SOS = "pukaar_sos"
    private const val CHANNEL_ALERTS = "pukaar_alerts"
    private const val CHANNEL_OFFICIAL = "pukaar_official"
    private const val ID_ACTIVE = 7101
    private const val ID_SHAKE = 7102
    private const val ID_NEARBY_BASE = 7200
    private const val ID_OFFICIAL_BASE = 7400
    private const val ID_CONTROL_BASE = 7700

    fun ensureChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_SOS, context.getString(R.string.pk_notif_channel_sos), NotificationManager.IMPORTANCE_HIGH)
                .apply { description = context.getString(R.string.pk_notif_channel_sos_desc) },
        )
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ALERTS, context.getString(R.string.pk_notif_channel_alerts), NotificationManager.IMPORTANCE_HIGH)
                .apply { description = context.getString(R.string.pk_notif_channel_alerts_desc) },
        )
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_OFFICIAL, context.getString(R.string.pk_notif_channel_official), NotificationManager.IMPORTANCE_HIGH)
                .apply { description = context.getString(R.string.pk_notif_channel_official_desc) },
        )
    }

    private fun canNotify(context: Context) =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    private fun openApp(context: Context, route: String?, requestCode: Int): PendingIntent {
        val intent = Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            .apply { route?.let { putExtra(PukaarIntents.EXTRA_ROUTE, it) } }
        return PendingIntent.getActivity(context, requestCode, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    /** Ongoing notification while an SOS is active, with an "I'm safe" action. */
    fun showActive(context: Context, sos: ActiveSos) {
        if (!canNotify(context) || sos.closed) return
        ensureChannels(context)
        val time = DateFormat.getTimeFormat(context).format(Date(sos.events.lastOrNull()?.at ?: sos.startedAt))
        val (title, text) = when (sos.stage) {
            SosStage.Sending -> context.getString(R.string.pk_sos_hero_sending) to context.getString(R.string.pk_sos_hero_sending_body)
            SosStage.Relayed -> context.getString(R.string.pk_sos_hero_relayed) to context.getString(R.string.pk_sos_hero_relayed_body)
            SosStage.SentByRadio -> context.getString(R.string.pk_delivery_radio) to context.getString(R.string.pk_sos_hero_relayed_body)
            SosStage.HelpNotified -> context.getString(R.string.pk_sos_hero_notified) to context.getString(R.string.pk_notif_reached_at, time)
            SosStage.RescuerAttending -> context.getString(R.string.pk_sos_hero_attending) to context.getString(R.string.pk_sos_hero_attending_body)
            SosStage.Resolved -> context.getString(R.string.pk_sos_hero_resolved) to ""
        }
        val safe = PendingIntent.getBroadcast(
            context, 1, Intent(context, SosActionReceiver::class.java).setAction(SosActionReceiver.ACTION_SAFE),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_SOS)
            .setSmallIcon(R.drawable.ic_stat_pukaar)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(openApp(context, PukaarIntents.ROUTE_SOS_STATUS, 2))
            .addAction(0, context.getString(R.string.pk_sos_im_safe_short), safe)
            .build()
        runCatching { NotificationManagerCompat.from(context).notify(ID_ACTIVE, notification) }
    }

    fun cancelActive(context: Context) {
        NotificationManagerCompat.from(context).cancel(ID_ACTIVE)
    }

    /**
     * Shake detected while Pukaar may be in the background. Android blocks starting activities
     * from the background, so the countdown opens through a full-screen intent (locked phone) or
     * a heads-up notification the user taps.
     */
    fun showShakeTrigger(context: Context) {
        if (!canNotify(context)) return
        ensureChannels(context)
        val intent = Intent(context, SosActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val pending = PendingIntent.getActivity(context, 3, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notification = NotificationCompat.Builder(context, CHANNEL_ALERTS)
            .setSmallIcon(R.drawable.ic_stat_pukaar)
            .setContentTitle(context.getString(R.string.pk_notif_shake_title))
            .setContentText(context.getString(R.string.pk_notif_shake_text))
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setAutoCancel(true)
            .setContentIntent(pending)
            .setFullScreenIntent(pending, true)
            .setTimeoutAfter(30_000)
            .build()
        runCatching { NotificationManagerCompat.from(context).notify(ID_SHAKE, notification) }
    }

    fun cancelShake(context: Context) = NotificationManagerCompat.from(context).cancel(ID_SHAKE)

    /** Someone nearby sent an SOS: neighbours can often help first. */
    fun showNearbySos(context: Context, packet: SosPacket, summary: String) {
        if (!canNotify(context)) return
        ensureChannels(context)
        val notification = NotificationCompat.Builder(context, CHANNEL_ALERTS)
            .setSmallIcon(R.drawable.ic_stat_pukaar)
            .setContentTitle(context.getString(R.string.pk_notif_nearby_sos_title, packet.name))
            .setContentText(summary)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(openApp(context, PukaarIntents.ROUTE_CHAT, 4))
            .build()
        runCatching { NotificationManagerCompat.from(context).notify(ID_NEARBY_BASE + (packet.id.hashCode() and 0xff), notification) }
    }

    /** A control-room message for this phone's SOS (dashboard D3). Each message gets its own notification. */
    fun showControlMessage(context: Context, message: ControlMessage) {
        if (!canNotify(context)) return
        ensureChannels(context)
        val notification = NotificationCompat.Builder(context, CHANNEL_OFFICIAL)
            .setSmallIcon(R.drawable.ic_stat_pukaar)
            .setSubText(message.from)
            .setContentTitle(context.getString(R.string.pk_notif_control_message_title))
            .setContentText(message.text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message.text))
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setAutoCancel(true)
            .setContentIntent(openApp(context, PukaarIntents.ROUTE_SOS_STATUS, 6))
            .build()
        runCatching { NotificationManagerCompat.from(context).notify(ID_CONTROL_BASE + (message.id.hashCode() and 0xff), notification) }
    }

    fun showOfficial(context: Context, packet: OfficialPacket) {
        if (!canNotify(context)) return
        ensureChannels(context)
        val notification = NotificationCompat.Builder(context, CHANNEL_OFFICIAL)
            .setSmallIcon(R.drawable.ic_stat_pukaar)
            .setSubText(context.getString(R.string.pk_chat_title))
            .setContentTitle(packet.from)
            .setContentText(packet.text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(packet.text))
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setAutoCancel(true)
            .setContentIntent(openApp(context, PukaarIntents.ROUTE_CHAT, 5))
            .build()
        runCatching { NotificationManagerCompat.from(context).notify(ID_OFFICIAL_BASE + (packet.id.hashCode() and 0xff), notification) }
    }
}

/** Handles the notification's "I'm safe" action. */
class SosActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == ACTION_SAFE) SosManager.markSafe()
    }

    companion object {
        const val ACTION_SAFE = "app.pukaar.action.SAFE"
    }
}
