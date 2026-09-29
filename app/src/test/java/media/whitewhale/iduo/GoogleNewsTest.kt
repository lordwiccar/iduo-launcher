package media.whitewhale.iduo

import org.junit.Assert.assertEquals
import org.junit.Test

class GoogleNewsTest {
    @Test fun frontPageAndSectionAddressesCarryTheEdition() {
        assertEquals("https://news.google.com/rss?hl=cs&gl=CZ&ceid=CZ:cs", googleNewsFeedUrl(NewsEdition.CZ, NewsTopic.TOP))
        assertEquals("https://news.google.com/rss/headlines/section/topic/TECHNOLOGY?hl=en-GB&gl=GB&ceid=GB:en",
            googleNewsFeedUrl(NewsEdition.GB, NewsTopic.TECHNOLOGY))
    }

    @Test fun defaultEditionMatchesLanguageThenCountry() {
        assertEquals(NewsEdition.AT, defaultNewsEdition("de", "AT"))
        assertEquals(NewsEdition.DE, defaultNewsEdition("de", "FR"))
        assertEquals(NewsEdition.CZ, defaultNewsEdition("cs", "US"))
        assertEquals(NewsEdition.FR, defaultNewsEdition("fr", "FR"))
        assertEquals(NewsEdition.CA_FR, defaultNewsEdition("fr", "CA"))
        assertEquals(NewsEdition.IL, defaultNewsEdition("iw", "IL"))
        // No Croatian edition: fall back to the country, then to the US.
        assertEquals(NewsEdition.CZ, defaultNewsEdition("hr", "CZ"))
        assertEquals(NewsEdition.US, defaultNewsEdition("hr", "HR"))
    }

    @Test fun everyEditionIsUniqueAndLabelledByCountry() {
        assertEquals(NewsEdition.entries.size, NewsEdition.entries.map { it.ceid }.toSet().size)
        assertEquals("Czechia", NewsEdition.CZ.label(java.util.Locale.ENGLISH))
        assertEquals("Canada (French)", NewsEdition.CA_FR.label(java.util.Locale.ENGLISH))
    }

    @Test fun sourcesFollowSectionOrder() {
        val urls = googleNewsSources(NewsEdition.SK, setOf(NewsTopic.SPORTS, NewsTopic.TOP)).map { it.url }
        assertEquals(listOf(googleNewsFeedUrl(NewsEdition.SK, NewsTopic.TOP), googleNewsFeedUrl(NewsEdition.SK, NewsTopic.SPORTS)), urls)
    }

    @Test fun googleNewsItemsNameTheirPublisherAndDropTheRepeatedSummary() {
        val feed = parseFeed("""<rss version="2.0"><channel><title>Top stories - Google News</title>
            <item><title>Big story - Example Times</title><link>https://news.google.com/rss/articles/abc</link>
              <description>&lt;a href="https://example.com/a"&gt;Big story&lt;/a&gt;&amp;nbsp;&amp;nbsp;&lt;font color="#6f6f6f"&gt;Example Times&lt;/font&gt;</description>
              <pubDate>Mon, 28 Sep 2026 10:00:00 GMT</pubDate>
              <source url="https://example.com">Example Times</source></item>
            </channel></rss>""", googleNewsFeedUrl(NewsEdition.US, NewsTopic.TOP))!!
        val item = feed.items.single()
        assertEquals("Big story", item.title)
        assertEquals("Example Times", item.sourceTitle)
        assertEquals("", item.summary)
    }
}
