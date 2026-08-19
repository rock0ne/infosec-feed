package uk.cybertecpro.infosecfeed

/**
 * Feed roster. This app replaces the Samsung/Google Discover panel, so the
 * breadth has to be comparable — but every source is security-relevant.
 *
 * Edit this file to add or remove sources; nothing else needs to change.
 *
 * Note on reachability: several publishers (BleepingComputer, Reddit, a few
 * vendor blogs) reject datacentre IPs but serve phones normally. Sources are
 * therefore attempted optimistically and skipped silently on failure.
 */
data class RssSource(val name: String, val url: String, val category: String)

object Categories {
    const val ALERTS = "Alerts"
    const val VULNS = "Vulns"
    const val INTEL = "Threat Intel"
    const val NEWS = "News"
    const val RESEARCH = "Research"
    const val COMMUNITY = "Community"

    val ORDER = listOf(ALERTS, VULNS, INTEL, NEWS, RESEARCH, COMMUNITY)
}

object Sources {

    const val CISA_KEV = "https://www.cisa.gov/sites/default/files/feeds/known_exploited_vulnerabilities.json"
    const val NVD_BASE = "https://services.nvd.nist.gov/rest/json/cves/2.0"
    const val GITHUB_SEARCH =
        "https://api.github.com/search/repositories?q=topic:security+pushed:>%s&sort=stars&order=desc&per_page=10"

    val RSS = listOf(
        // ---------- Alerts: government and authoritative advisories ----------
        RssSource("CISA Advisories", "https://www.cisa.gov/cybersecurity-advisories/all.xml", Categories.ALERTS),
        RssSource("NCSC UK", "https://www.ncsc.gov.uk/api/1/services/v1/report-rss-feed.xml", Categories.ALERTS),
        RssSource("MSRC Update Guide", "https://api.msrc.microsoft.com/update-guide/rss", Categories.ALERTS),
        RssSource("Zero Day Initiative", "https://www.zerodayinitiative.com/rss/published/", Categories.ALERTS),

        RssSource("JPCERT", "https://www.jpcert.or.jp/english/rss/jpcert-en.rdf", Categories.ALERTS),

        // ---------- Vulns: exploit and disclosure tracking ----------
        RssSource("Full Disclosure", "https://seclists.org/rss/fulldisclosure.rss", Categories.VULNS),
        RssSource("Exploit-DB", "https://www.exploit-db.com/rss.xml", Categories.VULNS),

        // ---------- Threat Intel: vendor research teams ----------
        RssSource("Cisco Talos", "https://blog.talosintelligence.com/rss/", Categories.INTEL),
        RssSource("Unit 42", "https://unit42.paloaltonetworks.com/feed/", Categories.INTEL),
        RssSource("Microsoft Security", "https://www.microsoft.com/en-us/security/blog/feed/", Categories.INTEL),
        RssSource("CrowdStrike", "https://www.crowdstrike.com/blog/feed/", Categories.INTEL),
        RssSource("SentinelOne Labs", "https://www.sentinelone.com/labs/feed/", Categories.INTEL),
        RssSource("WeLiveSecurity", "https://www.welivesecurity.com/en/rss/feed/", Categories.INTEL),
        RssSource("Check Point Research", "https://research.checkpoint.com/feed/", Categories.INTEL),
        RssSource("Trend Micro", "https://feeds.trendmicro.com/TrendMicroResearch", Categories.INTEL),
        RssSource("Huntress", "https://www.huntress.com/blog/rss.xml", Categories.INTEL),
        RssSource("Rapid7", "https://blog.rapid7.com/rss/", Categories.INTEL),
        RssSource("Red Canary", "https://redcanary.com/feed/", Categories.INTEL),

        RssSource("Malwarebytes Labs", "https://www.malwarebytes.com/blog/feed/index.xml", Categories.INTEL),
        RssSource("Elastic Security Labs", "https://www.elastic.co/security-labs/rss/feed.xml", Categories.INTEL),
        RssSource("Bitdefender Labs", "https://www.bitdefender.com/blog/api/rss/labs/", Categories.INTEL),
        RssSource("Objective-See", "https://objective-see.org/rss.xml", Categories.INTEL),

        // ---------- News ----------
        RssSource("The Hacker News", "https://feeds.feedburner.com/TheHackersNews", Categories.NEWS),
        RssSource("BleepingComputer", "https://www.bleepingcomputer.com/feed/", Categories.NEWS),
        RssSource("The Record", "https://therecord.media/feed", Categories.NEWS),
        RssSource("SecurityWeek", "https://www.securityweek.com/feed/", Categories.NEWS),
        RssSource("Dark Reading", "https://www.darkreading.com/rss.xml", Categories.NEWS),
        RssSource("Infosecurity Mag", "https://www.infosecurity-magazine.com/rss/news/", Categories.NEWS),
        RssSource("CyberScoop", "https://cyberscoop.com/feed/", Categories.NEWS),
        RssSource("The Register", "https://www.theregister.com/security/headlines.atom", Categories.NEWS),
        RssSource("Help Net Security", "https://www.helpnetsecurity.com/feed/", Categories.NEWS),
        RssSource("Krebs on Security", "https://krebsonsecurity.com/feed/", Categories.NEWS),
        RssSource("Graham Cluley", "https://grahamcluley.com/feed/", Categories.NEWS),

        // ---------- Research and deep technical ----------
        RssSource("Project Zero", "https://googleprojectzero.blogspot.com/feeds/posts/default?alt=rss", Categories.RESEARCH),
        RssSource("Google Security", "https://security.googleblog.com/feeds/posts/default?alt=rss", Categories.RESEARCH),
        RssSource("PortSwigger Research", "https://portswigger.net/research/rss", Categories.RESEARCH),
        RssSource("watchTowr Labs", "https://labs.watchtowr.com/rss/", Categories.RESEARCH),
        RssSource("Cloudflare", "https://blog.cloudflare.com/rss/", Categories.RESEARCH),
        RssSource("Schneier", "https://www.schneier.com/feed/atom/", Categories.RESEARCH),
        RssSource("Troy Hunt", "https://www.troyhunt.com/rss/", Categories.RESEARCH),
        RssSource("Trail of Bits", "https://blog.trailofbits.com/feed/", Categories.RESEARCH),

        RssSource("Qualys", "https://blog.qualys.com/feed", Categories.RESEARCH),
        RssSource("Tenable", "https://www.tenable.com/blog/feed", Categories.RESEARCH),
        RssSource("Wiz", "https://www.wiz.io/blog/rss.xml", Categories.RESEARCH),
        RssSource("Snyk", "https://snyk.io/blog/feed/", Categories.RESEARCH),
        RssSource("Horizon3", "https://www.horizon3.ai/feed/", Categories.RESEARCH),
        RssSource("Assetnote", "https://blog.assetnote.io/feed.xml", Categories.RESEARCH),

        // ---------- Community ----------
        RssSource("SANS ISC", "https://isc.sans.edu/rssfeed_full.xml", Categories.COMMUNITY),
        RssSource("r/netsec", "https://www.reddit.com/r/netsec/.rss", Categories.COMMUNITY),
        RssSource("r/blueteamsec", "https://www.reddit.com/r/blueteamsec/.rss", Categories.COMMUNITY),
    )
}
