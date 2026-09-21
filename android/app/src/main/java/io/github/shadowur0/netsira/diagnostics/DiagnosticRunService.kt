// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.shadowur0.netsira.diagnostics

import android.content.Context
import io.github.shadowur0.netsira.devices.AirOsClient
import io.github.shadowur0.netsira.devices.AirOsSnapshot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

enum class DiagnosticMode {
    Quick,
    Standard,
    Comprehensive
}

data class AirOsConnectionSettings(
    val host: String,
    val username: String,
    val password: String
)

data class DiagnosticRunResult(
    val mode: DiagnosticMode,
    val quick: QuickDiagnosticResult,
    val speed: Ndt7Result?,
    val airOs: AirOsSnapshot?,
    val errors: List<String>,
    val skipped: List<String>
)

class DiagnosticRunService(private val context: Context) {
    suspend fun run(
        mode: DiagnosticMode,
        airOsSettings: AirOsConnectionSettings?
    ): DiagnosticRunResult {
        val errors = mutableListOf<String>()
        val skipped = mutableListOf<String>()
        val quick = QuickDiagnostic(context).run()

        var airOs: AirOsSnapshot? = null
        var speed: Ndt7Result? = null

        if (mode == DiagnosticMode.Comprehensive) {
            if (airOsSettings == null ||
                airOsSettings.host.isBlank() ||
                airOsSettings.username.isBlank() ||
                airOsSettings.password.isEmpty()
            ) {
                skipped += "CPE status: device address or credentials were not fully provided."
            } else {
                try {
                    airOs = withContext(Dispatchers.IO) {
                        AirOsClient().connectAndRead(
                            airOsSettings.host,
                            airOsSettings.username,
                            airOsSettings.password
                        )
                    }
                } catch (e: Exception) {
                    errors += "CPE status failed: " + (e.message ?: e.javaClass.simpleName)
                }
            }
        }

        if (mode != DiagnosticMode.Quick) {
            if (!quick.hasNetwork) {
                skipped += "M-Lab throughput: no active network was detected."
            } else {
                try {
                    speed = MlabNdt7Client().run()
                } catch (e: Exception) {
                    errors += "M-Lab throughput failed: " + (e.message ?: e.javaClass.simpleName)
                }
            }
        }

        return DiagnosticRunResult(mode, quick, speed, airOs, errors, skipped)
    }
}
