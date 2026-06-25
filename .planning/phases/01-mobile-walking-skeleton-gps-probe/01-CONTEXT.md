# Phase 1: Mobile Walking Skeleton + GPS Probe - Context

**Gathered:** 2026-06-25
**Status:** Ready for planning
**Source:** User-approved project direction + existing roadmap

<domain>

## Phase Boundary

Phase 1 proves that LapSight can be opened as a real mobile project and can present the first mounted-phone dash screen. The working slice must include Android/iOS project structure, shared UI, shared state models, and a GPS probe interface that can display either real platform data or a deterministic simulator fallback.

This phase does not implement lap detection, start/finish line crossing, session persistence, ghost delta, external GNSS, or Meta glasses output.

</domain>

<decisions>

## Implementation Decisions

### Stack

- Use Kotlin Multiplatform with a shared module for UI/state/domain code.
- Use Compose Multiplatform for the first shared dash UI.
- Use the JetBrains KMP wizard mobile-shared structure as the starting point: `androidApp`, `iosApp`, `shared`.
- Keep Android app entry point separate from the shared KMP module to avoid AGP 9 plugin conflicts.

### Android

- Android app module owns Android manifest, application packaging, and permission declarations.
- Android location implementation can start as a simulator if platform wiring becomes a build blocker, but the interfaces must be ready for Fused Location Provider.

### iOS

- iOS Xcode project remains the launch path for iOS.
- Core Location implementation can be deferred behind the same interface if iOS build tooling is unavailable on Windows.

### UI

- The live dash must prioritize speed, GPS accuracy, update rate, elapsed time, fix status, and sample count.
- The layout must be readable in both portrait and landscape.
- The UI must include safety/accuracy language for closed-course use.

### Testing

- Shared state formatting and simulator behavior should be testable on the JVM/host side.
- Platform location permission flows do not need full automated tests in Phase 1.

### the agent's Discretion

- Exact package/module names, UI styling, and simulator timing implementation may be chosen by the implementation agent.
- If current Android SDK or iOS tooling is unavailable, document the blocker and still verify shared Gradle tasks that can run locally.

</decisions>

<canonical_refs>

## Canonical References

### Project

- `.planning/PROJECT.md` — product scope, constraints, and core value.
- `.planning/REQUIREMENTS.md` — Phase 1 requirement IDs and definition of done.
- `.planning/ROADMAP.md` — Phase 1 goal and success criteria.
- `.planning/research/STACK.md` — stack rationale and official docs.

### External

- Kotlin KMP wizard template — source structure reference.
- Android KMP plugin docs — AGP 9 module split and shared module plugin guidance.
- Compose Multiplatform docs — shared UI and version compatibility.

</canonical_refs>

<specifics>

## Specific Ideas

- First screen title: LapSight.
- Primary fields: speed, GPS accuracy, update rate, elapsed time, sample count, fix status.
- Controls: Start Probe, Stop Probe, Reset.
- Simulator fallback: emit plausible changing speed/accuracy/update-rate samples on a timer.
- Model names can be simple: `GpsFixStatus`, `LocationSample`, `GpsProbeState`.

</specifics>

<deferred>

## Deferred Ideas

- Real lap timing engine.
- Start/finish line UI.
- Session persistence.
- Track map.
- Ghost/delta.
- External GNSS.
- Meta glasses bridge.

</deferred>

---

*Phase: 01-mobile-walking-skeleton-gps-probe*
*Context gathered: 2026-06-25*
