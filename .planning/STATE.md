---
gsd_state_version: 1.0
milestone: v1.0
milestone_name: milestone
status: in_progress
stopped_at: Completed quick task 260702-vn1 (unified Track detail course map)
last_updated: "2026-07-03T02:53:32.581Z"
progress:
  total_phases: 8
  completed_phases: 5
  total_plans: 37
  completed_plans: 35
  percent: 66
---

# State: LapSight

**Initialized:** 2026-06-25
**Current Status:** Phase 5.1 execution in progress. Plans 05.1-01, 05.1-02, 05.1-03, 05.1-04, 05.1-05, and 05.1-06 complete. Quick tasks 260629-wwd, 260702-uju, and 260702-vn1 fixed the field-test Ready blocker, Track editor usability/data-refresh blockers, and the duplicate Track detail map regression. Track detail now uses one beautified circuit map; `Edit course` switches that same map into editable mode instead of rendering a second trace. Remaining: Plan 07 (manual field UAT, gated per D-54), Plan 08 (Go/No-Go). Code-audit evidence layer for the Go gate = PASS.

## Project Reference

See: `.planning/PROJECT.md`

**Core value:** The user can mount a phone, record a session, and see trustworthy live lap timing plus delta-to-best with minimal interaction while moving.

## Current Focus

**Interphase hardening — after Phase 5, before Phase 5.1**

Phase 5 course-profile work is implemented. A post-execution Android hardening pass fixed
blocking usability issues in Drive, Review, recovery, theme handling, telemetry replay,
and Android Phone GPS provider wiring.

Next step: resume Plan 07 field UAT on the installed Android build. Confirm a Phone GPS
feed around 0.9-1.0 Hz no longer blocks Ready with `low GPS rate`, then re-test
Track detail/editor gestures: single beautified Trace map, in-place `Edit course`,
start/finish dragging, sector-boundary dragging, revision save refresh, and
duplicate-profile editability.

## Working Assumptions

- Project is greenfield.
- Repository: `https://github.com/HuanfuLi/LapSight.git`
- Phone companion app comes before Meta glasses integration.
- Kotlin Multiplatform + Compose Multiplatform is the current preferred stack.
- Lap engine must be clean-room and testable.
- Open-source references are research inputs, not direct copy sources.
- Android uses Fused Location Provider through the shared `LocationSampleProvider` boundary.
- iOS still needs a Core Location provider through the same boundary.

## Next Command Candidates

- UAT Android Phone GPS on an unlocked device outdoors and save one real marking/session.
- Review or commit the Android Phone GPS provider changes.
- Start `.planning/phases/05.1-mvp-field-validation-and-hardening-gate/` when ready.
- Complete Phase 5 milestone summary if a formal archive is needed.

## Review Checklist

- [x] Confirm project name: LapSight.
- [x] Confirm stack direction: KMP + Compose Multiplatform.
- [ ] Confirm app license policy.
- [x] Complete Android runtime UAT (Phase 1).
- [x] Complete Android on-device UAT (Phase 2 lap timing).
- [ ] Complete iOS Xcode runtime UAT.
- [x] Review Phase 2 plan.
- [x] Phase 3 complete (8/8 plans, all tests pass, androidApp:assembleDebug succeeds).
- [x] Phase 4 planned (4/4 plans, decision coverage 24/24).
- [x] Phase 4 executed (4/4 plans, shared checks and Android debug build pass).
- [x] Phase 4 verified via UAT.
- [x] Phase 5 planned (14 plans).
- [x] Phase 5 executed (14/14 plans complete).
- [x] Phase 5 Android hardening UAT completed before Phase 5.1.
- [x] Android Fused Location Provider wired into shared `LocationSampleProvider`.
- [ ] Android real GPS outdoor field sample captured and reviewed.

## Phase 3 Completion Summary

All 8 plans executed with atomic TDD commits:

| Plan | Name | Key Commits |
|------|------|-------------|
| 03-01 | Simulated GPS feed and fixture-backed Drive slice | TDD: test + feat + refactor |
| 03-02 | Blocking package verification gate | auto |
| 03-03 | Versioned local storage foundation | TDD: test + feat |
| 03-04 | Reference-line extraction domain | TDD: test + feat |
| 03-05 | Three-tab shell, Mark New Track, Track Review UI | auto + auto |
| 03-06 | Timing session drafts, save/discard, recovery | TDD: test + feat + feat |
| 03-07 | Offline vector trace rendering | TDD: test + feat + feat |
| 03-08 | JSON/GPX export with platform share handoff | TDD: test + feat + feat |

Requirements satisfied: SESS-01, SESS-02, SESS-03, SESS-04, SESS-05

## Phase 4 Plan Summary

| Plan | Name | Wave |
|------|------|------|
| 04-01 | Ghost progress-curve and live-delta domain | 1 |
| 04-02 | Reference-lap storage and timing-session integration | 2 |
| 04-03 | Minimal mounted-phone live delta UI | 3 |
| 04-04 | Variable-pace simulator, UAT, and roadmap reminder | 4 |

