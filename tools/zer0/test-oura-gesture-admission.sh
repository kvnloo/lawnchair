#!/usr/bin/env bash
set -euo pipefail

root="$(cd "$(dirname "$0")/../.." && pwd)"
tmp="$(mktemp -d)"
trap 'rm -rf "$tmp"' EXIT

javac -encoding UTF-8 -d "$tmp" \
  "$root/lawnchair/src/app/lawnchair/zer0/voice/z0live/OuraGestureAdmission.java" \
  "$root/tools/zer0/OuraGestureAdmissionTest.java"
java -cp "$tmp" OuraGestureAdmissionTest
