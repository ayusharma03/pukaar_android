package app.pukaar.map

import android.content.Context
import android.location.Geocoder
import android.location.Location
import app.pukaar.data.Places
import app.pukaar.device.Locations
import com.bitchat.android.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import java.util.Locale

/**
 * The area onboarding 1.7 and Settings offer to save, with a name and a size estimate.
 * [fromLocation] is false when no fix was available and the bundled region was used instead.
 */
data class OfflineArea(val name: String, val bounds: LatLngBounds, val estimateMb: Int, val fromLocation: Boolean)

private const val RADIUS_KM = 15.0

/** How long to wait for a fresh GPS fix before falling back to the last known one. */
private const val FIX_TIMEOUT_MS = 15_000L

/**
 * Around the user when we know where they are ("Detected from your location"); otherwise around
 * the saved places (shelters, hospitals, police) of the bundled region.
 *
 * Asks for a fresh fix first: on a new phone, or one that hasn't used GPS lately, the last known
 * location is often empty, and the area silently became the sample region.
 */
suspend fun pickOfflineArea(context: Context): OfflineArea =
    offlineAreaFor(context, Locations.current(context, FIX_TIMEOUT_MS))

suspend fun offlineAreaFor(context: Context, here: Location?): OfflineArea {
    val places = Places.load(context)
    val bounds: LatLngBounds
    val name: String
    if (here != null) {
        bounds = OfflineMaps.boundsAround(here.latitude, here.longitude, RADIUS_KM)
        val nearPlaces = places.places.any { Locations.distanceM(here.latitude, here.longitude, it.lat, it.lon) < 50_000f }
        name = if (nearPlaces && places.region.isNotBlank()) places.region
            else placeName(context, here) ?: context.getString(R.string.pk_offline_area_here)
    } else if (places.places.isNotEmpty()) {
        val b = LatLngBounds.Builder()
        places.places.forEach { b.include(LatLng(it.lat, it.lon)) }
        val box = b.build()
        // Pad so the places aren't at the edge.
        bounds = OfflineMaps.boundsAround(box.center.latitude, box.center.longitude, RADIUS_KM)
        name = places.region.ifBlank { context.getString(R.string.pk_offline_unknown_region) }
    } else {
        bounds = OfflineMaps.boundsAround(0.0, 0.0, RADIUS_KM)
        name = context.getString(R.string.pk_offline_unknown_region)
    }
    return OfflineArea(name, bounds, OfflineMaps.estimateMb(bounds), fromLocation = here != null)
}

/** Town or district name for the fix. Needs internet, which downloading the map needs anyway. */
@Suppress("DEPRECATION") // The listener version is API 33+ only.
private suspend fun placeName(context: Context, here: Location): String? = withContext(Dispatchers.IO) {
    if (!Geocoder.isPresent()) return@withContext null
    runCatching {
        Geocoder(context, Locale.getDefault()).getFromLocation(here.latitude, here.longitude, 1)
            ?.firstOrNull()
            ?.let { it.locality ?: it.subAdminArea ?: it.adminArea }
    }.getOrNull()
}