Requirements targeted: GHOST-01, GHOST-02, GHOST-03, GHOST-04

Requirements satisfied: GHOST-01, GHOST-02, GHOST-03, GHOST-04

## Phase 5 Execution Summary

- Plans 05-01 through 05-14 are complete.
- A post-execution hardening pass addressed field-test blockers discovered before Phase 5.1.
- `CourseCompatibilityKey(profileId, geometryCompatibilityId, direction, isSimulated)` remains the exact Ghost persistence/session identity.
- Course matching is independent from `LapEngine`; unmatched samples display `--` and rematch automatically.
- Wrong-course preflight blocks only trustworthy accuracy-adjusted distances over 250 m; explicit override starts normal Timing and persists evidence.
- Drive now resets the feed at formal timing start, uses a perpendicular start/finish boundary for closed-path captures, and no longer stalls the first lap display around 1.994 s.
- Landscape timing always enters fullscreen; the old optional landscape-fullscreen setting has been removed to avoid trapping controls behind system navigation.
- Review is grouped into Sessions, Tracks, and Raw captures; session rows use timestamps instead of copied track names, and timing details include telemetry chart/replay.
- Theme mode supports System, Dark, and Light.
- Android Phone GPS and Simulated feeds now share the same `LocationSampleProvider` interface and `LocationSample` model, with Settings-based source switching.
- Android precise location permission is requested from the shared UI path; no automatic fallback from Phone GPS to Simulated occurs.

## Recent Decisions

- Course matching requires full compatibility-key equality and matching provider provenance.
- Live delta uses normalized direction-relative course progress, not accumulated traveled distance.
- Dense local reference segments are excluded from nonlocal ambiguity competition.
- Legacy Recorded references may derive a bounded path from their exact-key raw lap; Reverse fails closed without explicit recorded-orientation geometry.
- Only a trustworthy accuracy-adjusted whole-course distance over 250 m produces a wrong-course block; unavailable evidence remains non-blocking.
- Wrong-course override consumes the captured profile revision, direction, source, course, and block without rerunning preflight.
- Plan 05-13 preserves the exact compatibility key and keeps preflight outside `LapEngine` and matcher behavior.
- Phase 5.1 remains a validation/hardening gate, not a new feature phase. Telemetry replay added in hardening is scoped to reviewing saved session data already captured by the phone app.
- Real and simulated feeds must remain explicitly labeled and source-gated; simulated data must never silently stand in for a selected Phone GPS run.
- Phase 5.1 host-test Gradle task is `:shared:testAndroidHostTest` (the AGP KMP `withHostTest` task), NOT `:shared:testDebugUnitTest` (does not exist). Warm runtime ~5-6s; 326 host tests pass (pure execution 0.570s). Confirmed in plan 05.1-01.
- SessionReplayDecoder treats malformed/truncated bytes and degenerate (zero-length) start/finish as typed data (Corrupt/NoCourse), never a thrown exception — the export→replay determinism link for D-25/D-28.
- Conservative Ready thresholds now used for field testing: horizontal accuracy 25 m, fix freshness 15 s, sample rate 0.9 Hz — injectable and validated. Quick task 260629-wwd lowered the previous 1.0 Hz floor after real-device testing showed Fused Location commonly hovering at 0.9-1.0 Hz.
- The aggregate Ready gate in `SessionController.startTiming` is opt-in (`requireReady`); production Drive UI opts in (D-13), engine/determinism tests keep legacy behavior. Wrong-course override (D-18) runs before the Ready gate and bypasses it.
- Session source now follows the live feed (PhoneGps/Simulated) via `AppShell` `sourceForTrack` injection, not the Track's marking source — the confirmed P1 evidence-integrity fix (D-04/D-42).
- Replay determinism (D-25..D-28) is now an automated standing gate: `FullPipelineDeterminismTest` asserts the full `SessionController` pipeline (laps, completedSectorResults, ordered LiveDeltaSnapshot sequence, CourseCompatibilityKey/direction) is byte-identical across runs and across export→decode→replay; the legacy finalState-only replay/recovery/ghost/direction tests were widened to full algorithmic output (Plan 05.1-02).
- Oval GPS fixtures complete laps through `SessionController` under `CourseDirection.Reverse` (the explicit accepted-sign is enforced even under lenient config); the Recorded config deterministically rejects the same physical crossings.
- UI hardening (Plan 05.1-05): Drive dash + shell colors/typography/spacing now flow through `MaterialTheme` semantic tokens (`ui/Theme.kt`) + a 4dp `LocalSpacing` scale (`ui/Spacing.kt`); 0 inline hex and 0 inline `fontSize`/`.sp` remain. Hero readouts use `TextAutoSize.StepBased` so glance sizing is clip-safe. Six D-36 pillars re-audited: all >= 3/4 (overall 19/24); no Hardening-Required UI blocker. Information hierarchy and manual orientation toggle unchanged.
- Field-UAT protocol authored (Plan 05.1-06) and updated by quick task 260629-wwd: `5.1-UAT.md` is the device-independent Android-only protocol (on-device smoke S1-S6, mounted-display glance over the 7 core elements portrait/landscape x daylight/low-light, Ready-before-timing with the 25.0 m / 15000 ms / 0.9 Hz thresholds, the 3+2 five-valid-session matrix, lap-count-100% hard blocker, <=0.5 s video target, replay-diagnosis-first via `SessionReplayDecoder`, and Review + JSON/GPX export smoke R1-R5 with concrete acceptance). `5.1-FIELD-TEST-LOG.md` is the 5-row (3 primary + 2 secondary) evidence-index skeleton on the D-48 template. Both are pending real field evidence (D-54), filled during Plan 07. Build/install uses the corrected `:shared:testAndroidHostTest` + `:androidApp:assembleDebug`.
- Code audit complete (Plan 05.1-04): `5.1-CODE-REVIEW.md` is the deep severity-tagged audit of every Phase 1-5 core path. Verdict: core-path P0/P1/P2 CLEAR for the Go gate (D-42) — zero open findings; the confirmed source-provenance P1 (AppShell.kt) is verified fixed by Plan 03; 4 P3/info backlog items (Okio-in-I/O-seam is not an ARCH-01 breach, belt-and-suspenders `..` replacement, optional first-run safety gate, undecided app license). Clean-room boundary verified (engine imports zero Compose/platform; Okio only in storage/export I/O), engine pure (no Random/clock in `lap/`), no network/analytics in `commonMain`, no `doves`/`gpl` in `shared/src`, export-filename path traversal mitigated + tested, bad-input-as-data confirmed, manual orientation confirmed. `docs/THIRD-PARTY-LICENSES.md` delivers the owed ARCH-03 inventory (all deps Apache-2.0 except proprietary Play Services Location + test-only JUnit; none GPL) + ARCH-04 clean-room attestation + local-GPS privacy note. The app's own license remains an open product decision (tracked risk, not a core-timing blocker). REQUIREMENTS.md reconciled: PLAT-01, SAFE-03, ARCH-01/03/04 marked complete; PLAT-02 correctly pending (iOS out of scope, D-02).
- Quick task 260702-uju hardened Track editor after field feedback: start/finish and sector boundaries now drag by relative course progress instead of repeated nearest-point taps; the circuit illustration uses thicker outer/inner strokes and larger handles; Review refreshes after profile mutations; duplicated V2-only profiles stay editable. Verification: focused editor/profile tests, full `:shared:testAndroidHostTest`, and `:androidApp:assembleDebug` passed.
- Quick task 260702-vn1 fixed the Track detail duplicate-map regression: the original `Trace` now uses the shared beautified circuit canvas, and `Edit course` switches that same map into edit mode in place. Verification: `:shared:testAndroidHostTest`, `:androidApp:assembleDebug`, `:androidApp:installDebug`, and ADB launch on device `25053RT47C` passed.

