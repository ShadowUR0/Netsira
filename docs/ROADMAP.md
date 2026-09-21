# Netsira roadmap

## Phase 1 — Foundation ✅

Completed on `develop`.

- Product definition and platform boundaries.
- AGPL-3.0-or-later policy and third-party license tracking.
- Local-first / F-Droid constraints.
- Shared design-system rules and provisional semantic tokens.
- Shared diagnostic report JSON schema.
- Device capability schema and initial airOS read-only contract.
- Android foundation: Kotlin + Jetpack Compose.
- Desktop foundation: C# + .NET 10 + Avalonia 12.
- Web foundation: React + TypeScript + Vite + Tabler Core.
- HiDPI/performance policy for desktop.
- CI builds for Android, Desktop and Web.
- All three Phase 1 CI builds verified green.

Linux desktop remains a later target. iOS remains deferred to native Swift/SwiftUI.

## Phase 2 — First useful diagnostics ✅

Completed on `develop`.

- Shared RF calculation definitions and test vectors.
- Quick, standard and comprehensive diagnostic orchestration.
- Android network-state and reachability implementation.
- Desktop network-state and reachability implementation.
- Web browser-safe reachability/measurement layer.
- M-Lab NDT7 client integration with explicit privacy acknowledgement.
- UBNT local discovery plus airOS 6/8 authentication and read-only status adapter.
- Evidence-based findings engine with shared codes and thresholds.
- Exportable JSON diagnostic reports on Android, Desktop and Web.
- CI validation for shared specs and RF vectors.

Phase 2 remains read-only for network devices; no device configuration writes were added.


## Phase 3 — Live device diagnostics ✅

Completed on `develop`.

- Reusable authenticated airOS sessions; status polling no longer logs in on every sample.
- Native antenna-alignment mode on Android and Desktop with 1-second live signal/SNR/chain updates.
- 30-second stability monitoring on Android and Desktop:
  - repeated TCP reachability
  - probe loss
  - average latency
  - jitter
  - best/worst signal and signal spread when airOS is available
  - average SNR when airOS is available
- Browser-safe 30-second stability mode on Web using same-origin HTTPS timing; it is explicitly not presented as equivalent to native TCP/CPE monitoring.
- Alignment and stability summaries are exportable in Android/Desktop diagnostic reports.
- Web stability summaries are exportable in Web reports.
- Web Quick/Stability no longer contact M-Lab implicitly; M-Lab remains opt-in for NDT7 throughput.
- Shared monitoring contract plus reference vectors validated in CI.
- Final Android, Desktop, Web, and Specs builds verified green.

Network-device integration remains read-only.


## UX repair pass after Phase 3 ✅

- Reworked top-level navigation on Web, Android, and Desktop.
- Replaced the one-long-screen layout with real Diagnostics, Devices, Calculators, History, and Settings views.
- Added at-a-glance health/metric cards and interpreted findings before raw technical details.
- Moved device connection fields into a dedicated Devices view.
- Applied single-column labeled forms on Android.
- Added responsive navigation and larger/focus-visible controls on Web.
- Added shared UX/UI guidance based on usability/accessibility research in `docs/UX_UI.md`.
- Web, Android, and Desktop builds verified green.
