package uk.cybertecpro.infosecfeed

import java.net.URL

/** Extracts and normalises untrusted image candidates supplied by RSS/Atom feeds. */
object RssImageUrl {

    private val imagePattern = Regex(
        """<img\b[^>]*?\bsrc\s*=\s*(?:["']([^"']+)["']|([^\s>]+))""",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
    )

    fun firstFromHtml(html: String): String? {
        val match = imagePattern.find(html) ?: return null
        return match.groupValues[1].ifBlank { match.groupValues[2] }.ifBlank { null }
    }

    /**
     * Resolves relative and protocol-relative candidates, upgrades cleartext, and
     * rejects schemes that must never reach the network loader.
     */
    fun resolve(candidate: String?, baseUrl: String): String? {
        val value = candidate
            ?.trim()
            ?.replace("&amp;", "&", ignoreCase = true)
            ?.takeIf { it.isNotEmpty() && it.length <= 4096 }
            ?: return null

        return runCatching {
            val absolute = when {
                value.startsWith("//") -> "https:$value"
                value.startsWith("http://", ignoreCase = true) ->
                    value.replaceFirst(Regex("^http://", RegexOption.IGNORE_CASE), "https://")
                else -> URL(URL(baseUrl), value).toString()
            }
            val parsed = URL(absolute)
            if (parsed.protocol != "https" || !parsed.userInfo.isNullOrEmpty()) return null
            parsed.toURI().normalize().toString()
        }.getOrNull()
    }
}
