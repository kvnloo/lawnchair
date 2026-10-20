#!/usr/bin/env bash
set -euo pipefail

root="$(cd "$(dirname "$0")/../.." && pwd)"
work="$(mktemp -d)"
cache="${XDG_CACHE_HOME:-$HOME/.cache}/lawnchair-z0live-protocol"
trap 'rm -rf "$work"' EXIT
mkdir -p "$cache"

# Keep dependency artifacts outside the checkout. Versions match a stable OkHttp Java API.
artifacts=(
  "com/squareup/okhttp3/okhttp/4.12.0/okhttp-4.12.0.jar"
  "com/squareup/okio/okio-jvm/3.6.0/okio-jvm-3.6.0.jar"
  "org/jetbrains/kotlin/kotlin-stdlib/1.9.10/kotlin-stdlib-1.9.10.jar"
  "org/json/json/20240303/json-20240303.jar"
)
classpath="$work"
for artifact in "${artifacts[@]}"; do
  jar="$cache/${artifact##*/}"
  if [[ ! -s "$jar" ]]; then
    curl --fail --silent --show-error --location --retry 2 \
      --connect-timeout 10 --max-time 90 \
      "https://repo.maven.apache.org/maven2/$artifact" -o "$work/dependency.jar"
    mv "$work/dependency.jar" "$jar"
  fi
  classpath="$classpath:$jar"
done

javac --release 17 --add-modules jdk.httpserver -encoding UTF-8 -cp "$classpath" -d "$work" \
  "$root/lawnchair/src/app/lawnchair/zer0/voice/z0live/PairingValidation.java" \
  "$root/lawnchair/src/app/lawnchair/zer0/voice/z0live/PendingPairing.java" \
  "$root/lawnchair/src/app/lawnchair/zer0/voice/z0live/Z0LiveProtocol.java" \
  "$root/lawnchair/src/app/lawnchair/zer0/voice/z0live/Z0LiveTelemetry.java" \
  "$root/lawnchair/src/app/lawnchair/zer0/voice/z0live/Z0LiveEvents.java" \
  "$root/tools/zer0/Z0LiveProtocolTest.java" \
  "$root/tools/zer0/Z0LiveTelemetryTest.java" \
  "$root/tools/zer0/Z0LiveBackendContractTest.java" \
  "$root/lawnchair/src/app/lawnchair/zer0/voice/z0live/Z0LiveAppMatcher.java" \
  "$root/tools/zer0/Z0LiveAppMatcherTest.java" \
  "$root/lawnchair/src/app/lawnchair/zer0/voice/z0live/Z0LiveInboxDelivery.java" \
  "$root/lawnchair/src/app/lawnchair/zer0/voice/z0live/Z0LiveInboxPlayback.java" \
  "$root/lawnchair/src/app/lawnchair/zer0/voice/z0live/Z0LiveBargeIn.java" \
  "$root/lawnchair/src/app/lawnchair/zer0/voice/z0live/Z0LiveSpeechGate.java" \
  "$root/lawnchair/src/app/lawnchair/zer0/voice/z0live/Z0LiveTurnState.java" \
  "$root/tools/zer0/Z0LiveSpeechGateTest.java" \
  "$root/lawnchair/src/app/lawnchair/zer0/voice/z0live/Z0LiveBrokerSession.java" \
  "$root/tools/zer0/Z0LiveMultiAgentContractTest.java" \
  "$root/tools/zer0/Z0LiveInboxDeliveryTest.java"
java --add-modules jdk.httpserver -cp "$classpath" app.lawnchair.zer0.voice.z0live.Z0LiveMultiAgentContractTest
java -cp "$classpath" app.lawnchair.zer0.voice.z0live.Z0LiveInboxDeliveryTest
java -cp "$classpath" app.lawnchair.zer0.voice.z0live.Z0LiveSpeechGateTest
java -cp "$classpath" app.lawnchair.zer0.voice.z0live.Z0LiveAppMatcherTest
java --add-modules jdk.httpserver -cp "$classpath" app.lawnchair.zer0.voice.z0live.Z0LiveProtocolTest
java -cp "$classpath" app.lawnchair.zer0.voice.z0live.Z0LiveTelemetryTest
java --add-modules jdk.httpserver -cp "$classpath" app.lawnchair.zer0.voice.z0live.Z0LiveBackendContractTest
