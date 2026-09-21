// SPDX-License-Identifier: AGPL-3.0-or-later
using System.Net;
using System.Text;
using System.Text.Json;

namespace Netsira.Desktop.Devices;

public sealed record AirOsSnapshot(
    int ApiVersion,
    string Model,
    string? Hostname,
    string? Firmware,
    double? SignalDbm,
    double? NoiseDbm,
    double? SnrDb,
    string? ChainRssi,
    double? FrequencyMHz,
    double? ChannelWidthMHz,
    double? TxPowerDbm,
    double? DistanceMeters,
    string? TxRate,
    string? RxRate,
    double? EthernetSpeedMbps,
    bool? EthernetFullDuplex,
    double? CpuLoadPercent,
    long? TotalRamBytes,
    long? FreeRamBytes);

public sealed class AirOsClient : IDisposable
{
    private readonly HttpClient _http;
    private string? _csrf;
    private Uri? _baseUri;
    private int? _apiVersion;

    public AirOsClient(bool allowInvalidCertificate = false)
    {
        var handler = new HttpClientHandler
        {
            AllowAutoRedirect = false,
            CookieContainer = new CookieContainer(),
            UseCookies = true
        };
        if (allowInvalidCertificate)
            handler.ServerCertificateCustomValidationCallback = HttpClientHandler.DangerousAcceptAnyServerCertificateValidator;

        _http = new HttpClient(handler) { Timeout = TimeSpan.FromSeconds(10) };
        _http.DefaultRequestHeaders.UserAgent.ParseAdd("Netsira/0.3");
    }

    public bool IsAuthenticated => _baseUri is not null && _apiVersion is not null;

    public async Task LoginAsync(
        string baseUrl,
        string username,
        string password,
        CancellationToken cancellationToken = default)
    {
        _baseUri = NormalizeBaseUri(baseUrl);
        _csrf = null;
        _apiVersion = await TryLoginV8Async(_baseUri, username, password, cancellationToken)
            ? 8
            : await LoginV6Async(_baseUri, username, password, cancellationToken);
    }

    public async Task<AirOsSnapshot> ReadStatusAsync(CancellationToken cancellationToken = default)
    {
        if (_baseUri is null || _apiVersion is null)
            throw new InvalidOperationException("airOS client is not authenticated.");

        using var request = new HttpRequestMessage(HttpMethod.Get, new Uri(_baseUri, "status.cgi"));
        if (!string.IsNullOrWhiteSpace(_csrf))
            request.Headers.TryAddWithoutValidation("X-CSRF-ID", _csrf);

        using var response = await _http.SendAsync(request, cancellationToken);
        if (response.StatusCode is HttpStatusCode.Unauthorized or HttpStatusCode.Forbidden)
            throw new UnauthorizedAccessException("airOS session expired or status access was rejected.");
        response.EnsureSuccessStatusCode();

        await using var stream = await response.Content.ReadAsStreamAsync(cancellationToken);
        using var doc = await JsonDocument.ParseAsync(stream, cancellationToken: cancellationToken);
        return ParseSnapshot(_apiVersion.Value, doc.RootElement);
    }

    public async Task<AirOsSnapshot> ConnectAndReadAsync(
        string baseUrl,
        string username,
        string password,
        CancellationToken cancellationToken = default)
    {
        await LoginAsync(baseUrl, username, password, cancellationToken);
        return await ReadStatusAsync(cancellationToken);
    }

    private async Task<bool> TryLoginV8Async(Uri baseUri, string username, string password, CancellationToken ct)
    {
        var payload = "{\"username\":\"" + JsonEncodedText.Encode(username) +
            "\",\"password\":\"" + JsonEncodedText.Encode(password) + "\"}";
        using var response = await _http.PostAsync(
            new Uri(baseUri, "api/auth"),
            new StringContent(payload, Encoding.UTF8, "application/json"),
            ct);

        if (response.StatusCode == HttpStatusCode.NotFound) return false;
        if (response.StatusCode is HttpStatusCode.Unauthorized or HttpStatusCode.Forbidden)
            throw new UnauthorizedAccessException("Invalid airOS username or password.");

        response.EnsureSuccessStatusCode();
        _csrf = response.Headers.TryGetValues("X-CSRF-ID", out var values) ? values.FirstOrDefault() : null;
        return true;
    }

    private async Task<int> LoginV6Async(Uri baseUri, string username, string password, CancellationToken ct)
    {
        var loginUri = new Uri(baseUri, "login.cgi");
        using (var initial = await _http.GetAsync(loginUri, ct))
            initial.EnsureSuccessStatusCode();

        using var post = new HttpRequestMessage(HttpMethod.Post, loginUri)
        {
            Content = new FormUrlEncodedContent(new Dictionary<string, string>
            {
                ["username"] = username,
                ["password"] = password,
                ["uri"] = "/index.cgi"
            })
        };
        post.Headers.Referrer = loginUri;
        post.Headers.TryAddWithoutValidation("Origin", baseUri.GetLeftPart(UriPartial.Authority));

        using var login = await _http.SendAsync(post, ct);
        if ((int)login.StatusCode is not (301 or 302 or 303 or 307 or 308))
            throw new UnauthorizedAccessException("airOS 6 login failed.");

        using var activate = await _http.GetAsync(new Uri(baseUri, "index.cgi"), ct);
        if (IsLoginRedirect(activate))
            throw new UnauthorizedAccessException("airOS 6 session activation failed.");
        if (!activate.IsSuccessStatusCode && !IsRedirect(activate.StatusCode))
            activate.EnsureSuccessStatusCode();

        return 6;
    }