## Performance Metrics

| Phase-Plan | Duration | Tasks | Files |
|------------|----------|-------|-------|
| 05-12 | 20min | 2 | 10 |
| Phase 05 P13 | 13min | 2 tasks | 7 files |
| 05.1-01 | 35min | 2 tasks | 4 files |
| 05.1-03 | 50min | 3 tasks | 8 files |
| 05.1-02 | 45min | 2 tasks | 6 files |
| 05.1-05 | 14min | 3 tasks | 6 files |
| 05.1-06 | 12min | 2 tasks | 2 files |
| 05.1-04 | 30min | 2 tasks | 2 files |

## Quick Tasks Completed

| # | Description | Date | Commit | Directory |
|---|-------------|------|--------|-----------|
| 260629-wwd | Phase 5.1 field-test blocker hardening: relax GPS Ready rate, tidy pre-timing Drive layout, replace rotate icon | 2026-06-30 | 04fff42 | [260629-wwd-phase-5-1-field-test-blocker-hardening-r](./quick/260629-wwd-phase-5-1-field-test-blocker-hardening-r/) |
| 260702-uju | Track editor field-test hardening: fix start/finish drag behavior, editable sectors, live review refresh, duplicated profile editability, and track rendering polish | 2026-07-03 | ed5e81b | [260702-uju-track-editor-field-test-hardening-fix-st](./quick/260702-uju-track-editor-field-test-hardening-fix-st/) |
| 260702-vn1 | Unify Track detail trace and edit course map so the original Trace is beautified and editing does not render a duplicate map | 2026-07-03 | ee84fef | [260702-vn1-unify-track-detail-trace-and-edit-course](./quick/260702-vn1-unify-track-detail-trace-and-edit-course/) |

## Session Continuity

**Last session:** 2026-07-03T02:53:32.581Z
**Stopped At:** Completed quick task 260702-vn1 (unified Track detail course map)
**Resume File:** `.planning/quick/260702-vn1-unify-track-detail-trace-and-edit-course/260702-vn1-SUMMARY.md`

---

*Last updated: 2026-07-03 after quick task 260702-vn1 (unified Track detail course map)*
