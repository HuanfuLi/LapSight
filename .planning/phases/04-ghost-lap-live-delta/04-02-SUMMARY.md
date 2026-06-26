---
phase: 04-ghost-lap-live-delta
plan: 02
subsystem: ghost-delta
tags: [kotlin-multiplatform, ghost, reference-lap, storage, timing-session, tdd]

# Dependency graph
requires:
  - phase: 04-ghost-lap-live-delta
    plan: 01
    provides: ProgressCurveBuilder, LiveDeltaEngine, ReferenceLap/ProgressCurve/ProgressPoint domain models
  - phase: 03-local-sessions-review-and-export
    provides: TimingSession/Track split, SessionController/TimingSessionRecorder lifecycle, LocalSessionStore (File + InMemory)
provides:
  - GhostReferencePayloadV1 + ProgressPointDto serializable reference payloads (raw samples + progress curve)
  - LocalSessionStore.loadReferenceLap/saveReferenceLap with real/simulated source isolation per Track
  - ReferenceLapSelector (pure) — build a ReferenceLap from a completed lap window, pick the faster reference
  - Recorder-owned LiveDeltaEngine wiring with same-session active-reference updates
  - SessionController.liveDelta()/activeReference() read-only accessors; Save-only global reference promotion
affects: [04-03 live delta UI, 04-04 variable-pace UAT]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "Source-slotted persistence: real and simulated references stored at separate keys/paths and boundary-validated on load"
    - "Streaming reference replacement: recorder rebuilds a candidate reference per completed lap and swaps only on a strict improvement"
    - "Payload-derived promotion: global reference rebuilt from the saved payload's fastest lap so Save/Resume share one code path"

key-files:
  created:
    - shared/src/commonMain/kotlin/com/huanfuli/lapsight/shared/ghost/ReferenceLapSelector.kt
    - shared/src/commonTest/kotlin/com/huanfuli/lapsight/shared/storage/GhostReferenceStoreTest.kt
    - shared/src/commonTest/kotlin/com/huanfuli/lapsight/shared/session/TimingGhostIntegrationTest.kt
  modified:
    - shared/src/commonMain/kotlin/com/huanfuli/lapsight/shared/session/SessionModels.kt
    - shared/src/commonMain/kotlin/com/huanfuli/lapsight/shared/session/SessionController.kt
    - shared/src/commonMain/kotlin/com/huanfuli/lapsight/shared/session/TimingSessionRecorder.kt
    - shared/src/commonMain/kotlin/com/huanfuli/lapsight/shared/storage/SchemaVersions.kt
    - shared/src/commonMain/kotlin/com/huanfuli/lapsight/shared/storage/LocalSessionStore.kt
    - shared/src/commonMain/kotlin/com/huanfuli/lapsight/shared/storage/FileSessionStore.kt
    - shared/src/commonMain/kotlin/com/huanfuli/lapsight/shared/storage/InMemorySessionStore.kt

key-decisions:
  - "GhostReferencePayloadV1 carries SourceMetadata (not a bare Boolean) as the single source of truth for the real/simulated boundary; the domain ReferenceLap.isSimulated maps to source.isSimulated"
  - "References are stored in their own source slot (references/<trackId>__real|sim.json) AND the requested boundary is re-validated on load — defense in depth against a misplaced demo payload satisfying a real lookup"
  - "Global reference promotion is rebuilt from the saved payload (not recorder in-memory state) so Save and Resume reuse one path and a discarded session can never promote"
  - "fasterOf prefers the incumbent on ties so an equal new lap never churns the active/persisted reference"

patterns-established:
  - "Pattern: ReferenceLapSelector stays pure (LocationSample + LapEvent only); session/storage layers own boundary + persistence so the ghost package keeps zero storage dependency"
  - "Pattern: recorder drives LiveDeltaEngine through the same onSample path used for checkpoints, resetting delta at each lap boundary via currentLapNumber change detection"

requirements-completed: [GHOST-01, GHOST-02, GHOST-04]

# Metrics
duration: 9min
completed: 2026-06-26
---

# Phase 4 Plan 02: Ghost Reference Storage and Timing Integration Summary

**Persisted full ghost reference laps (raw samples + progress curve) with real/simulated source isolation per Track, and wired the formal timing recorder to load the saved Track's fastest reference, update the active reference the instant a faster lap completes, and promote the best eligible lap to global storage only on explicit Save.**

## Performance

