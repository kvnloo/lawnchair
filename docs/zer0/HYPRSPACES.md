# HyprSpaces — experimental Android spaces/window control

**Status (2026-10-17): previous head e8cde08 passed full hosted Android CI (three APK variants, Spotless) and JVM checks. Current pin changes need a fresh exact-head CI; physical Samsung testing has not run.** Downstream experiment only; branch [experiment/zer0-hyprspaces](https://github.com/kvnloo/lawnchair/tree/experiment/zer0-hyprspaces) is based on the [integrated preview](https://github.com/kvnloo/lawnchair/pull/8), not on production 16-dev.

## Current scope

- Dock QSB long-press → Spaces (experimental), alongside Customize and Voice. Standard home, app drawer, search, widgets and folders remain unchanged.
- Local named contexts, initially Now / Build / Learn / Life, with create / rename / delete / activate. These are **context labels**, not separate OS task stacks or Android desktops.
- Each named space has up to eight pinned personal-profile app shortcuts. Use the first-app picker to add pins; tap a shortcut to launch or × to unpin. Scratch pins stay in activity memory only. Pinned component IDs are serialized privately; labels are displayed only if the app currently passes Lawnchair's hidden-app policy. Launching rechecks the installed component and visibility; renamed/deleted spaces migrate or delete pin state. There is no background app scanning.
- Scratch is transient: active status and selected app pair are not stored. Normal spaces persist their names, selection, and two personal-profile app components in private SharedPreferences.
- An explicit button launches one app then requests an adjacent second app using Android public Intent flags FLAG_ACTIVITY_NEW_TASK and FLAG_ACTIVITY_LAUNCH_ADJACENT. This is a **best-effort placement request**, not verified split-screen or a fully capable tiling manager. A partial failure can leave one app open. No automatic retries.
- Existing one-shot voice prototype now accepts SWITCH_SPACE only for an exact, locally configured name; an unrecognized space has no execution side effect. OPEN_APP remains exact-label only.
- Pure-JVM SpaceModel constraints cover custom name validation, case-insensitive uniqueness, 12-space cap, persistence, rename/delete active selection, corrupt preference recovery and Scratch isolation.

## Not yet built

Arbitrary tiling layouts, window geometry inspection/verification, move/resizing tasks, One UI pop-up control, app drawer replacement, predictive spaces, Android Accessibility/TaskOrganizer adapter, and persistent GPT realtime voice are **not** implemented. Consumer ChatGPT Voice mode cannot be treated as a documented third-party app automation API.

## Backend strategy

| Tier | Authorization | Capabilities |
| --- | --- | --- |
| 0: Launcher-native | Existing launcher role | Named spaces, searchable app launcher, saved app pairs |
| 1: Public Android | Normal Activity intent | App launch; best-effort adjacent launch; popup/size behavior not guaranteed |
| 2: Samsung / UI assistance | Explicit user consent | Explore One UI pop-up, app pairs and layout verification, with capability probes |
| 3: Optional accessibility | Independently enabled by user | Observe focused UI/execute limited gestures with receipts, never silent blanket control |
| 4: Optional privileged desktop backend | Device/user dependent | Explore true tiling/task moves separately; no root/system APIs in this branch |
| Voice bridge | Opt-in session, developer backend | z0live / OpenAI Realtime function calling → z0int/Jev bounded PhoneAction → local executor/receipt |

A PhoneAction must carry utterance ID, action ID, expected foreground revision, profile, expiry and authority. Execution must be idempotent and return verified/unverified/unknown outcomes. Repeated commands or stale state do not silently retry, and no API key is stored inside the APK.

## Next evidence gates on Galaxy S25 Ultra

1. Run bash tools/zer0/test-voice-commands.sh, ./gradlew spotlessCheck, and ./gradlew assembleLawnWithQuickstepGithubDebug with the repo submodules, and attach logs / SHA.
2. Preserve One UI Home as the fallback; verify QSB normal search, Customize, Voice and Spaces, plus icons, widgets, folders, app drawer, recents and TalkBack.
3. Test add/rename/delete, restart and Scratch reset; normal space/app pair persistence; work/private profile isolation.
4. Request split for two resizable apps and an incompatible pair. Capture screenshots and classify actual state: true split / fullscreen / no-op / one-app-only. Launch-intent return is NOT verification.
5. Measure utterance-end → verified state p50/p95 and false launches; only then develop pop-up/resize and long-lived voice control.

## Platform references

- [Android multi-window](https://developer.android.com/develop/ui/views/layout/support-multi-window-mode)
- [Samsung One UI app pairs](https://www.samsung.com/us/support/answer/ANS10002022/)
- [OpenAI Realtime tool calling](https://developers.openai.com/api/docs/guides/realtime-conversations)
- [Android VoiceInteractionService](https://developer.android.com/reference/android/service/voice/VoiceInteractionService)

Lawnchair/AOSP original authorship remains intact. Inspired by Hyprland/niri. Previous work: [voice #5](https://github.com/kvnloo/lawnchair/issues/5), [z0int/z0live contract #6](https://github.com/kvnloo/lawnchair/issues/6), [spaces #7](https://github.com/kvnloo/lawnchair/issues/7), [quality tracker #2](https://github.com/kvnloo/lawnchair/issues/2). Keep branded AI/windows experiments downstream; propose generic upstream changes only with device proof and maintainer review.
