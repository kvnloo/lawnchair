#!/usr/bin/env bash
set -euo pipefail

root="$(cd "$(dirname "$0")/../.." && pwd)"
work="$(mktemp -d)"
trap 'rm -rf "$work"' EXIT

javac -encoding UTF-8 -d "$work" \
  "$root/lawnchair/src/app/lawnchair/zer0/voice/VoiceCommand.java" \
  "$root/lawnchair/src/app/lawnchair/zer0/voice/AppNameMatcher.java" \
  "$root/lawnchair/src/app/lawnchair/zer0/spaces/SpaceModel.java" \
  "$root/tools/zer0/VoiceCommandTest.java" \
  "$root/tools/zer0/AppNameMatcherTest.java" \
  "$root/tools/zer0/SpaceModelTest.java"

java -cp "$work" VoiceCommandTest
java -cp "$work" AppNameMatcherTest

java -cp "$work" SpaceModelTest