- **Duration:** ~9 min
- **Started:** 2026-06-26T03:36:59Z
- **Completed:** 2026-06-26T03:46:07Z
- **Tasks:** 3
- **Files:** 3 created, 7 modified

## Accomplishments
- TDD Wave 0 tests (RED) describing reference persistence, schema/boundary validation, and the full timing reference lifecycle (D-01..D-05, D-12, D-24; T-04-04/T-04-05/T-04-06).
- `GhostReferencePayloadV1` + `ProgressPointDto` canonical, versioned payloads that preserve BOTH the raw best-lap samples and the precomputed progress curve (D-05), with `ReferenceLap` ↔ payload mappers.
- `LocalSessionStore.loadReferenceLap/saveReferenceLap` implemented in both `FileSessionStore` (atomic JSON at `references/<trackId>__real|sim.json`) and `InMemorySessionStore`, with typed `NotFound`/`Corrupt` results so a missing/malformed reference never blocks timing (T-04-05).
- Real and simulated references kept in separate slots and re-validated against the requested boundary on load, so a demo lap can never satisfy a real Track lookup (D-04, D-24, T-04-04).
- Pure `ReferenceLapSelector` that slices a completed lap's window from the session samples, builds its progress curve via `ProgressCurveBuilder`, and chooses the faster reference (incumbent wins ties).
- `TimingSessionRecorder` now owns a `LiveDeltaEngine`: it loads the persisted reference at start, resets the delta at each lap boundary, and replaces the active reference the moment a strictly faster valid lap completes (D-02, D-12).
- `SessionController` loads the saved Track's reference on start and resume, exposes read-only `liveDelta()`/`activeReference()` (production UI no longer needs `recorderForTest()`), and promotes the fastest eligible lap to global storage only on `saveStoppedDraft()` — `discardDraft()` leaves global reference untouched, and a simulated session never updates the real slot (T-04-06).

## Task Commits

Each task was committed atomically:

1. **Task 1: Wave 0 failing tests** — `9ee427f` (test)
2. **Task 2: Full reference-lap payload storage** — `453f9b6` (feat)
3. **Task 3: Reference selection + same-session best updates** — `cedf0bd` (feat)

_TDD plan gate: RED `test(04-02)` commit (`9ee427f`) precedes the GREEN `feat(04-02)` commits (`453f9b6`, `cedf0bd`)._

## Files Created/Modified
- `shared/.../ghost/ReferenceLapSelector.kt` (new) — pure builder/selector: lap-window slicing, progress-curve construction, `fasterOf` tie-stable comparison.
- `shared/.../session/SessionModels.kt` — `ProgressPointDto`, `GhostReferencePayloadV1`, and the `ProgressPoint`/`ReferenceLap` ↔ DTO mappers.
- `shared/.../session/TimingSessionRecorder.kt` — owns a `LiveDeltaEngine`; per-completed-lap reference rebuild + strict-improvement swap; lap-boundary delta reset; read-only `liveDelta`/`activeReference`.
- `shared/.../session/SessionController.kt` — reference load on start/resume, Save-only global promotion, `liveDelta()`/`activeReference()` accessors, payload-derived reference rebuild.
- `shared/.../storage/SchemaVersions.kt` — `CURRENT_GHOST_REFERENCE_SCHEMA_VERSION`.
- `shared/.../storage/LocalSessionStore.kt` — `loadReferenceLap`/`saveReferenceLap` contract.
- `shared/.../storage/FileSessionStore.kt` / `InMemorySessionStore.kt` — source-slotted reference persistence with boundary + schema validation.
- `shared/.../storage/GhostReferenceStoreTest.kt` (new) — 7 tests: round trip, NotFound/Corrupt, real/sim isolation (file + memory), per-Track scoping.
- `shared/.../session/TimingGhostIntegrationTest.kt` (new) — 6 tests: reference load on start, first-lap-unavailable→following-lap-reference, faster-lap replacement, Save-persists/Discard-does-not, simulated-session-isolation.

