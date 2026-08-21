using InfoSecFeed.Windows.Models;

namespace InfoSecFeed.Windows.Services;

public static class SourceCatalog
{
    public static readonly Uri CisaKev = new(
        "https://www.cisa.gov/sites/default/files/feeds/known_exploited_vulnerabilities.json");
    public static readonly Uri Nvd = new("https://services.nvd.nist.gov/rest/json/cves/2.0");
    public static readonly Uri GitHubSearch = new("https://api.github.com/search/repositories");

    public static IReadOnlyList<FeedSource> Rss { get; } =
    [
        new("CISA Advisories", U("https://www.cisa.gov/cybersecurity-advisories/all.xml"), Categories.Alerts),
        new("NCSC UK", U("https://www.ncsc.gov.uk/api/1/services/v1/report-rss-feed.xml"), Categories.Alerts),
        new("MSRC Update Guide", U("https://api.msrc.microsoft.com/update-guide/rss"), Categories.Alerts),
        new("Zero Day Initiative", U("https://www.zerodayinitiative.com/rss/published/"), Categories.Alerts),
        new("JPCERT", U("https://www.jpcert.or.jp/english/rss/jpcert-en.rdf"), Categories.Alerts),

        new("Full Disclosure", U("https://seclists.org/rss/fulldisclosure.rss"), Categories.Vulnerabilities),
        new("Exploit-DB", U("https://www.exploit-db.com/rss.xml"), Categories.Vulnerabilities),

        new("Cisco Talos", U("https://blog.talosintelligence.com/rss/"), Categories.Intelligence),
        new("Unit 42", U("https://unit42.paloaltonetworks.com/feed/"), Categories.Intelligence),
        new("Microsoft Security", U("https://www.microsoft.com/en-us/security/blog/feed/"), Categories.Intelligence),
        new("CrowdStrike", U("https://www.crowdstrike.com/blog/feed/"), Categories.Intelligence),
        new("SentinelOne Labs", U("https://www.sentinelone.com/labs/feed/"), Categories.Intelligence),
        new("WeLiveSecurity", U("https://www.welivesecurity.com/en/rss/feed/"), Categories.Intelligence),
        new("Check Point Research", U("https://research.checkpoint.com/feed/"), Categories.Intelligence),
        new("Trend Micro", U("https://feeds.trendmicro.com/TrendMicroResearch"), Categories.Intelligence),
        new("Huntress", U("https://www.huntress.com/blog/rss.xml"), Categories.Intelligence),
        new("Rapid7", U("https://blog.rapid7.com/rss/"), Categories.Intelligence),
        new("Red Canary", U("https://redcanary.com/feed/"), Categories.Intelligence),
        new("Malwarebytes Labs", U("https://www.malwarebytes.com/blog/feed/index.xml"), Categories.Intelligence),
        new("Elastic Security Labs", U("https://www.elastic.co/security-labs/rss/feed.xml"), Categories.Intelligence),
        new("Bitdefender Labs", U("https://www.bitdefender.com/blog/api/rss/labs/"), Categories.Intelligence),
        new("Objective-See", U("https://objective-see.org/rss.xml"), Categories.Intelligence),

        new("The Hacker News", U("https://feeds.feedburner.com/TheHackersNews"), Categories.News),
        new("BleepingComputer", U("https://www.bleepingcomputer.com/feed/"), Categories.News),
        new("The Record", U("https://therecord.media/feed"), Categories.News),
        new("SecurityWeek", U("https://www.securityweek.com/feed/"), Categories.News),
        new("Dark Reading", U("https://www.darkreading.com/rss.xml"), Categories.News),
        new("Infosecurity Mag", U("https://www.infosecurity-magazine.com/rss/news/"), Categories.News),
        new("CyberScoop", U("https://cyberscoop.com/feed/"), Categories.News),
        new("The Register", U("https://www.theregister.com/security/headlines.atom"), Categories.News),
        new("Help Net Security", U("https://www.helpnetsecurity.com/feed/"), Categories.News),
        new("Krebs on Security", U("https://krebsonsecurity.com/feed/"), Categories.News),
        new("Graham Cluley", U("https://grahamcluley.com/feed/"), Categories.News),

        new("Project Zero", U("https://googleprojectzero.blogspot.com/feeds/posts/default?alt=rss"), Categories.Research),
        new("Google Security", U("https://security.googleblog.com/feeds/posts/default?alt=rss"), Categories.Research),
        new("PortSwigger Research", U("https://portswigger.net/research/rss"), Categories.Research),
        new("watchTowr Labs", U("https://labs.watchtowr.com/rss/"), Categories.Research),
        new("Cloudflare", U("https://blog.cloudflare.com/rss/"), Categories.Research),
        new("Schneier", U("https://www.schneier.com/feed/atom/"), Categories.Research),
        new("Troy Hunt", U("https://www.troyhunt.com/rss/"), Categories.Research),
        new("Trail of Bits", U("https://blog.trailofbits.com/feed/"), Categories.Research),
        new("Qualys", U("https://blog.qualys.com/feed"), Categories.Research),
        new("Tenable", U("https://www.tenable.com/blog/feed"), Categories.Research),
        new("Wiz", U("https://www.wiz.io/blog/rss.xml"), Categories.Research),
        new("Snyk", U("https://snyk.io/blog/feed/"), Categories.Research),
        new("Horizon3", U("https://www.horizon3.ai/feed/"), Categories.Research),
        new("Assetnote", U("https://blog.assetnote.io/feed.xml"), Categories.Research),

        new("SANS ISC", U("https://isc.sans.edu/rssfeed_full.xml"), Categories.Community),
        new("r/netsec", U("https://www.reddit.com/r/netsec/.rss"), Categories.Community),
        new("r/blueteamsec", U("https://www.reddit.com/r/blueteamsec/.rss"), Categories.Community),
    ];

    private static Uri U(string value) => new(value, UriKind.Absolute);
}
