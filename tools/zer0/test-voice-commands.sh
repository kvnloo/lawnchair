#!/usr/bin/env bash
set -euo pipefail

root="$(cd "$(dirname "$0")/../.." && pwd)"
work="$(mktemp -d)"
trap 'rm -rf "$work"' EXIT

javac -encoding UTF-8 -d "$work" \
  "$root/lawnchair/src/app/lawnchair/zer0/voice/VoiceCommand.java" \
  "$root/lawnchair/src/app/lawnchair/zer0/voice/PhoneActionGate.java" \
  "$root/lawnchair/src/app/lawnchair/zer0/voice/AssistantLaunchPolicy.java" \
  "$root/lawnchair/src/app/lawnchair/zer0/voice/AppNameMatcher.java" \
  "$root/lawnchair/src/app/lawnchair/zer0/spaces/SpaceModel.java" \
  "$root/lawnchair/src/app/lawnchair/zer0/spaces/SpacePins.java" \
  "$root/lawnchair/src/app/lawnchair/zer0/spaces/SpaceAppSearch.java" \
  "$root/lawnchair/src/app/lawnchair/zer0/spaces/SplitProbe.java" \
  "$root/lawnchair/src/app/lawnchair/zer0/spaces/SpacePageBinding.java" \
  "$root/lawnchair/src/app/lawnchair/zer0/spaces/WindowBoundsRequest.java" \
  "$root/tools/zer0/VoiceCommandTest.java" \
  "$root/tools/zer0/PhoneActionGateTest.java" \
  "$root/tools/zer0/AssistantLaunchPolicyTest.java" \
  "$root/tools/zer0/AppNameMatcherTest.java" \
  "$root/tools/zer0/SpaceModelTest.java" \
  "$root/tools/zer0/SpacePinsTest.java" \
  "$root/tools/zer0/SpaceAppSearchTest.java" \
  "$root/tools/zer0/SplitProbeTest.java" \
  "$root/tools/zer0/SpacePageBindingTest.java" \
  "$root/tools/zer0/WindowBoundsRequestTest.java"

java -cp "$work" VoiceCommandTest
java -cp "$work" AppNameMatcherTest

java -cp "$work" SpaceModelTest
java -cp "$work" SpacePinsTest
java -cp "$work" PhoneActionGateTest
java -cp "$work" SpaceAppSearchTest
java -cp "$work" SplitProbeTest
java -cp "$work" SpacePageBindingTest

# Static-only smoke for opt-in device proof collection; does not require adb or a phone.
bash -n "$root/tools/zer0/samsung-spaces-check.sh"
bash "$root/tools/zer0/samsung-spaces-check.sh" --help | grep -q "EXPLICIT OPT-IN"

java -cp "$work" WindowBoundsRequestTest

java -cp "$work" AssistantLaunchPolicyTest
