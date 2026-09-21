// SPDX-License-Identifier: AGPL-3.0-or-later
using Avalonia.Controls;
using Avalonia.Interactivity;
using Netsira.Desktop.Core;
using Netsira.Desktop.Diagnostics;

namespace Netsira.Desktop;

public sealed partial class MainWindow : Window
{
    private readonly QuickDiagnosticService _diagnostics = new();

    public MainWindow() => InitializeComponent();

    private async void RunQuickTest(object? sender, RoutedEventArgs e)
    {
        QuickTestButton.IsEnabled = false;
        QuickTestStatus.Text = "Running…";
        try
        {
            var r = await _diagnostics.RunAsync();
            var lines = new List<string>
            {
                $"Network: {(r.HasNetwork ? "available" : "unavailable")}",
                $"Gateway: {r.Gateway ?? "not detected"}",
                $"DNS: {(r.DnsOk ? "ok" : "failed")}",
                $"Latency: {(r.AverageLatencyMs is null ? "unavailable" : $"{r.AverageLatencyMs:0.0} ms")}",
                $"Jitter: {(r.JitterMs is null ? "unavailable" : $"{r.JitterMs:0.0} ms")}",
                $"Probe loss: {r.LossPercent:0}%"
            };
            lines.AddRange(r.Findings.Select(x => "• " + x));
            QuickTestStatus.Text = string.Join(Environment.NewLine, lines);
        }
        finally { QuickTestButton.IsEnabled = true; }
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
