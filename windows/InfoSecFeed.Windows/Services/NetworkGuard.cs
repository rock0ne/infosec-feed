using System.Net;
using System.Net.Sockets;

namespace InfoSecFeed.Windows.Services;

public static class NetworkGuard
{
    public static async Task EnsurePublicHttpsAsync(Uri uri, CancellationToken cancellationToken = default)
    {
        if (uri.Scheme != Uri.UriSchemeHttps || !string.IsNullOrEmpty(uri.UserInfo) || string.IsNullOrWhiteSpace(uri.Host))
            throw new InvalidOperationException("Only credential-free public HTTPS destinations are allowed");

        if (IPAddress.TryParse(uri.Host, out var literal))
        {
            if (!IsPublicAddress(literal)) throw new InvalidOperationException("Private or local destinations are blocked");
            return;
        }

        if (uri.Host.Equals("localhost", StringComparison.OrdinalIgnoreCase) || uri.Host.EndsWith(".local", StringComparison.OrdinalIgnoreCase))
            throw new InvalidOperationException("Private or local destinations are blocked");

        var addresses = await Dns.GetHostAddressesAsync(uri.DnsSafeHost, cancellationToken);
        if (addresses.Length == 0 || addresses.Any(address => !IsPublicAddress(address)))
            throw new InvalidOperationException("Destination did not resolve exclusively to public addresses");
    }

    public static bool IsPublicAddress(IPAddress address)
    {
        if (IPAddress.IsLoopback(address) || address.Equals(IPAddress.Any) || address.Equals(IPAddress.IPv6Any) ||
            address.Equals(IPAddress.None) || address.Equals(IPAddress.IPv6None) || address.IsIPv6LinkLocal ||
            address.IsIPv6Multicast || address.IsIPv6SiteLocal) return false;

        if (address.AddressFamily == AddressFamily.InterNetworkV6)
        {
            var bytes = address.GetAddressBytes();
            if ((bytes[0] & 0xFE) == 0xFC) return false; // fc00::/7 unique local
            if (address.IsIPv4MappedToIPv6) return IsPublicAddress(address.MapToIPv4());
            return true;
        }

        if (address.AddressFamily != AddressFamily.InterNetwork) return false;
        var octets = address.GetAddressBytes();
        return octets[0] switch
        {
            0 or 10 or 127 => false,
            100 when octets[1] is >= 64 and <= 127 => false, // carrier-grade NAT
            169 when octets[1] == 254 => false,
            172 when octets[1] is >= 16 and <= 31 => false,
            192 when octets[1] == 168 => false,
            198 when octets[1] is 18 or 19 => false, // benchmark networks
            >= 224 => false,
            _ => true,
        };
    }
}
