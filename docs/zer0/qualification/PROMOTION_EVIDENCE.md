# Search fixes: promotion evidence matrix

Part of [task #25](https://github.com/kvnloo/lawnchair/issues/25), stored under the single [Lawnchair upstream queue #10](https://github.com/kvnloo/lawnchair/issues/10). No second issue or upstream PR was created.

## Source and credit

- Existing [fork PR #3](https://github.com/kvnloo/lawnchair/pull/3): test file tests/multivalentTests/src/app/lawnchair/search/algorithms/SearchUtilsTest.kt; fork head 14df6933f3821ede62119bf6f63f304e2209adcd. Original Lawnchair/AOSP authors, mvanhorn and validcube retain their credit.
- Existing [fork PR #4](https://github.com/kvnloo/lawnchair/pull/4): one-line guard in lawnchair/src/app/lawnchair/allapps/AllAppsSearchInput.kt; fork head c46b38e0cb7ccfabf6f1a10c15bf6d8649a8bf8f. Flightkick reported the original stale behavior and MrSluffy delivered lifecycle repair #7347; this is an independent follow-up.
- Fixed source SHAs above are a historical fork readback, not promises of current origin or Android qualification. Recheck exact heads before every promotion decision.

## Cases and missing gates

The machine-readable [search-acceptance-cases.json](search-acceptance-cases.json) maps ten stable IDs, the original head/source, expected behavior, test targets and evidence class. Five hidden-app policy cases refer to actual AndroidJUnit4 SearchUtilsTest methods; their previously reported isolated Kotlin stub smoke is **LIMITED**, not real Android instrumentation. The other five stale-result cases are on-device/manual candidate targets, not source-integrated tests.

- Hidden modes: NEVER exact-name exclusion; IF_NAME_TYPED partial exclusion and full reveal; ALWAYS zero-state eligibility; unknown preference fail-closed.
- Stale callbacks: delayed A to B, A to clear, A to clear to A, keyboard zero-state/clear, and home/app drawer reopen.
- A to clear to A with the identical query is a **known generation gap**, not a passed regression. The string-equality guard does not disambiguate an earlier generation from a new request using the same query.
- Search test/source targets require exact-head Android instrumentation wiring and Gradle/Soong native test execution; also check spotlessCheck and complete supported Android APK build.
- Reproduce a delayed provider on emulator or real device; capture keyboard, zero-state, latest-input and hidden-app modes with redacted screenshot/video proof. Verify work-profile/hidden-app boundaries and TalkBack where applicable.
- Compare current Lawnchair upstream code and existing PR owners before posting; generic independent improvements only. Do not promote downstream z0live, voice, Jev, HyprSpaces, OEM windowing or branded zer0 features upstream as generic fixes.

## Fail-closed offline check (NOT RUN)

    python3 tools/zer0/check-promotion-cases.py docs/zer0/qualification/search-acceptance-cases.json
    python3 tools/zer0/test-promotion-cases.py

A case is only metadata-qualified for READY_FOR_HUMAN_PROMOTION_REVIEW with exact verified source head, native Android PASS, device PASS, maintainer/owner review PASS, and evidence links. The validator cannot independently verify those links or human review. The original PRs stay draft; upstream posting, contributor credit, owner decisions and real hardware gates remain separate.

**Current disposition: all ten cases QUALIFICATION_NEEDED. Android tests/device and current-head reviewer checks NOT RUN.**
