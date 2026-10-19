#!/usr/bin/env python3
"""Offline CI/APK/device evidence consistency checks (not independent attestation)."""

import argparse
import hashlib
import json
import re
import sys
import zipfile
from pathlib import Path

SHA = re.compile(r"[0-9a-f]{40}")
SHA256 = re.compile(r"[0-9a-fA-F]{64}")
STATES = {"success", "failure", "cancelled", "skipped", "pending", "in_progress"}


def obj(value):
    return value if isinstance(value, dict) else {}


def valid(value, pattern):
    return isinstance(value, str) and pattern.fullmatch(value) is not None


def digest(path):
    checksum = hashlib.sha256()
    with path.open("rb") as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b""):
            checksum.update(chunk)
    return checksum.hexdigest()


def validate(record, claim="ci", apk_path=None):
    errors = []
    root = obj(record)
    source = obj(root.get("source"))
    ci = obj(root.get("ci"))
    sha = source.get("sha")
    if root.get("schema_version") != 1:
        errors.append("schema_version must be 1")
    if not valid(sha, SHA):
        errors.append("source.sha must be lowercase 40-hex")
    if not isinstance(source.get("ref"), str) or not source["ref"].strip():
        errors.append("source.ref missing")
    if ci.get("head_sha") != sha:
        errors.append("CI head_sha mismatch")
    run_id = ci.get("run_id")
    if type(run_id) is not int or run_id < 1:
        errors.append("CI run_id invalid")
    if not str(ci.get("run_url", "")).startswith("https://github.com/"):
        errors.append("CI run_url missing")
    if ci.get("conclusion") != "success":
        errors.append("CI conclusion not success")
    jobs = ci.get("jobs")
    if not isinstance(jobs, list) or not jobs:
        errors.append("CI jobs missing")
        jobs = []
    required = 0
    seen = set()
    for entry in jobs:
        job = obj(entry)
        name = job.get("name")
        if not isinstance(name, str) or not name.strip() or name in seen:
            errors.append("CI job missing/duplicate name")
        seen.add(name)
        if type(job.get("required")) is not bool:
            errors.append("CI required flag invalid")
        if job.get("state") not in STATES:
            errors.append("CI job state invalid")
        if job.get("required") is True:
            required += 1
            if job.get("state") != "success":
                errors.append("required job not successful: " + str(name))
    if not required:
        errors.append("no required CI jobs")

    artifact = obj(root.get("artifact"))
    if claim in ("artifact", "device"):
        if artifact.get("source_sha") != sha:
            errors.append("artifact source SHA mismatch")
        if artifact.get("run_id") != run_id:
            errors.append("artifact run ID mismatch")
        if not artifact.get("package_id") or not artifact.get("variant"):
            errors.append("artifact package/variant missing")
        if not valid(artifact.get("sha256"), SHA256):
            errors.append("artifact SHA256 invalid")
        if not valid(artifact.get("signer_sha256"), SHA256):
            errors.append("signer certificate SHA256 invalid")
        if not str(artifact.get("url", "")).startswith("https://github.com/"):
            errors.append("artifact URL missing")
        if apk_path is None:
            errors.append("--apk is required for artifact/device claim")
        else:
            path = Path(apk_path)
            if not path.is_file():
                errors.append("APK file missing")
            else:
                if digest(path).lower() != str(artifact.get("sha256", "")).lower():
                    errors.append("APK SHA256 mismatch")
                if not zipfile.is_zipfile(path):
                    errors.append("APK is not a ZIP container")
                else:
                    with zipfile.ZipFile(path) as archive:
                        if "AndroidManifest.xml" not in archive.namelist():
                            errors.append("APK manifest absent")
    if claim == "device":
        device = obj(root.get("device"))
        if device.get("status") != "PASS":
            errors.append("device status not PASS")
        if device.get("owner_consented") is not True:
            errors.append("device owner consent missing")
        if device.get("source_sha") != sha:
            errors.append("device source SHA mismatch")
        if device.get("installed_apk_sha256") != artifact.get("sha256"):
            errors.append("device installed APK SHA256 mismatch")
        if not isinstance(device.get("test_target"), str) or not device["test_target"].strip():
            errors.append("device test target missing")
        if not isinstance(device.get("observations"), str) or not device["observations"].strip():
            errors.append("device observations missing")
    if claim not in ("ci", "artifact", "device"):
        errors.append("unknown claim")
    return errors


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("evidence", type=Path)
    parser.add_argument("--claim", choices=("ci", "artifact", "device"), default="ci")
    parser.add_argument("--apk", type=Path)
    args = parser.parse_args(argv)
    try:
        record = json.loads(args.evidence.read_text(encoding="utf-8"))
    except (OSError, UnicodeError, ValueError) as exc:
        print("INVALID JSON:", exc, file=sys.stderr)
        return 2
    errors = validate(record, args.claim, args.apk)
    for error in errors:
        print("INVALID:", error, file=sys.stderr)
    if errors:
        return 1
    print("METADATA CONSISTENT ONLY: not verified signing/build/install/device evidence")
    return 0


if __name__ == "__main__":
    sys.exit(main())
