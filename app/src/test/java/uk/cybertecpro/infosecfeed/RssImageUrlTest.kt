package uk.cybertecpro.infosecfeed

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RssImageUrlTest {

    @Test
    fun extractsQuotedAndUnquotedImageSources() {
        assertEquals(
            "https://cdn.example/story.webp",
            RssImageUrl.firstFromHtml("<p>x</p><img alt='x' src=\"https://cdn.example/story.webp\">")
        )
        assertEquals(
            "/images/story.jpg",
            RssImageUrl.firstFromHtml("<IMG SRC=/images/story.jpg alt=test>")
        )
    }

    @Test
    fun resolvesRelativeAndProtocolRelativeUrlsToHttps() {
        assertEquals(
            "https://news.example/images/story.jpg",
            RssImageUrl.resolve("/images/story.jpg", "https://news.example/posts/1")
        )
        assertEquals(
            "https://cdn.example/story.jpg",
            RssImageUrl.resolve("//cdn.example/story.jpg", "https://news.example/posts/1")
        )
        assertEquals(
            "https://cdn.example/story.jpg?a=1&b=2",
            RssImageUrl.resolve(
                "http://cdn.example/story.jpg?a=1&amp;b=2",
                "https://news.example/posts/1"
            )
        )
    }

    @Test
    fun rejectsActiveSchemesCredentialsAndOversizedCandidates() {
        assertNull(RssImageUrl.resolve("javascript:alert(1)", "https://news.example/posts/1"))
        assertNull(RssImageUrl.resolve("data:image/png;base64,abc", "https://news.example/posts/1"))
        assertNull(RssImageUrl.resolve("https://user:pass@cdn.example/x", "https://news.example"))
        assertNull(RssImageUrl.resolve("https://cdn.example/${"x".repeat(5000)}", "https://news.example"))
    }
}
