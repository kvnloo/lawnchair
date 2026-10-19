#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
BUILD_DIR="$(mktemp -d "${TMPDIR:-/tmp}/spaces-restore.XXXXXX")"
trap 'rm -rf "$BUILD_DIR"' EXIT

javac -encoding UTF-8 -d "$BUILD_DIR" \
  "$ROOT/lawnchair/src/app/lawnchair/zer0/spaces/SpaceModel.java" \
  "$ROOT/lawnchair/src/app/lawnchair/zer0/spaces/SpacePins.java" \
  "$ROOT/lawnchair/src/app/lawnchair/zer0/spaces/SpacePageBinding.java" \
  "$ROOT/tools/zer0/SpacesRestoreMatrixTest.java"

java -cp "$BUILD_DIR" SpacesRestoreMatrixTest
