package uk.cybertecpro.infosecfeed

import org.json.JSONArray
import org.json.JSONObject

/** One normalised feed entry from any source. */
data class FeedItem(
    val id: String,
    val title: String,
    val summary: String,
    val source: String,
    val url: String,
    val published: Long,
    val severity: String? = null,
    val category: String = Categories.NEWS,
    val imageUrl: String? = null,
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id); put("title", title); put("summary", summary)
        put("source", source); put("url", url); put("published", published)
        put("severity", severity ?: JSONObject.NULL)
        put("category", category)
        put("imageUrl", imageUrl ?: JSONObject.NULL)
    }

    companion object {
        fun fromJson(o: JSONObject) = FeedItem(
            id = o.getString("id"),
            title = o.getString("title"),
            summary = o.optString("summary", ""),
            source = o.getString("source"),
            url = o.optString("url", ""),
            published = o.optLong("published", 0L),
            severity = if (o.isNull("severity")) null else o.optString("severity"),
            category = o.optString("category", Categories.NEWS),
            imageUrl = if (o.isNull("imageUrl")) null else o.optString("imageUrl"),
        )

        fun listToJson(items: List<FeedItem>): String {
            val a = JSONArray()
            items.forEach { a.put(it.toJson()) }
            return a.toString()
        }

        fun listFromJson(text: String): List<FeedItem> {
            val a = JSONArray(text)
            return (0 until a.length()).map { fromJson(a.getJSONObject(it)) }
        }
    }
}
