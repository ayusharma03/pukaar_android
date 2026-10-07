package app.pukaar.data

import android.content.Context
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName

enum class PlaceType {
    @SerializedName("shelter") Shelter,
    @SerializedName("hospital") Hospital,
    @SerializedName("police") Police,
}

data class Place(
    val id: String,
    val name: String,
    val type: PlaceType,
    val lat: Double,
    val lon: Double,
    val phone: String? = null,
    val capacity: Int? = null,
)

data class PlaceSet(val region: String, val sample: Boolean, val places: List<Place>)

/**
 * Shelters, hospitals and police stations for the saved area (FR-20), read from
 * assets/pukaar/places.json so they work with no network. The bundled file is sample data.
 */
object Places {
    @Volatile private var cached: PlaceSet? = null

    fun load(context: Context): PlaceSet {
        cached?.let { return it }
        val set = runCatching {
            context.assets.open("pukaar/places.json").bufferedReader().use { Gson().fromJson(it, PlaceSet::class.java) }
        }.getOrNull()?.let { s ->
            // Gson skips Kotlin defaults: drop entries with missing fields.
            @Suppress("SENSELESS_COMPARISON")
            s.copy(places = s.places.orEmpty().filter { it.id != null && it.name != null && it.type != null })
        } ?: PlaceSet("", sample = true, places = emptyList())
        cached = set
        return set
    }

    fun byId(context: Context, id: String) = load(context).places.firstOrNull { it.id == id }
}
