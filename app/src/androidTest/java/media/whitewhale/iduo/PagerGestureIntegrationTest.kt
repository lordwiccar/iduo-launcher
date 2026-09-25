package media.whitewhale.iduo

import androidx.compose.ui.geometry.Offset
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.lifecycle.ViewModelProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class PagerGestureIntegrationTest {
    val compose = createAndroidComposeRule<MainActivity>()
    @get:Rule val rules = org.junit.rules.RuleChain.outerRule(WithoutNativeFeed()).around(compose)
    private fun pager() = compose.onNodeWithTag("app-pager")
    private fun ready() {
        check(android.os.Build.HARDWARE in listOf("ranchu", "goldfish"))
        compose.waitUntil(15000) { !ViewModelProvider(compose.activity)[LauncherModel::class.java].state.value.loading }
        // Starting at the library also works when the saved workspace has several home pages.
        compose.onNodeWithTag("library-page-link").performClick()
        compose.waitForIdle()
    }
    private fun page() = pager().fetchSemanticsNode().config[SemanticsProperties.StateDescription]
    private fun shortSwipe(right: Boolean, duration: Long, pause: Long = 0, fraction: Float = .26f,
        travelDp: Float? = null) {
        val density = compose.activity.resources.displayMetrics.density
        pager().performTouchInput {
            val start = Offset(width * if (right) .25f else .70f, height * .70f)
            val travel = Offset((travelDp?.times(density) ?: (width * fraction)) * if (right) 1f else -1f, height * .025f)
            down(start)
            // Several real movement samples, including a small diagonal, rather than a
            // single giant jump that always passes the half-page release threshold.
            for (step in 1..12) moveTo(start + travel * (step / 12f), duration / 12)
            if (pause > 0) { advanceEventTime(pause); moveTo(start + travel) }
            up()
        }
        compose.waitForIdle()
    }

    @Test fun ordinaryShortSwipesOpenAndCloseAllAppsReliably() {
        ready()
        pager().performTouchInput { swipeRight() }
        compose.waitForIdle()
        val home = page()
        org.junit.Assert.assertTrue(home.startsWith("Home page"))
        for ((duration, pause) in listOf(240L to 0L, 360L to 0L, 360L to 80L)) {
            shortSwipe(right = false, duration, pause)
            assertEquals("All apps", page())
            shortSwipe(right = true, duration, pause)
            assertEquals(home, page())
        }
    }

    @Test fun sameThumbTravelWorksOnCoverAndInnerDisplays() {
        ready()
        shortSwipe(right = true, duration = 360, pause = 80, travelDp = 88f)
        org.junit.Assert.assertTrue(page().startsWith("Home page"))
        shortSwipe(right = false, duration = 360, pause = 80, travelDp = 88f)
        assertEquals("All apps", page())
    }

    @Test fun smallPeeksAndReversedDragsReturnToTheirStartingPage() {
        ready()
        shortSwipe(right = true, duration = 360, pause = 100, fraction = .07f)
        assertEquals("All apps", page())
        pager().performTouchInput {
            val start = Offset(width * .25f, height * .70f)
            down(start)
            for (step in 1..12) moveTo(start + Offset(width * .32f * step / 12f, 0f), 20)
            for (step in 1..12) moveTo(start + Offset(width * (.32f - .28f * step / 12f), 0f), 20)
            up()
        }
        compose.waitForIdle()
        assertEquals("All apps", page())
    }

    @Test fun shortFlicksStillTurnPages() {
        ready()
        shortSwipe(right = true, duration = 120, fraction = .14f)
        org.junit.Assert.assertTrue(page().startsWith("Home page"))
        shortSwipe(right = false, duration = 120, fraction = .14f)
        assertEquals("All apps", page())
    }

}

/** Reproduces cover mode's child scrollable competing with the common ancestor recognizer. */
class CoverRepeatedPagerGestureIntegrationTest {
    @get:Rule val compose = createComposeRule()
    private lateinit var state: PagerState

