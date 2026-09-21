// SPDX-License-Identifier: AGPL-3.0-or-later
using System.Diagnostics;
using System.Net.Sockets;

namespace Netsira.Desktop.Devices;

public sealed record AirOsLiveSample(
    DateTimeOffset Timestamp,
    double? SignalDbm,
    double? NoiseDbm,
    double? SnrDb,
    IReadOnlyList<double> Chains,
    double? CpuLoadPercent);

public sealed record AlignmentSummary(
    int SampleCount,
    double? BestSignalDbm,
    double? WorstSignalDbm,
    double? AverageSnrDb,
    double? MaxChainDeltaDb);

public sealed class AirOsLiveMonitorService
{
    public async Task<AlignmentSummary> RunAsync(
        AirOsClient client,
        TimeSpan interval,
        Func<AirOsLiveSample, Task> onSample,
        CancellationToken cancellationToken)
    {
        var samples = new List<AirOsLiveSample>();

        while (!cancellationToken.IsCancellationRequested)
        {
            try
            {
                var snapshot = await client.ReadStatusAsync(cancellationToken);
                var sample = new AirOsLiveSample(
                    DateTimeOffset.UtcNow,
                    snapshot.SignalDbm,
                    snapshot.NoiseDbm,
                    snapshot.SnrDb,
                    ParseChains(snapshot.ChainRssi),
                    snapshot.CpuLoadPercent);
                samples.Add(sample);
                await onSample(sample);
            }
            catch (OperationCanceledException) when (cancellationToken.IsCancellationRequested)
            {
                break;
            }

            try
            {
                await Task.Delay(interval, cancellationToken);
            }
            catch (OperationCanceledException) when (cancellationToken.IsCancellationRequested)
            {
                break;
            }
        }

        return Summarize(samples);
    }

    public static AlignmentSummary Summarize(IReadOnlyList<AirOsLiveSample> samples)
    {
        var signals = samples.Where(x => x.SignalDbm.HasValue).Select(x => x.SignalDbm!.Value).ToArray();
        var snr = samples.Where(x => x.SnrDb.HasValue).Select(x => x.SnrDb!.Value).ToArray();
        var deltas = samples
            .Where(x => x.Chains.Count >= 2)
            .Select(x => x.Chains.Max() - x.Chains.Min())
            .ToArray();

        return new AlignmentSummary(
            samples.Count,
            signals.Length == 0 ? null : signals.Max(),
            signals.Length == 0 ? null : signals.Min(),
            snr.Length == 0 ? null : snr.Average(),
            deltas.Length == 0 ? null : deltas.Max());
    }

    private static IReadOnlyList<double> ParseChains(string? value) =>
        string.IsNullOrWhiteSpace(value)
            ? Array.Empty<double>()
            : value.Split(',', StringSplitOptions.TrimEntries | StringSplitOptions.RemoveEmptyEntries)
                .Select(x => double.TryParse(x, out var number) ? (double?)number : null)
                .Where(x => x.HasValue)
                .Select(x => x!.Value)
                .ToArray();
}

public sealed record StabilitySample(
    DateTimeOffset Timestamp,
    double? TcpLatencyMs,
    double? SignalDbm,
    double? SnrDb);

public sealed record StabilitySummary(
    int SampleCount,
    double ProbeLossPercent,
    double? AverageLatencyMs,
    double? JitterMs,
    double? BestSignalDbm,
    double? WorstSignalDbm,
    double? SignalSpreadDb,
    double? AverageSnrDb);

public sealed class StabilityMonitorService
{
    public async Task<StabilitySummary> RunAsync(
        TimeSpan duration,
        TimeSpan interval,
        AirOsClient? airOsClient,
        Func<StabilitySample, Task>? onSample = null,
        CancellationToken cancellationToken = default)
    {
        var samples = new List<StabilitySample>();
        var started = Stopwatch.StartNew();

        while (started.Elapsed < duration && !cancellationToken.IsCancellationRequested)
        {
            var sampleStarted = Stopwatch.StartNew();
            var latency = await MeasureTcpConnectAsync(cancellationToken);
            double? signal = null;
            double? snr = null;

            if (airOsClient is not null)
            {
                try
                {
                    var snapshot = await airOsClient.ReadStatusAsync(cancellationToken);
                    signal = snapshot.SignalDbm;
                    snr = snapshot.SnrDb;
                }
                catch (OperationCanceledException) when (cancellationToken.IsCancellationRequested)
                {
                    break;
                }
                catch
                {
                    // A single CPE polling failure is represented by null wireless metrics.
                }
            }

            var sample = new StabilitySample(DateTimeOffset.UtcNow, latency, signal, snr);
            samples.Add(sample);
            if (onSample is not null) await onSample(sample);

            var remaining = interval - sampleStarted.Elapsed;
            if (remaining > TimeSpan.Zero)
            {
                try { await Task.Delay(remaining, cancellationToken); }
                catch (OperationCanceledException) when (cancellationToken.IsCancellationRequested) { break; }
            }
        }

        return Summarize(samples);
    }

    public static StabilitySummary Summarize(IReadOnlyList<StabilitySample> samples)
    {
        var latency = samples.Where(x => x.TcpLatencyMs.HasValue).Select(x => x.TcpLatencyMs!.Value).ToArray();
        var signal = samples.Where(x => x.SignalDbm.HasValue).Select(x => x.SignalDbm!.Value).ToArray();
        var snr = samples.Where(x => x.SnrDb.HasValue).Select(x => x.SnrDb!.Value).ToArray();

        double? jitter = null;
        if (latency.Length >= 2)
        {
            var deltas = new List<double>(latency.Length - 1);
            for (var i = 1; i < latency.Length; i++) deltas.Add(Math.Abs(latency[i] - latency[i - 1]));
            jitter = deltas.Average();
        }

        var loss = samples.Count == 0 ? 0 : (samples.Count - latency.Length) * 100.0 / samples.Count;
        double? best = signal.Length == 0 ? null : signal.Max();
        double? worst = signal.Length == 0 ? null : signal.Min();

        return new StabilitySummary(
            samples.Count,
            loss,
            latency.Length == 0 ? null : latency.Average(),
            jitter,
            best,
            worst,
            best.HasValue && worst.HasValue ? best.Value - worst.Value : null,
            snr.Length == 0 ? null : snr.Average());
    }

    private static async Task<double?> MeasureTcpConnectAsync(CancellationToken ct)
    {
        using var client = new TcpClient();
        var sw = Stopwatch.StartNew();
        try
        {
            using var timeout = CancellationTokenSource.CreateLinkedTokenSource(ct);
            timeout.CancelAfter(TimeSpan.FromSeconds(3));
            await client.ConnectAsync("1.1.1.1", 443, timeout.Token);
            return sw.Elapsed.TotalMilliseconds;
        }
        catch
        {
            return null;
        }
    }
}
