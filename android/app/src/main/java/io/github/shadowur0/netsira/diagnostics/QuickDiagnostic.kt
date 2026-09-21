// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.shadowur0.netsira.diagnostics

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import kotlin.math.abs

data class QuickDiagnosticResult(
    val hasNetwork: Boolean,
    val transport: String,
    val dnsOk: Boolean,
    val averageLatencyMs: Double?,
    val jitterMs: Double?,
    val lossPercent: Double,
    val findings: List<String>
)

class QuickDiagnostic(private val context: Context) {
    suspend fun run(): QuickDiagnosticResult = withContext(Dispatchers.IO) {
        val cm = context.getSystemService(ConnectivityManager::class.java)
        val active = cm.activeNetwork
        val caps = active?.let(cm::getNetworkCapabilities)
        val hasNetwork = active != null && caps != null
        val transport = when {
            caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true -> "Wi-Fi"
            caps?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true -> "Cellular"
            caps?.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) == true -> "Ethernet"
            hasNetwork -> "Other"
            else -> "None"
        }
        val dnsOk = runCatching { InetAddress.getAllByName("example.com").isNotEmpty() }.getOrDefault(false)
        val samples = mutableListOf<Double>()
        repeat(5) { index ->
            measureTcp("1.1.1.1", 443)?.let(samples::add)
            if (index < 4) delay(120)
        }
        val loss = (5 - samples.size) * 20.0
        val average = samples.takeIf { it.isNotEmpty() }?.average()
        val jitter = if (samples.size >= 2) samples.zipWithNext { a, b -> abs(b - a) }.average() else null

        QuickDiagnosticResult(hasNetwork, transport, dnsOk, average, jitter, loss,
            findings(hasNetwork, dnsOk, average, jitter, loss))
    }

    private fun measureTcp(host: String, port: Int): Double? {
        val started = System.nanoTime()
        return runCatching {
            Socket().use { it.connect(InetSocketAddress(host, port), 3000) }
            (System.nanoTime() - started) / 1_000_000.0
        }.getOrNull()
    }

    private fun findings(network: Boolean, dns: Boolean, latency: Double?, jitter: Double?, loss: Double) = buildList {
        if (!network) add("Problem: no active network was detected.")
        if (!dns) add("Problem: DNS resolution failed.")
        if (loss >= 20) add("Problem: " + loss.toInt() + "% of TCP probes failed.")
        else if (loss > 0) add("Warning: " + loss.toInt() + "% of TCP probes failed.")
        if (latency != null && latency >= 200) add("Warning: average TCP connection latency is " + latency.toInt() + " ms.")
        if (jitter != null && jitter >= 50) add("Warning: probe jitter is " + jitter.toInt() + " ms.")
        if (isEmpty()) add("No obvious problem was found by the quick test.")
    }
}
