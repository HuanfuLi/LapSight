# State: LapSight

**Initialized:** 2026-06-25
**Current Status:** Planning docs created for review.

## Project Reference

See: `.planning/PROJECT.md`

**Core value:** The user can mount a phone, record a session, and see trustworthy live lap timing plus delta-to-best with minimal interaction while moving.

## Current Focus

**Phase 1: Mobile Walking Skeleton + GPS Probe**

The next practical step is to turn the empty repository into a runnable mobile project and validate live GPS acquisition on real devices. Do not implement ghost/delta before the GPS probe and lap engine replay tests exist.

## Working Assumptions

- Project is greenfield.
- Repository: `https://github.com/HuanfuLi/LapSight.git`
- Phone companion app comes before Meta glasses integration.
- Kotlin Multiplatform + Compose Multiplatform is the current preferred stack.
- Lap engine must be clean-room and testable.
- Open-source references are research inputs, not direct copy sources.

## Next Command Candidates

- `$gsd-discuss-phase 1` — clarify Phase 1 implementation details.
- `$gsd-ui-phase 1` — define dash UI contract before coding.
- `$gsd-plan-phase 1` — generate an executable implementation plan.

## Review Checklist

- [ ] Confirm project name: LapSight.
- [ ] Confirm stack direction: KMP + Compose Multiplatform.
- [ ] Confirm whether Phase 1 must support iOS immediately or may spike Android first.
- [ ] Confirm whether planning docs should be committed.
- [ ] Confirm app license policy.

---
*Last updated: 2026-06-25 after initialization*
