package app.pukaar.map

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject
import org.maplibre.android.MapLibre
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.offline.OfflineManager
import org.maplibre.android.offline.OfflineRegion
import org.maplibre.android.offline.OfflineRegionError
import org.maplibre.android.offline.OfflineRegionStatus
import org.maplibre.android.offline.OfflineTilePyramidRegionDefinition
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.tan

/**
 * The offline map area (FR-19, FR-22): OpenFreeMap tiles and fonts for one area, downloaded while
 * online into MapLibre's offline database, which Pukaar's dark and light styles then read from
 * with no network.
 */
object OfflineMaps {
    private const val TAG = "PukaarMaps"
    const val STYLE_DARK = "asset://pukaar/map/style_dark.json"
    const val STYLE_LIGHT = "asset://pukaar/map/style_light.json"
    /**
     * MapLibre's offline downloader only fetches http(s) styles, not asset:// ones. This hosted
     * style uses the same tile source and fonts as Pukaar's styles, and the offline cache is shared
     * by every style, so downloading with it makes Pukaar's own styles work offline.
     */
    private const val STYLE_DOWNLOAD = "https://tiles.openfreemap.org/styles/positron"
    private const val MIN_ZOOM = 6.0
    private const val MAX_ZOOM = 14.0 // OpenFreeMap tiles stop at 14; MapLibre overzooms beyond

    sealed interface State {
        data object None : State
        data class Downloading(val percent: Int, val bytes: Long) : State
        data class Saved(val name: String, val bytes: Long, val savedAt: Long) : State
        data class Failed(val reason: String) : State
    }

    private val _state = MutableStateFlow<State>(State.None)
    val state: StateFlow<State> = _state.asStateFlow()

    private val main = Handler(Looper.getMainLooper())
    @Volatile private var initialized = false

    fun init(context: Context) {
        if (initialized) return
        initialized = true
        main.post {
            MapLibre.getInstance(context.applicationContext)
            refresh(context)
        }
    }

    /** Reads what is already saved. */
    fun refresh(context: Context) = main.post {
        OfflineManager.getInstance(context).listOfflineRegions(object : OfflineManager.ListOfflineRegionsCallback {
            override fun onList(offlineRegions: Array<OfflineRegion>?) {
                val regions = offlineRegions.orEmpty().filter { meta(it)?.optBoolean("pukaar") == true }
                val done = regions.filter { meta(it)?.optBoolean("complete") == true }
                if (done.isEmpty()) {
                    if (_state.value !is State.Downloading) _state.value = State.None
                    return
                }
                var total = 0L
                var pending = done.size
                done.forEach { region ->
                    region.getStatus(object : OfflineRegion.OfflineRegionStatusCallback {
                        override fun onStatus(status: OfflineRegionStatus?) {
                            total = max(total, status?.completedResourceSize ?: 0L)
                            if (--pending == 0) {
                                val m = meta(done.first())
                                _state.value = State.Saved(m?.optString("name").orEmpty(), total, m?.optLong("at") ?: 0L)
                            }
                        }

                        override fun onError(error: String?) {
                            if (--pending == 0) _state.value = State.Saved(meta(done.first())?.optString("name").orEmpty(), total, 0L)
                        }
                    })
                }
            }

            override fun onError(error: String) {
                Log.w(TAG, "List regions failed: $error")
            }
        })
    }

    /** Downloads [bounds] for both styles, replacing any earlier Pukaar area. */
    fun download(context: Context, bounds: LatLngBounds, name: String) {
        if (_state.value is State.Downloading) return
        _state.value = State.Downloading(0, 0)
        val app = context.applicationContext
        deleteAll(app) {
            downloadStyle(app, STYLE_DOWNLOAD, bounds, name, onDone = { bytes ->
                _state.value = State.Saved(name, bytes, System.currentTimeMillis())
            })
        }
    }

