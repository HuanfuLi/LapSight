---
phase: 05
slug: track-setup-and-course-profiles
status: approved
nyquist_compliant: true
wave_0_complete: false
created: 2026-06-26
---

# Phase 05 — Validation Strategy

> Per-phase validation contract for reusable course profiles, complete Sector timing, revisions, direction variants, and wrong-course handling.

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

- After every implementation task: run the new focused test class plus directly affected existing tests.
- After every plan wave: run `.\gradlew.bat :shared:check`.
- After UI or storage waves: also run `.\gradlew.bat :androidApp:assembleDebug`.
- Before `$gsd-verify-work 5`: require the full shared suite, Android build, deterministic replay matrix, migration UAT, and the documented macOS/iOS gate.
- Max automated feedback latency: one task commit.

---

## Per-Task Verification Map

| Task ID | Intended Slice | Wave | Requirement | Threat Ref | Secure/Safe Behavior | Test Type | Automated Command | File Exists | Status |
|---------|----------------|------|-------------|------------|----------------------|-----------|-------------------|-------------|--------|
| 05-W0-01 | V1 characterization and migration fixtures | 0 | D-12..D-16 | T-05-01/T-05-02 | Literal old payloads load through explicit version dispatch; unknown future versions fail closed | migration/fault injection | `.\gradlew.bat :shared:testAndroidHostTest --tests "*SchemaMigrationTest*"` | ❌ W0 | ⬜ pending |
| 05-W0-02 | Explicit current Track selection | 0 | D-01..D-04 | T-05-03 | Selection persists; archived/corrupt selection becomes unavailable; no newest fallback occurs | repository/controller integration | `.\gradlew.bat :shared:testAndroidHostTest --tests "*CurrentTrackSelectionTest*"` | ❌ W0 | ⬜ pending |
| 05-W0-03 | Closed-path geometry and editor | 0 | D-05/D-08..D-10 | T-05-04 | Arc placement, tangent/normal generation, constrained drag, spacing, and invalid geometry are deterministic | unit/property-style | `.\gradlew.bat :shared:testAndroidHostTest --tests "*ClosedReferencePathTest*"` | ❌ W0 | ⬜ pending |
| 05-W0-04 | Complete Sector timing | 0 | D-06..D-11 | T-05-04/T-05-05 | `N` Sectors cover the entire closed lap; final interval exists; duration and cumulative Split remain distinct | replay/unit | `.\gradlew.bat :shared:testAndroidHostTest --tests "*CompleteSectorReplayTest*"` | ❌ W0 | ⬜ pending |
| 05-W0-05 | Profile revisions and lifecycle | 0 | D-12..D-16 | T-05-01/T-05-06 | Revisions are immutable; archive preserves history; duplicate creates an independent profile | repository/domain integration | `.\gradlew.bat :shared:testAndroidHostTest --tests "*TrackRevisionStoreTest*"` | ❌ W0 | ⬜ pending |
| 05-W0-06 | Recorded/reverse Course Direction | 0 | D-18..D-21 | T-05-05 | Progress, Sector order, and every boundary orientation reverse; turnaround cannot create a false short lap | replay/unit | `.\gradlew.bat :shared:testAndroidHostTest --tests "*CourseDirectionReplayTest*"` | ❌ W0 | ⬜ pending |
| 05-W0-07 | Ghost compatibility | 0 | D-15/D-19 | T-05-06/T-05-07 | Compatibility includes geometry, direction, and source; Sector-only revision keeps the valid Ghost | storage/integration | `.\gradlew.bat :shared:testAndroidHostTest --tests "*GhostCompatibilityTest*"` | ❌ W0 | ⬜ pending |
| 05-W0-08 | Wrong-course preflight | 0 | D-22..D-23 | T-05-08 | Whole-loop preflight allows pit/paddock starts, blocks clearly far courses, and persists explicit override | replay/controller | `.\gradlew.bat :shared:testAndroidHostTest --tests "*WrongCoursePreflightTest*"` | ❌ W0 | ⬜ pending |
| 05-W0-09 | Live progress confidence | 0 | D-20/D-24 | T-05-08 | Ambiguous/off-course position shows `--` while lap timing and raw GPS continue; delta resumes after rematch | replay/integration | `.\gradlew.bat :shared:testAndroidHostTest --tests "*CourseProgressMatcherTest*"` | ❌ W0 | ⬜ pending |
| 05-W0-10 | Export and historical Review regression | 0 | D-13..D-15/D-23 | T-05-01/T-05-06 | Old and new Sessions render/export their own immutable course snapshots and override metadata | integration | `.\gradlew.bat :shared:testAndroidHostTest --tests "*JsonExportTest*" --tests "*ReviewSummaryTest*"` | ✅ extend existing | ⬜ pending |

---

## Required Deterministic Fixtures

- [ ] Closed oval with known perimeter and equal arc-length Sector points.
- [ ] The same oval in reverse with identical physical boundaries.
- [ ] Turnaround across start/finish and an intermediate boundary.
- [ ] Pit/paddock position near, but not on, the reference line.
- [ ] Clearly different/far course plus explicit override.
- [ ] Hairpin/parallel straights that create nearest-segment ambiguity.
- [ ] Temporary off-course excursion followed by reliable rematch.
- [ ] Missed intermediate boundary with valid start/finish lap completion.
- [ ] Literal V1 Track, Review index, TimingSession, draft, and Ghost JSON plus malformed and future-version payloads.

No test framework installation is required.

---

## Manual / Device UAT

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| Android V1 upgrade | D-12..D-16 | Must exercise real app-private files and upgrade lifecycle | Preserve/export a current V1 dataset, install upgrade build, verify old Tracks, Sessions, exports, and Ghost references before creating a V2 revision |
| Selection relaunch | D-01..D-04/D-18..D-19 | Crosses persistence and app bootstrap | Select Track and direction, force-stop/relaunch, verify the exact selection returns and no newest substitution occurs |
| Offline editor | D-05..D-11 | Drag behavior and visual complete coverage require device inspection | Configure 2, 3, and 6 Sectors; drag boundaries along the trace; verify perpendicular lines and every interval including the final Sector |
| Revision and archive flow | D-12..D-16 | Navigation/history behavior spans multiple screens | Create Sector-only and start/finish revisions, archive current profile, verify history remains and selection clears without replacement |
| Wrong-course override | D-22..D-23 | Requires the user-facing decision flow | Trigger a far-course block, override it, save the Session, and verify Review marks the override |
| Live confidence recovery | D-20/D-24 | Mounted-phone presentation and continuity require end-to-end inspection | Run the excursion fixture and verify lap/raw counts continue while delta changes `value → -- → value` |
| iOS persistence | D-01..D-04/D-12..D-19 | Windows cannot execute Xcode/iOS runtime tests | On macOS, run shared iOS tests and cold-launch the app after selection and revision creation |
| Safety presentation | Project constraint | Safety language and passive moving UI are visual requirements | Confirm closed-course/private-track language remains visible and editing controls are unavailable from the moving fullscreen dash |

---

## Validation Sign-Off

- [x] Every locked decision group has an automated or explicit manual verification path.
- [x] Wave 0 captures migration, geometry, Sector, direction, compatibility, and preflight risks before production changes.
- [x] No watch-mode or real-track driving is required for automated verification.
- [x] Full-suite and Android build gates are defined per wave.
- [x] macOS/iOS limitations are explicit rather than silently treated as passed.
- [x] `nyquist_compliant: true` at planning time.

**Approval:** approved for Phase 5 planning.
