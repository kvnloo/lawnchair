#!/usr/bin/env python3
"""On-device in-app control benchmark through the DUMP-guarded Z0LiveControlProbe (adb only).

    S22ADB=/path/to/adb-wrapper python3 tools/zer0/z0live-control-bench.py OUT.jsonl

For each scenario: launch the app, read the tree N times (snapshot latency), run phone_do with
the spoken goal (tier-0 decision + act + confirm latency, landed/evidence), save the screen the
decision was made on (for held-out tier 1/2 scoring), then go back. Timings are the phone's own
(SystemClock inside the app) plus the adb broadcast wall time for reference.
"""

import json
import os
import shlex
import subprocess
import sys
import time

ADB = os.environ.get("S22ADB", "adb").split()
PROBE = "app.lawnchair.play.debug/app.lawnchair.zer0.voice.z0live.Z0LiveControlProbe"
SCENARIOS = [
    ("settings", "am start -a android.settings.SETTINGS", "open battery", "Battery"),
    ("settings", "am start -a android.settings.SETTINGS", "tap connections", "Connections"),
    ("settings", "am start -a android.settings.SETTINGS", "search settings", "Search"),
    ("chrome", "am start -n com.android.chrome/com.google.android.apps.chrome.Main", "tap the menu button", "More options"),
    ("chrome", "am start -n com.android.chrome/com.google.android.apps.chrome.Main", "show my tabs", "tabs"),
    ("gmail", "monkey -p com.google.android.gm -c android.intent.category.LAUNCHER 1", "open the menu", "Open navigation drawer"),
    ("gmail", "monkey -p com.google.android.gm -c android.intent.category.LAUNCHER 1", "search my mail", "Search in mail"),
    ("gmail", "monkey -p com.google.android.gm -c android.intent.category.LAUNCHER 1", "write a new email", "Compose"),
    ("myfiles", "monkey -p com.sec.android.app.myfiles -c android.intent.category.LAUNCHER 1", "tap the menu button", "More options"),
    ("calendar", "monkey -p com.samsung.android.calendar -c android.intent.category.LAUNCHER 1", "open the menu", "drawer"),
    ("photos", "monkey -p com.google.android.apps.photos -c android.intent.category.LAUNCHER 1", "go to search", "Search"),
    ("contacts", "monkey -p com.samsung.android.app.contacts -c android.intent.category.LAUNCHER 1", "tap the menu button", "More options"),
]


def shell(cmd, timeout=30):
    return subprocess.run(ADB + ["shell", cmd], capture_output=True, text=True, timeout=timeout).stdout


def probe(**extras):
    args = " ".join(f"--es {k} {shlex.quote(str(v))}" for k, v in extras.items())
    started = time.perf_counter()
    out = shell(f"am broadcast -n {PROBE} {args}")
    wall = (time.perf_counter() - started) * 1000
    data = out[out.find('data="') + 6: out.rfind('"')] if 'data="' in out else "{}"
    try:
        result = json.loads(data)
    except ValueError:
        result = {"ok": False, "status": "unparsed", "raw": out[-300:]}
    result["adb_wall_ms"] = round(wall, 1)
    return result


def main():
    out = open(sys.argv[1], "a")
    for app, launch, goal, expect in SCENARIOS:
        shell(launch)
        time.sleep(2.0)
        trees = [probe(cmd="tree") for _ in range(5)]
        screen = trees[-1]
        result = probe(cmd="do", goal=goal)
        time.sleep(0.6)
        after = probe(cmd="tree")
        row = {"app": app, "goal": goal, "expect_hint": expect,
               "tree_snapshot_ms": [t.get("snapshot_ms") for t in trees],
               "tree_probe_ms": [t.get("probe_ms") for t in trees],
               "tree_adb_wall_ms": [t.get("adb_wall_ms") for t in trees],
               "screen": {"package": screen.get("package"), "nodes": screen.get("nodes")},
               "do": result, "after_package": after.get("package"),
               "after_labels": [n.get("l") for n in (after.get("nodes") or [])][:25]}
        out.write(json.dumps(row) + "\n")
        out.flush()
        d = result.get("decision") or {}
        print(app, repr(goal), result.get("status"), "landed=", result.get("landed"),
              "target=", (result.get("target") or {}).get("label"), "conf=", d.get("confidence"),
              "ms=", result.get("ms"), "snap=", [t.get("snapshot_ms") for t in trees])
        for _ in range(3):
            shell("input keyevent KEYCODE_BACK")
            time.sleep(0.3)
        shell("input keyevent KEYCODE_HOME")
        time.sleep(0.8)


if __name__ == "__main__":
    main()
