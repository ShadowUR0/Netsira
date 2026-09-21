// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.shadowur0.netsira

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import io.github.shadowur0.netsira.core.RfCalculators
import io.github.shadowur0.netsira.devices.AirOsClient
import io.github.shadowur0.netsira.devices.AirOsLiveMonitorService
import io.github.shadowur0.netsira.devices.AirOsLiveSample
import io.github.shadowur0.netsira.devices.AirOsSnapshot
import io.github.shadowur0.netsira.devices.StabilityMonitorService
import io.github.shadowur0.netsira.devices.StabilitySummary
import io.github.shadowur0.netsira.devices.UbntDiscovery
import io.github.shadowur0.netsira.diagnostics.AirOsConnectionSettings
import io.github.shadowur0.netsira.diagnostics.DiagnosticMode
import io.github.shadowur0.netsira.diagnostics.DiagnosticRunService
import io.github.shadowur0.netsira.diagnostics.FindingEngine
import io.github.shadowur0.netsira.diagnostics.MlabNdt7Client
import io.github.shadowur0.netsira.diagnostics.Ndt7Result
import io.github.shadowur0.netsira.diagnostics.QuickDiagnostic
import io.github.shadowur0.netsira.diagnostics.QuickDiagnosticResult
import io.github.shadowur0.netsira.reports.AndroidReportBuilder
import io.github.shadowur0.netsira.ui.NetsiraTheme
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { NetsiraTheme { NetsiraApp() } }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NetsiraApp() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val sessionStartedAt = remember { Instant.now().toString() }
    var pendingReport by remember { mutableStateOf("") }
    var lastQuick by remember { mutableStateOf<QuickDiagnosticResult?>(null) }
    var lastSpeed by remember { mutableStateOf<Ndt7Result?>(null) }
    var lastAirOs by remember { mutableStateOf<AirOsSnapshot?>(null) }
    var lastMode by remember { mutableStateOf("quick") }
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null && pendingReport.isNotEmpty()) {
            runCatching {
                context.contentResolver.openOutputStream(uri)?.use {
                    it.write(pendingReport.toByteArray(Charsets.UTF_8))
                }
            }
        }
    }

    var mlabConsent by remember { mutableStateOf(false) }
    var modeRunning by remember { mutableStateOf(false) }
    var modeText by remember { mutableStateOf("Not run yet.") }

    var quickRunning by remember { mutableStateOf(false) }
    var quickText by remember { mutableStateOf("Not run yet.") }

    var speedRunning by remember { mutableStateOf(false) }
    var speedText by remember { mutableStateOf("Not run yet.") }

    var alignmentJob by remember { mutableStateOf<Job?>(null) }
    var alignmentText by remember { mutableStateOf("Not running.") }
    val alignmentSamples = remember { mutableListOf<AirOsLiveSample>() }

    var stabilityJob by remember { mutableStateOf<Job?>(null) }
    var stabilityText by remember { mutableStateOf("Not run yet.") }
    var lastStability by remember { mutableStateOf<StabilitySummary?>(null) }

    var discoveryRunning by remember { mutableStateOf(false) }
    var discoveryText by remember { mutableStateOf("Not scanned.") }
    var airOsRunning by remember { mutableStateOf(false) }
    var airOsText by remember { mutableStateOf("Not connected.") }
    var airOsHost by remember { mutableStateOf("http://192.168.1.20") }
    var airOsUser by remember { mutableStateOf("ubnt") }
    var airOsPassword by remember { mutableStateOf("") }

    var distance by remember { mutableStateOf("1") }
    var frequency by remember { mutableStateOf("5800") }
    var fspl by remember { mutableStateOf("—") }

    fun runMode(mode: DiagnosticMode) {
        if (!mlabConsent) {
            modeText = "Enable the M-Lab privacy acknowledgement before running this test."
            return
        }

        modeRunning = true
        modeText = if (mode == DiagnosticMode.Standard) "Running standard test…" else "Running comprehensive test…"
        scope.launch {
            val run = DiagnosticRunService(context).run(
                mode,
                AirOsConnectionSettings(airOsHost, airOsUser, airOsPassword)
            )
            lastQuick = run.quick
            lastMode = run.mode.name.lowercase()
            lastSpeed = run.speed
            if (run.airOs != null) lastAirOs = run.airOs

            modeText = buildString {
                appendLine("Mode: " + run.mode.name)
                appendLine("Network: " + if (run.quick.hasNetwork) run.quick.transport else "unavailable")
                appendLine("DNS: " + if (run.quick.dnsOk) "ok" else "failed")
                appendLine("Latency: " + (run.quick.averageLatencyMs?.let { String.format(Locale.US, "%.1f ms", it) } ?: "unavailable"))
                appendLine("Jitter: " + (run.quick.jitterMs?.let { String.format(Locale.US, "%.1f ms", it) } ?: "unavailable"))
                appendLine("Probe loss: " + run.quick.lossPercent.toInt() + "%")
                run.speed?.let {
                    appendLine("Download: " + String.format(Locale.US, "%.2f Mb/s", it.downloadMbps))
                    appendLine("Upload: " + String.format(Locale.US, "%.2f Mb/s", it.uploadMbps))
                }
                run.airOs?.let {
                    appendLine("CPE: " + it.model + " — signal " + (it.signalDbm?.toInt()?.toString() ?: "?") + " dBm — SNR " + (it.snrDb?.toInt()?.toString() ?: "?") + " dB")
                    FindingEngine.forAirOs(it).forEach { finding -> appendLine("• " + finding.title) }
                }
                run.quick.findings.forEach { appendLine("• " + it.title) }
                run.skipped.forEach { appendLine("Skipped: " + it) }
                run.errors.forEach { appendLine("Error: " + it) }
            }.trim()
            modeRunning = false
        }
    }

    fun startAlignment() {
        if (airOsHost.isBlank() || airOsUser.isBlank() || airOsPassword.isEmpty()) {
            alignmentText = "Enter the airOS device address, username and password first."
            return
        }

        alignmentJob?.cancel()
        alignmentSamples.clear()
        alignmentText = "Connecting…"
        alignmentJob = scope.launch {
            try {
                withContext(Dispatchers.IO) {
                    val client = AirOsClient()
                    client.login(airOsHost, airOsUser, airOsPassword)
                    AirOsLiveMonitorService().run(client) { sample ->
                        alignmentSamples += sample
                        withContext(Dispatchers.Main) {
                            val chains = if (sample.chains.isEmpty()) "unavailable"
                            else sample.chains.joinToString(", ") { it.toInt().toString() }
                            alignmentText =
                                "Signal: " + (sample.signalDbm?.toInt()?.toString() ?: "?") + " dBm   " +
                                "SNR: " + (sample.snrDb?.toInt()?.toString() ?: "?") + " dB\n" +
                                "Chains: " + chains
                        }
                    }
                }
            } catch (_: CancellationException) {
                val summary = AirOsLiveMonitorService.summarize(alignmentSamples.toList())
                alignmentText =
                    "Stopped after " + summary.sampleCount + " samples.\n" +
                    "Best/worst signal: " + (summary.bestSignalDbm?.toInt()?.toString() ?: "?") + " / " +
                    (summary.worstSignalDbm?.toInt()?.toString() ?: "?") + " dBm\n" +
                    "Average SNR: " + (summary.averageSnrDb?.let { String.format(Locale.US, "%.1f", it) } ?: "?") + " dB   " +
                    "Max chain delta: " + (summary.maxChainDeltaDb?.let { String.format(Locale.US, "%.1f", it) } ?: "?") + " dB"
            } catch (e: Exception) {
                alignmentText = "Alignment failed: " + (e.message ?: e.javaClass.simpleName)
            } finally {
                alignmentJob = null
            }
        }
    }

    fun startStability() {
        stabilityJob?.cancel()
        stabilityText = "Starting 30-second stability test…"
        stabilityJob = scope.launch {
            try {
                val summary = withContext(Dispatchers.IO) {
                    var airOsClient: AirOsClient? = null
                    if (airOsHost.isNotBlank() && airOsUser.isNotBlank() && airOsPassword.isNotEmpty()) {
                        airOsClient = runCatching {
                            AirOsClient().also { it.login(airOsHost, airOsUser, airOsPassword) }
                        }.getOrNull()
                    }

                    StabilityMonitorService().run(
                        durationMs = 30_000,
                        intervalMs = 1_000,
                        airOsClient = airOsClient
                    ) { sample ->
                        withContext(Dispatchers.Main) {
                            stabilityText =
                                "TCP: " + (sample.tcpLatencyMs?.let { String.format(Locale.US, "%.1f ms", it) } ?: "failed") +
                                (sample.signalDbm?.let { "   Signal: " + it.toInt() + " dBm   SNR: " + (sample.snrDb?.toInt() ?: "?") + " dB" } ?: "")
                        }
                    }
                }

                lastStability = summary
                lastMode = "stability"
                stabilityText =
                    "Samples: " + summary.sampleCount + "\n" +
                    "Probe loss: " + String.format(Locale.US, "%.1f%%", summary.probeLossPercent) + "\n" +
                    "Average latency: " + (summary.averageLatencyMs?.let { String.format(Locale.US, "%.1f ms", it) } ?: "?") + "\n" +
                    "Jitter: " + (summary.jitterMs?.let { String.format(Locale.US, "%.1f ms", it) } ?: "?") + "\n" +
                    "Signal best/worst/spread: " +
                    (summary.bestSignalDbm?.toInt()?.toString() ?: "?") + " / " +
                    (summary.worstSignalDbm?.toInt()?.toString() ?: "?") + " / " +
                    (summary.signalSpreadDb?.let { String.format(Locale.US, "%.1f", it) } ?: "?") + " dB\n" +
                    "Average SNR: " + (summary.averageSnrDb?.let { String.format(Locale.US, "%.1f dB", it) } ?: "?")
            } catch (_: CancellationException) {
                stabilityText = "Stability test cancelled."
            } catch (e: Exception) {
                stabilityText = "Stability test failed: " + (e.message ?: e.javaClass.simpleName)
            } finally {
                stabilityJob = null
            }
        }
    }

    Scaffold(topBar = {
        TopAppBar(title = { Column { Text("Netsira"); Text("Network diagnostics", style = MaterialTheme.typography.labelMedium) } })
    }) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item { Text("Diagnostics", style = MaterialTheme.typography.headlineMedium) }
            item {
                SectionCard("Test modes") {
                    Text(
                        "Standard and comprehensive tests include M-Lab NDT7 throughput measurement.",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Row {
                        Checkbox(checked = mlabConsent, onCheckedChange = { mlabConsent = it })
                        Text(
                            "I understand that M-Lab records measurement metadata including my public IP address",
                            modifier = Modifier.padding(top = 12.dp)
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            enabled = !modeRunning,
                            onClick = { runMode(DiagnosticMode.Standard) }
                        ) { Text("Standard") }
                        Button(
                            enabled = !modeRunning,
                            onClick = { runMode(DiagnosticMode.Comprehensive) }
                        ) { Text("Comprehensive") }
                    }
                    Text(modeText)
                }
            }
            item {
                Button(
                    enabled = lastQuick != null || lastSpeed != null || lastAirOs != null,
                    onClick = {
                        pendingReport = AndroidReportBuilder.build(sessionStartedAt, lastQuick, lastSpeed, lastAirOs, lastMode)
                        exportLauncher.launch("netsira-report-" + System.currentTimeMillis() + ".json")
                    }
                ) { Text("Export report") }
            }

            item {
                SectionCard("Quick test") {
                    Button(enabled = !quickRunning, onClick = {
                        quickRunning = true
                        quickText = "Running…"
                        scope.launch {
                            val r = QuickDiagnostic(context).run()
                            lastQuick = r
                            lastMode = "quick"
                            quickText = buildString {
                                appendLine("Network: " + if (r.hasNetwork) r.transport else "unavailable")
                                appendLine("DNS: " + if (r.dnsOk) "ok" else "failed")
                                appendLine("Latency: " + (r.averageLatencyMs?.let { String.format(Locale.US, "%.1f ms", it) } ?: "unavailable"))
                                appendLine("Jitter: " + (r.jitterMs?.let { String.format(Locale.US, "%.1f ms", it) } ?: "unavailable"))
                                appendLine("Probe loss: " + r.lossPercent.toInt() + "%")
                                r.findings.forEach { appendLine("• " + it.title) }
                            }.trim()
                            quickRunning = false
                        }
                    }) { Text(if (quickRunning) "Running…" else "Run quick test") }
                    Text(quickText)
                }
            }

            item {
                SectionCard("airOS device status") {
                    Text("Read-only. Credentials stay in memory and are not saved.", style = MaterialTheme.typography.bodySmall)
                    Button(enabled = !discoveryRunning, onClick = {
                        discoveryRunning = true
                        discoveryText = "Scanning local network…"
                        scope.launch {
                            discoveryText = try {
                                val devices = UbntDiscovery.discover()
                                if (devices.size == 1 && !devices[0].ip.isNullOrBlank()) {
                                    airOsHost = "http://" + devices[0].ip
                                }
                                if (devices.isEmpty()) {
                                    "No Ubiquiti discovery replies received."
                                } else {
                                    devices.joinToString("\n") {
                                        (it.ip ?: "no IP") + " — " + (it.model ?: "Ubiquiti") + " — " + (it.hostname ?: it.mac)
                                    }
                                }
                            } catch (e: Exception) {
                                "Discovery failed: " + (e.message ?: e.javaClass.simpleName)
                            }
                            discoveryRunning = false
                        }
                    }) { Text(if (discoveryRunning) "Scanning…" else "Discover Ubiquiti devices") }
                    Text(discoveryText, style = MaterialTheme.typography.bodySmall)
                    OutlinedTextField(value = airOsHost, onValueChange = { airOsHost = it }, label = { Text("Device URL or IP") }, singleLine = true)
                    OutlinedTextField(value = airOsUser, onValueChange = { airOsUser = it }, label = { Text("Username") }, singleLine = true)
                    OutlinedTextField(
                        value = airOsPassword,
                        onValueChange = { airOsPassword = it },
                        label = { Text("Password") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation()
                    )
                    Button(enabled = !airOsRunning, onClick = {
                        airOsRunning = true
                        airOsText = "Connecting…"
                        scope.launch {
                            airOsText = try {
                                val r = withContext(Dispatchers.IO) { AirOsClient().connectAndRead(airOsHost, airOsUser, airOsPassword) }
                                lastAirOs = r
                                lastMode = "standard"
                                buildString {
                                    appendLine("airOS API: " + r.apiVersion)
                                    appendLine("Model: " + r.model)
                                    appendLine("Hostname: " + (r.hostname ?: "unknown"))
                                    appendLine("Firmware: " + (r.firmware ?: "unknown"))
                                    appendLine("Signal: " + (r.signalDbm?.let { it.toInt().toString() + " dBm" } ?: "unavailable"))
                                    appendLine("Noise: " + (r.noiseDbm?.let { it.toInt().toString() + " dBm" } ?: "unavailable"))
                                    appendLine("SNR: " + (r.snrDb?.let { it.toInt().toString() + " dB" } ?: "unavailable"))
                                    appendLine("Chains: " + (r.chainRssi ?: "unavailable"))
                                    appendLine("Frequency: " + (r.frequencyMHz?.let { it.toInt().toString() + " MHz" } ?: "unavailable"))
                                    appendLine("Channel width: " + (r.channelWidthMHz?.let { it.toInt().toString() + " MHz" } ?: "unavailable"))
                                    appendLine("TX power: " + (r.txPowerDbm?.let { it.toInt().toString() + " dBm" } ?: "unavailable"))
                                    appendLine("Distance: " + (r.distanceMeters?.let { it.toInt().toString() + " m" } ?: "unavailable"))
                                    appendLine("Ethernet: " + (r.ethernetSpeedMbps?.let { it.toInt().toString() + " Mb/s" } ?: "unavailable"))
                                    FindingEngine.forAirOs(r).forEach { appendLine("• " + it.title) }
                                }.trim()
                            } catch (e: Exception) {
                                "airOS read failed: " + (e.message ?: e.javaClass.simpleName)
                            }
                            airOsRunning = false
                        }
                    }) { Text(if (airOsRunning) "Connecting…" else "Read device status") }
                    Text(airOsText)
                }
            }

            item {
                SectionCard("Antenna alignment") {
                    Text(
                        "Uses one airOS session and refreshes signal, SNR and chains once per second.",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            enabled = alignmentJob == null,
                            onClick = { startAlignment() }
                        ) { Text("Start alignment") }
                        Button(
                            enabled = alignmentJob != null,
                            onClick = { alignmentJob?.cancel() }
                        ) { Text("Stop") }
                    }
                    Text(alignmentText)
                }
            }

            item {
                SectionCard("Stability monitor") {
                    Text(
                        "Runs repeated TCP reachability for 30 seconds and samples airOS signal/SNR when credentials are available.",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            enabled = stabilityJob == null,
                            onClick = { startStability() }
                        ) { Text("Run 30-second test") }
                        Button(
                            enabled = stabilityJob != null,
                            onClick = { stabilityJob?.cancel() }
                        ) { Text("Cancel") }
                    }
                    Text(stabilityText)
                }
            }

            item {
                SectionCard("Internet throughput (M-Lab NDT7)") {
                    Text("Optional. Measurement Lab records measurement metadata including your public IP address.", style = MaterialTheme.typography.bodySmall)
                    Button(enabled = !speedRunning, onClick = {
                        if (!mlabConsent) {
                            speedText = "Enable the M-Lab privacy acknowledgement above before running this test."
                            return@Button
                        }
                        speedRunning = true
                        speedText = "Locating M-Lab server and running download/upload…"
                        scope.launch {
                            speedText = try {
                                val r = MlabNdt7Client().run()
                                lastSpeed = r
                                lastMode = "standard"
                                val location = listOfNotNull(r.city, r.country).joinToString(", ")
                                "Download: " + String.format(Locale.US, "%.2f Mb/s", r.downloadMbps) + "\n" +
                                    "Upload: " + String.format(Locale.US, "%.2f Mb/s", r.uploadMbps) + "\n" +
                                    "Server: " + r.machine + if (location.isNotEmpty()) " (" + location + ")" else ""
                            } catch (e: Exception) {
                                "NDT7 test failed: " + (e.message ?: e.javaClass.simpleName)
                            }
                            speedRunning = false
                        }
                    }) { Text(if (speedRunning) "Running…" else "Run download + upload test") }
                    Text(speedText)
                }
            }

            item {
                SectionCard("FSPL calculator") {
                    OutlinedTextField(value = distance, onValueChange = { distance = it }, label = { Text("Distance (km)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                    OutlinedTextField(value = frequency, onValueChange = { frequency = it }, label = { Text("Frequency (MHz)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                    Button(onClick = {
                        val dv = distance.toDoubleOrNull()
                        val fv = frequency.toDoubleOrNull()
                        fspl = if (dv != null && fv != null && dv > 0 && fv > 0) String.format(Locale.US, "%.2f dB", RfCalculators.fsplDb(dv, fv)) else "Invalid values"
                    }) { Text("Calculate") }
                    Text("Result: " + fspl)
                }
            }
        }
    }
}

@Composable
private fun SectionCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            content()
        }
    }
}
