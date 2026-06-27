---
phase: 05-track-setup-and-course-profiles
plan: 7
subsystem: lap-timing
tags: [kotlin-multiplatform, lap-engine, complete-sectors, v2-persistence, tdd]

# Dependency graph
requires:
  - phase: 05-track-setup-and-course-profiles
    plan: 6
    provides: Offline TrackEditorScreen, TrackProfileController.appendRevision, immutable revision history
provides:
  - CourseDefinition.derivedSectors (M intermediate boundaries -> M+1 complete intervals; disabled -> none)
  - SectorResult / SectorResultDto (adjacent-crossing duration + separate cumulative Split)
  - LapEngine emits complete ordered Sector results behind a green replay gate (D-06/D-07/D-11/D-20)
  - TimingSessionPayloadV1.sectorResults (defaulted; V2 complete coverage persisted beside V1 legacy splits)
  - ReviewCompleteSector + TimingSessionReviewSummary.completeSectors (V2 review rows)
  - TimingSessionRecorder fans complete Sector results into every draft checkpoint unconditionally
affects: [05-08-revision-store-tests, 05-09-course-direction, 05-12-course-progress-matcher]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "Intermediate boundaries (SectorLine list) and derived complete intervals (SectorDefinition) are distinct; N sectors = N-1 boundaries + start/finish closure"
    - "The lap engine tracks the next expected boundary by order; only an in-order crossing advances V2 Sector state, so duplicate/backward/out-of-order/opposite crossings are ignored without touching legacy SectorEvent behavior"
    - "The final interval closes on the accepted start/finish crossing at the same interpolated timestamp as the lap; a missed boundary leaves coverage incomplete but never gates lap/raw timing"
    - "V2 complete Sectors persist via a defaulted TimingSessionPayloadV1.sectorResults field, so V1 history decodes unchanged and legacy SectorEventDto keeps its cumulative-line-split meaning"

key-files:
  created:
    - shared/src/commonTest/kotlin/com/huanfuli/lapsight/shared/lap/CompleteSectorReplayTest.kt
  modified:
    - shared/src/commonMain/kotlin/com/huanfuli/lapsight/shared/lap/TimingLines.kt
    - shared/src/commonMain/kotlin/com/huanfuli/lapsight/shared/lap/LapModels.kt
    - shared/src/commonMain/kotlin/com/huanfuli/lapsight/shared/lap/LapEngine.kt
    - shared/src/commonMain/kotlin/com/huanfuli/lapsight/shared/session/SessionModels.kt
    - shared/src/commonMain/kotlin/com/huanfuli/lapsight/shared/session/TimingSessionRecorder.kt
    - shared/src/commonMain/kotlin/com/huanfuli/lapsight/shared/review/ReviewModels.kt
    - shared/src/commonMain/kotlin/com/huanfuli/lapsight/shared/storage/LocalSessionStore.kt
    - shared/src/commonMain/kotlin/com/huanfuli/lapsight/shared/storage/InMemorySessionStore.kt
    - shared/src/commonMain/kotlin/com/huanfuli/lapsight/shared/storage/FileSessionStore.kt
    - shared/src/commonTest/kotlin/com/huanfuli/lapsight/shared/review/ReviewSummaryTest.kt
    - shared/src/commonTest/kotlin/com/huanfuli/lapsight/shared/export/JsonExportTest.kt

key-decisions:
  - "Re-interpret the existing CourseDefinition.sectors list as intermediate boundaries and DERIVE complete intervals (derivedSectors) rather than rename the persisted shape; M boundaries -> M+1 intervals, empty -> none (D-07), keeping all existing line-centric SectorEvent/SectorLine behavior and tests untouched"
  - "Run V2 complete-Sector advancement independently of the legacy per-line dedup inside handleSectorCrossing, gated purely by next-expected-boundary order, so the order gate alone rejects duplicate/backward/out-of-order/opposite crossings (D-20) without a per-boundary direction model"
  - "Persist V2 results through a defaulted TimingSessionPayloadV1.sectorResults field (canonicalJson encodeDefaults=true / ignoreUnknownKeys=true), so V1 JSON decodes cleanly and migration/export are byte-compatible; no new schema version is introduced in this plan"
  - "Expose completeSectors (V2) and sectorSplits (V1 LegacyCumulativeSplit) as separate Review fields; the V2 list is read from the persisted snapshot and is NEVER inferred from legacy line crossings"

requirements-completed: [SC-02]

# Metrics
duration: ~45min
completed: 2026-06-27
---

# Phase 5 Plan 07: Complete Sector Timing and V2 Persistence Summary

