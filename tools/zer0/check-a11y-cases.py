#!/usr/bin/env python3
"""Validate source-grounded accessibility qualification cases. No device access."""

import json
from pathlib import Path
import sys

DEFAULT = Path(__file__).resolve().parents[2] / "docs/zer0/qualification/a11y-cases.json"
FIELDS = {
    "id", "source", "source_observation", "preconditions", "expected_observation",
    "evidence_class", "rollback", "result", "observed_state", "evidence_ref",
}
RESULTS = {"NOT_RUN", "PASS", "FAIL", "BLOCKED"}
EVIDENCE = {"screen-reader", "android-device", "assistive-tech", "manual-visual"}


def validate(value):
    errors = []
    if not isinstance(value, dict) or value.get("catalog_version") != 1:
        return ["catalog_version must equal integer 1"]
    items = value.get("cases")
    if not isinstance(items, list) or not items:
        return ["cases must be a nonempty array"]
    seen = set()
    for index, item in enumerate(items):
        key = f"cases[{index}]"
        if not isinstance(item, dict):
            errors.append(key + " must be an object")
            continue
        missing = FIELDS - set(item)
        extra = set(item) - FIELDS
        if missing:
            errors.append(key + " missing: " + ", ".join(sorted(missing)))
        if extra:
            errors.append(key + " unrecognized: " + ", ".join(sorted(extra)))
        identifier = item.get("id")
        if not isinstance(identifier, str) or not identifier.startswith("LC-A11Y-"):
            errors.append(key + " invalid stable id")
        elif identifier in seen:
            errors.append(key + " duplicate id: " + identifier)
        else:
            seen.add(identifier)
        for field in FIELDS - {"id", "result", "evidence_class", "evidence_ref"}:
            v = item.get(field)
            if not isinstance(v, str) or not v.strip():
                errors.append(key + " requires nonempty " + field)
        if item.get("result") not in RESULTS:
            errors.append(key + " unsupported result")
        if item.get("evidence_class") not in EVIDENCE:
            errors.append(key + " unsupported evidence_class")
        if item.get("result") == "NOT_RUN":
            if item.get("observed_state") != "NOT_OBSERVED" or item.get("evidence_ref") is not None:
                errors.append(key + " NOT_RUN must have no observed evidence")
        elif item.get("result") in {"PASS", "FAIL"}:
            if item.get("observed_state") == "NOT_OBSERVED":
                errors.append(key + " result requires observed_state")
            if not isinstance(item.get("evidence_ref"), str) or not item["evidence_ref"].strip():
                errors.append(key + " result requires evidence_ref")
    return errors


def main():
    target = Path(sys.argv[1]) if len(sys.argv) == 2 else DEFAULT
    try:
        data = json.loads(target.read_text(encoding="utf-8"))
    except (OSError, ValueError) as exc:
        print(f"INVALID catalog: {exc}", file=sys.stderr)
        return 1
    errors = validate(data)
    if errors:
        for error in errors:
            print("INVALID " + error, file=sys.stderr)
        return 1
    print(f"VALID: {len(data['cases'])} accessibility cases (validation only; no device tests)")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
