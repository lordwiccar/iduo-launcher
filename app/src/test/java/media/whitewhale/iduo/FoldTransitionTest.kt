package media.whitewhale.iduo

import android.view.Surface
import org.junit.Assert.assertEquals
import org.junit.Test

class FoldTransitionTest {
    @Test
    fun innerScreenFrostsTowardHalfOpenAndClearsWhenFlat() {
        assertEquals(0f, foldFrostTarget(cover = false, angle = 180f), 0f)
        assertEquals(.5f, foldFrostTarget(cover = false, angle = 135f), 1e-6f)
        assertEquals(1f, foldFrostTarget(cover = false, angle = 90f), 0f)
        assertEquals(1f, foldFrostTarget(cover = false, angle = 0f), 0f)
    }

    @Test
    fun coverFrostsTowardHalfOpenAndClearsWhenShut() {
        assertEquals(0f, foldFrostTarget(cover = true, angle = 0f), 0f)
        assertEquals(.5f, foldFrostTarget(cover = true, angle = 45f), 1e-6f)
        assertEquals(1f, foldFrostTarget(cover = true, angle = 90f), 0f)
        assertEquals(1f, foldFrostTarget(cover = true, angle = 180f), 0f)
    }

    @Test
    fun unknownHingeLeavesHomeSharp() {
        assertEquals(0f, foldFrostTarget(cover = false, angle = null), 0f)
        assertEquals(0f, foldFrostTarget(cover = true, angle = null), 0f)
    }

    @Test
    fun freeEdgeFollowsTheScreenRotation() {
        // Upright, the inner screen's swinging half is on the left and the cover's free edge on the right.
        assertEquals(-1f to 0f, freeEdgeDirection(cover = false, Surface.ROTATION_0))
        assertEquals(1f to 0f, freeEdgeDirection(cover = true, Surface.ROTATION_0))
        // Turned a quarter, the natural left edge lies at the bottom of the screen.
        assertEquals(0f to 1f, freeEdgeDirection(cover = false, Surface.ROTATION_90))
        assertEquals(1f to 0f, freeEdgeDirection(cover = false, Surface.ROTATION_180))
        assertEquals(0f to -1f, freeEdgeDirection(cover = false, Surface.ROTATION_270))
    }
}
