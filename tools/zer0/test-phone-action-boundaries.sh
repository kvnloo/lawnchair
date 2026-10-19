#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
BUILD_DIR="$(mktemp -d "${TMPDIR:-/tmp}/phone-action-boundaries.XXXXXX")"
trap 'rm -rf "$BUILD_DIR"' EXIT

javac -encoding UTF-8 -d "$BUILD_DIR" \
  "$ROOT/lawnchair/src/app/lawnchair/zer0/voice/PhoneActionGate.java" \
  "$ROOT/lawnchair/src/app/lawnchair/zer0/voice/VoiceCommand.java" \
  "$ROOT/lawnchair/src/app/lawnchair/zer0/voice/JevFastDecisionPolicy.java" \
  "$ROOT/tools/zer0/PhoneActionBoundaryMatrixTest.java"

java -cp "$BUILD_DIR" PhoneActionBoundaryMatrixTest
