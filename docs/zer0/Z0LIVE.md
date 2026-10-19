# Lawnchair z0live voice + Ripple

Experimental fork build, branch `feat/z0live-realtime`. This extends the existing
voice/HyprSpaces harness rather than replacing its parser or phone-action gate.
See [discovery receipts](Z0LIVE_DISCOVERY.md) for the earlier work and Ripple identity.

## Install and pair

1. Download `Lawnchair-z0live.apk` from the `z0live-latest` prerelease in
   `kvnloo/lawnchair`. Package is `app.lawnchair.play.debug`. It is debug-signed
   with the fork's stable private CI signer, not an official Play release.
   A different signing certificate on an existing installation requires a separate
   migration/uninstall; do not uninstall without backing up your launcher layout.
2. Enable Tailscale on the phone and desktop. The broker URL is
   `https://0.taila30dc.ts.net:8791`; it must be tailnet-reachable with valid TLS.
3. On the desktop run, in the z0live checkout:
   ```sh
   z0live pair s25 --qr
   ```
   Open **Lawnchair Settings → ⋮ → Experimental features → Integrations → z0live → Manage pairing → Scan pairing QR**,
   scan the desktop QR and confirm the displayed server origin. Google Play services
   supplies a QR-only scanner; QR scanning requests no Camera permission; the separate optional flashlight tool requests Camera permission only when explicitly enabled in settings. The scanner
   module may need a first-use download. If unavailable, an error is shown and you
   can still open a pairing deeplink or paste it into **Settings → Paste pairing link
   → Review pairing**. Treat QR codes and links as passwords. Credentials are only
   saved after confirmation, Keystore-encrypted and excluded from backups. Both scan
   and VIEW links use the same validator, single-use pending request and confirm/save
   path. Pending links stay in process memory until controls are resumed rather than
   expiring unseen after 30 seconds. Process death discards them: re-scan/reopen the
   link. No unconfirmed credential is written to disk or retained in Activity intents.
4. Tap the QSB microphone, or Start in the Material 3 session sheet. Grant microphone and
   notification permissions. Pairing and external assistant/deeplink entry do not
   start recording without a visible Start action. Captions, mute and End are in
   the controls; the foreground notification also has mute/end. Closing controls
   does not hang up; End does. Android may kill the process: it is deliberately
   non-sticky and never silently restarts microphone capture.
5. If the server is unreachable, use **Open Tailscale**, connect/approve its VPN,
   and return. Health retries are automatic and bounded (six attempts); returning
   also requests a retry. Lawnchair does not independently enable another app's VPN,
   and does not assume authentication/TLS/server failures mean the VPN is off.

## Other entry points

- Long-press the QSB: z0live settings, previous Gemini handoff, local fast voice,
  search-provider voice and HyprSpaces remain available.
- Home screen → Widgets → z0live: Voice opens the same controls; Ripple opens the
  configured panel. RemoteViews cannot embed an interactive WebView in the widget.
- Edit Quick Settings → add z0live. It unlocks/opens a resumed Activity before mic start.
- Android Settings → Apps → Default apps → Digital assistant → z0live/Lawnchair.
  VoiceInteractionService and ACTION_ASSIST open visible controls. Samsung side-key
  assignment and assistant gesture availability depend on device/OS settings.
- Public links `z0live://voice` and `z0live://ripple` open controls/panel only.

## What Ripple means here

Ripple is the `@kvnloo/ripple` React/React Native **ephemeral intent surface** in
`kvnloo/ripple`'s closed/unmerged PR #1 (`cursor/intent-surface-e30f`), used by Dash.
Its old working names were `aodl-ui`/`IntentSurface`. It is not a known hosted service.
No deployed Ripple URL was verified, so this APK ships a **generic configurable HTTPS
WebView host**, not the native Ripple React component. Set your trusted, self-contained
web build URL in z0live Settings. The transient panel has Close and excludes itself
from Recents; there is no SYSTEM_ALERT_WINDOW permission, cross-app bubble or JS-to-native
phone bridge. Navigation/subresources are same-origin HTTPS only; file/content access,
WebView mic/camera/geolocation, mixed content and third-party cookies are denied.
No z0live pairing token is injected into pages. Web storage may persist across panel
openings, so this is an ephemeral window, not a guaranteed incognito browsing session.

## Protocol and voice ownership

`Z0LiveService` owns native `org.webrtc` audio, peer, event channel, audio focus,
notification, bounded telemetry and authenticated polling. Audio travels directly
phone ↔ OpenAI Realtime; final transcripts and tool/summary events go to the desktop
broker and its Hermes handoff, not raw audio. The backend's configured provider identity
and entitlement determine whether session creation works; an APK build is not proof
of provider entitlement.

The canonical contract is the z0live backend's `docs/API.md`:

- authenticated `/v1/health`, `/v1/realtime/session`, direct OpenAI `/v1/realtime/calls`;
- inbox is `{messages:[...],after:N,has_more:B}`, **not a bare array**;
- cursor persists after handling; subsequent pages drain without waiting for a new session;
- `action:null` means speech only. Declared local tools run through public Android APIs;
  unknown, invalid and stale-session inbox actions are rejected with a structured result;
- phone tools: home, fuzzy app opening/disambiguation and app search/list, web search/URL,
  Settings pages, Clock timer/alarm intents, maps geo intent, dialer/SMS composer (never
  auto-send), media keys, torch (optional Camera permission), media volume, share-note,
  Ripple open/close and z0live mute/end;
