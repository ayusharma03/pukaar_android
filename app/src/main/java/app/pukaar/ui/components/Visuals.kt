package app.pukaar.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.pukaar.model.ConnectionStatus
import app.pukaar.model.ConnectionType
import app.pukaar.ui.theme.PukaarDimens
import app.pukaar.ui.theme.PukaarIcon
import app.pukaar.ui.theme.PukaarTextStyles
import app.pukaar.ui.theme.StatusColor
import app.pukaar.ui.theme.Sym
import app.pukaar.ui.theme.status
import com.bitchat.android.R
import kotlin.math.cos
import kotlin.math.sin

private val RippleFieldSize = 312.dp
private val RingDiameter = 300.dp
private val NodeSize = 22.dp

/**
 * Home ripple field (handoff "Custom components" 2): a ring around the SOS button whose style
 * shows the connection state, with a node on the ring for each nearby phone.
 */
@Composable
fun RippleField(
    connection: ConnectionStatus,
    modifier: Modifier = Modifier,
    center: @Composable BoxScope.() -> Unit,
) {
    val s = MaterialTheme.status
    val ringColor = when (connection.type) {
        ConnectionType.Online, ConnectionType.Gateway -> s.confirmed.main
        ConnectionType.Mesh -> s.mesh.main
        ConnectionType.Radio -> s.radio.main
        ConnectionType.Isolated -> s.warning.main
    }
    val dashed = connection.type == ConnectionType.Mesh || connection.type == ConnectionType.Isolated

    Box(modifier.size(RippleFieldSize), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(RingDiameter).clearAndSetSemantics { }) {
            drawCircle(
                color = ringColor.copy(alpha = 0.7f),
                radius = size.minDimension / 2 - 1.dp.toPx(),
                style = Stroke(
                    width = 1.5.dp.toPx(),
                    pathEffect = if (dashed) PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 5.dp.toPx())) else null,
                ),
            )
        }
        center()

        val radius = RingDiameter / 2
        when (connection.type) {
            ConnectionType.Mesh -> {
                val count = connection.peers.coerceIn(1, 8)
                repeat(count) { i ->
                    // Spread from the upper right, the way the design places them.
                    val angle = Math.toRadians(-60.0 + i * (360.0 / count))
                    PhoneNode(
                        s.mesh,
                        Modifier.offset(x = radius * cos(angle).toFloat(), y = radius * sin(angle).toFloat()),
                    )
                }
            }
            ConnectionType.Radio -> RingBadge(Modifier.offset(y = -radius)) {
                Icon(painterResource(R.drawable.ic_radio), null, tint = s.radio.onContainer, modifier = Modifier.size(16.dp))
            }
            ConnectionType.Gateway -> RingBadge(Modifier.offset(x = radius * 0.7071f, y = -radius * 0.7071f), colors = s.confirmed) {
                Text("+${connection.gatewayHelping}", style = MaterialTheme.typography.labelMedium, color = s.confirmed.onContainer)
            }
            else -> Unit
        }
    }
}

@Composable
private fun PhoneNode(colors: StatusColor, modifier: Modifier = Modifier) {
    Box(
        modifier
            .size(NodeSize)
            .clip(CircleShape)
            .background(colors.container)
            .border(2.dp, colors.main, CircleShape)
            .clearAndSetSemantics { },
        contentAlignment = Alignment.Center,
    ) {
        PukaarIcon(Sym.smartphone, null, size = 14.dp, tint = colors.onContainer)
    }
}

@Composable
private fun RingBadge(modifier: Modifier, colors: StatusColor = MaterialTheme.status.radio, content: @Composable () -> Unit) {
    Box(
        modifier
            .size(32.dp)
            .clip(CircleShape)
            .background(colors.container)
            .border(2.dp, colors.main, CircleShape)
            .clearAndSetSemantics { },
        contentAlignment = Alignment.Center,
    ) { content() }
}

/** One stop on the hops path. */
data class HopNode(
    val label: String,
    val icon: String? = null,
    val painter: Int? = null,
    val colors: StatusColor?,
    val reached: Boolean,
)

/**
 * "How your SOS travelled" (2i): 44 dp circles joined by 2 dp dotted lines.
 * Stops not reached yet are drawn in neutral colours.
 */
@Composable
fun HopsPath(nodes: List<HopNode>, modifier: Modifier = Modifier) {
    val neutral = MaterialTheme.colorScheme.outlineVariant
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        nodes.forEachIndexed { i, node ->
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.fillMaxWidth().height(44.dp), contentAlignment = Alignment.Center) {
                    // Connector lines to the neighbours, behind the circle.
                    Canvas(Modifier.fillMaxSize()) {
                        val y = size.height / 2
                        val effect = PathEffect.dashPathEffect(floatArrayOf(2.dp.toPx(), 4.dp.toPx()))
                        val lineColor = if (node.reached) (node.colors?.main ?: neutral) else neutral
                        if (i > 0) drawLine(lineColor, Offset(0f, y), Offset(size.width / 2, y), 2.dp.toPx(), StrokeCap.Round, effect)
                        if (i < nodes.lastIndex) {
                            val nextColor = if (nodes[i + 1].reached) (nodes[i + 1].colors?.main ?: neutral) else neutral
                            drawLine(nextColor, Offset(size.width / 2, y), Offset(size.width, y), 2.dp.toPx(), StrokeCap.Round, effect)
                        }
                    }
                    val bg = if (node.reached) node.colors?.container ?: MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh
                    val fg = if (node.reached) node.colors?.onContainer ?: MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                    Box(Modifier.size(44.dp).clip(CircleShape).background(bg), contentAlignment = Alignment.Center) {
                        when {
                            node.icon != null -> PukaarIcon(node.icon, null, tint = fg)
                            node.painter != null -> Icon(painterResource(node.painter), null, tint = fg, modifier = Modifier.size(24.dp))
                        }
                    }
                }
                Text(
                    node.label,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (node.reached) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = PukaarDimens.space1, start = 2.dp, end = 2.dp),
                )
            }
        }
    }
}

