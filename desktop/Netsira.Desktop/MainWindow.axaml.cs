// SPDX-License-Identifier: AGPL-3.0-or-later
using Avalonia.Controls;
using Avalonia.Interactivity;
using Avalonia.Platform.Storage;
using Netsira.Desktop.Core;
using Netsira.Desktop.Devices;
using Netsira.Desktop.Diagnostics;
using Netsira.Desktop.Reports;

namespace Netsira.Desktop;

public sealed partial class MainWindow : Window
{
    private readonly QuickDiagnosticService _diagnostics = new();
    private readonly MlabNdt7Client _ndt7 = new();
    private readonly DiagnosticRunService _runService = new();
    private readonly UbntDiscoveryService _discovery = new();
    private readonly DateTimeOffset _sessionStartedAt = DateTimeOffset.UtcNow;
    private QuickDiagnosticResult? _lastQuick;
    private Ndt7Result? _lastSpeed;
    private AirOsSnapshot? _lastAirOs;
    private string _lastMode = "quick";

    public MainWindow() => InitializeComponent();

    private async void RunStandardTest(object? sender, RoutedEventArgs e) =>
        await RunModeAsync(DiagnosticMode.Standard);

    private async void RunComprehensiveTest(object? sender, RoutedEventArgs e) =>
        await RunModeAsync(DiagnosticMode.Comprehensive);

    private async Task RunModeAsync(DiagnosticMode mode)
    {
        if (MlabConsent.IsChecked != true)
        {
            ModeTestStatus.Text = "Enable the M-Lab privacy acknowledgement before running this test.";
            return;
        }

        StandardTestButton.IsEnabled = false;
        ComprehensiveTestButton.IsEnabled = false;
        ModeTestStatus.Text = mode == DiagnosticMode.Standard
            ? "Running standard test…"
            : "Running comprehensive test…";

        try
        {
            var settings = new AirOsConnectionSettings(
                AirOsHost.Text ?? "",
                AirOsUser.Text ?? "",
                AirOsPassword.Text ?? "",
                AllowInvalidCertificate.IsChecked == true);

            var run = await _runService.RunAsync(mode, settings);
            _lastQuick = run.Quick;
            _lastMode = run.Mode.ToString().ToLowerInvariant();
            _lastSpeed = run.Speed;
            _lastAirOs = run.AirOs ?? _lastAirOs;
            ExportReportButton.IsEnabled = true;

            var lines = new List<string>
            {
                $"Mode: {run.Mode}",
                $"Network: {(run.Quick.HasNetwork ? "available" : "unavailable")}",
                $"DNS: {(run.Quick.DnsOk ? "ok" : "failed")}",
                $"Latency: {(run.Quick.AverageLatencyMs is null ? "unavailable" : $"{run.Quick.AverageLatencyMs:0.0} ms")}",
                $"Jitter: {(run.Quick.JitterMs is null ? "unavailable" : $"{run.Quick.JitterMs:0.0} ms")}",
                $"Probe loss: {run.Quick.LossPercent:0}%"
            };

            if (run.Speed is not null)
            {
                lines.Add($"Download: {run.Speed.DownloadMbps:0.00} Mb/s");
                lines.Add($"Upload: {run.Speed.UploadMbps:0.00} Mb/s");
            }

            if (run.AirOs is not null)
            {
                lines.Add($"CPE: {run.AirOs.Model} — signal {run.AirOs.SignalDbm?.ToString("0") ?? "?"} dBm — SNR {run.AirOs.SnrDb?.ToString("0") ?? "?"} dB");
                lines.AddRange(FindingEngine.ForAirOs(run.AirOs).Select(x => "• " + x.Title));
            }

            lines.AddRange(run.Quick.Findings.Select(x => "• " + x.Title));
            lines.AddRange(run.Skipped.Select(x => "Skipped: " + x));
            lines.AddRange(run.Errors.Select(x => "Error: " + x));
            ModeTestStatus.Text = string.Join(Environment.NewLine, lines);
        }
        catch (Exception ex)
        {
            ModeTestStatus.Text = "Diagnostic run failed: " + ex.Message;
        }
        finally
        {
            StandardTestButton.IsEnabled = true;
            ComprehensiveTestButton.IsEnabled = true;
        }
    }