    private static bool IsRedirect(HttpStatusCode code) => (int)code is 301 or 302 or 303 or 307 or 308;

    private static bool IsLoginRedirect(HttpResponseMessage response) =>
        IsRedirect(response.StatusCode) &&
        response.Headers.Location?.ToString().Contains("login.cgi", StringComparison.OrdinalIgnoreCase) == true;

    private static Uri NormalizeBaseUri(string value)
    {
        var trimmed = value.Trim();
        if (!trimmed.Contains("://", StringComparison.Ordinal)) trimmed = "http://" + trimmed;
        var uri = new Uri(trimmed, UriKind.Absolute);
        if (uri.Scheme is not ("http" or "https")) throw new ArgumentException("Only HTTP or HTTPS is supported.");
        var builder = new UriBuilder(uri) { Path = "/", Query = "", Fragment = "" };
        return builder.Uri;
    }

    private static AirOsSnapshot ParseSnapshot(int apiVersion, JsonElement root)
    {
        TryGet(root, out var host, "host");
        TryGet(root, out var wireless, "wireless");

        var station = FirstArrayItem(wireless, "sta");
        var remote = station.HasValue && TryGet(station.Value, out var remoteValue, "remote") ? remoteValue : (JsonElement?)null;

        var signal = Number(wireless, "signal") ?? Number(station, "signal") ?? Number(remote, "signal");
        var noise = Number(wireless, "noisef") ?? Number(station, "noisefloor") ?? Number(remote, "noisefloor");
        double? snr = signal.HasValue && noise.HasValue ? signal.Value - noise.Value : null;

        var chain = ArrayNumbers(station, "chainrssi") ?? ArrayNumbers(remote, "chainrssi");
        var ethernet = FirstInterfaceWithStatus(root);

        return new AirOsSnapshot(
            ApiVersion: apiVersion,
            Model: Text(host, "devmodel") ?? "Unknown",
            Hostname: Text(host, "hostname"),
            Firmware: Text(host, "fwversion"),
            SignalDbm: signal,
            NoiseDbm: noise,
            SnrDb: snr,
            ChainRssi: chain,
            FrequencyMHz: Number(wireless, "frequency"),
            ChannelWidthMHz: Number(wireless, "chanbw"),
            TxPowerDbm: Number(wireless, "txpower"),
            DistanceMeters: Number(wireless, "distance") ?? Number(station, "distance") ?? Number(remote, "distance"),
            TxRate: Text(wireless, "txrate"),
            RxRate: Text(wireless, "rxrate"),
            EthernetSpeedMbps: Number(ethernet, "speed"),
            EthernetFullDuplex: Boolean(ethernet, "duplex"),
            CpuLoadPercent: Number(host, "cpuload"),
            TotalRamBytes: Integer(host, "totalram"),
            FreeRamBytes: Integer(host, "freeram"));
    }

    private static JsonElement? FirstInterfaceWithStatus(JsonElement root)
    {
        if (!TryGet(root, out var interfaces, "interfaces") || interfaces.ValueKind != JsonValueKind.Array) return null;
        foreach (var item in interfaces.EnumerateArray())
        {
            if (TryGet(item, out var status, "status") && status.ValueKind == JsonValueKind.Object)
                return status;
        }
        return null;
    }

    private static JsonElement? FirstArrayItem(JsonElement? parent, string name)
    {
        if (!parent.HasValue || !TryGet(parent.Value, out var array, name) || array.ValueKind != JsonValueKind.Array) return null;
        foreach (var item in array.EnumerateArray()) return item;
        return null;
    }

    private static bool TryGet(JsonElement root, out JsonElement value, string name)
    {
        if (root.ValueKind == JsonValueKind.Object && root.TryGetProperty(name, out value)) return true;
        value = default;
        return false;
    }

    private static double? Number(JsonElement? parent, string name)
    {
        if (!parent.HasValue || !TryGet(parent.Value, out var value, name)) return null;
        if (value.ValueKind == JsonValueKind.Number && value.TryGetDouble(out var number)) return number;
        if (value.ValueKind == JsonValueKind.String && double.TryParse(value.GetString()?.Split(' ')[0], out number)) return number;
        return null;
    }

    private static long? Integer(JsonElement? parent, string name)
    {
        if (!parent.HasValue || !TryGet(parent.Value, out var value, name)) return null;
        if (value.ValueKind == JsonValueKind.Number && value.TryGetInt64(out var number)) return number;
        if (value.ValueKind == JsonValueKind.String && long.TryParse(value.GetString()?.Split(' ')[0], out number)) return number;
        return null;
    }

    private static string? Text(JsonElement? parent, string name)
    {
        if (!parent.HasValue || !TryGet(parent.Value, out var value, name)) return null;
        return value.ValueKind == JsonValueKind.String ? value.GetString() : value.ToString();
    }

    private static bool? Boolean(JsonElement? parent, string name)
    {
        if (!parent.HasValue || !TryGet(parent.Value, out var value, name)) return null;
        return value.ValueKind switch
        {
            JsonValueKind.True => true,
            JsonValueKind.False => false,
            JsonValueKind.Number when value.TryGetInt32(out var n) => n != 0,
            _ => null
        };
    }

    private static string? ArrayNumbers(JsonElement? parent, string name)
    {
        if (!parent.HasValue || !TryGet(parent.Value, out var value, name) || value.ValueKind != JsonValueKind.Array) return null;
        return string.Join(", ", value.EnumerateArray().Select(v => v.ToString()));
    }

    public void Dispose() => _http.Dispose();
}
