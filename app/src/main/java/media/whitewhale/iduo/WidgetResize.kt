package media.whitewhale.iduo

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

private val HANDLE_TARGET = 48.dp
private val HANDLE_DOT = 20.dp

/**
 * Resizes a widget on Home: a frame with a handle on each corner. Each drag takes effect when the
 * finger lifts, and a size that cannot be had springs back. [bounds] are the widget's bounds now,
 * [pitchX] the distance between columns and [rowLine] the distance of a row's top from the page's.
 */
@Composable
internal fun WidgetResizeOverlay(placement: WidgetPlacement, bounds: Rect, columns: Int, rows: Int, pitchX: Float,
    rowLine: (Int) -> Float, canApply: (WidgetFootprint) -> Boolean, onApply: (WidgetFootprint) -> Unit,
    onOptions: () -> Unit, onDone: () -> Unit) {
    val current = WidgetFootprint(placement.column, placement.row, placement.spanX, placement.spanY)
    var draft by remember(current) { mutableStateOf(current) }
    val valid = draft == current || canApply(draft)
    val color = if (valid) Color.White else Color(0xFFFF6B6B)
    val density = LocalDensity.current
    // [bounds] are in the window's coordinates, and this overlay starts below the camera's band.
    var origin by remember { mutableStateOf(Offset.Zero) }
    val left = bounds.left - origin.x + (draft.column - current.column) * pitchX
    val right = bounds.right - origin.x + (draft.column + draft.spanX - current.column - current.spanX) * pitchX
    val top = bounds.top - origin.y + rowLine(draft.row) - rowLine(current.row)
    val bottom = bounds.bottom - origin.y + rowLine(draft.row + draft.spanY) - rowLine(current.row + current.spanY)
    val latest by rememberUpdatedState(current)
    val latestCanApply by rememberUpdatedState(canApply)
    val latestApply by rememberUpdatedState(onApply)
    val latestRowLine by rememberUpdatedState(rowLine)
    Box(Modifier.fillMaxSize().testTag("widget-resize-mode").onGloballyPositioned { origin = it.positionInRoot() }.pointerInput(Unit) { detectTapGestures { onDone() } }) {
        Box(Modifier.offset { IntOffset(left.roundToInt(), top.roundToInt()) }
            .size(with(density) { (right - left).toDp() }, with(density) { (bottom - top).toDp() })
            .border(3.dp, color, RoundedCornerShape(24.dp)).testTag("widget-resize-preview-${placement.slot}")) {
            val handleDescription = stringResource(R.string.drag_to_resize)
            ResizeCorner.entries.forEach { corner ->
                val alignment = when {
                    corner.left && corner.top -> Alignment.TopStart
                    corner.top -> Alignment.TopEnd
                    corner.left -> Alignment.BottomStart
                    else -> Alignment.BottomEnd
                }
                val shift = HANDLE_TARGET / 2
                Box(Modifier.align(alignment).offset(if (corner.left) -shift else shift, if (corner.top) -shift else shift)
                    .size(HANDLE_TARGET).testTag("widget-resize-handle-${placement.slot}-${corner.name.lowercase()}")
                    .semantics { contentDescription = handleDescription }
                    .pointerInput(corner) {
                        var dx = 0f; var dy = 0f
                        var start = latest
                        detectDragGestures(onDragStart = { dx = 0f; dy = 0f; start = latest },
                            onDrag = { change, amount ->
                                change.consume(); dx += amount.x; dy += amount.y
                                draft = draggedFootprint(start, corner, dx, dy, pitchX, latestRowLine, columns, rows)
                            },
                            onDragEnd = { if (draft != start && latestCanApply(draft)) latestApply(draft) else draft = start },
                            onDragCancel = { draft = start })
                    }, contentAlignment = Alignment.Center) {
                    Box(Modifier.size(HANDLE_DOT).background(color, CircleShape).border(1.dp, Color.Black.copy(alpha = .25f), CircleShape))
                }
            }
        }
        Row(Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = 8.dp)
            .background(Glass.copy(alpha = .97f), RoundedCornerShape(22.dp))
            .pointerInput(Unit) { detectTapGestures { } }.testTag("widget-resize-toolbar"),
            verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onOptions, Modifier.testTag("widget-resize-options")) { Text(stringResource(R.string.widget_options)) }
            Text("${draft.spanX} × ${draft.spanY}", color = Ink)
            TextButton(onClick = onDone, Modifier.testTag("widget-resize-done")) { Text(stringResource(R.string.done)) }
        }
    }
}
