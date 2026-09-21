# Netsira UX/UI principles

These rules apply to Web, Android, Windows, and future iOS.

## Evidence-backed principles

1. **Progressive disclosure**
   - Primary screens show the next useful action and the most important status.
   - Protocol names, raw values, privacy/provider details, and expert controls live under explicit details/advanced areas.

2. **Recognition over recall**
   - Use persistent labels, icons, status words, and consistent units.
   - Do not make users remember what an acronym or color means.

3. **Visible system status**
   - Every running diagnostic must show a clear running state.
   - Results are summarized first, raw measurements second.

4. **Visual hierarchy**
   - Overall health and actionable findings appear before raw measurements.
   - Use metric cards, bars, sparklines/charts, and status badges where they reduce reading.

5. **Forms**
   - Labels stay visible above fields.
   - On narrow/mobile layouts use a single column.
   - Helper text explains unusual fields such as local device URL or certificate trust.

6. **Navigation**
   - Navigation controls must switch actual views.
   - The active destination is visually distinct.
   - Android uses bottom navigation for top-level destinations; desktop/web use persistent navigation when space allows.

7. **Accessibility**
   - Pointer/touch targets are comfortably sized.
   - Keyboard focus is visible.
   - Color never carries meaning alone: pair it with text/icon/state.

## Netsira information architecture

- Diagnostics: guided tests and current results.
- Devices: local CPE discovery/connection and device health.
- Calculators: RF engineering tools.
- History: saved runs and comparisons.
- Settings: privacy, measurement providers, advanced behavior.

## Result language

Prefer:
- Good / Needs attention / Problem / Not available
- Signal, stability, latency, loss, download, upload

Defer or explain:
- NDT7
- CPE
- SNR
- chain imbalance
- CSRF/session details


## Research references

- Nielsen Norman Group — Progressive Disclosure:
  https://www.nngroup.com/articles/progressive-disclosure/
- W3C — WCAG 2.2, including focus visibility and target-size guidance:
  https://www.w3.org/TR/WCAG22/
- Baymard Institute — Mobile form labels above fields:
  https://baymard.com/research-articles/mobile-form-usability-label-position
- Baymard Institute — Avoid extensive multi-column forms:
  https://baymard.com/research-articles/avoid-multi-column-forms
- Fluent 2 — Navigation:
  https://fluent2.microsoft.design/components/web/react/core/nav/usage
- Fluent 2 — Iconography:
  https://fluent2.microsoft.design/iconography

These sources inform the interaction principles; Netsira does not copy any source UI verbatim.
