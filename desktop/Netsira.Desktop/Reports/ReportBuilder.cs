// SPDX-License-Identifier: AGPL-3.0-or-later
using System.Text.Json;
using System.Text.Json.Nodes;
using Netsira.Desktop.Devices;
using Netsira.Desktop.Diagnostics;

namespace Netsira.Desktop.Reports;

public static class ReportBuilder
{
    public static string Build(
        DateTimeOffset startedAt,
        QuickDiagnosticResult? quick,
        Ndt7Result? speed,
        AirOsSnapshot? airOs)
    {
        var stages = new JsonArray();
        var findings = new JsonArray();

        if (quick is not null)
        {
            stages.Add(new JsonObject
            {
                ["id"] = "device",
                ["status"] = quick.HasNetwork ? "ok" : "problem",
                ["metrics"] = new JsonObject
                {
                    ["hasNetwork"] = quick.HasNetwork,
                    ["gateway"] = quick.Gateway,
                    ["dnsOk"] = quick.DnsOk
                }
            });

            stages.Add(new JsonObject
            {
                ["id"] = "internet",
                ["status"] = !quick.DnsOk || quick.LossPercent >= 20
                    ? "problem"
                    : quick.LossPercent > 0 || quick.AverageLatencyMs >= 200 || quick.JitterMs >= 50
                        ? "warning"
                        : "ok",
                ["metrics"] = new JsonObject
                {
                    ["averageTcpConnectMs"] = quick.AverageLatencyMs,
                    ["jitterMs"] = quick.JitterMs,
                    ["probeLossPercent"] = quick.LossPercent,
                    ["downloadMbps"] = speed?.DownloadMbps,
                    ["uploadMbps"] = speed?.UploadMbps
                }
            });

            foreach (var text in quick.Findings)
            {
                var severity = text.StartsWith("Problem:", StringComparison.OrdinalIgnoreCase)
                    ? "problem"
                    : text.StartsWith("Warning:", StringComparison.OrdinalIgnoreCase) ? "warning" : "info";
                findings.Add(new JsonObject
                {
                    ["code"] = "QUICK_TEST_RESULT",
                    ["severity"] = severity,
                    ["title"] = text,
                    ["evidence"] = new JsonArray(
                        JsonValue.Create("dnsOk=" + quick.DnsOk),
                        JsonValue.Create("lossPercent=" + quick.LossPercent.ToString("0.##")),
                        JsonValue.Create("averageTcpConnectMs=" + (quick.AverageLatencyMs?.ToString("0.##") ?? "null")),
                        JsonValue.Create("jitterMs=" + (quick.JitterMs?.ToString("0.##") ?? "null")))
                });
            }
        }
        else if (speed is not null)
        {
            stages.Add(new JsonObject
            {
                ["id"] = "internet",
                ["status"] = "info",
                ["metrics"] = new JsonObject
                {
                    ["downloadMbps"] = speed.DownloadMbps,
                    ["uploadMbps"] = speed.UploadMbps
                }
            });
        }

        if (airOs is not null)
        {
            stages.Add(new JsonObject
            {
                ["id"] = "cpe",
                ["status"] = "info",
                ["metrics"] = new JsonObject
                {
                    ["apiVersion"] = airOs.ApiVersion,
                    ["model"] = airOs.Model,
                    ["hostname"] = airOs.Hostname,
                    ["firmware"] = airOs.Firmware,
                    ["signalDbm"] = airOs.SignalDbm,
                    ["noiseDbm"] = airOs.NoiseDbm,
                    ["snrDb"] = airOs.SnrDb,
                    ["chainRssi"] = airOs.ChainRssi,
                    ["frequencyMHz"] = airOs.FrequencyMHz,
                    ["channelWidthMHz"] = airOs.ChannelWidthMHz,
                    ["txPowerDbm"] = airOs.TxPowerDbm,
                    ["distanceMeters"] = airOs.DistanceMeters,
                    ["ethernetSpeedMbps"] = airOs.EthernetSpeedMbps,
                    ["ethernetFullDuplex"] = airOs.EthernetFullDuplex,
                    ["cpuLoadPercent"] = airOs.CpuLoadPercent
                }
            });
        }

        var metadata = new JsonObject();
        if (speed is not null)
        {
            metadata["measurementProvider"] = "Measurement Lab";
            metadata["ndt7Machine"] = speed.Machine;
            metadata["ndt7City"] = speed.City;
            metadata["ndt7Country"] = speed.Country;
        }

        var report = new JsonObject
        {
            ["schemaVersion"] = "0.1.0",
            ["id"] = Guid.NewGuid().ToString(),
            ["mode"] = speed is not null || airOs is not null ? "standard" : "quick",
            ["platform"] = "windows",
            ["startedAt"] = startedAt.ToString("O"),
            ["endedAt"] = DateTimeOffset.UtcNow.ToString("O"),
            ["stages"] = stages,
            ["findings"] = findings,
            ["metadata"] = metadata
        };

        return report.ToJsonString(new JsonSerializerOptions { WriteIndented = true });
    }
}
