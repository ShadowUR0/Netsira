// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.shadowur0.netsira

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import io.github.shadowur0.netsira.core.RfCalculators
import io.github.shadowur0.netsira.devices.*
import io.github.shadowur0.netsira.diagnostics.*
import io.github.shadowur0.netsira.reports.AndroidReportBuilder
import io.github.shadowur0.netsira.ui.NetsiraTheme
import kotlinx.coroutines.*
import java.time.Instant
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { NetsiraTheme { NetsiraApp() } }
    }
}

private enum class AppPage(val label: String) {
    Diagnostics("Diagnostics"),
    Devices("Devices"),
    Calculators("Calculators"),
    History("History"),
    Settings("Settings")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NetsiraApp() {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    var page by rememberSaveable { mutableStateOf(AppPage.Diagnostics) }

    val sessionStartedAt = remember { Instant.now().toString() }
    var pendingReport by remember { mutableStateOf("") }
    var lastQuick by remember { mutableStateOf<QuickDiagnosticResult?>(null) }
    var lastSpeed by remember { mutableStateOf<Ndt7Result?>(null) }
    var lastAirOs by remember { mutableStateOf<AirOsSnapshot?>(null) }
    var lastAlignment by remember { mutableStateOf<AlignmentSummary?>(null) }
    var lastStability by remember { mutableStateOf<StabilitySummary?>(null) }
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
    var modeMessage by remember { mutableStateOf("Not run yet.") }
    var quickRunning by remember { mutableStateOf(false) }
    var speedRunning by remember { mutableStateOf(false) }
    var speedMessage by remember { mutableStateOf("Not run yet.") }

    var stabilityJob by remember { mutableStateOf<Job?>(null) }
    var stabilityMessage by remember { mutableStateOf("Not run yet.") }

    var discoveryRunning by remember { mutableStateOf(false) }
    var discoveryMessage by remember { mutableStateOf("Not scanned.") }
    var airOsRunning by remember { mutableStateOf(false) }
    var airOsMessage by remember { mutableStateOf("Not connected.") }
    var airOsHost by rememberSaveable { mutableStateOf("http://192.168.1.20") }
    var airOsUser by rememberSaveable { mutableStateOf("ubnt") }
    var airOsPassword by remember { mutableStateOf("") }

    var alignmentJob by remember { mutableStateOf<Job?>(null) }
    var alignmentMessage by remember { mutableStateOf("Not running.") }
    val alignmentSamples = remember { mutableListOf<AirOsLiveSample>() }

    var distance by rememberSaveable { mutableStateOf("1") }
    var frequency by rememberSaveable { mutableStateOf("5800") }
    var fspl by remember { mutableStateOf("—") }

    fun exportReport() {
        pendingReport = AndroidReportBuilder.build(
            sessionStartedAt, lastQuick, lastSpeed, lastAirOs, lastAlignment, lastStability, lastMode
        )
        exportLauncher.launch("netsira-report-" + System.currentTimeMillis() + ".json")
    }

    fun runQuick() {
        quickRunning = true
        scope.launch {
            try {
                lastQuick = QuickDiagnostic(context).run()
                lastMode = "quick"
            } finally {
                quickRunning = false
            }
        }
    }

    fun runMode(mode: DiagnosticMode) {
        if (!mlabConsent) {
            modeMessage = "Confirm the M-Lab privacy notice first."
            return
        }
        modeRunning = true
        modeMessage = if (mode == DiagnosticMode.Standard) "Running standard test…" else "Running comprehensive test…"
        scope.launch {
            try {
                val runResult = DiagnosticRunService(context).run(
                    mode, AirOsConnectionSettings(airOsHost, airOsUser, airOsPassword)
                )
                lastQuick = runResult.quick
                lastSpeed = runResult.speed
                if (runResult.airOs != null) lastAirOs = runResult.airOs
                lastMode = runResult.mode.name.lowercase()
                modeMessage = "Finished" +
                    if (runResult.errors.isNotEmpty()) " · " + runResult.errors.size + " issue(s)" else ""
            } catch (e: Exception) {
                modeMessage = "Test failed: " + (e.message ?: e.javaClass.simpleName)
            } finally {
                modeRunning = false
            }
        }
    }

    fun runSpeed() {
        if (!mlabConsent) {
            speedMessage = "Confirm the M-Lab privacy notice first."
            return
        }
        speedRunning = true
        speedMessage = "Running M-Lab download/upload test…"
        scope.launch {
            try {
                lastSpeed = MlabNdt7Client().run()
                lastMode = "standard"
                speedMessage = "Speed test complete."
            } catch (e: Exception) {
                speedMessage = "Speed test failed: " + (e.message ?: e.javaClass.simpleName)
            } finally {
                speedRunning = false
            }
        }
    }

    fun startStability() {
        stabilityJob?.cancel()
        stabilityMessage = "Starting 30-second stability test…"
        stabilityJob = scope.launch {
            try {
                val summary = withContext(Dispatchers.IO) {
                    var client: AirOsClient? = null
                    if (airOsHost.isNotBlank() && airOsUser.isNotBlank() && airOsPassword.isNotEmpty()) {
                        client = runCatching {
                            AirOsClient().also { it.login(airOsHost, airOsUser, airOsPassword) }
                        }.getOrNull()
                    }
                    StabilityMonitorService().run(
                        durationMs = 30_000,
                        intervalMs = 1_000,
                        airOsClient = client
                    ) { sample ->
                        withContext(Dispatchers.Main) {
                            stabilityMessage =
                                "TCP " + (sample.tcpLatencyMs?.let { String.format(Locale.US, "%.0f ms", it) } ?: "failed") +
                                (sample.signalDbm?.let { " · Signal " + it.toInt() + " dBm" } ?: "")
                        }
                    }
                }
                lastStability = summary
                lastMode = "stability"
                stabilityMessage = "Stability test complete."
            } catch (_: CancellationException) {
                stabilityMessage = "Stability test cancelled."
            } catch (e: Exception) {
                stabilityMessage = "Stability test failed: " + (e.message ?: e.javaClass.simpleName)
            } finally {
                stabilityJob = null
            }
        }
    }

    fun discoverDevices() {
        discoveryRunning = true
        discoveryMessage = "Scanning local network…"
        scope.launch {
            try {
                val devices = UbntDiscovery.discover()
                if (devices.size == 1 && !devices[0].ip.isNullOrBlank()) {
                    airOsHost = "http://" + devices[0].ip
                }
                discoveryMessage = if (devices.isEmpty()) {
                    "No Ubiquiti devices replied."
                } else {
                    devices.joinToString("\n") {
                        (it.ip ?: "No IP") + " · " + (it.model ?: "Ubiquiti") + " · " + (it.hostname ?: it.mac)
                    }
                }
            } catch (e: Exception) {
                discoveryMessage = "Discovery failed: " + (e.message ?: e.javaClass.simpleName)
            } finally {
                discoveryRunning = false
            }
        }
    }

    fun readAirOs() {
        if (airOsHost.isBlank() || airOsUser.isBlank() || airOsPassword.isEmpty()) {
            airOsMessage = "Enter device address, username and password."
            return
        }
        airOsRunning = true
        airOsMessage = "Connecting…"
        scope.launch {
            try {
                lastAirOs = withContext(Dispatchers.IO) {
                    AirOsClient().connectAndRead(airOsHost, airOsUser, airOsPassword)
                }
                lastMode = "standard"
                airOsMessage = "Device status loaded."
            } catch (e: Exception) {
                airOsMessage = "Device read failed: " + (e.message ?: e.javaClass.simpleName)
            } finally {
                airOsRunning = false
            }
        }
    }

    fun startAlignment() {
        if (airOsHost.isBlank() || airOsUser.isBlank() || airOsPassword.isEmpty()) {
            alignmentMessage = "Enter device address, username and password first."
            return
        }
        alignmentJob?.cancel()
        alignmentSamples.clear()
        alignmentMessage = "Connecting…"
        alignmentJob = scope.launch {
            try {
                withContext(Dispatchers.IO) {
                    val client = AirOsClient()
                    client.login(airOsHost, airOsUser, airOsPassword)
                    AirOsLiveMonitorService().run(client) { sample ->
                        alignmentSamples += sample
                        withContext(Dispatchers.Main) {
                            alignmentMessage =
                                "Signal " + (sample.signalDbm?.toInt()?.toString() ?: "?") + " dBm · " +
                                "SNR " + (sample.snrDb?.toInt()?.toString() ?: "?") + " dB"
                        }
                    }
                }
            } catch (_: CancellationException) {
                val summary = AirOsLiveMonitorService.summarize(alignmentSamples.toList())
                lastAlignment = summary
                lastMode = "alignment"
                alignmentMessage = "Stopped · " + summary.sampleCount + " samples"
            } catch (e: Exception) {
                alignmentMessage = "Alignment failed: " + (e.message ?: e.javaClass.simpleName)
            } finally {
                alignmentJob = null
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Column { Text(page.label, fontWeight = FontWeight.SemiBold); Text("Netsira", style = MaterialTheme.typography.labelMedium) } },
                actions = {
                    if (lastQuick != null || lastSpeed != null || lastAirOs != null || lastStability != null || lastAlignment != null) {
                        TextButton(onClick = ::exportReport) { Text("Export") }
                    }
                }
            )
        },
        bottomBar = {
            NavigationBar {
                AppPage.entries.forEach { item ->
                    val icon = when (item) {
                        AppPage.Diagnostics -> Icons.Default.Home
                        AppPage.Devices -> Icons.Default.Devices
                        AppPage.Calculators -> Icons.Default.Calculate
                        AppPage.History -> Icons.Default.History
                        AppPage.Settings -> Icons.Default.Settings
                    }
                    NavigationBarItem(
                        selected = page == item,
                        onClick = { page = item },
                        icon = { Icon(icon, contentDescription = item.label) },
                        label = { Text(item.label) }
                    )
                }
            }
        }
    ) { padding ->
        when (page) {
            AppPage.Diagnostics -> DiagnosticsPage(
                Modifier.padding(padding), quickRunning, lastQuick, lastSpeed, lastStability,
                modeRunning, modeMessage, mlabConsent, { mlabConsent = it },
                speedRunning, speedMessage, stabilityJob != null, stabilityMessage,
                ::runQuick, { runMode(DiagnosticMode.Standard) }, { runMode(DiagnosticMode.Comprehensive) },
                ::runSpeed, ::startStability, { stabilityJob?.cancel() }
            )
            AppPage.Devices -> DevicesPage(
                Modifier.padding(padding), discoveryRunning, discoveryMessage, ::discoverDevices,
                airOsHost, { airOsHost = it }, airOsUser, { airOsUser = it },
                airOsPassword, { airOsPassword = it }, airOsRunning, airOsMessage,
                lastAirOs, ::readAirOs, alignmentJob != null, alignmentMessage,
                ::startAlignment, { alignmentJob?.cancel() }
            )
            AppPage.Calculators -> CalculatorPage(
                Modifier.padding(padding), distance, { distance = it }, frequency, { frequency = it },
                fspl
            ) {
                val d = distance.toDoubleOrNull()
                val f = frequency.toDoubleOrNull()
                fspl = if (d != null && f != null && d > 0 && f > 0) {
                    String.format(Locale.US, "%.2f dB", RfCalculators.fsplDb(d, f))
                } else "Invalid values"
            }
            AppPage.History -> PlaceholderPage(
                Modifier.padding(padding), "No saved runs yet",
                "Persistent history and comparisons arrive in the product-polish phase. Export JSON reports for now."
            )
            AppPage.Settings -> SettingsPage(
                Modifier.padding(padding), mlabConsent, { mlabConsent = it }
            )
        }
    }
}

