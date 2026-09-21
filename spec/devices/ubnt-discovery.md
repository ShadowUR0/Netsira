# UBNT local discovery

Netsira native applications may discover Ubiquiti devices on the same local IPv4 segment without scanning every host.

## Active discovery

The implementation sends both discovery probes to UDP port 10001 on each available IPv4 broadcast address:

- V1: `01 00 00 00`
- V2: `02 00 00 00`

Responses use a four-byte header followed by TLV fields. Netsira currently parses:

- 0x01 hardware address
- 0x02 interface MAC + IPv4 address
- 0x03 firmware
- 0x0A uptime
- 0x0B hostname/radio name
- 0x0C short model
- 0x0D SSID
- 0x14 long model

Discovery is local-only and does not send credentials.

## Scope

Discovery is a convenience, not a prerequisite. Manual device URL/IP entry always remains available because broadcasts may be blocked by VLANs, client isolation, firewall rules, VPN routing, or platform restrictions.

Protocol behavior was independently checked against multiple MIT-licensed open implementations. Netsira implements the packet format in its own native code.
