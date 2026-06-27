---
phase: 05
slug: track-setup-and-course-profiles
status: approved
nyquist_compliant: true
wave_0_complete: n/a   # no separate Wave 0; every new test is authored in its owning plan/task via task-level RED -> GREEN before implementation
asvs_level: L1
created: 2026-06-26
revised: 2026-06-26
---

# Phase 05 — Validation Strategy

> Per-phase validation contract for reusable course profiles, complete Sector timing, revisions, direction variants, and wrong-course handling.

> **Security:** ASVS Level L1 (offline mobile app; web auth/session/transport controls N/A — applied by analogy to local data integrity and input validation). Every HIGH-severity threat in the table below links to the plan/task and test that mitigates it. Threat Ref IDs match the `<threat_model>` blocks in the corresponding PLAN.md exactly.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| Framework | `kotlin.test` 2.4.0 through shared Kotlin Multiplatform tests |
| Config file | `shared/build.gradle.kts` |
| Quick run command | `.\gradlew.bat :shared:testAndroidHostTest --tests "*<FocusedTestClass>*"` |
| Full suite command | `.\gradlew.bat :shared:check` |
| Android build command | `.\gradlew.bat :androidApp:assembleDebug` |
| iOS gate | Run shared iOS simulator tests/Xcode build and cold-launch persistence UAT on macOS |

---

## Sampling Rate

- After every implementation task: run the new focused test class plus directly affected existing tests (task-level RED is written first, then implemented to GREEN within the same task — no task leaves a failing verify).
- After every plan wave: run `.\gradlew.bat :shared:check`.
- After UI or storage waves: also run `.\gradlew.bat :androidApp:assembleDebug`.
- Before `$gsd-verify-work 5`: require the full shared suite, Android build, deterministic replay matrix, migration UAT, and the documented macOS/iOS gate.
- Max automated feedback latency: one task commit.

---

## Per-Task Verification Map

Every test below is created and made green inside the listed plan/task (task-level TDD). There is no separate Wave 0; the wave column is the plan's execution wave. Threat Ref IDs map to the named plan's `<threat_model>`.

