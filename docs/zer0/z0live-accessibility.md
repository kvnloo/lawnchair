# z0live accessibility control

The isolated `Z0LiveAccessibilityService` handles optional in-app control. Lawnchair's existing navigation service does not gain screen-reading access.

## Consent

Android Settings > Accessibility lists **z0live in-app control**. Only the owner can enable that service. The z0live Compose settings page has a separate **In-app control for this session** switch. That switch requires an active voice session and the enabled service. Its confirmation does not open Android settings automatically.

Consent stays in memory. Session end, interruption, service unbinding, or process death revokes consent and invalidates the current snapshot. Previous persistent navigation consent does not grant screen access. Opening Android Accessibility settings requires an explicit click.

## Tool contract

Tools remain declared while the service is disabled. `permission_required` includes owner enablement instructions without launching settings.

- `phone_ui_tree` returns `package`, `window_id`, an opaque `snapshot`, and at most 60 text-only controls. Traversal stops at 200 nodes and rejects larger trees. Labels are at most 120 characters and resource IDs at most 200. Editable values are omitted.
- `phone_tap`, `phone_type_text`, and `phone_scroll` require the package, window ID, snapshot, selector `by` as `text` or `id`, and exact `target`. Text entry selects the editable field by resource ID and uses `ACTION_SET_TEXT`. It replaces the field and does not submit. Scroll direction is `forward` or `backward`.
- `phone_navigation` supports `back`, `home`, `recents`, and `notifications` under the same session consent.

Actions reject duplicate matches and never choose a parent or a coordinate. Package or window changes, accessibility content-change events, changed node descriptions, and prior action dispatch invalidate the snapshot. A fresh tree is required after each action. A final active-window and refreshed-target check runs before dispatch.

A successful Android action returns `status: dispatched` and `verified: false`. This result confirms API acceptance only. It does not prove that the UI changed.

## Sensitive content

The policy blocks password fields, including numeric, web, visible-password, and password-flag input types. Password windows are rejected before labels are serialized. Editable field values are never read. Package matching blocks known financial, password-manager, authentication, messaging, dialer, permission, installer, Android settings, and Lawnchair packages. `Z0LiveAccessibilityPolicy.blockedPackage` contains the default rules.

The target policy rejects sending, calling, purchasing, confirmation, deletion, permission, and credential labels or IDs. Existing call and SMS tools still open owner-confirmed composers. Accessibility does not add shell commands, screenshots, gestures, coordinate fallback, programmatic service enablement, or automatic sending.

Package and label rules depend on the metadata apps expose. They are conservative heuristics, not a complete classifier of arbitrary app behavior. Unlabelled controls, inaccessible UI, large trees, translated sensitive labels, and manufacturer-specific accessibility behavior require further qualification. No handset verification is included in the host tests.

## Host verification

`bash tools/zer0/test-z0live-accessibility.sh` exercises the production Java policy without Android. Fixtures cover disabled access, session consent, blocked packages, package and window mismatch, exact text and resource IDs, ambiguity, unsafe targets, and password input types.

`bash tools/zer0/test-z0live-accessibility-catalog.sh` runs the compiled production catalog and disabled dispatch. It uses Android SDK class definitions and allocates an unused Context fixture without running Android constructors. It does not instantiate a service or interact with a phone. Run the variant compilation task and protocol script first to provide the compiled classes and cached JSON dependency.

The protocol, audio, and voice-command scripts remain separate regression gates. Android compilation, lint, and APK assembly validate integration. Phone acceptance remains required for actual tap, text, scroll, navigation, lifecycle, and provider behavior.
