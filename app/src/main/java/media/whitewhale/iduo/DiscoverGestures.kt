package media.whitewhale.iduo

import kotlin.math.abs

/** Navigation on Duo's own frame/recovery surface. Google handles gestures inside its window. */
internal class DiscoverHomeSwipe(private val threshold: Float, private val slop: Float) {
    private var startX = 0f
    private var startY = 0f
    private var tracking = false

    fun down(x: Float, y: Float) { startX = x; startY = y; tracking = true }
    fun cancel() { tracking = false }
    fun move(x: Float, y: Float): Boolean {
        if (!tracking) return false
        val left = startX - x
        val vertical = abs(y - startY)
        // Once a vertical scroll or rightward gesture wins, it cannot become page navigation.
        if ((vertical > slop && vertical > abs(left)) || left < -slop) { cancel(); return false }
        if (left >= threshold && left > vertical * 1.5f) { cancel(); return true }
        return false
    }
}

/** Google's scroll callback describes the whole feed moving, not a carousel within an article.
 * Commit Home after a deliberate 72dp drag, instead of exposing the loading surface while
 * waiting for Google to move its entire window offscreen. Re-arm only after a visible feed.
 */
internal class DiscoverDismissal {
    private var visible = false
    val tracking get() = visible
    fun suspend() { visible = false }
    fun progress(value: Float, widthDp: Float): Boolean {
        if (!value.isFinite() || value !in 0f..1f) return false
        if (value >= .99f) { visible = true; return false }
        if (!visible) return false
        val distance = (1f - value) * widthDp.coerceAtLeast(1f)
        if (distance < minOf(72f, widthDp * .25f)) return false
        visible = false
        return true
    }
}
