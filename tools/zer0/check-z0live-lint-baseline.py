#!/usr/bin/env python3
"""Verify that supplemental lint diagnostics stay pinned to byte-identical inherited files."""
import hashlib
import json
from pathlib import Path
import subprocess
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[2]
proof = json.loads((ROOT / "docs/zer0/z0live-lint-baseline-provenance.json").read_text())
base = proof["base_commit"]
assert base == "ba091219e2d7c4cac8f173754d0c234f9ff866f0"
baseline = ET.parse(ROOT / "z0live-upstream-lint-baseline.xml").getroot()
for issue in baseline:
    for location in issue.findall("location"):
        path = Path(location.attrib["file"])
        assert not path.is_absolute() and ".." not in path.parts
        assert "/z0live/" not in str(path) and not path.name.startswith("z0live_")
keys = set()
for issue in baseline:
    location = issue.find("location")
    assert location is not None
    keys.add((issue.get("id"), issue.get("message"), location.get("file"), location.get("line")))
for path, expected in proof["unchanged_files_sha256"].items():
    data = subprocess.check_output(["git", "show", f"{base}:{path}"], cwd=ROOT)
    assert hashlib.sha256(data).hexdigest() == expected, f"Base drift: {path}"
    assert hashlib.sha256((ROOT / path).read_bytes()).hexdigest() == expected, f"Changed inherited lint file: {path}; re-evaluate its supplemental baseline"
for item in proof["supplemental_diagnostics"]:
    assert item["id"] in {"NewApi", "WrongConstant"}
    assert item["file"] in proof["unchanged_files_sha256"]
    assert (item["id"], item["message"], item["file"], item["line"]) in keys
assert len(baseline) == proof["native_baseline_entries"] + len(proof["supplemental_diagnostics"])
print(f"PASS: {len(proof['supplemental_diagnostics'])} legacy diagnostics pinned to {len(proof['unchanged_files_sha256'])} unchanged base files; no z0live sources baselined")
