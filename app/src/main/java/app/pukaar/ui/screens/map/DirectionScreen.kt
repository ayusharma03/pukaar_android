package app.pukaar.ui.screens.map

import android.app.Activity
import android.content.Context
import android.hardware.GeomagneticField
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.view.WindowManager
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.pukaar.data.Place
import app.pukaar.data.PlaceType
import app.pukaar.device.Locations
import app.pukaar.ui.components.Compass
import app.pukaar.ui.components.InfoBox
import app.pukaar.ui.components.OutlineButton
import app.pukaar.ui.components.PreviewTheme
import app.pukaar.ui.components.PrimaryButton
import app.pukaar.ui.components.PukaarCard
import app.pukaar.ui.components.PukaarPreviews
import app.pukaar.ui.components.PukaarTopBar
import app.pukaar.ui.components.Tag
import app.pukaar.ui.screens.chat.formatDistance
import app.pukaar.ui.screens.sos.KeepScreenOn
import app.pukaar.ui.theme.PukaarDimens
import app.pukaar.ui.theme.PukaarIcon
import app.pukaar.ui.theme.PukaarTextStyles
import app.pukaar.ui.theme.Sym
import app.pukaar.ui.theme.status
import com.bitchat.android.R
import kotlin.math.max

data class DirectionState(
    val place: Place,
    val me: Location?,
    val headingDeg: Float,
    val needsCalibration: Boolean,
)

/** Stateful Direction: sensors, location, screen on and dimmed. */
@Composable
fun DirectionRoute(place: Place, onBack: () -> Unit, onChange: () -> Unit) {
    val context = LocalContext.current
    var me by remember { mutableStateOf<Location?>(null) }
    var heading by remember { mutableFloatStateOf(0f) }
    var accuracy by remember { mutableIntStateOf(SensorManager.SENSOR_STATUS_ACCURACY_HIGH) }

    androidx.compose.runtime.LaunchedEffect(Unit) {
        Locations.updates(context, 2_000).collect { me = it }
    }
    DisposableEffect(Unit) {
        val sm = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val sensor = sm.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
        val rot = FloatArray(9)
        val orient = FloatArray(3)
        val listener = object : SensorEventListener {
            override fun onSensorChanged(e: SensorEvent) {
                SensorManager.getRotationMatrixFromVector(rot, e.values)
                SensorManager.getOrientation(rot, orient)
                var azimuth = Math.toDegrees(orient[0].toDouble()).toFloat()
                me?.let { loc ->
                    azimuth += GeomagneticField(loc.latitude.toFloat(), loc.longitude.toFloat(), loc.altitude.toFloat(), System.currentTimeMillis()).declination
                }
                azimuth = (azimuth + 360f) % 360f
                // Smooth across the 0/360 seam.
                var delta = azimuth - heading
                if (delta > 180) delta -= 360
                if (delta < -180) delta += 360
                heading = (heading + delta * 0.2f + 360f) % 360f
            }

            override fun onAccuracyChanged(s: Sensor?, a: Int) {
                accuracy = a
            }
        }
        sensor?.let { sm.registerListener(listener, it, SensorManager.SENSOR_DELAY_UI) }
        onDispose { sm.unregisterListener(listener) }
    }
    KeepScreenOn()
    DimScreen(0.3f)

    DirectionScreen(
        DirectionState(place, me, heading, accuracy <= SensorManager.SENSOR_STATUS_ACCURACY_LOW),
        onBack = onBack,
        onChange = onChange,
    )
}

/** Dims the window while shown, to save battery on long walks. */
@Composable
private fun DimScreen(brightness: Float) {
    val activity = LocalContext.current as? Activity ?: return
    DisposableEffect(activity) {
        val window = activity.window
        val before = window.attributes.screenBrightness
        window.attributes = window.attributes.apply { screenBrightness = brightness }
        onDispose { window.attributes = window.attributes.apply { screenBrightness = before.takeIf { it >= 0 } ?: WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE } }
    }
}

