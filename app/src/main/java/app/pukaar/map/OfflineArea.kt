package app.pukaar.map

import android.content.Context
import app.pukaar.data.Places
import app.pukaar.device.Locations
import com.bitchat.android.R
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds

/** The area onboarding 1.7 and Settings offer to save, with a name and a size estimate. */
data class OfflineArea(val name: String, val bounds: LatLngBounds, val estimateMb: Int)

private const val RADIUS_KM = 15.0

/**
 * Around the user when we know where they are ("Detected from your location"); otherwise around
 * the saved places (shelters, hospitals, police) of the bundled region.
 */
suspend fun pickOfflineArea(context: Context): OfflineArea {
    val places = Places.load(context)
    val here = Locations.lastKnown(context)
    val bounds: LatLngBounds
    val name: String
    if (here != null) {
        bounds = OfflineMaps.boundsAround(here.latitude, here.longitude, RADIUS_KM)
        val nearPlaces = places.places.any { Locations.distanceM(here.latitude, here.longitude, it.lat, it.lon) < 50_000f }
        name = if (nearPlaces && places.region.isNotBlank()) places.region else context.getString(R.string.pk_offline_area_here)
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
    return OfflineArea(name, bounds, OfflineMaps.estimateMb(bounds))
}