**Replaced line-centric Sector semantics with complete adjacent course intervals across the clean-room lap engine and the record/Review/export pipeline: the engine now derives N complete ordered Sector results from N-1 intermediate boundaries plus the start/finish closure (each carrying an adjacent-crossing duration AND a separate cumulative Split), tracks the next expected boundary so duplicate/backward/out-of-order/opposite crossings never advance Sector state, closes the final interval on the accepted start/finish crossing at the lap timestamp, and lets a missed boundary leave coverage explicitly incomplete without ever gating lap or raw timing — then persists those V2 results through a defaulted `TimingSessionPayloadV1.sectorResults` field so Review and the unchanged canonical `JsonExportService` expose complete Sector coverage while V1 `SectorEventDto` keeps its legacy cumulative-split meaning. Closes SC-02 / D-06, D-07, D-11, D-20.**

## Performance

- **Duration:** ~45 min
- **Tasks:** 2 (both `type="auto" tdd="true"`)
- **Files:** 12 (1 created, 11 modified)

## Accomplishments

- **Complete ordered Sector intervals (Task 1):** `CourseDefinition.derivedSectors` turns `M` intermediate boundaries into `M + 1` complete `SectorDefinition` intervals (Sector 1 opens at the lap crossing; the final Sector closes back on the start/finish line); an empty boundary list (Sectors disabled) yields no intervals (D-07). `SectorResult(lapNumber, sectorId, sectorOrder, startedAtMillis, endedAtMillis, durationMillis, cumulativeSplitMillis)` carries both the adjacent-crossing duration and the separate cumulative Split (D-11). The `LapEngine` tracks the next expected boundary index plus the in-progress sector's open time, emits each interval as its closing boundary crosses, closes the final interval on the accepted start/finish crossing at the same interpolated timestamp as the lap (D-06), and leaves coverage explicitly incomplete (fewer than N results) when a boundary is missed — never pausing lap or raw timing (D-20). The legacy line-centric `SectorEvent`/`SectorLine` path is preserved verbatim, so all existing `LapEngineTest` cases stay green.
- **Green replay gate (Task 1):** `CompleteSectorReplayTest` independently proves N=2..6 (exactly N results, contiguous order), disabled (no results), Sector-1 duration == cumulative split, later-Sector cumulative > adjacent duration, final-interval closure at the lap timestamp, duplicate/backward boundary rejection, a missed boundary that still completes the lap, and stable interpolation ordering when one low-frequency segment crosses start/finish and both boundaries at once.
- **V2 persistence beside V1 legacy (Task 2):** `SectorResultDto` + a defaulted `TimingSessionPayloadV1.sectorResults` field persist complete intervals; `SectorEventDto` is documented as the V1 `LegacyCumulativeSplit` and is never relabeled. `TimingSessionRecorder` accumulates `SectorResult`s from the engine and fans them out into every draft checkpoint unconditionally; `saveTimingDraft` threads them (defaulted param) through both stores so a saved session keeps complete coverage end-to-end.
- **Review + export (Task 2):** `ReviewModels` exposes `TimingSessionReviewSummary.completeSectors` (`ReviewCompleteSector` with separate duration and cumulative split) alongside the legacy `sectorSplits`, reading complete intervals only from the persisted snapshot. The existing canonical `JsonExportService` serializes the V2 snapshot with no service change. `ReviewSummaryTest` and `JsonExportTest` assert both the V2 separate fields and preserved V1 legacy semantics.

## Task Commits

1. **Task 1 — RED** — `04529af` — `test(05-07)`: failing `CompleteSectorReplayTest`.
2. **Task 1 — GREEN** — `23efd11` — `feat(05-07)`: `derivedSectors`, `SectorResult`, engine boundary-order tracking; `CompleteSectorReplayTest` + `LapEngineTest` green.
3. **Task 2 — RED** — `4ad6b00` — `test(05-07)`: V2 assertions in `ReviewSummaryTest` + `JsonExportTest`.
4. **Task 2 — GREEN** — `ad88967` — `feat(05-07)`: `SectorResultDto` + payload field, recorder fan-out, store threading, Review/export V2 rows; `:shared:check` + `:androidApp:assembleDebug` green.

## Files Created/Modified

- `lap/TimingLines.kt` (modified) — `SectorDefinition` + `CourseDefinition.derivedSectors`.
- `lap/LapModels.kt` (modified) — `SectorResult` + `LapTimingState.latestSectorResult`/`completedSectorResults`.
- `lap/LapEngine.kt` (modified) — next-expected-boundary tracking, `maybeAdvanceSector`, final-interval closure, reset wiring.
- `lap/CompleteSectorReplayTest.kt` (created) — the independent green replay gate.
- `session/SessionModels.kt` (modified) — `SectorResultDto`, `TimingSessionPayloadV1.sectorResults`, mappers; legacy `SectorEventDto` doc.
- `session/TimingSessionRecorder.kt` (modified) — accumulate + unconditional fan-out of complete Sector results.
- `review/ReviewModels.kt` (modified) — `ReviewCompleteSector` + `completeSectors`; legacy `ReviewSectorSplit` doc.
- `storage/LocalSessionStore.kt`, `storage/InMemorySessionStore.kt`, `storage/FileSessionStore.kt` (modified) — defaulted `sectorResults` param on `saveTimingDraft`, threaded into the built payload.
- `review/ReviewSummaryTest.kt`, `export/JsonExportTest.kt` (modified) — V2 + V1-legacy assertions.

