# zer0 voice navigation — isolated fork experiment

Status: **one-shot, user-triggered speech-to-app wiring implemented in this fork**. Plain-JVM command/matcher checks passed locally; the full Android build and Samsung device behavior remain unverified. Jev, z0live, GPT-Live and CUA are not wired.

## Why this branch exists

Use Lawnchair 16 (`16-dev`) as the actual launcher. Preserve its widgets, search providers, gestures, hotseat, theme, and Android integration. Keep zer0-specific experiments opt-in and outside AOSP `src/` where possible; ship separately from ordinary upstream contributions.

## Current code

- `lawnchair/src/app/lawnchair/zer0/voice/VoiceCommand.java` parses a finalized user utterance into one of a small known set of commands; UNKNOWN is normal and never a fallback-to-execution.
- `bash tools/zer0/test-voice-commands.sh` tests the parser on a plain JVM; no Android SDK or cloud required.
- `AppNameMatcher.java` resolves a unique **exact app label**; labels that are missing or ambiguous are rejected.
- `VoiceCommandActivity.java` starts Android's `ACTION_RECOGNIZE_SPEECH` from a user action, consumes **only final** recognized text, and calls `LauncherApps.startMainActivity` for an unambiguous current-profile `OPEN_APP` request.
- `LawnQsbLayout.kt` adds a separate `Voice command (beta)` entry to the **search bar's long-press menu**; existing QSB mic/search provider behavior is unchanged. The command activity is non-exported.
- `bash tools/zer0/test-voice-commands.sh` runs **45 pure JVM checks** (30 grammar, 15 matcher/integration).
- **No Android build or device integration test has run.** The framework dispatch, menu and speech UX remain unverified.

**Executable now (after a successful Android build):** exact unique-label `open <app>` in the current personal profile. The parser still recognizes home/back/recents, search, focus and named spaces, but the Activity intentionally rejects them as unsupported. No work/private-profile launching, messaging, privileged action, background microphone or accessibility control. Recognition is delegated to an installed Android recognizer app and **may send audio to its servers**, depending on its implementation.

## Next minimal integration

1. Baseline: build Lawnchair 16 dev, install alongside known-safe launcher on Samsung/One UI, test home gestures, widgets, folders, app drawer, backups, and recents. `./gradlew assembleLawnWithQuickstepGithubDebug` plus `spotlessCheck`; follow the current upstream developer setup docs and submodule steps.
2. **Implemented, requires Android qualification:** long-press existing QSB -> `Voice command (beta)` -> system speech recognition prompt. Keep the normal QSB mic behavior intact. No always-on microphone or new permission.
3. **Implemented for one action, requires device proof:** route final speech recognition through parser, exact-match launchable activities in the current profile, refuse ambiguous/absent matches. All other parsed intents are non-executable. Add test coverage for lifecycle recreation and recognizer cancellations after device smoke.
4. Introduce `PhoneAction` / `PhoneActionReceipt` as a narrow adapter contract with session ID, utterance ID, action ID, expected foreground revision, outcome state, and permission. Explicit user approval for risky operations, no replay after uncertain outcome. Only then connect z0intelligence's authority/router and Jev fallback for bounded ambiguity.
5. Add z0live/ChatGPT developer API realtime voice as **an optional transport** with single audio owner, interruption/teardown, backend-owned credentials and verified results. The ChatGPT application's Voice UI is not an automation API.
6. CUA-style observe → act → verify: native Android accessibility state first if user grants access; screenshots/remote desktop fallback only on explicit permission. Android permissions and Play accessibility policy apply.
7. Measure recognition-stop → verified screen change p50/p95, misroutes, false commits, battery, and memory against basic Android voice + conventional search. Speed targets require device measurements; do not claim performance from grammar tests.

## Upstream split

- **Good upstream:** generic provider/shortcut intents; user-controlled mic target; keyboard-first search and accessibility labeling with small device repro and tests.
- **Keep downstream:** zer0 spaces, Jev/z0int dispatch authority, z0live runtime, CUA phone-control orchestration, long-lived microphone, predictive attention control.
- Existing upstream discussion: [AI chatbot search #6635](https://github.com/LawnchairLauncher/lawnchair/issues/6635), [global search provider PR #7221](https://github.com/LawnchairLauncher/lawnchair/pull/7221), [external keyboard search #6031](https://github.com/LawnchairLauncher/lawnchair/issues/6031), [TalkBack report #6711](https://github.com/LawnchairLauncher/lawnchair/issues/6711). Do not open duplicates or overwrite other contributors' work.

## Ownership and credit

Lawnchair and Launcher3 code remain credited to their original contributors, AOSP authors, and maintainers. The parser adapts the zer0 standalone voice prototype built October 11, 2026; the project design draws on `kvnloo/z0intelligence`, `kvnloo/z0live`, `kvnloo/oh-my-pi#104` and CUA's upstream Android accessibility work (`trycua/cua#878`). Preserve individual authorship and trace code/test contributions to specific commits when porting. Do not characterize upstream community requests or the early parser test as verified full phone-control integration.

## Coordination

- [Wave 0 test/quality tracker #2](https://github.com/kvnloo/lawnchair/issues/2)
- [Wave 1 voice/device qualification #5](https://github.com/kvnloo/lawnchair/issues/5)
- [Wave 2 bounded Jev/z0int/z0live adapter #6](https://github.com/kvnloo/lawnchair/issues/6)
- [Wave 3 Flow Spaces #7](https://github.com/kvnloo/lawnchair/issues/7)

## Device smoke (not yet performed)

1. Build `./gradlew assembleLawnWithQuickstepGithubDebug` with the documented Android 37.2 toolchain and `./gradlew spotlessCheck` (ensure repo submodules present).
2. Install the debug variant, set it as HOME temporarily; preserve a safe path back to One UI Home.
3. Long-press the dock QSB, confirm **Customize** still opens settings and **Voice command (beta)** opens the system prompt.
4. Speak `Open Maps` and `Open Camera`. If label is unique, verify that **the named app visibly launches**, and that cancellation does nothing.
5. Try `Send a message`, `Switch to Build`, `Open unknown app`, and two apps with identical display names. Verify zero unintended actions and appropriate feedback.
6. Verify non-exported activity, no new RECORD_AUDIO or AccessibilityService privilege, TalkBack menu labeling, widgets and recents.
7. Capture version, SHA, Samsung One UI/Android build, actual STT provider, measured command-to-launch latency, and failures. Do not infer results from source-level or JVM tests.
