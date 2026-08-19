package uk.cybertecpro.infosecfeed

import android.content.Context
import android.util.Xml
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.xmlpull.v1.XmlPullParser
import java.io.File
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit

class FeedRepository(private val context: Context) {

    companion object {
        /** Sentinel for a date the feed did not supply or that could not be parsed. */
        const val UNDATED = 0L

        /** Activity, widget and worker refreshes share one process-wide network pass. */
        private val refreshMutex = Mutex()
    }

    private val cacheFile: File get() = File(context.filesDir, "feed-cache.json")

    /** Last successfully written cache, or empty. Safe to call on any thread. */
    fun cached(): List<FeedItem> = runCatching {
        if (cacheFile.exists()) FeedItem.listFromJson(cacheFile.readText()) else emptyList()
    }.getOrDefault(emptyList())

    fun lastUpdated(): Long = if (cacheFile.exists()) cacheFile.lastModified() else 0L

    /**
     * Fetches every source concurrently. A source that fails is skipped, never fatal.
     * Returns the merged, de-duplicated, newest-first list and writes it to cache.
     */
    suspend fun refresh(): List<FeedItem> = refreshMutex.withLock {
        refreshOnce()
    }

    private suspend fun refreshOnce(): List<FeedItem> = withContext(Dispatchers.IO) {
        // ~50 sources: cap concurrent sockets so slow hosts cannot exhaust the pool.
        val gate = Semaphore(8)
        // Reddit answers 429 when several subreddits are requested at once.
        val redditGate = Semaphore(1)
        val collected = coroutineScope {
            val jobs = mutableListOf<kotlinx.coroutines.Deferred<List<FeedItem>>>()
            jobs += async { gate.withPermit { safe("CISA KEV") { fetchKev() } } }
            jobs += async { gate.withPermit { safe("NVD CRITICAL") { fetchNvd("CRITICAL") } } }
            jobs += async { gate.withPermit { safe("NVD HIGH") { fetchNvd("HIGH") } } }
            jobs += async { gate.withPermit { safe("GitHub") { fetchGithub() } } }
            Sources.RSS.forEach { src ->
                val isReddit = src.url.contains("reddit.com")
                jobs += async {
                    gate.withPermit {
                        if (isReddit) {
                            redditGate.withPermit {
                                val items = safe(src.name) { fetchRss(src) }
                                kotlinx.coroutines.delay(2500)
                                items
                            }
                        } else {
                            safe(src.name) { fetchRss(src) }
                        }
                    }
                }
            }
            jobs.awaitAll()
        }.flatten()

        val merged = collected
            .distinctBy { it.id }
            .sortedByDescending { rank(it) }
            .take(500)

        if (merged.isNotEmpty()) {
            runCatching { cacheFile.writeText(FeedItem.listToJson(merged)) }
            prefetchTopImages(merged)
        }
        android.util.Log.i(
            "InfoSecFeed",
            "refresh complete items=${merged.size} images=${merged.count { it.imageUrl != null }}",
        )
        merged
    }

    /**
     * Ranking is recency plus an importance offset, so actively-exploited and
     * critical items outrank a merely newer headline. GitHub repository pushes
     * are demoted: they are activity, not news.
     */
    private fun rank(item: FeedItem): Long {
        if (item.published == UNDATED) return Long.MIN_VALUE
        val hour = 3_600_000L
        val boost = when {
            item.severity == "EXPLOITED" -> 72 * hour
            item.severity == "CRITICAL" -> 18 * hour
            item.severity == "HIGH" -> 5 * hour
            item.source == "GitHub" -> -30 * hour
            else -> 0L
        }
        return item.published + boost
    }

    private suspend fun <T> List<kotlinx.coroutines.Deferred<T>>.awaitAll(): List<T> = map { it.await() }

    private inline fun safe(label: String, block: () -> List<FeedItem>): List<FeedItem> =
        runCatching(block).getOrElse {
            android.util.Log.w("InfoSecFeed", "source failed: $label -> ${it.message}")
            emptyList()
        }

