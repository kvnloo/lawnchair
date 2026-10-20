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
- cursor is encrypted, monotonic and scoped to backend + paired-device credential. Each explicit connection snapshots the latest inbox id and starts there: history is never an opening speech/summary. Live reply identities are persisted before speech dispatch;
- `action:null` means speech only. Declared local tools run through public Android APIs;
  unknown, invalid and stale-session inbox actions are rejected with a structured result;
- phone tools: home, fuzzy app opening/disambiguation and app search/list, web search/URL,
  Settings pages, Clock timer/alarm intents, maps geo intent, dialer/SMS composer (never
  auto-send), media keys, torch (optional Camera permission), media volume, share-note,
  Ripple open/close and z0live mute/end;
- back/recents/notification shade are declared only with the separate explicit z0live
  navigation opt-in AND the existing Lawnchair Accessibility service enabled. No screen
  reading or Accessibility gesture/click automation is added;
- builtin `/v1/tools/NAME` dispatch uses every entry of the session mint's validated `builtin_tools` catalog, including `ask_z0`, `cancel_work` and future additive names. Broker builtins take precedence over phone-local tool names; older brokers retain the legacy fallback. `ask_hermes` gets a bounded 40-second client deadline for its legacy synchronous wait;
- PR #27 desktop multi-agent observations use `floor.data.state: user|assistant|quiet`, explicit `barge_in` and `cancel.data.topic_changed:true` for literal “never mind”/“cancel that”/“stop that”. VAD start/stop and response create/done are forwarded; quiet is withheld until both the user and actual WebRTC output buffer are quiet. Muted/focus-lost VAD cannot cancel work;
- a 20-second `app_state` active-session heartbeat includes the current `floor`, even when quietly listening or muted. It starts only after a human-started mint and stops on End/error/service destruction. Events use a separate serial control executor, so an outstanding legacy work-tool HTTP wait cannot block cancellation;
- barge-in/cancel drops queued and late-arriving inbox speech for interrupted `params.turn_id` values, advances the durable cursor and emits a non-spoken cancellation receipt. Cancelled replies are not re-notified. Admission racing an interruption is cancelled via exact-turn `/v1/cancel`, without cancelling a newer work tree;
- `response.done` supplied usage is forwarded as `model_usage.data:{turn_id,response_id,usage}` only after association with a broker-returned, session-owned turn. Early usage waits for tool admission; continuations/inbox relay retain that turn. Ordinary chat without a broker turn is not sent as invalid usage. No token/cost values are invented;
- bare batched event arrays with canonical types, Unix seconds and UUID `data.event_id`;
- broker-generated session identity, explicit `/v1/sessions/ID/end` on hangup;
- tool continuation waits for `response.done` and pending tool work. Android TextToSpeech
  is not used. A new reply scoped to the current human-started session is injected as
  data into that Realtime conversation, then GPT relays it in its existing voice with
  tool calls disabled. Old-session/broadcast replies are silent notifications, not speech;
- inactive delivery is a best-effort JobScheduler poll (minimum 15-minute interval,
  network required, Android/OEM scheduling and notification permission apply). It never
  starts a microphone session or executes phone tools. Interrupted replies also remain
  in silent notifications; the local cursor prevents automatic replay;
- turn detection is `semantic_vad` (eagerness high, far-field noise reduction) with
  `interrupt_response:false, create_response:false`; `Z0LiveSpeechGate` interrupts and replies only on
  transcript words. Over assistant speech it commits the first 250 ms of the owner's speech early
  (barge probe) so words arrive within ~0.6 s; a reply starts at the first two-word delta.
- historically barge-in enabled server VAD `interrupt_response` and `create_response`.
  User speech cancels an active response and clears the WebRTC output audio buffer;
  OpenAI's WebRTC buffer-clear also truncates unheard conversation audio. The mic stays
  available for interruption unless the owner manually mutes it or audio focus is lost.

### Frameless Bidi transport (OMP `/live` architecture)

