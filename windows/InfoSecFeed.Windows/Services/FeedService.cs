using System.Globalization;
using System.IO;
using System.IO.Compression;
using System.Net;
using System.Net.Http;
using System.Net.Http.Headers;
using System.Security.Cryptography;
using System.Text;
using System.Text.Json;
using System.Text.RegularExpressions;
using System.Xml;
using System.Xml.Linq;
using InfoSecFeed.Windows.Models;

namespace InfoSecFeed.Windows.Services;

public sealed partial class FeedService : IDisposable
{
    private const int MaxDocumentBytes = 12 * 1024 * 1024;
    private readonly HttpClient _http;
    private readonly SemaphoreSlim _networkGate = new(8, 8);
    private readonly SemaphoreSlim _redditGate = new(1, 1);

    public FeedService()
    {
        var handler = new HttpClientHandler
        {
            AllowAutoRedirect = false,
            AutomaticDecompression = DecompressionMethods.GZip | DecompressionMethods.Deflate | DecompressionMethods.Brotli,
        };
        _http = new HttpClient(handler) { Timeout = TimeSpan.FromSeconds(25) };
    }

    public async Task<IReadOnlyList<FeedItem>> RefreshAsync(CancellationToken cancellationToken = default)
    {
        var jobs = new List<Task<IReadOnlyList<FeedItem>>>
        {
            SafelyAsync("CISA KEV", FetchKevAsync, cancellationToken),
            SafelyAsync("NVD CRITICAL", token => FetchNvdAsync("CRITICAL", token), cancellationToken),
            SafelyAsync("NVD HIGH", token => FetchNvdAsync("HIGH", token), cancellationToken),
            SafelyAsync("GitHub", FetchGitHubAsync, cancellationToken),
        };

        jobs.AddRange(SourceCatalog.Rss.Select(source =>
            SafelyAsync(source.Name, token => FetchRssAsync(source, token), cancellationToken)));

        var batches = await Task.WhenAll(jobs);
        return batches.SelectMany(batch => batch)
            .Where(item => item.Title.Length > 0)
            .DistinctBy(item => item.Id)
            .OrderByDescending(item => item.Rank)
            .Take(500)
            .ToList();
    }

    private async Task<IReadOnlyList<FeedItem>> SafelyAsync(
        string label,
        Func<CancellationToken, Task<IReadOnlyList<FeedItem>>> operation,
        CancellationToken cancellationToken)
    {
        await _networkGate.WaitAsync(cancellationToken);
        try
        {
            if (label.StartsWith("r/", StringComparison.Ordinal))
            {
                await _redditGate.WaitAsync(cancellationToken);
                try
                {
                    var result = await operation(cancellationToken);
                    await Task.Delay(TimeSpan.FromSeconds(2.5), cancellationToken);
                    return result;
                }
                finally
                {
                    _redditGate.Release();
                }
            }

            return await operation(cancellationToken);
        }
        catch (OperationCanceledException) when (cancellationToken.IsCancellationRequested)
        {
            throw;
        }
        catch (Exception error)
        {
            System.Diagnostics.Debug.WriteLine($"Source failed: {label}: {error.Message}");
            return [];
        }
        finally
        {
            _networkGate.Release();
        }
    }

    private async Task<IReadOnlyList<FeedItem>> FetchKevAsync(CancellationToken cancellationToken)
    {
        using var document = JsonDocument.Parse(await DownloadTextAsync(SourceCatalog.CisaKev, "application/json", cancellationToken));
        if (!document.RootElement.TryGetProperty("vulnerabilities", out var vulnerabilities)) return [];

        var source = vulnerabilities.EnumerateArray().ToArray();
        return source.Skip(Math.Max(0, source.Length - 40)).Select(entry =>
        {
            var cve = Text(entry, "cveID");
            var vendor = Text(entry, "vendorProject");
            var product = Text(entry, "product");
            var due = Text(entry, "dueDate");
            var description = Text(entry, "shortDescription");
            return new FeedItem(
                $"kev:{cve}",
                $"{cve} — {Text(entry, "vulnerabilityName")}",
                $"{vendor} {product}  ·  remediate by {due}\n{description}".Trim(),
                "CISA KEV",
                $"https://nvd.nist.gov/vuln/detail/{Uri.EscapeDataString(cve)}",
                ParseDate(Text(entry, "dateAdded")),
                "EXPLOITED",
                Categories.Alerts);
        }).ToList();
    }

