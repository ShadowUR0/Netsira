# Netsira design system rules

Netsira must feel like the same product across Android, desktop, web and future iOS without forcing the same widgets onto every platform.

## Shared

- Information architecture and feature names.
- Semantic design tokens.
- Severity vocabulary.
- Tabler icon meanings.
- Report layout hierarchy.
- Metric names and units.
- Empty/loading/error/unknown concepts.

## Platform-specific

- Navigation controls.
- Window and dialog behavior.
- Density.
- Keyboard/mouse affordances.
- Touch affordances.
- Native system integration.

## Rendering quality

- Prefer vectors for icons and UI graphics.
- Raster assets must ship at appropriate densities and never be the source of core UI icons.
- Respect per-monitor DPI changes on desktop.
- Text remains real text, never rasterized artwork.
- Avoid permanent blur/translucency effects that create unnecessary GPU cost.
- Charts render from vector/canvas primitives, not screenshots.

The Phase 1 accent is provisional. Brand identity can change later without changing semantic token names.
