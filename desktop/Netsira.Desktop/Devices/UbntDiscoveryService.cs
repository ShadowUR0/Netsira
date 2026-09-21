// SPDX-License-Identifier: AGPL-3.0-or-later
using System.Collections.Concurrent;
using System.Net;
using System.Net.NetworkInformation;
using System.Net.Sockets;
using System.Text;

namespace Netsira.Desktop.Devices;

public sealed record UbntDiscoveredDevice(
    string Mac,
    string? Ip,
    string? Hostname,
    string? Model,
    string? Firmware,
    string? Essid,
    uint? UptimeSeconds);

public sealed class UbntDiscoveryService
{
    private const int Port = 10001;
    private static readonly byte[] ProbeV1 = [0x01, 0x00, 0x00, 0x00];
    private static readonly byte[] ProbeV2 = [0x02, 0x00, 0x00, 0x00];

    public async Task<IReadOnlyList<UbntDiscoveredDevice>> DiscoverAsync(
        TimeSpan? timeout = null,
        CancellationToken cancellationToken = default)
    {
        timeout ??= TimeSpan.FromSeconds(4);
        var found = new ConcurrentDictionary<string, UbntDiscoveredDevice>(StringComparer.OrdinalIgnoreCase);
        var sockets = CreateSockets();

        if (sockets.Count == 0)
        {
            var fallback = new UdpClient(AddressFamily.InterNetwork);
            fallback.EnableBroadcast = true;
            sockets.Add((fallback, IPAddress.Broadcast));
        }

        using var timeoutCts = CancellationTokenSource.CreateLinkedTokenSource(cancellationToken);
        timeoutCts.CancelAfter(timeout.Value);

        try
        {
            foreach (var (socket, broadcast) in sockets)
            {
                await socket.SendAsync(ProbeV1, new IPEndPoint(broadcast, Port), cancellationToken);
                await socket.SendAsync(ProbeV2, new IPEndPoint(broadcast, Port), cancellationToken);
            }

            var listeners = sockets.Select(pair => ListenAsync(pair.Socket, found, timeoutCts.Token)).ToArray();
            try { await Task.WhenAll(listeners); }
            catch (OperationCanceledException) when (timeoutCts.IsCancellationRequested) { }
        }
        finally
        {
            foreach (var (socket, _) in sockets) socket.Dispose();
        }

        return found.Values
            .OrderBy(x => ParseIpForSort(x.Ip))
            .ThenBy(x => x.Hostname)
            .ToArray();
    }

    private static async Task ListenAsync(
        UdpClient socket,
        ConcurrentDictionary<string, UbntDiscoveredDevice> found,
        CancellationToken ct)
    {
        while (!ct.IsCancellationRequested)
        {
            try
            {
                var result = await socket.ReceiveAsync(ct);
                var device = ParseResponse(result.Buffer, result.RemoteEndPoint.Address);
                if (device is not null && !string.IsNullOrWhiteSpace(device.Mac))
                    found.AddOrUpdate(device.Mac, device, (_, old) => Merge(old, device));
            }
            catch (OperationCanceledException) when (ct.IsCancellationRequested)
            {
                break;
            }
            catch (SocketException)
            {
                // One interface may reject/lose broadcast traffic; other listeners continue.
            }
        }
    }

    private static List<(UdpClient Socket, IPAddress Broadcast)> CreateSockets()
    {
        var result = new List<(UdpClient, IPAddress)>();
        var seen = new HashSet<string>(StringComparer.Ordinal);

        foreach (var nic in NetworkInterface.GetAllNetworkInterfaces())
        {
            if (nic.OperationalStatus != OperationalStatus.Up ||
                nic.NetworkInterfaceType is NetworkInterfaceType.Loopback or NetworkInterfaceType.Tunnel)
                continue;

            foreach (var unicast in nic.GetIPProperties().UnicastAddresses)
            {
                if (unicast.Address.AddressFamily != AddressFamily.InterNetwork ||
                    unicast.IPv4Mask is null)
                    continue;

                var key = unicast.Address.ToString();
                if (!seen.Add(key)) continue;

                try
                {
                    var client = new UdpClient(new IPEndPoint(unicast.Address, 0))
                    {
                        EnableBroadcast = true
                    };
                    client.Client.SetSocketOption(SocketOptionLevel.Socket, SocketOptionName.ReuseAddress, true);
                    result.Add((client, BroadcastOf(unicast.Address, unicast.IPv4Mask)));
                }
                catch (SocketException)
                {
                    // Skip interfaces that cannot bind.
                }
            }
        }

        return result;
    }

