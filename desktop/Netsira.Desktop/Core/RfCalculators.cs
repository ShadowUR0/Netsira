// SPDX-License-Identifier: AGPL-3.0-or-later
namespace Netsira.Desktop.Core;

public static class RfCalculators
{
    public static double FsPlDb(double distanceKm, double frequencyMHz)
    {
        if (distanceKm <= 0) throw new ArgumentOutOfRangeException(nameof(distanceKm));
        if (frequencyMHz <= 0) throw new ArgumentOutOfRangeException(nameof(frequencyMHz));
        return 32.44 + 20 * Math.Log10(distanceKm) + 20 * Math.Log10(frequencyMHz);
    }

    public static double FresnelRadiusMeters(double d1Km, double d2Km, double frequencyGHz)
    {
        if (d1Km <= 0 || d2Km <= 0) throw new ArgumentOutOfRangeException(nameof(d1Km));
        if (frequencyGHz <= 0) throw new ArgumentOutOfRangeException(nameof(frequencyGHz));
        var wavelength = 0.299792458 / frequencyGHz;
        var d1 = d1Km * 1000.0;
        var d2 = d2Km * 1000.0;
        return Math.Sqrt(wavelength * d1 * d2 / (d1 + d2));
    }

    public static double EirpDbm(double txPowerDbm, double antennaGainDbi, double cableLossDb = 0) =>
        txPowerDbm + antennaGainDbi - cableLossDb;

    public static double DbmToMilliwatts(double dbm) => Math.Pow(10, dbm / 10.0);

    public static double MilliwattsToDbm(double milliwatts)
    {
        if (milliwatts <= 0) throw new ArgumentOutOfRangeException(nameof(milliwatts));
        return 10 * Math.Log10(milliwatts);
    }
}