Settings > z0live > **Voice connection** chooses the transport: **Auto** (default) offers
`transports:["bidi","realtime"]` and falls back to gpt-realtime if Live call setup fails; **Realtime**
offers only `["realtime"]`; **Live** offers bidi and reports a failed setup instead of switching
voices. With Auto or Live the APK offers `transports:["bidi","realtime"]` at mint. A broker with `Z0LIVE_LIVE_TRANSPORT=bidi`
(its default) answers with a `gpt-live-1-codex` session and **no client secret**; the phone posts
its SDP offer to the broker's authenticated `/v1/live/calls`, which signals Codex with the desktop's
ChatGPT login, owns the call's sideband, and returns the SDP answer. Media is still direct
phone ↔ OpenAI WebRTC. Older brokers ignore the offer and keep minting gpt-realtime.

- The model has no function tools in this mode (the provider rejects `tools`). It natively creates
  client delegations; the broker answers each one through one persistent Hermes session (silent
  `commentary` progress, one speakable final) or, for a single declared phone action, queues an
  ordinary inbox action (`say:""`). The phone executes it exactly as before and reports the usual
  `tool_result` event, which becomes the spoken result. The inbox is polled every 600 ms while a
  Bidi call is up (3 s for gpt-realtime).
- The phone sends **no** control messages on the data channel: no VAD `session.update`,
  `response.create/cancel` or `output_audio_buffer.clear`. Inbox `say` text is never injected; it
  becomes a silent notification. The debug text-injection hook is disabled for Bidi calls.
- Captions come from `input_transcript.added`, `output_transcript.added` and `turn.done`; an
  `error` event ends the call honestly. Transcripts are recorded by the broker, not re-posted.
- Barge-in: the model does its own full-duplex turn-taking. `Z0LiveBidiBarge` additionally mutes
  playback (`setSpeakerMute`) the moment the live owner transcript shows real words
  (`Z0LiveSpeechGate.isSpeech`) while an assistant turn is open or ended less than 1.5 s ago (its
  buffered tail), and restores it for the model's next turn. The model yields at about the moment its
  transcript of the owner arrives (~1.1 s), so this cuts the buffered tail, not the model's yield. Noise and grunts never duck. Echo is handled by the platform AEC on the
  `VOICE_COMMUNICATION` source; the desktop OMP-style level gate is not used on Android.