    private async Task<IReadOnlyList<FeedItem>> FetchNvdAsync(string severity, CancellationToken cancellationToken)
    {
        var now = DateTimeOffset.UtcNow;
        var from = now.AddDays(-7);
        var query = $"?pubStartDate={Uri.EscapeDataString(from.ToString("yyyy-MM-dd'T'HH:mm:ss.fff", CultureInfo.InvariantCulture))}" +
                    $"&pubEndDate={Uri.EscapeDataString(now.ToString("yyyy-MM-dd'T'HH:mm:ss.fff", CultureInfo.InvariantCulture))}" +
                    $"&cvssV3Severity={severity}&resultsPerPage=25";
        using var document = JsonDocument.Parse(await DownloadTextAsync(new Uri(SourceCatalog.Nvd + query), "application/json", cancellationToken));
        if (!document.RootElement.TryGetProperty("vulnerabilities", out var vulnerabilities)) return [];

        var output = new List<FeedItem>();
        foreach (var wrapper in vulnerabilities.EnumerateArray())
        {
            if (!wrapper.TryGetProperty("cve", out var cve)) continue;
            var id = Text(cve, "id");
            var description = "";
            if (cve.TryGetProperty("descriptions", out var descriptions))
            {
                foreach (var candidate in descriptions.EnumerateArray())
                {
                    if (Text(candidate, "lang") != "en") continue;
                    description = Text(candidate, "value");
                    break;
                }
            }
            output.Add(new FeedItem(
                $"nvd:{id}", id, Truncate(description, 400), "NVD",
                $"https://nvd.nist.gov/vuln/detail/{Uri.EscapeDataString(id)}",
                ParseDate(Text(cve, "published")), severity, Categories.Vulnerabilities));
        }
        return output;
    }

    private async Task<IReadOnlyList<FeedItem>> FetchGitHubAsync(CancellationToken cancellationToken)
    {
        var since = DateTimeOffset.UtcNow.AddDays(-7).ToString("yyyy-MM-dd", CultureInfo.InvariantCulture);
        var query = Uri.EscapeDataString($"topic:security pushed:>{since}");
        var uri = new Uri($"{SourceCatalog.GitHubSearch}?q={query}&sort=stars&order=desc&per_page=10");
        using var document = JsonDocument.Parse(await DownloadTextAsync(uri, "application/vnd.github+json", cancellationToken));
        if (!document.RootElement.TryGetProperty("items", out var items)) return [];

        return items.EnumerateArray().Select(item =>
        {
            var id = item.TryGetProperty("id", out var identifier) ? identifier.GetInt64() : 0;
            var stars = item.TryGetProperty("stargazers_count", out var count) ? count.GetInt32() : 0;
            return new FeedItem(
                $"gh:{id}", Text(item, "full_name"),
                $"★ {stars}  ·  {Truncate(Text(item, "description"), 300)}",
                "GitHub", Text(item, "html_url"), ParseDate(Text(item, "pushed_at")),
                null, Categories.Community);
        }).ToList();
    }

    private async Task<IReadOnlyList<FeedItem>> FetchRssAsync(FeedSource source, CancellationToken cancellationToken)
    {
        var raw = await DownloadTextAsync(source.Url, "application/rss+xml, application/atom+xml, application/xml, text/xml", cancellationToken);
        return ParseRss(raw, source);
    }

