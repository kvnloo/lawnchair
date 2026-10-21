#!/usr/bin/env python3
"""On-device in-app control benchmark through the DUMP-guarded Z0LiveControlProbe (adb only).

    S22ADB=/path/to/adb-wrapper S22LOCK=/path/to/s22-ui.lock \
        python3 tools/zer0/z0live-control-bench.py OUT.jsonl [--u2]

Each scenario is one UI burst held under an exclusive flock(2) on S22LOCK (the same lock the
`flock` command takes), so workers sharing the phone never interleave: launch the app, read the
tree 5 times (snapshot latency), optionally read it 5 times through phone-harness's
uiautomator2 server (--u2; curl on the phone, so neither path pays host transport), then run
each spoken goal with phone_do (tier-0 decision + act + confirm latency, landed/evidence), save
the screen each decision was made on (held-out tier 1/2 scoring), and go home.

Timings: snapshot_ms/probe_ms are the phone's own clock inside Lawnchair; adb_wall_ms is the
host's wall time for the whole `adb shell am broadcast` (process start included), reference only.
"""

import fcntl
import json
import os
import shlex
import subprocess
import sys
import time

ADB = os.environ.get("S22ADB", "adb").split()
LOCK = os.environ.get("S22LOCK", "")
PROBE = "app.lawnchair.play.debug/app.lawnchair.zer0.voice.z0live.Z0LiveControlProbe"
U2_DIR = "/data/local/tmp/ph-u2"
U2_PORT = 9118


def launcher(pkg):
    return f"monkey -p {pkg} -c android.intent.category.LAUNCHER 1"


