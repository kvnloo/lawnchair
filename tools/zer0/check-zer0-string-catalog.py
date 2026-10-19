#!/usr/bin/env python3
"""Check source-backed string inventory without editing Android resources."""

import json
from pathlib import Path
import re
import sys
import unicodedata
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[2]
CATALOG = ROOT / "docs/zer0/qualification/string-catalog.json"
REQUIRED = {
    "key", "source", "source_text", "source_translatable",
    "recommend_translatable", "placeholders", "usage", "context",
}
PLACEHOLDER = re.compile(r"(?<!%)%(?!%)(?:([1-9]\d*)\$)?([dsbf])")
KEY = re.compile(r"^[a-z][a-z0-9_]*$")


def markers(text):
    return [
        {"position": int(pos) if pos else None, "type": kind}
        for pos, kind in PLACEHOLDER.findall(text)
    ]


def unsafe(text):
    return any(
        (ord(c) < 0x20 and c not in "\n\t")
        or unicodedata.category(c) == "Cf"
        for c in text
    )


def validate(catalog, root=ROOT):
    errors = []
    if not isinstance(catalog, dict) or catalog.get("catalog_version") != 1:
        return ["catalog_version must equal 1"]
    entries = catalog.get("entries")
    if not isinstance(entries, list) or not entries:
        return ["entries must be a nonempty array"]

    observed = {}
    known_files = set()
    for index, item in enumerate(entries):
        label = f"entries[{index}]"
        if not isinstance(item, dict):
            errors.append(label + " must be an object")
            continue
        for field in REQUIRED - set(item):
            errors.append(f"{label} missing {field}")
        if set(item) - REQUIRED:
            errors.append(f"{label} unexpected fields: {sorted(set(item) - REQUIRED)}")
        name = item.get("key")
        source = item.get("source")
        if not isinstance(name, str) or not KEY.fullmatch(name):
            errors.append(f"{label} invalid key")
            continue
        if not isinstance(source, str) or source.startswith("/") or ".." in Path(source).parts:
            errors.append(f"{label} invalid source path")
            continue
        unique = (source, name)
        if unique in observed:
            errors.append(f"{label} duplicate key in source: {source}::{name}")
        observed[unique] = item
        known_files.add(source)
        for field in ("source_text", "usage", "context"):
            value = item.get(field)
            if not isinstance(value, str) or not value.strip() or unsafe(value):
                errors.append(f"{label} unsafe or empty {field}")
        for field in ("source_translatable", "recommend_translatable"):
            if not isinstance(item.get(field), bool):
                errors.append(f"{label} {field} must be boolean")
        if item.get("placeholders") != markers(item.get("source_text", "")):
            errors.append(f"{label} incorrect string placeholders")
    seen_source_keys = set()
    for source in sorted(known_files):
        path = root / source
        try:
            parsed = ET.parse(path).getroot()
        except (OSError, ET.ParseError) as exc:
            errors.append(f"{source}: XML unavailable or invalid: {exc}")
            continue
        if parsed.tag != "resources":
            errors.append(f"{source}: expected resources root")
            continue
        for node in parsed.findall("string"):
            name = node.attrib.get("name")
            if not name:
                errors.append(f"{source}: unnamed string")
                continue
            key = (source, name)
            if key in seen_source_keys:
                errors.append(f"{source}: duplicate source key {name}")
                continue
            seen_source_keys.add(key)
            entry = observed.get(key)
            if entry is None:
                errors.append(f"{source}: missing catalog key {name}")
                continue
            value = "".join(node.itertext())
            if entry.get("source_text") != value:
                errors.append(f"{source}/{name}: source text changed")
            if entry.get("source_translatable") != (
                node.attrib.get("translatable") != "false"
            ):
                errors.append(f"{source}/{name}: translatability changed")
            if entry.get("placeholders") != markers(value):
                errors.append(f"{source}/{name}: positional placeholders changed")
            if unsafe(value):
                errors.append(f"{source}/{name}: unsafe control text")
    for source, name in observed:
        if (source, name) not in seen_source_keys:
            errors.append(f"{source}: catalog key not present in resources: {name}")
    return errors


def main():
    path = Path(sys.argv[1]) if len(sys.argv) == 2 else CATALOG
    try:
        catalog = json.loads(path.read_text(encoding="utf-8"))
    except (OSError, ValueError) as exc:
        print(f"INVALID catalog: {exc}", file=sys.stderr)
        return 1
    problems = validate(catalog)
    if problems:
        for message in problems:
            print("INVALID: " + message, file=sys.stderr)
        return 1
    print(f"VALID: {len(catalog['entries'])} Android string keys (catalog only; translation QA NOT RUN)")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