    public static IReadOnlyList<FeedItem> ParseRss(string raw, FeedSource source)
    {
        var body = raw.TrimStart('\uFEFF', ' ', '\n', '\r', '\t');
        var head = body[..Math.Min(200, body.Length)].ToLowerInvariant();
        if (head.StartsWith("<!doctype html") || head.StartsWith("<html"))
            throw new InvalidDataException("HTML was returned instead of a feed");

        var settings = new XmlReaderSettings
        {
            DtdProcessing = DtdProcessing.Prohibit,
            XmlResolver = null,
            MaxCharactersInDocument = MaxDocumentBytes,
        };
        using var stringReader = new StringReader(body);
        using var xmlReader = XmlReader.Create(stringReader, settings);
        var document = XDocument.Load(xmlReader, LoadOptions.None);

        return document.Descendants()
            .Where(node => node.Name.LocalName is "item" or "entry")
            .Take(12)
            .Select(node => ParseEntry(node, source))
            .Where(item => item is not null)
            .Cast<FeedItem>()
            .ToList();
    }

    private static FeedItem? ParseEntry(XElement entry, FeedSource source)
    {
        var title = Value(entry, "title").Trim();
        var linkElement = entry.Elements().FirstOrDefault(node => node.Name.LocalName == "link");
        var link = linkElement?.Attribute("href")?.Value ?? linkElement?.Value?.Trim() ?? "";
        if (title.Length == 0 || !Uri.TryCreate(link, UriKind.Absolute, out var articleUri) ||
            articleUri.Scheme is not ("https" or "http")) return null;

        var rawDescription = FirstValue(entry, "description", "summary", "encoded", "content");
        var published = ParseDate(FirstValue(entry, "pubDate", "published", "updated", "date"));
        var image = ImageUrl(entry, rawDescription);
        var id = $"rss:{Hash($"{source.Name}|{link}|{title}")}";

        return new FeedItem(
            id, WebUtility.HtmlDecode(StripHtml(title)),
            Truncate(WebUtility.HtmlDecode(StripHtml(rawDescription)), 500),
            source.Name, articleUri.ToString(), published, null, source.Category, image);
    }

    private async Task<string> DownloadTextAsync(Uri initialUri, string accept, CancellationToken cancellationToken)
    {
        var current = initialUri;
        for (var redirect = 0; redirect < 6; redirect++)
        {
            await NetworkGuard.EnsurePublicHttpsAsync(current, cancellationToken);

            using var request = new HttpRequestMessage(HttpMethod.Get, current);
            request.Headers.Accept.ParseAdd(accept);
            request.Headers.AcceptLanguage.ParseAdd("en-GB,en;q=0.9");
            request.Headers.UserAgent.ParseAdd(
                current.Host.Contains("reddit.com", StringComparison.OrdinalIgnoreCase)
                    ? "InfoSecFeed-Windows/1.0 (security feed reader; contact via GitHub)"
                    : "InfoSecFeed-Windows/1.0 (+https://github.com/rock0ne/infosec-feed)");

            using var response = await _http.SendAsync(request, HttpCompletionOption.ResponseHeadersRead, cancellationToken);
            if ((int)response.StatusCode is >= 300 and <= 399)
            {
                var location = response.Headers.Location ?? throw new HttpRequestException("Redirect without a Location header");
                var resolved = location.IsAbsoluteUri ? location : new Uri(current, location);
                current = resolved.Scheme == Uri.UriSchemeHttp
                    ? new UriBuilder(resolved) { Scheme = Uri.UriSchemeHttps, Port = -1 }.Uri
                    : resolved;
                continue;
            }

            response.EnsureSuccessStatusCode();
            if (response.Content.Headers.ContentLength > MaxDocumentBytes)
                throw new InvalidDataException("Feed exceeds the download limit");

            await using var input = await response.Content.ReadAsStreamAsync(cancellationToken);
            using var output = new MemoryStream();
            var buffer = new byte[16 * 1024];
            while (true)
            {
                var read = await input.ReadAsync(buffer, cancellationToken);
                if (read == 0) break;
                if (output.Length + read > MaxDocumentBytes)
                    throw new InvalidDataException("Feed exceeds the download limit");
                output.Write(buffer, 0, read);
            }
            return Encoding.UTF8.GetString(output.ToArray());
        }

        throw new HttpRequestException("Too many redirects");
    }

