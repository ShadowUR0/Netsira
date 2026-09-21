// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.shadowur0.netsira.devices

import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import java.net.InetSocketAddress
import java.net.Socket
import kotlin.coroutines.coroutineContext
import kotlin.math.abs

data class AirOsLiveSample(
    val timestampMs: Long,
    val signalDbm: Double?,
    val noiseDbm: Double?,
    val snrDb: Double?,
    val chains: List<Double>,
    val cpuLoadPercent: Double?
)

data class AlignmentSummary(
    val sampleCount: Int,
    val bestSignalDbm: Double?,
    val worstSignalDbm: Double?,
    val averageSnrDb: Double?,
    val maxChainDeltaDb: Double?
)

class AirOsLiveMonitorService {
    suspend fun run(
        client: AirOsClient,
        intervalMs: Long = 1_000,
        onSample: suspend (AirOsLiveSample) -> Unit
    ): AlignmentSummary {
        val samples = mutableListOf<AirOsLiveSample>()
        try {
            while (true) {
                coroutineContext.ensureActive()
                val snapshot = client.readStatus()
                val sample = AirOsLiveSample(
                    timestampMs = System.currentTimeMillis(),
                    signalDbm = snapshot.signalDbm,
                    noiseDbm = snapshot.noiseDbm,
                    snrDb = snapshot.snrDb,
                    chains = parseChains(snapshot.chainRssi),
                    cpuLoadPercent = snapshot.cpuLoadPercent
                )
                samples += sample
                onSample(sample)
                delay(intervalMs)
            }
        } finally {
            // Caller cancellation stops the loop; summary is computed by the caller if needed.
        }
    }

    companion object {
        fun summarize(samples: List<AirOsLiveSample>): AlignmentSummary {
            val signals = samples.mapNotNull { it.signalDbm }
            val snr = samples.mapNotNull { it.snrDb }
            val deltas = samples.mapNotNull { sample ->
                sample.chains.takeIf { it.size >= 2 }?.let { it.max() - it.min() }
            }
            return AlignmentSummary(
                sampleCount = samples.size,
                bestSignalDbm = signals.maxOrNull(),
                worstSignalDbm = signals.minOrNull(),
                averageSnrDb = snr.takeIf { it.isNotEmpty() }?.average(),
                maxChainDeltaDb = deltas.maxOrNull()
            )
        }

        private fun parseChains(value: String?): List<Double> =
            value?.split(',')?.mapNotNull { it.trim().toDoubleOrNull() }.orEmpty()
    }
}

data class StabilitySample(
    val timestampMs: Long,
    val tcpLatencyMs: Double?,
    val signalDbm: Double?,
    val snrDb: Double?
)

data class StabilitySummary(
    val sampleCount: Int,
    val probeLossPercent: Double,
    val averageLatencyMs: Double?,
    val jitterMs: Double?,
    val bestSignalDbm: Double?,
    val worstSignalDbm: Double?,
    val signalSpreadDb: Double?,
    val averageSnrDb: Double?
)

class StabilityMonitorService {
    suspend fun run(
        durationMs: Long = 30_000,
        intervalMs: Long = 1_000,
        airOsClient: AirOsClient? = null,
        onSample: suspend (StabilitySample) -> Unit = {}
    ): StabilitySummary {
        val samples = mutableListOf<StabilitySample>()
        val started = System.nanoTime()

        while ((System.nanoTime() - started) / 1_000_000 < durationMs) {
            coroutineContext.ensureActive()
            val sampleStarted = System.nanoTime()
            val latency = measureTcp()

            var signal: Double? = null
            var snr: Double? = null
            if (airOsClient != null) {
                runCatching { airOsClient.readStatus() }.getOrNull()?.let {
                    signal = it.signalDbm
                    snr = it.snrDb
                }
            }

            val sample = StabilitySample(System.currentTimeMillis(), latency, signal, snr)
            samples += sample
            onSample(sample)

            val elapsedMs = (System.nanoTime() - sampleStarted) / 1_000_000
            if (elapsedMs < intervalMs) delay(intervalMs - elapsedMs)
        }

        return summarize(samples)
    }

    private fun measureTcp(): Double? {
        val started = System.nanoTime()
        return runCatching {
            Socket().use { it.connect(InetSocketAddress("1.1.1.1", 443), 3000) }
            (System.nanoTime() - started) / 1_000_000.0
        }.getOrNull()
    }

    companion object {
        fun summarize(samples: List<StabilitySample>): StabilitySummary {
            val latency = samples.mapNotNull { it.tcpLatencyMs }
            val signal = samples.mapNotNull { it.signalDbm }
            val snr = samples.mapNotNull { it.snrDb }
            val jitter = if (latency.size >= 2) {
                latency.zipWithNext { a, b -> abs(b - a) }.average()
            } else null
            val loss = if (samples.isEmpty()) 0.0 else (samples.size - latency.size) * 100.0 / samples.size
            val best = signal.maxOrNull()
            val worst = signal.minOrNull()

            return StabilitySummary(
                sampleCount = samples.size,
                probeLossPercent = loss,
                averageLatencyMs = latency.takeIf { it.isNotEmpty() }?.average(),
                jitterMs = jitter,
                bestSignalDbm = best,
                worstSignalDbm = worst,
                signalSpreadDb = if (best != null && worst != null) best - worst else null,
                averageSnrDb = snr.takeIf { it.isNotEmpty() }?.average()
            )
        }
    }
}
