package app.pukaar.system

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import androidx.core.app.NotificationCompat
import app.pukaar.sos.SosManager
import app.pukaar.sos.SosStage
import app.pukaar.ui.screens.chat.ChatOutbox
import com.bitchat.android.R

/**
 * The persistent "Pukaar · running" notification that keeps the mesh alive (NFR-4, handoff 2x):
 * "Connected to 4 phones · 2 messages waiting", with an SOS action. Built for bitchat's
 * MeshForegroundService, which passes its own open and quit intents.
 */
object MeshNotification {
    fun build(
        context: Context,
        channelId: String,
        activePeers: Int,
        openIntent: PendingIntent,
        quitIntent: PendingIntent,
    ): Notification {
        val res = context.resources
        val peers = if (activePeers > 0) res.getQuantityString(R.plurals.pk_conn_mesh, activePeers, activePeers)
        else context.getString(R.string.pk_mesh_notif_no_peers)
        val waiting = waitingCount()
        val content = if (waiting > 0) context.getString(R.string.pk_mesh_notif_with_waiting, peers, res.getQuantityString(R.plurals.pk_mesh_notif_waiting, waiting, waiting))
        else peers

        val sos = PendingIntent.getActivity(
            context, 20, SosActivity.intent(context), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(context, channelId)
            .setContentTitle(context.getString(R.string.pk_mesh_notif_title))
            .setContentText(content)
            .setSmallIcon(R.drawable.ic_stat_pukaar)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(openIntent)
            .addAction(0, context.getString(R.string.pk_sos), sos)
            .addAction(0, context.getString(R.string.pk_mesh_notif_quit), quitIntent)
            .build()
    }

    private fun waitingCount(): Int = runCatching {
        val sosWaiting = SosManager.active.value?.let { !it.closed && it.stage == SosStage.Sending } == true
        ChatOutbox.waiting.value.size + if (sosWaiting) 1 else 0
    }.getOrDefault(0)
}