    @Test fun secondGestureDuringSettleStillReachesAnExactAdjacentEndpoint() {
        compose.setContent {
            state = rememberPagerState(pageCount = { 3 })
            val limits = remember(state) { PageGestureLimits(state) }
            Box(Modifier.fillMaxSize().onePageGestures(state, limits).testTag("cover-pager-owner")) {
                HorizontalPager(state, Modifier.fillMaxSize(), userScrollEnabled = true) { }
            }
        }
        compose.waitForIdle()
        compose.mainClock.autoAdvance = false
        val pager = compose.onNodeWithTag("cover-pager-owner")
        pager.performTouchInput {
            val start = Offset(width * .70f, height * .70f)
            val travel = Offset(-width * .26f, 0f)
            down(start)
            repeat(12) { step -> moveTo(start + travel * ((step + 1) / 12f), 12) }
            up()
        }
        compose.mainClock.advanceTimeByFrame()
        val betweenPages = state.currentPage + state.currentPageOffsetFraction
        assertTrue("The first release must be actively settling before the repeated DOWN: $betweenPages",
            betweenPages in .01f..0.99f)

        pager.performTouchInput {
            val start = Offset(width * .70f, height * .70f)
            val travel = Offset(-width * .26f, 0f)
            down(start)
            // Holding with a sub-slop sample lets an animating child Scrollable take
            // UserInput ownership before the ancestor deliberately crosses slop.
            moveTo(start + Offset(-1f, 0f), 180)
            repeat(12) { step -> moveTo(start + travel * ((step + 1) / 12f), 12) }
            up()
        }
        repeat(150) { compose.mainClock.advanceTimeByFrame() }
        compose.waitForIdle()
        assertEquals(1, state.settledPage)
        assertEquals(1f, state.currentPage + state.currentPageOffsetFraction, .001f)
    }

    @Test fun displacedTerminalReleaseTurnsOnePageWhileTapVerticalAndCancelDoNot() {
        val clicks = java.util.concurrent.atomic.AtomicInteger()
        compose.setContent {
            state = rememberPagerState(pageCount = { 3 })
            val limits = remember(state) { PageGestureLimits(state) }
            Box(Modifier.fillMaxSize().onePageGestures(state, limits).testTag("terminal-pager-owner")) {
                HorizontalPager(state, Modifier.fillMaxSize(), userScrollEnabled = true) {
                    Box(Modifier.fillMaxSize().clickable { clicks.incrementAndGet() })
                }
            }
        }
        compose.waitForIdle()
        val pager = compose.onNodeWithTag("terminal-pager-owner")

        pager.performTouchInput { down(center); advanceEventTime(130); up() }
        compose.waitForIdle()
        assertEquals(1, clicks.get())
        assertEquals(0, state.settledPage)
        pager.performTouchInput {
            val start = Offset(width * .50f, height * .35f)
            down(start); advanceEventTime(130)
            updatePointerTo(0, start + Offset(0f, height * .35f)); up()
        }
        compose.waitForIdle()
        assertEquals(0, state.settledPage)
        pager.performTouchInput {
            val start = Offset(width * .70f, height * .70f)
            down(start); advanceEventTime(130)
            updatePointerTo(0, start + Offset(-width * .48f, 0f)); cancel()
        }
        compose.waitForIdle()
        assertEquals(0, state.settledPage)
        val clicksBeforeHorizontalRelease = clicks.get()

        // Reproduce a blocked frame delivering DOWN followed directly by a displaced UP.
        pager.performTouchInput {
            val start = Offset(width * .70f, height * .70f)
            down(start); advanceEventTime(130)
            updatePointerTo(0, start + Offset(-width * .48f, 0f)); up()
        }
        compose.waitForIdle()
        assertEquals(1, state.settledPage)
        assertEquals(1f, state.currentPage + state.currentPageOffsetFraction, .001f)
        assertEquals("The claimed terminal swipe must not click its child page",
            clicksBeforeHorizontalRelease, clicks.get())
    }
}
