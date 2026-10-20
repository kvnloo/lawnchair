#!/usr/bin/env bash
# Runs the compiled production catalog and disabled dispatch. No Android service is instantiated.
set -euo pipefail
root="$(cd "$(dirname "$0")/../.." && pwd)"
work="$(mktemp -d)"
trap 'rm -rf "$work"' EXIT
python3 - "$root" "$work" <<'PY'
import os
from pathlib import Path
import subprocess
import sys

root, work = map(Path, sys.argv[1:])
sdk = Path(os.environ.get('ANDROID_HOME', '/home/kvn/android/sdk'))
gradle = Path(os.environ.get('GRADLE_USER_HOME', str(Path.home() / '.gradle')))
android = sorted((sdk / 'platforms').glob('*/android.jar'))[-1]
stdlib = sorted((gradle / 'caches/modules-2/files-2.1/org.jetbrains.kotlin/kotlin-stdlib').glob('*/*/*.jar'))[-1]
json = Path(os.environ.get('XDG_CACHE_HOME', str(Path.home() / '.cache'))) / 'lawnchair-z0live-protocol/json-20240303.jar'
classes = root / 'build/intermediates/compile_app_classes_jar/lawnWithQuickstepPlayDebug/bundleLawnWithQuickstepPlayDebugClassesToCompileJar/classes.jar'
if not classes.is_file() or not json.is_file():
    raise SystemExit('Run the variant unit or assembly task and test-z0live-protocol.sh first.')
classpath = ':'.join(map(str, [json, android, classes, stdlib, work]))
subprocess.run(['javac', '-cp', classpath, '-d', str(work), str(root / 'tools/zer0/Z0LiveAccessibilityCatalogTest.java')], check=True)
subprocess.run(['java', '-cp', classpath, 'Z0LiveAccessibilityCatalogTest'], check=True)
PY
