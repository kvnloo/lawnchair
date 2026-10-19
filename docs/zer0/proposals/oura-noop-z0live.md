# Oura Gen 3 → NOOP → Lawnchair z0live (downstream)

Tracking: [Lawnchair #26](https://github.com/kvnloo/lawnchair/issues/26).
Input provider: NOOP's existing experimental Oura BLE source, **not** a
second Bluetooth stack in Lawnchair. Linux desktop donor:
[kvnloo/.files #25](https://github.com/kvnloo/.files/pull/25).

## What this draft does

- Adds standalone `OuraGestureAdmission`, a deterministic policy for an
  explicitly armed, authenticated and **already active** z0live microphone
  session. Only fresh, monotonically sequenced double taps can authorize a
  future toggle; triple/quad and replay/future timestamps reject. It does
  not open a receiver or change production routing.
- Adds 14 standalone admission regressions:
  `bash tools/zer0/test-oura-gesture-admission.sh`.
- Does not modify Hermes-owned [PR #14](https://github.com/kvnloo/lawnchair/pull/14)
  or [integration owner map #15](https://github.com/kvnloo/lawnchair/issues/15).

## Actual handoff today

NOOP's new `OuraZ0LiveHandoff.visibleControlsIntent()` can construct an
explicit intent to the experimental Android package `app.lawnchair.play.debug`
and `z0live://voice`. The existing exported Lawnchair LinkActivity accepts
that **exact** URL and opens visible controls, with no microphone auto-start.
This is appropriate for a user-initiated foreground action only. A BLE
notification by itself is not a user-visible Activity activation.

## Required before gesture-controlled recording

1. NOOP must independently decode real 0x33 accelerometer samples from a
   bounded, opt-in 0x06 stream; always send realtime-OFF on teardown. Tune
   against true tap/no-tap device recordings; preserve Oura app pairing.
2. Establish a real per-install authenticated NOOP↔Lawnchair transport with
   explicit user arming, 1.5 s freshness, monotonic sequence and
   replay/expiry/revocation semantics. A Boolean supplied in an exported
   Intent, a package name or a deeplink alone **is not authentication**.
3. Wire this admission policy only into an existing user-started voice
   session. First-session creation must remain in a resumed permission-granted
   Activity; don't use an exported deeplink/broadcast to secretly start mic.
4. If NOOP is backgrounded and Android blocks launching controls, show a
   visible user-action notification and leave the microphone stopped.
5. Verify on real Oura Gen 3 + Galaxy S25 Ultra / One UI: safe start/stop,
   spoken audio, false activations, battery, loss/disconnect and rotation.

## Attribution

Protocol facts: [NOOP's clean-room Oura specification](https://github.com/ryanbr/noop/blob/main/docs/OURA_PROTOCOL.md)
and [Th0rgal/open_oura](https://github.com/Th0rgal/open_oura).
No GPL/unlicensed reverse-engineering code is imported. All gestures and
backend transport are opt-in and offline-by-default. Device tests **NOT RUN**.