    private fun downloadStyle(
        context: Context,
        style: String,
        bounds: LatLngBounds,
        name: String,
        onDone: (Long) -> Unit,
    ) {
        val definition = OfflineTilePyramidRegionDefinition(style, bounds, MIN_ZOOM, MAX_ZOOM, context.resources.displayMetrics.density)
        val metadata = JSONObject().put("pukaar", true).put("name", name).put("style", style).put("complete", false)
        OfflineManager.getInstance(context).createOfflineRegion(definition, metadata.toString().toByteArray(), object : OfflineManager.CreateOfflineRegionCallback {
            override fun onCreate(offlineRegion: OfflineRegion) {
                offlineRegion.setObserver(object : OfflineRegion.OfflineRegionObserver {
                    override fun onStatusChanged(status: OfflineRegionStatus) {
                        val pct = if (status.requiredResourceCount > 0) (status.completedResourceCount * 100 / status.requiredResourceCount).toInt() else 0
                        _state.value = State.Downloading(pct.coerceIn(0, 99), status.completedResourceSize)
                        if (status.isComplete) {
                            offlineRegion.setDownloadState(OfflineRegion.STATE_INACTIVE)
                            offlineRegion.setObserver(null)
                            val done = metadata.put("complete", true).put("at", System.currentTimeMillis())
                            offlineRegion.updateMetadata(done.toString().toByteArray(), object : OfflineRegion.OfflineRegionUpdateMetadataCallback {
                                override fun onUpdate(metadata: ByteArray) = onDone(status.completedResourceSize)
                                override fun onError(error: String) = onDone(status.completedResourceSize)
                            })
                        }
                    }

                    override fun onError(error: OfflineRegionError) {
                        // Transient errors (a tile failed) are retried by MapLibre; log only.
                        Log.w(TAG, "Offline download: ${error.reason} ${error.message}")
                    }

                    override fun mapboxTileCountLimitExceeded(limit: Long) {
                        _state.value = State.Failed("tile limit $limit")
                    }
                })
                offlineRegion.setDownloadState(OfflineRegion.STATE_ACTIVE)
            }

            override fun onError(error: String) {
                Log.w(TAG, "Create region failed: $error")
                _state.value = State.Failed(error)
            }
        })
    }

    fun delete(context: Context) {
        deleteAll(context.applicationContext) { _state.value = State.None }
    }

    private fun deleteAll(context: Context, then: () -> Unit) = main.post {
        OfflineManager.getInstance(context).listOfflineRegions(object : OfflineManager.ListOfflineRegionsCallback {
            override fun onList(offlineRegions: Array<OfflineRegion>?) {
                val ours = offlineRegions.orEmpty().filter { meta(it)?.optBoolean("pukaar") == true }
                if (ours.isEmpty()) return then()
                var pending = ours.size
                ours.forEach {
                    it.setDownloadState(OfflineRegion.STATE_INACTIVE)
                    it.delete(object : OfflineRegion.OfflineRegionDeleteCallback {
                        override fun onDelete() { if (--pending == 0) then() }
                        override fun onError(error: String) { if (--pending == 0) then() }
                    })
                }
            }

            override fun onError(error: String) = then()
        })
    }

    private fun meta(region: OfflineRegion): JSONObject? = runCatching { JSONObject(String(region.metadata)) }.getOrNull()

    /** A square area around a point, [radiusKm] in each direction. */
    fun boundsAround(lat: Double, lon: Double, radiusKm: Double): LatLngBounds {
        val dLat = radiusKm / 110.574
        val dLon = radiusKm / (111.320 * cos(Math.toRadians(lat)))
        return LatLngBounds.Builder()
            .include(LatLng(lat - dLat, lon - dLon))
            .include(LatLng(lat + dLat, lon + dLon))
            .build()
    }

    /** Rough download size in MB: tile count across zooms × an average vector tile size. */
    fun estimateMb(bounds: LatLngBounds): Int {
        var tiles = 0L
        for (z in MIN_ZOOM.toInt()..MAX_ZOOM.toInt()) {
            val n = 1 shl z
            val x1 = lonToTile(bounds.longitudeWest, n)
            val x2 = lonToTile(bounds.longitudeEast, n)
            val y1 = latToTile(bounds.latitudeNorth, n)
            val y2 = latToTile(bounds.latitudeSouth, n)
            tiles += (x2 - x1 + 1).toLong() * (y2 - y1 + 1).toLong()
        }
        return max(1, (tiles * 45 / 1024).toInt()) // ~45 KB per tile with glyphs, a cautious average
    }

    private fun lonToTile(lon: Double, n: Int) = floor((lon + 180.0) / 360.0 * n).toInt()
    private fun latToTile(lat: Double, n: Int): Int {
        val r = Math.toRadians(lat)
        return floor((1.0 - ln(tan(r) + 1 / cos(r)) / PI) / 2.0 * n).toInt()
    }
}
