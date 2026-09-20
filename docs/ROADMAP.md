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

## Phase 2 — First useful diagnostics

Planned next:

- Shared RF calculation definitions and test vectors.
- Quick test orchestration and result model.
- Android network-state and reachability implementation.
- Desktop network-state and reachability implementation.
- Web browser-safe reachability/measurement layer.
- M-Lab NDT7 client integration with explicit privacy disclosure.
- Initial airOS discovery/authentication/read-only status adapter.
- First evidence-based findings engine.
- Exportable diagnostic report.

Do not add device configuration writes in Phase 2.
