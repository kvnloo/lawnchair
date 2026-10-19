# zer0 package isolation and generic upstream seam audit

Part of [Lawnchair #23](https://github.com/kvnloo/lawnchair/issues/23). **Inventory and proposals only**; no source moves, manifest edits, upstream PRs or tests were executed. Source branch \`integration/chatgpt-lawnchair\`. Promotability is controlled by existing Lawnchair issue #10, not this document.

## Deterministic read-only inventory

\`\`\`sh
python3 tools/zer0/audit-zer0-boundaries.py > /tmp/zer0-boundaries.json
python3 tools/zer0/test-audit-zer0-boundaries.py
\`\`\`

**NOT RUN — ChatGPT cannot execute code.** The audit traverses tracked or local Java/Kotlin files under \`lawnchair/src/app/lawnchair/zer0/\`, reads import statements and resource references, records external launcher entry points, categorizes pure-boundary candidates and fails if those import \`android\`, \`androidx\`, \`com.android.launcher3\`, \`com.google.android\`, Java networking, OkHttp, Retrofit, Ktor or WebRTC, or reference \`R.string/drawable/layout/xml\`. Generated directories are skipped. This is deliberately **not** a proof of transitive dependencies, Gradle test pass or source extraction.

## Scope and authorization map

| Candidate source (actual repository path) | Boundary and external dependency | Evidence still required | Upstream posture |
| --- | --- | --- | --- |
| \`lawnchair/src/app/lawnchair/zer0/spaces/SpaceModel.java\` | Named context state, transient Scratch, prefs strings; no intended Android calls | JVM tests plus real backup/restore and deleted-name migration | Downstream until platform-neutral use case agreed |
| \`lawnchair/src/app/lawnchair/zer0/spaces/SpacePins.java\`, \`SpacePageBinding.java\`, \`SpaceAppSearch.java\` | Pure component serialization/page request/search admission; production \`HyprSpacesActivity.java\` resolves Android LauncherApps/visibility/real pages | Pure matrix + real hidden/work-profile device gate | **Potential** generic search/pin preflight test extraction only, not feature bundle |
| \`lawnchair/src/app/lawnchair/zer0/voice/VoiceCommand.java\`, \`PhoneActionGate.java\`, \`JevFastDecisionPolicy.java\` | Deterministic typed command/permission/intent preflight; actual Android executor remains separate | Kotlin/Android executor role/permission/capture proof, fast-model evidence | No model-brand/router promotion without maintainer demand |
| \`lawnchair/src/app/lawnchair/zer0/voice/z0live/Z0LiveTurnState.java\`, \`Z0LiveAudioState.java\` | Realtime tool continuation and audio state flags only; production \`Z0LiveService.java\` owns WebRTC/audio focus | Host JVM tests, device echo/mute/teardown races | Branded provider implementation downstream; pure tests reusable by consent |
| \`lawnchair/src/app/lawnchair/zer0/spaces/HyprSpacesActivity.java\`, \`HyprSpacesUi.kt\` | Android UI, LauncherApps, hidden-app preference policy, layouts, lifecycle, window intents | Full APK/One UI evidence; ability to restore launcher baseline | Not a generic upstream patch without review |
| \`lawnchair/src/app/lawnchair/zer0/voice/z0live/Z0LiveActivity.kt\`, \`Z0LivePhoneTools.kt\`, \`Z0LiveService.java\` | Active Hermes PR #14 UI, Android mic/accessibility/device/network/proprietary Realtime bridge | Hermes exact-source build and native device/audio/consent receipts | **Hermes-owned; skip** |

## External seams (non-zer0 paths; read-only)

- \`lawnchair/src/app/lawnchair/qsb/LawnQsbLayout.kt\`: QSB/assistant voice entry. AOSP/Launcher3/Lawnchair ownership and existing theme/customization must remain intact.
- \`lawnchair/AndroidManifest.xml\`: launcher activity, intent handlers, assistant service and foreground permission declarations (actively changing in Hermes PR #14); cannot relocate silently.
- \`lawnchair/src/app/lawnchair/ui/preferences/destinations/ExperimentalFeaturesPreferences.kt\`: feature entry and Compose preferences; owner-controlled flags, not standalone new launcher.
- \`lawnchair/res/values/z0live_strings.xml\`, \`zer0_spaces.xml\`, \`zer0_assistants.xml\`: branded resources with localization and credential-pairing semantics, not generic helper source.
- \`tools/zer0/\`: host JVM/docs/provenance regression harnesses (some previously donor-branched); these tests can serve as narrow upstream evidence but should not carry a second feature UX.

## Credit, candidate boundaries and gates

Preserve original AOSP/Launcher3/Lawnchair, Android adapter and individual human contributors’ attribution. Feature exposure via QSB, preferences, manifests and resource keys is a multi-owner integration, **not** an isolated utility package. Inspect actual upstream parent and open PRs #3/#4/#14 at promotion time for human provenance; no blanket assertion that these are extracted, accepted, upstream-ready or independently mergeable. PR #14 owns live UI/permissions/backend; do not edit.

A safe upstream candidate must be independently useful to current Lawnchair maintainers, minimal, backed by existing repository style/test conventions and satisfy Android visible/hidden/work-profile constraints. Suggested **separate future** human-reviewed proposals:

1. A small regression of generic \`SpaceAppSearch\` exact-match ambiguity handling or \`SpacePageBinding\` stale-request rejection if that behavior exists in upstream, independent of zer0 identity; confirm source equivalence first.
2. Nonbranded Android UI accessibility test for existing QSB labels/focus once device evidence exists; do not copy experimental assistant UI.
3. Narrow bug fix to original Lawnchair package if a maintainer-reproduced issue is demonstrated, carrying original author/provenance and isolated tests.

No port/release candidate is labeled ready merely because pure-source imports pass. Real compilation, Gradle/Kotlin lint, APK, Samsung/One UI, CI attribution review and maintainer acceptance: **NOT RUN**.
