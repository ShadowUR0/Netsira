# airOS adapter contract

Initial target families: airOS 6 and airOS 8.

The adapter is read-only in the first implementation.

## Rules

1. Discover capabilities after authentication; never assume all metrics exist.
2. Do not mutate radio, network, power, channel or reboot settings.
3. Credentials stay local to the client. Persistent credential storage is opt-in only.
4. Authentication and parsing failures must be distinguishable from an unreachable device.
5. Firmware-specific parsing belongs inside the adapter, not in presentation code.

Candidate metrics include signal, noise floor, SNR, per-chain RSSI, frequency, channel width, TX power/rates, RX rates, estimated capacity, throughput, distance, AirMAX data, Ethernet speed/duplex, uptime, CPU and memory.