@Composable
private fun DiagnosticsPage(
    modifier: Modifier,
    quickRunning: Boolean,
    lastQuick: QuickDiagnosticResult?,
    lastSpeed: Ndt7Result?,
    lastStability: StabilitySummary?,
    modeRunning: Boolean,
    modeMessage: String,
    mlabConsent: Boolean,
    onConsentChange: (Boolean) -> Unit,
    speedRunning: Boolean,
    speedMessage: String,
    stabilityRunning: Boolean,
    stabilityMessage: String,
    onQuick: () -> Unit,
    onStandard: () -> Unit,
    onComprehensive: () -> Unit,
    onSpeed: () -> Unit,
    onStability: () -> Unit,
    onCancelStability: () -> Unit
) {
    val hasProblem = lastQuick?.findings?.any { it.severity == "problem" } == true
    val hasWarning = lastQuick?.findings?.any { it.severity == "warning" } == true
    val stateText = when {
        lastQuick == null -> "Not tested"
        hasProblem -> "Problem found"
        hasWarning -> "Needs attention"
        else -> "Looks good"
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Text("Network health", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Text("Start simple. Open advanced tests only when you need them.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        item {
            ElevatedCard {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    AssistChip(onClick = {}, label = { Text(stateText) })
                    Text(
                        lastQuick?.findings?.firstOrNull()?.title ?: "Run a quick check to see what is healthy.",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                    Button(onClick = onQuick, enabled = !quickRunning, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                        Text(if (quickRunning) "Checking…" else "Run quick check")
                    }
                }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                MetricTile(
                    Modifier.weight(1f), "Internet",
                    when { lastQuick == null -> "—"; lastQuick.dnsOk -> "Reachable"; else -> "Problem" },
                    lastQuick?.averageLatencyMs?.let { String.format(Locale.US, "%.0f ms", it) } ?: "Latency"
                )
                MetricTile(
                    Modifier.weight(1f), "Download",
                    lastSpeed?.let { String.format(Locale.US, "%.1f", it.downloadMbps) } ?: "—", "Mb/s"
                )
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                MetricTile(
                    Modifier.weight(1f), "Loss",
                    lastQuick?.let { String.format(Locale.US, "%.0f%%", it.lossPercent) } ?: "—", "Quick probes"
                )
                MetricTile(
                    Modifier.weight(1f), "Stability",
                    lastStability?.let { String.format(Locale.US, "%.0f%%", 100 - it.probeLossPercent) } ?: "—",
                    "Successful probes"
                )
            }
        }
        if (lastQuick != null) {
            item {
                SectionCard("What Netsira found", "Important findings first") {
                    lastQuick.findings.forEach { FindingRow(it) }
                }
            }
        }
        item {
            SectionCard("More tests", "Use these when the quick check is not enough") {
                Text("Stability · 30 seconds", fontWeight = FontWeight.SemiBold)
                Text("Repeated latency/loss checks; adds signal and SNR when airOS is available.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = onStability, enabled = !stabilityRunning) { Text(if (stabilityRunning) "Running…" else "Run stability") }
                    OutlinedButton(onClick = onCancelStability, enabled = stabilityRunning) { Text("Cancel") }
                }
                Text(stabilityMessage, style = MaterialTheme.typography.bodySmall)
                HorizontalDivider()
                Text("Internet speed", fontWeight = FontWeight.SemiBold)
                Text("Uses Measurement Lab and is optional.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                ConsentRow(mlabConsent, onConsentChange)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = onSpeed, enabled = !speedRunning) { Text(if (speedRunning) "Running…" else "Speed test") }
                    OutlinedButton(onClick = onStandard, enabled = !modeRunning) { Text("Standard") }
                }
                Text(speedMessage, style = MaterialTheme.typography.bodySmall)
                HorizontalDivider()
                Text("Comprehensive", fontWeight = FontWeight.SemiBold)
                Text("Combines internet checks with airOS status when credentials are available.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedButton(onClick = onComprehensive, enabled = !modeRunning, modifier = Modifier.fillMaxWidth()) {
                    Text(if (modeRunning) "Running…" else "Run comprehensive test")
                }
                Text(modeMessage, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun DevicesPage(
    modifier: Modifier,
    discoveryRunning: Boolean,
    discoveryMessage: String,
    onDiscover: () -> Unit,
    host: String,
    onHostChange: (String) -> Unit,
    username: String,
    onUsernameChange: (String) -> Unit,
    password: String,
    onPasswordChange: (String) -> Unit,
    airOsRunning: Boolean,
    airOsMessage: String,
    lastAirOs: AirOsSnapshot?,
    onRead: () -> Unit,
    alignmentRunning: Boolean,
    alignmentMessage: String,
    onAlignmentStart: () -> Unit,
    onAlignmentStop: () -> Unit
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Text("Local devices", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Text("Connect to a supported CPE and see radio health visually.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        item {
            SectionCard("Find your Ubiquiti device", "Discovery stays on your local network") {
                Button(onClick = onDiscover, enabled = !discoveryRunning, modifier = Modifier.fillMaxWidth()) {
                    Text(if (discoveryRunning) "Scanning…" else "Discover devices")
                }
                Text(discoveryMessage, style = MaterialTheme.typography.bodySmall)
            }
        }
        item {
            SectionCard("Connect to airOS", "Credentials stay in memory and are not saved") {
                LabeledField("Device address", "Example: 192.168.1.20", host, onHostChange)
                LabeledField("Username", null, username, onUsernameChange)
                Text("Password", fontWeight = FontWeight.Medium)
                OutlinedTextField(
                    value = password,
                    onValueChange = onPasswordChange,
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation()
                )
                Button(onClick = onRead, enabled = !airOsRunning, modifier = Modifier.fillMaxWidth()) {
                    Text(if (airOsRunning) "Connecting…" else "Read device status")
                }
                Text(airOsMessage, style = MaterialTheme.typography.bodySmall)
            }
        }
        if (lastAirOs != null) {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    MetricTile(Modifier.weight(1f), "Signal", lastAirOs.signalDbm?.let { it.toInt().toString() + " dBm" } ?: "—", "Received power")
                    MetricTile(Modifier.weight(1f), "SNR", lastAirOs.snrDb?.let { it.toInt().toString() + " dB" } ?: "—", "Signal quality")
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    MetricTile(Modifier.weight(1f), "Ethernet", lastAirOs.ethernetSpeedMbps?.let { it.toInt().toString() + " Mb/s" } ?: "—", if (lastAirOs.ethernetFullDuplex == true) "Full duplex" else "Link")
                    MetricTile(Modifier.weight(1f), "CPU", lastAirOs.cpuLoadPercent?.let { it.toInt().toString() + "%" } ?: "—", lastAirOs.model)
                }
            }
            item {
                SectionCard("Device findings", "What matters from the raw radio values") {
                    FindingEngine.forAirOs(lastAirOs).forEach { FindingRow(it) }
                }
            }
        }
        item {
            SectionCard("Antenna alignment", "Live signal and SNR, refreshed every second") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = onAlignmentStart, enabled = !alignmentRunning) { Text(if (alignmentRunning) "Running…" else "Start") }
                    OutlinedButton(onClick = onAlignmentStop, enabled = alignmentRunning) { Text("Stop") }
                }
                Text(alignmentMessage)
            }
        }
    }
}

@Composable
private fun CalculatorPage(
    modifier: Modifier,
    distance: String,
    onDistanceChange: (String) -> Unit,
    frequency: String,
    onFrequencyChange: (String) -> Unit,
    result: String,
    onCalculate: () -> Unit
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Text("RF calculators", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Text("Engineering tools stay separate from diagnostics.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        item {
            SectionCard("Free-space path loss", "Estimate path loss from distance and frequency") {
                LabeledField("Distance (km)", null, distance, onDistanceChange, KeyboardType.Decimal)
                LabeledField("Frequency (MHz)", null, frequency, onFrequencyChange, KeyboardType.Decimal)
                Button(onClick = onCalculate, modifier = Modifier.fillMaxWidth()) { Text("Calculate") }
                Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text("Estimated path loss", style = MaterialTheme.typography.labelMedium)
                        Text(result, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsPage(modifier: Modifier, mlabConsent: Boolean, onConsentChange: (Boolean) -> Unit) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Text("Settings", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Text("Privacy and advanced behavior.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        item {
            SectionCard("Privacy", "Netsira is local-first") {
                SettingRow("Telemetry", "No analytics or hidden telemetry", "Off")
                HorizontalDivider()
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("M-Lab throughput", fontWeight = FontWeight.SemiBold)
                        Text("Optional external speed measurement", style = MaterialTheme.typography.bodySmall)
                    }
                    Switch(checked = mlabConsent, onCheckedChange = onConsentChange)
                }
            }
        }
    }
}

@Composable
private fun PlaceholderPage(modifier: Modifier, title: String, body: String) {
    Box(modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        ElevatedCard {
            Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(body, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun SectionCard(title: String, subtitle: String? = null, content: @Composable ColumnScope.() -> Unit) {
    ElevatedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            content()
        }
    }
}

@Composable
private fun MetricTile(modifier: Modifier, label: String, value: String, supporting: String) {
    ElevatedCard(modifier) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(supporting, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun FindingRow(finding: DiagnosticFinding) {
    val container = when (finding.severity) {
        "problem" -> MaterialTheme.colorScheme.errorContainer
        "warning" -> MaterialTheme.colorScheme.tertiaryContainer
        else -> MaterialTheme.colorScheme.secondaryContainer
    }
    Surface(color = container, shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Text(if (finding.severity == "problem") "Problem" else if (finding.severity == "warning") "Needs attention" else "OK", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
            Text(finding.title)
        }
    }
}

@Composable
private fun ConsentRow(checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked = checked, onCheckedChange = onCheckedChange)
        Text("I understand the M-Lab privacy notice", modifier = Modifier.weight(1f))
    }
}

@Composable
private fun SettingRow(title: String, body: String, value: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.SemiBold)
            Text(body, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        AssistChip(onClick = {}, label = { Text(value) })
    }
}

@Composable
private fun LabeledField(
    label: String,
    helper: String?,
    value: String,
    onValueChange: (String) -> Unit,
    keyboardType: KeyboardType = KeyboardType.Text
) {
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Text(label, fontWeight = FontWeight.Medium)
        if (helper != null) Text(helper, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType)
        )
    }
}
