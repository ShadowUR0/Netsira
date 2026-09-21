// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.shadowur0.netsira.core

import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.sqrt

object RfCalculators {
    fun fsplDb(distanceKm: Double, frequencyMHz: Double): Double {
        require(distanceKm > 0 && frequencyMHz > 0)
        return 32.44 + 20 * log10(distanceKm) + 20 * log10(frequencyMHz)
    }
    fun fresnelRadiusMeters(d1Km: Double, d2Km: Double, frequencyGHz: Double): Double {
        require(d1Km > 0 && d2Km > 0 && frequencyGHz > 0)
        val wavelength = 0.299792458 / frequencyGHz
        val d1 = d1Km * 1000
        val d2 = d2Km * 1000
        return sqrt(wavelength * d1 * d2 / (d1 + d2))
    }
    fun eirpDbm(txPowerDbm: Double, antennaGainDbi: Double, cableLossDb: Double = 0.0) =
        txPowerDbm + antennaGainDbi - cableLossDb
    fun dbmToMilliwatts(dbm: Double) = 10.0.pow(dbm / 10.0)
}