    // ---------- HTTP ----------

    /**
     * HttpURLConnection will not follow a redirect that changes protocol or host,
     * which silently breaks several publisher feeds. Follow them explicitly.
     */
    private fun open(urlString: String, accept: String): InputStream {
        var current = urlString
        repeat(5) {
            val conn = (URL(current).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 15_000
                readTimeout = 20_000
                instanceFollowRedirects = false
                setRequestProperty(
                    "User-Agent",
                    if (current.contains("reddit.com")) {
                        // Reddit rate-limits generic browser agents aggressively.
                        "android:uk.cybertecpro.infosecfeed:1.0 (personal feed reader)"
                    } else {
                        "Mozilla/5.0 (Linux; Android 15) AppleWebKit/537.36 (KHTML, like Gecko) " +
                            "Chrome/127.0.0.0 Mobile Safari/537.36 InfoSecFeed/1.0"
                    }
                )
                setRequestProperty("Accept", accept)
                setRequestProperty("Accept-Language", "en-GB,en;q=0.9")
            }
            val code = conn.responseCode
            if (code in 300..399) {
                val location = conn.getHeaderField("Location")
                conn.disconnect()
                if (location.isNullOrBlank()) throw IllegalStateException("HTTP $code without Location")
                val resolved = URL(URL(current), location).toString()
                // Some feeds redirect to http://; cleartext is disabled, so upgrade.
                current = if (resolved.startsWith("http://")) {
                    resolved.replaceFirst("http://", "https://")
                } else {
                    resolved
                }
                return@repeat
            }
            if (code !in 200..299) {
                conn.disconnect()
                throw IllegalStateException("HTTP $code")
            }
            return conn.inputStream
        }
        throw IllegalStateException("too many redirects")
    }

    private fun readText(urlString: String, accept: String): String =
        open(urlString, accept).bufferedReader().use { it.readText() }

    // ---------- sources ----------

    /** CISA Known Exploited Vulnerabilities: highest-signal source, no key required. */
    private fun fetchKev(): List<FeedItem> {
        val root = JSONObject(readText(Sources.CISA_KEV, "application/json"))
        val arr = root.getJSONArray("vulnerabilities")
        val out = ArrayList<FeedItem>()
        // The catalogue is ordered oldest-first; walk the tail for the newest additions.
        val start = maxOf(0, arr.length() - 40)
        for (i in start until arr.length()) {
            val v = arr.getJSONObject(i)
            val cve = v.optString("cveID")
            val added = parseDate(v.optString("dateAdded"), "yyyy-MM-dd")
            out += FeedItem(
                id = "kev:$cve",
                title = "$cve — ${v.optString("vulnerabilityName")}",
                summary = buildString {
                    append(v.optString("vendorProject")).append(' ').append(v.optString("product"))
                    val due = v.optString("dueDate")
                    if (due.isNotEmpty()) append("  ·  remediate by ").append(due)
                    val d = v.optString("shortDescription")
                    if (d.isNotEmpty()) append("\n").append(d)
                },
                source = "CISA KEV",
                url = "https://nvd.nist.gov/vuln/detail/$cve",
                published = added,
                severity = "EXPLOITED",
                category = Categories.ALERTS,
            )
        }
        return out
    }

