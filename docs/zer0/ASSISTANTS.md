# Android voice assistant handoff (experimental)

**Status:** fork-only native assistant handoffs plus one-shot Android on-device speech recognition. No default assistant role claimed, no ChatGPT API authentication, no Jev Android runtime, no continuous voice session, and no arbitrary cross-app actions. Distinguish this UI from any OS-level assistant integration.

## Test current handoff

From Lawnchair home, long-press the dock QSB and choose **Assistants (experimental)**:

1. **Open the default Android assistant** uses Android's public \`Intent.ACTION_ASSIST\`; the OS selects an available activity. On Samsung it may fail to resolve even when system assistant gestures work. A failure shows a local message, never switches the default assistant.
2. **Choose a default digital assistant in Settings** opens Android Default apps Settings (when available). The user chooses Google/Gemini or another eligible assistant. Lawnchair never alters the role or privileges.
3. **Open ChatGPT app** resolves an *installed, visible personal-profile launch activity* of exact Android package \`com.openai.chatgpt\`, rechecks the target and launches it through LauncherApps. Sign in and tap ChatGPT's Voice icon **inside the ChatGPT app**. There is no documented public third-party deep link that logs into or controls consumer ChatGPT Voice.
4. **One-shot navigation** starts the existing external Android recognizer for commands like "Open Camera" and "Switch to Build". **Request offline transcription** sets \`RecognizerIntent.EXTRA_PREFER_OFFLINE\`; Android explicitly allows recognizers to ignore the hint. The separate on-device recognition capability display does **not** mean Lawnchair uses that recognizer.

Google began retiring classic mobile Google Assistant in September 2026, with **Gemini** replacing it on most Android devices. The Google app generally holds Android's Digital assistant app role; the Gemini/Google settings choose the experience. Do not advise users to disable Google app updates or use unsupported classic Assistant workarounds.

## Dedicated one-shot offline navigation

The new **On-device voice** option is separate from the previous generic speech-recognizer *hint*. It requires Android API 31+, an installed on-device recognition service, and the user's explicit Start tap. On the first Start, Android asks for `RECORD_AUDIO` runtime permission. Lawnchair then calls the documented `SpeechRecognizer.createOnDeviceSpeechRecognizer()`; **there is no fallback to an online recognizer**. Only the final result is delivered to the existing non-exported exact-match `VoiceCommandActivity`. Partial hypotheses never execute commands.

The mic is cancelled and the recognizer destroyed when cancelled, backgrounded, after a result/error, or after 30 seconds; rotations don't re-arm it. Audio and transcripts are not stored by Lawnchair. The Android system service itself controls offline language models and recognizer implementation; availability isn't proof that the installed language pack can recognize your speech. **This is one command per tap, not low-latency continuous duplex voice or a local LLM.**

Device test: first verify with Wi-Fi/mobile data enabled, then repeat with both network connections disconnected after installing the local language pack. Check "Open Camera" and "Switch to Build", permission denial, quick background/return, cancel, and missing model/service. Use Settings to revoke the microphone permission afterwards if desired. Neither APK build CI nor JVM parser tests prove actual microphone or offline operation.

## Backend interfaces and consent model

| Provider | Current handoff | Needed for actual cross-app action integration |
| --- | --- | --- |
| Gemini / Google | Default Android assistant \`ACTION_ASSIST\` | Supported Android AppFunctions/app-owned APIs, subject to system privileges and provider eligibility |
| ChatGPT consumer app | Open installed app; user starts Voice | No shared consumer session/token; requires a documented supported tool bridge (not available here) |
| z0live + OpenAI developer Realtime | **Not implemented** | User-controlled backend mints short-lived client secrets; mobile WebRTC/RTC transport, typed PhoneAction tools, confirmations, receipts. Never put a permanent API key in APK |
| Jev + z0intelligence | **Not implemented** | Verified compatible Android binary/runtime or remote trusted Jev service, explicit transport/auth, versioned typed action contract and timeout |
| Local STT | External recognizer with offline hint; dedicated one-shot on-device \`SpeechRecognizer\` implemented for Android 12+ | Installed language pack, foreground/mic lifecycle, native on-device tests, accuracy/latency and continuous speech transport still not qualified |

**Cross-app limitation:** Android AppFunctions (API 36+) is experimental, and general invocation is reserved for trusted system-privileged agents. A non-privileged launcher cannot gain unrestricted control over all applications by selecting Google/Gemini/ChatGPT. Accessibility is not a blanket agent-computer-use permission: each user-directed automation must be narrowly scoped, disclosed and consented to and follow distribution/policy rules. Never initiate autonomous sensitive actions from voice/model text.

## Qualification

Verify on Galaxy S25 Ultra: configured assistant handoff/no-handler case; ChatGPT installed/not installed, hidden app and multiple launch components; keyboard/TalkBack; preference-hint recognition and dedicated on-device recognition with network disconnected; mic denial, cancel, timeout, background, missing language pack, and no inadvertent default-role change. One UI Home must remain recoverable. Note that no phone test has been completed yet.

Sources: [Android ACTION_ASSIST](https://developer.android.com/reference/android/content/Intent#ACTION_ASSIST), [Settings ACTION_MANAGE_DEFAULT_APPS_SETTINGS](https://developer.android.com/reference/android/provider/Settings#ACTION_MANAGE_DEFAULT_APPS_SETTINGS), [Android speech recognition offline hint](https://developer.android.com/reference/android/speech/RecognizerIntent#EXTRA_PREFER_OFFLINE), [OpenAI developer Realtime](https://developers.openai.com/api/docs/guides/realtime), [Android AppFunctions](https://developer.android.com/ai/appfunctions).
