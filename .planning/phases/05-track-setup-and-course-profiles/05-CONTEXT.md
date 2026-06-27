# Phase 5: Track Setup and Course Profiles - Context

**Gathered:** 2026-06-26
**Status:** Ready for planning

<domain>
## Phase Boundary

Phase 5 turns saved Tracks into reusable local course profiles. It delivers explicit current-track selection, offline start/finish and sector setup, profile revisions and archiving, forward/reverse course configurations, and a broad wrong-course preflight before Timing starts.

The phase must preserve existing TimingSession and Ghost history while course setup evolves. It does not add cloud/shared tracks, nearby-track recommendation, map tiles, external GNSS, telemetry charts, or Meta glasses output.

</domain>

<decisions>
## Implementation Decisions

### Track Selection and Reuse

- **D-01:** Persist an explicit current Track and reuse it on Drive. Display its name clearly and never silently switch to the newest saved Track.
- **D-02:** Provide a compact Track selector on Drive and a `Set as current track` action in Track Review/detail.
- **D-03:** If no timing-ready Track is selected, block Timing and show a direct Track-selection action. Do not fall back to the newest Track.
- **D-04:** Phase 5 uses explicit manual selection and remembers the last selection. Location-based nearby-track recommendation is deferred.

### Start/Finish and Sector Semantics

- **D-05:** Start/finish remains an editable timing boundary on the offline reference-trace screen. Formal Timing still requires a confirmed start/finish.
- **D-06:** A Sector is a complete course interval, not the timing line at its end. On a closed course, `N` Sectors are formed by the start/finish boundary plus `N-1` intermediate Sector boundaries; every part of the lap belongs to exactly one Sector.
- **D-07:** Sector timing may be disabled or configured for 2 through 6 Sectors. A new enabled setup defaults to 3 Sectors.
- **D-08:** When the user selects `N` Sectors, generate `N-1` boundaries at equal arc-length intervals along the TrackReferenceLine.
- **D-09:** Generate each intermediate boundary as a normal perpendicular to the local reference-line tangent. The user does not draw the line or move its endpoints.
- **D-10:** Users may drag a Sector boundary only along the reference line. The system continuously recomputes its local tangent and perpendicular boundary.
- **D-11:** Derive Sector order from reference-line progress beginning at start/finish in the selected Course Direction. Default labels are `Sector 1...N`; labels may be edited. Sector duration is the difference between adjacent boundary-crossing times; cumulative Split time is derived separately.

### Profile Revisions and Historical Data

- **D-12:** A logical Track profile has a stable identity and immutable geometry revisions. Name and other non-geometric metadata may update directly.
- **D-13:** Changes to the reference line, start/finish, Sector count, or Sector boundary positions create a new revision rather than overwriting prior geometry.
- **D-14:** Normal Track selection shows only the latest active revision. Track detail exposes version history; old revisions remain available to historical TimingSessions.
- **D-15:** Sector-only revisions retain fastest-lap and Ghost compatibility because lap geometry and start/finish are unchanged. Reference-line or start/finish changes break compatibility and start a new fastest-lap history.
- **D-16:** Archive replaces destructive Track deletion: archived profiles leave active selectors but retain revisions, sessions, and Ghosts. Duplicating a Track creates an independent logical profile and version history.

### Course Direction and Wrong-Course Handling

- **D-17:** Do not implement a continuous global wrong-direction state, pause, or warning.
- **D-18:** Each Track revision supports the recorded direction and its reverse as selectable Course Direction configurations. They share physical geometry, but progress direction and Sector order are reversed.
- **D-19:** Remember the last selected Course Direction. Fastest laps and Ghost references are isolated by Track revision compatibility and Course Direction.
- **D-20:** Stopping, reversing, or turning around during a lap does not pause Timing, Sector calculation, Ghost processing, or raw GPS capture. It remains part of the lap's actual performance.
- **D-21:** Timing boundaries remain directional. Crossing a boundary opposite to the selected Course Direction does not complete a lap or Sector; this prevents a turnaround across start/finish from creating a false short lap. Selecting the reverse Course Direction flips the accepted crossing direction.
- **D-22:** Before Timing starts, compare the latest valid GPS position with the selected Track's complete reference line, not only start/finish. Block only when it is clearly far from the whole course, so a pit/paddock start remains valid.
- **D-23:** A blocked wrong-course preflight offers an explicit `Still use this track` override. Record the override in the TimingSession so Review can identify it.
- **D-24:** After Timing starts, never stop or invalidate a lap merely because the position is far from the reference line. Continue Timing and raw GPS capture. If progress cannot be matched confidently, show Ghost delta as `--` and resume it automatically when matching becomes reliable.

### the agent's Discretion

