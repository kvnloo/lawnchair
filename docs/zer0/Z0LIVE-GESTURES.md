# Opt-in z0live Standby (fork)

Stacked on `feat/z0live-realtime` / PR #14, separate from the concurrent multi-agent telemetry builder and PR #28's NOOP capability onboarding. No edits to those working trees. The service keeps its microphone FGS type continuously foreground across idle/session/idle, creates no WebRTC or AudioRecord during idle, and exposes Standby OFF. Double tap starts/toggles mute; triple tap closes the current session, then returns ready. An existing capture is never toggled both locally and by the broker.

The system-selected VoiceInteractionService exemption is gated by active service + ROLE_ASSISTANT + permission + explicit prior opt-in. Permission/OS denial is not capture success. The debug-only shell Activity is exported behind DUMP for the separately opted-in broker ADB fallback; no real adb/phone/install has been used. Release controls remain nonexported. A killed process is not an immortal Standby service.

Authenticated command polling sends coarse screen-unlock and Lawnchair resume timestamps. Voice here pins this paired phone target for 5 minutes. Credentials remain in the existing encrypted pairing store. No raw motion/key/audio in broker commands or CI. Claiming a command is at-most-once dispatch, not proof of microphone or WebRTC connection.

Full contract, Android 14–16 primary sources, One UI evidence limits, sender configuration and owner acceptance script:
https://github.com/kvnloo/z0live/blob/feat/oura-context-routing/docs/GESTURES.md

Offline policy tests exercise the exact admission decision used by the service (48 combinations). Android compilation/lint/APK CI are separate gates. **Delayed first capture, no privacy indicator while idle, locked-phone operation and Samsung battery policy remain NOT RUN on hardware.** This is not an Android16/One UI compatibility certification or a claim that provider handshake latency is instant.

CI: `.github/workflows/z0live-standby-apk.yml` publishes a downloadable exact-SHA APK, SHA256SUMS, package/version/commit metadata and signing certificate. It never replaces stable/latest assets and never installs anything. Existing inherited lint debt remains pinned; feature diagnostics must not be baselined.
