package uk.cybertecpro.infosecfeed

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AlertPolicyTest {
    private fun item(id: String, severity: String?, published: Long) = FeedItem(
        id = id,
        title = id,
        summary = "",
        source = "test",
        url = "https://example.com/$id",
        published = published,
        severity = severity,
    )

    @Test
    fun `only new exploited and critical items alert`() {
        val items = listOf(
            item("exploited", "EXPLOITED", 1),
            item("critical", "CRITICAL", 5),
            item("high", "HIGH", 10),
            item("news", null, 20),
        )
        assertEquals(
            listOf("exploited", "critical"),
            AlertPolicy.newAlerts(items, emptySet()).map { it.id },
        )
        assertEquals(
            listOf("exploited"),
            AlertPolicy.newAlerts(items, setOf("critical")).map { it.id },
        )
    }

    @Test
    fun `alert burst is bounded`() {
        val items = (1L..12L).map { item("c$it", "CRITICAL", it) }
        val selected = AlertPolicy.newAlerts(items, emptySet(), limit = 5)
        assertEquals(5, selected.size)
        assertEquals("c12", selected.first().id)
    }

    @Test
    fun `alert ids exclude routine content`() {
        val ids = AlertPolicy.alertIds(
            listOf(item("critical", "CRITICAL", 1), item("high", "HIGH", 2)),
        )
        assertEquals(setOf("critical"), ids)
        assertTrue("high" !in ids)
    }
}