- Exact distance/confidence thresholds for wrong-course preflight and live progress matching, provided they are conservative and covered by deterministic replay tests.
- Tangent smoothing, generated boundary length, drag snapping, and minimum spacing needed to keep generated Sector boundaries stable under noisy reference geometry.
- Exact start/finish editing affordance and validation details on the offline trace, while preserving the confirmed-boundary requirement.
- Internal schema/migration mechanics, naming, and UI wording for profile revisions, archives, and recorded/reverse Course Directions.
- Whether editable Sector labels ship in the first implementation wave or use generated labels initially, provided stable IDs and correct complete-coverage timing semantics are implemented.

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Project Scope and Phase Boundary

- `AGENTS.md` — phone-first product boundary, clean-room engine rule, replay-test requirement, safety language, and future-glasses constraint.
- `.planning/PROJECT.md` — product scope, architecture direction, constraints, and deferred capabilities.
- `.planning/REQUIREMENTS.md` — current milestone requirements and traceability conventions.
- `.planning/ROADMAP.md` — Phase 5 goal and success criteria.
- `.planning/STATE.md` — current implementation and workflow state.

### Prior Product Decisions

- `.planning/phases/03-local-sessions-review-and-export/03-CONTEXT.md` — Track/TrackReferenceLine/TimingSession separation, record-first course creation, offline trace rendering, local versioned storage, Review navigation, and simulator isolation.
- `.planning/phases/04-ghost-lap-live-delta/04-CONTEXT.md` — Track-scoped fastest-lap references, live-delta confidence suppression, same-session reference updates, and deterministic continuous simulator behavior.

### Track, Timing, and Storage

- `shared/src/commonMain/kotlin/com/huanfuli/lapsight/shared/track/TrackModels.kt` — current Track, TrackReferenceLine, start/finish, SectorLineDto, review index, and V1 payload shapes.
- `shared/src/commonMain/kotlin/com/huanfuli/lapsight/shared/track/ReferenceLineExtractor.kt` — existing closed-loop reference-line extraction used as the geometry source.
- `shared/src/commonMain/kotlin/com/huanfuli/lapsight/shared/storage/LocalSessionStore.kt` — current persistence contract; lacks explicit profile update/revision/archive/current-selection operations.
- `shared/src/commonMain/kotlin/com/huanfuli/lapsight/shared/storage/FileSessionStore.kt` — file-backed payload/index implementation that must migrate safely.
- `shared/src/commonMain/kotlin/com/huanfuli/lapsight/shared/storage/InMemorySessionStore.kt` — deterministic test storage implementation that must mirror the file contract.
- `shared/src/commonMain/kotlin/com/huanfuli/lapsight/shared/session/SessionModels.kt` — TimingSession and GhostReference payloads that need revision/direction compatibility metadata.
- `shared/src/commonMain/kotlin/com/huanfuli/lapsight/shared/session/SessionController.kt` — loads Track geometry, starts Timing, snapshots course setup, and resolves persisted Ghost references.

### Lap, Sector, and Ghost Engines

- `shared/src/commonMain/kotlin/com/huanfuli/lapsight/shared/lap/TimingLines.kt` — current StartFinishLine, SectorLine, and CourseDefinition semantics to refactor.
- `shared/src/commonMain/kotlin/com/huanfuli/lapsight/shared/lap/LapModels.kt` — current cumulative SectorEvent and per-line Sector state.
- `shared/src/commonMain/kotlin/com/huanfuli/lapsight/shared/lap/LapEngine.kt` — current crossing processing, timing continuity, and direction rejection behavior.
- `shared/src/commonMain/kotlin/com/huanfuli/lapsight/shared/lap/CrossingDetector.kt` — finite timing-line crossing detection and directional acceptance.
- `shared/src/commonMain/kotlin/com/huanfuli/lapsight/shared/ghost/GhostModels.kt` — reference-lap and live-delta states, including low-confidence suppression.
- `shared/src/commonMain/kotlin/com/huanfuli/lapsight/shared/ghost/LiveDeltaEngine.kt` — current distance-accumulation implementation that needs direction and reliable course-position matching considered.
- `shared/src/commonMain/kotlin/com/huanfuli/lapsight/shared/ghost/ReferenceLapSelector.kt` — fastest valid lap selection and reference construction.

### UI and Offline Trace

- `shared/src/commonMain/kotlin/com/huanfuli/lapsight/shared/ui/DriveMarkingController.kt` — currently chooses the newest timing-ready Track and is the main integration point for explicit current selection.
- `shared/src/commonMain/kotlin/com/huanfuli/lapsight/shared/ui/DriveScreen.kt` — Drive/marking/timing entry points and mounted-phone presentation.
- `shared/src/commonMain/kotlin/com/huanfuli/lapsight/shared/ui/ReviewScreen.kt` — Track and TimingSession detail surfaces.
- `shared/src/commonMain/kotlin/com/huanfuli/lapsight/shared/review/ReviewModels.kt` — offline timing/reference trace layers and line rendering.
- `shared/src/commonMain/kotlin/com/huanfuli/lapsight/shared/review/TraceProjection.kt` — existing geographic-to-offline-canvas projection support.

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets

