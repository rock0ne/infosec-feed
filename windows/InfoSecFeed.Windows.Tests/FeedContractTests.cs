using System.Xml;
using System.Net;
using InfoSecFeed.Windows.Models;
using InfoSecFeed.Windows.Services;
using Xunit;

namespace InfoSecFeed.Windows.Tests;

public sealed class FeedContractTests
{
    private static readonly DateTimeOffset Now = new(2026, 8, 21, 0, 0, 0, TimeSpan.Zero);

    [Fact]
    public void Catalog_has_the_mobile_roster_and_https_only()
    {
        Assert.Equal(50, SourceCatalog.Rss.Count);
        Assert.All(SourceCatalog.Rss, source => Assert.Equal(Uri.UriSchemeHttps, source.Url.Scheme));
        Assert.Equal(6, SourceCatalog.Rss.Select(source => source.Category).Distinct().Count());
    }

    [Fact]
    public void Ranking_prioritises_exploited_and_sends_undated_last()
    {
        var normal = Item("normal", Now);
        var exploited = Item("exploited", Now.AddHours(-50), "EXPLOITED");
        var undated = Item("undated", null);

        Assert.True(exploited.Rank > normal.Rank);
        Assert.Equal(long.MinValue, undated.Rank);
    }

    [Fact]
    public void GitHub_activity_is_demoted()
    {
        var news = Item("news", Now.AddHours(-20));
        var github = Item("github", Now, source: "GitHub");
        Assert.True(news.Rank > github.Rank);
    }

    [Fact]
    public void Alerts_are_opt_in_new_and_high_signal_only()
    {
        var kev = Item("kev", Now, "EXPLOITED");
        var critical = Item("critical", Now, "CRITICAL");
        var news = Item("news", Now);

        Assert.Empty(AlertPolicy.SelectNew([kev, critical], new HashSet<string>(), AlertMode.Off));
        Assert.Equal([kev], AlertPolicy.SelectNew([kev, critical, news], new HashSet<string>(), AlertMode.KevOnly));
        Assert.Equal([critical], AlertPolicy.SelectNew([kev, critical], new HashSet<string> { "kev" }, AlertMode.KevAndCritical));
    }

    [Fact]
    public void Search_and_category_filters_compose()
    {
        var cve = Item("CVE-2026-1234", Now, "CRITICAL", category: Categories.Vulnerabilities);
        var story = Item("AI threat research", Now, category: Categories.Research);
        var filtered = FeedFilter.Apply([cve, story], Categories.Vulnerabilities, "2026-1234");
        Assert.Equal([cve], filtered);
    }

    [Fact]
    public void Rss_parser_reads_atom_and_html_image_without_rendering_html()
    {
        const string xml = """
            <feed xmlns="http://www.w3.org/2005/Atom">
              <entry>
                <title>Security &amp; update</title>
                <link href="https://example.com/story" />
                <updated>2026-08-20T12:00:00Z</updated>
                <summary><![CDATA[<p>Investigation details</p><img src="https://example.com/image.jpg">]]></summary>
              </entry>
            </feed>
            """;
        var source = new FeedSource("Example", new Uri("https://example.com/feed"), Categories.Research);
        var result = FeedService.ParseRss(xml, source);

        var item = Assert.Single(result);
        Assert.Equal("Security & update", item.Title);
        Assert.Equal("Investigation details", item.Summary);
        Assert.Equal("https://example.com/image.jpg", item.ImageUrl);
        Assert.Equal(new DateTimeOffset(2026, 8, 20, 12, 0, 0, TimeSpan.Zero), item.Published);
    }

    [Fact]
    public void Rss_parser_prohibits_document_types()
    {
        const string xml = "<!DOCTYPE rss [<!ENTITY xxe SYSTEM 'file:///c:/windows/win.ini'>]><rss><channel><item><title>&xxe;</title></item></channel></rss>";
        var source = new FeedSource("Example", new Uri("https://example.com/feed"), Categories.News);
        Assert.Throws<XmlException>(() => FeedService.ParseRss(xml, source));
    }

    [Theory]
    [InlineData("19 Aug 26 12:00:00 +0000")]
    [InlineData("Aug 19, 2026 12:00:00+00:00")]
    [InlineData("2026-08-19T12:00:00Z")]
    public void Dates_used_by_publishers_remain_parseable(string value) =>
        Assert.NotNull(FeedService.ParseDate(value));

    [Theory]
    [InlineData("127.0.0.1")]
    [InlineData("10.0.0.1")]
    [InlineData("172.16.10.1")]
    [InlineData("192.168.1.20")]
    [InlineData("169.254.10.4")]
    [InlineData("100.64.0.1")]
    [InlineData("224.0.0.1")]
    [InlineData("::1")]
    [InlineData("fd00::1")]
    public void Private_and_local_addresses_are_blocked(string value) =>
        Assert.False(NetworkGuard.IsPublicAddress(IPAddress.Parse(value)));

    [Theory]
    [InlineData("1.1.1.1")]
    [InlineData("8.8.8.8")]
    [InlineData("2606:4700:4700::1111")]
    public void Public_addresses_are_allowed(string value) =>
        Assert.True(NetworkGuard.IsPublicAddress(IPAddress.Parse(value)));

    [Theory]
    [InlineData(0, DisplayPreferences.DefaultTextScale)]
    [InlineData(double.NaN, DisplayPreferences.DefaultTextScale)]
    [InlineData(0.7, DisplayPreferences.MinimumTextScale)]
    [InlineData(2.0, DisplayPreferences.MaximumTextScale)]
    [InlineData(1.3, 1.3)]
    public void Text_scale_is_migrated_and_bounded(double requested, double expected) =>
        Assert.Equal(expected, DisplayPreferences.NormalizeTextScale(requested));

    [Fact]
    public void Text_scale_steps_and_label_are_stable()
    {
        Assert.Equal(1.25, DisplayPreferences.Increase(DisplayPreferences.DefaultTextScale));
        Assert.Equal(1.05, DisplayPreferences.Decrease(DisplayPreferences.DefaultTextScale));
        Assert.Equal("115%", DisplayPreferences.Percentage(DisplayPreferences.DefaultTextScale));
    }

    private static FeedItem Item(
        string id,
        DateTimeOffset? published,
        string? severity = null,
        string source = "Example",
        string category = Categories.News) =>
        new(id, id, id, source, "https://example.com", published, severity, category);
}
