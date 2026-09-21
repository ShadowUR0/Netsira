// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.shadowur0.netsira.reports

import io.github.shadowur0.netsira.devices.AirOsSnapshot
import io.github.shadowur0.netsira.diagnostics.DiagnosticFinding
import io.github.shadowur0.netsira.diagnostics.FindingEngine
import io.github.shadowur0.netsira.diagnostics.Ndt7Result
import io.github.shadowur0.netsira.diagnostics.QuickDiagnosticResult
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.util.UUID

object AndroidReportBuilder {
    fun build(
        startedAt: String,
        quick: QuickDiagnosticResult?,
        speed: Ndt7Result?,
        airOs: AirOsSnapshot?,
        mode: String? = null
    ): String {
        val stages = JSONArray()
        val findings = JSONArray()

        if (quick != null) {
            stages.put(
                JSONObject()
                    .put("id", "device")
                    .put("status", if (quick.hasNetwork) "ok" else "problem")
                    .put("metrics", JSONObject()
                        .put("hasNetwork", quick.hasNetwork)
                        .put("transport", quick.transport)
                        .put("dnsOk", quick.dnsOk))
            )

            stages.put(
                JSONObject()
                    .put("id", "internet")
                    .put("status", FindingEngine.stageStatus(quick.findings))
                    .put("metrics", JSONObject()
                        .putNullable("averageTcpConnectMs", quick.averageLatencyMs)
                        .putNullable("jitterMs", quick.jitterMs)
                        .put("probeLossPercent", quick.lossPercent)
                        .putNullable("downloadMbps", speed?.downloadMbps)
                        .putNullable("uploadMbps", speed?.uploadMbps))
            )
            addFindings(findings, quick.findings)
        } else if (speed != null) {
            stages.put(
                JSONObject()
                    .put("id", "internet")
                    .put("status", "info")
                    .put("metrics", JSONObject()
                        .put("downloadMbps", speed.downloadMbps)
                        .put("uploadMbps", speed.uploadMbps))
            )
        }

        if (airOs != null) {
            val cpeFindings = FindingEngine.forAirOs(airOs)
            stages.put(
                JSONObject()
                    .put("id", "cpe")
                    .put("status", FindingEngine.stageStatus(cpeFindings))
                    .put("metrics", JSONObject()
                        .put("apiVersion", airOs.apiVersion)
                        .put("model", airOs.model)
                        .putNullable("hostname", airOs.hostname)
                        .putNullable("firmware", airOs.firmware)
                        .putNullable("signalDbm", airOs.signalDbm)
                        .putNullable("noiseDbm", airOs.noiseDbm)
                        .putNullable("snrDb", airOs.snrDb)
                        .putNullable("chainRssi", airOs.chainRssi)
                        .putNullable("frequencyMHz", airOs.frequencyMHz)
                        .putNullable("channelWidthMHz", airOs.channelWidthMHz)
                        .putNullable("txPowerDbm", airOs.txPowerDbm)
                        .putNullable("distanceMeters", airOs.distanceMeters)
                        .putNullable("ethernetSpeedMbps", airOs.ethernetSpeedMbps)
                        .putNullable("ethernetFullDuplex", airOs.ethernetFullDuplex)
                        .putNullable("cpuLoadPercent", airOs.cpuLoadPercent))
            )
            addFindings(findings, cpeFindings)
        }

        val metadata = JSONObject()
        if (speed != null) {
            metadata.put("measurementProvider", "Measurement Lab")
            metadata.put("ndt7Machine", speed.machine)
            metadata.putNullable("ndt7City", speed.city)
            metadata.putNullable("ndt7Country", speed.country)
        }

        return JSONObject()
            .put("schemaVersion", "0.1.0")
            .put("id", UUID.randomUUID().toString())
            .put("mode", mode ?: if (speed != null || airOs != null) "standard" else "quick")
            .put("platform", "android")
            .put("startedAt", startedAt)
            .put("endedAt", Instant.now().toString())
            .put("stages", stages)
            .put("findings", findings)
            .put("metadata", metadata)
            .toString(2)
    }

    private fun addFindings(target: JSONArray, source: List<DiagnosticFinding>) {
        source.forEach { finding ->
            val evidence = JSONArray()
            finding.evidence.forEach(evidence::put)
            target.put(
                JSONObject()
                    .put("code", finding.code)
                    .put("severity", finding.severity)
                    .put("title", finding.title)
                    .put("evidence", evidence)
            )
        }
    }

    private fun JSONObject.putNullable(key: String, value: Any?): JSONObject =
        put(key, value ?: JSONObject.NULL)
}
