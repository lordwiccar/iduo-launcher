package media.whitewhale.iduo

import org.junit.Assert.assertEquals
import org.junit.Test

class AppCategoriesTest {
    private val none = -1 // ApplicationInfo.CATEGORY_UNDEFINED

    @Test fun `google and maker apps get their own folders first`() {
        assertEquals(AppCategory.GOOGLE, appCategory("com.google.android.gm", "Gmail", 7, preinstalled = true))
        assertEquals(AppCategory.GOOGLE, appCategory("com.android.chrome", "Chrome", none, preinstalled = true))
        assertEquals(AppCategory.MAKER, appCategory("com.samsung.android.app.notes", "Samsung Notes", none, preinstalled = true))
        assertEquals(AppCategory.MAKER, appCategory("com.sec.android.gallery3d", "Gallery", 3, preinstalled = true))
    }

    @Test fun `android's own category decides next`() {
        assertEquals(AppCategory.GAMES, appCategory("com.example.puzzle", "Puzzle", 0, preinstalled = false))
        assertEquals(AppCategory.MEDIA, appCategory("com.example.tunes", "Tunes", 1, preinstalled = false))
        assertEquals(AppCategory.TRAVEL, appCategory("com.waze", "Waze", 6, preinstalled = false))
    }

    @Test fun `names tell the purpose of apps without a category`() {
        assertEquals(AppCategory.FINANCE, appCategory("cz.airbank.android", "My Air", none, preinstalled = false))
        assertEquals(AppCategory.SHOPPING, appCategory("cz.alza.eshop", "Alza", none, preinstalled = false))
        assertEquals(AppCategory.SOCIAL, appCategory("org.telegram.messenger", "Telegram", none, preinstalled = false))
        assertEquals(AppCategory.EDUCATION, appCategory("com.duolingo", "Duolingo", none, preinstalled = false))
        // Banks and audiobooks often call themselves productivity apps; their names win.
        assertEquals(AppCategory.FINANCE, appCategory("cz.fio.sb2", "Fio banka", 7, preinstalled = false))
        assertEquals(AppCategory.FINANCE, appCategory("com.paypal.android.p2pmobile", "PayPal", 7, preinstalled = false))
        assertEquals(AppCategory.MEDIA, appCategory("com.audioteka", "Audioteka", 7, preinstalled = false))
        assertEquals(AppCategory.HEALTH, appCategory("cz.nakit.eocko.wallet", "EZKarta", none, preinstalled = false))
        assertEquals(AppCategory.TRAVEL, appCategory("cz.dpp.praguepublictransport", "pid lítačka", 6, preinstalled = false))
        assertEquals(AppCategory.PRODUCTIVITY, appCategory("com.example.tool", "Tool", 7, preinstalled = false))
        // "fit" must stand alone: "benefit" is not fitness.
        assertEquals(AppCategory.OTHER, appCategory("com.example.benefits", "Benefits", none, preinstalled = false))
        // "schedule" contains "edu" but is not an education app.
        assertEquals(AppCategory.OTHER, appCategory("com.example.scheduler", "Shifts", none, preinstalled = false))
    }

    @Test fun `other preinstalled apps are system apps`() {
        assertEquals(AppCategory.SYSTEM, appCategory("com.android.settings", "Settings", none, preinstalled = true))
        assertEquals(AppCategory.OTHER, appCategory("com.example.thing", "Thing", none, preinstalled = false))
    }

    @Test fun `recent apps come first and empty folders are left out`() {
        val apps = listOf("a" to AppCategory.GAMES, "b" to AppCategory.SOCIAL, "c" to AppCategory.SOCIAL)
        val folders = libraryFolders(apps, listOf("c", "gone"), { it.first }, { it.second })
        assertEquals(listOf(AppCategory.RECENT, AppCategory.SOCIAL, AppCategory.GAMES), folders.map { it.first })
        assertEquals(listOf("c"), folders.first().second.map { it.first })
    }
}
