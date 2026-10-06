package app.pukaar.ui.screens.map

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalInspectionMode
import app.pukaar.map.OfflineMaps
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import androidx.compose.ui.unit.dp
import app.pukaar.data.Place
import app.pukaar.data.PlaceType
import app.pukaar.sos.NearbySos
import app.pukaar.sos.NearbySosState
import app.pukaar.sos.SosManager
import app.pukaar.device.Locations
import app.pukaar.model.ConnectionStatus
import app.pukaar.ui.components.ConnectionPill
import app.pukaar.ui.components.PreviewTheme
import app.pukaar.ui.components.PrimaryButton
import app.pukaar.ui.components.PukaarPreviews
import app.pukaar.ui.components.TonalButton
import app.pukaar.ui.screens.chat.formatDistance
import app.pukaar.ui.theme.PukaarDimens
import app.pukaar.ui.theme.PukaarIcon
import app.pukaar.ui.theme.Sym
import app.pukaar.ui.theme.status
import com.bitchat.android.R
import kotlin.math.cos
import kotlin.math.roundToInt

data class MapState(
    val places: List<Place>,
    val sampleData: Boolean,
    val region: String,
    val me: LatLon?,
    val meAccuracyM: Float?,
    val connection: ConnectionStatus,
    /** True when the base map for this area is saved for offline use. */
    val mapSaved: Boolean = false,
    /** Other people's SOS heard over the mesh (shown while they still need help). */
    val nearbySos: List<NearbySos> = emptyList(),
    /** Open with this selected: a place id, or "sos:<id>". */
    val focusId: String? = null,
)

data class LatLon(val lat: Double, val lon: Double)

fun placeIcon(type: PlaceType) = when (type) {
    PlaceType.Shelter -> Sym.nightShelter
    PlaceType.Hospital -> Sym.localHospital
    PlaceType.Police -> Sym.localPolice
}

fun placeTypeLabel(type: PlaceType) = when (type) {
    PlaceType.Shelter -> R.string.pk_map_shelter
    PlaceType.Hospital -> R.string.pk_map_hospital
    PlaceType.Police -> R.string.pk_map_police
}

/** Compass point (8-wind) for a bearing, as a string resource. */
fun compassPoint(bearing: Float): Int {
    val points = listOf(
        R.string.pk_dir_n, R.string.pk_dir_ne, R.string.pk_dir_e, R.string.pk_dir_se,
        R.string.pk_dir_s, R.string.pk_dir_sw, R.string.pk_dir_w, R.string.pk_dir_nw,
    )
    return points[(((bearing % 360) + 360) % 360 / 45f).roundToInt() % 8]
}

/**
 * Map (2p). MapLibre draws the base map in Pukaar's style (from the offline area when it's
 * saved, FR-19); shelters, hospitals, police and the user are drawn on top in Compose so they
 * work even with no map tiles at all. Previews use a plain projection instead of MapLibre.
 */
