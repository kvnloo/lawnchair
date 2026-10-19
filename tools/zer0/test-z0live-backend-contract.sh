#!/usr/bin/env bash
set -euo pipefail
root="$(cd "$(dirname "$0")/../.." && pwd)"
# Populate the existing pinned dependency cache and verify the broader client tests first.
bash "$root/tools/zer0/test-z0live-protocol.sh"
work="$(mktemp -d)"
trap 'rm -rf "$work"' EXIT
cache="${XDG_CACHE_HOME:-$HOME/.cache}/lawnchair-z0live-protocol"
classpath="$work"
for jar in okhttp-4.12.0.jar okio-jvm-3.6.0.jar kotlin-stdlib-1.9.10.jar json-20240303.jar; do
  classpath="$classpath:$cache/$jar"
done
javac --release 17 --add-modules jdk.httpserver -encoding UTF-8 -cp "$classpath" -d "$work" \
  "$root/lawnchair/src/app/lawnchair/zer0/voice/z0live/PairingValidation.java" \
  "$root/lawnchair/src/app/lawnchair/zer0/voice/z0live/Z0LiveProtocol.java" \
  "$root/lawnchair/src/app/lawnchair/zer0/voice/z0live/Z0LiveEvents.java" \
  "$root/tools/zer0/Z0LiveBackendContractTest.java"
java --add-modules jdk.httpserver -cp "$classpath" app.lawnchair.zer0.voice.z0live.Z0LiveBackendContractTest
