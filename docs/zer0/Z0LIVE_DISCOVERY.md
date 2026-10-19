# z0live discovery receipts

## Existing Lawnchair work reused

This branch starts at `experiment/hyprspaces-phone-harness` (`ba091219e2d7c4cac8f173754d0c234f9ff866f0`), preserving earlier voice/HyprSpaces work through ancestry, not copying another launcher.

- [PR #1](https://github.com/kvnloo/lawnchair/pull/1): bounded, one-shot `RecognizerIntent` → exact app-label matching, no continuous microphone.
- [PR #8](https://github.com/kvnloo/lawnchair/pull/8): integrated voice/search preview and hidden-app checks.
- [PR #12](https://github.com/kvnloo/lawnchair/pull/12): existing QSB Gemini handoff and local speech fast lane, typed phone-action gate.
- [Issue #13](https://github.com/kvnloo/lawnchair/issues/13): owner’s intended OpenAI Realtime → Hermes-over-Tailscale provider. Explicitly developer Realtime API, not consumer ChatGPT subscription voice.
- The fork’s default `16-dev` was identical to its upstream ancestor (0 commits ahead, 12 behind current upstream) at discovery. No persistent Realtime implementation was present in these inspected branches.

## What Ripple actually is

- Repository: [kvnloo/ripple](https://github.com/kvnloo/ripple).
- `main` has documentation only. README uses its former working name `@kvnloo/aodl-ui` / `IntentSurface`.
- [Closed, unmerged PR #1](https://github.com/kvnloo/ripple/pull/1) and branch `cursor/intent-surface-e30f` contain the implementation and correct product name: `import { Ripple } from "@kvnloo/ripple"`.
- It is a React/React Native component, with S-Pen/pointer input, that Dash mounts as an ephemeral intent surface. Not a Hermes service, not a launcher and not an established hosted webpage.
- [Dash](https://github.com/kvnloo/dash) is an Expo phone app backed by a Bun WebSocket bridge over Tailscale (default bridge port 4747).
- No deployed Ripple HTTPS URL was verified. Lawnchair’s configurable HTTPS panel is deliberately a generic host for a future/owner-configured web build. It does **not** claim to import or ship the Ripple React Native component.
- Android home-screen widgets use RemoteViews, which do not support embedding arbitrary WebViews. The widget opens the transient panel; it is not an interactive webpage rendered inside the widget.

## Tailscale limitation and inspected integration

Android does not let Lawnchair independently enable another app’s VPN or bypass VPN consent. This implementation checks the actual HTTPS endpoint and opens installed `com.tailscale.ipn` if needed, then retries; a stopped server, bad certificate or bad pairing is not proof the VPN is off.

The current official [tailscale-android manifest](https://github.com/tailscale/tailscale-android/blob/main/android/src/main/AndroidManifest.xml) does expose `com.tailscale.ipn.CONNECT_VPN` and `DISCONNECT_VPN` broadcasts. Its [IPNReceiver](https://github.com/tailscale/tailscale-android/blob/main/android/src/main/java/com/tailscale/ipn/IPNReceiver.java) says it allows external apps to start the VPN, through WorkManager. This is a Tailscale-owned integration, not direct VPN control or a guaranteed supported URL scheme; installed versions, initial VPN consent and Samsung force-stop/deep-sleep still matter. The build uses conservative app handoff instead of silently depending on an unverified installed-version broadcast.

## History search limits

Read-only queries examined small Lawnchair/Ripple user-message excerpts from Hermes profile state databases. Bounded recent Claude Code JSONL and Codex session scans were also attempted; returned snippets mostly concerned unrelated evaluation/research tasks, not a new authoritative deployment URL. `ctx search` was unavailable because its daemon/supervisor ownership check failed; no ctx configuration was changed. The resident repository index returned warming/no hits, so repository discovery used bounded filesystem checks and the canonical GitHub APIs. No claim of an exhaustive history audit is made.

## Validation boundary

JDK 21, Android SDK 37 / build-tools 37 and adb exist on the build host. No ADB device was attached during discovery. A green APK build or JVM mock-server test does not prove Samsung full-duplex audio, assistant gestures, battery behavior, or an actual OpenAI-to-Hermes conversation on the phone.
