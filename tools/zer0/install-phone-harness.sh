#!/usr/bin/env bash
# Run only when you want to install a locally downloaded APK on your authorized phone.
# Does not uninstall packages, pair ADB, grant privileges, or change the default HOME.
set -euo pipefail

if [[ $# -ne 1 || ! -f "${1:-}" || "${1:-}" != *.apk ]]; then
  echo 'Usage: bash tools/zer0/install-phone-harness.sh /path/to/downloaded.apk' >&2
  exit 2
fi
apk="$1"
if ! command -v adb >/dev/null 2>&1; then
  echo 'adb missing; use your existing authorized Tern Wi-Fi ADB connection' >&2
  exit 3
fi
if [[ -n "${ANDROID_SERIAL:-}" ]]; then
  serial="$ANDROID_SERIAL"
else
  mapfile -t devices < <(adb devices | awk 'NR>1 && $2 == "device" { print $1 }')
  if [[ ${#devices[@]} -ne 1 ]]; then
    echo 'Expected exactly one authorized device; set ANDROID_SERIAL if needed' >&2
    adb devices >&2
    exit 4
  fi
  serial="${devices[0]}"
fi
if [[ "$(adb -s "$serial" get-state 2>/dev/null)" != device ]]; then
  echo 'Device not authorized; pairing and existing settings were not changed' >&2
  exit 4
fi

echo "Authorized ADB target: $serial"
sha256sum "$apk"
aapt_bin="$(command -v aapt || true)"
apksigner_bin="$(command -v apksigner || true)"
sdk="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}"
if [[ -n "$sdk" && -d "$sdk/build-tools" ]]; then
  build_tools="$(find "$sdk/build-tools" -mindepth 1 -maxdepth 1 -type d | sort -V | tail -1)"
  [[ -z "$aapt_bin" && -x "$build_tools/aapt" ]] && aapt_bin="$build_tools/aapt"
  [[ -z "$apksigner_bin" && -x "$build_tools/apksigner" ]] && apksigner_bin="$build_tools/apksigner"
fi
if [[ -n "$aapt_bin" ]]; then
  "$aapt_bin" dump badging "$apk" | grep -E '^(package:|sdkVersion:|targetSdkVersion:)' || true
fi
if [[ -n "$apksigner_bin" ]]; then
  "$apksigner_bin" verify --print-certs "$apk" | grep -E 'certificate SHA-256 digest' || true
fi

set +e
result="$(adb -s "$serial" install -r "$apk" 2>&1)"
status=$?
set -e
printf '%s\n' "$result"
if [[ $status -ne 0 || "$result" != *Success* ]]; then
  case "$result" in
    *INSTALL_FAILED_UPDATE_INCOMPATIBLE*)
      echo 'SIGNATURE CONFLICT: different certificate for the same package ID' >&2
      echo 'Preserve your working launcher; use a consistently signed build or another flavor' >&2 ;;
    *INSTALL_FAILED_VERSION_DOWNGRADE*)
      echo 'VERSION DOWNGRADE: obtain a newer build with the same signing identity' >&2 ;;
    *INSTALL_FAILED_OLDER_SDK*)
      echo 'SDK INCOMPATIBLE: target device is below the APK minimum' >&2 ;;
    *)
      echo 'Keep the INSTALL_FAILED_* output for diagnosis; do not uninstall blindly' >&2 ;;
  esac
  echo 'No package was uninstalled and your existing HOME choice was unchanged' >&2
  exit 5
fi
echo 'Android accepted installation. Verify Launcher UX, mic permission and One UI Home rollback manually.'
