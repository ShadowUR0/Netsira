# Performance and rendering policy

Desktop performance is an architectural requirement.

## Avalonia desktop

- Target .NET 10 LTS.
- Use stable Avalonia 12.x releases.
- Keep compiled bindings enabled.
- Keep XAML compiled at build time.
- Never perform network, DNS, file or device I/O on the UI thread.
- Prefer virtualization for potentially long result/history collections.
- Prefer StreamGeometry/vector primitives for custom graphics.
- Avoid loading full-size raster images only to scale them down.
- Native AOT is a release-track goal after dependency compatibility is proven.
- Judge performance using Release builds, not Debug builds.

Avalonia uses Skia and GPU acceleration by default. A future diagnostics/about screen should expose the active renderer so software-rendering fallbacks are visible.

## Android

- Keep network operations in coroutines off the main dispatcher.
- Prefer stable immutable UI state.
- Avoid unnecessary recomposition.
- Use vector drawables for core iconography.

## Web

- Do not ship charting or device libraries until used.
- Code-split feature areas as the application grows.
- Avoid Tabler optional vendor bundles in the main bundle.

## Regression rule

Do not accept a visually fancier implementation if it materially worsens scrolling, launch time, memory use or sustained diagnostic sampling.
