# zer0 Lawnchair fork — actionable issue queue

2026-10-11. **GitHub Issues is now enabled on the fork.** This remains the implementation/evidence map. Dedicated downstream issues: [#5 — Android voice qualification](https://github.com/kvnloo/lawnchair/issues/5), [#6 — Jev/z0int/z0live adapter](https://github.com/kvnloo/lawnchair/issues/6), [#7 — Flow Spaces](https://github.com/kvnloo/lawnchair/issues/7). Baseline/upstream testing ownership stays with [Wave 0 #2](https://github.com/kvnloo/lawnchair/issues/2). Do not cross-post duplicate issues upstream.

## P0 — Qualify Lawnchair 16 as a Samsung daily-driver baseline (coordinate #2)

**Problem:** Existing `16-dev` is not yet built or tested as zer0 on a Samsung One UI device; launcher, recents and third-party gesture limitations must be measured before feature changes.

**Smallest task:** Build the unchanged fork with documented submodules; record exact SHA/variant and perform smoke tests on a Samsung phone. Preserve original app ID and name in this task to avoid conflating baseline and rebrand.

**Acceptance:** CI build result + `spotlessCheck`; install/uninstall/restore path; screenshot + device/OS/build revision; verify launcher role, dock QSB, app drawer search, widgets, backup/restore, folder gesture, home/recents return, TalkBack. Separate observed pass/fail/unknown. No performance promises or root-only QuickSwitch claims. Note [Lawnchair #6646](https://github.com/LawnchairLauncher/lawnchair/issues/6646) intentionally excludes One UI from its QuickSwitch issue umbrella, so test Samsung independently.

**Dependencies:** none. **Upstream:** forward a real reproducible bug, not the fork setup.

## P1 — Expose safe, opt-in voice navigation via existing QSB/gesture seams (#5)

**Progress (October 11):** The bounded voice parser now has a long-press QSB menu action and a user-triggered Android speech intent. An explicit, unambiguous `OPEN_APP` can call `LauncherApps.startMainActivity` in the current profile. **Not built or Samsung-qualified**. Existing QSB mic/search semantics remain unchanged.

**Remaining smallest task:** Qualify the user-invoked entry on Samsung. Speech is delegated to an installed recognizer and **may use remote audio processing**, unlike guaranteed on-device STT. Pass *final* transcripts to `VoiceCommand.parse`; exact-match only `OPEN_APP`, reject ambiguous labels, unsupported intents have no action. After device proof, optionally add `SHOW_HOME` and search. Do **not** implement cross-app accessibility, always-on recording, or cloud credentials in this issue.

**Acceptance:** 45 JVM parser/matcher checks remain green; device tests for unknown/misheard intents, mic denied, unavailable recognizer, repeated utterance, app not installed, profile restrictions, TalkBack description; app opening visibly verified; no action from interim STT fragments; no messages sent or payments tapped.

**Dependencies:** P0. **Upstream overlap:** [AI provider #6635](https://github.com/LawnchairLauncher/lawnchair/issues/6635), [installed-provider PR #7221](https://github.com/LawnchairLauncher/lawnchair/pull/7221), and legacy [mic target #3035](https://github.com/LawnchairLauncher/lawnchair/issues/3035) (closed). Seek maintainer input before a generic upstream patch.

## P1 — Connect optional z0live/Jev decisions to a verified phone-action contract (#6)

**Problem:** Speech transport, bounded local decisions and Android execution presently exist as separate projects; a phone-side authority/receipt boundary is missing.

**Smallest task:** Make an adapter that accepts finalized utterance IDs + typed candidate actions. z0intelligence owns legal-action admission and idempotent dispatch; Jev only chooses from already authorized candidates; z0live owns one realtime mic/session and interruption; launcher executes low-risk native intents. CUA Android is an optional *observe → act → verify* adapter under explicit AccessibilityService consent, not a second planner.

**Acceptance:** fake transport + fake executor tests verify duplicate utterances execute at most once, cancellation does not execute unknown unfinished work, a stale foreground/window revision fails closed, denial does not call executor, outcome receipts distinguish verified/unverified/unknown. No API keys in APK. Record actual p50/p95 recognition-end → verified-change on phone, with battery and false execution metrics.

**Dependencies:** P0, native action part of P1. **Downstream only.** Donor interfaces: [z0intelligence](https://github.com/kvnloo/z0intelligence), [z0live](https://github.com/kvnloo/z0live), [OMP voice integration #104](https://github.com/kvnloo/oh-my-pi/issues/104), [CUA Android AX issue #878](https://github.com/trycua/cua/issues/878).

## P2 — Prototype attention-first spaces on the existing Launcher3 workspace (#7)

**Problem:** Home pages are positional, not intentional; the requested Now/Build/Learn/Life workflows need persistent names and focused app discovery without replacing Launcher3.

**Smallest task:** Start with local, manual spaces mapping to existing pages or optional app groups (not automatic app moving). Retain launcher widgets, folders, backup/restore, multi-profile visibility and navigation gestures. Add a clear undo for space switches. Later test contextual suggestions behind opt-in.

**Acceptance:** rename/activate/persist/reload four spaces without losing regular pages; work/private apps don't leak into an unrelated profile; normal launcher still works with all zer0 features disabled; compare touch count and task-switch time against standard search.

**Dependencies:** P0. **Upstream precedent:** Lawnchair Discussions' Aug 11, 2026 “Context-Aware or Profile-Specific Favorite Apps / Layout Switching for Work Mode” (vinaykharayat); preserve idea attribution.

## Upstream triage: do not duplicate owner work

| Thread | Status checked | Next contribution |
|---|---|---|
| [#6635 AI chatbots in QSB](https://github.com/LawnchairLauncher/lawnchair/issues/6635) | Open; proposer binarymelon | Ask about generic assistant search/voice targets after testing, not branded Jev bundle |
| [PR #7221 installed global search](https://github.com/LawnchairLauncher/lawnchair/pull/7221) | Open; owner already implementing | Review/test; no competing picker |
| [PR #7112 smart local search](https://github.com/LawnchairLauncher/lawnchair/pull/7112) | Open PoC; author credits UsageStats ranking; separate ownership | Benchmark relevance/privacy; don't replace with Jev absent evidence |
| [PR #6566 Android native text classifier](https://github.com/LawnchairLauncher/lawnchair/pull/6566) | Open PoC | Review classification intent safety; no duplicate classifier |
| [#6031 keyboard-first search](https://github.com/LawnchairLauncher/lawnchair/issues/6031) | Open | Small generic upstream candidate after reproducible test |
| [#6711 TalkBack labels](https://github.com/LawnchairLauncher/lawnchair/issues/6711) | Open; report by Graco | Small accessibility bug fix after confirming current branch/device |
| [#7175 license headers](https://github.com/LawnchairLauncher/lawnchair/issues/7175) | Open unresolved | Review distribution compatibility before rebrand/public release |

**Policy:** Upstream PRs target `16-dev`, conform to Conventional Commits, include a focused test plan. Upstream issue creation currently appears restricted on GitHub; use existing issue/Discussion or maintainer channels for feature design. Do not post experimental zer0-only runtime code as an upstream PR. Preserve credit to authors, reporters and testers by name and link.
