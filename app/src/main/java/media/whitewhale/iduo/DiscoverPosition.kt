package media.whitewhale.iduo

/** The inset feed is narrower than a page. Match its travel through most of the drag,
 * then smoothly consume the remaining glass margin near Home instead of jumping at zero. */
internal fun discoverPageProgress(native: Float, pageWidth: Float, feedWidth: Float): Float {
    val edge = (native / .25f).coerceIn(0f, 1f)
    val inset = edge * edge * (3f - 2f * edge)
    return ((native * feedWidth + (pageWidth - feedWidth) * inset) / pageWidth.coerceAtLeast(1f)).coerceIn(0f, 1f)
}

internal fun discoverNativeProgress(page: Float, pageWidth: Float, feedWidth: Float): Float {
    if (page <= 0f) return 0f
    if (page >= 1f) return 1f
    var low = 0f; var high = 1f
    repeat(16) {
        val middle = (low + high) * .5f
        if (discoverPageProgress(middle, pageWidth, feedWidth) < page) low = middle else high = middle
    }
    return (low + high) * .5f
}
