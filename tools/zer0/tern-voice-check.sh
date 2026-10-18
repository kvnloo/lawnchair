#!/usr/bin/env bash
# Run inside Tern on the SAME Android phone as HyprSpaces.
# Safe preflight: no ADB pairing, USB debugging changes, screen capture, login or installs.
set -euo pipefail

mode="${1:-check}"
if [[ "$mode" != "check" && "$mode" != "help" ]]; then
  echo "Usage: bash tools/zer0/tern-voice-check.sh [check]" >&2
  exit 2
fi
if [[ "$mode" == "help" ]]; then
  echo "Check the existing Wi-Fi ADB + local z0live listener on this phone."
  exit 0
fi

if ! command -v adb >/dev/null 2>&1; then
  echo "adb unavailable in Tern (existing pairing not changed)" >&2
  exit 3
fi

# ADB may target this very phone via its authorized Wi-Fi debugging connection.
if [[ -n "${ANDROID_SERIAL:-}" ]]; then
  serial="$ANDROID_SERIAL"
else
  mapfile -t devices < <(adb devices | awk 'NR > 1 && $2 == "device" {print $1}')
  if [[ "${#devices[@]}" -ne 1 ]]; then
    echo "Expected exactly one authorized device; set ANDROID_SERIAL if needed" >&2
    exit 4
  fi
  serial="${devices[0]}"
fi
if [[ "$(adb -s "$serial" get-state 2>/dev/null)" != "device" ]]; then
  echo "ADB authorization missing" >&2
  exit 5
fi
echo "Wi-Fi/USB ADB: authorized (connection method not inferred)"
echo "Android model: $(adb -s "$serial" shell getprop ro.product.model | tr -d '\r')"

if command -v curl >/dev/null 2>&1; then
  if curl --noproxy '*' -fSs --max-time 2 -o /dev/null "http://127.0.0.1:8780/voice-setup.html"; then
    echo "z0live UI: reachable at http://127.0.0.1:8780/voice-setup.html"
  else
    echo "z0live UI: not running (in OMP: /brainstorm on; or start admitted z0live VoicePlan)"
  fi
else
  echo "curl unavailable; UI reachability not tested"
fi

echo "No device content captured, credentials read, or control actions executed."
