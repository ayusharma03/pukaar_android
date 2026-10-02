package app.pukaar.ui.screens.map

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
 * MapLibre's MapView in Compose, following the screen's lifecycle. Pukaar draws its own pins and
 * location in Compose on top; this only renders the base map (from the offline area when saved).
 */
@Composable
fun PukaarMapView(
    modifier: Modifier = Modifier,
    onReady: (MapLibreMap) -> Unit,
    onCameraMove: () -> Unit,
) {
    val context = LocalContext.current
    val mapView = remember {
        MapLibre.getInstance(context.applicationContext)
        MapView(context).apply {
            onCreate(null)
            getMapAsync { map ->
                map.uiSettings.apply {
                    isCompassEnabled = false
                    isRotateGesturesEnabled = false
                    isTiltGesturesEnabled = false
                    isLogoEnabled = false
                    // Pukaar shows the OpenStreetMap attribution in its own note line (MapScreen),
                    // because MapLibre's button would sit under the overlays.
                    isAttributionEnabled = false
                }
                map.addOnCameraMoveListener { onCameraMove() }
                map.addOnCameraIdleListener { onCameraMove() }
                onReady(map)
            }
        }
    }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle, mapView) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> mapView.onStart()
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                Lifecycle.Event.ON_STOP -> mapView.onStop()
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) mapView.onPause()
            if (lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) mapView.onStop()
            mapView.onDestroy()
        }
    }
    AndroidView(factory = { mapView }, modifier = modifier)
}
