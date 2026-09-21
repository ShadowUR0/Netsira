// SPDX-License-Identifier: AGPL-3.0-or-later
using System.Diagnostics;
using System.Net;
using System.Net.NetworkInformation;
using System.Net.Sockets;

namespace Netsira.Desktop.Diagnostics;

public sealed record QuickDiagnosticResult(
    bool HasNetwork,
    string? Gateway,
    bool DnsOk,
    double? AverageLatencyMs,
    double? JitterMs,
    double LossPercent,
    IReadOnlyList<DiagnosticFinding> Findings);

public sealed class QuickDiagnosticService
{
    public async Task<QuickDiagnosticResult> RunAsync(CancellationToken cancellationToken = default)
    {
        var hasNetwork = NetworkInterface.GetIsNetworkAvailable();
        var gateway = FindDefaultGateway();
        var dnsOk = false;
        try
        {
            var addresses = await Dns.GetHostAddressesAsync("example.com", cancellationToken);
            dnsOk = addresses.Length > 0;
        }
        catch { dnsOk = false; }

        var samples = new List<double>();
        const int attempts = 5;
        for (var i = 0; i < attempts; i++)
        {
            cancellationToken.ThrowIfCancellationRequested();
            var latency = await MeasureTcpConnectAsync("1.1.1.1", 443, cancellationToken);
            if (latency is not null) samples.Add(latency.Value);
            if (i < attempts - 1) await Task.Delay(120, cancellationToken);
        }

        var loss = (attempts - samples.Count) * 100.0 / attempts;
        double? average = samples.Count == 0 ? null : samples.Average();
        var jitter = CalculateJitter(samples);
        var findings = FindingEngine.ForQuick(hasNetwork, dnsOk, average, jitter, loss);
        return new QuickDiagnosticResult(hasNetwork, gateway, dnsOk, average, jitter, loss, findings);
    }

    private static string? FindDefaultGateway() =>
        NetworkInterface.GetAllNetworkInterfaces()
            .Where(n => n.OperationalStatus == OperationalStatus.Up)
            .SelectMany(n => n.GetIPProperties().GatewayAddresses)
            .Select(g => g.Address)
            .FirstOrDefault(a => !a.Equals(IPAddress.Any) && !a.Equals(IPAddress.IPv6Any))
            ?.ToString();

    private static async Task<double?> MeasureTcpConnectAsync(string host, int port, CancellationToken ct)
    {
        using var client = new TcpClient();
        var sw = Stopwatch.StartNew();
        try
        {
            using var timeoutCts = CancellationTokenSource.CreateLinkedTokenSource(ct);
            timeoutCts.CancelAfter(TimeSpan.FromSeconds(3));
            await client.ConnectAsync(host, port, timeoutCts.Token);
            return sw.Elapsed.TotalMilliseconds;
        }
        catch { return null; }
    }

    private static double? CalculateJitter(IReadOnlyList<double> samples)
    {
        if (samples.Count < 2) return null;
        var deltas = new List<double>();
        for (var i = 1; i < samples.Count; i++) deltas.Add(Math.Abs(samples[i] - samples[i - 1]));
        return deltas.Average();
    }
}
