package media.whitewhale.iduo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GoogleNewsImagesTest {
    @Test fun articleIdComesOnlyFromGoogleNewsLinks() {
        assertEquals("CBMi_x-1", GoogleNewsImages.articleId("https://news.google.com/rss/articles/CBMi_x-1?oc=5"))
        assertEquals("CBMi", GoogleNewsImages.articleId("https://news.google.com/articles/CBMi"))
        assertNull(GoogleNewsImages.articleId("https://example.com/rss/articles/CBMi"))
    }

    @Test fun pictureComesFromOpenGraphThenTwitter() {
        assertEquals("https://a/b.jpg?x=1&y=2", pictureOf("""<meta content='https://a/b.jpg?x=1&amp;y=2' property="og:image">"""))
        assertEquals("https://t/p.webp", pictureOf("""<meta data-x name="twitter:image" content="https://t/p.webp" />"""))
        assertEquals("/i.png", pictureOf("""<link rel="image_src" href="/i.png">"""))
        assertNull(pictureOf("""<meta name="description" content="nothing">"""))
    }
}
