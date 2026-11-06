package media.whitewhale.iduo

import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import org.junit.Assert.assertEquals
import org.junit.Test

class FloatingMenuTest {
    private fun position(anchor: IntRect?) = floatingMenuPosition(anchor, 1000, 2000, 400, 300, 20)

    @Test fun restsAboveItsAnchorWhenItFits() {
        assertEquals(IntOffset(300, 680), position(IntRect(400, 1000, 600, 1200)))
    }

    @Test fun goesBelowAnAnchorNearTheTop() {
        assertEquals(IntOffset(300, 320), position(IntRect(400, 100, 600, 300)))
    }

    @Test fun staysOnScreenBesideTheEdges() {
        assertEquals(20, position(IntRect(0, 1000, 10, 1010)).x)
        assertEquals(580, position(IntRect(990, 1000, 1000, 1010)).x)
    }

    @Test fun anAnchorFillingTheScreenStillLeavesTheMenuOnIt() {
        assertEquals(1680, position(IntRect(0, 100, 1000, 1990)).y)
    }

    @Test fun withoutAnAnchorItIsInTheMiddle() {
        assertEquals(IntOffset(300, 850), position(null))
    }
}
