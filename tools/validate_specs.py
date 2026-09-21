#!/usr/bin/env python3
import json
import math
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]

def load(path):
    with (ROOT / path).open("r", encoding="utf-8") as fh:
        return json.load(fh)

def fspl(v):
    return 32.44 + 20 * math.log10(v["distanceKm"]) + 20 * math.log10(v["frequencyMHz"])

def fresnel(v):
    wavelength = 0.299792458 / v["frequencyGHz"]
    d1 = v["d1Km"] * 1000.0
    d2 = v["d2Km"] * 1000.0
    return math.sqrt(wavelength * d1 * d2 / (d1 + d2))

def eirp(v):
    return v["txPowerDbm"] + v["antennaGainDbi"] - v["cableLossDb"]

def dbm_to_mw(v):
    return 10 ** (v["dbm"] / 10.0)

ops = {
    "fspl": fspl,
    "fresnel": fresnel,
    "eirp": eirp,
    "dbmToMw": dbm_to_mw,
}

vectors = load("spec/calculators/rf-vectors.json")
for case in vectors["cases"]:
    operation = case["operation"]
    if operation not in ops:
        raise SystemExit(f"Unknown RF operation: {operation}")
    actual = ops[operation](case["input"])
    if abs(actual - case["expected"]) > case["tolerance"]:
        raise SystemExit(
            f"RF vector failed: {case['name']}: expected {case['expected']}, got {actual}"
        )

report = load("spec/diagnostics/report.schema.json")
required = set(report.get("required", []))
expected_required = {"schemaVersion", "id", "mode", "platform", "startedAt", "stages", "findings"}
if not expected_required.issubset(required):
    raise SystemExit("Report schema lost required core fields")

findings = load("spec/diagnostics/findings.json")
codes = findings["codes"]
if len(codes) != len(set(codes)):
    raise SystemExit("Duplicate finding codes")
for code in codes:
    if not code or any(ch not in "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789_" for ch in code):
        raise SystemExit(f"Invalid finding code: {code}")

capabilities = load("spec/devices/capabilities.schema.json")
if "capabilities" not in capabilities.get("properties", {}):
    raise SystemExit("Device capability schema is missing capabilities")

monitoring = load("spec/diagnostics/monitoring.json")
alignment = monitoring.get("alignment", {})
stability = monitoring.get("stability", {})
if alignment.get("defaultIntervalMs", 0) <= 0:
    raise SystemExit("Alignment polling interval must be positive")
if stability.get("defaultDurationSeconds", 0) <= 0:
    raise SystemExit("Stability duration must be positive")
if stability.get("defaultIntervalMs", 0) <= 0:
    raise SystemExit("Stability polling interval must be positive")

required_alignment = {"sampleCount", "bestSignalDbm", "worstSignalDbm", "averageSnrDb", "maxChainDeltaDb"}
required_stability = {"sampleCount", "probeLossPercent", "averageLatencyMs", "jitterMs", "bestSignalDbm", "worstSignalDbm", "signalSpreadDb", "averageSnrDb"}
if not required_alignment.issubset(set(alignment.get("summary", []))):
    raise SystemExit("Alignment summary contract is incomplete")
if not required_stability.issubset(set(stability.get("summary", []))):
    raise SystemExit("Stability summary contract is incomplete")

monitoring_vectors = load("spec/diagnostics/monitoring-vectors.json")

alignment_samples = monitoring_vectors["alignment"]["samples"]
alignment_expected = monitoring_vectors["alignment"]["expected"]
alignment_signals = [x["signalDbm"] for x in alignment_samples if x["signalDbm"] is not None]
alignment_snr = [x["snrDb"] for x in alignment_samples if x["snrDb"] is not None]
alignment_deltas = [
    max(x["chains"]) - min(x["chains"])
    for x in alignment_samples
    if len(x.get("chains", [])) >= 2
]
alignment_actual = {
    "sampleCount": len(alignment_samples),
    "bestSignalDbm": max(alignment_signals),
    "worstSignalDbm": min(alignment_signals),
    "averageSnrDb": sum(alignment_snr) / len(alignment_snr),
    "maxChainDeltaDb": max(alignment_deltas),
}

stability_samples = monitoring_vectors["stability"]["samples"]
stability_expected = monitoring_vectors["stability"]["expected"]
stability_latency = [x["tcpLatencyMs"] for x in stability_samples if x["tcpLatencyMs"] is not None]
stability_signal = [x["signalDbm"] for x in stability_samples if x["signalDbm"] is not None]
stability_snr = [x["snrDb"] for x in stability_samples if x["snrDb"] is not None]
stability_jitter_deltas = [
    abs(stability_latency[i] - stability_latency[i - 1])
    for i in range(1, len(stability_latency))
]
best_signal = max(stability_signal)
worst_signal = min(stability_signal)
stability_actual = {
    "sampleCount": len(stability_samples),
    "probeLossPercent": (len(stability_samples) - len(stability_latency)) * 100.0 / len(stability_samples),
    "averageLatencyMs": sum(stability_latency) / len(stability_latency),
    "jitterMs": sum(stability_jitter_deltas) / len(stability_jitter_deltas),
    "bestSignalDbm": best_signal,
    "worstSignalDbm": worst_signal,
    "signalSpreadDb": best_signal - worst_signal,
    "averageSnrDb": sum(stability_snr) / len(stability_snr),
}

for label, actual, expected in (
    ("alignment", alignment_actual, alignment_expected),
    ("stability", stability_actual, stability_expected),
):
    for key, expected_value in expected.items():
        actual_value = actual[key]
        if isinstance(expected_value, (int, float)):
            if abs(actual_value - expected_value) > 1e-6:
                raise SystemExit(
                    f"{label} monitoring vector failed for {key}: "
                    f"expected {expected_value}, got {actual_value}"
                )
        elif actual_value != expected_value:
            raise SystemExit(
                f"{label} monitoring vector failed for {key}: "
                f"expected {expected_value}, got {actual_value}"
            )

print(
    f"Validated {len(vectors['cases'])} RF vectors, {len(codes)} finding codes, "
    "monitoring contracts/vectors, and core schemas."
)