/** Direction to shelter (2q): a compass that points the straight-line way and counts down the distance. */
@Composable
fun DirectionScreen(state: DirectionState, onBack: () -> Unit, onChange: () -> Unit) {
    val place = state.place
    val me = state.me
    val distance = me?.let { Locations.distanceM(it.latitude, it.longitude, place.lat, place.lon) }
    val bearing = me?.let { Locations.bearing(it.latitude, it.longitude, place.lat, place.lon) }
    val arrived = distance != null && distance <= 50f

    Column(Modifier.fillMaxSize()) {
        PukaarTopBar(stringResource(R.string.pk_direction_title), onBack = onBack, actions = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.padding(end = PukaarDimens.space2)) {
                PukaarIcon(Sym.brightnessLow, null, size = 18.dp, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(stringResource(R.string.pk_direction_screen_on), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        })
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = PukaarDimens.space4),
            verticalArrangement = Arrangement.spacedBy(PukaarDimens.space3),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            PukaarCard(Modifier.fillMaxWidth(), padding = PukaarDimens.space3) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(PukaarDimens.space3)) {
                    Box(Modifier.size(44.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer), contentAlignment = Alignment.Center) {
                        PukaarIcon(placeIcon(place.type), null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
                    }
                    Column(Modifier.weight(1f)) {
                        Text(place.name, style = MaterialTheme.typography.titleMedium)
                        Text(stringResource(R.string.pk_direction_subtitle, stringResource(placeTypeLabel(place.type))), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    OutlineButton(stringResource(R.string.pk_change), onChange)
                }
            }

            when {
                me == null -> InfoBox(Sym.myLocation, stringResource(R.string.pk_direction_no_gps), MaterialTheme.status.warning)
                state.needsCalibration -> InfoBox(Sym.explore, stringResource(R.string.pk_direction_calibrate), MaterialTheme.status.warning)
            }

            val compassDescription = if (bearing != null) stringResource(R.string.pk_direction_compass_description, stringResource(compassPoint(bearing))) else null
            Compass(headingDeg = state.headingDeg, bearingDeg = bearing, description = compassDescription)

            if (arrived) {
                Tag(stringResource(R.string.pk_direction_arrived), MaterialTheme.status.confirmed)
            }
            if (distance != null) {
                Text(
                    formatDistance(distance),
                    style = PukaarTextStyles.compassDistance,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                )
                val minutes = max(1, (distance / 1.3f / 60f).toInt())
                Text(pluralStringResource(R.plurals.pk_direction_walk, minutes, minutes), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (distance > 5_000) InfoBox(Sym.info, stringResource(R.string.pk_direction_far), MaterialTheme.status.warning)
            }

            InfoBox(Sym.warning, stringResource(R.string.pk_direction_warning), MaterialTheme.status.warning)

            if (me != null) MiniMap(me, place)
            Box(Modifier.height(PukaarDimens.space2))
        }
        PrimaryButton(
            stringResource(R.string.pk_stop),
            onBack,
            Modifier.fillMaxWidth().navigationBarsPadding().padding(PukaarDimens.space4),
            icon = Sym.close,
            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            contentColor = MaterialTheme.colorScheme.onSurface,
        )
    }
}

/** Small preview with the user and the shelter, north up. */
@Composable
private fun MiniMap(me: Location, place: Place) {
    val colors = MaterialTheme.colorScheme
    val description = stringResource(R.string.pk_direction_minimap)
    Canvas(
        Modifier
            .fillMaxWidth()
            .height(120.dp)
            .clip(MaterialTheme.shapes.large)
            .background(colors.surfaceContainerLow)
            .semantics { contentDescription = description },
    ) {
        val pad = 24.dp.toPx()
        val dLat = place.lat - me.latitude
        val dLon = (place.lon - me.longitude) * kotlin.math.cos(Math.toRadians(me.latitude))
        val span = max(kotlin.math.abs(dLat), kotlin.math.abs(dLon)).coerceAtLeast(1e-6)
        val scale = (minOf(size.width, size.height) / 2 - pad) / span
        val c = Offset(size.width / 2, size.height / 2)
        val a = Offset((c.x - dLon * scale / 2).toFloat(), (c.y + dLat * scale / 2).toFloat())
        val b = Offset((c.x + dLon * scale / 2).toFloat(), (c.y - dLat * scale / 2).toFloat())
        drawLine(colors.primary, a, b, 3.dp.toPx())
        drawCircle(colors.surface, 9.dp.toPx(), a)
        drawCircle(colors.primary, 6.dp.toPx(), a)
        drawCircle(colors.primaryContainer, 10.dp.toPx(), b)
        drawCircle(colors.onPrimaryContainer, 4.dp.toPx(), b)
    }
}

@PukaarPreviews
@Composable
private fun DirectionPreview() = PreviewTheme {
    val loc = Location("preview").apply { latitude = 26.152; longitude = 85.897 }
    DirectionScreen(
        DirectionState(Place("a", "Rampur Govt. School", PlaceType.Shelter, 26.1580, 85.9020, capacity = 300), loc, 20f, false),
        {}, {},
    )
}
