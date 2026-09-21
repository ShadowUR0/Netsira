// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.shadowur0.netsira.devices

import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.net.URI
import java.util.concurrent.TimeUnit

data class AirOsSnapshot(
    val apiVersion: Int,
    val model: String,
    val hostname: String?,
    val firmware: String?,
    val signalDbm: Double?,
    val noiseDbm: Double?,
    val snrDb: Double?,
    val chainRssi: String?,
    val frequencyMHz: Double?,
    val channelWidthMHz: Double?,
    val txPowerDbm: Double?,
    val distanceMeters: Double?,
    val txRate: String?,
    val rxRate: String?,
    val ethernetSpeedMbps: Double?,
    val ethernetFullDuplex: Boolean?,
    val cpuLoadPercent: Double?
)

class AirOsClient {
    private class MemoryCookieJar : CookieJar {
        private val cookies = mutableMapOf<String, List<Cookie>>()
        override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
            this.cookies[url.host] = cookies
        }
        override fun loadForRequest(url: HttpUrl): List<Cookie> = cookies[url.host].orEmpty()
    }

    private val client = OkHttpClient.Builder()
        .cookieJar(MemoryCookieJar())
        .followRedirects(false)
        .followSslRedirects(false)
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .build()

    private var csrf: String? = null

    fun connectAndRead(baseValue: String, username: String, password: String): AirOsSnapshot {
        val base = normalize(baseValue)
        val api = if (tryLoginV8(base, username, password)) 8 else loginV6(base, username, password)

        val request = Request.Builder()
            .url(resolve(base, "status.cgi"))
            .header("User-Agent", "Netsira/0.2")
            .apply { csrf?.let { header("X-CSRF-ID", it) } }
            .build()

        client.newCall(request).execute().use { response ->
            if (response.code == 401 || response.code == 403) error("airOS rejected the authenticated status request.")
            if (!response.isSuccessful) error("airOS status failed: HTTP " + response.code)
            return parseSnapshot(api, JSONObject(response.body.string()))
        }
    }

    private fun tryLoginV8(base: String, username: String, password: String): Boolean {
        val json = JSONObject().put("username", username).put("password", password).toString()
        val request = Request.Builder()
            .url(resolve(base, "api/auth"))
            .header("User-Agent", "Netsira/0.2")
            .post(json.toRequestBody("application/json".toMediaType()))
            .build()

        client.newCall(request).execute().use { response ->
            if (response.code == 404) return false
            if (response.code == 401 || response.code == 403) error("Invalid airOS username or password.")
            if (!response.isSuccessful) error("airOS 8 login failed: HTTP " + response.code)
            csrf = response.header("X-CSRF-ID")
            return true
        }
    }

    private fun loginV6(base: String, username: String, password: String): Int {
        val loginUrl = resolve(base, "login.cgi")
        client.newCall(Request.Builder().url(loginUrl).get().build()).execute().use {
            if (!it.isSuccessful) error("airOS 6 login page failed: HTTP " + it.code)
        }

        val form = FormBody.Builder()
            .add("username", username)
            .add("password", password)
            .add("uri", "/index.cgi")
            .build()
        val post = Request.Builder()
            .url(loginUrl)
            .header("Referer", loginUrl)
            .header("Origin", URI(base).let { it.scheme + "://" + it.authority })
            .post(form)
            .build()

        client.newCall(post).execute().use { response ->
            if (response.code !in listOf(301, 302, 303, 307, 308)) error("airOS 6 login failed.")
        }

        client.newCall(Request.Builder().url(resolve(base, "index.cgi")).get().build()).execute().use { response ->
            val location = response.header("Location").orEmpty()
            if (response.code in listOf(301, 302, 303, 307, 308) && location.contains("login.cgi", ignoreCase = true)) {
                error("airOS 6 session activation failed.")
            }
        }
        return 6
    }

    private fun parseSnapshot(api: Int, root: JSONObject): AirOsSnapshot {
        val host = root.optJSONObject("host")
        val wireless = root.optJSONObject("wireless")
        val station = wireless?.optJSONArray("sta")?.optJSONObject(0)
        val remote = station?.optJSONObject("remote")

        val signal = number(wireless, "signal") ?: number(station, "signal") ?: number(remote, "signal")
        val noise = number(wireless, "noisef") ?: number(station, "noisefloor") ?: number(remote, "noisefloor")
        val chains = arrayNumbers(station?.optJSONArray("chainrssi")) ?: arrayNumbers(remote?.optJSONArray("chainrssi"))
        val ethernet = firstInterfaceStatus(root.optJSONArray("interfaces"))

        return AirOsSnapshot(
            apiVersion = api,
            model = text(host, "devmodel") ?: "Unknown",
            hostname = text(host, "hostname"),
            firmware = text(host, "fwversion"),
            signalDbm = signal,
            noiseDbm = noise,
            snrDb = if (signal != null && noise != null) signal - noise else null,
            chainRssi = chains,
            frequencyMHz = number(wireless, "frequency"),
            channelWidthMHz = number(wireless, "chanbw"),
            txPowerDbm = number(wireless, "txpower"),
            distanceMeters = number(wireless, "distance") ?: number(station, "distance") ?: number(remote, "distance"),
            txRate = text(wireless, "txrate"),
            rxRate = text(wireless, "rxrate"),
            ethernetSpeedMbps = number(ethernet, "speed"),
            ethernetFullDuplex = bool(ethernet, "duplex"),
            cpuLoadPercent = number(host, "cpuload")
        )
    }

    private fun firstInterfaceStatus(array: JSONArray?): JSONObject? {
        if (array == null) return null
        for (i in 0 until array.length()) {
            array.optJSONObject(i)?.optJSONObject("status")?.let { return it }
        }
        return null
    }

    private fun number(obj: JSONObject?, key: String): Double? {
        if (obj == null || !obj.has(key) || obj.isNull(key)) return null
        val value = obj.opt(key)
        return when (value) {
            is Number -> value.toDouble()
            is String -> value.substringBefore(' ').toDoubleOrNull()
            else -> null
        }
    }

    private fun text(obj: JSONObject?, key: String): String? =
        obj?.optString(key)?.takeIf { it.isNotBlank() && it != "null" }

    private fun bool(obj: JSONObject?, key: String): Boolean? {
        if (obj == null || !obj.has(key) || obj.isNull(key)) return null
        return when (val value = obj.opt(key)) {
            is Boolean -> value
            is Number -> value.toInt() != 0
            else -> null
        }
    }

    private fun arrayNumbers(array: JSONArray?): String? {
        if (array == null || array.length() == 0) return null
        return (0 until array.length()).joinToString(", ") { array.opt(it).toString() }
    }

    private fun normalize(value: String): String {
        var candidate = value.trim()
        if (!candidate.contains("://")) candidate = "http://" + candidate
        val uri = URI(candidate)
        require(uri.scheme == "http" || uri.scheme == "https") { "Only HTTP or HTTPS is supported." }
        require(!uri.host.isNullOrBlank()) { "Invalid device address." }
        return URI(uri.scheme, uri.userInfo, uri.host, uri.port, "/", null, null).toString()
    }

    private fun resolve(base: String, path: String): String = URI(base).resolve(path).toString()
}
