package app.pukaar.ui.screens.map

import android.content.Context
import android.view.ViewGroup
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import org.maplibre.android.MapLibre
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView

/**
 * One MapLibre MapView for the whole session, created the first time the map is shown.
 *
 * Creating and destroying a MapView each time the Map tab opens and closes crashed the app
 * (native SIGSEGV in MapRenderer.render) when switching tabs quickly: the render thread could
 * still be drawing a frame for a view that had just been destroyed. Keeping one view and only
 * attaching/detaching it avoids that race, and the map stays loaded between visits.
 */
class PukaarMapHolder(private val context: Context, private val lifecycle: Lifecycle) {
    private var view: MapView? = null
    private var map: MapLibreMap? = null
    private val waiting = mutableListOf<(MapLibreMap) -> Unit>()

    fun view(): MapView = view ?: createView().also { view = it }

    private fun createView(): MapView {
        MapLibre.getInstance(context.applicationContext)
        return MapView(context).apply {
            onCreate(null)
            // Catch up with the screen's lifecycle; the holder's observer handles the rest.
            if (lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) onStart()
            if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) onResume()
            getMapAsync { m ->
                m.uiSettings.apply {
                    isCompassEnabled = false
                    isRotateGesturesEnabled = false
                    isTiltGesturesEnabled = false
                    isLogoEnabled = false
                    // Pukaar shows the OpenStreetMap attribution in its own note line (MapScreen),
                    // because MapLibre's button would sit under the overlays.
                    isAttributionEnabled = false
                }
                map = m
                waiting.toList().forEach { it(m) }
                waiting.clear()
            }
        }
    }

    fun whenReady(block: (MapLibreMap) -> Unit) {
        map?.let(block) ?: waiting.add(block)
    }

    internal fun forward(event: Lifecycle.Event) {
        val v = view ?: return
        when (event) {
            Lifecycle.Event.ON_START -> v.onStart()
            Lifecycle.Event.ON_RESUME -> v.onResume()
            Lifecycle.Event.ON_PAUSE -> v.onPause()
            Lifecycle.Event.ON_STOP -> v.onStop()
            Lifecycle.Event.ON_DESTROY -> destroy()
            else -> Unit
        }
    }

    internal fun destroy() {
        val v = view ?: return
        view = null
        map = null
        waiting.clear()
        (v.parent as? ViewGroup)?.removeView(v)
        v.onDestroy()
    }
}

val LocalPukaarMapHolder = compositionLocalOf<PukaarMapHolder?> { null }

/** Provides one [PukaarMapHolder] for everything below it (PukaarNavHost wraps the app with it). */
@Composable
fun rememberPukaarMapHolder(): PukaarMapHolder {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val holder = remember(lifecycle) { PukaarMapHolder(context, lifecycle) }
    DisposableEffect(holder) {
        val observer = LifecycleEventObserver { _, event -> holder.forward(event) }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            holder.destroy()
        }
    }
    return holder
}

/**
 * The session's MapView in Compose. Pukaar draws its own pins and location on top; this only
 * renders the base map (from the offline area when saved).
 */
@Composable
fun PukaarMapView(
    modifier: Modifier = Modifier,
    onReady: (MapLibreMap) -> Unit,
    onCameraMove: () -> Unit,
) {
    val holder = LocalPukaarMapHolder.current ?: rememberPukaarMapHolder()
    DisposableEffect(holder) {
        var attached: MapLibreMap? = null
        var active = true
        val move = MapLibreMap.OnCameraMoveListener { onCameraMove() }
        val idle = MapLibreMap.OnCameraIdleListener { onCameraMove() }
        holder.whenReady { m ->
            if (!active) return@whenReady // left the screen before the map was ready
            attached = m
            m.addOnCameraMoveListener(move)
            m.addOnCameraIdleListener(idle)
            onReady(m)
        }
        onDispose {
            active = false
            attached?.removeOnCameraMoveListener(move)
            attached?.removeOnCameraIdleListener(idle)
        }
    }
    AndroidView(
        factory = { holder.view().also { (it.parent as? ViewGroup)?.removeView(it) } },
        modifier = modifier,
        // Leaving the screen only detaches the view; the holder owns its lifetime.
        onRelease = { (it.parent as? ViewGroup)?.removeView(it) },
    )
}
