package app.pukaar.system

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.hardware.SensorManager.GRAVITY_EARTH
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ProcessLifecycleOwner
import app.pukaar.data.PukaarStore
import app.pukaar.data.ShakeSensitivity
import app.pukaar.sos.SosManager
import app.pukaar.sos.SosNotifier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.sqrt

/**
 * "Shake hard 3 times" starts the SOS countdown (FR-13). Runs in the app process, which bitchat's
 * mesh foreground service keeps alive, so it works while Pukaar's screens are closed.
 */
object ShakeTrigger : SensorEventListener {
    private lateinit var appContext: Context
    private var sensorManager: SensorManager? = null
    private var threshold = 2.4f
    private val peaks = ArrayDeque<Long>()
    private var lastPeak = 0L
    private var lastTrigger = 0L

    /** Set while the countdown is on screen, so shaking again doesn't stack countdowns. */
    @Volatile var countdownShowing = false

    fun start(context: Context, scope: CoroutineScope) {
        appContext = context.applicationContext
        sensorManager = appContext.getSystemService(SensorManager::class.java)
        scope.launch {
            PukaarStore.settings.map { it.shakeEnabled to it.shakeSensitivity }.distinctUntilChanged().collect { (enabled, sensitivity) ->
                threshold = when (sensitivity) {
                    ShakeSensitivity.Low -> 2.9f
                    ShakeSensitivity.Medium -> 2.4f
                    ShakeSensitivity.High -> 1.9f
                }
                withContext(Dispatchers.Main) { if (enabled) register() else unregister() }
            }
        }
    }

    private fun register() {
        val sm = sensorManager ?: return
        val accel = sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) ?: return
        sm.unregisterListener(this)
        sm.registerListener(this, accel, SensorManager.SENSOR_DELAY_UI)
    }

    private fun unregister() {
        sensorManager?.unregisterListener(this)
    }

    override fun onSensorChanged(event: SensorEvent) {
        val (x, y, z) = Triple(event.values[0], event.values[1], event.values[2])
        val g = sqrt(x * x + y * y + z * z) / GRAVITY_EARTH
        if (g < threshold) return
        val now = System.currentTimeMillis()
        if (now - lastPeak < 250) return // one shake produces several samples above threshold
        lastPeak = now
        peaks.addLast(now)
        while (peaks.isNotEmpty() && now - peaks.first() > 2_000) peaks.removeFirst()
        if (peaks.size >= 3 && now - lastTrigger > 10_000) {
            peaks.clear()
            lastTrigger = now
            trigger()
        }
    }

    private fun trigger() {
        if (countdownShowing) return
        if (SosManager.active.value?.closed == false) return // an SOS is already running
        val foreground = ProcessLifecycleOwner.get().lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
        if (foreground) {
            appContext.startActivity(SosActivity.intent(appContext))
        } else {
            SosNotifier.showShakeTrigger(appContext)
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
}
