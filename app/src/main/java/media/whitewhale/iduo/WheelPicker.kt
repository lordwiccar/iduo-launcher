package media.whitewhale.iduo

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import kotlin.math.abs

private val WheelItemHeight = 48.dp
private const val WHEEL_VISIBLE_ITEMS = 5

/**
 * A vertical, snapping wheel like a clock's time picker. The value in the middle band is the
 * selection; [onSelected] reports its index whenever the wheel settles on a new one. Items that
 * are not [enabled] stay visible but dimmed.
 */
@Composable
internal fun WheelPicker(
    labels: List<String>, initial: Int, onSelected: (Int) -> Unit, modifier: Modifier = Modifier,
    enabled: (Int) -> Boolean = { true },
) {
    val state = rememberLazyListState(initialFirstVisibleItemIndex = initial.coerceIn(0, (labels.size - 1).coerceAtLeast(0)))
    val fling = rememberSnapFlingBehavior(lazyListState = state)
    val scope = rememberCoroutineScope()
    val itemPx = with(LocalDensity.current) { WheelItemHeight.toPx() }
    // With two items of padding above, item i sits in the middle band when it is the first visible one.
    val selected by remember {
        derivedStateOf {
            (state.firstVisibleItemIndex + if (state.firstVisibleItemScrollOffset > itemPx / 2) 1 else 0)
                .coerceIn(0, (labels.size - 1).coerceAtLeast(0))
        }
    }
    LaunchedEffect(selected) { onSelected(selected) }
    val padding = WheelItemHeight * (WHEEL_VISIBLE_ITEMS / 2)
    Box(modifier.fillMaxWidth().height(WheelItemHeight * WHEEL_VISIBLE_ITEMS), contentAlignment = Alignment.Center) {
        Box(Modifier.fillMaxWidth().height(WheelItemHeight)
            .background(MaterialTheme.colorScheme.secondaryContainer, RoundedCornerShape(14.dp)))
        LazyColumn(Modifier.fillMaxWidth().height(WheelItemHeight * WHEEL_VISIBLE_ITEMS).testTag("wheel-picker"),
            state = state, flingBehavior = fling, contentPadding = PaddingValues(vertical = padding),
            horizontalAlignment = Alignment.CenterHorizontally) {
            itemsIndexed(labels) { index, label ->
                val distance = abs(index - selected)
                Box(Modifier.fillMaxWidth().height(WheelItemHeight)
                    .clickable { scope.launch { state.animateScrollToItem(index) } }
                    .semantics { this.selected = index == selected }
                    .testTag("wheel-item-$index"), contentAlignment = Alignment.Center) {
                    Text(label, Modifier.alpha((if (distance == 0) 1f else if (distance == 1) .55f else .3f) *
                        if (enabled(index)) 1f else .45f),
                        fontSize = if (distance == 0) 22.sp else 18.sp,
                        fontWeight = if (distance == 0) FontWeight.SemiBold else FontWeight.Normal)
                }
            }
        }
    }
}
