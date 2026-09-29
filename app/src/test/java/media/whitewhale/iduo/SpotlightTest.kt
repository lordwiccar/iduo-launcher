package media.whitewhale.iduo

import org.junit.Assert.assertEquals
import org.junit.Test

class SpotlightTest {
    private val labels = listOf("Google Maps", "Maps.me", "Kalkulačka", "Kalendář", "Photomath", "Zpráva", "Camera")

    private fun find(query: String, limit: Int = 8) = rankedMatches(labels, query, limit) { it }

    @Test fun ignoresCaseAndDiacritics() {
        assertEquals(listOf("Kalkulačka"), find("KALKULACKA"))
        assertEquals(listOf("Zpráva"), find("zprava"))
    }

    @Test fun ranksPrefixThenWordStartThenInside() {
        assertEquals(listOf("Maps.me", "Google Maps", "Photomath"), find("ma"))
    }

    @Test fun sortsAlphabeticallyWithinARank() {
        assertEquals(listOf("Kalendář", "Kalkulačka"), find("kal"))
    }

    @Test fun blankQueryAndLimit() {
        assertEquals(emptyList<String>(), find("  "))
        assertEquals(listOf("Maps.me"), find("ma", limit = 1))
    }

    @Test fun googleUrlEncodesThePhrase() {
        assertEquals("https://www.google.com/search?q=po%C4%8Das%C3%AD+v+Praze+%26+okol%C3%AD", googleSearchUrl("počasí v Praze & okolí"))
    }
}