    private async void RunQuickTest(object? sender, RoutedEventArgs e)
    {
        QuickTestButton.IsEnabled = false;
        QuickTestStatus.Text = "Running…";
        try
        {
            var r = await _diagnostics.RunAsync();
            _lastQuick = r;
            _lastMode = "quick";
            ExportReportButton.IsEnabled = true;
            var lines = new List<string>
            {
                $"Network: {(r.HasNetwork ? "available" : "unavailable")}",
                $"Gateway: {r.Gateway ?? "not detected"}",
                $"DNS: {(r.DnsOk ? "ok" : "failed")}",
                $"Latency: {(r.AverageLatencyMs is null ? "unavailable" : $"{r.AverageLatencyMs:0.0} ms")}",
                $"Jitter: {(r.JitterMs is null ? "unavailable" : $"{r.JitterMs:0.0} ms")}",
                $"Probe loss: {r.LossPercent:0}%"
            };
            lines.AddRange(r.Findings.Select(x => "• " + x.Title));
            QuickTestStatus.Text = string.Join(Environment.NewLine, lines);
        }
        catch (Exception ex) { QuickTestStatus.Text = "Quick test failed: " + ex.Message; }
        finally { QuickTestButton.IsEnabled = true; }
    }

    private async void DiscoverDevices(object? sender, RoutedEventArgs e)
    {
        DiscoveryButton.IsEnabled = false;
        DiscoveryStatus.Text = "Scanning local network…";
        try
        {
            var devices = await _discovery.DiscoverAsync();
            if (devices.Count == 0)
            {
                DiscoveryStatus.Text = "No Ubiquiti discovery replies received.";
                return;
            }

            if (devices.Count == 1 && !string.IsNullOrWhiteSpace(devices[0].Ip))
                AirOsHost.Text = "http://" + devices[0].Ip;

            DiscoveryStatus.Text = string.Join(Environment.NewLine, devices.Select(d =>
                $"{d.Ip ?? "no IP"} — {d.Model ?? "Ubiquiti"} — {d.Hostname ?? d.Mac}"));
        }
        catch (Exception ex)
        {
            DiscoveryStatus.Text = "Discovery failed: " + ex.Message;
        }
        finally
        {
            DiscoveryButton.IsEnabled = true;
        }
    }

