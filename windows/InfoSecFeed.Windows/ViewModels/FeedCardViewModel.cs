using System.Diagnostics;
using System.Windows.Media.Imaging;
using InfoSecFeed.Windows.Models;

namespace InfoSecFeed.Windows.ViewModels;

public sealed class FeedCardViewModel(FeedItem item) : BindableBase
{
    private BitmapSource? _image;

    public FeedItem Item { get; } = item;
    public string Title => Item.Title;
    public string Summary => Item.Summary;
    public string Source => Item.Source;
    public string Category => Item.Category;
    public string? Severity => Item.Severity;
    public bool HasSeverity => !string.IsNullOrWhiteSpace(Item.Severity);
    public string Age => RelativeAge(Item.Published);
    public BitmapSource? Image { get => _image; set => Set(ref _image, value); }
    public bool HasImage => Image is not null;

    public RelayCommand OpenCommand { get; } = new(_ => Open(item.Url));

    private static void Open(string value)
    {
        if (!Uri.TryCreate(value, UriKind.Absolute, out var uri) || uri.Scheme is not ("https" or "http")) return;
        Process.Start(new ProcessStartInfo(uri.AbsoluteUri) { UseShellExecute = true });
    }

    private static string RelativeAge(DateTimeOffset? published)
    {
        if (published is null) return "Undated";
        var age = DateTimeOffset.UtcNow - published.Value;
        if (age < TimeSpan.Zero) age = TimeSpan.Zero;
        if (age < TimeSpan.FromMinutes(1)) return "Just now";
        if (age < TimeSpan.FromHours(1)) return $"{(int)age.TotalMinutes}m";
        if (age < TimeSpan.FromDays(1)) return $"{(int)age.TotalHours}h";
        if (age < TimeSpan.FromDays(30)) return $"{(int)age.TotalDays}d";
        return published.Value.ToLocalTime().ToString("dd MMM yyyy");
    }
}
