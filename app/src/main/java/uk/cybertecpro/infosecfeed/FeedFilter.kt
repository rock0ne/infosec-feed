package uk.cybertecpro.infosecfeed

/** Offline feed filtering shared by the search field and category chips. */
object FeedFilter {
    fun apply(items: List<FeedItem>, category: String?, query: String): List<FeedItem> {
        val categoryItems = category?.let { selected ->
            items.filter { it.category == selected }
        } ?: items
        val needle = query.trim()
        if (needle.isBlank()) return categoryItems
        return categoryItems.filter { item ->
            item.title.contains(needle, ignoreCase = true) ||
                item.summary.contains(needle, ignoreCase = true) ||
                item.source.contains(needle, ignoreCase = true)
        }
    }
}
