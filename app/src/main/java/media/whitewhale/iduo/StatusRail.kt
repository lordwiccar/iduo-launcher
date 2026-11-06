package media.whitewhale.iduo

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material3.Icon
import androidx.compose.runtime.*
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlin.math.cos
import kotlin.math.sin

/** The current time, updated just after each minute turns. */
@Composable
private fun currentMinute(): LocalDateTime {
    val now by produceState(LocalDateTime.now()) {
        while (true) {
            value = LocalDateTime.now()
            delay(60_050L - (System.currentTimeMillis() % 60_000L))
        }
    }
    return now
}

/** Formatters for the time, the short date and the spoken date. */
private class StatusFormats(val time: DateTimeFormatter, val date: DateTimeFormatter, val spokenDate: DateTimeFormatter)

@Composable
private fun rememberStatusFormats(): StatusFormats {
    val format = if (android.text.format.DateFormat.is24HourFormat(LocalContext.current)) "HH:mm" else "h:mm"
    val locale = LocalConfiguration.current.locales[0]
    return remember(format, locale) {
        StatusFormats(DateTimeFormatter.ofPattern(format), localizedDateFormatter(locale, "MMMd"),
            localizedDateFormatter(locale, "EEEEMMMMd"))
    }
}

@Composable
private fun statusDescription(status: DeviceStatus, now: LocalDateTime, formats: StatusFormats, locationInUse: Boolean = false) =
    listOfNotNull(
        if (locationInUse) stringResource(R.string.status_location) else null,
        "${now.format(formats.spokenDate)}, ${now.format(formats.time)}",
        status.battery?.let { stringResource(if (status.charging) R.string.status_battery_charging else R.string.status_battery, it) }
            ?: stringResource(R.string.status_battery_unavailable),
        if (status.powerSave) stringResource(R.string.status_battery_saver) else null,
        if (status.wifiConnected) status.wifiLevel?.let { stringResource(R.string.status_wifi_level, it) } ?: stringResource(R.string.status_wifi)
        else stringResource(R.string.status_wifi_off),
        if (status.airplane) stringResource(R.string.status_airplane)
        else if (status.simLevels.size >= 2) status.simLevels.mapIndexed { index, level ->
            level?.let { stringResource(R.string.status_sim_signal, index + 1, it) } ?: stringResource(R.string.status_cellular_unavailable)
        }.joinToString(". ")
        else status.cellularLevel?.let { stringResource(R.string.status_cellular, it) }
            ?: stringResource(R.string.status_cellular_unavailable),
        status.cellularNetwork?.takeIf { status.cellularData && !status.wifiConnected && !status.airplane }
            ?.let { stringResource(R.string.status_mobile_data, it) },
    ).joinToString(". ")

private val statusTextStyle = TextStyle(shadow = Shadow(Color.Black.copy(alpha = .3f), Offset(0f, 1f), 3f))

/**
 * The cover screen's status across the top: the battery, Wi-Fi and signal ring with the battery
 * level in the left corner, the date and time in the right one.
 */
@Composable
fun CoverStatusBar(status: DeviceStatus, modifier: Modifier = Modifier, ringSize: Dp = 28.dp) {
    val now = currentMinute()
    val formats = rememberStatusFormats()
    val description = statusDescription(status, now, formats)
    Row(modifier.testTag("cover-status").semantics(mergeDescendants = true) { contentDescription = description },
        verticalAlignment = Alignment.CenterVertically) {
        StatusRing(status, Modifier.size(ringSize))
        Spacer(Modifier.width(6.dp))
        Text(if (airplaneOutside(status, ringCentre(status))) stringResource(R.string.status_airplane_short) else status.battery?.let { "$it%${if (status.charging) " +" else ""}" } ?: "—",
            color = Color.White.copy(alpha = .94f), fontSize = 13.sp, fontWeight = FontWeight.Medium,
            maxLines = 1, softWrap = false, style = statusTextStyle)
        Spacer(Modifier.weight(1f))
        Text(now.format(formats.date), color = Color.White.copy(alpha = .94f), fontSize = 13.sp, fontWeight = FontWeight.Medium,
            maxLines = 1, softWrap = false, style = statusTextStyle)
        Spacer(Modifier.width(8.dp))
        Text(now.format(formats.time), color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold,
            maxLines = 1, softWrap = false, style = statusTextStyle)
    }
}

