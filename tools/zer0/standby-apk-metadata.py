"""Inspect actual APK bytes, package and signer; CI and local use the same path."""
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
apks = list(Path("build/outputs/apk").rglob("*.apk"))
assert len(apks) == 1, apks
apk = apks[0]
sdk = Path(os.environ["ANDROID_HOME"])
tools = max((sdk / "build-tools").iterdir(), key=lambda p: p.name)
badging = subprocess.check_output([str(tools / "aapt"), "dump", "badging", str(apk)], text=True)
cert = subprocess.check_output([str(tools / "apksigner"), "verify", "--print-certs", str(apk)], text=True)
package = re.search(r"package: name='([^']+)' versionCode='([^']+)' versionName='([^']+)'", badging)
assert package and package[1] == "app.lawnchair.play.debug"
out = Path("standby-dist"); out.mkdir(exist_ok=True)
target = out / "Lawnchair-standby.apk"; shutil.copyfile(apk, target)
sha = hashlib.sha256(target.read_bytes()).hexdigest()
metadata = dict(commit=os.environ.get("GITHUB_SHA") or subprocess.check_output(["git","rev-parse","HEAD"],text=True).strip(), run_id=os.environ.get("GITHUB_RUN_ID"), package=package[1], versionName=package[3], size=target.stat().st_size, sha256=sha)
(out / "apk-metadata.json").write_text(json.dumps(metadata, indent=2) + "\n")
(out / "SHA256SUMS").write_text(sha + "  Lawnchair-standby.apk\n")
(out / "signing-certificate.txt").write_text(cert)
print(json.dumps(metadata, indent=2))