| Slice | Plan | Task | Wave | Requirement | Threat Ref | Test Class / Command | Status |
|-------|------|------|------|-------------|------------|----------------------|--------|
| V1 freeze + V2 contracts + migration mapping | 05-01 | T1, T2 | 1 | SC-01, SC-03 / D-12..D-16 | T-05-01, T-05-02 | `SchemaMigrationTest` | ⬜ pending |
| Side-by-side store migration (idempotent, fault-injected) | 05-02 | T1 | 2 | SC-01, SC-03 | T-05-03, T-05-04 | `StoreMigrationTest` (+ `FileSessionStoreTest`) | ⬜ pending |
| Explicit current selection, no fallback | 05-03 | T1, T2 | 3 | SC-01, SC-03 / D-01..D-04 | T-05-05, T-05-06 | `CurrentTrackSelectionTest`, `DriveMarkingControllerTest` | ⬜ pending |
| New-user empty-store create + name + select (SC-01 e2e) | 05-04 | T1 | 4 | SC-01 / D-01..D-04 | T-05-07, T-05-08, T-05-09 | `CourseProfileCreateTest` | ⬜ pending |
| Closed-path + constrained editor geometry | 05-05 | T1, T2 | 5 | SC-02 / D-05, D-07..D-10 | T-05-10, T-05-11 | `ClosedReferencePathTest`, `CourseProfileEditorTest` | ⬜ pending |
| Offline editor save → immutable revision | 05-06 | T2 | 6 | SC-02 / D-12..D-14 | T-05-12 | `CourseProfileEditorTest`, `TrackRevisionStoreTest` | ⬜ pending |
| Complete-Sector timing semantics | 05-07 | T1 | 7 | SC-02 / D-06, D-07, D-11, D-20 | T-05-13, T-05-14 | `CompleteSectorReplayTest`, `LapEngineTest` | ⬜ pending |
| V2 Sector persist/Review/export; V1 legacy preserved | 05-07 | T2 | 7 | D-06..D-11 / D-13..D-15 | T-05-15 | `ReviewSummaryTest`, `JsonExportTest` | ⬜ pending |
| Profile lifecycle: rename/archive/duplicate/history | 05-08 | T1, T2 | 7 | SC-01, SC-03 / D-12..D-16 | T-05-16, T-05-17, T-05-18 | `TrackRevisionStoreTest`, `TrackProfileReviewTest` | ⬜ pending |
| Recorded/Reverse Course Direction + turnaround integrity | 05-09 | T1, T2 | 8 | SC-03, SC-04 / D-17..D-21 | T-05-19, T-05-20 | `CourseDirectionReplayTest` | ⬜ pending |
| Ghost compatibility key contract + key-gated selection | 05-10 | T1 | 9 | SC-03, SC-04 / D-15, D-16, D-18, D-19 | T-05-21, T-05-22 | `GhostCompatibilityTest` | ⬜ pending |
| Exact-key reference persistence + provider provenance | 05-11 | T1 | 10 | SC-03, SC-04 / D-19, D-20 | T-05-23, T-05-24 | `TimingGhostIntegrationTest` | ⬜ pending |
| Course-progress matcher + suppression/recovery | 05-12 | T1, T2 | 11 | SC-04 / D-20, D-24 | T-05-25, T-05-26, T-05-27 | `CourseProgressMatcherTest`, `TimingGhostIntegrationTest` | ⬜ pending |
| Wrong-course preflight + override + vertical integration | 05-13 | T1, T2 | 12 | SC-01..SC-04 / D-22, D-23 | T-05-28, T-05-29 | `WrongCoursePreflightTest`, `CourseProfileIntegrationTest` | ⬜ pending |
| Review override evidence + Phase 5 UAT gates | 05-14 | T1, T2 | 13 | SC-01..SC-04 / D-13, D-14, D-23 | T-05-30 | `ReviewSummaryTest` (override) | ⬜ pending |

**HIGH-severity threat coverage (every HIGH threat links to a green test):**

| Threat Ref | Severity | Plan/Task | Mitigating Test |
|-----------|----------|-----------|-----------------|
| T-05-01 | HIGH | 05-01 T1 | `SchemaMigrationTest` version/corruption cases |
| T-05-02 | HIGH | 05-01 T2 | `SchemaMigrationTest` migration-correctness cases |
| T-05-03 | HIGH | 05-02 T1 | `StoreMigrationTest` unsafe-ID cases |
| T-05-04 | HIGH | 05-02 T1 | `StoreMigrationTest` fault-injection cases |
| T-05-05 | HIGH | 05-03 T1 | `CurrentTrackSelectionTest` no-fallback/path cases |
| T-05-07 | HIGH | 05-04 T1 | `CourseProfileCreateTest` unsafe-name cases |
| T-05-12 | HIGH | 05-06 T2 | `TrackRevisionStoreTest` append cases |
| T-05-16 | HIGH | 05-08 T1 | `TrackRevisionStoreTest` unsafe-ID cases |
| T-05-17 | HIGH | 05-08 T1 | `TrackRevisionStoreTest` compatibility cases |
| T-05-19 | HIGH | 05-09 T1 | `CourseDirectionReplayTest` opposite-crossing cases |
| T-05-21 | HIGH | 05-10 T1 | `GhostCompatibilityTest` mismatch cases |
| T-05-22 | HIGH | 05-10 T1 | `GhostCompatibilityTest` V1-migration cases |
| T-05-23 | HIGH | 05-11 T1 | `TimingGhostIntegrationTest` mismatch/path cases |
| T-05-24 | HIGH | 05-11 T1 | `TimingGhostIntegrationTest` provenance cases |
| T-05-27 | HIGH | 05-12 T2 | `TimingGhostIntegrationTest` continuity cases |
| T-05-28 | HIGH | 05-13 T1 | `WrongCoursePreflightTest` |
| T-05-29 | HIGH | 05-13 T2 | `CourseProfileIntegrationTest` continuity cases |

