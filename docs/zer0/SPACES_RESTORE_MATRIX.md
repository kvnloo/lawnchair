# Spaces restoration, Scratch and pin privacy qualification

Part of [Lawnchair #19](https://github.com/kvnloo/lawnchair/issues/19). Pure-JVM model checks are **not** Android backup/restore, hidden-app policy, work-profile isolation or compositor proof. The [HyprSpaces design](HYPRSPACES.md) makes named spaces labels, not independent task stacks.

## Host-JVM gate

```sh
bash tools/zer0/test-spaces-restore-matrix.sh
```

Also run existing `SpaceModelTest`, `SpacePinsTest`, `SpacePageBindingTest` and repository gates. **Tests: NOT RUN — ChatGPT cannot execute code.** Hermes records exact SHA and actual pass/fail evidence.

### Pure contract expectations

- `SpaceModel.restore/encodeNames`: stable case-folded ordered set, Unicode NFKC normalization, invalid/reserved/corrupt records skipped, duplicates collapsed, 12-name cap and first-name fallback.
- `SpaceModel.activate("Scratch")`: transient active only; `encodeNames` and `savedActiveName` retain durable named state; `restore` cannot make Scratch active.
- `SpaceModel.rename/remove`: selected named space migrates/falls back, removed identity no longer serialized; immutable previous model remains unchanged. **External preference-key migration for per-space pins/page bindings is not modeled**, so cannot be proven by these unit tests.
- `SpacePins.decode/encode/add/remove`: malformed records filtered on decode, duplicates and ninth pin rejected, unchanged order and copies. **Syntactically valid work/hidden/uninstalled component strings are not visibility checks**.
- `SpacePageBinding.canNavigate`: positive, fresh screen IDs allowed (including ID zero); `NO_SCREEN`, future and stale requests fail. A valid-looking **deleted screen ID remains admitted by pure preflight**; real Launcher workspace existence must be checked by the host.

## Android evidence ledger (all rows NOT RUN)

Use a disposable test profile/known apps only. Capture fields *test APK SHA, device/One UI build, user consent, precondition, action, observed workspace state, expected result, permission/profile and rollback result*. Never upload private screenshots/dumps automatically.

| Case | Exact precondition and action | Expected observation | Rollback | Status |
| --- | --- | --- | --- | --- |
| Disabled-feature baseline | Boot known-signed APK with Spaces feature disabled; record widget/folder/hotseat layout, app drawer and QSB; exit/relaunch launcher | Icons, widgets, folders, hotseat and search unchanged | Restore previous signed APK or selected HOME launcher | NOT RUN |
| Named-space restart | Create/rename/delete named spaces, assign active one; force-stop/restart | Names, active selection and normal-space pins/page links restored; deleted key material removed | Clear experimental settings or restore disposable launcher backup | NOT RUN |
| Scratch lifecycle | Activate Scratch; add pin/choose app pair; background, restart and restore backup | Scratch state/pins/pair **not** persisted; previously selected named space intact | Switch to named space, clear Scratch and restore backup | NOT RUN |
| Pin hidden/uninstalled app | Pin known personal-profile app, then hide/uninstall it; relaunch/switch space | No label/launch for invisible or absent app; pin never bypasses policy | Unhide/reinstall test app or remove pin | NOT RUN |
| Cross-profile pin | Configure work-profile test app with explicit consent; attempt pin/launch from personal Spaces | No cross-profile launch or identifying label without an authorized OS path | Remove pin, restore profile isolation | NOT RUN |
| Rename/delete linked space | Link a known existing home screen, add pins; rename then delete | Pin key migration and stale link cleanup; no orphan shortcuts or wrong-space page hop | Unlink/reset experimental page bindings | NOT RUN |
| Unavailable/deleted page | Link space to page; delete/reorder/import layout; try navigation before/after 10s | No navigation to stale or nonexistent page; normal home contents intact | Restore saved home layout, unlink space | NOT RUN |
| Backup/import restore | Back up, alter and restore disposable home with same app version | Feature-disabled baseline preserved; no hidden profile leaks; normal-space restore defined | Import original known-good backup / restore previous APK | NOT RUN |
| PR #11 palette overlap | Integrator compares PR #11 delta against named-space/current integration UI | Separate reviewed overlap; no palette implementation copied from #11 | Keep PR #11 isolated for Hermes reconciliation | NOT RUN |

**Evidence categories stay distinct:** model assertions, APK build, real backup/restore, user-visible layout, Samsung split and hidden-profile enforcement. No real Samsung or launcher changes are claimed by this PR.
