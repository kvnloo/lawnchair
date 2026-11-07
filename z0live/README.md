# z0live companion (slice S0b)

- `core/` (`:z0live-core`, Android library): launcher-free protocol, broker session, transport, speech gate,
  telemetry, accessibility policy and all z0live resources. The JVM suites in `tools/zer0/test-z0live-*.sh`
  compile from here.
- `shared/`: service, store, gesture channel, inbox notifications, accessibility, assistant and tile services.
  Lawnchair and the companion both compile this one copy; each app supplies `Z0LiveActivity` and `Z0LiveHost`.
- `app/` (`:z0live-app`, package `app.zer0.z0live`): the standalone companion. Build with
  `./gradlew :z0live-app:assembleDebug` (JDK 21).

## Launcher contract

Send an activity intent to `app.zer0.z0live`:

| Action | Effect |
|---|---|
| `z0live.action.START` | no call: open the controls, which start the call (mic permission stays in the visible Activity) |
| `z0live.action.MUTE` | during a call: toggle mute |
| `z0live.action.END` | during a call: end it |

Anything else, or MUTE/END with no call, does nothing (`Z0LiveCommands`, pinned by `Z0LiveCommandsTest`).

    adb shell am start -a z0live.action.START -p app.zer0.z0live

Lawnchair sends them from Settings > z0live > z0live app (`Z0LiveCompanion`, shown only when the companion is
installed). Senders use `Z0LiveCommands.PACKAGE` and `SEND_FLAGS` (NEW_TASK | EXCLUDE_FROM_RECENTS).

`android.intent.action.ASSIST` with package `app.zer0.z0live` also starts a call, so Detent's existing
assistant picker reaches the companion with no Detent change. Lawnchair keeps its embedded copy until the
companion passes S1; then Lawnchair's gestures send these intents and its copy is removed.
