package app.pukaar.device

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.os.Looper
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/** Location for SOS, the map and the compass. Uses GPS only; works without internet. */
object Locations {

    fun hasPermission(context: Context) =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    /** A fresh fix within [timeoutMs], or the last known one, or null. */
    @SuppressLint("MissingPermission")
    suspend fun current(context: Context, timeoutMs: Long = 8_000): Location? {
        if (!hasPermission(context)) return null
        val client = LocationServices.getFusedLocationProviderClient(context)
        val fresh = withTimeoutOrNull(timeoutMs) {
            suspendCancellableCoroutine<Location?> { cont ->
                val token = CancellationTokenSource()
                client.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, token.token)
                    .addOnSuccessListener { cont.resume(it) }
                    .addOnFailureListener { cont.resume(null) }
                cont.invokeOnCancellation { token.cancel() }
            }
        }
        return fresh ?: lastKnown(context)
    }

    @SuppressLint("MissingPermission")
    suspend fun lastKnown(context: Context): Location? {
        if (!hasPermission(context)) return null
        return suspendCancellableCoroutine { cont ->
            LocationServices.getFusedLocationProviderClient(context).lastLocation
                .addOnSuccessListener { cont.resume(it) }
                .addOnFailureListener { cont.resume(null) }
        }
    }

    /** Continuous updates while collected (map, compass). Emits nothing without permission. */
    @SuppressLint("MissingPermission")
    fun updates(context: Context, intervalMs: Long = 3_000): Flow<Location> = callbackFlow {
        if (!hasPermission(context)) {
            awaitClose { }
            return@callbackFlow
        }
        val client = LocationServices.getFusedLocationProviderClient(context)
        val callback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.lastLocation?.let { trySend(it) }
            }
        }
        client.lastLocation.addOnSuccessListener { it?.let { loc -> trySend(loc) } }
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, intervalMs).build()
        client.requestLocationUpdates(request, callback, Looper.getMainLooper())
        awaitClose { client.removeLocationUpdates(callback) }
    }

    fun distanceM(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Float {
        val out = FloatArray(2)
        Location.distanceBetween(lat1, lon1, lat2, lon2, out)
        return out[0]
    }

    /** Initial bearing in degrees (0 = north) from point 1 to point 2. */
    fun bearing(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Float {
        val out = FloatArray(2)
        Location.distanceBetween(lat1, lon1, lat2, lon2, out)
        return (out[1] + 360f) % 360f
    }
}
