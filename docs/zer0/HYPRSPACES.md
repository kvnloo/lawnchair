# HyprSpaces — experimental Android spaces/window control

**Status (2026-10-17): previous head e8cde08 passed full hosted Android CI (three APK variants, Spotless) and JVM checks. Current pin changes need a fresh exact-head CI; physical Samsung testing has not run.** Downstream experiment only; branch [experiment/zer0-hyprspaces](https://github.com/kvnloo/lawnchair/tree/experiment/zer0-hyprspaces) is based on the [integrated preview](https://github.com/kvnloo/lawnchair/pull/8), not on production 16-dev.

## Current scope

- Dock QSB long-press → Spaces (experimental), alongside Customize and Voice. Standard home, app drawer, search, widgets and folders remain unchanged.
- Local named contexts, initially Now / Build / Learn / Life, with create / rename / delete / activate. These are **context labels**, not separate OS task stacks or Android desktops.
- **Optional existing-home-page link:** open Spaces from the Launcher QSB while on the intended home page, activate the named space, then tap "Link this space to the home page I came from". Switching to that space later sends one private, expiring request to Lawnchair's existing Workspace screen ID. Launcher validates the page still exists before snapping. It does not move icons, widgets or tasks. A screen ID is stable while that page exists, not a promise across layout import/restore. Unlink or delete the space to remove the binding. Scratch cannot bind. Opening Spaces via voice has no source-page binding picker, but can switch to an already-linked space.
- Each named space has up to eight pinned personal-profile app shortcuts. Use the first-app picker to add pins; tap a shortcut to launch or × to unpin. Scratch pins stay in activity memory only. Pinned component IDs are serialized privately; labels are displayed only if the app currently passes Lawnchair's hidden-app policy. Launching rechecks the installed component and visibility; renamed/deleted spaces migrate or delete pin state. There is no background app scanning.
- Scratch is transient: active status and selected app pair are not stored. Normal spaces persist their names, selection, and two personal-profile app components in private SharedPreferences.
- A separate controlled baseline requests one app adjacent to the Spaces UI, while the existing explicit app-pair button launches one app then requests an adjacent second app using Android public Intent flags FLAG_ACTIVITY_NEW_TASK and FLAG_ACTIVITY_LAUNCH_ADJACENT. This is a **best-effort placement request**, not verified split-screen or a fully capable tiling manager. A partial failure can leave one app open. No automatic retries.
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

## Permissionless on-device multiwindow probe

The experiment now records the last *launch request* in private app settings, with Android API, advertised freeform feature, and whether the Spaces Activity was in multiwindow at request time. Returning to Spaces offers **explicit user report** controls for "two visible", "fullscreen only", "one visible", or "no change". Users can choose **Copy redacted test receipt**; it contains no installed-app package IDs, screen contents, audio, or device serial. The literal `os_verified=false` remains present even when a person reports a split. Clear the receipt from the same UI.

Run **adjacent-to-Spaces** and **two-app-pair** separately on the real Samsung phone. A successful `startActivity()` return means only a launch request was emitted, not that the compositor honored any intended layout. Save a user screenshot and ADB logs *only with explicit local consent*; no automatic uploads.

## Reproducible ADB proof on Samsung

Run `bash tools/zer0/samsung-spaces-check.sh preflight` with one USB/ADB-authorized device, optionally `ANDROID_SERIAL=...`. It prints OS/One UI properties and the HOME role holder without capturing private screen data.

After a **user-initiated** split experiment, optionally run `bash tools/zer0/samsung-spaces-check.sh capture ./my-local-evidence` to save raw Android activity and window dumps. The files can contain private app/window metadata, stay local, and are **never auto-uploaded**; review and redact them before attaching or sharing. Pair a manual screenshot and the app's explicit redacted probe receipt for actual human-visible layout confirmation. A `dumpsys` line by itself is not a verified split layout without a matching window/task observation.

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
