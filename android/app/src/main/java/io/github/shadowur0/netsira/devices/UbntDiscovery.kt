// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.shadowur0.netsira.devices

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.Inet4Address
import java.net.InetAddress
import java.net.NetworkInterface
import java.net.SocketTimeoutException
import java.nio.charset.StandardCharsets

data class UbntDiscoveredDevice(
    val mac: String,
    val ip: String?,
    val hostname: String?,
    val model: String?,
    val firmware: String?,
    val essid: String?,
    val uptimeSeconds: Long?
)

object UbntDiscovery {
    private const val PORT = 10001
    private val probeV1 = byteArrayOf(0x01, 0x00, 0x00, 0x00)
    private val probeV2 = byteArrayOf(0x02, 0x00, 0x00, 0x00)

    suspend fun discover(timeoutMs: Long = 4_000): List<UbntDiscoveredDevice> = withContext(Dispatchers.IO) {
        val found = linkedMapOf<String, UbntDiscoveredDevice>()
        DatagramSocket(null).use { socket ->
            socket.reuseAddress = true
            socket.broadcast = true
            socket.bind(java.net.InetSocketAddress(0))
            socket.soTimeout = 400

            val destinations = broadcastAddresses().ifEmpty { setOf(InetAddress.getByName("255.255.255.255")) }
            destinations.forEach { address ->
                socket.send(DatagramPacket(probeV1, probeV1.size, address, PORT))
                socket.send(DatagramPacket(probeV2, probeV2.size, address, PORT))
            }

            val deadline = System.nanoTime() + timeoutMs * 1_000_000
            val buffer = ByteArray(65_535)
            while (System.nanoTime() < deadline) {
                try {
                    val packet = DatagramPacket(buffer, buffer.size)
                    socket.receive(packet)
                    parseResponse(packet.data.copyOfRange(packet.offset, packet.offset + packet.length), packet.address)
                        ?.let { device ->
                            val previous = found[device.mac]
                            found[device.mac] = if (previous == null) device else merge(previous, device)
                        }
                } catch (_: SocketTimeoutException) {
                    // Continue until the overall scan deadline.
                }
            }
        }
        found.values.sortedBy { ipv4SortKey(it.ip) }
    }

    private fun broadcastAddresses(): Set<InetAddress> {
        val result = linkedSetOf<InetAddress>()
        val interfaces = runCatching { NetworkInterface.getNetworkInterfaces()?.toList().orEmpty() }.getOrDefault(emptyList())
        interfaces.filter { runCatching { it.isUp && !it.isLoopback }.getOrDefault(false) }.forEach { nic ->
            nic.interfaceAddresses.forEach { iface ->
                val broadcast = iface.broadcast
                if (iface.address is Inet4Address && broadcast != null) result += broadcast
            }
        }
        return result
    }

    internal fun parseResponse(data: ByteArray, sender: InetAddress): UbntDiscoveredDevice? {
        if (data.size < 4 || (data[0] != 0x01.toByte() && data[0] != 0x02.toByte())) return null

        var mac: String? = null
        var ip: String? = sender.hostAddress
        var hostname: String? = null
        var modelShort: String? = null
        var modelLong: String? = null
        var firmware: String? = null
        var essid: String? = null
        var uptime: Long? = null
        var offset = 4

        while (offset + 3 <= data.size) {
            val type = data[offset].toInt() and 0xff
            val length = ((data[offset + 1].toInt() and 0xff) shl 8) or (data[offset + 2].toInt() and 0xff)
            offset += 3
            if (offset + length > data.size) break

            when (type) {
                0x01 -> if (length >= 6) mac = formatMac(data, offset)
                0x02 -> if (length >= 10) {
                    if (mac == null) mac = formatMac(data, offset)
                    ip = InetAddress.getByAddress(data.copyOfRange(offset + 6, offset + 10)).hostAddress
                }
                0x03 -> firmware = decode(data, offset, length)
                0x0A -> if (length >= 4) {
                    uptime = ((data[offset].toLong() and 0xff) shl 24) or
                        ((data[offset + 1].toLong() and 0xff) shl 16) or
                        ((data[offset + 2].toLong() and 0xff) shl 8) or
                        (data[offset + 3].toLong() and 0xff)
                }
                0x0B -> hostname = decode(data, offset, length)
                0x0C -> modelShort = decode(data, offset, length)
                0x0D -> essid = decode(data, offset, length)
                0x14 -> modelLong = decode(data, offset, length)
            }
            offset += length
        }

        val finalMac = mac ?: return null
        return UbntDiscoveredDevice(finalMac, ip, hostname, modelLong ?: modelShort, firmware, essid, uptime)
    }

    private fun formatMac(data: ByteArray, offset: Int): String =
        (0 until 6).joinToString(":") { "%02X".format(data[offset + it].toInt() and 0xff) }

    private fun decode(data: ByteArray, offset: Int, length: Int): String =
        String(data, offset, length, StandardCharsets.UTF_8).trimEnd('\u0000').trim()

    private fun merge(old: UbntDiscoveredDevice, new: UbntDiscoveredDevice) =
        old.copy(
            ip = new.ip ?: old.ip,
            hostname = new.hostname ?: old.hostname,
            model = new.model ?: old.model,
            firmware = new.firmware ?: old.firmware,
            essid = new.essid ?: old.essid,
            uptimeSeconds = new.uptimeSeconds ?: old.uptimeSeconds
        )

    private fun ipv4SortKey(ip: String?): Long {
        val bytes = runCatching { InetAddress.getByName(ip).address }.getOrNull() ?: return Long.MAX_VALUE
        if (bytes.size != 4) return Long.MAX_VALUE
        return bytes.fold(0L) { acc, b -> (acc shl 8) or (b.toLong() and 0xff) }
    }
}
