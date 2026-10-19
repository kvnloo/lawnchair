#!/usr/bin/env bash
set -euo pipefail
root="$(cd "$(dirname "$0")/../.." && pwd)"
work="$(mktemp -d)"
trap 'rm -rf "$work"' EXIT
javac -encoding UTF-8 -d "$work" \
    "$root/lawnchair/src/app/lawnchair/zer0/voice/z0live/Z0LiveAudioState.java" \
    "$root/tools/zer0/Z0LiveAudioStateTest.java" \
    "$root/lawnchair/src/app/lawnchair/zer0/voice/z0live/Z0LiveTurnState.java" \
    "$root/tools/zer0/Z0LiveTurnStateTest.java"
java -cp "$work" Z0LiveAudioStateTest
java -cp "$work" Z0LiveTurnStateTest
