# Third-party notices

Direct dependencies intentionally adopted by Netsira:

| Component | Use | License |
| --- | --- | --- |
| AndroidX / Jetpack Compose | Android UI/runtime | Apache-2.0 |
| Material 3 for Compose | Android UI components | Apache-2.0 |
| kotlinx.coroutines | Android async work | Apache-2.0 |
| OkHttp | Android HTTP/WebSocket transport | Apache-2.0 |
| Avalonia UI | Desktop UI | MIT |
| React | Web UI | MIT |
| Vite | Web build tooling | MIT |
| TypeScript | Web language/tooling | Apache-2.0 |
| Tabler Core | Web UI kit | MIT |
| Tabler Icons | Cross-platform icon source | MIT |

Approved for later integration after implementation review:

| Component | Intended use | License |
| --- | --- | --- |
| Ktor Client | Optional future Android protocol work | Apache-2.0 |
| kotlinx.serialization | Android JSON | Apache-2.0 |
| Chart.js | Web charts | MIT |
| iperf3 | Optional LAN throughput testing | BSD-style |

External service:

- Measurement Lab NDT7 is used for optional public internet throughput measurements. M-Lab has its own privacy policy and publishes measurement data. Netsira must disclose this before users start the test.

Do not vendor ApexCharts through Tabler. Netsira will use a separately audited chart library.


## Desktop UI shell

- Semi.Avalonia — MIT — Avalonia theme inspired by Semi Design.
- Irihi.Ursa — MIT — Avalonia control library.
- Irihi.Ursa.Themes.Semi — MIT — Semi theme package for Ursa controls.

Netsira uses these packages through NuGet. No proprietary Semi extension packages are included.
