# Netsira contributor instructions

## Product rules

Netsira is one product across platforms, not one UI copied everywhere.

- Android: Kotlin + Jetpack Compose.
- Desktop: C# + .NET + Avalonia UI. Windows is the first desktop target; Linux comes later.
- Web: React + TypeScript + Vite + Tabler Core.
- iOS is deferred and will be native Swift/SwiftUI.

Keep the same information architecture, terminology, semantic colors, icon meanings, report schema, and diagnostic states across platforms. Do not wrap the web app as the desktop app.

## Architecture

- Local-first by default.
- No mandatory backend.
- Device adapters are read-only until a later explicit milestone.
- Native apps may provide diagnostics the web cannot.
- All diagnostic conclusions must preserve evidence and support an unknown state instead of inventing certainty.

## Privacy / F-Droid

Do not add Firebase, Crashlytics, Google Play Services, advertising SDKs, hidden telemetry, or proprietary analytics.

Any new runtime dependency must have its license checked before merge. Update THIRD_PARTY_NOTICES.md when required.

## Licensing

Project license: SPDX `AGPL-3.0-or-later`.

Do not change the project license or add an incompatible dependency without an explicit decision.

## UI quality

- No raster UI chrome when a vector/SVG equivalent exists.
- Do not scale tiny bitmaps to create icons.
- Respect HiDPI scaling.
- Avoid expensive blur/transparency as a default design language.
- Keep network and disk work off the UI thread.
- Prefer virtualized controls for long collections.
- Desktop bindings should be compiled.
- Do not add UI frameworks just to generate a look; stay within the selected platform design system.

## Branch discipline

Work on `develop`. Keep `main` stable unless the task explicitly calls for a release/merge.
