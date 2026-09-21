# Netsira

**Web:** https://shadowur0.github.io/Netsira/

Netsira is an open-source network and wireless diagnostics suite.

It is not a speed-test clone. The project is designed to diagnose a connection layer by layer:

`Device -> LAN -> Router -> CPE -> Wireless link -> ISP -> Internet`

## Platforms

- Android: Kotlin + Jetpack Compose
- Desktop: C# + .NET + Avalonia UI (Windows first, Linux later)
- Web: React + TypeScript + Vite + Tabler Core
- iOS: planned later with Swift + SwiftUI

The platforms share the same product language, report model, icons, semantic design tokens, and diagnostic concepts, while each platform keeps a native/appropriate interface instead of reusing one UI everywhere.

## Principles

- Local-first. Router/CPE credentials and local diagnostic data stay on-device by default.
- No telemetry, ads, Firebase, Crashlytics, or Google Play Services.
- Full diagnostics belong to native apps; the web app only uses capabilities browsers can safely provide.
- Device integrations start read-only.
- No private server is required for the core product.
- F-Droid compatibility is a first-class constraint.
- Performance and HiDPI rendering are requirements, not cleanup work.

## Branches

- `main`: stable foundation and releases.
- `develop`: active development.

## License

Netsira is licensed under **GNU AGPL-3.0-or-later**.

See [LICENSE](LICENSE).
