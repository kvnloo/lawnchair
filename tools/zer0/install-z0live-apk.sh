#!/usr/bin/env bash
# install-z0live-apk.sh — the one way to update-install Lawnchair z0live on a phone.
#
#   tools/zer0/install-z0live-apk.sh APK [SERIAL]
#
# Why: Android (and One UI in particular) drops an app's accessibility services
# from enabled_accessibility_services when the package is replaced, so every
# reinstall silently turned off z0live in-app control. This wrapper:
#   1. refuses unless the APK signer matches the installed package (never uninstalls);
#   2. snapshots the secure accessibility settings;
#   3. runs `adb install -r` under the phone's install lock;
#   4. restores every previously enabled service (it only re-adds what was there, so
#      owner-disabled services stay off) and always keeps the z0live control service on
#      if it was on before;
#   5. re-applies the battery exemptions and verifies the result.
set -euo pipefail
unset ADB_SERVER_SOCKET
APK=${1:?usage: install-z0live-apk.sh APK [SERIAL]}
SERIAL=${2:-${ANDROID_SERIAL:-}}
PKG=app.lawnchair.play.debug
LOCK=${Z0LIVE_INSTALL_LOCK:-/home/kvn/.hermes/profiles/clean/cache/scratch/s25-noop-install.lock}
SDK=${ANDROID_SDK_ROOT:-/home/kvn/android/sdk}
APKSIGNER=$(ls -d "$SDK"/build-tools/*/apksigner 2>/dev/null | sort -V | tail -1)
adb_() { if [ -n "$SERIAL" ]; then adb -s "$SERIAL" "$@"; else adb "$@"; fi; }
digest() { "$APKSIGNER" verify --print-certs "$1" | sed -n 's/.*certificate SHA-256 digest: //p' | head -1; }

[ -f "$APK" ] || { echo "no such APK: $APK" >&2; exit 2; }
[ -x "$APKSIGNER" ] || { echo "apksigner not found under $SDK" >&2; exit 2; }

exec 9>"$LOCK"
flock -w 600 9 || { echo "install lock busy: $LOCK" >&2; exit 3; }

tmp=$(mktemp -d); trap 'rm -rf "$tmp"' EXIT
if remote=$(adb_ shell pm path "$PKG" 2>/dev/null | sed -n 's/^package://p' | grep base.apk | tr -d '\r'); [ -n "$remote" ]; then
  adb_ pull "$remote" "$tmp/installed.apk" >/dev/null
  want=$(digest "$tmp/installed.apk"); got=$(digest "$APK")
  if [ "$want" != "$got" ]; then
    echo "signer mismatch (installed $want, apk $got); refusing. Never uninstall to bypass." >&2
    exit 4
  fi
fi

before=$(adb_ shell settings get secure enabled_accessibility_services | tr -d '\r')
[ "$before" = "null" ] && before=""
echo "accessibility before: $(echo "$before" | tr ':' '\n' | grep -c . || true) services"

adb_ install -r "$APK"

after=$(adb_ shell settings get secure enabled_accessibility_services | tr -d '\r')
[ "$after" = "null" ] && after=""
merged=$(printf '%s\n%s\n' "$after" "$before" | tr ':' '\n' | awk 'NF && !seen[$0]++' | paste -sd: -)
if [ "$merged" != "$after" ]; then
  adb_ shell settings put secure enabled_accessibility_services "'$merged'"
  adb_ shell settings put secure accessibility_enabled 1
  echo "restored accessibility services dropped by the update"
fi

adb_ shell dumpsys deviceidle whitelist +"$PKG" >/dev/null || true
adb_ shell cmd appops set "$PKG" RUN_ANY_IN_BACKGROUND allow || true

final=$(adb_ shell settings get secure enabled_accessibility_services | tr -d '\r')
missing=$(printf '%s\n' "$before" | tr ':' '\n' | awk 'NF' | while read -r s; do case ":$final:" in *":$s:"*) ;; *) echo "$s";; esac; done)
adb_ shell dumpsys package "$PKG" | grep -m2 -E 'versionName|lastUpdateTime' | sed 's/^ *//' || true
if [ -n "$missing" ]; then echo "STILL MISSING: $missing" >&2; exit 5; fi
echo "accessibility after: $(echo "$final" | tr ':' '\n' | grep -c .) services (all previous kept)"