@Composable
fun StatusRail(
    status: DeviceStatus,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    iconSize: Dp = 40.dp,
    locationInUse: Boolean = false,
) {
    val now = currentMinute()
    val formats = rememberStatusFormats()
    val timeFormatter = formats.time
    val dateFormatter = formats.date
    val description = statusDescription(status, now, formats, locationInUse)
    val fontScale = LocalDensity.current.fontScale
    val labelStyle = statusTextStyle
    BoxWithConstraints(modifier.testTag("status-rail").semantics(mergeDescendants = true) { contentDescription = description }) {
        val availableWidth = (maxWidth - 4.dp).coerceAtLeast(28.dp)
        val visualSize = minOf(iconSize, availableWidth, if (compact) 44.dp else 56.dp)
        val timeSize = minOf(18f, availableWidth.value / (2.65f * fontScale)).sp
        val detailSize = minOf(11f, availableWidth.value / (3.45f * fontScale)).sp
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(3.dp)) {
            // Compact windows omit the reserve so status stays clear of the fixed dock.
            if (!compact) Box(Modifier.fillMaxWidth().height(20.dp), contentAlignment = Alignment.Center) {
                // Callers currently leave this false; the slot waits for a truthful activity signal.
                if (locationInUse) Icon(Icons.Rounded.LocationOn, null, tint = Color.White,
                    modifier = Modifier.size(18.dp))
            }
            Text(now.format(timeFormatter), color = Color.White, fontSize = timeSize, fontWeight = FontWeight.Bold,
                maxLines = 1, softWrap = false, overflow = TextOverflow.Clip, style = labelStyle)
            if (!compact) Text(now.format(dateFormatter), color = Color.White.copy(alpha = .94f), fontSize = detailSize,
                fontWeight = FontWeight.Medium, maxLines = 1, softWrap = false, overflow = TextOverflow.Clip, style = labelStyle)
            // Above the dock, the battery level sits in the ring's top opening.
            StatusRing(status, Modifier.padding(top = 4.dp).size(visualSize), percent = true)
        }
    }
}

/** Where the battery ring opens at the top, for the battery level, in degrees. */
private const val RING_TOP_GAP = 54f
/** Where the ring opens at the bottom, for the signal dots or mobile data's generation, in degrees. */
private const val RING_BOTTOM_GAP = 110f

/**
 * Battery as a ring open at the bottom, filling clockwise from its lower left end, green while
 * charging and yellow in battery saver. With [percent] the battery level cuts its own opening out
 * of the top; the ring fills as if it were whole, so a level near half can end inside the opening.
 *
 * On Wi-Fi the middle shows Wi-Fi and the bottom opening has four dots of cellular signal. On
 * mobile data the middle shows four signal bars and the opening names the generation. Otherwise
 * the middle shows airplane mode or, with neither, a cell tower, over the dots. With two SIMs the
 * first SIM keeps the dots or bars; the second's smaller dots sit under the symbol, which moves up
 * to make room.
 */
