package uk.cybertecpro.infosecfeed

/** Pure selection policy: alert only on genuinely new, high-signal feed entries. */
object AlertPolicy {
    private val alertSeverities = setOf("EXPLOITED", "CRITICAL")

    fun newAlerts(items: List<FeedItem>, seenIds: Set<String>, limit: Int = 5): List<FeedItem> =
        items.asSequence()
            .filter { it.severity in alertSeverities && it.id !in seenIds }
            .sortedWith(
                compareByDescending<FeedItem> { it.severity == "EXPLOITED" }
                    .thenByDescending { it.published },
            )
            .take(limit)
            .toList()

    fun alertIds(items: List<FeedItem>): Set<String> =
        items.asSequence()
            .filter { it.severity in alertSeverities }
            .map { it.id }
            .toSet()
}
