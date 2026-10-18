## Native Gemini-first UX (October 18, 2026)

- Tap **anywhere on the dock bar** or its microphone to launch Android's selected assistant directly. When the Google app is the assistant and Gemini is selected, that opens Gemini. The bar uses Lawnchair's existing QSB component with a compact **Ask Gemini** label.
- Tap **left search/provider icon** for the original Lawnchair search. No loss of app drawer, widgets, native search provider, home gestures or search preferences. Long press remains for Customize/Spaces only; no assistant picker.
- An optional **lightning icon** uses the existing foreground Android *on-device SpeechRecognizer* solely for local fast actions (open app, switch space, search app). When unavailable, it opens Gemini. It does not invoke the legacy cloud speech prompt or TTS. A lightweight deterministic typed action classifier runs locally; a real Jev decision model/latency bridge is not yet connected.
- Android's public `ACTION_ASSIST` first; if no handler on a One UI build, launch the uniquely visible, installed Gemini app via `LauncherApps`. This last resort is the ordinary Gemini app, **not an undocumented Gemini Live or overlay deep link**. Fail closed on hidden/ambiguous components.
- Android Settings → Apps → Default apps → Digital assistant → **Google**; in Google's Gemini settings select **Gemini**. Lawnchair must not change the assistant role. The user configures this only once, not per command.
- **Gemini audio/transcripts belong to Gemini.** A separate consumer Gemini Live invocation is user-driven inside Google's app; there is no supported way to intercept its audio/voice output or inject Jev execution tools into that session.
- Do not advertise the model or latency as implemented until TypeSafe Jev (or another model) returns a validated typed proposal over a trusted transport with benchmarked p50/p95 and tool-permission gates. Launcher-owned deterministic actions remain available in the fast lane.

Google's Android support: https://support.google.com/gemini/answer/14554984 ; https://support.google.com/gemini/answer/15274899 ; Android public assist Intent: https://developer.android.com/reference/android/content/Intent#ACTION_ASSIST .

---

# Earlier smart voice implementation (now Gemini-first)

**Status (2026-10-18):** One microphone entry and preference, backed by the existing Lawnchair Compose QSB, popup and settings components. This is a **one-shot speech router**, NOT a native GPT Realtime session or ChatGPT account sign-in. Android APK and Galaxy S25 Ultra execution must be checked on device.

## User experience

- Tap the normal dock microphone, or long-press dock search -> **Smart voice**.
- On devices reporting a dedicated Android offline speech recognizer (API 31+), **one QSB mic tap** opens a compact Lawnchair-themed listening view and directly requests microphone permission/listens. It uses an explicit foreground-only `SpeechRecognizer.createOnDeviceSpeechRecognizer()`, 30s ceiling and one-final-result guard. No cloud fallback from that path. Where unavailable, the original system speech-recognizer intent is still used (which may be online).
- The speech recognizer returns one finalized utterance. Opening a unique, visible personal-profile app, switching a configured HyprSpace and safely filtering launcher app search run locally (revalidating target visibility/permission).
- Other commands: requests matching a bounded device-action vocabulary open the **already configured Android assistant**; conversational questions open the installed **ChatGPT app** if uniquely visible, otherwise Android assistant. **A third-party app is just launched**. Android exposes no documented API to forward this captured utterance to ChatGPT's consumer voice session: user must speak again there. Never suggest that Android's assistant role has changed.
- Preferences -> Dock search -> **Smart voice** uses Lawnchair's real `SwitchPreference`; switching it off restores the chosen search provider's normal microphone target. No manually choosing ChatGPT vs Gemini for each request.
- No fabricated UI, standalone menu of buttons, new model session, credentials, permission grants, or unconditional fallback shell commands.
- If an app is absent/hidden/ambiguous, fail closed. No sensitive action automatically dispatched.
- Existing Tern/Hermes/OMP Wi-Fi ADB, `z0live` browser entry, and opt-in HyprSpaces probes remain separate and usable.

## Technical

- `PreferenceManager2.zer0SmartVoiceRouting`: opt-in switch controls the existing mic icon (on by default **only in this experimental fork**).
- `LawnQsbLayout` uses its existing `LawnQsbUi` and `OptionsPopupView`.
- `VoiceCommandActivity`: bounded one-shot recognizer, local PhoneAction gate, unknown command -> `SmartVoiceRouter` -> `AssistantHandoff`. No exported Activity, mic permission, or speculative system-level privileges.
- `AssistantHubActivity` raw `LinearLayout` UI removed, along with its launcher menu and manifest entry.
- Pure JVM routing tests: `bash tools/zer0/test-voice-commands.sh`. Android CI: `./gradlew spotlessCheck assembleLawnWithQuickstepPlayDebug`. Device test still required.

## Test cases

1. With Smart voice enabled, speak `open Camera`, `switch to Build`, `explain transformers`, `set a 5-minute timer`. Expect local/open-space, local/open-space, ChatGPT app handoff, Android assistant handoff respectively. **For external handoffs, repeat the request in the opened app.**
2. Disable Smart voice under Dock search settings: microphone returns to selected provider; long-press Smart voice still supports local commands but never routes unknown requests to external apps.
3. Remove/disable ChatGPT: question chooses existing Android assistant if available; do not open unrelated launchable apps. Test hidden applications and multiple activities.
4. Cancel recognizer: zero actions. Revoke recognizer: safe no-handler result. Test TalkBack and screen rotation.
5. Keep One UI Home installed as fallback. Compare signature and package ID before attempting to replace an APK signed on another CI worker.

## Seamless future endpoint

For **genuinely continuous one-microphone interaction** and verified actions without repeating requests, the launcher needs a native foreground voice session and its own typed action protocol. Integrate `z0live` + `PhoneAction` once a stable authenticated transport and runtime are implemented. Providers should be transport modules (Android STT, local models, OpenAI developer Realtime API, etc.), not separate chooser screens. ChatGPT consumer Voice cannot be commandeered; OpenAI's [Sign in with ChatGPT for OSS](https://developers.openai.com/siwc/token-sharing-open-source) provides eligible **Responses API** access, not an asserted GPT-Live/Realtime streaming entitlement. No subscription token sharing or reverse-engineered ChatGPT app controls.

Credit: original AOSP/Launcher3 and Lawnchair QSB, Compose preferences and plugin authors remain intact. Research/refs: [Lawnchair patterns](https://github.com/LawnchairLauncher/docs/blob/main/developers/architecture/lawnchair-design-patterns.md), [Android assistant service](https://developer.android.com/reference/android/service/voice/VoiceInteractionService), [ChatGPT OSS plan use](https://developers.openai.com/siwc/token-sharing-open-source).

## Base-branch reconciliation

Merged the already-developed [on-device recognition and lifecycle tests](https://github.com/kvnloo/lawnchair/tree/experiment/zer0-hyprspaces) from the HyprSpaces base with a two-parent commit. Preserved author history and Android permission constraints, replaced the raw LinearLayout listener UI with native Compose and routed its one-shot final transcript into the existing typed action/router. This is not ChatGPT Realtime or a BYOS login. Device testing is mandatory before claiming offline recognition performance or installability.