    private async void ReadAirOs(object? sender, RoutedEventArgs e)
    {
        AirOsButton.IsEnabled = false;
        AirOsStatus.Text = "Connecting…";
        try
        {
            using var client = new AirOsClient(AllowInvalidCertificate.IsChecked == true);
            var r = await client.ConnectAndReadAsync(AirOsHost.Text ?? "", AirOsUser.Text ?? "", AirOsPassword.Text ?? "");
            _lastAirOs = r;
            _lastMode = "standard";
            ExportReportButton.IsEnabled = true;
            var lines = new List<string>
            {
                $"airOS API: {r.ApiVersion}",
                $"Model: {r.Model}",
                $"Hostname: {r.Hostname ?? "unknown"}",
                $"Firmware: {r.Firmware ?? "unknown"}",
                $"Signal: {(r.SignalDbm is null ? "unavailable" : $"{r.SignalDbm:0} dBm")}",
                $"Noise: {(r.NoiseDbm is null ? "unavailable" : $"{r.NoiseDbm:0} dBm")}",
                $"SNR: {(r.SnrDb is null ? "unavailable" : $"{r.SnrDb:0} dB")}",
                $"Chains: {r.ChainRssi ?? "unavailable"}",
                $"Frequency: {(r.FrequencyMHz is null ? "unavailable" : $"{r.FrequencyMHz:0} MHz")}",
                $"Channel width: {(r.ChannelWidthMHz is null ? "unavailable" : $"{r.ChannelWidthMHz:0} MHz")}",
                $"TX power: {(r.TxPowerDbm is null ? "unavailable" : $"{r.TxPowerDbm:0} dBm")}",
                $"Distance: {(r.DistanceMeters is null ? "unavailable" : $"{r.DistanceMeters:0} m")}",
                $"TX/RX rate: {r.TxRate ?? "—"} / {r.RxRate ?? "—"}",
                $"Ethernet: {(r.EthernetSpeedMbps is null ? "unavailable" : $"{r.EthernetSpeedMbps:0} Mb/s")} " +
                    (r.EthernetFullDuplex is null ? "" : r.EthernetFullDuplex == true ? "full duplex" : "half duplex"),
                $"CPU: {(r.CpuLoadPercent is null ? "unavailable" : $"{r.CpuLoadPercent:0.#}%")}"
            };
            lines.AddRange(FindingEngine.ForAirOs(r).Select(x => "• " + x.Title));
            AirOsStatus.Text = string.Join(Environment.NewLine, lines);
        }
        catch (Exception ex) { AirOsStatus.Text = "airOS read failed: " + ex.Message; }
        finally { AirOsButton.IsEnabled = true; }
    }

    private async void RunSpeedTest(object? sender, RoutedEventArgs e)
    {
        if (MlabConsent.IsChecked != true)
        {
            SpeedTestStatus.Text = "Enable the M-Lab privacy acknowledgement above before running this test.";
            return;
        }

        SpeedTestButton.IsEnabled = false;
        SpeedTestStatus.Text = "Locating M-Lab server and running download/upload…";
        try
        {
            var r = await _ndt7.RunAsync();
            _lastSpeed = r;
            _lastMode = "standard";
            ExportReportButton.IsEnabled = true;
            var location = string.Join(", ", new[] { r.City, r.Country }.Where(x => !string.IsNullOrWhiteSpace(x)));
            SpeedTestStatus.Text =
                $"Download: {r.DownloadMbps:0.00} Mb/s{Environment.NewLine}" +
                $"Upload: {r.UploadMbps:0.00} Mb/s{Environment.NewLine}" +
                $"Server: {r.Machine}" + (string.IsNullOrEmpty(location) ? "" : $" ({location})");
        }
        catch (Exception ex) { SpeedTestStatus.Text = "NDT7 test failed: " + ex.Message; }
        finally { SpeedTestButton.IsEnabled = true; }
    }

    private async void ExportReport(object? sender, RoutedEventArgs e)
    {
        var json = ReportBuilder.Build(_sessionStartedAt, _lastQuick, _lastSpeed, _lastAirOs, _lastMode);
        var file = await StorageProvider.SaveFilePickerAsync(new FilePickerSaveOptions
        {
            Title = "Export Netsira diagnostic report",
            SuggestedFileName = "netsira-report-" + DateTimeOffset.Now.ToString("yyyyMMdd-HHmmss") + ".json",
            DefaultExtension = "json",
            FileTypeChoices = new[]
            {
                new FilePickerFileType("JSON report") { Patterns = new[] { "*.json" } }
            }
        });
        if (file is null) return;

        await using var stream = await file.OpenWriteAsync();
        using var writer = new StreamWriter(stream);
        await writer.WriteAsync(json);
    }

    private void CalculateFspl(object? sender, RoutedEventArgs e)
    {
        if (!double.TryParse(DistanceKm.Text, out var distance) ||
            !double.TryParse(FrequencyMHz.Text, out var frequency) ||
            distance <= 0 || frequency <= 0)
        {
            FsplResult.Text = "Result: enter positive numeric values.";
            return;
        }
        FsplResult.Text = $"Result: {RfCalculators.FsPlDb(distance, frequency):0.00} dB";
    }
}
