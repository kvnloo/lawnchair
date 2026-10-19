# Voice and Spaces string/localization readiness

Part of [Lawnchair #21](https://github.com/kvnloo/lawnchair/issues/21). This is a **read-only source inventory** of English strings in \`lawnchair/res/values/{z0live_strings,strings_zer0,zer0_assistants,zer0_spaces}.xml\` as of \`integration/chatgpt-lawnchair\`. It proposes translator context; no resources, locale files, or Hermes-owned UI have been edited. No guessed translated text or real pairing hostnames/tokens are included.

## Deterministic qualification gates

\`\`\`sh
python3 tools/zer0/check-zer0-string-catalog.py
python3 tools/zer0/test-zer0-string-catalog.py
\`\`\`

**Tests NOT RUN — ChatGPT cannot execute code.** Hermes runs at exact SHA and records source revision. The validator compares all actual XML keys, exact English source text, \`translatable\` flags, and ordered typed format placeholders against \`string-catalog.json\`. It rejects missing/stale/duplicate entries, malformed paths, unsafe bidi/control characters and incorrect placeholders; fixture negative controls test positional \`%1$s\`/\`%2$d\`, apostrophes, Unicode, product names and known contract mismatches.

## Translation readiness rules

- \`z0live_strings.xml\` presently labels experimental English strings with \`translatable="false"\`. Catalog records *actual status* separately from **proposed** translatability. Do not flip active resources until Hermes approves source/resource work and versioned translation freeze.
- Preserve Android positional format tokens (\`%1$s\`, \`%1$d\`, \`%2$b\` etc) exactly, including index/type, and validate pseudo-localized percent escaping. Apostrophes/quotes in Android resources can require Android-specific escaping even when well-formed XML; use the existing Android resource tooling as final authority.
- Brand/product names such as \`z0live\`, \`Ripple\` and named Gemini label may intentionally remain untranslated; functional commands, consent, warning, connection/error states and accessibility labels should receive translator context first.
- Translator notes must distinguish **queued/requested** from **completed/verified**, local phone authority from desktop/Hermes, Scratch as ephemeral not saved, and assistant handoff requiring user speech inside the external app.
- Inspect the hardcoded-literal inventory in the catalog (advanced Spaces bullet, delete/unpin \`×\` and dynamic voice status/error). These are recorded source observations, not proposed edits. Any production-string migration belongs to Hermes after source conflicts are settled.
- Strings containing approved placeholders for backend origins or app labels describe **display-time values**, not embedded real account data. Mask pairing tokens in all screen-reader/QA logs and screenshots.

## Integration sequence (NOT RUN)

1. Hermes reconciles the current moving z0live UI/resource producer and exact branch revision; refresh the key catalog after any source change.
2. Change user-facing labels to be translatable (except approved identifiers), with context comments where the existing repo's Android-l10n conventions permit. Preserve default-English resources and original Lawnchair/AOSP attribution.
3. Run Android resource/aapt checks, existing translation lint, and pseudo-locale Android builds without modifying shared workflow gates.
4. **Device qualification, not unit evidence:** pseudo-locale expansion at 200% font in portrait/narrow/landscape, RTL layout/focus/reading order, TalkBack labels with variables/values, 48dp touch targets, Settings dialogs, Start/Mute/End, pairing QR failure, denied microphone, network error and Space pins.
5. Document actual result, package/build signature, device OS, observed text clipping/RTL ordering and rollback plan. All such cases **NOT RUN** until Hermes/owner's device test.

No existing translations or workflows are claimed to pass. Documentation and tests do not access hardware or private credentials.
