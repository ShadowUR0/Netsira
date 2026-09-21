// SPDX-License-Identifier: AGPL-3.0-or-later
using Netsira.Desktop.Devices;

namespace Netsira.Desktop.Diagnostics;

public sealed record DiagnosticFinding(
    string Code,
    string Severity,
    string Title,
    IReadOnlyList<string> Evidence);

public static class FindingEngine
{
    public static IReadOnlyList<DiagnosticFinding> ForQuick(
        bool hasNetwork,
        bool dnsOk,
        double? latencyMs,
        double? jitterMs,
        double lossPercent)
    {
        var findings = new List<DiagnosticFinding>();

        if (!hasNetwork)
            findings.Add(F("NET_NO_ACTIVE", "problem", "No active network was detected.", "hasNetwork=false"));

        if (!dnsOk)
            findings.Add(F("DNS_RESOLUTION_FAILED", "problem", "DNS resolution failed.", "dnsOk=false"));

        if (lossPercent >= 20)
            findings.Add(F("CONNECT_LOSS_HIGH", "problem",
                $"{lossPercent:0}% of TCP reachability probes failed.",
                $"lossPercent={lossPercent:0.##}"));
        else if (lossPercent > 0)
            findings.Add(F("CONNECT_LOSS_PRESENT", "warning",
                $"{lossPercent:0}% of TCP reachability probes failed.",
                $"lossPercent={lossPercent:0.##}"));

        if (latencyMs is >= 200)
            findings.Add(F("CONNECT_LATENCY_HIGH", "warning",
                $"Average TCP connection latency is {latencyMs:0} ms.",
                $"averageTcpConnectMs={latencyMs:0.##}"));

        if (jitterMs is >= 50)
            findings.Add(F("CONNECT_JITTER_HIGH", "warning",
                $"Probe jitter is {jitterMs:0} ms.",
                $"jitterMs={jitterMs:0.##}"));

        if (findings.Count == 0)
            findings.Add(F("QUICK_NO_OBVIOUS_ISSUE", "info",
                "No obvious problem was found by the quick test.",
                $"dnsOk={dnsOk}", $"lossPercent={lossPercent:0.##}",
                $"averageTcpConnectMs={latencyMs?.ToString("0.##") ?? "null"}",
                $"jitterMs={jitterMs?.ToString("0.##") ?? "null"}"));

        return findings;
    }

    public static IReadOnlyList<DiagnosticFinding> ForAirOs(AirOsSnapshot snapshot)
    {
        var findings = new List<DiagnosticFinding>();

        if (snapshot.SnrDb is < 10)
            findings.Add(F("CPE_SNR_VERY_LOW", "problem",
                $"Wireless SNR is very low at {snapshot.SnrDb:0} dB.",
                $"snrDb={snapshot.SnrDb:0.##}"));
        else if (snapshot.SnrDb is < 20)
            findings.Add(F("CPE_SNR_LOW", "warning",
                $"Wireless SNR is low at {snapshot.SnrDb:0} dB.",
                $"snrDb={snapshot.SnrDb:0.##}"));

        var chainValues = ParseChainRssi(snapshot.ChainRssi);
        if (chainValues.Count >= 2)
        {
            var delta = chainValues.Max() - chainValues.Min();
            if (delta >= 8)
                findings.Add(F("CPE_CHAIN_IMBALANCE", "warning",
                    $"Receive chains differ by {delta:0} dB.",
                    $"chainRssi={snapshot.ChainRssi}", $"chainDeltaDb={delta:0.##}"));
        }

        if (snapshot.EthernetFullDuplex == false)
            findings.Add(F("CPE_HALF_DUPLEX", "warning",
                "The CPE Ethernet link reports half duplex.",
                "ethernetFullDuplex=false",
                $"ethernetSpeedMbps={snapshot.EthernetSpeedMbps?.ToString("0.##") ?? "null"}"));

        if (snapshot.CpuLoadPercent is >= 90)
            findings.Add(F("CPE_CPU_HIGH", "warning",
                $"The CPE CPU load is high at {snapshot.CpuLoadPercent:0.#}%.",
                $"cpuLoadPercent={snapshot.CpuLoadPercent:0.##}"));

        if (findings.Count == 0)
            findings.Add(F("CPE_NO_OBVIOUS_ISSUE", "info",
                "No obvious issue was found in the airOS metrics Netsira could read.",
                $"signalDbm={snapshot.SignalDbm?.ToString("0.##") ?? "null"}",
                $"snrDb={snapshot.SnrDb?.ToString("0.##") ?? "null"}",
                $"ethernetSpeedMbps={snapshot.EthernetSpeedMbps?.ToString("0.##") ?? "null"}"));

        return findings;
    }

    public static string StageStatus(IEnumerable<DiagnosticFinding> findings)
    {
        var list = findings.ToArray();
        if (list.Any(x => x.Severity == "problem")) return "problem";
        if (list.Any(x => x.Severity == "warning")) return "warning";
        return "info";
    }

    private static DiagnosticFinding F(string code, string severity, string title, params string[] evidence) =>
        new(code, severity, title, evidence);

    private static IReadOnlyList<double> ParseChainRssi(string? value)
    {
        if (string.IsNullOrWhiteSpace(value)) return Array.Empty<double>();
        return value.Split(',', StringSplitOptions.TrimEntries | StringSplitOptions.RemoveEmptyEntries)
            .Select(x => double.TryParse(x, out var number) ? (double?)number : null)
            .Where(x => x.HasValue)
            .Select(x => x!.Value)
            .ToArray();
    }
}
