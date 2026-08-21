using System.IO;
using System.Text.Json;
using InfoSecFeed.Windows.Models;

namespace InfoSecFeed.Windows.Services;

public sealed record UserSettings(
    AlertMode AlertMode = AlertMode.Off,
    int RefreshMinutes = 30,
    double TextScale = DisplayPreferences.DefaultTextScale,
    bool OpenMaximized = false);

public sealed class AppStorage
{
    private static readonly JsonSerializerOptions JsonOptions = new()
    {
        PropertyNamingPolicy = JsonNamingPolicy.CamelCase,
        WriteIndented = false,
    };

    private readonly string _directory = Path.Combine(
        Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData),
        "CyberTecPro", "InfoSecFeed");

    private string FeedPath => Path.Combine(_directory, "feed-cache.json");
    private string SettingsPath => Path.Combine(_directory, "settings.json");
    private string SeenPath => Path.Combine(_directory, "seen-alerts.json");

    public bool HasSeenState => File.Exists(SeenPath);

    public async Task<IReadOnlyList<FeedItem>> LoadFeedAsync(CancellationToken cancellationToken = default) =>
        await LoadAsync<List<FeedItem>>(FeedPath, cancellationToken) ?? [];

    public Task SaveFeedAsync(IReadOnlyList<FeedItem> items, CancellationToken cancellationToken = default) =>
        SaveAtomicAsync(FeedPath, items, cancellationToken);

    public async Task<UserSettings> LoadSettingsAsync(CancellationToken cancellationToken = default)
    {
        var settings = await LoadAsync<UserSettings>(SettingsPath, cancellationToken) ?? new UserSettings();
        return settings with
        {
            TextScale = DisplayPreferences.NormalizeTextScale(settings.TextScale),
            RefreshMinutes = Math.Clamp(settings.RefreshMinutes, 5, 240),
        };
    }

    public Task SaveSettingsAsync(UserSettings settings, CancellationToken cancellationToken = default) =>
        SaveAtomicAsync(SettingsPath, settings, cancellationToken);

    public async Task<HashSet<string>> LoadSeenIdsAsync(CancellationToken cancellationToken = default) =>
        await LoadAsync<HashSet<string>>(SeenPath, cancellationToken) ?? [];

    public Task SaveSeenIdsAsync(IReadOnlySet<string> ids, CancellationToken cancellationToken = default) =>
        SaveAtomicAsync(SeenPath, ids.Order(StringComparer.Ordinal).ToArray(), cancellationToken);

    private static async Task<T?> LoadAsync<T>(string path, CancellationToken cancellationToken)
    {
        if (!File.Exists(path)) return default;
        try
        {
            await using var stream = File.OpenRead(path);
            return await JsonSerializer.DeserializeAsync<T>(stream, JsonOptions, cancellationToken);
        }
        catch (JsonException)
        {
            return default;
        }
        catch (IOException)
        {
            return default;
        }
    }

    private async Task SaveAtomicAsync<T>(string path, T value, CancellationToken cancellationToken)
    {
        Directory.CreateDirectory(_directory);
        var temporary = path + ".tmp";
        await using (var stream = new FileStream(temporary, FileMode.Create, FileAccess.Write, FileShare.None))
        {
            await JsonSerializer.SerializeAsync(stream, value, JsonOptions, cancellationToken);
            await stream.FlushAsync(cancellationToken);
        }
        File.Move(temporary, path, true);
    }
}
