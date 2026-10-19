# z0live Android implementation map

Owner-facing setup, verification and limitations: [Z0LIVE.md](Z0LIVE.md).
Discovery and existing-work provenance: [Z0LIVE_DISCOVERY.md](Z0LIVE_DISCOVERY.md).

The resumed implementation stays on `feat/z0live-realtime`, based on the existing
`experiment/hyprspaces-phone-harness` branch. Backend ownership is separate: Android
consumes the canonical z0live API; this work does not modify or restart that service.

- `Z0LiveProtocol`: authenticated bounded OkHttp calls, strict canonical inbox pages,
  builtin tool routes, broker identity and end-session, direct ephemeral SDP exchange.
- `Z0LiveService` / `Z0LivePeer`: explicit microphone foreground service, service-owned
  native RTC thread, audio focus, captions, bounded event queue and inbox TTS.
- `Z0LiveEvents` / `Z0LiveTelemetry` / `Z0LiveTurnState` / `Z0LiveAudioState`: executable
  JVM-tested event normalization, retry/dedupe identity, safe-turn and mute boundaries.
- `Z0LiveActivity`: visible permission/start controls and pairing confirmation; exported
  no-history link trampoline strips credential-bearing intents before internal UI entry.
- `Z0LiveStore`: Keystore-backed EncryptedSharedPreferences for pairing, panel URL and cursor.
- QSB, widget, tile and assistant services: shared entry controls, no receiver-started mic.
- `RippleActivity`: configured same-origin HTTPS panel; no native phone/credential bridge.
- `.github/workflows/z0live-apk.yml`: bounded contracts, formatting, Android lint/build,
  signed experimental prerelease with stable URL and inspectable APK metadata.

Review boundaries: late callbacks after end/re-pair, manual mute across TTS/focus,
returned inbox cursor/pagination, broker event vocabulary, continuation after response.done,
credentials in redirects/backups/history, and strict WebView origins. Device/OEM audio,
assistant gestures and screen-off behavior remain physical-phone acceptance work.
