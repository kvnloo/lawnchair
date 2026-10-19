# Assistant + Spaces accessibility qualification (source-backed)

Part of [Lawnchair #20](https://github.com/kvnloo/lawnchair/issues/20). Current status: **all 18 cases NOT_RUN**. This inventory is grounded in sources read at `feat/z0live-realtime` (active Hermes voice rewrite) and `integration/chatgpt-lawnchair` (Spaces). Source references are **observations of code**, while `expected_observation` describes desired accessible behavior. No Android screen reader or device result has been recorded.

## Validator and negative controls

```sh
python3 tools/zer0/check-a11y-cases.py
# Optional: python3 tools/zer0/check-a11y-cases.py /path/to/candidate.json
```

Hermes must run it against exact SHA. Validate the checked-in NOT_RUN catalog; copy the JSON to a temporary path, remove a mandatory evidence field and verify nonzero exit; restore it, duplicate a case ID and verify nonzero exit. Never commit the invalid control or treat validator success as phone proof. **Tests: NOT RUN — ChatGPT cannot execute code.**

Catalog requires stable unique IDs, code/document source locator, preconditions, current source observation, desired observation, evidence class, rollback, result and evidence fields. `NOT_RUN` must have `observed_state=NOT_OBSERVED` and null `evidence_ref`. A future PASS/FAIL requires a genuine observed state and local redacted evidence reference. BLOCKED records must explain blockage rather than fabricate tests.

## Code inventory and highest-priority repair questions

- `Z0LiveActivity.SessionSheet`: user-visible Start; active Mute/Unmute and End; dynamic icon/text. Status and captions already declare polite live-region semantics. **Potential repair:** error/detail Text lacks an explicit announcement semantic; qualify before any Hermes-owned change.
- `Z0LiveActivity.onRequestPermissionsResult`: microphone denial stops intended start and shows a message. Check that TalkBack announces it, that focus lands sensibly and that no microphone remains live.
- `Z0LiveActivity.scanPairing/reviewPairing`: Google QR scanner with cancellation/error path, masked pairing input, explicit origin review. Screen reader must never speak the secret token; test missing scanner module and paste fallback.
- `Z0LivePreferences`: Compose ClickablePreference, SwitchPreference and consent dialogs. Verify keyboard/Switch Access focus and that navigation opt-in is **not** accessibility permission itself.
- `HyprSpacesUi.SpacesPage`: current named space is indicated by subtitle, Scratch is explicit. Pin rows use an `Unpin` TextButton that may not identify which app to a screen reader: qualify distinct per-app actions before repair.
- `HyprSpacesActivity.loadPersonalApps`: personal-profile launcher list with hidden-app filter. Only device evidence can prove no identity leakage after uninstall/hide/work-profile change.
- `ASSISTANTS.md`: documented Gemini/Android/ChatGPT handoff is a launch, not transcript forwarding. Avoid misleading spoken success for unavailable external assistants.

## Execution protocol

Use an exact known signed APK, current One UI build, explicit microphone/assistant/voice/phone-role consent, One UI Home fallback, and a disposable pairing token. Keep private audio, transcripts, screenshots and package lists local.

1. Baseline before enabling experimental entry points: TalkBack navigation, focus order, app drawer, search, widgets, folders and hotseat.
2. Voice: test inactive/active/muted/error/denied permission/pairing/QR unavailable/revocation/End with TalkBack; classify actual captured state vs purely visible label.
3. Spaces: current-space announcement, Scratch, pins/unpin identity, hidden/work-profile availability and focus after rename/delete.
4. Repeat at **200% font**, narrow portrait, landscape, Switch Access/hardware keyboard, and reduced animation. Check 48dp target adequacy and whether captions/errors are scrollable without hiding End.
5. For each result update the case with observed behavior, evidence reference (redacted local test ID), test SHA/build and rollback result. Do not mark PASS based on source inspection or Android build alone.

No assistant/UI rewrite, resource change, QSB/manifest change, screenshot capture or device command is part of this PR. Hermes owns repairs and exact-APK qualification.