    /** Recent NVD CVEs at a given severity. Unauthenticated limit is 5 requests / 30s. */
    private fun fetchNvd(severity: String): List<FeedItem> {
        val fmt = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS", Locale.UK).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
        val now = System.currentTimeMillis()
        val from = now - TimeUnit.DAYS.toMillis(7)
        val url = Sources.NVD_BASE +
            "?pubStartDate=${fmt.format(Date(from))}" +
            "&pubEndDate=${fmt.format(Date(now))}" +
            "&cvssV3Severity=$severity&resultsPerPage=25"

        val root = JSONObject(readText(url, "application/json"))
        val vulns = root.optJSONArray("vulnerabilities") ?: return emptyList()
        val out = ArrayList<FeedItem>()
        for (i in 0 until vulns.length()) {
            val cve = vulns.getJSONObject(i).optJSONObject("cve") ?: continue
            val id = cve.optString("id")
            val descs = cve.optJSONArray("descriptions")
            var text = ""
            if (descs != null) {
                for (j in 0 until descs.length()) {
                    val d = descs.getJSONObject(j)
                    if (d.optString("lang") == "en") { text = d.optString("value"); break }
                }
            }
            out += FeedItem(
                id = "nvd:$id",
                title = id,
                summary = text.take(400),
                source = "NVD",
                url = "https://nvd.nist.gov/vuln/detail/$id",
                published = parseDate(cve.optString("published"), "yyyy-MM-dd'T'HH:mm:ss.SSS"),
                severity = severity,
                category = Categories.VULNS,
            )
        }
        return out
    }

