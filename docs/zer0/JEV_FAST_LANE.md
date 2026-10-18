# Jev-class fast decision boundary for Android voice

**October 18, 2026 — downstream only.** The fast lane is present and on-device deterministic; **the actual TypeSafe Jev model is not yet connected to the launcher**. Avoid describing this as deployed Jev inference.

## Fast path

Lawnchair QSB lightning icon -> Android foreground one-shot **on-device** STT (when supported) -> `VoiceCommand.parse` -> `JevFastDecisionPolicy.compileExact` -> `PhoneActionGate` -> exact personal-profile app / configured space / bounded search.

This grammar bypasses all model calls for explicit commands, avoiding inference cost. The app still checks installed and visible target, hidden-app policy, one-shot replay guard and phone OS permission. Unknown commands do not gain broad execution privileges; they hand off to Gemini, where the user must repeat them.

## Jev model extension (not connected)

1. Host (e.g. Tern + z0intelligence) observes *permitted*, fresh and consented UI targets.
2. Compile a compact table of ≤32 eligible Android `OPEN_APP`, `SWITCH_SPACE`, `SEARCH_APPS` actions, including target, profile scope and freshness revision. Exclude passwords, payments, protected windows, security settings, messages and disallowed targets.
3. Jev selects only an **index and probability**, plus the observation revision. Reject indices not in the offered table, confidence below 0.90, NaN, out-of-range values and mismatched/zero UI revision. Model output must never supply shell commands, arbitrary selectors, package names or unseen UI targets.
4. After selection, independent `PhoneActionGate` admission and Android target revalidation precede execution, with outcome receipts. Failed/uncertain outcomes must not be retried speculatively.
5. Implement a separately authenticated transport only after Tern local endpoint, pairing and secret storage are reviewed. Never hard-code API tokens in the APK, auto-pair ADB, expose an unauthenticated localhost action endpoint, or assume Gemini app voice events can be intercepted.
6. Measure recognizer-stop -> verified screen-change p50/p95 and failures on the Galaxy S25 Ultra against Gemini and Tern; host JVM grammar timing isn't a complete UX benchmark.

## Run contract and microbenchmark

`bash tools/zer0/test-voice-commands.sh` prints the legal-decision test count and a labeled host JVM parse+compile microbenchmark, not phone measurements. See [TypeSafe Jev](https://docs.typesafe.ai/introduction), [z0intelligence](https://github.com/kvnloo/z0intelligence), and [jev-ultrafast](https://github.com/kvnloo/jev-ultrafast) for typed operation/target heads.

Lawnchair, AOSP and other original contributors retain attribution.
