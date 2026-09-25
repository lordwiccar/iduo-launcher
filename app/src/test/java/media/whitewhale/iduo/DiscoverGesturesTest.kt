package media.whitewhale.iduo

import org.junit.Assert.*
import org.junit.Test

class DiscoverGesturesTest {
    @Test fun frameSwipeCommitsOnceAndDoesNotMistakeVerticalScrollingForHome() {
        val swipe = DiscoverHomeSwipe(threshold = 72f, slop = 8f)
        swipe.down(300f, 100f)
        assertFalse(swipe.move(260f, 105f))
        assertTrue(swipe.move(220f, 108f))
        assertFalse(swipe.move(100f, 108f))
        swipe.down(300f, 100f)
        assertFalse(swipe.move(295f, 130f))
        assertFalse(swipe.move(100f, 140f))
    }

    @Test fun rightwardAndCancelledGesturesCannotTurnIntoHomeNavigation() {
        val swipe = DiscoverHomeSwipe(threshold = 72f, slop = 8f)
        swipe.down(100f, 100f)
        assertFalse(swipe.move(120f, 100f))
        assertFalse(swipe.move(0f, 100f))
        swipe.down(100f, 100f)
        swipe.cancel()
        assertFalse(swipe.move(0f, 100f))
    }

    @Test fun nativeDismissalRequiresAVisibleFeedAndCommitsBeforeItRevealsTheLoadingPage() {
        val dismissal = DiscoverDismissal()
        assertFalse(dismissal.progress(0f, 330f)) // Initial attachment isn't a dismissal.
        assertFalse(dismissal.progress(.6f, 330f))
        assertFalse(dismissal.progress(1f, 330f))
        assertFalse(dismissal.progress(.9f, 330f)) // Short movement can settle back.
        assertFalse(dismissal.progress(1f, 330f))
        assertTrue(dismissal.progress(.77f, 330f))
        assertFalse(dismissal.progress(0f, 330f)) // Commit only once.
    }

    @Test fun openingAnotherAppDoesNotLookLikeAHomeSwipe() {
        val dismissal = DiscoverDismissal()
        dismissal.progress(1f, 330f)
        dismissal.suspend()
        assertFalse(dismissal.progress(0f, 330f))
        assertFalse(dismissal.progress(.4f, 330f)) // Reattachment while returning.
        dismissal.progress(1f, 330f)
        assertTrue(dismissal.progress(.7f, 330f))
    }

    @Test fun wideAndNarrowFeedsUseReachableDistancesAndIgnoreInvalidCallbacks() {
        val dismissal = DiscoverDismissal()
        dismissal.progress(1f, 800f)
        assertFalse(dismissal.progress(Float.NaN, 800f))
        assertFalse(dismissal.progress(-1f, 800f))
        assertFalse(dismissal.progress(.95f, 800f))
        assertTrue(dismissal.progress(.9f, 800f))
        dismissal.progress(1f, 200f)
        assertTrue(dismissal.progress(.74f, 200f))
    }
}