@Composable
fun MapScreen(
    state: MapState,
    onSos: () -> Unit,
    onNetwork: () -> Unit,
    onDirection: (Place) -> Unit,
    onCall: (String) -> Unit,
    onSosDirection: (NearbySos) -> Unit = {},
) {
    var layers by rememberSaveable { mutableStateOf(setOf(PlaceType.Shelter, PlaceType.Hospital, PlaceType.Police)) }
    var selectedId by rememberSaveable(state.focusId) { mutableStateOf(state.focusId) }
    var query by rememberSaveable { mutableStateOf("") }

    val placesCenter = if (state.places.isEmpty()) null else LatLon(state.places.map { it.lat }.average(), state.places.map { it.lon }.average())
    // "Outside the downloaded area" (screens.md §7): more than 50 km from every saved place.
    val outsideArea = state.me != null && state.places.isNotEmpty() &&
        state.places.minOf { Locations.distanceM(state.me.lat, state.me.lon, it.lat, it.lon) } > 50_000f
    val me = if (outsideArea) null else state.me
    val origin = me ?: placesCenter ?: LatLon(0.0, 0.0)
    // Null until the user pans or recentres, so the map follows the first fix instead of jumping on every update.
    var userCenter by remember { mutableStateOf<LatLon?>(null) }
    val center = userCenter ?: origin
    var metersPerPx by remember { mutableFloatStateOf(6f) }
    var size by remember { mutableStateOf(IntSize.Zero) }
    var map by remember { mutableStateOf<MapLibreMap?>(null) }
    var cameraTick by remember { mutableIntStateOf(0) }
    val preview = LocalInspectionMode.current
    val dark = MaterialTheme.colorScheme.surface.luminance() < 0.5f

    // Base map style follows the theme; the camera starts on the user (or the saved area).
    LaunchedEffect(map, dark) {
        map?.setStyle(if (dark) OfflineMaps.STYLE_DARK else OfflineMaps.STYLE_LIGHT)
    }

    val visible = state.places.filter { it.type in layers }
    val from = me ?: center
    val nearestShelter = state.places.filter { it.type == PlaceType.Shelter }
        .minByOrNull { Locations.distanceM(from.lat, from.lon, it.lat, it.lon) }
    val sosPins = state.nearbySos.filter { it.open && it.hasLocation }
    val focusLoc = state.focusId?.takeIf { it.startsWith("loc:") }?.removePrefix("loc:")?.split(",")
        ?.mapNotNull { it.toDoubleOrNull() }?.takeIf { it.size == 2 }?.let { LatLon(it[0], it[1]) }
    val selectedSos = state.nearbySos.firstOrNull { "sos:${it.id}" == selectedId && it.hasLocation }
    val selected = if (selectedSos != null) null else state.places.firstOrNull { it.id == selectedId } ?: nearestShelter
    LaunchedEffect(map, me != null) {
        val focus = selectedSos?.packet
        val target = when {
            focus?.lat != null && focus.lon != null -> LatLng(focus.lat, focus.lon)
            focusLoc != null -> LatLng(focusLoc.lat, focusLoc.lon)
            else -> LatLng(origin.lat, origin.lon)
        }
        map?.moveCamera(CameraUpdateFactory.newLatLngZoom(target, if (focus != null || focusLoc != null) 15.0 else 13.0))
    }

    fun toScreen(p: LatLon): Offset {
        map?.let { m ->
            cameraTick // re-read on every camera move
            val pt = m.projection.toScreenLocation(LatLng(p.lat, p.lon))
            return Offset(pt.x, pt.y)
        }
        val mPerDegLat = 110_540.0
        val mPerDegLon = 111_320.0 * cos(Math.toRadians(center.lat))
        val dx = (p.lon - center.lon) * mPerDegLon / metersPerPx
        val dy = -(p.lat - center.lat) * mPerDegLat / metersPerPx
        return Offset((size.width / 2f + dx).toFloat(), (size.height / 2f + dy).toFloat())
    }

    val colors = MaterialTheme.colorScheme
    val s = MaterialTheme.status
    val density = LocalDensity.current

    Box(Modifier.fillMaxSize().background(colors.surfaceContainerLow)) {
        if (!preview) {
            PukaarMapView(Modifier.fillMaxSize(), onReady = { map = it }, onCameraMove = { cameraTick++ })
        }
        // Pins and location on top. Without MapLibre (previews), this layer pans and zooms itself.
        Box(
            Modifier
                .fillMaxSize()
                .onSizeChanged { size = it }
                .then(if (map != null) Modifier else Modifier.pointerInput(origin) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        metersPerPx = (metersPerPx / zoom).coerceIn(0.3f, 60f)
                        val mPerDegLat = 110_540.0
                        val c = userCenter ?: origin
                        val mPerDegLon = 111_320.0 * cos(Math.toRadians(c.lat))
                        userCenter = LatLon(
                            c.lat + pan.y * metersPerPx / mPerDegLat,
                            c.lon - pan.x * metersPerPx / mPerDegLon,
                        )
                    }
                }),
        ) {
            Canvas(Modifier.fillMaxSize()) {
                // Distance rings every 500 m around the user help judge distance without roads.
                if (me != null) {
                    val c = toScreen(me)
                    // Screen radius of a distance on the ground, measured through the current projection.
                    fun radiusPx(meters: Float) = (toScreen(LatLon(me.lat + meters / 110_540.0, me.lon)) - c).getDistance()
                    // Distance rings only on the plain fallback; the real map has roads to judge by.
                    if (map == null) for (r in 1..6) drawCircle(colors.outlineVariant, radiusPx(r * 500f), c, style = Stroke(1.dp.toPx()))
                    state.meAccuracyM?.let { acc -> drawCircle(colors.primary.copy(alpha = 0.15f), radiusPx(acc), c) }
                    val lineTo = selectedSos?.packet?.let { LatLon(it.lat!!, it.lon!!) } ?: selected?.let { LatLon(it.lat, it.lon) }
                    if (lineTo != null) {
                        drawLine(
                            (if (selectedSos != null) s.sosFill else colors.primary).copy(alpha = 0.6f),
                            c, toScreen(lineTo), 3.dp.toPx(),
                        )
                    }
                    drawCircle(colors.surface, 10.dp.toPx(), c)
                    drawCircle(colors.primary, 7.dp.toPx(), c)
                }
            }
            visible.forEach { place ->
                val pos = toScreen(LatLon(place.lat, place.lon))
                val isSelected = place.id == selected?.id
                val pinSize = if (isSelected) 44.dp else 32.dp
                val half = with(density) { (pinSize / 2).roundToPx() }
                Column(
                    Modifier
                        .offset { IntOffset(pos.x.roundToInt() - half, pos.y.roundToInt() - half) }
                        .clickable(role = Role.Button) { selectedId = place.id },
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    val (bg, fg) = when {
                        isSelected -> colors.primary to colors.onPrimary
                        place.type == PlaceType.Shelter -> colors.primaryContainer to colors.onPrimaryContainer
                        else -> colors.surfaceContainerHighest to colors.onSurface
                    }
                    Box(
                        Modifier.size(pinSize).clip(CircleShape).background(bg).border(2.dp, colors.surface, CircleShape)
                            .semantics { contentDescription = place.name },
                        contentAlignment = Alignment.Center,
                    ) { PukaarIcon(placeIcon(place.type), null, size = if (isSelected) 24.dp else 18.dp, tint = fg) }
                    if (isSelected) {
                        Text(
                            place.name,
                            style = MaterialTheme.typography.labelMedium,
                            color = colors.onSurface,
                            modifier = Modifier.padding(top = 2.dp).clip(MaterialTheme.shapes.extraSmall).background(colors.surface).padding(horizontal = 4.dp),
                        )
                    }
                }
            }

            focusLoc?.let { loc ->
                val pos = toScreen(loc)
                val half = with(density) { 18.dp.roundToPx() }
                Box(
                    Modifier
                        .offset { IntOffset(pos.x.roundToInt() - half, pos.y.roundToInt() - half) }
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(colors.primary)
                        .border(2.dp, colors.surface, CircleShape),
                    contentAlignment = Alignment.Center,
                ) { PukaarIcon(Sym.locationOn, stringResource(R.string.pk_chat_location_shared), size = 22.dp, tint = colors.onPrimary) }
            }
            sosPins.forEach { sos ->
                val pos = toScreen(LatLon(sos.packet.lat!!, sos.packet.lon!!))
                val isSelected = sos == selectedSos
                val pinSize = if (isSelected) 44.dp else 36.dp
                val half = with(density) { (pinSize / 2).roundToPx() }
                val description = stringResource(R.string.pk_map_sos_title, sos.packet.name)
                Box(
                    Modifier
                        .offset { IntOffset(pos.x.roundToInt() - half, pos.y.roundToInt() - half) }
                        .size(pinSize)
                        .clip(CircleShape)
                        .background(s.sosFill)
                        .border(2.dp, colors.surface, CircleShape)
                        .clickable(role = Role.Button) { selectedId = "sos:${sos.id}" }
                        .semantics { contentDescription = description },
                    contentAlignment = Alignment.Center,
                ) { PukaarIcon(Sym.sos, null, size = if (isSelected) 26.dp else 22.dp, tint = s.onSosFill) }
            }
        }

        // Top overlay: connection, search, layer chips, data notes.
        Column(
            Modifier.fillMaxWidth().statusBarsPadding().padding(PukaarDimens.space4),
            verticalArrangement = Arrangement.spacedBy(PukaarDimens.space2),
        ) {
            ConnectionPill(state.connection, onClick = onNetwork)
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text(stringResource(R.string.pk_map_search)) },
                leadingIcon = { PukaarIcon(Sym.search, null) },
                singleLine = true,
                shape = RoundedCornerShape(28.dp),
                colors = OutlinedTextFieldDefaults.colors(focusedContainerColor = colors.surfaceContainerHigh, unfocusedContainerColor = colors.surfaceContainerHigh),
            )
            if (query.isNotBlank()) {
                val results = state.places.filter { it.name.contains(query.trim(), ignoreCase = true) }.take(5)
                Surface(shape = MaterialTheme.shapes.large, color = colors.surfaceContainerHigh) {
                    Column {
                        if (results.isEmpty()) {
                            Text(stringResource(R.string.pk_map_no_results), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(PukaarDimens.space4))
                        }
                        results.forEach { place ->
                            Row(
                                Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable {
                                    selectedId = place.id
                                    userCenter = LatLon(place.lat, place.lon)
                                    map?.animateCamera(CameraUpdateFactory.newLatLngZoom(LatLng(place.lat, place.lon), 14.0))
                                    query = ""
                                }.padding(horizontal = PukaarDimens.space4),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(PukaarDimens.space3),
                            ) {
                                PukaarIcon(placeIcon(place.type), null, tint = colors.primary)
                                Text(place.name, style = MaterialTheme.typography.bodyLarge)
                            }
                        }
                    }
                }
            }
            LazyRow(horizontalArrangement = Arrangement.spacedBy(PukaarDimens.space2)) {
                items(PlaceType.entries) { type ->
                    val on = type in layers
                    Row(
                        Modifier
                            .heightIn(min = PukaarDimens.minTarget)
                            .padding(vertical = 4.dp)
                            .clip(MaterialTheme.shapes.small)
                            .background(if (on) colors.secondaryContainer else colors.surfaceContainerHigh)
                            .clickable(role = Role.Checkbox) { layers = if (on) layers - type else layers + type }
                            .padding(horizontal = PukaarDimens.space3, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(PukaarDimens.space1),
                    ) {
                        PukaarIcon(if (on) Sym.check else placeIcon(type), null, size = 18.dp, tint = if (on) colors.onSecondaryContainer else colors.onSurfaceVariant)
                        Text(stringResource(layerLabel(type)), style = MaterialTheme.typography.labelLarge, color = if (on) colors.onSecondaryContainer else colors.onSurface)
                    }
                }
            }
            Text(
                listOfNotNull(
                    if (outsideArea) stringResource(R.string.pk_map_outside_area) else null,
                    if (state.sampleData) stringResource(R.string.pk_map_sample_note) else null,
                    if (!state.mapSaved) stringResource(R.string.pk_map_tiles_note) else null,
                    stringResource(R.string.pk_map_attribution),
                ).joinToString("\n"),
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant,
                modifier = Modifier.clip(MaterialTheme.shapes.extraSmall).background(colors.surface.copy(alpha = 0.85f)).padding(horizontal = 6.dp, vertical = 2.dp),
            )
        }

        // Bottom: my location + SOS above the sheet, then the place sheet.
        Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth()) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = PukaarDimens.space4, vertical = PukaarDimens.space2),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PrimaryButton(
                    stringResource(R.string.pk_sos), onSos,
                    containerColor = s.sosFill, contentColor = s.onSosFill,
                )
                Box(
                    Modifier.size(56.dp).clip(RoundedCornerShape(16.dp)).background(colors.surfaceContainerHigh)
                        .clickable(enabled = me != null, role = Role.Button) {
                            userCenter = null
                            metersPerPx = 6f
                            me?.let { map?.animateCamera(CameraUpdateFactory.newLatLngZoom(LatLng(it.lat, it.lon), 14.0)) }
                        },
                    contentAlignment = Alignment.Center,
                ) { PukaarIcon(Sym.myLocation, stringResource(R.string.pk_map_my_location), tint = if (me != null) colors.primary else colors.outline) }
            }
            if (selectedSos != null) NearbySosSheet(selectedSos, me, onSosDirection) else PlaceSheet(
                place = selected,
                isNearest = selected?.id == nearestShelter?.id && selectedId == null,
                from = me,
                onDirection = onDirection,
                onCall = onCall,
            )
        }
    }
}

