// SPDX-License-Identifier: AGPL-3.0-or-later
export function fsplDb(distanceKm: number, frequencyMHz: number): number {
  if (distanceKm <= 0 || frequencyMHz <= 0) throw new RangeError('Values must be positive')
  return 32.44 + 20 * Math.log10(distanceKm) + 20 * Math.log10(frequencyMHz)
}
export function fresnelRadiusMeters(d1Km: number, d2Km: number, frequencyGHz: number): number {
  if (d1Km <= 0 || d2Km <= 0 || frequencyGHz <= 0) throw new RangeError('Values must be positive')
  const wavelength = 0.299792458 / frequencyGHz
  const d1 = d1Km * 1000
  const d2 = d2Km * 1000
  return Math.sqrt((wavelength * d1 * d2) / (d1 + d2))
}
export const eirpDbm = (txPowerDbm: number, antennaGainDbi: number, cableLossDb = 0) =>
  txPowerDbm + antennaGainDbi - cableLossDb
export const dbmToMilliwatts = (dbm: number) => 10 ** (dbm / 10)