    private static IPAddress BroadcastOf(IPAddress address, IPAddress mask)
    {
        var a = address.GetAddressBytes();
        var m = mask.GetAddressBytes();
        var b = new byte[4];
        for (var i = 0; i < 4; i++) b[i] = (byte)(a[i] | ~m[i]);
        return new IPAddress(b);
    }

    internal static UbntDiscoveredDevice? ParseResponse(ReadOnlySpan<byte> data, IPAddress sender)
    {
        if (data.Length < 4) return null;
        if (data[0] is not (0x01 or 0x02)) return null;

        string? mac = null;
        string? ip = sender.ToString();
        string? hostname = null;
        string? modelShort = null;
        string? modelLong = null;
        string? firmware = null;
        string? essid = null;
        uint? uptime = null;

        var offset = 4;
        while (offset + 3 <= data.Length)
        {
            var type = data[offset];
            var length = (data[offset + 1] << 8) | data[offset + 2];
            offset += 3;
            if (length < 0 || offset + length > data.Length) break;

            var value = data.Slice(offset, length);
            switch (type)
            {
                case 0x01 when value.Length >= 6:
                    mac = FormatMac(value[..6]);
                    break;
                case 0x02 when value.Length >= 10:
                    mac ??= FormatMac(value[..6]);
                    ip = new IPAddress(value.Slice(6, 4)).ToString();
                    break;
                case 0x03:
                    firmware = Decode(value);
                    break;
                case 0x0A when value.Length >= 4:
                    uptime = ((uint)value[0] << 24) | ((uint)value[1] << 16) | ((uint)value[2] << 8) | value[3];
                    break;
                case 0x0B:
                    hostname = Decode(value);
                    break;
                case 0x0C:
                    modelShort = Decode(value);
                    break;
                case 0x0D:
                    essid = Decode(value);
                    break;
                case 0x14:
                    modelLong = Decode(value);
                    break;
            }

            offset += length;
        }

        if (string.IsNullOrWhiteSpace(mac)) return null;
        return new UbntDiscoveredDevice(mac, ip, hostname, modelLong ?? modelShort, firmware, essid, uptime);
    }

    private static UbntDiscoveredDevice Merge(UbntDiscoveredDevice old, UbntDiscoveredDevice current) =>
        new(
            old.Mac,
            current.Ip ?? old.Ip,
            current.Hostname ?? old.Hostname,
            current.Model ?? old.Model,
            current.Firmware ?? old.Firmware,
            current.Essid ?? old.Essid,
            current.UptimeSeconds ?? old.UptimeSeconds);

    private static string FormatMac(ReadOnlySpan<byte> bytes) =>
        string.Join(":", bytes.ToArray().Select(x => x.ToString("X2")));

    private static string Decode(ReadOnlySpan<byte> bytes) =>
        Encoding.UTF8.GetString(bytes).TrimEnd('\0').Trim();

    private static uint ParseIpForSort(string? ip)
    {
        if (ip is null || !IPAddress.TryParse(ip, out var parsed) ||
            parsed.AddressFamily != AddressFamily.InterNetwork)
            return uint.MaxValue;
        var b = parsed.GetAddressBytes();
        return ((uint)b[0] << 24) | ((uint)b[1] << 16) | ((uint)b[2] << 8) | b[3];
    }
}