@Composable
private fun StatusRing(status: DeviceStatus, modifier: Modifier, percent: Boolean = false) {
    val wifiVisual = wifiSignalVisual(status.wifiConnected, status.wifiLevel)
    val cellularVisual = cellularSignalVisual(status.cellularLevel, status.airplane)
    val measurer = rememberTextMeasurer()
    val centre = ringCentre(status)
    val cellTower = androidx.compose.ui.graphics.vector.rememberVectorPainter(CellTowerIcon)
    val battery = status.battery?.toString() ?: "—"
    val level = if (airplaneOutside(status, centre)) AIRPLANE_GLYPH else battery
    Canvas(modifier) {
        val w = size.width
        val center = Offset(w / 2, w / 2)
        val ringWidth = w * .075f
        val radius = w / 2 - ringWidth / 2 - w * .02f
        val arcSize = Size(radius * 2, radius * 2)
        val topLeft = Offset(center.x - radius, center.y - radius)
        fun label(value: String, fontSize: Float, y: Float) {
            val text = measurer.measure(value, TextStyle(color = Color.White, fontSize = fontSize.toSp(),
                fontWeight = FontWeight.Bold, shadow = Shadow(Color.Black.copy(alpha = .3f), Offset(0f, 1f), 3f)))
            drawText(text, topLeft = Offset(center.x - text.size.width / 2f, y - text.size.height / 2f))
        }
        // Canvas angles run clockwise from three o'clock; the ring runs from the bottom opening's
        // left edge, up and over the top, down to the bottom opening's right edge.
        val start = 90f + RING_BOTTOM_GAP / 2
        val sweep = 360f - RING_BOTTOM_GAP
        val ring: DrawScope.() -> Unit = {
            drawArc(Color.Black.copy(alpha = .14f), start, sweep, false, topLeft, arcSize,
                style = Stroke(width = ringWidth * 1.2f, cap = StrokeCap.Round))
            val stroke = Stroke(width = ringWidth, cap = StrokeCap.Round)
            drawArc(Color.White.copy(alpha = .28f), start, sweep, false, topLeft, arcSize, style = stroke)
            status.battery?.let { battery ->
                val color = if (status.charging) Color(0xFF34C85B) else if (status.powerSave) Color(0xFFFECC09) else Color.White
                drawArc(color, start, sweep * battery.coerceIn(0, 100) / 100f, false, topLeft, arcSize, style = stroke)
            }
        }
        if (percent) {
            // The battery level's opening has square ends, along the ring's radii.
            val half = Math.toRadians(RING_TOP_GAP / 2.0)
            val opening = Path().apply {
                moveTo(center.x, center.y)
                lineTo(center.x - w * sin(half).toFloat(), center.y - w * cos(half).toFloat())
                lineTo(center.x + w * sin(half).toFloat(), center.y - w * cos(half).toFloat())
                close()
            }
            clipPath(opening, ClipOp.Difference) { ring() }
            // A full battery's three digits are smaller, to stay inside the opening.
            label(level, w * if (level.length > 2) .2f else .26f, center.y - radius + w * .037f)
        } else ring()
        val dualSim = status.simLevels.size >= 2 && !status.airplane
        // With two SIMs the middle's symbol rises to leave room for the second SIM's dots below it.
        val lift = if (dualSim) w * DUAL_SIM_LIFT else 0f
        val secondLevel = if (dualSim) status.simLevels[1]?.coerceIn(0, 4) ?: 0 else 0
        val firstLevel = if (dualSim) status.simLevels[0]?.coerceIn(0, 4) ?: 0
            else (cellularVisual as? CellularSignalVisual.Available)?.activeDots ?: 0
        // Four dots along a curve, lit left to right.
        fun signalDots(lit: Int, along: Float, dot: Float, dim: Float = .3f) {
            for (i in 0..3) {
                val angle = Math.toRadians((90.0 + RING_BOTTOM_GAP / 2 * DOTS_SPREAD) - i * (RING_BOTTOM_GAP * DOTS_SPREAD / 3))
                val dotCenter = Offset(center.x + along * cos(angle).toFloat(), center.y + along * sin(angle).toFloat())
                drawCircle(Color.White.copy(alpha = if (i < lit) 1f else dim), dot, dotCenter)
            }
        }
        when (centre) {
            RingCentre.WIFI -> if (wifiVisual is WifiSignalVisual.Connected) {
                // Two rounded arcs over a fan, all spreading from one point low in the ring.
                val base = Offset(center.x, center.y + w * .198f - lift)
                listOf(.194f, .31f).forEachIndexed { index, share ->
                    val r = w * share
                    drawArc(Color.White.copy(alpha = signalAlpha(wifiVisual.elements[index + 1])), 225f, 90f, false,
                        Offset(base.x - r, base.y - r), Size(r * 2, r * 2), style = Stroke(w * .068f, cap = StrokeCap.Round))
                }
                drawPath(wifiFan(base, w * .11f), Color.White.copy(alpha = signalAlpha(wifiVisual.elements[0])))
            }
            RingCentre.CELLULAR -> {
                // Four rising bars, lit left to right, with the second SIM's dots under them.
                val bottom = center.y + w * .109f - lift
                val barWidth = w * .063f
                BAR_HEIGHTS.forEachIndexed { index, share ->
                    val x = center.x + (index - 1.5f) * w * BAR_STEP
                    drawRoundRect(Color.White.copy(alpha = if (index < firstLevel) 1f else .3f),
                        Offset(x - barWidth / 2, bottom - w * share), Size(barWidth, w * share),
                        CornerRadius(barWidth / 2))
                    if (dualSim) drawCircle(Color.White.copy(alpha = if (index < secondLevel) 1f else SECOND_SIM_DIM), w * SECOND_SIM_DOT,
                        Offset(x, bottom + w * .09f))
                }
            }
            RingCentre.SIGNAL -> {
                // A cell tower, faint while there is no mobile signal at all.
                val side = w * if (dualSim) .36f else .44f
                translate(center.x - side / 2, center.y + w * .02f - lift - side / 2) {
                    with(cellTower) {
                        draw(Size(side, side), alpha = if (cellularVisual is CellularSignalVisual.Available) 1f else .45f,
                            colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(Color.White))
                    }
                }
            }
            RingCentre.AIRPLANE -> label(AIRPLANE_GLYPH, w * .27f, center.y + w * .03f)
        }
        val generation = status.cellularNetwork.takeIf { centre == RingCentre.CELLULAR }
        if (generation != null) label(generation, w * if (generation.length > 2) .21f else .254f, center.y + w * .35f)
        else {
            signalDots(firstLevel, radius * 1.05f, w * SIM_DOT)
            if (dualSim) signalDots(secondLevel, radius * SECOND_SIM_CURVE, w * SECOND_SIM_DOT, SECOND_SIM_DIM)
        }
    }
}

