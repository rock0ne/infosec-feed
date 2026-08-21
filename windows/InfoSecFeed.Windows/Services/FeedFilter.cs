using InfoSecFeed.Windows.Models;

namespace InfoSecFeed.Windows.Services;

public static class FeedFilter
{
    public static IReadOnlyList<FeedItem> Apply(
        IEnumerable<FeedItem> items,
        string category,
        string query)
    {
        var normalizedQuery = query.Trim();
        return items.Where(item =>
                (category == Categories.All || item.Category == category) &&
                (normalizedQuery.Length == 0 ||
                 item.Title.Contains(normalizedQuery, StringComparison.OrdinalIgnoreCase) ||
                 item.Summary.Contains(normalizedQuery, StringComparison.OrdinalIgnoreCase) ||
                 item.Source.Contains(normalizedQuery, StringComparison.OrdinalIgnoreCase) ||
                 (item.Severity?.Contains(normalizedQuery, StringComparison.OrdinalIgnoreCase) ?? false)))
            .OrderByDescending(item => item.Rank)
            .ToList();
    }
}
