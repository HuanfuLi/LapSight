---
gsd_state_version: 1.0
milestone: v1.0
milestone_name: milestone
status: in_progress
stopped_at: Android Phone GPS provider wired behind shared LocationSampleProvider
last_updated: "2026-06-29T00:31:36.957Z"
progress:
  total_phases: 8
  completed_phases: 5
  total_plans: 37
  completed_plans: 29
  percent: 63
---

# State: LapSight

**Initialized:** 2026-06-25
**Current Status:** Phase 5 execution and Android hardening UAT complete. Android Phone GPS is wired for pre-5.1 field prep. Phase 5.1 has not started.

## Project Reference

See: `.planning/PROJECT.md`

**Core value:** The user can mount a phone, record a session, and see trustworthy live lap timing plus delta-to-best with minimal interaction while moving.

## Current Focus

**Interphase hardening — after Phase 5, before Phase 5.1**

Phase 5 course-profile work is implemented. A post-execution Android hardening pass fixed
blocking usability issues in Drive, Review, recovery, theme handling, telemetry replay,
and Android Phone GPS provider wiring.

Next step: perform an unlocked-device Android UAT of the Phone GPS / Simulated switch,
then start Phase 5.1 field-validation planning. Do not begin paid/real-world field
validation until the real GPS feed is checked outdoors.

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

## Performance Metrics

| Phase-Plan | Duration | Tasks | Files |
|------------|----------|-------|-------|
| 05-12 | 20min | 2 | 10 |
| Phase 05 P13 | 13min | 2 tasks | 7 files |

## Session Continuity

**Last session:** 2026-06-28T17:43:29.104Z
**Stopped At:** Android Phone GPS provider wired behind shared LocationSampleProvider
**Resume File:** None

---

*Last updated: 2026-06-28 after Android Phone GPS provider wiring*
