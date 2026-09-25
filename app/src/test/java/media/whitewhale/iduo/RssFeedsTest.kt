package media.whitewhale.iduo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RssFeedsTest {
    @Test fun parsesRssWithMediaThumbnailAndHtmlSummary() {
        val feed = parseFeed("""<?xml version="1.0"?>
            <rss version="2.0" xmlns:media="http://search.yahoo.com/mrss/">
              <channel><title>Example News</title><link>https://example.com/</link>
                <image><title>Logo</title><url>https://example.com/logo.png</url></image>
                <item><title>First &amp; best</title><link>https://example.com/a</link>
                  <description><![CDATA[<p>Hello <b>world</b>&nbsp;today</p>]]></description>
                  <pubDate>Wed, 23 Sep 2026 10:00:00 +0200</pubDate>
                  <media:thumbnail url="https://example.com/a.jpg"/></item>
                <item><title>Second</title><link>/b</link></item>
              </channel></rss>""", "https://example.com/feed")!!
        assertEquals("Example News", feed.title)
        assertEquals(2, feed.items.size)
        val first = feed.items[0]
        assertEquals("First & best", first.title)
        assertEquals("Hello world today", first.summary)
        assertEquals("https://example.com/a.jpg", first.imageUrl)
        assertEquals(parseFeedDate("2026-09-23T08:00:00Z"), first.published)
        assertEquals("Example News", first.sourceTitle)
        assertEquals("https://example.com/b", feed.items[1].link)
    }

    @Test fun parsesAtomAlternateLinksAndImagesInContent() {
        val feed = parseFeed("""<feed xmlns="http://www.w3.org/2005/Atom"><title>Blog</title>
            <entry><title>Post</title><id>tag:1</id>
              <link rel="self" href="https://blog.example/api/1"/><link href="https://blog.example/post"/>
              <updated>2026-09-20T12:30:00+02:00</updated>
              <content type="html">&lt;img src="https://blog.example/p.png"&gt; Body</content></entry></feed>""",
            "https://blog.example/atom")!!
        assertEquals("Blog", feed.title)
        val entry = feed.items.single()
        assertEquals("https://blog.example/post", entry.link)
        assertEquals("https://blog.example/p.png", entry.imageUrl)
        assertEquals("tag:1", entry.id)
        assertTrue(entry.published > 0)
    }

    @Test fun webPageIsNotAFeedButAdvertisesOne() {
        val html = """<html><head><link rel="stylesheet" href="/s.css">
            <link rel="alternate" type="application/rss+xml" title="RSS" href="/rss.xml"></head><body></body></html>"""
        assertNull(parseFeed(html, "https://site.example/"))
        assertEquals("https://site.example/rss.xml", discoverFeedLink(html, "https://site.example/"))
    }

    @Test fun addressesBecomeHttps() {
        assertEquals("https://example.com/feed", normalizeFeedAddress(" example.com/feed "))
        assertEquals("https://example.com", normalizeFeedAddress("http://example.com"))
        assertNull(normalizeFeedAddress("ftp://example.com"))
        assertNull(normalizeFeedAddress("not an address"))
        assertNull(normalizeFeedAddress("localhost"))
    }

    @Test fun mergeKeepsNewestFirstWithoutDuplicateLinks() {
        fun item(link: String, time: Long) = RssItem(link, "s", "S", link, "https://x/$link", "", null, time)
        val merged = mergeFeedItems(listOf(item("a", 1), item("b", 3), item("a", 2), item("c", 0)))
        assertEquals(listOf("b", "a", "c"), merged.map { it.id })
    }
}