## Decisions Made

- **Derive, don't rename.** The persisted `CourseDefinition.sectors` list is re-read as intermediate boundaries and complete intervals are derived (`M -> M+1`, empty -> none). This keeps every existing line-centric model, test, and serialized shape intact while adding the complete-interval contract.
- **Order gate is the whole guard.** V2 advancement runs independently of legacy per-line dedup and is gated only by the next-expected-boundary index, so duplicate, backward, out-of-order, and opposite crossings are all rejected by one deterministic, clean-room rule — no per-boundary direction learning was needed.
- **Defaulted field, no new schema version.** `sectorResults` defaults to empty and rides the canonical JSON config (`encodeDefaults`/`ignoreUnknownKeys`), so V1 history and the existing V1->V2 migration are unaffected and export needs no service change.
- **Snapshot-truth Review.** `completeSectors` is rendered only from the persisted session snapshot and is never inferred from legacy line crossings, preserving historical truth.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] Threaded `sectorResults` through the store draft path**
- **Found during:** Task 2 (persisting V2 results end-to-end)
- **Issue:** The plan's `files_modified` did not list the storage files, but the only way a recorded session's complete Sector results reach the saved payload is the `recorder -> saveTimingDraft -> loadUnfinishedDraft -> saveTimingSession` pipeline. `saveTimingDraft` builds the `TimingSessionPayloadV1` from individual fields, so without a `sectorResults` parameter the results are dropped before Save.
- **Fix:** Added a defaulted `sectorResults: List<SectorResultDto> = emptyList()` parameter to `LocalSessionStore.saveTimingDraft` and included it in the payload built by both `InMemorySessionStore` and `FileSessionStore`. The default keeps every existing caller (`SessionController.startTiming`) and V1 history unaffected; only the recorder passes a non-empty list.
- **Files modified:** `storage/LocalSessionStore.kt`, `storage/InMemorySessionStore.kt`, `storage/FileSessionStore.kt`
- **Commit:** `ad88967`

**2. [Rule 3 - Blocking] Worktree missing gitignored `local.properties` (Android SDK location)**
- **Found during:** Task 1 (first Gradle run)
- **Issue:** The parallel worktree does not inherit the main checkout's gitignored `local.properties` (`sdk.dir`), so `:shared:testAndroidHostTest` / `:shared:check` / `:androidApp:assembleDebug` cannot configure the Android SDK.
- **Fix:** Copied `local.properties` from the main checkout into the worktree (confirmed gitignored; NOT committed).
- **Commit:** N/A (gitignored build config).

**Total deviations:** 2 (both Rule 3 / blocking). No architectural change, no new dependency, no deferred scope, no new schema version.

## Verification

- **Task 1:** `:shared:testAndroidHostTest --tests "*CompleteSectorReplayTest*" --tests "*LapEngineTest*"` BUILD SUCCESSFUL.
- **Task 2:** `:shared:testAndroidHostTest --tests "*CompleteSectorReplayTest*" --tests "*ReviewSummaryTest*" --tests "*JsonExportTest*"` BUILD SUCCESSFUL; full `:shared:check` BUILD SUCCESSFUL; `:androidApp:assembleDebug` BUILD SUCCESSFUL.
- iOS link/run tasks remain SKIPPED on Windows (as in every prior plan); iOS source compiles cleanly under `:shared:check`.

## Issues Encountered

- Pre-existing compiler warnings in unrelated lap/review/export test files (`!!` on non-null receivers, an always-true condition) and `expect`/`actual` beta notes were left untouched (out of scope).

## Known Stubs

- None. The engine emits real complete Sector results, the recorder persists them unconditionally, and Review/export read the persisted V2 snapshot. No hardcoded empty/placeholder data flows to the UI. Manual on-device UAT of N-Sector coverage and missed-boundary behavior remains a normal phase-level checkpoint (not a code stub).

## Threat Flags

- None new. T-05-13 (course count/order tampering) is mitigated by `CompleteSectorReplayTest` count cases (N=2..6, disabled). T-05-14 (untrusted crossing sequence) is mitigated by the expected-order gate, bounded per-lap state, and continued lap timing on incomplete sectors. T-05-15 (legacy Review/export repudiation) is mitigated by the explicit `LegacyCumulativeSplit` label and the never-inferred, snapshot-only V2 `completeSectors`. No security surface beyond the plan's `<threat_model>`.

## Self-Check: PASSED

---
*Phase: 05-track-setup-and-course-profiles*
*Completed: 2026-06-27*