- back/recents/notification shade are declared only with the separate explicit z0live
  navigation opt-in AND the existing Lawnchair Accessibility service enabled. No screen
  reading or Accessibility gesture/click automation is added;
- builtin `/v1/tools/tell_hermes`, `/v1/tools/get_context`, `/v1/tools/summarize`, plus the broker's live work tools (`work_search`, `ask_hermes`, `start_task`, `task_status`, `what_changed`). The session mint's validated `builtin_tools` catalog is authoritative; older brokers use a compatibility fallback. `ask_hermes` gets a bounded 40-second client deadline for its 25-second synchronous wait;
- bare batched event arrays with canonical types, Unix seconds and UUID `data.event_id`;
- broker-generated session identity, explicit `/v1/sessions/ID/end` on hangup;
- tool continuation waits for `response.done` and pending tool work; Hermes TTS waits
  for user/provider idle and temporarily suppresses the mic without losing manual mute.

## Native surfaces and identity

The settings destination uses Lawnchair's existing PreferenceLayout, PreferenceGroup,
ClickablePreference and SwitchPreference within the typed navigation graph. The session
sheet and Ripple chrome use LawnchairTheme, Material 3, dynamic colors and native
icons. The old programmatic Java activities are removed. Closing the session sheet
keeps the explicit foreground session alive; the ongoing notification is the minimal
cross-app surface. No overlay-window permission is requested. Pairing and captions
remain protected by FLAG_SECURE; do not remove it just to collect screenshots.

Persona `z0live.persona.v2` is minted server-side with the exact current builtin and
app tool schemas. It knows ChatGPT Realtime (`gpt-realtime`, `marin`), direct phone ↔
OpenAI WebRTC, the short-lived tailnet desktop broker and separate Claude Opus Hermes
session. Local tools return structured dispatch/ambiguity/permission/error results;
Clock/composer/media dispatch is not proof of saved alarms, sent messages or playback.

## Verification

```sh
bash tools/zer0/test-voice-commands.sh
bash tools/zer0/test-z0live-protocol.sh
bash tools/zer0/test-z0live-audio.sh
./gradlew spotlessCheck
./gradlew lintLawnWithQuickstepPlayDebug
./gradlew assembleLawnWithQuickstepPlayDebug
```

Full-project lint initially failed with thousands of existing upstream/fork diagnostics.
The native baseline was generated against detached pre-feature source `ba091219e2`;
101 additional NewApi/WrongConstant findings on **29 byte-identical inherited Quickstep
files** are source-hash pinned by `check-z0live-lint-baseline.py` and the provenance JSON.
No z0live source/resource findings are baselined. This is a no-new-feature-errors gate,
not a claim that the inherited launcher is globally lint-clean. New-feature findings were
fixed (including backup whitelist validation, back dispatcher, Compose resource reads and
English-only labels); the legacy tile overload is narrowly guarded/suppressed below API 34.

For a consented paired debug APK, run the real-phone text-turn suite in one batch:

```sh
python3 tools/zer0/device-z0live-checks.py --serial DEVICE \
  --broker-db /absolute/path/to/broker.db --out /private/device-receipts \
  --consent-real-device-actions
```

The receiver is packaged only from `debug/`, protected by Android's shell/system DUMP
permission, never starts microphone capture and accepts turns only when connected and
idle. The driver shell-quotes complete turns on the device, mutes the test mic, verifies
the exact full injected transcript, waits for real GPT audio completion and tool results,
checks focus transitions, cancels its timer and ends only its own session. It aborts if
the session changes. Reserve the phone for the batch: concurrent owner navigation can
invalidate focus evidence. `--only` runs a bounded continuation set (including
`what-changed`). These are live provider checks, not fixtures; microphone/STT, physical
speaker quality, a saved alarm, actual media playback and configured Ripple still need
their own acceptance evidence.

CI builds on push to this branch and publishes an immutable `z0live-build-*` prerelease
plus the moving `z0live-latest` download, metadata, checksum and certificate. Metadata
records commit, run, versionName, package and byte size. CI never embeds desktop/device
credentials and does not need a running broker.

## Owner device acceptance checklist / known gaps

- Verify actual two-way audio/captions and "tell Hermes to ..." → desktop native Hermes
  work → spoken inbox reply; request an explicit conversation summary and inspect backend history.
- Mute → trigger Hermes reply → ensure mute remains; interrupt provider voice; test headphones,
  Bluetooth, calls/audio-focus loss, screen-off and switching apps. These need a physical phone.
- Test unavailable/revoked pairing, Tailscale reconnect, permission denial, foreground End,
  new session and process restart. Network/peer loss ends honestly; **automatic WebRTC
  reconnection is not implemented** (Start creates a new session).
- Persistent foreground session is user-started, not hotword/boot listening or an unkillable
  service. Samsung battery restrictions can stop it. Real S25 install, connection, identity,
  app matching/Chrome, Wi-Fi, torch and volume checks have been exercised; full per-feature
  acceptance must use the current release APK and the device receipts, not CI alone.
- Inbox uses polling, not SSE. Telemetry is bounded in memory, retries up to three times;
  it is not a durable offline outbox. Long outages/process death can lose unsent events.
- New experimental z0live labels are English-only (marked non-translatable pending localization).
- No screen reading, unrestricted remote phone-action execution, embedded Ripple native component,
  verified Ripple deployment or separate shared Android SDK is shipped.