- If `/v1/live/calls` fails, the same offer is sent to OpenAI with a freshly minted gpt-realtime
  session (`transports:["realtime"]`) and the old broker session is ended, so the phone keeps working.

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
./gradlew testLawnWithQuickstepPlayDebugUnitTest
./gradlew lintLawnWithQuickstepPlayDebug
./gradlew assembleLawnWithQuickstepPlayDebug
```

`test-z0live-protocol.sh` compiles the actual HTTP client and portable broker-session
state against a strict PR #27-shaped loopback fake. It exercises every minted builtin
(including additive future names), floor/audio boundaries, quiet/muted heartbeat,
barge/topic cancellation, queued/late inbox suppression, audible and quiet pending-HTTP
admission races (including failed-request cleanup), usage
association/deduplication and session End. `test-z0live-backend-contract.sh` runs that
suite plus the legacy broker contract. These JVM tests—not the variant's currently
`NO-SOURCE` Gradle unit-test task—provide the protocol regression evidence; they do
not certify a physical microphone, native worker stop or Android service lifecycle.

Full-project lint initially failed with thousands of existing upstream/fork diagnostics.
The native baseline was generated against detached pre-feature source `ba091219e2`;
101 additional NewApi/WrongConstant findings on **29 byte-identical inherited Quickstep
files** are source-hash pinned by `check-z0live-lint-baseline.py` and the provenance JSON.
No z0live source/resource findings are baselined. This is a no-new-feature-errors gate,
not a claim that the inherited launcher is globally lint-clean. New-feature findings were
fixed (including backup whitelist validation, back dispatcher, Compose resource reads and
English-only labels); the legacy tile overload is narrowly guarded/suppressed below API 34.

**Phone automation is currently prohibited until the parent explicitly re-enables it.**
The following is a reference command, not authorization to execute it. For a reserved,
consented paired debug APK, the real-phone text-turn suite runs in one batch:

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

## Owner multi-agent acceptance (manual; no agent device access)

1. Install/update the verified `z0live-latest` APK with Android's normal installer (do not uninstall to bypass a signer mismatch). End any existing voice session, then tap Start to mint the new catalog. Ask “What model are you using?” and “What changed today?”; confirm audible/captioned replies and the broker's `ask_z0`/work-tool receipts. No re-pairing or new permission is needed for desktop tools.
2. Ask for a read-only comparison that needs desktop workers. While the acknowledgement/reply is speaking, interrupt with “never mind”. Repeat once while the audio is quiet but desktop work is still running. Audio must stop promptly; no queued progress/final speech from that turn may play afterward. Broker trace must contain cancellation for the exact turn and terminal `cancel_confirmed` receipts for recorded meta/child run IDs; `cancelling` alone is not confirmation.
3. Start another read-only request, then say “Cancel the desktop work using cancel_work”. Confirm `/v1/tools/cancel_work`, the cancellation event and honest pending/confirmed stop status. Start a different request afterward: its reply must still work and must not inherit the previous turn's suppression.
4. Leave the session quietly listening for over two minutes, then request work. Verify 20-second `app_state` heartbeats keep the current floor/session active. Repeat muted: no synthetic speech-start/barge events or automatic unmute. End via the sheet/notification and confirm heartbeats stop and the broker session is ended. Start again: no historical reply should replay.
5. During an inbox reply, speak over it; then try mute/unmute and an incoming-call/audio-focus interruption. The actual speaker buffer must clear, floor must not become quiet while either side is audible, and already claimed interrupted replies must not repeat. Test speaker/headphones/Bluetooth and screen-off separately.
6. Inspect the broker's trace for the work turn: L0 `provider_usage` must carry the actual provider `response_id` and supplied token/cache counters (including tool continuation/inbox relay when supplied). Ordinary chat without an admitted broker turn has no turn-scoped usage; unknown dollars remain unknown.

These are owner checks to perform, **not current hardware acceptance results**. No phone query, install, microphone turn or device cleanup is authorized for this delivery agent.

## Owner device acceptance checklist / known gaps

- Verify actual two-way audio/captions and "tell Hermes to ..." → desktop native Hermes
  work → spoken inbox reply; request an explicit conversation summary and inspect backend history.
- Mute → trigger Hermes reply → ensure mute remains; interrupt provider voice; test headphones,
  Bluetooth, calls/audio-focus loss, screen-off and switching apps. These need a physical phone.
- Test unavailable/revoked pairing, Tailscale reconnect, permission denial, foreground End,
  new session and process restart. Network/peer loss ends honestly; **automatic WebRTC
  reconnection is not implemented** (Start creates a new session).
- Persistent foreground session is user-started, not hotword/boot listening or an unkillable
  service. Samsung battery restrictions can stop it. **Current-repair device acceptance:
  NOT RUN.** Phone actions were stopped at the owner's/parent's direction after repeated
  inbox speech disrupted phone use. Earlier diagnostic installs/turns are not acceptance
  proof for the no-replay/Realtime-inbox/barge-in repair. Do not run ADB, install or inject
  turns until the parent explicitly re-enables phone access. Local/JVM/CI checks alone
  do not satisfy G0/G2/G8.
- Inbox uses polling, not SSE. Telemetry is bounded in memory, retries up to three times;
  it is not a durable offline outbox. Long outages/process death can lose unsent events.
- New experimental z0live labels are English-only (marked non-translatable pending localization).
- No screen reading, unrestricted remote phone-action execution, embedded Ripple native component,
  verified Ripple deployment or separate shared Android SDK is shipped.
