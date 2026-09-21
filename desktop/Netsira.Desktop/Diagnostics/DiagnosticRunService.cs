// SPDX-License-Identifier: AGPL-3.0-or-later
using Netsira.Desktop.Devices;

namespace Netsira.Desktop.Diagnostics;

public enum DiagnosticMode
{
    Quick,
    Standard,
    Comprehensive
}

public sealed record AirOsConnectionSettings(
    string Host,
    string Username,
    string Password,
    bool AllowInvalidCertificate);

public sealed record DiagnosticRunResult(
    DiagnosticMode Mode,
    QuickDiagnosticResult Quick,
    Ndt7Result? Speed,
    AirOsSnapshot? AirOs,
    IReadOnlyList<string> Errors,
    IReadOnlyList<string> Skipped);

public sealed class DiagnosticRunService
{
    private readonly QuickDiagnosticService _quick = new();
    private readonly MlabNdt7Client _ndt7 = new();

    public async Task<DiagnosticRunResult> RunAsync(
        DiagnosticMode mode,
        AirOsConnectionSettings? airOsSettings,
        CancellationToken cancellationToken = default)
    {
        var errors = new List<string>();
        var skipped = new List<string>();
        var quick = await _quick.RunAsync(cancellationToken);

        AirOsSnapshot? airOs = null;
        Ndt7Result? speed = null;

        if (mode == DiagnosticMode.Comprehensive)
        {
            if (airOsSettings is null ||
                string.IsNullOrWhiteSpace(airOsSettings.Host) ||
                string.IsNullOrWhiteSpace(airOsSettings.Username) ||
                string.IsNullOrEmpty(airOsSettings.Password))
            {
                skipped.Add("CPE status: device address or credentials were not fully provided.");
            }
            else
            {
                try
                {
                    using var client = new AirOsClient(airOsSettings.AllowInvalidCertificate);
                    airOs = await client.ConnectAndReadAsync(
                        airOsSettings.Host,
                        airOsSettings.Username,
                        airOsSettings.Password,
                        cancellationToken);
                }
                catch (Exception ex)
                {
                    errors.Add("CPE status failed: " + ex.Message);
                }
            }
        }

        if (mode != DiagnosticMode.Quick)
        {
            if (!quick.HasNetwork)
            {
                skipped.Add("M-Lab throughput: no active network was detected.");
            }
            else
            {
                try
                {
                    speed = await _ndt7.RunAsync(cancellationToken);
                }
                catch (Exception ex)
                {
                    errors.Add("M-Lab throughput failed: " + ex.Message);
                }
            }
        }

        return new DiagnosticRunResult(mode, quick, speed, airOs, errors, skipped);
    }
}
