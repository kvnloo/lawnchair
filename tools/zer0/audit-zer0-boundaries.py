#!/usr/bin/env python3
"""Read-only source/import inventory; no package moves or GitHub API calls."""

from pathlib import Path
import json
import re
import sys

ROOT = Path(__file__).resolve().parents[2]
SOURCE = Path("lawnchair/src/app/lawnchair/zer0")
PURE = {
    "voice/PhoneActionGate.java", "voice/JevFastDecisionPolicy.java",
    "voice/VoiceCommand.java",
    "spaces/SpaceModel.java", "spaces/SpacePins.java",
    "spaces/SpacePageBinding.java", "spaces/SpaceAppSearch.java",
    "voice/z0live/Z0LiveTurnState.java", "voice/z0live/Z0LiveAudioState.java",
}
ANDROID = ("android.", "androidx.", "com.android.launcher3.", "com.google.android.")
NETWORK = ("java.net.", "javax.net.", "okhttp3.", "retrofit2.", "io.ktor.", "org.webrtc.")
GENERATED_SEGMENTS = {
    "build", ".gradle", "generated", "gen", "out", ".idea", "node_modules",
}
ENTRYPOINTS = {
    "manifest": "lawnchair/AndroidManifest.xml",
    "qsb": "lawnchair/src/app/lawnchair/qsb/LawnQsbLayout.kt",
    "preferences": "lawnchair/src/app/lawnchair/ui/preferences/destinations/ExperimentalFeaturesPreferences.kt",
}
IMPORT = re.compile(r"^\s*import\s+(?:static\s+)?([A-Za-z_][\w.]*)(?:\s+as\s+\w+)?", re.M)


def classify(path, text):
    """Pure is a *source-boundary check*, not a build/permission claim."""
    imports = sorted(set(IMPORT.findall(text)))
    android = [value for value in imports if value.startswith(ANDROID)]
    network = [value for value in imports if value.startswith(NETWORK)]
    resources = bool(re.search(r"\bR\.(?:string|drawable|layout|xml)\.", text))
    return {
        "imports": imports, "android_imports": android,
        "network_imports": network, "resource_reference": resources,
    }


def is_generated(path):
    return bool(GENERATED_SEGMENTS.intersection(path.parts))


def scan(root=ROOT):
    base = root / SOURCE
    errors = []
    source_files = []
    for path in sorted(base.rglob("*")) if base.is_dir() else []:
        if not path.is_file() or path.suffix not in {".java", ".kt"}:
            continue
        relative = path.relative_to(base)
        if is_generated(relative):
            continue
        value = path.read_text(encoding="utf-8")
        info = classify(relative, value)
        category = "pure-boundary" if relative.as_posix() in PURE else "Android/launcher adapter or other"
        if category == "pure-boundary" and (
            info["android_imports"] or info["network_imports"] or info["resource_reference"]
        ):
            errors.append(
                f"{relative}: pure source imports Android/network or references Android R resources"
            )
        source_files.append({
            "path": (SOURCE / relative).as_posix(),
            "kind": category,
            "import_count": len(info["imports"]),
            "android_imports": info["android_imports"],
            "network_imports": info["network_imports"],
            "resource_reference": info["resource_reference"],
        })
    if not source_files:
        errors.append("no zer0 Java/Kotlin sources found")
    seams = {}
    for name, relative in ENTRYPOINTS.items():
        path = root / relative
        if path.is_file():
            source = path.read_text(encoding="utf-8")
            seams[name] = {
                "path": relative, "exists": True,
                "references_zer0": ("zer0" in source or "z0live" in source),
            }
        else:
            seams[name] = {"path": relative, "exists": False,
                           "references_zer0": False}
    return {
        "source_root": SOURCE.as_posix(),
        "source_file_count": len(source_files),
        "pure_expected_present": sorted(
            p for p in PURE if (base / p).is_file()
        ),
        "files": source_files,
        "entrypoints": seams,
        "violations": errors,
    }


def main():
    root = Path(sys.argv[1]).resolve() if len(sys.argv) == 2 else ROOT
    result = scan(root)
    print(json.dumps(result, indent=2, sort_keys=True))
    return int(bool(result["violations"]))


if __name__ == "__main__":
    raise SystemExit(main())
