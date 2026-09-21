# Diagnostic semantics

## Quick test

The quick test is intentionally short and safe. It does not attempt to infer a CPE problem without CPE evidence.

Native implementations collect, when available:

- active network state
- default gateway presence
- DNS resolution
- repeated TCP connection latency to a stable public HTTPS endpoint
- loss across those connection attempts
- jitter calculated from consecutive successful latency samples

The web implementation is browser-safe and uses browser connectivity state plus HTTPS timing to the M-Lab Locate API. It must not claim equivalence with native ICMP/TCP diagnostics.

## Initial finding thresholds

- loss >= 20% -> problem
- loss > 0% -> warning
- average latency >= 200 ms -> warning
- jitter >= 50 ms -> warning
- DNS failure -> problem
- no active network -> problem

Findings must include evidence and avoid blaming an ISP/CPE stage unless that stage was directly tested.
