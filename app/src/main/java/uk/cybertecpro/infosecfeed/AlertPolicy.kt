package uk.cybertecpro.infosecfeed

enum class AlertMode {
    OFF,
    KEV_ONLY,
    KEV_AND_CRITICAL,
}

/** Pure selection policy: alert only on genuinely new, high-signal feed entries. */
object AlertPolicy {
    fun newAlerts(
        items: List<FeedItem>,
        seenIds: Set<String>,
        mode: AlertMode,
        limit: Int = 5,
    ): List<FeedItem> =
        items.asSequence()
            .filter { accepts(it, mode) && it.id !in seenIds }
            .sortedWith(
                compareByDescending<FeedItem> { it.severity == "EXPLOITED" }
                    .thenByDescending { it.published },
            )
            .take(limit)
            .toList()

    fun alertIds(items: List<FeedItem>, mode: AlertMode): Set<String> =
        items.asSequence()
            .filter { accepts(it, mode) }
            .map { it.id }
            .toSet()

    private fun accepts(item: FeedItem, mode: AlertMode): Boolean = when (mode) {
        AlertMode.OFF -> false
        AlertMode.KEV_ONLY -> item.severity == "EXPLOITED"
        AlertMode.KEV_AND_CRITICAL -> item.severity == "EXPLOITED" || item.severity == "CRITICAL"
    }
}
