// SPDX-License-Identifier: AGPL-3.0-or-later
using System.Diagnostics;
using System.Net.Http.Headers;
using System.Net.WebSockets;
using System.Security.Cryptography;
using System.Text.Json;

namespace Netsira.Desktop.Diagnostics;

public sealed record Ndt7Result(double DownloadMbps, double UploadMbps, string Machine, string? City, string? Country);

public sealed class MlabNdt7Client
{
    private const string LocateUrl = "https://locate.measurementlab.net/v2/nearest/ndt/ndt7";
    private const string Subprotocol = "net.measurementlab.ndt.v7";
    private readonly HttpClient _http = new();

    public MlabNdt7Client()
    {
        _http.DefaultRequestHeaders.UserAgent.Add(new ProductInfoHeaderValue("Netsira", "0.2"));
    }

    public async Task<Ndt7Result> RunAsync(CancellationToken cancellationToken = default)
    {
        var server = await LocateAsync(cancellationToken);
        var download = await RunDownloadAsync(server.DownloadUrl, cancellationToken);
        var upload = await RunUploadAsync(server.UploadUrl, cancellationToken);
        return new Ndt7Result(download, upload, server.Machine, server.City, server.Country);
    }

    private async Task<(Uri DownloadUrl, Uri UploadUrl, string Machine, string? City, string? Country)> LocateAsync(CancellationToken ct)
    {
        using var response = await _http.GetAsync(LocateUrl, ct);
        response.EnsureSuccessStatusCode();
        await using var stream = await response.Content.ReadAsStreamAsync(ct);
        using var doc = await JsonDocument.ParseAsync(stream, cancellationToken: ct);
        var first = doc.RootElement.GetProperty("results")[0];
        var urls = first.GetProperty("urls");
        return (
            new Uri(urls.GetProperty("wss:///ndt/v7/download").GetString()!),
            new Uri(urls.GetProperty("wss:///ndt/v7/upload").GetString()!),
            first.GetProperty("machine").GetString() ?? "unknown",
            first.TryGetProperty("location", out var location) && location.TryGetProperty("city", out var city) ? city.GetString() : null,
            first.TryGetProperty("location", out location) && location.TryGetProperty("country", out var country) ? country.GetString() : null
        );
    }

    private static async Task<double> RunDownloadAsync(Uri uri, CancellationToken outerCt)
    {
        using var cts = CancellationTokenSource.CreateLinkedTokenSource(outerCt);
        cts.CancelAfter(TimeSpan.FromSeconds(16));
        using var ws = new ClientWebSocket();
        ws.Options.AddSubProtocol(Subprotocol);
        ws.Options.SetRequestHeader("User-Agent", "Netsira/0.2");
        await ws.ConnectAsync(uri, cts.Token);

        var sw = Stopwatch.StartNew();
        long bytes = 0;
        var buffer = new byte[64 * 1024];

        while (ws.State is WebSocketState.Open or WebSocketState.CloseReceived)
        {
            var result = await ws.ReceiveAsync(buffer, cts.Token);
            if (result.MessageType == WebSocketMessageType.Binary) bytes += result.Count;
            if (result.MessageType == WebSocketMessageType.Close)
            {
                if (ws.State == WebSocketState.CloseReceived)
                    await ws.CloseOutputAsync(WebSocketCloseStatus.NormalClosure, "ok", CancellationToken.None);
                break;
            }
        }

        sw.Stop();
        return Mbps(bytes, sw.Elapsed);
    }

    private static async Task<double> RunUploadAsync(Uri uri, CancellationToken outerCt)
    {
        using var cts = CancellationTokenSource.CreateLinkedTokenSource(outerCt);
        cts.CancelAfter(TimeSpan.FromSeconds(16));
        using var ws = new ClientWebSocket();
        ws.Options.AddSubProtocol(Subprotocol);
        ws.Options.SetRequestHeader("User-Agent", "Netsira/0.2");
        await ws.ConnectAsync(uri, cts.Token);

        var payload = new byte[64 * 1024];
        RandomNumberGenerator.Fill(payload);
        var sw = Stopwatch.StartNew();
        long bytes = 0;

        try
        {
            while (sw.Elapsed < TimeSpan.FromSeconds(10) && ws.State == WebSocketState.Open)
            {
                await ws.SendAsync(payload, WebSocketMessageType.Binary, true, cts.Token);
                bytes += payload.Length;
            }
        }
        catch (WebSocketException)
        {
            // Server may end early once the result is stable.
        }

        if (ws.State == WebSocketState.Open)
        {
            try { await ws.CloseOutputAsync(WebSocketCloseStatus.NormalClosure, "complete", CancellationToken.None); }
            catch { }
        }

        sw.Stop();
        return Mbps(bytes, sw.Elapsed);
    }

    private static double Mbps(long bytes, TimeSpan elapsed) =>
        elapsed.TotalSeconds <= 0 ? 0 : bytes * 8.0 / elapsed.TotalSeconds / 1_000_000.0;
}