MEDIUM/LOW threats (T-05-06, 08, 09, 10, 11, 13, 14, 15, 18, 20, 25, 26, 30, and every plan's T-05-SC) are dispositioned in their plan `<threat_model>` blocks; T-05-SC is `accept` (no package install in any plan).

---

## Required Deterministic Fixtures

- [ ] Closed oval with known perimeter and equal arc-length Sector points. (05-05)
- [ ] The same oval in reverse with identical physical boundaries. (05-09)
- [ ] Turnaround across start/finish and an intermediate boundary. (05-09)
- [ ] Pit/paddock position near, but not on, the reference line. (05-13)
- [ ] Clearly different/far course plus explicit override. (05-13)
- [ ] Hairpin/parallel straights that create nearest-segment ambiguity. (05-05, 05-12)
- [ ] Temporary off-course excursion followed by reliable rematch. (05-12)
- [ ] Missed intermediate boundary with valid start/finish lap completion. (05-07)
- [ ] Literal V1 Track, Review index, TimingSession, draft, and Ghost JSON plus malformed and future-version payloads. (05-01)

No test framework installation is required.

---

## Manual / Device UAT

Authored as `05-UAT.md` in Plan 05-14.

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| Android V1 upgrade | D-12..D-16 | Must exercise real app-private files and upgrade lifecycle | Preserve/export a current V1 dataset, install upgrade build, verify old Tracks, Sessions, exports, and Ghost references before creating a V2 revision |
| New-user create+select | SC-01 / D-01..D-02 | End-to-end first-run behavior | From a clean install, mark a track, name it, save, confirm it is selectable and reused after relaunch |
| Selection relaunch | D-01..D-04 / D-18..D-19 | Crosses persistence and app bootstrap | Select Track and direction, force-stop/relaunch, verify the exact selection returns and no newest substitution occurs |
| Offline editor | D-05..D-11 | Drag behavior and visual complete coverage require device inspection | Configure 2, 3, and 6 Sectors; drag boundaries along the trace; verify perpendicular lines and every interval including the final Sector |
| Revision and archive flow | D-12..D-16 | Navigation/history behavior spans multiple screens | Create Sector-only and start/finish revisions, archive current profile, verify history remains and selection clears without replacement |
| Wrong-course override | D-22..D-23 | Requires the user-facing decision flow | Trigger a far-course block, override it, save the Session, and verify Review marks the override |
| Live confidence recovery | D-20/D-24 | Mounted-phone presentation and continuity require end-to-end inspection | Run the excursion fixture and verify lap/raw counts continue while delta changes `value -> -- -> value` |
| iOS persistence | D-01..D-04 / D-12..D-19 | Windows cannot execute Xcode/iOS runtime tests | On macOS, run shared iOS tests and cold-launch the app after selection and revision creation |
| Safety presentation | Project constraint | Safety language and passive moving UI are visual requirements | Confirm closed-course/private-track language remains visible and editing controls are unavailable from the moving fullscreen dash |

---

## Validation Sign-Off

- [x] Every locked decision group has an automated or explicit manual verification path.
- [x] Each new test is authored and made green inside its owning plan/task (task-level RED -> GREEN); no task leaves an intentionally failing verify.
- [x] Migration, geometry, Sector, direction, compatibility, and preflight risks each have an independently green gate before downstream plans depend on them.
- [x] ASVS Level declared (L1); every HIGH-severity threat links to a concrete mitigating test; Threat Ref IDs match the PLAN.md `<threat_model>` blocks.
- [x] No watch-mode or real-track driving is required for automated verification.
- [x] Full-suite and Android build gates are defined per wave.
- [x] macOS/iOS limitations are explicit rather than silently treated as passed.
- [x] `nyquist_compliant: true` at planning time.

**Approval:** approved for Phase 5 planning (revised 2026-06-26 to match the 14-plan structure and ASVS-linked threat register).
</content>
