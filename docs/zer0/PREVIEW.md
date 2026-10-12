# zer0 integrated preview (not a release)

This branch is an integration **test preview**, not a proposed merge into `16-dev` and not an APK qualified for daily driving.

## Inputs with preserved history

| Source | Source PR | Source head at integration | What this preview contains |
| --- | --- | --- | --- |
| Voice navigation | [#1](https://github.com/kvnloo/lawnchair/pull/1) | `d291cc2` (including style fix) | opt-in QSB long-press voice activity, bounded parser, JVM tests |
| Hidden-app visibility policy tests | [#3](https://github.com/kvnloo/lawnchair/pull/3) | `14df693` | five focused Android-oriented regression cases |
| Stale search result protection | [#4](https://github.com/kvnloo/lawnchair/pull/4) | `c46b38e` | reject callback results for an obsolete query |

Source PRs stay open and independent. The integration branch has actual two-parent merge commits, preserving both ancestry and contributors.

## Additional preview-only iteration

1. **Search performance:** `LawnchairLocalSearchAlgorithm.doSearch` computes calculator and action results once per query instead of per asynchronous provider emission. Original result ordering remains: apps/shortcuts → calculator → other providers → action links. This reduces repeat work for one query; latency gains are **not yet measured on an Android device**.
2. **Visibility correctness:** voice `OPEN_APP` applies Lawnchair's `filterHiddenApps` via `VoiceAppVisibility` before any launch, and rejects uncertain preference reads. The policy comes from existing Lawnchair search, covered by the source PR #3 tests; avoid a second independent policy.
3. **Formatting:** QSB imports reordered per the actual `spotlessCheck` failure log of voice PR #1. Source PR #1 includes this isolated fix.

## Required checks

- [ ] GitHub Actions: full `assembleLawnWithQuickstepGithubDebug`, `assembleLawnWithQuickstepNightlyRelease`, `assembleLawnWithQuickstepPlayDebug`.
- [ ] `spotlessCheck`; repository-base formatter warnings must be distinguished from changes introduced here.
- [ ] Voice JVM parser/matcher checks.
- [ ] On-device Samsung One UI manual smoke: dock QSB, microphone launch/cancel, unique app name, uninstalled/ambiguous labels, hidden-app `NEVER`, `IF_NAME_TYPED`, `ALWAYS`, work/private profile rejection, widgets, folders, Home/recents.
- [ ] Search regression checks: delayed provider, clear/retype same query, zero-state suggestions, query cancellation and hidden-app policy. PR #4's same-query race is **not** solved by its equality guard.
- [ ] If faster search is claimed: instrument p50/p95 time from query modification to first app result and final result for a representative device corpus, with optimized vs baseline ABAB runs. Include CPU use and correctness; synthetic counts are not an end-user latency metric.

## Safety and provenance

No new always-on mic, AccessibilityService authority, SMS/payment action or ChatGPT consumer app automation. Recognition is delegated to the installed Android recognizer, which may send audio off-device. Hidden apps are still subject to the user's existing Lawnchair preferences.

Credit AOSP/Launcher3, Lawnchair contributors, the original zer0 voice prototype and the reporters/maintainers of [upstream #7325](https://github.com/LawnchairLauncher/lawnchair/issues/7325) (Flightkick), [upstream PR #7347](https://github.com/LawnchairLauncher/lawnchair/pull/7347) (MrSluffy), and the [testing request #7186](https://github.com/LawnchairLauncher/lawnchair/issues/7186) (validcube). See fork [Wave 0 issue #2](https://github.com/kvnloo/lawnchair/issues/2).

**Do not merge this preview PR into `16-dev` or propose it upstream as one feature PR.** Promote separately verified and appropriately scoped improvements only.