private fun layerLabel(type: PlaceType) = when (type) {
    PlaceType.Shelter -> R.string.pk_map_layer_shelters
    PlaceType.Hospital -> R.string.pk_map_layer_hospitals
    PlaceType.Police -> R.string.pk_map_layer_police
}

@Composable
private fun PlaceSheet(place: Place?, isNearest: Boolean, from: LatLon?, onDirection: (Place) -> Unit, onCall: (String) -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(PukaarDimens.space4), verticalArrangement = Arrangement.spacedBy(PukaarDimens.space2)) {
            Box(Modifier.align(Alignment.CenterHorizontally).size(width = 32.dp, height = 4.dp).clip(CircleShape).background(MaterialTheme.colorScheme.outlineVariant))
            if (place == null) {
                Text(stringResource(R.string.pk_map_no_places), style = MaterialTheme.typography.bodyLarge)
                return@Column
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(PukaarDimens.space3)) {
                Box(Modifier.size(44.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer), contentAlignment = Alignment.Center) {
                    PukaarIcon(placeIcon(place.type), null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
                }
                Column(Modifier.weight(1f)) {
                    Text(
                        stringResource(if (isNearest) R.string.pk_map_nearest_shelter else placeTypeLabel(place.type)),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(place.name, style = MaterialTheme.typography.titleLarge)
                    val details = buildList {
                        if (from != null) {
                            val d = Locations.distanceM(from.lat, from.lon, place.lat, place.lon)
                            val b = Locations.bearing(from.lat, from.lon, place.lat, place.lon)
                            add(stringResource(R.string.pk_map_distance_direction, formatDistance(d), stringResource(compassPoint(b))))
                        } else add(stringResource(R.string.pk_map_no_gps))
                        place.capacity?.let { add(stringResource(R.string.pk_map_capacity, it)) }
                    }
                    Text(details.joinToString(" · "), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(PukaarDimens.space2)) {
                PrimaryButton(stringResource(R.string.pk_map_show_direction), { onDirection(place) }, Modifier.weight(1f), icon = Sym.explore, height = PukaarDimens.minTarget)
                place.phone?.let { phone -> TonalButton(stringResource(R.string.pk_call), { onCall(phone) }, icon = Sym.call) }
            }
        }
    }
}

@PukaarPreviews
@Composable
private fun MapPreview() = PreviewTheme {
    BoxWithConstraints {
        MapScreen(
            MapState(
                places = listOf(
                    Place("a", "Rampur Govt. School", PlaceType.Shelter, 26.1610, 85.9080, capacity = 300),
                    Place("b", "District Hospital", PlaceType.Hospital, 26.1530, 85.8990, "108"),
                    Place("c", "Town Police Station", PlaceType.Police, 26.1500, 85.8930, "100"),
                ),
                sampleData = true, region = "Darbhanga", me = LatLon(26.152, 85.897), meAccuracyM = 20f,
                connection = ConnectionStatus.mesh(4),
            ),
            {}, {}, {}, {},
        )
    }
}

/** Someone else's SOS on the map: what they need, how far, and the way there. */
@Composable
private fun NearbySosSheet(sos: NearbySos, from: LatLon?, onDirection: (NearbySos) -> Unit) {
    val s = MaterialTheme.status
    val p = sos.packet
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(PukaarDimens.space4), verticalArrangement = Arrangement.spacedBy(PukaarDimens.space2)) {
            Box(Modifier.align(Alignment.CenterHorizontally).size(width = 32.dp, height = 4.dp).clip(CircleShape).background(MaterialTheme.colorScheme.outlineVariant))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(PukaarDimens.space3)) {
                Box(Modifier.size(44.dp).clip(CircleShape).background(s.sosFill), contentAlignment = Alignment.Center) {
                    PukaarIcon(Sym.sos, null, tint = s.onSosFill)
                }
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.pk_map_sos_title, p.name), style = MaterialTheme.typography.titleLarge)
                    val needs = listOf(pluralStringResource(R.plurals.pk_people_count, p.people, p.people)) +
                        p.flags.map { stringResource(SosManager.flagLabel(it)) }
                    Text(needs.joinToString(" · "), style = MaterialTheme.typography.bodyLarge)
                }
            }
            if (p.message.isNotBlank()) Text("\u201c${p.message}\u201d", style = MaterialTheme.typography.bodyLarge)
            val meta = buildList {
                if (from != null && p.lat != null && p.lon != null) {
                    val d = Locations.distanceM(from.lat, from.lon, p.lat, p.lon)
                    val b = Locations.bearing(from.lat, from.lon, p.lat, p.lon)
                    add(stringResource(R.string.pk_map_distance_direction, formatDistance(d), stringResource(compassPoint(b))))
                }
                add(stringResource(R.string.pk_chat_battery, p.battery))
                add(android.text.format.DateFormat.getTimeFormat(androidx.compose.ui.platform.LocalContext.current).format(java.util.Date(p.timeSec * 1000)))
            }
            Text(meta.joinToString(" · "), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (sos.state == NearbySosState.Attending) {
                app.pukaar.ui.components.Tag(stringResource(R.string.pk_sos_hero_attending), s.confirmed)
            }
            app.pukaar.ui.components.InfoBox(Sym.warning, stringResource(R.string.pk_map_sos_help_hint), s.warning)
            PrimaryButton(stringResource(R.string.pk_map_show_direction), { onDirection(sos) }, Modifier.fillMaxWidth(), icon = Sym.explore, height = PukaarDimens.minTarget)
        }
    }
}
