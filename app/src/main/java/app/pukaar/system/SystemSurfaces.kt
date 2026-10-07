package app.pukaar.system

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.widget.RemoteViews
import app.pukaar.device.DeviceStatus
import app.pukaar.model.ConnectionType
import app.pukaar.sos.SosManager
import com.bitchat.android.R

/** Quick Settings "SOS" tile (FR-13, handoff 2y). Opens the countdown, even over the lock screen. */
class SosTileService : TileService() {
    override fun onStartListening() {
        qsTile?.apply {
            label = getString(R.string.pk_sos)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) subtitle = getString(R.string.pk_app_name_short)
            icon = Icon.createWithResource(this@SosTileService, R.drawable.ic_stat_pukaar)
            state = Tile.STATE_ACTIVE
            updateTile()
        }
    }

    @SuppressLint("StartActivityAndCollapseDeprecated")
    override fun onClick() {
        val intent = SosActivity.intent(this)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startActivityAndCollapse(PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT))
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(intent)
        }
    }
}

/** Small 2 × 1 widget: one big SOS button. */
class SosWidgetSmall : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) = SosWidgets.updateAll(context)
}

/** Medium 2 × 2 widget: SOS button plus connection status. */
class SosWidgetMedium : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) = SosWidgets.updateAll(context)
}

object SosWidgets {
    fun updateAll(context: Context) {
        val manager = AppWidgetManager.getInstance(context) ?: return
        val tap = PendingIntent.getActivity(
            context, 10, SosActivity.intent(context), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        manager.getAppWidgetIds(ComponentName(context, SosWidgetSmall::class.java)).forEach { id ->
            val views = RemoteViews(context.packageName, R.layout.pk_widget_small)
            views.setOnClickPendingIntent(R.id.pk_widget_sos, tap)
            manager.updateAppWidget(id, views)
        }

        val mediumIds = manager.getAppWidgetIds(ComponentName(context, SosWidgetMedium::class.java))
        if (mediumIds.isEmpty()) return
        val status = runCatching { statusLine(context) }.getOrDefault("")
        val seconds = runCatching { app.pukaar.data.PukaarStore.settings.value.countdownSeconds }.getOrDefault(5)
        mediumIds.forEach { id ->
            val views = RemoteViews(context.packageName, R.layout.pk_widget_medium)
            views.setOnClickPendingIntent(R.id.pk_widget_sos, tap)
            views.setOnClickPendingIntent(R.id.pk_widget_root, tap)
            views.setTextViewText(R.id.pk_widget_status, status)
            views.setTextViewText(R.id.pk_widget_hint, context.resources.getQuantityString(R.plurals.pk_widget_hint, seconds, seconds))
            manager.updateAppWidget(id, views)
        }
    }

    private fun statusLine(context: Context): String {
        if (SosManager.active.value?.closed == false) return context.getString(R.string.pk_widget_sos_active)
        val c = DeviceStatus.connection.value
        return when (c.type) {
            ConnectionType.Online, ConnectionType.Gateway -> context.getString(R.string.pk_conn_online)
            ConnectionType.Mesh -> context.resources.getQuantityString(R.plurals.pk_phones_short, c.peers, c.peers)
            ConnectionType.Radio -> context.getString(R.string.pk_conn_radio)
            ConnectionType.Isolated -> context.getString(R.string.pk_conn_none_short)
        }
    }

    /** Asks the launcher to pin a widget (onboarding 1.8 "Add"). Returns false if unsupported. */
    fun requestPin(context: Context, medium: Boolean): Boolean {
        val manager = AppWidgetManager.getInstance(context) ?: return false
        if (!manager.isRequestPinAppWidgetSupported) return false
        val provider = ComponentName(context, if (medium) SosWidgetMedium::class.java else SosWidgetSmall::class.java)
        return manager.requestPinAppWidget(provider, null, null)
    }
}

/** Asks the system to add the SOS tile (Android 13+). Returns false where that isn't possible. */
fun requestAddSosTile(context: Context, onResult: (Boolean) -> Unit) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
        onResult(false)
        return
    }
    val sbm = context.getSystemService(android.app.StatusBarManager::class.java) ?: return onResult(false)
    sbm.requestAddTileService(
        ComponentName(context, SosTileService::class.java),
        context.getString(R.string.pk_sos),
        Icon.createWithResource(context, R.drawable.ic_stat_pukaar),
        context.mainExecutor,
    ) { result ->
        onResult(
            result == android.app.StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ADDED ||
                result == android.app.StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ALREADY_ADDED,
        )
    }
}
