// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.shadowur0.netsira.diagnostics

import io.github.shadowur0.netsira.devices.AirOsSnapshot
import kotlin.math.abs

data class DiagnosticFinding(
    val code: String,
    val severity: String,
    val title: String,
    val evidence: List<String>
)

object FindingEngine {
    fun forQuick(
        hasNetwork: Boolean,
        dnsOk: Boolean,
        latencyMs: Double?,
        jitterMs: Double?,
        lossPercent: Double
    ): List<DiagnosticFinding> = buildList {
        if (!hasNetwork) add(f("NET_NO_ACTIVE", "problem", "No active network was detected.", "hasNetwork=false"))
        if (!dnsOk) add(f("DNS_RESOLUTION_FAILED", "problem", "DNS resolution failed.", "dnsOk=false"))

        if (lossPercent >= 20) {
            add(f("CONNECT_LOSS_HIGH", "problem",
                lossPercent.toInt().toString() + "% of TCP reachability probes failed.",
                "lossPercent=" + lossPercent))
        } else if (lossPercent > 0) {
            add(f("CONNECT_LOSS_PRESENT", "warning",
                lossPercent.toInt().toString() + "% of TCP reachability probes failed.",
                "lossPercent=" + lossPercent))
        }

        if (latencyMs != null && latencyMs >= 200) {
            add(f("CONNECT_LATENCY_HIGH", "warning",
                "Average TCP connection latency is " + latencyMs.toInt() + " ms.",
                "averageTcpConnectMs=" + latencyMs))
        }

        if (jitterMs != null && jitterMs >= 50) {
            add(f("CONNECT_JITTER_HIGH", "warning",
                "Probe jitter is " + jitterMs.toInt() + " ms.",
                "jitterMs=" + jitterMs))
        }

        if (isEmpty()) add(f(
            "QUICK_NO_OBVIOUS_ISSUE", "info",
            "No obvious problem was found by the quick test.",
            "dnsOk=" + dnsOk,
            "lossPercent=" + lossPercent,
            "averageTcpConnectMs=" + (latencyMs ?: "null"),
            "jitterMs=" + (jitterMs ?: "null")
        ))
    }

    fun forAirOs(snapshot: AirOsSnapshot): List<DiagnosticFinding> = buildList {
        val snr = snapshot.snrDb
        if (snr != null && snr < 10) {
            add(f("CPE_SNR_VERY_LOW", "problem",
                "Wireless SNR is very low at " + snr.toInt() + " dB.",
                "snrDb=" + snr))
        } else if (snr != null && snr < 20) {
            add(f("CPE_SNR_LOW", "warning",
                "Wireless SNR is low at " + snr.toInt() + " dB.",
                "snrDb=" + snr))
        }

        val chains = snapshot.chainRssi
            ?.split(',')
            ?.mapNotNull { it.trim().toDoubleOrNull() }
            .orEmpty()
        if (chains.size >= 2) {
            val delta = abs(chains.max() - chains.min())
            if (delta >= 8) add(f(
                "CPE_CHAIN_IMBALANCE", "warning",
                "Receive chains differ by " + delta.toInt() + " dB.",
                "chainRssi=" + snapshot.chainRssi,
                "chainDeltaDb=" + delta
            ))
        }

        if (snapshot.ethernetFullDuplex == false) {
            add(f("CPE_HALF_DUPLEX", "warning",
                "The CPE Ethernet link reports half duplex.",
                "ethernetFullDuplex=false",
                "ethernetSpeedMbps=" + (snapshot.ethernetSpeedMbps ?: "null")))
        }

        if (snapshot.cpuLoadPercent != null && snapshot.cpuLoadPercent >= 90) {
            add(f("CPE_CPU_HIGH", "warning",
                "The CPE CPU load is high at " + snapshot.cpuLoadPercent.toInt() + "%.",
                "cpuLoadPercent=" + snapshot.cpuLoadPercent))
        }

        if (isEmpty()) add(f(
            "CPE_NO_OBVIOUS_ISSUE", "info",
            "No obvious issue was found in the airOS metrics Netsira could read.",
            "signalDbm=" + (snapshot.signalDbm ?: "null"),
            "snrDb=" + (snapshot.snrDb ?: "null"),
            "ethernetSpeedMbps=" + (snapshot.ethernetSpeedMbps ?: "null")
        ))
    }

    fun stageStatus(findings: List<DiagnosticFinding>): String = when {
        findings.any { it.severity == "problem" } -> "problem"
        findings.any { it.severity == "warning" } -> "warning"
        else -> "info"
    }

    private fun f(code: String, severity: String, title: String, vararg evidence: String) =
        DiagnosticFinding(code, severity, title, evidence.toList())
}
