package media.whitewhale.iduo

internal data class DiscoverScrollRequest(val position: Float, val scrolling: Boolean)

/** Ordinary Home/All apps paging must never open Google's invisible input window. */
internal class DiscoverPageDriver {
    private var last = DiscoverScrollRequest(0f, false)
    fun request(position: Float, scrolling: Boolean, towardFeed: Boolean = false): DiscoverScrollRequest? {
        // An unrelated page gesture must send neither startScroll nor closeOverlay.
        // In particular, closing during the first frame of an opening animation can
        // race the native start. The destination update identifies that opening.
        if (scrolling && position == 0f && !towardFeed && last.position == 0f && !last.scrolling) return null
        val next = DiscoverScrollRequest(position, scrolling)
        last = next
        return next
    }
}