/**
 * Compass (2q): a 280 dp dial with ticks every 5° and N at the top in true north,
 * and a kite arrow pointing to [bearingDeg]. [headingDeg] is where the phone points.
 */
@Composable
fun Compass(headingDeg: Float, bearingDeg: Float?, modifier: Modifier = Modifier, size: Dp = 280.dp, description: String? = null) {
    val dial = MaterialTheme.colorScheme.surfaceContainerLow
    val tick = MaterialTheme.colorScheme.outline
    val north = MaterialTheme.colorScheme.onSurface
    val front = MaterialTheme.colorScheme.primary
    val back = MaterialTheme.colorScheme.primaryContainer
    Box(
        modifier
            .size(size)
            .semantics { if (description != null) contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val r = this.size.minDimension / 2
            drawCircle(dial, r)
            rotate(-headingDeg) {
                for (deg in 0 until 360 step 5) {
                    val long = deg % 30 == 0
                    val major = deg % 90 == 0
                    val len = when {
                        major -> 18.dp.toPx()
                        long -> 12.dp.toPx()
                        else -> 6.dp.toPx()
                    }
                    val a = Math.toRadians(deg.toDouble() - 90)
                    val outer = r - 6.dp.toPx()
                    val start = Offset(center.x + (outer - len) * cos(a).toFloat(), center.y + (outer - len) * sin(a).toFloat())
                    val end = Offset(center.x + outer * cos(a).toFloat(), center.y + outer * sin(a).toFloat())
                    drawLine(if (deg == 0) north else tick, start, end, if (long) 2.dp.toPx() else 1.dp.toPx(), StrokeCap.Round)
                }
            }
            if (bearingDeg != null) {
                rotate(bearingDeg - headingDeg) {
                    val tip = Offset(center.x, center.y - r * 0.62f)
                    val tail = Offset(center.x, center.y + r * 0.42f)
                    val w = r * 0.22f
                    val frontPath = Path().apply {
                        moveTo(tip.x, tip.y); lineTo(center.x + w, center.y); lineTo(center.x - w, center.y); close()
                    }
                    val backPath = Path().apply {
                        moveTo(center.x + w, center.y); lineTo(tail.x, tail.y); lineTo(center.x - w, center.y); close()
                    }
                    drawPath(backPath, back)
                    drawPath(frontPath, front)
                }
            }
        }
        // "N" sits on the dial, turned with it.
        Box(Modifier.size(size).rotate(-headingDeg), contentAlignment = Alignment.TopCenter) {
            Text("N", style = MaterialTheme.typography.titleMedium, color = north, modifier = Modifier.padding(top = 28.dp).rotate(headingDeg))
        }
    }
}

/** SOS countdown ring (2k): white ring showing time left, around the big number. */
@Composable
fun CountdownRing(progress: Float, number: Int, color: Color, modifier: Modifier = Modifier) {
    Box(
        modifier
            .size(240.dp)
            .clip(CircleShape)
            .background(color.copy(alpha = 0.08f)),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize().padding(8.dp)) {
            drawArc(
                color = color,
                startAngle = -90f,
                sweepAngle = 360f * progress.coerceIn(0f, 1f),
                useCenter = false,
                style = Stroke(width = 8.dp.toPx(), cap = StrokeCap.Round),
            )
        }
        Text(number.toString(), style = PukaarTextStyles.countdown, color = color)
    }
}

@Composable
internal fun Spacer8() = Box(Modifier.width(PukaarDimens.space2))

@PukaarPreviews
@Composable
private fun VisualsPreview() = PreviewTheme {
    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        RippleField(ConnectionStatus.mesh(4, 18)) { SosButton(onClick = {}, animate = false) }
        HopsPath(
            listOf(
                HopNode("Your phone", icon = Sym.smartphone, colors = null, reached = true),
                HopNode("2 phones", painter = R.drawable.ic_mesh, colors = MaterialTheme.status.mesh, reached = true),
                HopNode("Phone with internet", icon = Sym.sendToMobile, colors = MaterialTheme.status.confirmed, reached = true),
                HopNode("Rescuers", icon = Sym.cloudDone, colors = MaterialTheme.status.confirmed, reached = false),
            ),
            Modifier.padding(horizontal = 16.dp),
        )
        Compass(headingDeg = 20f, bearingDeg = 45f, size = 200.dp)
    }
}
