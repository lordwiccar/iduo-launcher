package media.whitewhale.iduo

import org.junit.Assert.*
import org.junit.Test

class DiscoverPositionTest {
    @Test fun bothViewportSizesReachTheExactEndpointsWithoutAClosingJump() {
        for (width in listOf(996f, 2196f)) {
            val feed = width - 126f
            assertEquals(0f, discoverPageProgress(0f, width, feed), 0f)
            assertEquals(1f, discoverPageProgress(1f, width, feed), 0f)
            assertTrue(discoverPageProgress(.0001f, width, feed) < .001f)
            var previous = 0f
            for (step in 1..1000) {
                val next = discoverPageProgress(step / 1000f, width, feed)
                assertTrue(next > previous)
                assertTrue(next - previous < .003f)
                previous = next
            }
        }
    }
    @Test fun switchingGestureOwnersPreservesPosition() {
        for (width in listOf(996f, 2196f)) for (step in 0..100) {
            val page = step / 100f
            assertEquals(page, discoverPageProgress(discoverNativeProgress(page, width, width - 126f), width, width - 126f), .00003f)
        }
    }
}
