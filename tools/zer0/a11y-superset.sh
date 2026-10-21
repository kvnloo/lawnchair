#!/usr/bin/env bash
# a11y-superset.sh PRE POST [REMOVED] — O6 accessibility preservation (owner law, kvnloo/z0intelligence
# bend/ops_fsm o6_a11y_superset): every service enabled before an install is enabled after it, unless
# the owner removed it. Arguments are enabled_accessibility_services values (colon-separated; "null" =
# none). Prints each missing service on its own line; exit 5 if any is missing, else 0.
#
# Mirror of z0int.fsm.ops.a11y_ok. install-z0live-apk.sh asks `z0-guard a11y` (the decision function)
# when it is installed and falls back to this; test-a11y-superset.sh keeps the two equal.
set -euo pipefail
norm() { local v=${1:-}; v=${v//\'/}; [ "$v" = null ] && v=""; printf '%s\n' "$v" | tr ':' '\n' | awk 'NF'; }
pre=$(norm "${1:-}"); post=$(norm "${2:-}"); removed=$(norm "${3:-}")
missing=0
while IFS= read -r s; do
  [ -n "$s" ] || continue
  if ! grep -qxF -- "$s" <<<"$post" && ! grep -qxF -- "$s" <<<"$removed"; then
    printf '%s\n' "$s"; missing=1
  fi
done <<<"$pre"
[ $missing = 0 ] || exit 5
