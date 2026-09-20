# Netsira project definition

## Purpose

Netsira diagnoses network and wireless connectivity rather than reporting a single speed number.

`Device -> LAN -> Router -> CPE -> Wireless link -> ISP -> Internet`

The product inspects only the stages available on the current platform and network.

## Test families

- Quick: short health snapshot with the highest-value checks.
- Standard: broader diagnostics including local link and internet measurements.
- Comprehensive: all available checks plus cross-stage analysis.
- Stability: repeated latency/loss/jitter and signal observations over time.
- Alignment: live CPE signal/SNR/chain view for antenna alignment.
- LAN: local path and optional throughput diagnostics.
- Internet: latency, jitter, loss, DNS, routing and NDT7 throughput.
- Calculators: FSPL, Fresnel, link budget, EIRP, dBm/mW and related RF utilities.

The user does not choose a "before installation" or "after installation" mode. Calculators and live diagnostics are tools available at any time.

## Device integration

The first device family is Ubiquiti airOS 6/8. Adapters are capability-based. Missing metrics remain unavailable; Netsira must not synthesize them. Phase 1 device integration is read-only.

Later adapters may include MikroTik RouterOS, OpenWrt and standards such as SNMP where appropriate.

## Platform scope

### Android
Native Kotlin/Compose application for field work and direct LAN/CPE access.

### Desktop
C#/.NET/Avalonia. Windows first. Linux is deferred, but domain logic must remain portable.

### Web
React/TypeScript. Browser limitations are explicit: local HTTP devices, CORS, ICMP and OS network information may be unavailable.

### iOS
Deferred. Intended to be native Swift/SwiftUI while following the same Netsira specifications.

## Optional external measurement

M-Lab NDT7 is the default candidate for public download/upload measurement without Netsira-operated servers. External measurement is opt-in and must disclose provider privacy implications before first use.

Local router/CPE credentials and local reports are never sent to the measurement provider.
