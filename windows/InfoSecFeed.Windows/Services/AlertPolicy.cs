using InfoSecFeed.Windows.Models;

namespace InfoSecFeed.Windows.Services;

public enum AlertMode
{
    Off,
    KevOnly,
    KevAndCritical,
}

public static class AlertPolicy
{
    public static IReadOnlyList<FeedItem> SelectNew(
        IEnumerable<FeedItem> items,
        IReadOnlySet<string> seenIds,
        AlertMode mode,
        int limit = 5) =>
        items
            .Where(item => Accepts(item, mode) && !seenIds.Contains(item.Id))
            .OrderByDescending(item => item.Severity == "EXPLOITED")
            .ThenByDescending(item => item.Published)
            .Take(limit)
            .ToList();

    public static HashSet<string> AlertIds(IEnumerable<FeedItem> items, AlertMode mode) =>
        items.Where(item => Accepts(item, mode)).Select(item => item.Id).ToHashSet();

    private static bool Accepts(FeedItem item, AlertMode mode) => mode switch
    {
        AlertMode.Off => false,
        AlertMode.KevOnly => item.Severity == "EXPLOITED",
        AlertMode.KevAndCritical => item.Severity is "EXPLOITED" or "CRITICAL",
        _ => false,
    };
}
