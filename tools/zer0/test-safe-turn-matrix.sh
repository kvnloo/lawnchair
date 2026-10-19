#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
BUILD_DIR="$(mktemp -d "${TMPDIR:-/tmp}/z0live-safe-turn.XXXXXX")"
trap 'rm -rf "$BUILD_DIR"' EXIT

javac -encoding UTF-8 -d "$BUILD_DIR" \
  "$ROOT/lawnchair/src/app/lawnchair/zer0/voice/z0live/Z0LiveTurnState.java" \
  "$ROOT/lawnchair/src/app/lawnchair/zer0/voice/z0live/Z0LiveAudioState.java" \
  "$ROOT/tools/zer0/Z0LiveSafeTurnMatrixTest.java"

java -cp "$BUILD_DIR" Z0LiveSafeTurnMatrixTest
