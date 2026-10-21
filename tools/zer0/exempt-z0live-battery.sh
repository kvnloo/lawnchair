#!/usr/bin/env bash
# Explicit post-call rollout only. No installs, service toggles or UI input.
set -euo pipefail
if [[ $# != 2 || $2 != --apply ]]; then
    printf 'Usage: %s SERIAL --apply\n' "$0" >&2
    exit 2
fi
serial=$1
package=app.lawnchair.play.debug
adb=(env -u ADB_SERVER_SOCKET adb -s "$serial")
"${adb[@]}" get-state
"${adb[@]}" shell pm path "$package"
"${adb[@]}" shell dumpsys deviceidle whitelist +"$package"
"${adb[@]}" shell cmd appops set "$package" RUN_ANY_IN_BACKGROUND allow
"${adb[@]}" shell am set-inactive "$package" false
"${adb[@]}" shell am set-standby-bucket "$package" active
# Read back each affected state; the output is the receipt, not proof of Samsung list membership.
"${adb[@]}" shell dumpsys deviceidle whitelist
"${adb[@]}" shell cmd appops get "$package" RUN_ANY_IN_BACKGROUND
"${adb[@]}" shell am get-inactive "$package"
"${adb[@]}" shell am get-standby-bucket "$package"
printf '%s\n' 'Samsung sleeping/deep-sleeping lists need owner confirmation: see docs/zer0/Z0LIVE-ACCESSIBILITY-RECOVERY.md.'
