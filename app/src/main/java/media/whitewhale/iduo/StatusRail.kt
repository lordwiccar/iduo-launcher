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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
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
        Text(if (airplaneOutside(status, ringCentre(status, percent = false))) stringResource(R.string.status_airplane_short) else status.battery?.let { "$it%${if (status.charging) " +" else ""}" } ?: "—",
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
            // Above the dock, the battery level sits in the ring's top opening; a second SIM's
            // signal takes a row of dots below the ring.
            StatusRing(status, Modifier.padding(top = 4.dp).width(visualSize)
                .height(if (status.simLevels.size >= 2 && !status.airplane) visualSize * 1.13f else visualSize), percent = true)
        }
    }
}

/** Where the battery ring opens at the top, for the battery level, in degrees. */
private const val RING_TOP_GAP = 84f
/** Where the ring opens at the bottom, for the signal dots, in degrees. */
private const val RING_BOTTOM_GAP = 112f

/**
 * Battery as a thick ring open at the top and bottom, filling clockwise from its lower left end,
 * green while charging and yellow in battery saver; cellular signal as four dots across the bottom
 * opening. Inside it Wi-Fi, or without Wi-Fi mobile data's generation or airplane mode. With
 * [percent], the battery level sits in the top opening, or in the middle when nothing else is there,
 * and a second SIM's signal is a second row of dots below the ring, in either place.
 */
@Composable
private fun StatusRing(status: DeviceStatus, modifier: Modifier, percent: Boolean = false) {
    val wifiVisual = wifiSignalVisual(status.wifiConnected, status.wifiLevel)
    val cellularVisual = cellularSignalVisual(status.cellularLevel, status.airplane)
    val measurer = rememberTextMeasurer()
    val centre = ringCentre(status, percent)
    val battery = status.battery?.toString() ?: "—"
    val level = if (airplaneOutside(status, centre)) AIRPLANE_GLYPH else battery
    val centreText = when (centre) {
        RingCentre.CELLULAR -> status.cellularNetwork
        RingCentre.AIRPLANE -> AIRPLANE_GLYPH
        RingCentre.BATTERY -> battery
        else -> null
    }
    Canvas(modifier) {
        val w = size.width
        val center = Offset(w / 2, w / 2)
        val ringWidth = w * .085f
        val radius = w / 2 - ringWidth / 2 - w * .02f
        val arcSize = Size(radius * 2, radius * 2)
        val topLeft = Offset(center.x - radius, center.y - radius)
        // Canvas angles run clockwise from three o'clock; the ring runs from the bottom opening's
        // left edge, up and over the top opening, down to the bottom opening's right edge.
        // Without the battery level in it, the ring closes at the top.
        val topGap = if (percent && centre != RingCentre.BATTERY) RING_TOP_GAP else 0f
        val bottomGap = RING_BOTTOM_GAP
        val start = 90f + bottomGap / 2
        val leftSweep = 270f - topGap / 2 - start
        val rightStart = 270f + topGap / 2
        val rightSweep = 90f - bottomGap / 2 + 360f - rightStart
        val total = leftSweep + rightSweep
        val track = Color.White.copy(alpha = .28f)
        val shadow = Stroke(width = ringWidth * 1.2f, cap = StrokeCap.Round)
        val stroke = Stroke(width = ringWidth, cap = StrokeCap.Round)
        drawArc(Color.Black.copy(alpha = .14f), start, leftSweep, false, topLeft, arcSize, style = shadow)
        drawArc(Color.Black.copy(alpha = .14f), rightStart, rightSweep, false, topLeft, arcSize, style = shadow)
        drawArc(track, start, leftSweep, false, topLeft, arcSize, style = stroke)
        drawArc(track, rightStart, rightSweep, false, topLeft, arcSize, style = stroke)
        status.battery?.let { battery ->
            val filled = total * battery.coerceIn(0, 100) / 100f
            val color = if (status.charging) Color(0xFF34C85B) else if (status.powerSave) Color(0xFFFECC09) else Color.White
            drawArc(color, start, minOf(filled, leftSweep), false, topLeft, arcSize, style = stroke)
            if (filled > leftSweep) drawArc(color, rightStart, filled - leftSweep, false, topLeft, arcSize, style = stroke)
        }
        if (percent && centre != RingCentre.BATTERY) {
            val text = measurer.measure(level, TextStyle(color = Color.White, fontSize = (w * .2f).toSp(),
                fontWeight = FontWeight.Bold, shadow = Shadow(Color.Black.copy(alpha = .3f), Offset(0f, 1f), 3f)))
            drawText(text, topLeft = Offset(center.x - text.size.width / 2f, center.y - radius - text.size.height / 2f))
        }
        // Wi-Fi: three rounded arcs over a dot, centred a little low in the ring.
        val wifiBase = Offset(center.x, w * .6f)
        if (centre == RingCentre.WIFI && wifiVisual is WifiSignalVisual.Connected) {
            val arcStroke = w * .068f
            for (i in 1..3) {
                val r = w * (.04f + i * .08f)
                drawArc(Color.White.copy(alpha = signalAlpha(wifiVisual.elements[i])), 225f, 90f, false,
                    Offset(wifiBase.x - r, wifiBase.y - r), Size(r * 2, r * 2), style = Stroke(arcStroke, cap = StrokeCap.Round))
            }
            drawCircle(Color.White.copy(alpha = signalAlpha(wifiVisual.elements[0])), w * .052f, wifiBase)
        } else if (centreText != null) {
            // Text in the middle, a little low like the Wi-Fi symbol; longer labels get smaller.
            val text = measurer.measure(centreText, TextStyle(color = Color.White,
                fontSize = (w * if (centreText.length > 2) .22f else .27f).toSp(), fontWeight = FontWeight.Bold,
                shadow = Shadow(Color.Black.copy(alpha = .3f), Offset(0f, 1f), 3f)))
            drawText(text, topLeft = Offset(center.x - text.size.width / 2f, center.y + w * .03f - text.size.height / 2f))
        }
        // Cellular signal: four dots across the bottom opening, lit left to right; with two SIMs the
        // first SIM's, and the second's in the same curve just below.
        val dotRadius = w * .042f
        fun signalDots(lit: Int, below: Float) {
            for (i in 0..3) {
                // The dots stay clear of the ring's rounded ends.
                val angle = Math.toRadians((90.0 + RING_BOTTOM_GAP / 2 * .62) - i * (RING_BOTTOM_GAP * .62 / 3))
                val dotCenter = Offset(center.x + radius * cos(angle).toFloat(), center.y + below + radius * sin(angle).toFloat())
                drawCircle(Color.White.copy(alpha = if (i < lit) 1f else .3f), dotRadius, dotCenter)
            }
        }
        // In the cover's top corner the second row hangs just below the ring, outside its measured size.
        val dualSim = status.simLevels.size >= 2 && !status.airplane
        val firstLevel = if (dualSim) status.simLevels[0]?.coerceIn(0, 4) ?: 0
            else (cellularVisual as? CellularSignalVisual.Available)?.activeDots ?: 0
        signalDots(firstLevel, 0f)
        if (dualSim) signalDots(status.simLevels[1]?.coerceIn(0, 4) ?: 0, w * .16f)
    }
}

private const val AIRPLANE_GLYPH = "✈"

private fun signalAlpha(emphasis: SignalElementEmphasis): Float = when (emphasis) {
    SignalElementEmphasis.DIM -> .3f
    SignalElementEmphasis.NEUTRAL -> .62f
    SignalElementEmphasis.LIT -> 1f
}
