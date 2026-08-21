using System.IO;
using System.Net;
using System.Net.Http;
using System.Windows.Media.Imaging;

namespace InfoSecFeed.Windows.Services;

public sealed class ImageService : IDisposable
{
    private const int MaxImageBytes = 4 * 1024 * 1024;
    private readonly HttpClient _http = new(new HttpClientHandler
    {
        AutomaticDecompression = DecompressionMethods.GZip | DecompressionMethods.Deflate | DecompressionMethods.Brotli,
    })
    {
        Timeout = TimeSpan.FromSeconds(15),
    };
    private readonly Dictionary<string, BitmapSource?> _memory = new(StringComparer.Ordinal);
    private readonly SemaphoreSlim _gate = new(4, 4);

    public async Task<BitmapSource?> LoadAsync(string? url, CancellationToken cancellationToken = default)
    {
        if (!Uri.TryCreate(url, UriKind.Absolute, out var uri) || uri.Scheme != Uri.UriSchemeHttps) return null;
        lock (_memory)
        {
            if (_memory.TryGetValue(uri.AbsoluteUri, out var cached)) return cached;
        }

        await _gate.WaitAsync(cancellationToken);
        try
        {
            lock (_memory)
            {
                if (_memory.TryGetValue(uri.AbsoluteUri, out var cached)) return cached;
            }

            await NetworkGuard.EnsurePublicHttpsAsync(uri, cancellationToken);
            using var response = await _http.GetAsync(uri, HttpCompletionOption.ResponseHeadersRead, cancellationToken);
            response.EnsureSuccessStatusCode();
            if (response.RequestMessage?.RequestUri?.Scheme != Uri.UriSchemeHttps ||
                response.Content.Headers.ContentLength > MaxImageBytes) return Remember(uri, null);

            await using var input = await response.Content.ReadAsStreamAsync(cancellationToken);
            using var output = new MemoryStream();
            var buffer = new byte[16 * 1024];
            while (true)
            {
                var read = await input.ReadAsync(buffer, cancellationToken);
                if (read == 0) break;
                if (output.Length + read > MaxImageBytes) return Remember(uri, null);
                output.Write(buffer, 0, read);
            }
            output.Position = 0;

            var bitmap = new BitmapImage();
            bitmap.BeginInit();
            bitmap.CacheOption = BitmapCacheOption.OnLoad;
            bitmap.DecodePixelWidth = 900;
            bitmap.StreamSource = output;
            bitmap.EndInit();
            bitmap.Freeze();
            return Remember(uri, bitmap);
        }
        catch (Exception error) when (error is HttpRequestException or IOException or NotSupportedException)
        {
            return Remember(uri, null);
        }
        finally
        {
            _gate.Release();
        }
    }

    private BitmapSource? Remember(Uri uri, BitmapSource? image)
    {
        lock (_memory)
        {
            if (_memory.Count >= 80) _memory.Remove(_memory.Keys.First());
            _memory[uri.AbsoluteUri] = image;
        }
        return image;
    }

    public void Dispose()
    {
        _http.Dispose();
        _gate.Dispose();
    }
}
