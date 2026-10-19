# CI, APK and device provenance hygiene

Part of [Lawnchair #24](https://github.com/kvnloo/lawnchair/issues/24). This is an **offline consistency check, not independent attestation**. The tool cannot prove a GitHub result, package identity, signer authenticity or device behavior.

## Evidence levels

1. **CI:** exact Git SHA and reference, run ID and run URL, matching CI head SHA, successful run conclusion, at least one required job, and *every* required job successful. Optional skipped publishing jobs do not count as failure. Missing/pending/skipped/cancelled required jobs fail closed.
2. **Artifact:** in addition, match artifact source SHA and run ID, package ID, variant, signer certificate fingerprint and SHA256. The --apk argument is mandatory; compare local APK bytes and check the ZIP manifest exists. Neither package ID nor signer is independently authenticated by this script.
3. **Device:** in addition, require consent flag, exact source/APK hashes, named test target and observed result. A self-reported PASS in JSON does *not* substitute for witnessed physical phone/ADB testing. Leave real-world status NOT RUN or BLOCKED until Hermes verifies it.

## Commands (planned for Hermes; not run by GitHub connector)

    python3 tools/zer0/test-build-evidence.py
    python3 tools/zer0/check-build-evidence.py evidence.json --claim ci
    python3 tools/zer0/check-build-evidence.py evidence.json --claim artifact --apk /path/to/exact.apk
    python3 tools/zer0/check-build-evidence.py evidence.json --claim device --apk /path/to/exact.apk

Schema: [build-evidence.schema.json](build-evidence.schema.json). The checker covers cross-field constraints which JSON Schema alone does not cover. Unit tests use fabricated fixtures and a synthetic ZIP file, not an installable APK.

## Reviewed pipeline surfaces; follow-up proposals only

- **.github/workflows/ci.yml:** the final-status shell expression with grep -q -v -E against failure/cancelled is not a reliable check that no failed/cancelled required job exists. A separate, reviewed workflow fix should assert all required jobs succeeded and deal with skipped/neutral states deliberately.
- **.github/workflows/zer0-voice-jvm.yml** and **zer0-hyprspaces.yml:** host/JVM contracts are not Android hardware or APK installation evidence.
- **.github/workflows/zer0-phone-harness.yml:** collects SHA256, APK badging and certificate details. Reconcile *exact* workflow run and commit with local APK before marking device readiness.
- **.github/workflows/z0live-apk.yml:** produces APK and metadata with an isolated signer/build/lint path. A latest release tag or prior successful SHA is not proof for a newer moving PR head.
- Suggested next separate task: independent Android SDK apksigner/aapt verification and immutable SHA-stamped links, no release trigger changes and no additional credentials.

No production or workflow changes here. Preserve upstream Lawnchair/AOSP and pipeline contributors' attribution; all owner hardware consent is separate.