/** The bottom of the Wi-Fi symbol: a fan of 90 degrees with softened corners, spreading up from [tip]. */
private fun wifiFan(tip: Offset, r: Float): Path {
    val corner = r * .7071f
    val left = Offset(tip.x - corner, tip.y - corner)
    val right = Offset(tip.x + corner, tip.y - corner)
    fun along(to: Offset, share: Float) = Offset(tip.x + (to.x - tip.x) * share, tip.y + (to.y - tip.y) * share)
    return Path().apply {
        val start = along(left, .22f)
        val nearLeft = along(left, .78f)
        val nearRight = along(right, .78f)
        val end = along(right, .22f)
        moveTo(start.x, start.y)
        lineTo(nearLeft.x, nearLeft.y)
        quadraticTo(left.x, left.y, tip.x - corner * .62f, tip.y - r * .9f)
        quadraticTo(tip.x, tip.y - r * 1.08f, tip.x + corner * .62f, tip.y - r * .9f)
        quadraticTo(right.x, right.y, nearRight.x, nearRight.y)
        lineTo(end.x, end.y)
        quadraticTo(tip.x, tip.y, start.x, start.y)
        close()
    }
}

private const val AIRPLANE_GLYPH = "✈"

/** Material's cell tower symbol, which the bundled icon set predates. */
private val CellTowerIcon: androidx.compose.ui.graphics.vector.ImageVector by lazy {
    androidx.compose.ui.graphics.vector.ImageVector.Builder("CellTower", 24.dp, 24.dp, 24f, 24f).addPath(
        androidx.compose.ui.graphics.vector.addPathNodes("M7.3,14.7l1.2,-1.2c-1,-1 -1,-2.5 0,-3.5L7.3,8.8c-1.6,1.6 -1.6,4.3 0,5.9z" +
            "M6.1,6.1L4.9,4.9c-3.9,3.9 -3.9,10.2 0,14.1l1.2,-1.2c-3.2,-3.2 -3.2,-8.4 0,-11.7z" +
            "M19.1,4.9l-1.2,1.2c3.2,3.2 3.2,8.5 0,11.7l1.2,1.2c3.9,-3.9 3.9,-10.2 0,-14.1z" +
            "M16.7,8.5l-1.2,1.2c1,1 1,2.5 0,3.5l1.2,1.2c1.6,-1.6 1.6,-4.3 0,-5.9z" +
            "M14.5,12c0,-1.38 -1.12,-2.5 -2.5,-2.5S9.5,10.62 9.5,12c0,0.76 0.34,1.42 0.87,1.88L7,22h2l0.67,-2h4.67L15,22h2" +
            "l-3.37,-8.12c0.53,-0.46 0.87,-1.12 0.87,-1.88zM10.33,18L12,13l1.67,5h-3.34z"),
        fill = androidx.compose.ui.graphics.SolidColor(Color.White)).build()
}
/** Signal dot radius, as a share of the ring's width: the first SIM's, and the smaller second SIM's. */
private const val SIM_DOT = .047f
private const val SECOND_SIM_DOT = .032f
/** The second SIM's unlit dots are stronger, so all four stay visible at their smaller size. */
private const val SECOND_SIM_DIM = .55f
/** How much of the bottom opening the dots span. */
private const val DOTS_SPREAD = .56
/** The signal bars' heights and spacing, as shares of the ring's width. */
private val BAR_HEIGHTS = listOf(.14f, .18f, .215f, .25f)
private const val BAR_STEP = .103f
/** The second SIM's curve, as a share of the ring's radius. */
private const val SECOND_SIM_CURVE = .66f
/** How far the middle's symbol rises with two SIMs, as a share of the ring's width. */
private const val DUAL_SIM_LIFT = .07f

private fun signalAlpha(emphasis: SignalElementEmphasis): Float = when (emphasis) {
    SignalElementEmphasis.DIM -> .3f
    SignalElementEmphasis.NEUTRAL -> .62f
    SignalElementEmphasis.LIT -> 1f
}
