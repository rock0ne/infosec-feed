using System.Text.Json.Serialization;

namespace InfoSecFeed.Windows.Models;

public sealed record FeedItem(
    string Id,
    string Title,
    string Summary,
    string Source,
    string Url,
    DateTimeOffset? Published,
    string? Severity = null,
    string Category = Categories.News,
    string? ImageUrl = null)
{
    [JsonIgnore]
    public long Rank => FeedRanker.Rank(this);
}

public static class Categories
{
    public const string All = "All";
    public const string Alerts = "Alerts";
    public const string Vulnerabilities = "Vulns";
    public const string Intelligence = "Threat Intel";
    public const string News = "News";
    public const string Research = "Research";
    public const string Community = "Community";

    public static IReadOnlyList<string> Ordered { get; } =
        [All, Alerts, Vulnerabilities, Intelligence, News, Research, Community];
}

public static class FeedRanker
{
    public static long Rank(FeedItem item)
    {
        if (item.Published is null) return long.MinValue;

        var offset = item.Severity?.ToUpperInvariant() switch
        {
            "EXPLOITED" => TimeSpan.FromHours(72),
            "CRITICAL" => TimeSpan.FromHours(18),
            "HIGH" => TimeSpan.FromHours(5),
            _ when item.Source == "GitHub" => TimeSpan.FromHours(-30),
            _ => TimeSpan.Zero,
        };

        return item.Published.Value.Add(offset).ToUnixTimeMilliseconds();
    }
}
