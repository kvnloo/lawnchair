# Phone-action admission negative controls

Part of [Lawnchair task #18](https://github.com/kvnloo/lawnchair/issues/18). These are **pure Java** unit assertions about `PhoneActionGate.admit` and `JevFastDecisionPolicy.admitModelChoice`. They do not exercise Android, a Jev model, permissions, or the actual launcher executor.

## Proposed gates

```sh
bash tools/zer0/test-phone-action-boundaries.sh
# Hermes also runs the existing PhoneActionGate/JevFastDecisionPolicy JVM suites and repo build gates.
```

**Tests NOT RUN — ChatGPT cannot execute code.** The temporary compilation directory is removed on any exit. Exact SHA, JVM version, genuine suite result and optional inverted-assertion negative control belong in the issue body after Hermes execution.

## Public-method expectations

| Input / method | Expected gate result | Caller-side fake dispatches |
| --- | --- | --- |
| `PhoneActionGate.admit`: exact action ID or utterance replay | `DUPLICATE` | 0 |
| `admit`: time equals expiry or beyond 30-second horizon | `EXPIRED` | 0 |
| `admit`: changed UI revision | `STALE_UI` | 0 |
| `admit`: high-confidence remote-model proposal | `REQUIRES_CONFIRMATION` | 0 |
| `admit`: voice-only split | `NOT_ALLOWED` | 0 |
| `admit`: null authority/kind, control/bidi characters, bad identity | `INVALID` | 0 |
| `admit`: previously rejected valid identity reused with new authority | `DUPLICATE` | 0 |
| `admit`: corrected input after **invalid** request | `ADMITTED` | Not an Android success |
| `JevFastDecisionPolicy.admitModelChoice`: confidence just under 0.90, stale/zero revision, absent index, destructive kind or malformed target | `null` | None |
| `admitModelChoice`: exactly 0.90 confidence, fresh allowed index | Typed **proposal** only | None |
| `admit`: explicit user-tap opaque `work:camera` target | `ADMITTED` (design gap) | None in the test |

The only invocation counter is a **synthetic caller-side probe**. These classes expose no Android executor callback or injection seam, so the counter cannot prove production executor nonexecution. Rejected results must be filtered by the calling service before any side effect.

## Security gaps requiring service/device evidence

- **Cross-profile:** target is an opaque string; gate cannot resolve work/personal profile, hidden apps, installed package, visibility or consent. A syntactically valid work-profile-looking target may be admitted. The Android executor must resolve it under the owner's active profile and independently block unauthorized targets.
- **Verified receipt:** `ADMITTED` is permission to attempt a bounded local operation, **not** `completed`/`verified`. `admitModelChoice` yields a proposal, not permission; remote authority is still `REQUIRES_CONFIRMATION`. Actual UI result, user confirmation, and rollback require separate device tests.
- **Replay window:** IDs are cached only for the most recent 256 items in one process. Neither cross-restart deduplication nor durable action receipts are provided here.
- **Expiry:** monotonic `nowElapsedMs` must be from the same clock as `expiresAtElapsedMs`. No remote clock or post-admission action-duration guard is provided by the gate.
- **No destructive auto-actions:** only an explicit `USER_TAP` permits `REQUEST_SPLIT`; this does not generalize to deletion, call, SMS or other nonexistent action kinds.

Device foreground state, Samsung One UI behavior, accessibility, authorization, hidden/work apps and model reliability: **NOT RUN**. Do not add permissions or infer a phone tool from model prose.
