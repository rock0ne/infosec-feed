package uk.cybertecpro.infosecfeed

import org.junit.Assert.assertEquals
import org.junit.Test

class FeedFilterTest {
    private val items = listOf(
        FeedItem(
            id = "1",
            title = "CVE-2026-607 remote execution",
            summary = "Oracle database issue",
            source = "NVD",
            url = "https://example.com/1",
            published = 1,
            category = Categories.VULNS,
        ),
        FeedItem(
            id = "2",
            title = "Detection engineering guide",
            summary = "Sigma rules for defenders",
            source = "SANS ISC",
            url = "https://example.com/2",
            published = 2,
            category = Categories.RESEARCH,
        ),
    )

    @Test
    fun `finds CVE ids case insensitively`() {
        assertEquals(listOf("1"), FeedFilter.apply(items, null, "cve-2026-607").map { it.id })
    }

    @Test
    fun `search matches summary and source`() {
        assertEquals(listOf("1"), FeedFilter.apply(items, null, "oracle").map { it.id })
        assertEquals(listOf("2"), FeedFilter.apply(items, null, "sans").map { it.id })
    }

    @Test
    fun `category and search are combined`() {
        assertEquals(listOf("1"), FeedFilter.apply(items, Categories.VULNS, "oracle").map { it.id })
        assertEquals(emptyList<String>(), FeedFilter.apply(items, Categories.RESEARCH, "oracle").map { it.id })
    }
}
