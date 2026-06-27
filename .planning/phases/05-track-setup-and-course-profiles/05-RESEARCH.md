# Phase 5: Track Setup and Course Profiles - Research

**Researched:** 2026-06-26  
**Domain:** Kotlin Multiplatform course-profile persistence, closed-loop geometry, lap/sector timing, and ghost compatibility  
**Confidence:** HIGH for codebase architecture and schema risks; MEDIUM for geometry defaults; LOW for initial distance thresholds pending real-track calibration

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions

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

### Deferred Ideas (OUT OF SCOPE)

- Recommend nearby locally saved Tracks using live latitude/longitude, requiring confirmation before changing the current Track.
- Match the user's coordinates against Tracks saved or shared by other users.
- Cloud/shared Track libraries and automatic community Track matching.
- Map-tile backgrounds, telemetry charts, external GNSS, and Meta glasses output remain in their later roadmap phases/backlog.
</user_constraints>

<phase_requirements>
## Phase Requirements

The roadmap has no new formal requirement IDs yet. The following planning IDs make all four success criteria and D-01 through D-24 traceable as required by the phase brief. [VERIFIED: `.planning/ROADMAP.md` Phase 5 and `05-CONTEXT.md`]

| ID | Description | Research Support |
|----|-------------|------------------|
| SC-01 | Save named Track/course profiles. | Stable profile aggregate, explicit repository operations, current-selection state, migration plan. [VERIFIED: `05-CONTEXT.md` D-01..D-04, D-12..D-16] |
| SC-02 | Edit start/finish and optional Sectors. | Closed-polyline arc-length model, constrained editor, generated normal boundaries, complete-interval sector engine. [VERIFIED: `05-CONTEXT.md` D-05..D-11] |
| SC-03 | Reuse prior course setup for new sessions. | Persisted current profile/direction and immutable session `CourseSnapshot`. [VERIFIED: `05-CONTEXT.md` D-01..D-03, D-12..D-14] |
| SC-04 | Detect obvious wrong-course or wrong-direction use. | Conservative preflight plus explicit directional boundary acceptance; no global direction state. [VERIFIED: `05-CONTEXT.md` D-17, D-21..D-24] |
| D-01..D-04 | Explicit current Track selection and reuse. | `CurrentTrackSelection`, active-profile resolver, Drive selector, no migration fallback. [VERIFIED: `05-CONTEXT.md`] |
| D-05 | Editable confirmed start/finish. | Store its reference-line placement and derive finite endpoints; save only a valid confirmed boundary. [VERIFIED: `05-CONTEXT.md`] |
| D-06..D-07 | Complete coverage; disabled or 2–6 Sectors, default 3. | Separate intermediate boundaries from derived interval definitions; enforce count centrally. [VERIFIED: `05-CONTEXT.md`] |
| D-08..D-10 | Equal arc placement, normal generation, constrained dragging. | `ClosedReferencePath` cumulative-length table, smoothed tangent, nearest-point projection, cyclic spacing clamp. [VERIFIED: `05-CONTEXT.md`] |
| D-11 | Direction-derived order; duration separate from cumulative Split. | Direction-specific `CourseDefinition` and `SectorResult(durationMillis, cumulativeSplitMillis)`. [VERIFIED: `05-CONTEXT.md`] |
| D-12..D-14 | Stable profile and immutable revisions/history. | One atomic profile aggregate containing immutable revisions and mutable non-geometric metadata. [VERIFIED: `05-CONTEXT.md`] |
| D-15 | Sector-only Ghost compatibility. | Explicit `geometryCompatibilityId` copied only by sector-only revisions. [VERIFIED: `05-CONTEXT.md`] |
| D-16 | Archive and duplicate behavior. | Repository operations preserve payloads; archive clears matching current selection, duplicate creates new profile and compatibility IDs. [VERIFIED: `05-CONTEXT.md`] |
| D-17 | No continuous wrong-direction state. | Direction is boundary configuration and progress transform, not a runtime pause state. [VERIFIED: `05-CONTEXT.md`] |
| D-18..D-19 | Recorded/reverse variants and isolated history. | `CourseDirection` plus compatibility key `(profile, geometry, direction, source)`. [VERIFIED: `05-CONTEXT.md`] |
| D-20..D-21 | Never pause for reversing; reject opposite boundary crossings. | Lap clock/raw recording remain independent; every boundary has an explicit accepted approach side. [VERIFIED: `05-CONTEXT.md`] |
| D-22..D-23 | Whole-course preflight and recorded override. | Point-to-closed-polyline distance with uncertainty margin and persisted `CoursePreflightSnapshot`. [VERIFIED: `05-CONTEXT.md`] |
| D-24 | Continue timing off-course; suppress/resume Ghost only. | Reference-line matcher emits confidence independently from `LapEngine`; unavailable Ghost does not mutate lap state. [VERIFIED: `05-CONTEXT.md`] |
</phase_requirements>

## Summary

Phase 5 should be planned as a data-model and domain-engine migration with UI on top, not as a Track editor screen added to the existing `Track` class. The current `Track.id` simultaneously identifies a logical Track and one mutable geometry, `DriveMarkingController.snapshot()` picks the newest timing-ready Track, sessions and Ghosts key only by Track ID/source, and the iOS entry point uses the default in-memory store. Those facts make a storage/bootstrap foundation the first dependency. [VERIFIED: codebase `TrackModels.kt`, `DriveMarkingController.kt`, `SessionController.kt`, `FileSessionStore.kt`, `MainViewController.kt`]

Use a stable logical `TrackProfile` containing immutable `TrackRevision` records. Give each revision an explicit `geometryCompatibilityId`: copy it into sector-only revisions; create a new value when the reference line or start/finish changes. A live/saved Ghost key is then `(profileId, geometryCompatibilityId, courseDirection, isSimulated)`, never a geometry hash and never revision number alone. Sessions persist both that identity and an immutable `CourseSnapshot`, so Review remains correct if a profile is archived or later revised. [VERIFIED: `05-CONTEXT.md` D-12..D-19] [ASSUMED]

Replace line-centric Sector state at the lap-engine boundary. Persist `N-1` intermediate boundary placements, derive `N` complete intervals for the selected direction, and emit a `SectorResult` only when an interval closes. The start/finish crossing closes the final Sector and lap together. `durationMillis` and `cumulativeSplitMillis` must be separate fields. Existing V1 session events remain legacy cumulative line splits; preserve them rather than silently relabeling them as complete Sectors. [VERIFIED: codebase `LapModels.kt`, `LapEngine.kt`, `SessionModels.kt`; `05-CONTEXT.md` D-06..D-11]

