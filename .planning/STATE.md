---
gsd_state_version: 1.0
milestone: v1.0
milestone_name: milestone
status: in_progress
last_updated: "2026-06-28T01:31:06Z"
progress:
  total_phases: 8
  completed_phases: 4
  total_plans: 28
  completed_plans: 26
  percent: 93
---

# State: LapSight

**Initialized:** 2026-06-25
**Current Status:** Phase 5 execution in progress (12/14 plans complete).

## Project Reference

See: `.planning/PROJECT.md`

**Core value:** The user can mount a phone, record a session, and see trustworthy live lap timing plus delta-to-best with minimal interaction while moving.

## Current Focus

**Phase 5 IN PROGRESS — Track Setup and Course Profiles**

Plan 05-12 replaced traveled-distance Ghost progress with direction-relative course matching.
Matcher failure now suppresses only live delta while timing, sectors, raw capture, and same-session best updates continue.

Next step: Execute 05-13-PLAN.md (wrong-course preflight, override, and vertical integration).

## Working Assumptions

- Project is greenfield.
- Repository: `https://github.com/HuanfuLi/LapSight.git`
- Phone companion app comes before Meta glasses integration.
- Kotlin Multiplatform + Compose Multiplatform is the current preferred stack.
- Lap engine must be clean-room and testable.
- Open-source references are research inputs, not direct copy sources.
- Phase 1 uses simulator-backed GPS probe state; real providers are next.

## Next Command Candidates

- Execute `.planning/phases/05-track-setup-and-course-profiles/05-13-PLAN.md`.
- After 05-14, run Phase 5 verification; do not mark Phase 5 complete before both remaining plans execute.

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
- [ ] Phase 5 executed (12/14 plans complete).

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

- Plans 05-01 through 05-12 are complete.
- Plans 05-13 and 05-14 remain.
- `CourseCompatibilityKey(profileId, geometryCompatibilityId, direction, isSimulated)` remains the exact Ghost persistence/session identity.
- Course matching is independent from `LapEngine`; unmatched samples display `--` and rematch automatically.

## Recent Decisions

- Course matching requires full compatibility-key equality and matching provider provenance.
- Live delta uses normalized direction-relative course progress, not accumulated traveled distance.
- Dense local reference segments are excluded from nonlocal ambiguity competition.
- Legacy Recorded references may derive a bounded path from their exact-key raw lap; Reverse fails closed without explicit recorded-orientation geometry.

## Performance Metrics

| Phase-Plan | Duration | Tasks | Files |
|------------|----------|-------|-------|
| 05-12 | 20min | 2 | 10 |

## Session Continuity

**Last session:** 2026-06-28T01:31:06Z
**Stopped At:** Completed 05-12-PLAN.md
**Resume File:** None

---

*Last updated: 2026-06-28 after Plan 05-12*
