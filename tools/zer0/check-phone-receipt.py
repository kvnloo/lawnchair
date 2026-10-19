#!/usr/bin/env python3
"""Static provenance gate only: no adb/install/microphone/screenshot execution."""

import json
from pathlib import Path
import re
import sys

TEMPLATE = (Path(__file__).resolve().parents[2] /
            "docs/zer0/qualification/phone-receipt.template.json")
REQUIRED_PROVENANCE = {
    "source_sha", "ci_source_sha", "ci_run_url", "ci_artifact",
    "apk_sha256", "apk_package", "apk_version", "signing_cert_sha256",
}
SHA1 = re.compile(r"^[0-9a-fA-F]{40}$")
SHA256 = re.compile(r"^[0-9a-fA-F]{64}$")
CI = re.compile(r"^https://github\.com/[^/\s]+/[^/\s]+/actions/runs/[0-9]+$")
RESULTS = {"NOT_RUN", "PASS", "FAIL", "BLOCKED"}
CONSENT = {"none", "home", "assistant_role", "microphone", "capture"}
EVIDENCE = {"device-observation", "device-capture-redacted", "apk-metadata"}


def missing(value):
    return not isinstance(value, str) or not value.strip()


def complete_provenance(provenance, device):
    if not isinstance(provenance, dict) or set(provenance) != REQUIRED_PROVENANCE:
        return False
    if not SHA1.fullmatch(provenance.get("source_sha") or ""):
        return False
    if provenance["source_sha"] != provenance.get("ci_source_sha"):
        return False
    if not SHA256.fullmatch(provenance.get("apk_sha256") or ""):
        return False
    if not SHA256.fullmatch(provenance.get("signing_cert_sha256") or ""):
        return False
    if not CI.fullmatch(provenance.get("ci_run_url") or ""):
        return False
    if any(missing(provenance.get(k)) for k in (
        "ci_artifact", "apk_package", "apk_version"
    )):
        return False
    return isinstance(device, dict) and all(not missing(device.get(k)) for k in (
        "model", "android_version", "oneui_version"
    )) and device.get("home_fallback_confirmed") is True


def validate(doc):
    errors = []
    if not isinstance(doc, dict) or doc.get("receipt_version") != 1:
        return ["receipt_version must be 1"]
    cases = doc.get("cases")
    if not isinstance(cases, list) or not cases:
        return ["cases must be a nonempty array"]
    provenance, device, consent = (doc.get(k) for k in (
        "provenance", "device", "consent",
    ))
    if not isinstance(consent, dict) or not {
        "install", "home", "assistant_role", "microphone", "capture",
    }.issubset(consent):
        errors.append("consent fields missing")
        consent = {}
    case_ids = set()
    for index, case in enumerate(cases):
        label = f"cases[{index}]"
        if not isinstance(case, dict):
            errors.append(label + " must be object")
            continue
        ident = case.get("id")
        if not isinstance(ident, str) or not re.fullmatch(r"LC-PHONE-\d{3}", ident):
            errors.append(label + " invalid id")
        elif ident in case_ids:
            errors.append(label + " duplicate id " + ident)
        else:
            case_ids.add(ident)
        for field in ("kind", "preconditions", "expected_observation"):
            if missing(case.get(field)):
                errors.append(label + " missing " + field)
        result = case.get("result")
        if result not in RESULTS:
            errors.append(label + " invalid result")
            continue
        if case.get("consent_scope") not in CONSENT:
            errors.append(label + " invalid consent_scope")
        latency = case.get("latency_samples_ms")
        if not isinstance(latency, list) or any(
            not isinstance(v, (int, float)) or isinstance(v, bool)
            or not 0 <= v < 100000 for v in latency
        ):
            errors.append(label + " invalid measured latency samples")
        rollback = case.get("rollback_result")
        if rollback not in RESULTS | {"NOT_APPLICABLE"}:
            errors.append(label + " invalid rollback_result")
        if result == "NOT_RUN":
            if any(case.get(field) is not None for field in (
                "exact_command", "observed_state", "evidence_class",
                "evidence_ref", "rollback_observation",
            )) or latency or rollback != "NOT_RUN":
                errors.append(label + " NOT_RUN must not imply observation/latency/rollback")
        elif result == "BLOCKED":
            # A block is a real recorded state only when explained.
            if missing(case.get("observed_state")):
                errors.append(label + " BLOCKED needs blocker explanation")
        elif result in ("PASS", "FAIL"):
            for field in ("exact_command", "observed_state", "evidence_ref",
                          "rollback_observation"):
                if missing(case.get(field)):
                    errors.append(label + " evidence missing " + field)
            if case.get("evidence_class") not in EVIDENCE:
                errors.append(label + " missing evidence class")
            if rollback not in {"PASS", "FAIL", "BLOCKED", "NOT_APPLICABLE"}:
                errors.append(label + " rollback must be recorded")
            if result == "PASS":
                if not complete_provenance(provenance, device):
                    errors.append(label + " PASS lacks exact APK/source/signing/device provenance")
                required = case.get("consent_scope")
                if required != "none" and consent.get(required) is not True:
                    errors.append(label + " PASS lacks explicit " + str(required) + " consent")
                if case.get("evidence_class") == "device-capture-redacted" and consent.get("capture") is not True:
                    errors.append(label + " PASS lacks capture consent")
    return errors


def main():
    path = Path(sys.argv[1]) if len(sys.argv) == 2 else TEMPLATE
    try:
        document = json.loads(path.read_text(encoding="utf-8"))
    except (OSError, ValueError) as exc:
        print("INVALID: " + str(exc), file=sys.stderr)
        return 1
    issues = validate(document)
    for issue in issues:
        print("INVALID " + issue, file=sys.stderr)
    if issues:
        return 1
    print(f"VALID: {len(document['cases'])} receipt definitions; device evidence NOT inferred")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