Build one pure shared `ClosedReferencePath` primitive and reuse it for editor placement, direction transforms, point-to-course preflight distance, and live Ghost matching. This prevents four subtly different implementations of closed-loop wrapping and nearest-segment projection. Compose should only convert pointer coordinates through a reusable viewport transform and forward a candidate point/progress to the pure editor state. [CITED: https://developer.android.com/develop/ui/compose/touch-input/pointer-input] [VERIFIED: `AGENTS.md`]

**Primary recommendation:** implement V2 profile/migration/current-selection storage first, then the shared closed-path/course builder, then direction-aware complete-interval timing, and only then revision UI, Ghost compatibility, preflight, and full replay/UAT integration. [ASSUMED]

## Architectural Responsibility Map

| Capability | Primary Tier | Secondary Tier | Rationale |
|------------|--------------|----------------|-----------|
| Profile/revision/current-selection persistence | Database / Storage | Shared domain | App-private JSON is the source of truth; UI must call typed repository operations. [VERIFIED: codebase `LocalSessionStore.kt`, `StoragePaths.*.kt`] |
| V1→V2 migration | Database / Storage | Shared domain | Version dispatch, validation, and idempotent writes belong at the file boundary. [VERIFIED: codebase `FileSessionStore.kt`] |
| Arc length, tangents, nearest-point projection, direction transform | Shared domain (API/backend equivalent) | — | Algorithms must be platform/UI independent and replay-testable. [VERIFIED: `AGENTS.md`] |
| Offline boundary editing | Browser / Client (Compose UI) | Shared domain | Compose owns gestures/rendering; pure editor state owns snapping, constraints, and validation. [CITED: https://developer.android.com/develop/ui/compose/touch-input/pointer-input] |
| Direction-specific course construction | Shared domain | Storage | The engine consumes one immutable derived `CourseDefinition`; persisted revision/direction are inputs. [VERIFIED: codebase `TimingLines.kt`, `SessionController.kt`] |
| Lap and complete Sector timing | Shared domain | — | Timing must remain independent from Compose, storage, Android, and iOS. [VERIFIED: `AGENTS.md`; codebase `LapEngine.kt`] |
| Ghost compatibility and progress confidence | Shared domain | Storage | Compatibility is identity policy; confidence is realtime geometry; storage only resolves the matching reference. [VERIFIED: codebase `LiveDeltaEngine.kt`, `ReferenceLapSelector.kt`] |
| Wrong-course preflight | Shared domain | Compose UI | Domain returns typed Ready/Blocked/Unavailable; UI presents selection/override. [VERIFIED: `05-CONTEXT.md` D-22..D-23] |
| Android/iOS app-private roots | Platform bootstrap | Database / Storage | Platforms provide the root; shared code owns file semantics. iOS currently fails to inject its file store. [VERIFIED: codebase `StoragePaths.*.kt`, `MainActivity.kt`, `MainViewController.kt`] |

## Project Constraints (from AGENTS.md)

- LapSight is a mounted-phone timing instrument for karting, track driving, and cycling, with the phone as the source of truth; it is not a generic fitness/watch dashboard. [VERIFIED: `AGENTS.md`]
- Keep Kotlin Multiplatform shared domain logic and Compose Multiplatform UI direction; platform location remains Android Fused Location Provider and iOS Core Location. [VERIFIED: `AGENTS.md`]
- Keep the lap engine independent from UI and platform APIs. [VERIFIED: `AGENTS.md`]
- Every algorithmic behavior must be testable with synthetic or recorded replay data. [VERIFIED: `AGENTS.md`]
- Preserve local-first storage and clean-room implementation. [VERIFIED: `AGENTS.md`]
- Do not copy GPL-licensed DovesLapTimer or DovesDataViewer code without an explicit compatible license decision. [VERIFIED: `AGENTS.md`]
- Keep closed-course/private-track, passive-while-moving, and no-public-road-racing safety language explicit. [VERIFIED: `AGENTS.md`]
- Do not build the Meta glasses app before reliable phone timing state; external GNSS and glasses remain v2+. [VERIFIED: `AGENTS.md`]
- Work phase-by-phase, plan before coding, keep slices vertically observable, and automate lap-engine verification. [VERIFIED: `AGENTS.md`]
- The Phase 1-only instruction to avoid advanced track/ghost work has already been superseded by completed Phases 1–4 and is not a Phase 5 scope restriction. [VERIFIED: `.planning/STATE.md`, `.planning/ROADMAP.md`]

## Standard Stack

No new dependency is needed. Reuse the resolved project stack and add pure Kotlin code. The successful `:shared:check` and `:androidApp:assembleDebug` runs on 2026-06-26 verify that these pinned artifacts resolve in this repository. [VERIFIED: `gradle/libs.versions.toml`, local Gradle build]

### Core

| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| Kotlin Multiplatform | 2.4.0 | Shared profile, geometry, timing, migration, and tests | Already owns all domain code and Android/iOS targets. [VERIFIED: `gradle/libs.versions.toml`, `shared/build.gradle.kts`] |
| kotlinx.serialization JSON | 1.11.0 | Frozen V1 DTOs, V2 DTOs, schema dispatch, canonical export | Official API supports `JsonElement`, `parseToJsonElement`, and typed decode needed for explicit version dispatch. [CITED: https://kotlinlang.org/api/kotlinx.serialization/kotlinx-serialization-json/kotlinx.serialization.json/-json/] |
| Okio | 3.17.0 | Multiplatform app-private files and fault-injected store tests | Existing store uses it; official docs describe multiplatform `FileSystem`, test substitution, and `atomicMove`. [CITED: https://square.github.io/okio/file_system/] |
| Compose Multiplatform | 1.11.1 | Drive selector, Track detail/editor, drag gestures | Already renders shared UI; pointer input exposes drag gesture detectors while domain constraints remain outside UI. [VERIFIED: `gradle/libs.versions.toml`] [CITED: https://developer.android.com/develop/ui/compose/touch-input/pointer-input] |

### Supporting

| Library | Version | Purpose | When to Use |
|---------|---------|---------|-------------|
| `kotlin.test` | 2.4.0 | Common deterministic unit/replay/migration tests | All geometry, schema, engine, store, and controller behavior. Official KMP guidance places platform-neutral tests in `commonTest`. [CITED: https://kotlinlang.org/docs/multiplatform/multiplatform-run-tests.html] |
| Existing `LocalProjection` / `TraceProjection` | repository code | Geographic↔local-meter and offline trace projection | Reuse/refactor into a bidirectional viewport transform; never persist canvas coordinates. [VERIFIED: codebase `LocalProjection.kt`, `TraceProjection.kt`] |
| Existing `CrossingDetector` / `SegmentGeometry` | repository code | Finite line intersection and interpolated timestamps | Retain the geometry primitive; replace learned direction policy with explicit per-boundary orientation. [VERIFIED: codebase `CrossingDetector.kt`, `SegmentGeometry.kt`, `LapEngine.kt`] |

### Alternatives Considered

| Instead of | Could Use | Tradeoff |
|------------|-----------|----------|
| Versioned JSON aggregate | Add SQLDelight/database | A database would improve multi-record transactions/querying, but introduces a new package and a second migration system for a small local dataset; it is unnecessary for this phase. [ASSUMED] |
| Pure Kotlin closed-polyline utility | Add a geospatial package | A package may provide more GIS operations than needed and would require legitimacy/license review; current local-meter geometry already supplies the required foundation. [VERIFIED: codebase `LocalProjection.kt`] [ASSUMED] |
| Explicit V1/V2 DTO migrators | Mutate existing `*PayloadV1` classes in place | In-place mutation can make old schema numbers decode into new meanings and prevents byte-stable legacy fixtures. [VERIFIED: codebase `SchemaVersions.kt`, `TrackPayloadV1`, `TimingSessionPayloadV1`, `GhostReferencePayloadV1`] |

**Installation:** none. Do not add dependencies in Phase 5. [ASSUMED]

## Architecture Patterns

### System Architecture Diagram

```text
Track marking / saved V1 files / Track detail actions
                    |
                    v
        +---------------------------+
        | TrackProfileRepository    |<---- app-private Android/iOS storage
        | V1 dispatch + V2 writes   |      (external platform boundary)
        +-------------+-------------+
                      |
        +-------------v--------------+
        | TrackProfile + revisions   |
        | current profile/direction  |
        +------+------+--------------+
               |      |
       edit    |      | Start Timing + latest GPS
               v      v
   +----------------+  +-------------------------+
   | ClosedReference|  | Wrong-course preflight  |
   | Path + editor  |  | distance to whole loop  |
   +-------+--------+  +------------+------------+
           |                         |
      validate/save             clearly far?
           |                    /           \
           v                 yes             no/override
   append immutable             |                 |
   TrackRevision                v                 v
                         block + explicit   Direction-specific
                         override action    CourseDefinition
                                                  |
                         +------------------------+------------------+
                         |                                           |
                         v                                           v
                 LapEngine + complete                         CourseProgressMatcher
                 Sector intervals                            + Ghost compatibility
                         |                                           |
                         +-------------------+-----------------------+
                                             v
                                  immutable CourseSnapshot
                                  + raw samples + lap/Sector
                                  + preflight override
                                             |
                                             v
                                  draft -> save -> Review/export
```

This flow keeps UI and platform services outside algorithmic state while letting one closed-path model drive editing, preflight, direction, and Ghost confidence. [VERIFIED: `AGENTS.md`] [ASSUMED]

### Recommended Project Structure

```text
shared/src/commonMain/kotlin/com/huanfuli/lapsight/shared/
├── track/
│   ├── TrackProfileModels.kt          # V2 profile/revision/course DTOs
│   ├── ClosedReferencePath.kt         # arc length, tangent, projection, wrapping
│   ├── CourseGeometryBuilder.kt       # boundaries + direction-specific intervals
│   ├── CourseProfileEditor.kt         # pure edit state, constraints, validation
│   └── TrackProfileController.kt      # select/revise/archive/duplicate operations
├── storage/
│   ├── SchemaMigrations.kt            # frozen V1 -> V2 migration functions
│   ├── TrackProfileRepository.kt      # typed profile/current-selection contract
│   └── FileSessionStore.kt            # V2 files, index rebuild, legacy dispatch
├── lap/
│   ├── TimingLines.kt                 # directional boundary + complete intervals
│   ├── LapModels.kt                   # SectorResult, cumulative split separate
│   └── LapEngine.kt                   # expected-boundary sequencing
├── ghost/
│   ├── CourseProgressMatcher.kt       # matched direction progress + confidence
│   └── GhostCompatibility.kt          # profile/geometry/direction/source key
├── session/
│   └── SessionModels.kt               # CourseSnapshot + preflight metadata
└── ui/
    ├── DriveScreen.kt                 # compact selector/preflight presentation
    ├── ReviewScreen.kt                # profile history/archive/current action
    └── TrackEditorScreen.kt           # offline trace + constrained handles

shared/src/commonTest/kotlin/com/huanfuli/lapsight/shared/
├── storage/SchemaMigrationTest.kt
├── track/ClosedReferencePathTest.kt
├── track/CourseProfileEditorTest.kt
├── lap/CompleteSectorReplayTest.kt
├── ghost/CourseProgressMatcherTest.kt
└── session/CourseProfileIntegrationTest.kt
```

These are recommended ownership boundaries; existing files can be split incrementally to avoid a broad package move. [ASSUMED]

### Pattern 1: Stable Profile Aggregate + Immutable Revisions

Use one `profiles/<profileId>.json` aggregate containing mutable profile metadata and an append-only list of immutable revisions. A metadata rename or preferred-direction change rewrites only aggregate metadata; a geometry edit appends a revision. Keeping all revisions in one small profile file makes each update a single atomic payload write and avoids cross-file revision/index transactions. [ASSUMED] [CITED: https://square.github.io/okio/file_system/]

Recommended persisted identities:

| Type | Required identity/fields | Rule |
|------|--------------------------|------|
| `TrackProfile` | `profileId`, name, created time, archived time, preferred direction, revisions | Stable identity; duplicate always gets a new `profileId`. [VERIFIED: `05-CONTEXT.md` D-12, D-16] |
| `TrackRevision` | `revisionId`, ordinal, created time, source marking ID, reference line, course setup, `geometryCompatibilityId` | Never mutate after append. [VERIFIED: `05-CONTEXT.md` D-12..D-15] |
| `CourseSetup` | confirmed start/finish placement, sector enabled/count, `N-1` boundary placements | Positions are absolute normalized progress on the recorded reference orientation. [ASSUMED] |
| `CurrentTrackSelection` | nullable `profileId` | No V1 fallback; resolve latest active revision only when starting a session. [VERIFIED: `05-CONTEXT.md` D-01..D-04, D-14] |
| `CourseSnapshot` | profile/revision/compatibility IDs, direction, reference line, finite boundary endpoints, complete Sector definitions | Persist in every session so historical Review never renders later geometry. [VERIFIED: `05-CONTEXT.md` D-14, D-23] [ASSUMED] |

Do not compute compatibility by hashing serialized floating-point geometry. Generate a compatibility ID when lap geometry changes and carry it forward unchanged for sector-only revisions. This directly encodes D-15 and is deterministic at lookup time. [VERIFIED: `05-CONTEXT.md` D-15] [ASSUMED]

### Pattern 2: Frozen DTOs and Explicit Version Dispatch

Keep every V1 DTO frozen with literal `schemaVersion = 1`; add V2 DTOs with literal `2`. Do not increment `CURRENT_*_SCHEMA_VERSION` while leaving a class named `*V1` with that constant as its default—the current code does exactly that, so a constant-only bump would serialize the old shape under a new version. [VERIFIED: codebase `SchemaVersions.kt`, `TrackModels.kt`, `SessionModels.kt`]

Parse to `JsonElement`, inspect `schemaVersion`, decode the matching frozen DTO, validate it, then map to the current domain. Official kotlinx.serialization JSON supports tree parsing and typed decode from `JsonElement`; missing non-optional fields throw, while fields with defaults can absorb additive evolution. `ignoreUnknownKeys` only handles extra fields and is not a structural migration mechanism. [CITED: https://kotlinlang.org/api/kotlinx.serialization/kotlinx-serialization-json/kotlinx.serialization.json/-json/] [CITED: https://kotlinlang.org/docs/serialization-json-configuration.html] [CITED: https://kotlinlang.org/api/kotlinx.serialization/kotlinx-serialization-core/kotlinx.serialization/-missing-field-exception/]

Migration matrix:

| Legacy payload | V2 mapping | Historical rule |
|----------------|------------|-----------------|
| `TrackPayloadV1` | `profileId = old track.id`; deterministic `revisionId = oldId + ":r1"`; deterministic geometry compatibility ID; old sector lines become intermediate boundaries when count is 1–5. [ASSUMED] | Keep original V1 file; write the V2 aggregate side-by-side and make migration idempotent. [ASSUMED] |
| `TimingSessionPayloadV1` | Default direction to Recorded; map to deterministic migrated profile/revision/geometry IDs; preserve old start/finish and lines. [ASSUMED] | Keep old `SectorEventDto` as `LegacyCumulativeSplit`; do not relabel it as a complete Sector. [VERIFIED: codebase `SectorEventDto`] |
| `GhostReferencePayloadV1` | Default to Recorded plus migrated geometry ID and existing real/simulated source slot; rebuild course-aligned progress from raw samples when valid. [ASSUMED] | If rebuilding fails, retain the legacy payload for history and report no compatible live reference; never attach it to Reverse. [ASSUMED] |
| `ReviewIndex` V1 | Rebuild V2 profile rows from canonical payload directories; retain session/marking rows. [ASSUMED] | Treat index as a cache, not migration evidence. Current code already treats corrupt index as empty. [VERIFIED: codebase `FileSessionStore.readIndex()`] |
| Current selection | `null` | Force explicit selection after upgrade; never choose newest or sole Track silently. [VERIFIED: `05-CONTEXT.md` D-01..D-04] |

### Pattern 3: One Closed-Loop Arc-Length Model

`TrackReferenceLine.points` is currently a closed loop flag plus an ordered point list; the extractor emits 64 phase-aligned points and does not append a duplicate endpoint. Therefore the path utility must include the last→first segment explicitly and discard zero-length consecutive segments. [VERIFIED: codebase `ReferenceLineExtractor.Config`, `resampleAnchored()`, `TrackReferenceLine`]

Build a cumulative meter table once:

1. Project WGS84 points to local meters with the existing `LocalProjection`. [VERIFIED: codebase `LocalProjection.kt`]
2. Include every segment and the closing segment; store cumulative start/end distances. [ASSUMED]
3. `pointAt(s)` wraps `s` modulo total length and binary-searches the segment. [ASSUMED]
4. `project(point)` checks nearest points on all finite segments and returns absolute recorded progress, lateral distance, segment ID, and ambiguity data. With 64 reference points, an O(N) scan is bounded and does not need a spatial index. [VERIFIED: codebase `ReferenceLineExtractor.Config(resampleCount = 64)`] [ASSUMED]
5. Compute a smoothed tangent from `pointAt(s + w) - pointAt(s - w)` and fall back to the nearest non-degenerate segment. [ASSUMED]

Initial tuning values, all centralized and replay-configurable:

| Parameter | Initial value | Reason |
|-----------|---------------|--------|
| Tangent half-window | `clamp(0.5% of lap length, 5 m, 15 m)` | Smooths 64-point corners without averaging a large course portion. [ASSUMED] |
| Boundary full length | `30 m` | Conservative cross-discipline starting point; RaceChrono documents 50 m as a normal full-size-circuit default, but LapSight also serves karting/cycling. [CITED: https://racechrono.com/article/1923] [ASSUMED] |
| Drag snap | `1 m` arc length | Stable persistence without visibly coarse placement. [ASSUMED] |
| Minimum cyclic spacing | `max(20 m, 2% of lap length)` | Prevents coincident/crossing handles while allowing 6 Sectors on short courses; reject a count the path cannot fit. [ASSUMED] |
| Placement validity | generated boundary intersects the reference path once at its anchor | Prevents a finite timing line from also cutting a nearby hairpin/parallel section. [ASSUMED] |

Store absolute normalized progress in the recorded orientation. For a start/finish anchor `s0` and point `s`, recorded progress is `wrap(s - s0, L)` and reverse progress is `wrap(s0 - s, L)`. Reverse direction also swaps each finite boundary's endpoints so the same accepted-side predicate represents the opposite physical crossing. [ASSUMED]

### Pattern 4: Boundaries Are Not Sectors

Model intermediate timing boundaries separately from complete intervals:

```text
Start/Finish -> Sector 1 -> Boundary 1 -> Sector 2 -> ... -> Sector N -> Start/Finish
```

For `N` configured Sectors, persist `N-1` boundary placements and derive `N` direction-specific `SectorDefinition`s. Equal generation uses `s0 + k*L/N` in Recorded and the direction transform for Reverse. FIA's Barcelona map provides the real-world three-Sector pattern of two intermediate points plus the control/start line; AiM similarly stores start/finish, split positions, and driven line as distinct Track data. [CITED: https://www.fia.com/system/files/decision-document/2026_barcelona_event_-_circuit_map_-_barcelona_2026.pdf] [CITED: https://www.aim-sportline.com/docs/racestudio3/manual/html/tracks.html]

Engine behavior:

- Require the next expected intermediate boundary; reject duplicate, opposite-direction, or out-of-order crossings without advancing Sector state. The current engine only deduplicates and does not enforce order. [VERIFIED: codebase `LapEngine.handleSectorCrossing()`]
- The first intermediate crossing emits Sector 1 duration and cumulative Split; each later crossing subtracts the previous accepted boundary time. [VERIFIED: `05-CONTEXT.md` D-11]
- The accepted start/finish crossing emits the final Sector result and the lap result at the same interpolated timestamp. [VERIFIED: `05-CONTEXT.md` D-06, D-11]
- If an intermediate boundary is missed, still complete the lap at start/finish but leave affected Sector results unavailable; D-20 forbids pausing Timing. [VERIFIED: `05-CONTEXT.md` D-20] [ASSUMED]
- Direction-gate every boundary, including the very first start/finish crossing. The current engine learns direction from its first accepted start/finish and does not direction-gate Sector boundaries, which cannot satisfy D-21. [VERIFIED: codebase `LapEngine.startFirstLap()`, `handleSectorCrossing()`]

Recommended output:

```kotlin
data class SectorResult(
    val lapNumber: Int,
    val sectorId: String,
    val sectorOrder: Int,
    val startedAtMillis: Long,
    val endedAtMillis: Long,
    val durationMillis: Long,
    val cumulativeSplitMillis: Long,
)
```

Use generated labels in the first wave and preserve stable IDs. Editable labels can follow after complete-coverage semantics are proven, as explicitly allowed by context. [VERIFIED: `05-CONTEXT.md` discretion]

### Pattern 5: Compatibility Key, Session Snapshot, and Provider Provenance

Use this value object everywhere a fastest lap or Ghost is selected:

```kotlin
data class CourseCompatibilityKey(
    val profileId: String,
    val geometryCompatibilityId: String,
    val direction: CourseDirection,
    val isSimulated: Boolean,
)
```

`revisionId` is retained for exact historical navigation but is not the Ghost key because sector-only revisions intentionally remain compatible. `profileId` remains in the key so duplicating identical geometry starts independent history. [VERIFIED: `05-CONTEXT.md` D-15, D-16, D-19]

Session provenance must come from the active location provider/run, not from the Track's marking provenance. Current `SessionController.sourceForTrack` copies `Track.source`, which would mark a real run as simulated if it uses a demo-created Track. Fix this before expanding Ghost keys. [VERIFIED: codebase `SessionController` lines 44–49]

Persist `CourseSnapshot` in both draft and saved sessions. It should include reference-line points, selected direction, generated finite boundary endpoints, complete Sector definitions, compatibility key, and preflight outcome. Review should render this snapshot first rather than load the profile's latest geometry; the current Review path loads `trackId` and therefore would render revised geometry behind an old session. [VERIFIED: codebase `ReviewScreen.TimingTraceSection()`] [ASSUMED]

### Pattern 6: Conservative Preflight, Independent Live Confidence

Preflight is a one-time typed decision before session start:

```text
latest usable fix
    -> minimum distance to all closed reference segments
    -> subtract horizontal-accuracy uncertainty
    -> clearly far? Blocked(distance, threshold) : Ready
Blocked + user action -> ReadyWithOverride(snapshot persisted)
```

Recommended initial rule: block only when `max(0, distanceToPath - accuracyMeters) > 250 m`; if coordinates are non-finite, the fix is stale, or accuracy is worse than 100 m, return `Unavailable` and let the existing GPS-readiness path decide rather than presenting a wrong-course claim. Use a 15-second freshness window. These are conservative starting values, not validated product constants. [ASSUMED]

After start, feed every raw sample to the recorder and lap engine regardless of course-match confidence. A separate `CourseProgressMatcher` returns either `Matched(directionProgress, lateralDistance, confidence)` or `Unmatched(reason)`. `LiveDeltaEngine` consumes the matched course progress; unmatched emits `--`, and the next matched sample automatically resumes delta. Do not reset lap start, accumulated session time, or raw samples. [VERIFIED: `05-CONTEXT.md` D-20, D-24]

Initial live-confidence gates:

| Gate | Initial rule |
|------|--------------|
| Coordinate/accuracy | finite coordinates and accuracy ≤ 50 m, matching the existing Ghost builder default. [VERIFIED: codebase `ProgressCurveBuilder.DEFAULT_MAX_HORIZONTAL_ACCURACY_METERS`] |
| Lateral distance | ≤ `max(25 m, 2 × accuracy)` [ASSUMED] |
| Ambiguity | second non-adjacent candidate must be at least 5 m worse, unless continuity clearly selects one [ASSUMED] |
| Continuity | reject a progress jump larger than `max(50 m, 3 × speed × Δt + 15 m)` [ASSUMED] |

Unlike the current traveled-distance accumulator, course progress may move backward when the user reverses; that does not pause processing. Delta can continue at the matched course position, or become unavailable only when matching is ambiguous. [VERIFIED: codebase `LiveDeltaEngine` currently accumulates Euclidean travel; `05-CONTEXT.md` D-20, D-24] [ASSUMED]

### Anti-Patterns to Avoid

- **Bumping version constants only:** freezes no old schema and can emit an old shape under a new number. Use literal-version DTOs and explicit dispatch. [VERIFIED: codebase `SchemaVersions.kt`]
- **Adding fields to `Track` and continuing to overwrite it:** violates immutable revision/history behavior. [VERIFIED: `05-CONTEXT.md` D-12..D-14]
- **Geometry hash as compatibility:** tiny serialization/rounding changes can fork history, while a sector edit must not. Use an explicit compatibility ID. [ASSUMED]
- **Using `revisionId` as the Ghost key:** incorrectly breaks compatibility on sector-only revisions. [VERIFIED: `05-CONTEXT.md` D-15]
- **Loading latest profile geometry in session Review:** rewrites history visually. Use the session snapshot or exact revision. [VERIFIED: `05-CONTEXT.md` D-14]
- **Treating intermediate boundaries as Sector objects:** loses the final interval and conflates duration with cumulative Split. [VERIFIED: codebase `SectorEvent`; `05-CONTEXT.md` D-06, D-11]
- **Learning direction from the first crossing:** accepts an opposite-direction first crossing. Build explicit accepted orientation from selected Course Direction. [VERIFIED: codebase `LapEngine.startFirstLap()`; `05-CONTEXT.md` D-21]
- **Dragging endpoints or storing canvas coordinates:** allows skewed boundaries and resolution-dependent persistence. Store reference progress and regenerate normal endpoints. [VERIFIED: `05-CONTEXT.md` D-09..D-10]
- **Nearest point without ambiguity/continuity checks:** nearby parallel track segments can make Ghost progress jump. [ASSUMED]
- **Wrong-course checks during a lap:** contradicts the required non-pausing behavior. [VERIFIED: `05-CONTEXT.md` D-17, D-20, D-24]
- **Choosing a Track during migration:** violates explicit selection and can silently bind timing to the wrong course. [VERIFIED: `05-CONTEXT.md` D-01..D-04]
- **Using Track marking source as session source:** can contaminate real/simulated Ghost isolation. [VERIFIED: codebase `SessionController.sourceForTrack`; `05-CONTEXT.md` D-19]

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| JSON parser/version envelope | String/regex inspection | kotlinx.serialization `JsonElement` + frozen serializers | Typed failure and multiplatform behavior already exist. [CITED: https://kotlinlang.org/api/kotlinx.serialization/kotlinx-serialization-json/kotlinx.serialization.json/-json/] |
| Crash-safe file replacement and test filesystem | Platform-specific Java/NSFileManager implementations in domain | Existing Okio `FileSystem`, temp writes, and injected fake/forwarding filesystems | Keeps one KMP store and supports fault tests. [CITED: https://square.github.io/okio/file_system/] |
| Drag recognizer | Manual raw pointer state machine | Compose `pointerInput`/`detectDragGestures` | Gesture recognition belongs to UI; constraints still belong to pure domain state. [CITED: https://developer.android.com/develop/ui/compose/touch-input/pointer-input] |
| A second geo projection | Ad hoc lat/lon-to-pixel math in editor | Existing `LocalProjection` plus a reusable bidirectional trace viewport | Canonical calculations stay in meters; canvas remains rendering-only. [VERIFIED: codebase `LocalProjection.kt`, `TraceProjection.kt`] |
| Separate editor/preflight/Ghost path math | Three nearest-polyline implementations | One `ClosedReferencePath` | Shared wrapping/projection rules prevent inconsistent edge cases at the closing segment. [ASSUMED] |
| Global reverse-driving state machine | Pause/warning logic | Direction-specific progress transform and boundary endpoint orientation | Required behavior rejects opposite crossings without stopping actual lap timing. [VERIFIED: `05-CONTEXT.md` D-17..D-21] |
| Cryptography for local compatibility IDs | Custom hashes/signatures | Injected opaque IDs plus structural validation | Compatibility is domain identity, not tamper-proofing. [ASSUMED] |

**Key insight:** this phase's complexity is identity and semantics. Existing serialization, file, gesture, projection, and line-intersection primitives are sufficient; custom work should be limited to explicit migration and pure course-domain logic. [VERIFIED: codebase audit] [ASSUMED]

## Runtime State Inventory

This is a schema-migration phase, so repository grep alone is insufficient. [VERIFIED: phase description]

| Category | Items Found | Action Required |
|----------|-------------|-----------------|
| Stored data | Android app-private `<filesDir>/lapsight/{tracks,markings,sessions,drafts,references,index.json}`; iOS storage code targets `<Documents>/lapsight`, but the current iOS entry point does not inject it. Actual device record counts are not visible from this workspace. [VERIFIED: codebase `StoragePaths.android.kt`, `StoragePaths.ios.kt`, `FileSessionStore.kt`, `MainViewController.kt`] | Add idempotent V1 readers/V2 side-by-side migration; preserve old payloads; add a pre-UAT device backup/export step. Fix iOS injection before claiming persistence. [ASSUMED] |
| Live service config | None—no cloud, database server, map service, or external Track service is in Phase 5. [VERIFIED: `05-CONTEXT.md` deferred scope; codebase grep] | None. |
| OS-registered state | None found—no scheduled tasks, services, URL handlers, or registered Track identifiers are used by the app. [VERIFIED: codebase grep, Android manifest, iOS project files] | None. |
| Secrets/env vars | None found for profile/session schema or IDs. Android SDK/JDK environment affects builds only, not saved schema. [VERIFIED: codebase grep and local environment audit] | None. |
| Build artifacts / installed packages | Generated Kotlin serializers and Android APK contain old DTO serializers until rebuilt; no globally installed LapSight package is part of migration. [VERIFIED: Gradle project structure] | Clean rebuild is optional; normal Gradle compilation regenerates serializers. Install an upgrade build over an app containing V1 fixture data for UAT. [ASSUMED] |

The canonical post-edit question is answered as follows: after every repository file changes, V1 JSON on Android devices—and any future iOS file-backed data—still exists until the app's migration path reads it. No external runtime system holds Track names or IDs. [VERIFIED: storage audit] [ASSUMED]

## Common Pitfalls

### Pitfall 1: A “backward-compatible” data class that changes old meaning

**What goes wrong:** V1 JSON decodes because new fields have defaults, but old `trackId`, Sector lines, or Ghost progress are interpreted with V2 semantics. [VERIFIED: codebase V1 shapes]  
**Why it happens:** `ignoreUnknownKeys` and defaults solve additive fields, not identity or structural meaning. [CITED: https://kotlinlang.org/docs/serialization-json-configuration.html]  
**How to avoid:** freeze V1 DTOs, dispatch by version, migrate into an explicit current domain, and test committed V1 JSON fixtures. [ASSUMED]  
**Warning signs:** tests construct Kotlin V1 objects but never load literal historical JSON; version constants appear inside V1 default properties. [VERIFIED: codebase `TrackModels.kt`, `SessionModels.kt`]

### Pitfall 2: Sector-only edits erase or fork Ghost history

**What goes wrong:** using `revisionId` or Track file path as the lookup key gives a new Ghost slot for every Sector edit. [VERIFIED: `05-CONTEXT.md` D-15]  
**Why it happens:** revision identity and lap-geometry compatibility are different concepts. [VERIFIED: `05-CONTEXT.md` D-12..D-15]  
**How to avoid:** carry `geometryCompatibilityId` across sector-only revisions and include profile, direction, and source in the key. [ASSUMED]  
**Warning signs:** reference filenames contain only revision ID, or compatibility is decided by comparing point lists at lookup time. [ASSUMED]

### Pitfall 3: The final Sector never exists

**What goes wrong:** intermediate crossings emit cumulative splits, but the last boundary→start/finish interval has no result. This is the current implementation. [VERIFIED: codebase `LapEngine.handleSectorCrossing()`, `SectorEvent`]  
**Why it happens:** a line was named `SectorLine` and its event was named a Sector event. [VERIFIED: codebase `TimingLines.kt`, `LapModels.kt`]  
**How to avoid:** derive intervals separately and close the final interval in the accepted start/finish handler. [VERIFIED: `05-CONTEXT.md` D-06, D-11]  
**Warning signs:** configured Sector count equals intermediate-boundary count, or `splitMillis` is the only timing output. [VERIFIED: codebase current DTOs]

### Pitfall 4: Reverse direction changes order but not crossing orientation

**What goes wrong:** reversed Sector labels look correct, yet opposite crossings are still accepted or the first lap starts backward. [VERIFIED: current engine learns only start/finish direction and never gates Sector direction]  
**Why it happens:** direction is treated as list reversal rather than a complete course transform. [ASSUMED]  
**How to avoid:** transform progress and swap every boundary's endpoint orientation when building Reverse `CourseDefinition`; use one explicit accepted-side predicate from the first crossing onward. [ASSUMED]  
**Warning signs:** `CourseDirection` appears only in UI/storage code or `expectedStartFinishSign` is still learned at runtime. [VERIFIED: codebase `LapEngine.kt`]

### Pitfall 5: Editor rendering and persisted geometry drift apart

**What goes wrong:** a handle looks on the trace but saves a skewed/offset line, especially after rotation or aspect-ratio changes. [ASSUMED]  
**Why it happens:** current `TraceProjection` is one-way and returns only normalized canvas points. [VERIFIED: codebase `TraceProjection.kt`]  
**How to avoid:** expose a reusable transform with screen↔local conversion; save arc progress only; regenerate tangent/normal after every drag. [VERIFIED: `05-CONTEXT.md` D-09..D-10]  
**Warning signs:** DTOs contain pixels, `Offset`, canvas width/height, or hand-edited endpoint coordinates. [ASSUMED]

### Pitfall 6: Archived/current profile resolves to a different Track

**What goes wrong:** archiving the current profile causes Drive to select the newest remaining profile. [VERIFIED: current newest-ready behavior in `DriveMarkingController.snapshot()`]  
**Why it happens:** selection is derived rather than persisted. [VERIFIED: codebase audit]  
**How to avoid:** atomically clear a matching current selection when archived and show the direct selector action; never substitute another profile. [VERIFIED: `05-CONTEXT.md` D-01..D-04, D-16]  
**Warning signs:** code still calls `maxByOrNull(createdAtEpochMillis)` in timing readiness. [VERIFIED: codebase `DriveMarkingController.kt`]

### Pitfall 7: Off-course confidence controls lap timing

**What goes wrong:** a matcher failure pauses/invalidate laps or stops raw capture. [VERIFIED: prohibited by `05-CONTEXT.md` D-20, D-24]  
**Why it happens:** progress matching and timing share one state machine. [ASSUMED]  
**How to avoid:** matcher only controls Ghost availability; `LapEngine` and recorder always receive valid raw samples. [VERIFIED: `05-CONTEXT.md` D-20, D-24]  
**Warning signs:** `LapEngine` imports matcher confidence or preflight distance. [ASSUMED]

### Pitfall 8: Historical Review follows latest geometry

**What goes wrong:** opening an old session after a revision displays the newest reference line or Sector setup. [VERIFIED: current `ReviewScreen` loads Track by session `trackId`]  
**Why it happens:** the session snapshots start/finish and lines but not the complete reference/direction/revision identity. [VERIFIED: codebase `TimingSession`]  
**How to avoid:** persist and render `CourseSnapshot`, with exact revision lookup as a metadata fallback. [ASSUMED]  
**Warning signs:** Timing Review calls `loadLatestRevision(profileId)`. [ASSUMED]

### Pitfall 9: iOS appears to work but loses profiles

**What goes wrong:** shared UI works during one run but profiles/current selection disappear on relaunch. [VERIFIED: `MainViewController()` calls `App()` with default `InMemorySessionStore`]  
**Why it happens:** `StoragePaths.ios.kt` exists but is never passed to the root app. [VERIFIED: codebase]  
**How to avoid:** inject `StoragePaths.fileSessionStore()` in `MainViewController` and add iOS cold-launch UAT. [ASSUMED]  
**Warning signs:** Android persistence tests pass while no iOS bootstrap test/UAT exists. [VERIFIED: environment audit]

### Pitfall 10: Source isolation follows Track provenance

**What goes wrong:** a real session on a Track created with the simulator is saved as simulated and competes in the wrong Ghost slot. [VERIFIED: current `SessionController.sourceForTrack`]  
**Why it happens:** Track-marking source is reused as timing-run source. [VERIFIED: codebase]  
**How to avoid:** pass active provider provenance into `startTiming` and snapshot it on the session. [ASSUMED]  
**Warning signs:** `sourceForTrack(track)` still determines session source. [VERIFIED: codebase]

## Code Examples

Verified/adapted patterns from official APIs and current domain code:

### Explicit schema dispatch

```kotlin
// Source: https://kotlinlang.org/api/kotlinx.serialization/
//         kotlinx-serialization-json/kotlinx.serialization.json/-json/
fun decodeTrackProfile(text: String): LoadResult<TrackProfile> = try {
    val element = canonicalJson.parseToJsonElement(text)
    val version = element.jsonObject["schemaVersion"]
        ?.jsonPrimitive?.intOrNull ?: 1

    val profile = when (version) {
        1 -> migrateV1(canonicalJson.decodeFromJsonElement<TrackPayloadV1>(element))
        2 -> canonicalJson.decodeFromJsonElement<TrackProfilePayloadV2>(element).profile
        else -> return LoadResult.Corrupt("unsupported schemaVersion $version")
    }
    validateProfile(profile)?.let { return LoadResult.Corrupt(it) }
    LoadResult.Loaded(profile)
} catch (e: SerializationException) {
    LoadResult.Corrupt(e.message ?: "malformed JSON")
}
```

This is an adaptation of official `JsonElement` parsing/typed decode and the repository's typed `LoadResult` pattern. [CITED: https://kotlinlang.org/api/kotlinx.serialization/kotlinx-serialization-json/kotlinx.serialization.json/-json/] [VERIFIED: codebase `FileSessionStore.load()`]

### Direction-relative progress

```kotlin
// Source: derived from locked D-18 and the existing LocalProjection model.
fun directionProgress(
    absoluteRecordedMeters: Double,
    startFinishRecordedMeters: Double,
    totalMeters: Double,
    direction: CourseDirection,
): Double {
    val delta = when (direction) {
        CourseDirection.Recorded ->
            absoluteRecordedMeters - startFinishRecordedMeters
        CourseDirection.Reverse ->
            startFinishRecordedMeters - absoluteRecordedMeters
    }
    return ((delta % totalMeters) + totalMeters) % totalMeters
}
```

The same transform must order Sector boundaries and live matched progress. [VERIFIED: `05-CONTEXT.md` D-18] [ASSUMED]

### Normal boundary generation

```kotlin
// Source: clean-room vector geometry using existing LocalPoint/LocalProjection.
fun boundaryAt(path: ClosedReferencePath, s: Double, fullLengthMeters: Double): LocalLine {
    val center = path.pointAt(s)
    val tangent = path.smoothedUnitTangentAt(s)
    val normal = LocalPoint(x = -tangent.y, y = tangent.x)
    val half = fullLengthMeters / 2.0
    return LocalLine(
        a = LocalPoint(center.x - normal.x * half, center.y - normal.y * half),
        b = LocalPoint(center.x + normal.x * half, center.y + normal.y * half),
    )
}
```

Reverse course construction swaps `a` and `b`; users drag `s`, never either endpoint. [VERIFIED: `05-CONTEXT.md` D-09, D-10, D-21] [ASSUMED]

### Complete Sector result

```kotlin
// Source: locked D-06/D-11 semantics.
fun closeSector(
    definition: SectorDefinition,
    lapStartMillis: Long,
    intervalStartMillis: Long,
    crossingMillis: Long,
): SectorResult = SectorResult(
    lapNumber = currentLapNumber,
    sectorId = definition.id,
    sectorOrder = definition.order,
    startedAtMillis = intervalStartMillis,
    endedAtMillis = crossingMillis,
    durationMillis = crossingMillis - intervalStartMillis,
    cumulativeSplitMillis = crossingMillis - lapStartMillis,
)
```

The final call occurs inside accepted start/finish completion. [VERIFIED: `05-CONTEXT.md` D-06, D-11]

## State of the Art

| Old Approach | Current Approach for Phase 5 | When Changed | Impact |
|--------------|------------------------------|--------------|--------|
| Newest timing-ready Track heuristic | Persisted explicit current profile; no fallback | Phase 5 D-01..D-04 | Prevents silent course changes. [VERIFIED: `05-CONTEXT.md`] |
| One mutable `Track` geometry | Stable profile plus immutable revision list | Phase 5 D-12..D-14 | Historical sessions can resolve exact geometry. [VERIFIED: `05-CONTEXT.md`] |
| Track ID + source Ghost key | Profile + geometry compatibility + direction + source | Phase 5 D-15/D-19 | Sector edits retain history; direction and demo remain isolated. [VERIFIED: `05-CONTEXT.md`] |
| `SectorLine` events with cumulative `splitMillis` | `N-1` boundaries deriving `N` interval results with duration and cumulative Split | Phase 5 D-06..D-11 | Final Sector exists and complete coverage is explicit. [VERIFIED: `05-CONTEXT.md`] |
| Learned start/finish direction; undirected Sector lines | Explicit direction on every generated boundary | Phase 5 D-18/D-21 | Reverse variants and first-crossing rejection become deterministic. [VERIFIED: `05-CONTEXT.md`] |
| Traveled-distance Ghost progress | Reference-line matched direction progress with confidence | Phase 5 D-18/D-24 | Ghost can suppress/resume off-course without affecting timing. [VERIFIED: codebase `LiveDeltaEngine`; `05-CONTEXT.md`] |
| Track detail loads current Track geometry | Session stores immutable course snapshot | Phase 5 D-14/D-23 | Review/export remain historically truthful. [VERIFIED: `05-CONTEXT.md`] |

**Deprecated/outdated:**

- `DriveMarkingSnapshot.timingReadyTrackId` derived with `maxByOrNull`: replace with persisted selection resolution. [VERIFIED: codebase `DriveMarkingController.kt`]
- `SectorLine` as a user-facing Sector: retain a boundary type only; complete interval is the Sector. [VERIFIED: `05-CONTEXT.md` D-06]
- `SectorEvent.splitMillis` as the only Sector output: preserve for V1 history, do not emit for new sessions. [VERIFIED: codebase `SessionModels.kt`]
- `expectedStartFinishSign` learned from first accepted crossing: replace with explicit selected-direction orientation. [VERIFIED: codebase `LapEngine.kt`; `05-CONTEXT.md` D-21]
- Ghost reference filenames `<trackId>__real|sim`: migrate to a safe encoded compatibility key that includes geometry and direction. [VERIFIED: codebase `FileSessionStore.referencePath()`; `05-CONTEXT.md` D-19]

## Assumptions Log

| # | Claim | Section | Risk if Wrong |
|---|-------|---------|---------------|
| A1 | One aggregate JSON file per profile is preferable to one file per revision for this local dataset. | Architecture Pattern 1 | A larger-than-expected revision history could make rewrites costly; profile sizes should be measured. |
| A2 | V1 Track sector lines can map to `count + 1` intermediate-boundary semantics when count is 1–5. | Migration matrix | Existing users may have intended a different meaning; preserve original V1 bytes and label migrated history explicitly. |
| A3 | Generated labels can ship before editable labels. | Pattern 4 | Product may require label editing in the first wave; domain IDs still support adding it later. |
| A4 | Tangent window, 30 m line length, 1 m snap, and minimum spacing defaults are suitable starting values. | Pattern 3 | Boundaries may be unstable or intersect adjacent course sections; replay and real-track calibration must gate release. |
| A5 | Wrong-course threshold 250 m, 100 m accuracy limit, and 15-second freshness are conservative enough. | Pattern 6 | Could block a large paddock/pit or fail to block nearby different configurations; UAT must tune. |
| A6 | Live matcher lateral, ambiguity, and continuity thresholds are suitable starting values. | Pattern 6 | False suppression or false matches may occur on parallel/hairpin geometry; replay fixtures must include those shapes. |
| A7 | Missing an intermediate boundary should preserve lap completion but leave incomplete Sector results. | Pattern 4 | Product may prefer an explicit invalid-Sector status; planner should encode this as a typed result, not infer later. |
| A8 | Side-by-side V2 files should preserve V1 files rather than overwrite them during migration. | Pattern 2 / Runtime State | Uses extra local storage but materially lowers migration data-loss risk. |
| A9 | A V1 Ghost should be rebuilt against migrated Recorded geometry when possible and never assigned to Reverse. | Pattern 2 | Old progress data may not map confidently; retain but disable live use on failure. |

## Open Questions

1. **What real-track distances should ship as defaults?**
   - What we know: the phase requires conservative behavior and deterministic tests; RaceChrono documents a 50 m full-size-circuit trap width. [VERIFIED: `05-CONTEXT.md`; CITED: https://racechrono.com/article/1923]
   - What's unclear: LapSight spans karting, full-size tracks, and cycling, and no recorded near-parallel/paddock corpus exists yet. [VERIFIED: `AGENTS.md`; codebase fixture audit]
   - Recommendation: ship centralized initial values from this research only after fixtures for a kart track, full-size circuit, pit/paddock, hairpin, and parallel straight pass. [ASSUMED]

2. **Are editable Sector labels required in the first implementation wave?**
   - What we know: context explicitly allows generated labels initially if stable IDs and semantics are correct. [VERIFIED: `05-CONTEXT.md` discretion]
   - What's unclear: no post-discussion product requirement elevates label editing. [VERIFIED: `.planning/REQUIREMENTS.md`, `.planning/ROADMAP.md`]
   - Recommendation: defer label editing until the revision/interval model is working; keep label fields in snapshots with generated defaults. [ASSUMED]

3. **How much real V1 device data exists?**
   - What we know: Android has file-backed storage; iOS currently uses in-memory storage from its entry point. [VERIFIED: codebase platform bootstrap]
   - What's unclear: this workspace cannot inspect device sandboxes or record counts. [VERIFIED: environment audit]
   - Recommendation: before migration UAT, export/copy one real Android app-private dataset and run an upgrade build over it; do not rely only on synthetic JSON. [ASSUMED]

## Environment Availability

| Dependency | Required By | Available | Version | Fallback |
|------------|-------------|-----------|---------|----------|
| Gradle wrapper | Shared tests/build | ✓ | 9.1.0 | — [VERIFIED: local `gradlew --version`] |
| JDK | Gradle/Kotlin build | ✓ | launcher 23.0.2; daemon targets compatible Java 21 | — [VERIFIED: local environment] |
| Android SDK | Android debug build | ✓ | compile/target SDK 36 configured; build succeeds | — [VERIFIED: `gradle/libs.versions.toml`, local `:androidApp:assembleDebug`] |
| ADB/device | Android on-device migration UAT | ✗ from PATH and standard local candidate | — | Use Android Studio/device host with SDK platform-tools. [VERIFIED: local environment] |
| Xcode/macOS | iOS build, persistence, cold-launch UAT | ✗ on this Windows host | — | Run on a macOS/Xcode machine; no Windows fallback. [VERIFIED: local environment] |
| kotlinx.serialization | V1/V2 migration | ✓ | 1.11.0 | — [VERIFIED: resolved `:shared:check`] |
| Okio | File store/migration fault tests | ✓ | 3.17.0 | — [VERIFIED: resolved `:shared:check`] |
| Context7 CLI/MCP | Documentation lookup only | ✗ | — | Official Kotlin/Android/Okio docs were used. [VERIFIED: local environment] |

**Missing dependencies with no fallback:**

- macOS/Xcode is required for the iOS build and cold-launch persistence gate; Windows `:shared:check` compiles iOS sources but skips the iOS simulator test execution. [VERIFIED: local build output; CITED: https://kotlinlang.org/docs/multiplatform/multiplatform-run-tests.html]

**Missing dependencies with fallback:**

- ADB is not available from the audited shell; Android Studio or explicit platform-tools path can run device UAT. [VERIFIED: local environment]

## Validation Architecture

### Test Framework

| Property | Value |
|----------|-------|
| Framework | `kotlin.test` 2.4.0 over KMP common tests; Android host runner on this machine. [VERIFIED: `shared/build.gradle.kts`, `gradle/libs.versions.toml`] |
| Config file | `shared/build.gradle.kts` [VERIFIED: codebase] |
| Quick run command | `.\gradlew.bat :shared:testAndroidHostTest --tests "com.huanfuli.lapsight.shared.<package>.<TestClass>"` [VERIFIED: local Gradle task execution pattern] |
| Full suite command | `.\gradlew.bat :shared:check` [VERIFIED: README and successful local run] |
| Android build command | `.\gradlew.bat :androidApp:assembleDebug` [VERIFIED: README and successful local run] |
| iOS gate | Run `iosSimulatorArm64Test`/Xcode app build on macOS and perform cold-launch persistence UAT. [CITED: https://kotlinlang.org/docs/multiplatform/multiplatform-run-tests.html] |

### Phase Requirements → Test Map

| Req ID | Behavior | Test Type | Automated Command | File Exists? |
|--------|----------|-----------|-------------------|-------------|
| D-01..D-04 / SC-03 | Explicit current selection persists; no newest fallback; no-selection blocks with selector action | repository/controller integration | `.\gradlew.bat :shared:testAndroidHostTest --tests "*CurrentTrackSelectionTest"` | ❌ Wave 0 |
| D-05 / D-08..D-10 / SC-02 | Arc placement, smoothed tangent, normal endpoints, drag projection, spacing, validation | unit/property-style deterministic | `.\gradlew.bat :shared:testAndroidHostTest --tests "*ClosedReferencePathTest"` | ❌ Wave 0 |
| D-06..D-11 | 0 or 2–6 complete Sectors; equal generation; final interval; separate duration/Split; missed boundary | replay/unit | `.\gradlew.bat :shared:testAndroidHostTest --tests "*CompleteSectorReplayTest"` | ❌ Wave 0 |
| D-12..D-16 / SC-01 | Revisions immutable; compatibility carried correctly; archive/duplicate/history behavior | repository/domain integration | `.\gradlew.bat :shared:testAndroidHostTest --tests "*TrackRevisionStoreTest"` | ❌ Wave 0 |
| Historical integrity | Literal V1 Track/session/Ghost/index fixtures load and migrate without modifying originals | file migration/fault injection | `.\gradlew.bat :shared:testAndroidHostTest --tests "*SchemaMigrationTest"` | ❌ Wave 0 |
| D-18..D-21 | Recorded/reverse progress/order and every boundary's crossing direction; turnaround false-short-lap prevention | replay/unit | `.\gradlew.bat :shared:testAndroidHostTest --tests "*CourseDirectionReplayTest"` | ❌ Wave 0 |
| D-15/D-19 | Ghost key isolates geometry, direction, and source but survives sector-only revision | storage/integration | `.\gradlew.bat :shared:testAndroidHostTest --tests "*GhostCompatibilityTest"` | ❌ Wave 0 |
| D-22..D-23 / SC-04 | Whole-loop preflight, pit/paddock allowance, clear-far block, override persisted/reviewed | replay/controller | `.\gradlew.bat :shared:testAndroidHostTest --tests "*WrongCoursePreflightTest"` | ❌ Wave 0 |
| D-20/D-24 | Off-course/ambiguous match shows `--`; timing/raw capture continue; delta resumes on rematch | replay/integration | `.\gradlew.bat :shared:testAndroidHostTest --tests "*CourseProgressMatcherTest"` | ❌ Wave 0 |
| Cross-platform persistence | iOS entry point injects file store and profile survives relaunch | platform smoke/UAT | macOS Xcode + iOS simulator/device | ❌ Wave 0/manual gate |
| Export/Review regression | Old and V2 sessions export/render their own geometry and sector semantics | integration | `.\gradlew.bat :shared:testAndroidHostTest --tests "*JsonExportTest" --tests "*ReviewSummaryTest"` | ✅ extend existing |

### Required Deterministic Fixtures

- Closed oval with known perimeter and equal Sector points. [ASSUMED]
- Same oval in reverse with identical physical boundaries. [ASSUMED]
- Turnaround across start/finish and an intermediate boundary; no false lap/Sector. [VERIFIED: `05-CONTEXT.md` D-20..D-21]
- Pit/paddock position near but not on the reference line. [VERIFIED: `05-CONTEXT.md` D-22]
- Clearly different/far course with override. [VERIFIED: `05-CONTEXT.md` D-22..D-23]
- Hairpin/parallel straights creating nearest-segment ambiguity. [ASSUMED]
- Temporary off-course excursion followed by reliable rematch. [VERIFIED: `05-CONTEXT.md` D-24]
- Missed intermediate boundary with valid start/finish lap completion. [ASSUMED]
- V1 Track/session/Ghost/index JSON captured from the current serializer, plus malformed/unknown/newer-version cases. [VERIFIED: current schema]

### Sampling Rate

- **Per task commit:** run the new focused test class plus directly affected existing class. [ASSUMED]
- **Per wave merge:** `.\gradlew.bat :shared:check`. [VERIFIED: existing workflow]
- **UI/storage waves:** also run `.\gradlew.bat :androidApp:assembleDebug`. [VERIFIED: existing workflow]
- **Phase gate:** full suite and Android build green, iOS macOS build/test green, migration upgrade UAT complete, and deterministic replay matrix green before `$gsd-verify-work`. [VERIFIED: `AGENTS.md`, `.planning/config.json`] [ASSUMED]

### Wave 0 Gaps

- [ ] `shared/src/commonTest/.../storage/SchemaMigrationTest.kt` plus literal V1 resource fixtures.
- [ ] `shared/src/commonTest/.../storage/CurrentTrackSelectionTest.kt`.
- [ ] `shared/src/commonTest/.../track/ClosedReferencePathTest.kt`.
- [ ] `shared/src/commonTest/.../track/CourseProfileEditorTest.kt`.
- [ ] `shared/src/commonTest/.../lap/CompleteSectorReplayTest.kt`.
- [ ] `shared/src/commonTest/.../lap/CourseDirectionReplayTest.kt`.
- [ ] `shared/src/commonTest/.../ghost/GhostCompatibilityTest.kt`.
- [ ] `shared/src/commonTest/.../ghost/CourseProgressMatcherTest.kt`.
- [ ] `shared/src/commonTest/.../session/WrongCoursePreflightTest.kt`.
- [ ] Platform smoke coverage or explicit UAT script for iOS file-store injection.

No test framework installation is needed. [VERIFIED: successful `:shared:check`]

### UAT Risks and Required Checks

1. Upgrade over V1 Android data; verify old Tracks, sessions, exports, and Ghost references remain available before creating any V2 revision. [ASSUMED]
2. Relaunch after selecting Track/direction; verify exact selection returns and no newest Track substitution occurs. [VERIFIED: `05-CONTEXT.md` D-01..D-04]
3. Configure 2, 3, and 6 Sectors; verify visible complete coverage and final Sector timing. [VERIFIED: `05-CONTEXT.md` D-06..D-11]
4. Create a sector-only revision and a start/finish revision; verify only the latter resets Ghost/fastest history. [VERIFIED: `05-CONTEXT.md` D-15]
5. Drive Recorded and Reverse fixtures; verify ordering, crossing acceptance, and isolated Ghosts. [VERIFIED: `05-CONTEXT.md` D-18..D-21]
6. Archive current profile; verify selection clears, history remains, and no replacement is selected. [VERIFIED: `05-CONTEXT.md` D-01, D-16]
7. Trigger far-course block, override, save, and verify Review identifies the override. [VERIFIED: `05-CONTEXT.md` D-22..D-23]
8. Leave and rejoin the course during a timed lap; verify lap/raw sample counts continue while delta changes `value → -- → value`. [VERIFIED: `05-CONTEXT.md` D-20, D-24]
9. Cold-launch iOS after profile selection and after a revision; verify file-backed state survives. [VERIFIED: current iOS bootstrap gap]
10. Confirm closed-course/private-track and passive-while-moving language remains visible; editor interactions occur before Timing, not on the moving dash. [VERIFIED: `AGENTS.md`]

## Security Domain

Security enforcement is enabled because `.planning/config.json` does not set `security_enforcement` to false. [VERIFIED: `.planning/config.json`]

OWASP ASVS 5.0.0 is the current stable release, but LapSight is an offline mobile app rather than a web service; controls below apply by analogy to local data integrity and input handling. [CITED: https://owasp.org/www-project-application-security-verification-standard/] [ASSUMED]

### Applicable ASVS Categories

| ASVS Category | Applies | Standard Control |
|---------------|---------|-----------------|
| V2 Authentication | no | No accounts/cloud/authentication in scope. [VERIFIED: `05-CONTEXT.md` deferred scope] |
| V3 Session Management | no (web sessions) | TimingSession is domain data, not an authentication session. [VERIFIED: codebase `TimingSession`] |
| V4 Access Control | limited | App-private Android/iOS storage roots; no shared/cloud authorization boundary. [VERIFIED: codebase `StoragePaths.*.kt`] |
| V5 Input Validation | yes | Validate IDs, finite coordinates, list sizes, revision relationships, enum/version values, boundary counts, and geometry before use. [VERIFIED: current typed validation pattern in `FileSessionStore`] |
| V6 Cryptography | no new control | No secrets or network transport; do not invent crypto for compatibility IDs. Platform app-private storage remains the boundary. [VERIFIED: phase scope] [ASSUMED] |
| Safe deserialization / data integrity | yes | Strict version dispatch, frozen serializers, structural validation, typed corruption result, no polymorphic arbitrary type loading. [CITED: https://kotlinlang.org/docs/serialization-json-configuration.html] |

### Known Threat Patterns for Local KMP JSON

| Pattern | STRIDE | Standard Mitigation |
|---------|--------|---------------------|
| Corrupt/partial migration loses history | Tampering / Denial of Service | Decode and validate all source payloads first; payloads before index; idempotent side-by-side writes; retain V1 originals; fault-injected Okio tests. [CITED: https://square.github.io/okio/file_system/] [ASSUMED] |
| Path traversal through IDs | Tampering | Generate internal opaque IDs; reject `/`, `\`, `..`, control characters; never use user Track names in canonical paths. [ASSUMED] |
| Non-finite/huge geometry payload | Denial of Service | Reject NaN/infinite coordinates, empty/oversized point lists, non-increasing revisions, invalid sector counts, and unreasonable file/list sizes before geometry work. [VERIFIED: current store already checks some non-finite marking coordinates] [ASSUMED] |
| Unknown future schema interpreted as current | Tampering | Return `LoadResult.Corrupt("unsupported schemaVersion")`; never coerce a newer version down. [VERIFIED: current store pattern] |
| Compatibility collision/misbinding | Spoofing | Validate profile/revision/compatibility/direction/source fields agree across session and Ghost payload; use full value-object equality. [ASSUMED] |
| Demo data contaminates real history | Tampering | Derive source from active provider, include it in compatibility key and filename, validate requested source against payload. [VERIFIED: current defense-in-depth source check; `05-CONTEXT.md` D-19] |
| Stale current selection points to archived/corrupt profile | Denial of Service | Return typed unavailable selection and require explicit user action; never choose another profile. [VERIFIED: `05-CONTEXT.md` D-01..D-04, D-16] |
| Malformed coordinates crash preflight/matcher | Denial of Service | Typed `Unavailable`/`Unmatched` results; no exceptions in realtime path; retain timing/raw capture. [VERIFIED: established `LiveDeltaSnapshot.Unavailable` pattern] |

## Recommended Implementation Sequencing

1. **Wave 0 — characterization and literal migration fixtures.** Freeze current V1 JSON for Track, index, TimingSession, draft, and Ghost; add tests proving current behavior and identifying legacy line-split semantics. This is the rollback net before model edits. [VERIFIED: current schemas] [ASSUMED]
2. **Storage/bootstrap vertical slice.** Add V2 profile aggregate/current selection, explicit repository operations, migration dispatch, index rebuild, and iOS file-store injection. Expose a Drive selector and Track-detail `Set as current`; remove newest-ready fallback. Observable result: selection survives relaunch and Timing blocks without it. [VERIFIED: `05-CONTEXT.md` D-01..D-04] [ASSUMED]
3. **Closed-path/editor vertical slice.** Build/test `ClosedReferencePath`, viewport inverse transform, start/finish placement, equal Sector generation, constrained drag, and validation; save an immutable revision. Observable result: offline Track detail edits and version history. [VERIFIED: `05-CONTEXT.md` D-05, D-08..D-10, D-12..D-14] [ASSUMED]
4. **Direction + complete Sector engine slice.** Derive Recorded/Reverse course definitions, explicitly orient every boundary, refactor Sector results, snapshot course into drafts/sessions, and update live/Review output. Observable result: 2–6 complete Sector times and cumulative Splits in replay. [VERIFIED: `05-CONTEXT.md` D-06..D-11, D-18, D-21] [ASSUMED]
5. **Revision lifecycle slice.** Add metadata rename, archive, duplicate, latest active revision selection, and historical revision navigation. Observable result: archive removes profile from Drive selector without deleting history; duplicate has independent history. [VERIFIED: `05-CONTEXT.md` D-12..D-16] [ASSUMED]
6. **Ghost compatibility/progress slice.** Replace Track-only keys, fix provider provenance, migrate/rebuild V1 references, add course-progress matching and Reverse isolation. Observable result: sector-only revision keeps Ghost; geometry/direction/source changes do not. [VERIFIED: `05-CONTEXT.md` D-15, D-18..D-20, D-24] [ASSUMED]
7. **Preflight/integration/UAT slice.** Add whole-course distance decision, override persistence/Review badge, far/off-course fixtures, Android upgrade UAT, and macOS/iOS persistence gate. Observable result: obvious far Track blocks once, override works, and live timing continues through Ghost suppression. [VERIFIED: `05-CONTEXT.md` D-22..D-24] [ASSUMED]

Do not combine migration, Sector semantic replacement, and Ghost matcher replacement in one plan; each changes persisted meaning and needs an independently green replay/storage gate. [ASSUMED]

## Sources

### Primary (HIGH confidence)

- `05-CONTEXT.md` — all D-01 through D-24 locked decisions, discretion, and deferred scope.
- `AGENTS.md` — clean-room, shared-domain, replay, safety, and phone-first constraints.
- Current LapSight codebase — persisted V1 DTOs, store layout, lap/Sector semantics, Ghost logic, UI selection, Review lookup, and platform bootstrap.
- [Kotlin serialization JSON API](https://kotlinlang.org/api/kotlinx.serialization/kotlinx-serialization-json/kotlinx.serialization.json/-json/) — `JsonElement`, tree parsing, and typed decode.
- [Kotlin JSON configuration](https://kotlinlang.org/docs/serialization-json-configuration.html) — defaults, unknown keys, coercion, and name mapping.
- [Kotlin MissingFieldException API](https://kotlinlang.org/api/kotlinx.serialization/kotlinx-serialization-core/kotlinx.serialization/-missing-field-exception/) — missing required field behavior.
- [Okio FileSystem](https://square.github.io/okio/file_system/) — multiplatform filesystem, fake/fault testing, and atomic move.
- [Kotlin Multiplatform testing](https://kotlinlang.org/docs/multiplatform/multiplatform-run-tests.html) — common and target-specific test architecture.
- [Compose pointer input](https://developer.android.com/develop/ui/compose/touch-input/pointer-input) — drag gesture APIs and abstraction levels.
- [OWASP ASVS](https://owasp.org/www-project-application-security-verification-standard/) — current stable ASVS release and security-control framework.

### Secondary (MEDIUM confidence)

- [AiM RaceStudio 3 Tracks](https://www.aim-sportline.com/docs/racestudio3/manual/html/tracks.html) — Track model separation of start/finish, split positions, and driven line.
- [RaceChrono custom Track tutorial](https://racechrono.com/article/1923) — directional traps, editing after recording, and documented full-size default width.
- [FIA Barcelona circuit map](https://www.fia.com/system/files/decision-document/2026_barcelona_event_-_circuit_map_-_barcelona_2026.pdf) — three complete Sectors separated by two intermediate timing points and control/start line.

### Tertiary (LOW confidence)

- None. Unverified tuning choices are explicitly marked `[ASSUMED]` and listed in the Assumptions Log.

## Metadata

**Confidence breakdown:**

- Standard stack: HIGH — no new dependency; versions are pinned and resolved by successful local builds. [VERIFIED: local build]
- Schema/migration architecture: HIGH — based on exact V1 DTO/store behavior and official serialization APIs. [VERIFIED: codebase] [CITED: https://kotlinlang.org/api/kotlinx.serialization/kotlinx-serialization-json/kotlinx.serialization.json/-json/]
- Sector/direction architecture: HIGH — locked semantics plus exact current engine gaps. [VERIFIED: `05-CONTEXT.md`, codebase]
- Closed-path/editor algorithm: MEDIUM — standard clean-room geometry built on existing projection, but tuning needs fixture/real-track validation. [ASSUMED]
- Wrong-course/live matcher thresholds: LOW — intentionally conservative initial values without real LapSight course corpus. [ASSUMED]
- Pitfalls: HIGH — most are directly observed in current schemas/call paths; geometry calibration risks are marked assumed. [VERIFIED: codebase]

**Research date:** 2026-06-26  
**Valid until:** 2026-07-26 for architecture; retune thresholds after the first real-track Phase 5 dataset.