    private static string? ImageUrl(XElement entry, string html)
    {
        var enclosure = entry.DescendantsAndSelf().FirstOrDefault(node =>
            node.Name.LocalName == "enclosure" &&
            (node.Attribute("type")?.Value.StartsWith("image/", StringComparison.OrdinalIgnoreCase) ?? false));
        var candidate = enclosure?.Attribute("url")?.Value;

        candidate ??= entry.DescendantsAndSelf().FirstOrDefault(node =>
            node.Name.LocalName is "content" or "thumbnail" && node.Attribute("url") is not null)
            ?.Attribute("url")?.Value;
        candidate ??= HtmlImageRegex().Match(html) is { Success: true } match ? match.Groups[1].Value : null;

        if (!Uri.TryCreate(WebUtility.HtmlDecode(candidate), UriKind.Absolute, out var uri)) return null;
        if (uri.Scheme == Uri.UriSchemeHttp)
            uri = new UriBuilder(uri) { Scheme = Uri.UriSchemeHttps, Port = -1 }.Uri;
        return uri.Scheme == Uri.UriSchemeHttps ? uri.ToString() : null;
    }

    private static string Text(JsonElement element, string property) =>
        element.TryGetProperty(property, out var value) && value.ValueKind != JsonValueKind.Null
            ? value.ToString()
            : "";

    private static string Value(XElement element, string name) =>
        element.Elements().FirstOrDefault(node => node.Name.LocalName == name)?.Value ?? "";

    private static string FirstValue(XElement element, params string[] names)
    {
        foreach (var name in names)
        {
            var value = Value(element, name);
            if (!string.IsNullOrWhiteSpace(value)) return value;
        }
        return "";
    }

    public static DateTimeOffset? ParseDate(string value)
    {
        if (string.IsNullOrWhiteSpace(value)) return null;
        if (DateTimeOffset.TryParse(value, CultureInfo.InvariantCulture,
                DateTimeStyles.AllowWhiteSpaces | DateTimeStyles.AssumeUniversal | DateTimeStyles.AdjustToUniversal,
                out var parsed)) return parsed;

        string[] patterns = ["dd MMM yy HH:mm:ss zzz", "MMM dd, yyyy HH:mm:ssK", "yyyy-MM-dd"];
        return DateTimeOffset.TryParseExact(value, patterns, CultureInfo.InvariantCulture,
            DateTimeStyles.AllowWhiteSpaces | DateTimeStyles.AssumeUniversal | DateTimeStyles.AdjustToUniversal,
            out parsed) ? parsed : null;
    }

    private static string Hash(string input) =>
        Convert.ToHexString(SHA256.HashData(Encoding.UTF8.GetBytes(input)))[..20].ToLowerInvariant();

    private static string StripHtml(string value) =>
        WhitespaceRegex().Replace(TagRegex().Replace(value, " "), " ").Trim();

    private static string Truncate(string value, int length) => value.Length <= length ? value : value[..length].TrimEnd() + "…";

    [GeneratedRegex("<img[^>]+src=[\\\"']([^\\\"']+)[\\\"']", RegexOptions.IgnoreCase | RegexOptions.CultureInvariant)]
    private static partial Regex HtmlImageRegex();

    [GeneratedRegex("<[^>]+>", RegexOptions.CultureInvariant)]
    private static partial Regex TagRegex();

    [GeneratedRegex("\\s+", RegexOptions.CultureInvariant)]
    private static partial Regex WhitespaceRegex();

    public void Dispose()
    {
        _http.Dispose();
        _networkGate.Dispose();
        _redditGate.Dispose();
    }
}