# (app, launch command, [(spoken goal, expected label hint)...]); goals run in order in one burst.
SCENARIOS = [
    ("settings", "am start -a android.settings.SETTINGS", [("open battery", "Battery")]),
    ("chrome", "am start -n com.android.chrome/com.google.android.apps.chrome.Main", [("tap the menu button", "More options")]),
    ("chrome", "am start -n com.android.chrome/com.google.android.apps.chrome.Main", [("show my tabs", "tabs")]),
    ("gmail", launcher("com.google.android.gm"), [("open the menu", "Open navigation drawer")]),
    ("gmail", launcher("com.google.android.gm"), [("search my mail", "Search in mail")]),
    ("gmail", launcher("com.google.android.gm"), [("write a new email", "Compose")]),
    ("myfiles", launcher("com.sec.android.app.myfiles"), [("tap the menu button", "More options")]),
    ("calendar", launcher("com.samsung.android.calendar"), [("open the menu", "drawer")]),
    ("photos", launcher("com.google.android.apps.photos"), [("go to search", "Search")]),
    ("contacts", launcher("com.samsung.android.app.contacts"), [("tap the menu button", "More options")]),
    # The owner's complaint, on a logged-out ChatGPT: welcome sheet -> home -> sidebar.
    ("chatgpt", "am force-stop com.openai.chatgpt; " + launcher("com.openai.chatgpt"),
     [("skip", "Skip"), ("Can you click on the menu button", "Open sidebar"), ("go back", None)]),
    ("playstore", launcher("com.android.vending"), [("search for apps", "Search")]),
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


def u2_rpc_cmd(method, params):
    body = json.dumps({"jsonrpc": "2.0", "id": 1, "method": method, "params": params}, separators=(",", ":"))
    return f"curl -s -m 15 -d {shlex.quote(body)} http://127.0.0.1:{U2_PORT}/jsonrpc/0"


def u2_up():
    """Start phone-harness's u2 server if needed and set FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES
    at once, so the phone's accessibility services (z0live's included) stay bound."""
    if shell(f"curl -s -m 1 http://127.0.0.1:{U2_PORT}/ping").strip() != "pong":
        shell(f"cd {U2_DIR} && setsid nohup sh run.sh 600 {U2_PORT} >/dev/null 2>&1 </dev/null &")
        for _ in range(80):
            if shell(f"curl -s -m 1 http://127.0.0.1:{U2_PORT}/ping").strip() == "pong":
                break
            time.sleep(0.25)
        else:
            raise RuntimeError("u2 server did not come up")
    cfg = json.loads(shell(u2_rpc_cmd("getConfigurator", [])))["result"]
    if cfg.get("uiAutomationFlags") != 1 or cfg.get("waitForIdleTimeout") != 0:
        cfg["uiAutomationFlags"], cfg["waitForIdleTimeout"] = 1, 0
        shell(u2_rpc_cmd("setConfigurator", [cfg]))
    shell(f"touch {U2_DIR}/last_use")


def u2_dumps(n):
    """n timed dumps on the phone itself: `date +%s%N` around curl, inside one adb shell."""
    cmd = u2_rpc_cmd("dumpWindowHierarchy", [False, 50, True])
    script = (f"for i in $(seq {n}); do a=$(date +%s%N); r=$({cmd}); b=$(date +%s%N); "
              f"echo \"$(( (b-a)/1000 )) $(echo \"$r\" | grep -o '<node' | wc -l)\"; done")
    rows = []
    for line in shell(script, timeout=120).split("\n"):
        parts = line.split()
        if len(parts) == 2 and parts[0].isdigit():
            rows.append({"ms": int(parts[0]) / 1000.0, "nodes": int(parts[1])})
    return rows


class Burst:
    def __enter__(self):
        self.fd = open(LOCK, "a") if LOCK else None
        if self.fd:
            fcntl.flock(self.fd, fcntl.LOCK_EX)
        return self

    def __exit__(self, *exc):
        if self.fd:
            fcntl.flock(self.fd, fcntl.LOCK_UN)
            self.fd.close()


def main():
    out = open(sys.argv[1], "a")
    use_u2 = "--u2" in sys.argv[2:]
    for app, launch, goals in SCENARIOS:
        with Burst():
            if use_u2:
                u2_up()
            shell(launch)
            time.sleep(2.5)
            trees = [probe(cmd="tree") for _ in range(5)]
            u2 = u2_dumps(5) if use_u2 else []
            steps = []
            for goal, expect in goals:
                screen = probe(cmd="tree") if steps else trees[-1]
                result = probe(cmd="back") if goal == "go back" else probe(cmd="do", goal=goal)
                time.sleep(0.8)
                after = probe(cmd="tree")
                steps.append({"goal": goal, "expect_hint": expect,
                              "screen": {"package": screen.get("package"), "nodes": screen.get("nodes")},
                              "do": result, "after_package": after.get("package"),
                              "after_labels": [n.get("l") for n in (after.get("nodes") or [])][:25]})
                d = result.get("decision") or {}
                print(app, repr(goal), result.get("status"), "landed=", result.get("landed"),
                      "target=", (result.get("target") or {}).get("label"), "conf=", d.get("confidence"),
                      "ms=", result.get("ms"), flush=True)
            row = {"app": app, "ts": time.time(),
                   "tree_snapshot_ms": [t.get("snapshot_ms") for t in trees],
                   "tree_probe_ms": [t.get("probe_ms") for t in trees],
                   "tree_adb_wall_ms": [t.get("adb_wall_ms") for t in trees],
                   "tree_nodes": [len(t.get("nodes") or []) for t in trees],
                   "tree_package": trees[-1].get("package"),
                   "u2_dump": u2, "steps": steps}
            # v1 row shape (first goal) for the held-out tier scorer.
            row.update({"goal": steps[0]["goal"], "expect_hint": steps[0]["expect_hint"],
                        "screen": steps[0]["screen"], "do": steps[0]["do"]})
            out.write(json.dumps(row) + "\n")
            out.flush()
            print(app, "snap", row["tree_snapshot_ms"], "u2", [r["ms"] for r in u2], flush=True)
            for _ in range(3):
                shell("input keyevent KEYCODE_BACK")
                time.sleep(0.3)
            shell("input keyevent KEYCODE_HOME")
            time.sleep(0.8)


if __name__ == "__main__":
    main()
