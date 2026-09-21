// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.shadowur0.netsira

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import io.github.shadowur0.netsira.core.RfCalculators
import io.github.shadowur0.netsira.diagnostics.QuickDiagnostic
import io.github.shadowur0.netsira.ui.NetsiraTheme
import kotlinx.coroutines.launch
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
    var running by remember { mutableStateOf(false) }
    var resultText by remember { mutableStateOf("Not run yet.") }
    var distance by remember { mutableStateOf("1") }
    var frequency by remember { mutableStateOf("5800") }
    var fspl by remember { mutableStateOf("—") }

    Scaffold(topBar = { TopAppBar(title = { Column { Text("Netsira"); Text("Network diagnostics", style = MaterialTheme.typography.labelMedium) } }) }) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item { Text("Diagnostics", style = MaterialTheme.typography.headlineMedium) }
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Quick test", style = MaterialTheme.typography.titleMedium)
                        Button(enabled = !running, onClick = {
                            running = true
                            resultText = "Running…"
                            scope.launch {
                                val r = QuickDiagnostic(context).run()
                                resultText = buildString {
                                    appendLine("Network: " + if (r.hasNetwork) r.transport else "unavailable")
                                    appendLine("DNS: " + if (r.dnsOk) "ok" else "failed")
                                    appendLine("Latency: " + (r.averageLatencyMs?.let { String.format(Locale.US, "%.1f ms", it) } ?: "unavailable"))
                                    appendLine("Jitter: " + (r.jitterMs?.let { String.format(Locale.US, "%.1f ms", it) } ?: "unavailable"))
                                    appendLine("Probe loss: " + r.lossPercent.toInt() + "%")
                                    r.findings.forEach { appendLine("• " + it) }
                                }.trim()
                                running = false
                            }
                        }) { Text(if (running) "Running…" else "Run quick test") }
                        Text(resultText)
                    }
                }
            }
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("FSPL calculator", style = MaterialTheme.typography.titleMedium)
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
}
