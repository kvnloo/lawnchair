#!/usr/bin/env bash
set -euo pipefail
root="$(cd "$(dirname "$0")/../.." && pwd)"
work="$(mktemp -d)"
trap 'rm -rf "$work"' EXIT
sources=("$root/tools/zer0/Z0LiveAccessibilityPolicyTest.java")
policy="$root/lawnchair/src/app/lawnchair/zer0/voice/z0live/Z0LiveAccessibilityPolicy.java"
if [[ -f "$policy" ]]; then sources+=("$policy"); fi
javac --release 17 -encoding UTF-8 -d "$work" "${sources[@]}"
java -cp "$work" Z0LiveAccessibilityPolicyTest
