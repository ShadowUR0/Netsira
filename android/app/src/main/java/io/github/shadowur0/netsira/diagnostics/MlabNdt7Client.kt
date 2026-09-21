// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.shadowur0.netsira.diagnostics

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import okhttp3.*
import okio.ByteString
import org.json.JSONObject
import java.security.SecureRandom
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

data class Ndt7Result(
    val downloadMbps: Double,
    val uploadMbps: Double,
    val machine: String,
    val city: String?,
    val country: String?
)

class MlabNdt7Client {
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.SECONDS)
        .build()

    suspend fun run(): Ndt7Result = withContext(Dispatchers.IO) {
        val located = locate()
        val down = download(located.downloadUrl)
        val up = upload(located.uploadUrl)
        Ndt7Result(down, up, located.machine, located.city, located.country)
    }

    private data class Located(
        val downloadUrl: String,
        val uploadUrl: String,
        val machine: String,
        val city: String?,
        val country: String?
    )

    private fun locate(): Located {
        val request = Request.Builder()
            .url("https://locate.measurementlab.net/v2/nearest/ndt/ndt7")
            .header("User-Agent", "Netsira/0.2")
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("M-Lab locate failed: HTTP " + response.code)
            val root = JSONObject(response.body.string())
            val first = root.getJSONArray("results").getJSONObject(0)
            val urls = first.getJSONObject("urls")
            val location = first.optJSONObject("location")
            return Located(
                urls.getString("wss:///ndt/v7/download"),
                urls.getString("wss:///ndt/v7/upload"),
                first.optString("machine", "unknown"),
                location?.optString("city")?.takeIf { it.isNotBlank() },
                location?.optString("country")?.takeIf { it.isNotBlank() }
            )
        }
    }

    private suspend fun download(url: String): Double = withTimeout(16_000) {
        val opened = CompletableDeferred<Unit>()
        val finished = CompletableDeferred<Unit>()
        val bytes = AtomicLong(0)
        var started = 0L
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "Netsira/0.2")
            .header("Sec-WebSocket-Protocol", "net.measurementlab.ndt.v7")
            .build()
        val ws = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                started = System.nanoTime()
                opened.complete(Unit)
            }
            override fun onMessage(webSocket: WebSocket, data: ByteString) {
                bytes.addAndGet(data.size.toLong())
            }
            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                finished.complete(Unit)
            }
            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                if (!opened.isCompleted) opened.completeExceptionally(t)
                else if (!finished.isCompleted) finished.completeExceptionally(t)
            }
        })
        opened.await()
        try { finished.await() } finally { ws.cancel() }
        val seconds = (System.nanoTime() - started) / 1_000_000_000.0
        bytes.get() * 8.0 / seconds / 1_000_000.0
    }

    private suspend fun upload(url: String): Double = withTimeout(16_000) {
        val opened = CompletableDeferred<Unit>()
        val closed = CompletableDeferred<Unit>()
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "Netsira/0.2")
            .header("Sec-WebSocket-Protocol", "net.measurementlab.ndt.v7")
            .build()
        val ws = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) { opened.complete(Unit) }
            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) { closed.complete(Unit) }
            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                if (!opened.isCompleted) opened.completeExceptionally(t)
                else if (!closed.isCompleted) closed.complete(Unit)
            }
        })
        opened.await()

        val raw = ByteArray(64 * 1024)
        SecureRandom().nextBytes(raw)
        val payload = ByteString.of(*raw)
        val started = System.nanoTime()
        var bytes = 0L

        while ((System.nanoTime() - started) < 10_000_000_000L) {
            if (ws.queueSize() < 4L * 1024 * 1024) {
                if (!ws.send(payload)) break
                bytes += payload.size
            } else {
                delay(2)
            }
        }
        ws.close(1000, "complete")
        runCatching { withTimeout(2_000) { closed.await() } }
        val seconds = (System.nanoTime() - started) / 1_000_000_000.0
        bytes * 8.0 / seconds / 1_000_000.0
    }
}