## Decisions Made
- The reference payload carries `SourceMetadata` as the single boundary source of truth; `ReferenceLap.isSimulated` maps onto `source.isSimulated`, keeping the pure ghost model decoupled from storage's source type.
- Storage isolates real/simulated references by slot AND re-validates the requested boundary on load (defense in depth).
- Global-reference promotion rebuilds the reference from the saved payload, so Save and Resume share one path and a discarded faster lap can never leak into global storage.
- `fasterOf` prefers the incumbent on equal durations, avoiding needless reference churn/rewrites.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] Missing worktree `local.properties` (Android SDK location)**
- **Found during:** Task 1 (running the RED verification)
- **Issue:** The git worktree had no `local.properties`, so `:shared:testAndroidHostTest` failed with "SDK location not found" before any test could compile/run.
- **Fix:** Copied the main checkout's gitignored `local.properties` (`sdk.dir`) into the worktree root. Not committed (gitignored).
- **Files modified:** `local.properties` (untracked, local-only)
- **Committed in:** N/A (gitignored environment file)

**2. [Rule 3 - Blocking] File-private `InMemoryFileSystem` name collision in commonTest**
- **Found during:** Task 1 (running the RED verification)
- **Issue:** `FileSessionStoreTest` already declares a file-private top-level `InMemoryFileSystem` in the same `storage` package. Kotlin's compiler treated the two file-private declarations as conflicting, producing "it is private in file" errors that masked the intended missing-production-symbol RED.
- **Fix:** Renamed this plan's test helper to `GhostInMemoryFileSystem`.
- **Files modified:** `shared/.../storage/GhostReferenceStoreTest.kt`
- **Committed in:** `9ee427f` (Task 1)

**3. [Rule 3 - Blocking] Whole `commonTest` source set compiles as one unit**
- **Found during:** Task 2 (intending to run only `*GhostReferenceStoreTest*`)
- **Issue:** `TimingGhostIntegrationTest` (committed in Task 1) references Task 3 production symbols (`ReferenceLapSelector`, `SessionController.activeReference()`/`liveDelta()`, recorder `initialReference`). Because Kotlin compiles `commonTest` as a single unit, no targeted test could compile until the Task 3 production code existed.
- **Fix:** Wrote Task 3 production code before running verification, then ran the combined targeted suite (and the full host suite) once — both Task 2 and Task 3 verifications pass against the same green run. Tasks remain separate atomic commits. This mirrors the documented precedent from Plan 04-01 (deviation #2).
- **Files modified:** none beyond the planned Task 3 files.
- **Committed in:** `453f9b6` (Task 2 storage), `cedf0bd` (Task 3 integration)

---

**Total deviations:** 3 auto-fixed (all Rule 3 - blocking). All were environment/compilation prerequisites, not behavior changes. No scope creep; production code matches the plan's interfaces and tasks.

## Issues Encountered
- None beyond the deviations above. `GhostReferenceStoreTest` (7) and `TimingGhostIntegrationTest` (6) pass; the full `:shared:testAndroidHostTest` and `:shared:check` gates pass. iOS simulator test execution is SKIPPED on Windows (no Xcode), consistent with prior waves.

## TDD Gate Compliance
- RED: `9ee427f` (`test(04-02)`) committed the failing storage/integration tests; verified the failure was missing production symbols (after fixing the unrelated test-helper name collision).
- GREEN: `453f9b6` and `cedf0bd` (`feat(04-02)`) made `GhostReferenceStoreTest` then `TimingGhostIntegrationTest` pass (7/7 and 6/6, 0 failures).
- No REFACTOR commit was needed.

## Known Stubs
None. Live delta is fully wired from the formal recorder; references persist real samples and a real progress curve. No placeholder/empty values flow to any consumer.

## Threat Flags
None. No new network endpoints, auth paths, or trust boundaries beyond the reference storage surface already modeled in the plan's `<threat_model>` (T-04-04/T-04-05/T-04-06), all mitigated and tested.

## User Setup Required
None — no external service configuration required.

## Next Phase Readiness
- GHOST-01 (save/select the current Track's reference/best lap), GHOST-02 (live delta fed by the formal recorder), and GHOST-04 (full replayable reference data) are satisfied for Plan 04-03 (live delta UI) and Plan 04-04 (variable-pace UAT).
- Plan 04-03 should render `SessionController.liveDelta()` (value-only `--`/`+x.xxxs`/`-x.xxxs`) without touching `recorderForTest()`.

## Self-Check: PASSED

All 3 created files and 7 modified files exist; all task commits (`9ee427f`, `453f9b6`, `cedf0bd`) are present in git history. `GhostReferenceStoreTest` (7 tests) and `TimingGhostIntegrationTest` (6 tests) pass with 0 failures; `:shared:testAndroidHostTest` and `:shared:check` are green.

---
*Phase: 04-ghost-lap-live-delta*
*Completed: 2026-06-26*