    /** GitHub Search API. "Trending" has no official API, so recent security repos stand in. */
    private fun fetchGithub(): List<FeedItem> {
        val since = SimpleDateFormat("yyyy-MM-dd", Locale.UK).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }.format(Date(System.currentTimeMillis() - TimeUnit.DAYS.toMillis(7)))
        val root = JSONObject(readText(String.format(Sources.GITHUB_SEARCH, since), "application/vnd.github+json"))
        val items = root.optJSONArray("items") ?: return emptyList()
        val out = ArrayList<FeedItem>()
        for (i in 0 until items.length()) {
            val r = items.getJSONObject(i)
            out += FeedItem(
                id = "gh:${r.optLong("id")}",
                title = r.optString("full_name"),
                summary = "★ ${r.optInt("stargazers_count")}  ·  ${r.optString("description").take(300)}",
                source = "GitHub",
                url = r.optString("html_url"),
                published = parseDate(r.optString("pushed_at"), "yyyy-MM-dd'T'HH:mm:ss'Z'"),
                category = Categories.COMMUNITY,
            )
        }
        return out
    }

    /** Handles both RSS <item> and Atom <entry> in one pass. */
    private fun fetchRss(src: RssSource): List<FeedItem> {
        val raw = open(src.url, "application/rss+xml, application/atom+xml, application/xml, text/xml")
            .bufferedReader().use { it.readText() }

        // Strip a UTF-8 BOM and any leading whitespace; both break the pull parser.
        val body = raw.trimStart('\uFEFF', ' ', '\n', '\r', '\t')

        // Several publishers answer a feed path with an HTML page or a bot-check.
        val head = body.take(200).lowercase()
        if (head.startsWith("<!doctype html") || head.startsWith("<html")) {
            throw IllegalStateException("not a feed (HTML returned)")
        }

        run {
            val parser = Xml.newPullParser()
            parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
            parser.setInput(java.io.StringReader(body))

            val out = ArrayList<FeedItem>()
            var title = ""; var link = ""; var desc = ""; var date = ""
            var imageCandidate = ""; var htmlImageCandidate = ""
            var inEntry = false
            var event = parser.eventType

            while (event != XmlPullParser.END_DOCUMENT && out.size < 12) {
                when (event) {
                    XmlPullParser.START_TAG -> when (parser.name.lowercase()) {
                        "item", "entry" -> {
                            inEntry = true
                            title = ""; link = ""; desc = ""; date = ""
                            imageCandidate = ""; htmlImageCandidate = ""
                        }
                        "title" -> if (inEntry) title = parser.nextText().trim()
                        "link" -> if (inEntry) {
                            val href = parser.getAttributeValue(null, "href")
                            link = if (!href.isNullOrEmpty()) href else parser.nextText().trim()
                        }
                        "description", "summary", "content" -> if (inEntry) {
                            val rawDescription = parser.nextText()
                            if (desc.isEmpty()) desc = stripHtml(rawDescription)
                            if (htmlImageCandidate.isEmpty()) {
                                htmlImageCandidate = RssImageUrl.firstFromHtml(rawDescription).orEmpty()
                            }
                        }
                        "enclosure" -> if (inEntry && imageCandidate.isEmpty()) {
                            val type = parser.getAttributeValue(null, "type").orEmpty()
                            if (type.startsWith("image/", ignoreCase = true)) {
                                imageCandidate = parser.getAttributeValue(null, "url").orEmpty()
                            }
                        }
                        "media:content", "media:thumbnail" ->
                            if (inEntry && imageCandidate.isEmpty()) {
                                imageCandidate = parser.getAttributeValue(null, "url").orEmpty()
                            }
                        "pubdate", "published", "updated", "dc:date", "date" ->
                            if (inEntry && date.isEmpty()) date = parser.nextText().trim()
                    }
                    XmlPullParser.END_TAG -> {
                        val n = parser.name.lowercase()
                        if ((n == "item" || n == "entry") && inEntry) {
                            inEntry = false
                            if (title.isNotEmpty()) {
                                out += FeedItem(
                                    id = "rss:${src.name}:${link.ifEmpty { title }}",
                                    title = title,
                                    summary = desc.take(400),
                                    source = src.name,
                                    url = link,
                                    published = parseRssDate(date),
                                    category = src.category,
                                    imageUrl = RssImageUrl.resolve(
                                        imageCandidate.ifEmpty { htmlImageCandidate },
                                        link.ifEmpty { src.url },
                                    ),
                                )
                            }
                        }
                    }
                }
                event = parser.next()
            }
            return out
        }
    }

    // ---------- helpers ----------

    /** Warm only the visible feed head; widgets read this bounded disk cache. */
    private suspend fun prefetchTopImages(items: List<FeedItem>) = coroutineScope {
        val gate = Semaphore(3)
        items.asSequence().mapNotNull { it.imageUrl }.distinct().take(12).map { imageUrl ->
            async {
                gate.withPermit { ImageLoader.load(context.cacheDir, imageUrl) }
            }
        }.toList().awaitAll()
        ImageLoader.trimDiskCache(context.cacheDir)
    }

    private fun stripHtml(s: String): String =
        s.replace(Regex("<[^>]*>"), " ")
            .replace("&nbsp;", " ").replace("&amp;", "&")
            .replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"")
            .replace(Regex("\\s+"), " ").trim()

    private fun parseDate(value: String, pattern: String): Long = runCatching {
        SimpleDateFormat(pattern, Locale.UK).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }.parse(value.removeSuffix("Z").let { if (pattern.endsWith("'Z'")) value else it })?.time ?: 0L
    }.getOrDefault(0L)

    /**
     * Feeds are inconsistent: RFC-822 with numeric or named zones, two-digit
     * years, ISO-8601, and a few bespoke formats. An unparseable date returns
     * UNDATED rather than "now" — defaulting to now wrongly promotes stale
     * items to the top of the feed.
     */
    private fun parseRssDate(value: String): Long {
        if (value.isBlank()) return UNDATED
        val cleaned = value.trim()
        val patterns = listOf(
            "EEE, dd MMM yyyy HH:mm:ss Z",
            "EEE, dd MMM yyyy HH:mm:ss zzz",
            "EEE, dd MMM yy HH:mm:ss Z",       // CISA sends a two-digit year
            "EEE, dd MMM yy HH:mm:ss zzz",
            "EEE, dd MMM yyyy HH:mm Z",
            "MMM dd, yyyy HH:mm:ssZ",          // CrowdStrike
            "MMM dd, yyyy HH:mm:ss Z",
            "yyyy-MM-dd'T'HH:mm:ssXXX",
            "yyyy-MM-dd'T'HH:mm:ss.SSSXXX",
            "yyyy-MM-dd'T'HH:mm:ss'Z'",
            "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
            "yyyy-MM-dd HH:mm:ss",
            "yyyy-MM-dd",
        )
        for (p in patterns) {
            runCatching {
                SimpleDateFormat(p, Locale.UK).apply { isLenient = false }.parse(cleaned)?.time
            }.getOrNull()?.let { if (it > 0) return it }
        }
        return UNDATED
    }

}
