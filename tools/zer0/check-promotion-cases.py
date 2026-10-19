#!/usr/bin/env python3
"""Validate source-to-test search acceptance case coverage without network access."""

import argparse
import json
import re
import sys
from pathlib import Path

SHA = re.compile(r"[0-9a-f]{40}")
ID = re.compile(r"(HIDDEN|STALE)-[A-Z0-9-]+")
STATES = {"PASS", "FAIL", "NOT_RUN", "BLOCKED", "LIMITED"}
REQUIRED = {
    "HIDDEN-NEVER-EXACT", "HIDDEN-IF-PARTIAL", "HIDDEN-IF-FULL",
    "HIDDEN-ALWAYS-ZERO", "HIDDEN-UNKNOWN",
    "STALE-AB", "STALE-ACLEAR", "STALE-ACLEARA",
    "STALE-KEYBOARD-ZERO", "STALE-HOME-REOPEN",
}


def validate(data):
    errors = []
    if not isinstance(data, dict) or data.get("schema_version") != 1:
        return ["unsupported case data schema"]
    if data.get("upstream") != "LawnchairLauncher/lawnchair" or data.get("downstream") != "kvnloo/lawnchair":
        errors.append("incorrect repository identity")
    if data.get("queue") != "https://github.com/kvnloo/lawnchair/issues/10":
        errors.append("must use canonical promotion queue #10")
    cases = data.get("cases")
    if not isinstance(cases, list):
        return errors + ["cases must be a list"]
    seen = set()
    for item in cases:
        if not isinstance(item, dict):
            errors.append("case must be object")
            continue
        name = item.get("id")
        if not isinstance(name, str) or not ID.fullmatch(name):
            errors.append("invalid case ID")
            continue
        if name in seen:
            errors.append("duplicate case ID: " + name)
        seen.add(name)
        carrier = item.get("carrier")
        if not isinstance(carrier, dict):
            carrier = {}
        if carrier.get("pr") not in (3, 4):
            errors.append(name + ": carrier PR must be #3 or #4")
        head = carrier.get("head")
        if not isinstance(head, str) or not SHA.fullmatch(head):
            errors.append(name + ": invalid exact source head")
        path = carrier.get("source_path")
        if not isinstance(path, str) or not path.strip():
            errors.append(name + ": source path missing")
        if not isinstance(item.get("scenario"), str) or not item["scenario"].strip():
            errors.append(name + ": scenario missing")
        if not isinstance(item.get("expected"), str) or not item["expected"].strip():
            errors.append(name + ": expected observation missing")
        if not isinstance(item.get("test_target"), str) or not item["test_target"].strip():
            errors.append(name + ": test target missing")
        credits = item.get("credits")
        if not isinstance(credits, list) or not credits or not all(isinstance(c, str) and c.strip() for c in credits):
            errors.append(name + ": contributor/source provenance missing")
        if type(item.get("negative_control")) is not bool:
            errors.append(name + ": negative_control flag missing")
        ev = item.get("evidence")
        if not isinstance(ev, dict):
            ev = {}
        for key in ("host", "android", "device", "review"):
            if ev.get(key) not in STATES:
                errors.append(name + ": invalid " + key + " evidence state")
        status = item.get("status")
        if status not in ("QUALIFICATION_NEEDED", "HOLD", "READY_FOR_HUMAN_PROMOTION_REVIEW"):
            errors.append(name + ": unknown promotion status")
        if item.get("negative_control") and status == "READY_FOR_HUMAN_PROMOTION_REVIEW":
            errors.append(name + ": known-limitation negative control cannot be ready")
        if status == "READY_FOR_HUMAN_PROMOTION_REVIEW":
            if any(ev.get(key) != "PASS" for key in ("android", "device", "review")):
                errors.append(name + ": Android/device/review gate not passed")
            if ev.get("validated_head") != head:
                errors.append(name + ": validated head differs from source")
            refs = ev.get("verification_links")
            if not isinstance(refs, list) or not refs or not all(isinstance(x, str) and x.startswith("https://") for x in refs):
                errors.append(name + ": verified native/owner evidence URLs required")
    missing = REQUIRED - seen
    if missing:
        errors.append("missing required coverage: " + ", ".join(sorted(missing)))
    return errors


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("cases", type=Path)
    args = parser.parse_args()
    try:
        data = json.loads(args.cases.read_text(encoding="utf-8"))
    except (OSError, UnicodeError, ValueError) as exc:
        print("INVALID:", exc, file=sys.stderr)
        return 2
    errors = validate(data)
    for error in errors:
        print("INVALID:", error, file=sys.stderr)
    if errors:
        return 1
    print("CASE METADATA CONSISTENT; Android/device/ownership evidence not independently verified")
    return 0


if __name__ == "__main__":
    sys.exit(main())
