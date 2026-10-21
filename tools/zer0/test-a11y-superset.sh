#!/usr/bin/env bash
# test-a11y-superset.sh — O6 mirror tests. Fixed cases from the 2026-10-21 incident always run;
# when `z0-guard` (kvnloo/z0intelligence) is on PATH, 200 seeded random cases also compare the
# mirror with the decision function (parity).
set -euo pipefail
here=$(cd "$(dirname "$0")" && pwd)
m="$here/a11y-superset.sh"
L1=app.lawnchair.debug/app.lawnchair.LawnchairAccessibilityService
L2=app.lawnchair.play.debug/app.lawnchair.LawnchairAccessibilityService
Z=app.lawnchair.play.debug/app.lawnchair.zer0.voice.z0live.Z0LiveAccessibilityService
fail=0
check() { # name expect_exit pre post removed
  local got=0; "$m" "$3" "$4" "${5:-}" >/dev/null || got=$?
  if [ "$got" = "$2" ]; then echo "ok   $1"; else echo "FAIL $1 (exit $got, want $2)"; fail=1; fi
}
check "reinstall dropped the package's services (incident)" 5 "$L1:$L2:$Z" "$L1"
check "owner removed z0live in Settings, then install"     0 "$L1:$L2:$Z" "$L1:$L2" "$Z"
check "wrapper restored every service"                       0 "$L1:$L2:$Z" "$Z:$L2:$L1"
check "nothing enabled before"                               0 "null" "null"
check "quoted settings value"                                0 "'$Z'" "'$Z:$L1'"
check "new service added is fine"                            0 "$Z" "$Z:$L1"
if command -v z0-guard >/dev/null 2>&1; then
  n=0; bad=0
  while IFS='|' read -r pre post rm; do
    a=0; "$m" "$pre" "$post" "$rm" >/dev/null || a=$?
    b=0; Z0_GUARD_STATE=${TMPDIR:-/tmp}/a11y-parity z0-guard a11y --pre "$pre" --post "$post" --removed "$rm" >/dev/null || b=$?
    n=$((n+1)); [ "$a" = "$b" ] || { bad=$((bad+1)); echo "MISMATCH pre=$pre post=$post rm=$rm mirror=$a gate=$b"; }
  done < <(python3 - <<'PY'
import random
r = random.Random(20261021)
S = [f"p{i}/S{i}" for i in range(6)]
for _ in range(200):
    pick = lambda k: ":".join(r.sample(S, r.randint(0, k)))
    print(f"{pick(5)}|{pick(6)}|{pick(2)}")
PY
)
  echo "parity with z0-guard a11y: $((n-bad))/$n equal"; [ "$bad" = 0 ] || fail=1
else
  echo "skip parity: z0-guard not on PATH"
fi
exit $fail