- `TrackReferenceLine` and `ReferenceLineExtractor` already provide the canonical geographic closed loop needed for arc-length placement and direction reversal.
- `ReviewModels`, `TraceProjection`, and `ReviewScreen` already render offline reference traces plus timing lines; extend these rather than introducing map tiles.
- `CrossingDetector` already handles finite line crossings and direction rejection. Course Direction should configure line orientation instead of adding a global reverse-driving state machine.
- `LapEngine`, deterministic replay fixtures, and in-memory storage provide the required test seams for Sector coverage, boundary ordering, revision compatibility, and wrong-course preflight.
- Phase 4 reference-lap persistence and low-confidence `--` state can be extended to revision/direction compatibility.

### Established Patterns

- Shared domain logic owns Track, lap, Sector, Ghost, and persistence behavior; Compose and platform code render state and forward actions.
- TimingSession snapshots start/finish and Sector setup at start. Historical playback must continue using that immutable snapshot/revision.
- Canonical data remains latitude/longitude and versioned local JSON. Canvas coordinates are rendering-only.
- Simulated GPS replaces the provider layer and follows the normal business flow; all new checks require deterministic simulated/recorded coverage.
- Demo/Simulated data remains isolated from real fastest-lap and Ghost records.

### Current Semantic Gaps

- `DriveMarkingController.snapshot()` silently selects the newest Track with start/finish; Phase 5 must replace this with persisted explicit selection.
- `SectorLine`/`SectorLineDto` currently represents an intermediate boundary but is named and surfaced as though it were a complete Sector.
- `SectorEvent.splitMillis` is cumulative from lap start, and the final interval from the last intermediate line to start/finish has no independent Sector result. This violates D-06 and requires a domain/test refactor.
- `LapEngine` de-duplicates line crossings but does not model complete adjacent Sector intervals as the Phase 5 contract requires.
- Track payloads have no stable profile ID, revision, archive state, or Course Direction configuration.
- TimingSession and Ghost reference keys currently use Track ID/source isolation without explicit revision compatibility or Course Direction.
- `LocalSessionStore` supports save/load bundles but not profile metadata update, revision creation, archive, duplicate, or current-selection persistence.
- `LiveDeltaEngine` currently accumulates traveled distance; reliable reference-line projection/off-course confidence and reversed progress require explicit planning.

### Integration Points

- Add profile/revision/direction identity to Track and TimingSession persistence with backward-compatible schema migration.
- Derive a direction-specific CourseDefinition from the selected Track revision before `SessionController.startTiming`.
- Replace line-centric Sector timing with complete interval results while preserving any required cumulative Split output.
- Key fastest-lap/Ghost lookup by compatible Track geometry revision, Course Direction, and simulated/real source.
- Add current Track and Course Direction selection state to the shared controller/storage boundary and expose it on Drive and Track detail.
- Add profile revision/archive/duplicate operations to both file and in-memory stores, then update Review indexing and details.

</code_context>

<specifics>
## Specific Ideas

- The canonical three-Sector layout is:

  `Start/Finish → Sector 1 → Boundary 1 → Sector 2 → Boundary 2 → Sector 3 → Start/Finish`

- Sector labels describe intervals; boundary labels describe timing/Split lines. The complete lap must never contain an unassigned interval.
- Real-world and simulator precedent reviewed during discussion:
  - FIA circuit maps use two intermediate timing points plus start/finish to define three complete Sectors: <https://www.fia.com/system/files/decision-document/2026_barcelona_event_-_circuit_map_-_barcelona_2026.pdf>
  - AiM stores start/finish and Split positions and derives contiguous Split/Sector analysis and theoretical best laps: <https://www.aim-sportline.com/docs/racestudio3/manual/html/tracks.html>
  - RaceChrono uses directional start/finish and intermediate traps and can recalculate recorded sessions after Track edits: <https://racechrono.com/article/1923>
  - Gran Turismo presents predefined Sector practice and combines best Sector times into a theoretical best: <https://www.gran-turismo.com/us/gt7/manual/worldcircuit/05>
- A turnaround across start/finish must not create a false short lap. Directional boundary acceptance solves that integrity problem without pausing the real lap timer.
- Course Direction is a selectable variant under the same Track revision, not a geometry edit. Reverse direction reuses physical boundary positions but reverses progress, accepted crossings, and Sector order.

</specifics>

<deferred>
## Deferred Ideas

- Recommend nearby locally saved Tracks using live latitude/longitude, requiring confirmation before changing the current Track.
- Match the user's coordinates against Tracks saved or shared by other users.
- Cloud/shared Track libraries and automatic community Track matching.
- Map-tile backgrounds, telemetry charts, external GNSS, and Meta glasses output remain in their later roadmap phases/backlog.

</deferred>

---

*Phase: 05-Track Setup and Course Profiles*
*Context gathered: 2026-06-26*
