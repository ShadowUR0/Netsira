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

print(f"Validated {len(vectors['cases'])} RF vectors, {len(codes)} finding codes, and core schemas.")
