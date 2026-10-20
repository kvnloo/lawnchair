#!/usr/bin/env bash
set -euo pipefail
root=$(cd "$(dirname "$0")/../.." && pwd)
out=$(mktemp -d "${TMPDIR:-$HOME/.cache}/z0live-gesture.XXXXXX")
trap 'rm -rf "$out"' EXIT
javac -d "$out" "$root/lawnchair/src/app/lawnchair/zer0/voice/z0live/Z0LiveGesturePolicy.java" "$root/tools/zer0/Z0LiveGesturePolicyTest.java"
java -cp "$out" Z0LiveGesturePolicyTest
