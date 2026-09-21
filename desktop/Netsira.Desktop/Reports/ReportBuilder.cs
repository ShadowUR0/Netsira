// SPDX-License-Identifier: AGPL-3.0-or-later
using System.Text.Json;
using System.Text.Json.Nodes;
using Netsira.Desktop.Devices;
using Netsira.Desktop.Diagnostics;

namespace Netsira.Desktop.Reports;

public static class ReportBuilder
{
    public static string Build(DateTimeOffset startedAt, QuickDiagnosticResult? quick, Ndt7Result? speed, AirOsSnapshot? airOs, StabilitySummary? stability = null, string? mode = null)
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
                ["status"] = FindingEngine.StageStatus(quick.Findings),
                ["metrics"] = new JsonObject
                {
                    ["averageTcpConnectMs"] = quick.AverageLatencyMs,
                    ["jitterMs"] = quick.JitterMs,
                    ["probeLossPercent"] = quick.LossPercent,
                    ["downloadMbps"] = speed?.DownloadMbps,
                    ["uploadMbps"] = speed?.UploadMbps
                }
            });

            AddFindings(findings, quick.Findings);
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
            var cpeFindings = FindingEngine.ForAirOs(airOs);
            stages.Add(new JsonObject
            {
                ["id"] = "cpe",
                ["status"] = FindingEngine.StageStatus(cpeFindings),
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
            AddFindings(findings, cpeFindings);
        }

        if (stability is not null)
        {
            stages.Add(new JsonObject
            {
                ["id"] = "internet",
                ["status"] = stability.ProbeLossPercent >= 20
                    ? "problem"
                    : stability.ProbeLossPercent > 0 || stability.JitterMs >= 50 || stability.AverageLatencyMs >= 200
                        ? "warning"
                        : "info",
                ["metrics"] = new JsonObject
                {
                    ["stabilitySampleCount"] = stability.SampleCount,
                    ["probeLossPercent"] = stability.ProbeLossPercent,
                    ["averageLatencyMs"] = stability.AverageLatencyMs,
                    ["jitterMs"] = stability.JitterMs
                }
            });

            if (stability.BestSignalDbm is not null || stability.WorstSignalDbm is not null || stability.AverageSnrDb is not null)
            {
                stages.Add(new JsonObject
                {
                    ["id"] = "wireless",
                    ["status"] = stability.SignalSpreadDb >= 8 ? "warning" : "info",
                    ["metrics"] = new JsonObject
                    {
                        ["bestSignalDbm"] = stability.BestSignalDbm,
                        ["worstSignalDbm"] = stability.WorstSignalDbm,
                        ["signalSpreadDb"] = stability.SignalSpreadDb,
                        ["averageSnrDb"] = stability.AverageSnrDb
                    }
                });
            }
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
            ["mode"] = mode ?? (speed is not null || airOs is not null ? "standard" : "quick"),
            ["platform"] = "windows",
            ["startedAt"] = startedAt.ToString("O"),
            ["endedAt"] = DateTimeOffset.UtcNow.ToString("O"),
            ["stages"] = stages,
            ["findings"] = findings,
            ["metadata"] = metadata
        };

        return report.ToJsonString(new JsonSerializerOptions { WriteIndented = true });
    }

    private static void AddFindings(JsonArray target, IEnumerable<DiagnosticFinding> source)
    {
        foreach (var finding in source)
        {
            var evidence = new JsonArray();
            foreach (var item in finding.Evidence) evidence.Add(item);
            target.Add(new JsonObject
            {
                ["code"] = finding.Code,
                ["severity"] = finding.Severity,
                ["title"] = finding.Title,
                ["evidence"] = evidence
            });
        }
    }
}
