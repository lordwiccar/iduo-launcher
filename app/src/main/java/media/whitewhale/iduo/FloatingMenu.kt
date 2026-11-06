package media.whitewhale.iduo

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

private val MENU_WIDTH = 264.dp
private val MENU_MARGIN = 8.dp

/**
 * Where a menu of [menuWidth] x [menuHeight] goes in a space of [width] x [height]: above [anchor] if
 * it fits there, else below, and always on screen; in the middle without an anchor.
 */
internal fun floatingMenuPosition(anchor: IntRect?, width: Int, height: Int, menuWidth: Int, menuHeight: Int, margin: Int): IntOffset {
    fun fit(value: Int, size: Int, room: Int) = value.coerceIn(margin, maxOf(margin, room - size - margin))
    if (anchor == null) return IntOffset(fit((width - menuWidth) / 2, menuWidth, width), fit((height - menuHeight) / 2, menuHeight, height))
    val above = anchor.top - margin - menuHeight
    val y = if (above >= margin) above else fit(anchor.bottom + margin, menuHeight, height)
    return IntOffset(fit(anchor.center.x - menuWidth / 2, menuWidth, width), y)
}

/**
 * A small menu floating over Home beside [anchor], given in the window's coordinates. It is drawn in
 * Home's own window: a window of its own would bring back Android's bars and shift Home under it. A
 * tap outside or Back closes it.
 */
@Composable
internal fun FloatingMenu(anchor: Rect?, title: String?, tag: String, onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    BackHandler(onBack = onDismiss)
    val dismiss by rememberUpdatedState(onDismiss)
    var origin by remember { mutableStateOf<Offset?>(null) }
    // Where it first came to rest: a row unfolding inside must not send it away from under the finger.
    var resting by remember { mutableStateOf<IntOffset?>(null) }
    val shown = remember { MutableTransitionState(false).apply { targetState = true } }
    Layout(content = {
        AnimatedVisibility(shown, enter = fadeIn(tween(120)) + scaleIn(tween(120), initialScale = .94f)) {
            Surface(Modifier.width(MENU_WIDTH).testTag(tag).pointerInput(Unit) { detectTapGestures { } }, shape = RoundedCornerShape(22.dp),
                color = MaterialTheme.colorScheme.surface, contentColor = MaterialTheme.colorScheme.onSurface, shadowElevation = 8.dp) {
                Column(Modifier.verticalScroll(rememberScrollState()).padding(vertical = 8.dp)) {
                    title?.let {
                        Text(it, Modifier.padding(horizontal = 16.dp, vertical = 6.dp), style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    content()
                }
            }
        }
    }, modifier = Modifier.fillMaxSize().onGloballyPositioned { origin = it.positionInRoot() }
        .pointerInput(Unit) { detectTapGestures { dismiss() } }) { measurables, constraints ->
        val margin = MENU_MARGIN.roundToPx()
        val menu = measurables.first().measure(constraints.copy(minWidth = 0, minHeight = 0, maxHeight = maxOf(0, constraints.maxHeight - 2 * margin)))
        val from = origin ?: Offset.Zero
        val place = anchor?.let { IntRect((it.left - from.x).roundToInt(), (it.top - from.y).roundToInt(),
            (it.right - from.x).roundToInt(), (it.bottom - from.y).roundToInt()) }
        val position = resting?.let { IntOffset(it.x, it.y.coerceAtMost(maxOf(margin, constraints.maxHeight - menu.height - margin))) }
            ?: floatingMenuPosition(place, constraints.maxWidth, constraints.maxHeight, menu.width, menu.height, margin)
                .also { if (origin != null) resting = it }
        layout(constraints.maxWidth, constraints.maxHeight) { menu.place(position) }
    }
}

/** A row of a [FloatingMenu]: an icon, a label, and whatever [trailing] adds at its end. */
@Composable
internal fun MenuRow(icon: ImageVector, label: String, onClick: () -> Unit, modifier: Modifier = Modifier,
    tint: Color = MaterialTheme.colorScheme.onSurfaceVariant, trailing: @Composable RowScope.() -> Unit = {}) {
    val danger = tint == MaterialTheme.colorScheme.error
    Row(modifier.fillMaxWidth().heightIn(min = 46.dp).clickable(onClick = onClick).padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, Modifier.size(20.dp), tint = tint)
        Spacer(Modifier.width(14.dp))
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium,
            color = if (danger) tint else MaterialTheme.colorScheme.onSurface)
        trailing()
    }
}
