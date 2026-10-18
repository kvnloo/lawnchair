# Install the zer0 phone-harness APK safely

**Target:** Galaxy S25 Ultra, `experiment/hyprspaces-phone-harness`. Do not replace your working launcher until APK and package/signature are verified. This is a sideloaded test build, not an official Lawnchair release. Preserve One UI Home as an escape hatch.

## Pick the correct variant

- `assembleLawnWithQuickstepPlayDebug`: `app.lawnchair.play.debug` (best **side-by-side** test when the installed fork is GithubDebug).
- `assembleLawnWithQuickstepGithubDebug`: `app.lawnchair.debug`.
- `assembleLawnWithQuickstepNightlyRelease`: `app.lawnchair.nightly` (not the normal Debug package; check signing and package details).

Download the **artifact ZIP from a passing exact-head CI run**, extract the single APK, and verify `sha256sum`. GitHub Actions' reported `success` validates build jobs, **not** Samsung installation. `PlayDebug` and `GithubDebug` build flavors are distinct Android app package IDs, but *two builds of the same package* must also have the **same signing certificate** to update.

## Before installation

1. Preserve the installed, working launcher and choose One UI Home as your rollback option.
2. Confirm that Tern's existing Wi-Fi ADB authorization still works: `adb devices` should show one device in `device` state. Do **not** re-pair or change assistant/default HOME automatically.
3. Get the APK package/signature if Android build tools are installed:

   ```bash
   aapt dump badging ./Lawnchair.apk | grep '^package:'
   apksigner verify --print-certs ./Lawnchair.apk
   sha256sum ./Lawnchair.apk
   ```

4. Install without clearing data:

   ```bash
   adb install -r ./Lawnchair.apk
   ```

5. If installation fails, share the exact `INSTALL_FAILED_...` code (e.g. `INSTALL_FAILED_UPDATE_INCOMPATIBLE`, `INSTALL_FAILED_VERSION_DOWNGRADE`, `INSTALL_FAILED_OLDER_SDK`); never assume the failure is from Kotlin code or immediately uninstall the working app.

An **UPDATE_INCOMPATIBLE** result means the installed app has the same Android application ID but a different signing identity. The fork's original GitHub CI uses locally generated debug signing unless `keystore.properties` was populated. This may vary across Actions runners and branches; it is **not** an upgrade-safe release pipeline by default. Side-by-side flavor IDs avoid collisions between different IDs only, not repeated updates to the same ID.

## Persistent signing (requires repository-owner configuration)

Create a **private** Android keystore on a trusted personal computer, retain a separate offline backup, and set secret values in the fork's GitHub Actions Secrets. Lawnchair's Gradle `build.gradle` reads `keystore.properties` (`keyAlias`, `keyPassword`, `storeFile`, `storePassword`) and applies it to all build types; unconfigured builds fall back to the Gradle debug signing config.

Never commit a `.jks`, private key, key password, exported ChatGPT consumer cookies, or Tern/Hermes tokens to a public repository. A persistent signing key must remain private for future update compatibility. Re-signing an already-installed APK under a new key cannot make it update-compatible with a previous certificate.

## One-device smoke

- Install; leave One UI Home available.
- In the dock, long-press QSB → Spaces. Confirm native Lawnchair theme and existing home icons/widgets/app drawer.
- Tap QSB mic. If Android reports an on-device SpeechRecognizer, grant microphone permission and test one utterance, `open Camera`, `switch to Build`, `search for Maps`. It should stop if backgrounded/cancelled.
- Questions may open ChatGPT; device commands may open Android's configured assistant. **You must speak again inside external apps**; this is a handoff, not ChatGPT Realtime Voice.
- Try with offline language pack installed and no network; if on-device recognition is unavailable, the existing Android recognizer path may use a network service.
- Test app drawer, widgets, return Home, hidden apps, TalkBack and source/branch SHA. Do not grant unrequested permissions or infer real split-screen from an Intent return.

Source: `docs/zer0/ASSISTANTS.md`, `docs/zer0/HYPRSPACES.md`, `tools/zer0/tern-voice-check.sh`.
