# zer0 voice navigation — isolated fork experiment

Status: deterministic command-parser experiment only. No UI trigger, microphone session, Jev model, CUA executor, or Android-device qualification is part of this commit.

## Why this branch exists

Use Lawnchair 16 (`16-dev`) as the actual launcher. Preserve its widgets, search providers, gestures, hotseat, theme, and Android integration. Keep zer0-specific experiments opt-in and outside AOSP `src/` where possible; ship separately from ordinary upstream contributions.

## Current code

- `lawnchair/src/app/lawnchair/zer0/voice/VoiceCommand.java` parses a finalized user utterance into one of a small known set of commands; UNKNOWN is normal and never a fallback-to-execution.
- `bash tools/zer0/test-voice-commands.sh` tests the parser on a plain JVM; no Android SDK or cloud required.
- **Nothing executes these results yet.** The prior independent prototype's `SpeechRecognizer`-based push-to-talk UI is a reference, not a working feature in Lawnchair.

Supported intents: home/back/recents; open/search installed apps; named Now/Build/Learn/Life spaces; start/stop focus. Spaces, focus, and cross-app actions are not yet implemented in the fork. No action leaves the phone at this stage.

## Next minimal integration

1. Baseline: build Lawnchair 16 dev, install alongside known-safe launcher on Samsung/One UI, test home gestures, widgets, folders, app drawer, backups, and recents. `./gradlew assembleLawnWithQuickstepGithubDebug` plus `spotlessCheck`; follow the current upstream developer setup docs and submodule steps.
2. Add **opt-in push-to-talk entry point** in existing Lawnchair QSB or gesture settings. Reuse existing `QsbIconId.MIC`, search-provider and gesture APIs; do not add another global search implementation. No always-on microphone.
3. Route **final** speech recognition through parser. Resolve app names with Android's installed-app/shortcut model and reject ambiguous matches. Map only proven native actions; show UNKNOWN to the user rather than silently committing a guess.
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
