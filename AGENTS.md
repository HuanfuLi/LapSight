# LapSight Agent Guide

## Project Scope

LapSight is a phone-first lap timing and ghost delta app for karting, track driving, and cycling. The phone companion app is the source of truth for GPS, sessions, lap engine state, and future Meta glasses HUD output.

Do not treat this as a generic fitness tracker or smartwatch-style dashboard. The product is a mounted-phone timing instrument with a future MR display extension.

## Current Architecture Direction

- Kotlin Multiplatform for shared domain logic.
- Compose Multiplatform for initial shared UI, pending real-device validation.
- Android location via Fused Location Provider.
- iOS location via Core Location.
- Clean-room shared lap engine.
- Local-first session storage.
- Future external GNSS and Meta glasses bridge are v2+.

## Non-Negotiables

- Do not copy GPL-licensed code from DovesLapTimer or DovesDataViewer unless the project license decision explicitly allows it.
- Keep lap engine logic independent from UI and platform APIs.
- Every algorithmic behavior must be testable with synthetic or recorded replay data.
- Safety language must remain explicit: closed-course/private-track use, passive UI while moving, no public-road racing positioning.
- Do not build the glasses app before the phone companion produces reliable timing state.

## Planning References

- Project context: `.planning/PROJECT.md`
- Requirements: `.planning/REQUIREMENTS.md`
- Roadmap: `.planning/ROADMAP.md`
- Current state: `.planning/STATE.md`
- Stack research: `.planning/research/STACK.md`
- Lap engine research: `.planning/research/LAP_ENGINE.md`

## Preferred Workflow

1. Review `.planning/STATE.md`.
2. Work phase by phase from `.planning/ROADMAP.md`.
3. For each phase, produce or update a concrete implementation plan before coding.
4. Keep changes vertical: after each phase, the user should be able to do something observable.
5. Verify with automated tests where possible, especially for lap engine logic.

## Initial Next Step

Start with Phase 1: Mobile Walking Skeleton + GPS Probe.

Do not spend Phase 1 on full data modeling, advanced maps, ghost delta, or external sensors. The first proof is a runnable Android/iOS app with live GPS quality and a mounted-phone dash.
