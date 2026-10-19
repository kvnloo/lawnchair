# Samsung One UI exact-APK evidence and rollback qualification

Part of [Lawnchair #22](https://github.com/kvnloo/lawnchair/issues/22). This PR is a **qualification pack**, not a phone test or APK. All 20 [template cases](phone-receipt.template.json) start **NOT_RUN**; model, OS, APK hash, certificate, timings and consent are null. No physical S25 Ultra, Tailscale, microphone, launcher role or Samsung window behavior was exercised. Hermes/owner must independently authorize any test.

## Offline template validator

\`\`\`sh
python3 tools/zer0/check-phone-receipt.py
python3 tools/zer0/test-check-phone-receipt.py
# Or validate a private copied receipt:
python3 tools/zer0/check-phone-receipt.py /local/approved/redacted-receipt.json
\`\`\`

**Tests NOT RUN — ChatGPT cannot execute code.** The validator rejects \`PASS\` unless it has exact matching 40-hex source SHA + CI source SHA, full APK/signing SHA-256, GitHub CI run URL, artifact name, package, version, Samsung model/Android/One UI, confirmed One UI Home fallback, applicable explicit consent, actual command/observed state/evidence class/reference and recorded rollback. It accepts explained \`BLOCKED\` and unobserved \`NOT_RUN\`. It never probes adb or touches a device. Unit fixtures use clearly synthetic hashes/metadata **not actual device evidence**.

## Device preflight (owner opt-in, NOT RUN)

1. Record full **git source SHA for the candidate**, hosted GitHub Actions run URL, exact *artifact* name/source SHA and build result. A moving \`z0live-latest\` tag or yesterday's APK is **not** source identity. Cross-check CI success against the actual APK artifact and branch.
2. Save the approved artifact outside the repository in a local 0700 evidence directory. \`sha256sum APP.apk\`, \`aapt dump badging APP.apk\`, \`apksigner verify --print-certs APP.apk\` and inspect package/version/signing certificate. Compare against the already installed package signature **before** installation. Stop on incompatible signing or version; do not uninstall or \`pm clear\` as a shortcut.
3. Confirm exactly one explicitly selected authorized Samsung device, Android release/build, One UI version and current HOME role holder. Proposed **read-only** preflight: \`bash tools/zer0/samsung-spaces-check.sh preflight\` with user-chosen \`ANDROID_SERIAL\` if needed. Do **not** run it without approval; it gathers device facts, not windows or captions.
4. Confirm One UI Home remains installed and usable; document the previous HOME/assistant roles, granted permissions, saved launcher layout and explicit rollback path. Ask separately before APK install, HOME/assistant role selection, mic/audio test or screenshots/window dumps. \`tools/zer0/INSTALL_PHONE_HARNESS.md\` documents signature-safe install and One UI fallback; **never auto-run its install script**.
5. If a test *needs* screen evidence, owner opts in after confirming no credential/lock/private overlay is visible. The \`samsung-spaces-check.sh capture\` command writes **local private** activity/window metadata and never auto-uploads; review and redact locally first. No photos, private audio, logs or tokens belong in public PRs.

## Evidence cases

- **Foreground microphone:** denied, allowed, cancel, background/lock, End notification, discovering an accessible stop, no silent restart and real app status. A user-started session is not a guarantee of permanent recording.
- **Assistant role:** role accepted/refused, explicit Android selection, proper external handoff and restoring prior role; no \`adb role\` forcing or undocumented ChatGPT consumer app control.
- **Network and session:** Tailscale loss, private paired-device token revocation, bounded recovery, pending tool work, mute preservation and synthetic spoken inbox reply without TTS echo. Document actual failure as failure.
- **Launcher baseline:** QSB original search, app drawer, widgets, folders, hotseat, Recents, TalkBack and 200% font. Keep One UI Home fallback.
- **Spaces:** named/Scratch restart, hidden/uninstalled/work-profile pin isolation, stale page binding, independent split and freeform bounds observations. An Android startActivity/FLAG_ACTIVITY_LAUNCH_ADJACENT/setLaunchBounds call means **requested**, not verified dual-pane geometry.

For every case, fill \`exact_command\` (or precise tap sequence), \`observed_state\` (not expectation), \`evidence_class\`, \`evidence_ref\` (redacted local receipt ID), actual \`latency_samples_ms\` only if measured, \`rollback_result\` and \`rollback_observation\`. Record precise failure/blocked result as needed. The template itself is deliberately empty: never mark PASS based on hosted CI, source inspection, shell intent emission or unmeasured memory.

### Stop and rollback

If signature incompatible, assistant/HOME role uncertain, permission refused, two ADB targets, private lock screen, connection/certificate unexpected, or current app data at risk: **stop**. Preserve working launcher and owner credentials; do not use \`adb kill-server\`, uninstall, \`pm clear\`, force roles or overwrite an APK. After consented test, restore previous launcher/assistant and mic state, revoke synthetic token, unpin temporary apps and check original home widgets/folders.

See [z0live voice scope](../Z0LIVE.md), [install guidance](../INSTALL_PHONE_HARNESS.md), [HyprSpaces](../HYPRSPACES.md), and [private preflight script](../../../tools/zer0/samsung-spaces-check.sh). All Samsung window/audio/latency/rollback observations: **NOT RUN**.
