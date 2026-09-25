package media.whitewhale.iduo

import androidx.compose.foundation.pager.PagerState

/** Home/drop indices stay zero based; Discover is logical page -1. */
internal class LauncherPager(val state: PagerState, private val firstHome: Int) {
    val currentPage get() = state.currentPage - firstHome
    val settledPage get() = state.settledPage - firstHome
    fun requestScrollToPage(page: Int) = state.requestScrollToPage(page + firstHome)
    suspend fun scrollToPage(page: Int) = state.scrollToPage(page + firstHome)
    suspend fun animateScrollToPage(page: Int) = state.animateScrollToPage(page + firstHome)
}
