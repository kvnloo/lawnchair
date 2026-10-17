#!/usr/bin/env bash
# Explicit on-device HyprSpaces proof collection. No network uploads, rooting, installs,
# accessibility grants, screenshots, microphone recording or app-state mutation.
set -euo pipefail

usage() {
  cat <<'HELP'
Usage:
  bash tools/zer0/samsung-spaces-check.sh preflight
  bash tools/zer0/samsung-spaces-check.sh capture [local-output-directory]

Preflight prints device and launcher-role facts only. It does not dump screen contents.
Capture is EXPLICIT OPT-IN: dumpsys activity/window may contain installed-app package
names, task/window titles and other private on-device state. It saves two text files
locally and never uploads them. Review/redact manually before attaching to any issue.

Select your Android device with ANDROID_SERIAL if multiple devices are connected.
Preserve One UI Home as an alternate launcher before installing an experiment.
HELP
}

if [[ $# -lt 1 || $1 == "--help" || $1 == "help" ]]; then
  usage
  exit 0
fi

if ! command -v adb >/dev/null 2>&1; then
  echo "adb not found. Install Android platform-tools and enable USB debugging." >&2
  exit 2
fi

adb_flags=()
if [[ -n "${ANDROID_SERIAL:-}" ]]; then
  adb_flags=(-s "$ANDROID_SERIAL")
fi

run_adb() {
  adb "${adb_flags[@]}" "$@"
}

connected="$(run_adb devices | awk 'NR > 1 && $2 == "device" { n++ } END { print n + 0 }')"
if [[ "$connected" -ne 1 && -z "${ANDROID_SERIAL:-}" ]]; then
  echo "Expected exactly one authorized Android device (found $connected)." >&2
  echo "Choose one using ANDROID_SERIAL=<adb-device-serial>." >&2
  exit 3
fi

model="$(run_adb shell getprop ro.product.model | tr -d '\r')"
sdk="$(run_adb shell getprop ro.build.version.sdk | tr -d '\r')"
release="$(run_adb shell getprop ro.build.version.release | tr -d '\r')"
oneui="$(run_adb shell getprop ro.build.version.oneui | tr -d '\r')"

case "$1" in
  preflight)
    printf '%s\n' "HyprSpaces Android device preflight (not a window verification)"
    printf 'model=%s\nandroid_sdk=%s\nandroid_release=%s\noneui=%s\n' \
      "$model" "$sdk" "$release" "${oneui:-unknown}"
    printf '%s\n' "Home role holder(s):"
    run_adb shell cmd role holders android.app.role.HOME || true
    printf '%s\n' "No screen, window dumps, app lists or private data were saved."
    ;;
  capture)
    directory="${2:-./hyprspaces-local-evidence}"
    if [[ -e "$directory" && ! -d "$directory" ]]; then
      echo "Destination exists but is not a directory: $directory" >&2
      exit 4
    fi
    mkdir -p "$directory"
    printf '%s\n' "Capturing locally. Activity/window dumps may contain PRIVATE app and window data."
    printf '%s\n' "These files must be reviewed/redacted before external sharing."
    printf '%s\n' "model=$model" "android_sdk=$sdk" \
      "android_release=$release" "oneui=${oneui:-unknown}" \
      > "$directory/device.txt"
    run_adb shell dumpsys activity activities > "$directory/activities.txt"
    run_adb shell dumpsys window > "$directory/windows.txt"
    printf 'Local evidence written to %s (no upload performed).\n' "$directory"
    ;;
  *)
    usage >&2
    exit 1
    ;;
esac
