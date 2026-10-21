#!/usr/bin/env bash
# Tier-0 in-app control: real matcher/snapshot/policy classes on the shared labelled set.
set -euo pipefail
root="$(cd "$(dirname "$0")/../.." && pwd)"
work="$(mktemp -d)"
trap 'rm -rf "$work"' EXIT
cache="${XDG_CACHE_HOME:-$HOME/.cache}/lawnchair-z0live-protocol"
mkdir -p "$cache"
json="$cache/json-20240303.jar"
if [[ ! -s "$json" ]]; then
  curl --fail --silent --show-error --location --retry 2 --connect-timeout 10 --max-time 90 \
    "https://repo.maven.apache.org/maven2/org/json/json/20240303/json-20240303.jar" -o "$work/json.jar"
  mv "$work/json.jar" "$json"
fi
src="$root/lawnchair/src/app/lawnchair/zer0/voice/z0live"
javac --release 17 -encoding UTF-8 -cp "$json" -d "$work" \
  "$src/Z0LiveScreen.java" "$src/Z0LiveTargetMatcher.java" "$src/Z0LiveAccessibilityPolicy.java" \
  "$root/tools/zer0/Z0LiveControlTest.java"
java -cp "$work:$json" app.lawnchair.zer0.voice.z0live.Z0LiveControlTest "$root/tools/zer0/fixtures/z0live-control-cases.json"
