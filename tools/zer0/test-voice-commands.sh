#!/usr/bin/env bash
set -euo pipefail

root="$(cd "$(dirname "$0")/../.." && pwd)"
work="$(mktemp -d)"
trap 'rm -rf "$work"' EXIT

javac -encoding UTF-8 -d "$work" \
  "$root/lawnchair/src/app/lawnchair/zer0/voice/VoiceCommand.java" \
  "$root/lawnchair/src/app/lawnchair/zer0/voice/PhoneActionGate.java" \
  "$root/lawnchair/src/app/lawnchair/zer0/voice/AppNameMatcher.java" \
  "$root/lawnchair/src/app/lawnchair/zer0/spaces/SpaceModel.java" \
  "$root/lawnchair/src/app/lawnchair/zer0/spaces/SpacePins.java" \
  "$root/lawnchair/src/app/lawnchair/zer0/spaces/SpaceAppSearch.java" \
  "$root/tools/zer0/VoiceCommandTest.java" \
  "$root/tools/zer0/PhoneActionGateTest.java" \
  "$root/tools/zer0/AppNameMatcherTest.java" \
  "$root/tools/zer0/SpaceModelTest.java" \
  "$root/tools/zer0/SpacePinsTest.java" \
  "$root/tools/zer0/SpaceAppSearchTest.java"

java -cp "$work" VoiceCommandTest
java -cp "$work" AppNameMatcherTest

java -cp "$work" SpaceModelTest
java -cp "$work" SpacePinsTest
java -cp "$work" PhoneActionGateTest
java -cp "$work" SpaceAppSearchTest
