---
phase: 03-local-sessions-review-and-export
plan: 4
subsystem: track
tags: [kmp, algorithm, reference-line, track-marking, tdd, pure-domain]

# Dependency graph
requires:
  - phase: 03-local-sessions-review-and-export
    provides: Canonical track/session DTOs (TrackMarkingSession, Track, TrackReferenceLine), GpsQualitySummary, LocalProjection, GPS fixtures (Plan 03-03 + earlier waves)
provides:
  - ReferenceLineExtractor — pure shared service deriving a reference line from a continuous marking run by repeated spatial structure, with outlier rejection and graceful degradation
  - ReferenceLineExtraction result — reference line (or none), readiness, per-loop diagnostics, GPS quality rollup, not-ready reasons, linked raw marking session
  - TrackReviewState — Track Review domain state: reference readiness, quality summary, Save/Re-record/Discard decision gating, start/finish + sector editing inputs, toTrack() builder
affects: [03-05 track review/edit UI, 03-06 review, 03-07 timing]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "Angular-winding loop segmentation (centroid bearing accumulation, split per 2π revolution) — robust to sparse sampling and short off-track excursions"
    - "Anchored closed-loop arc-length resampling — phase-aligns loops to a fixed track point so consensus deviation reflects real GPS scatter, not sampling offset"
    - "Outlier-robust median consensus loop + RMS-deviation rejection floor"
    - "Typed not-ready reasons + non-lap diagnostics instead of exceptions on poor captures"

key-files:
  created:
    - shared/src/commonMain/kotlin/com/huanfuli/lapsight/shared/track/ReferenceLineExtractor.kt
    - shared/src/commonMain/kotlin/com/huanfuli/lapsight/shared/track/TrackReviewState.kt
    - shared/src/commonTest/kotlin/com/huanfuli/lapsight/shared/track/ReferenceLineExtractorTest.kt
  modified: []

key-decisions:
  - "Loop segmentation by angular winding around the path centroid (not start-proximity), which stays reliable at low sample rates where proximity detection misses returns"
  - "Anchored closed-loop resampling to phase-align loops; integer cross-correlation alignment left ~11m residual on identical clean loops, so loops are anchored to the track point nearest the first sample instead"
  - "Thresholds calibrated against the Phase 3 fixtures: maxConsensusRms 3.0m and minMedianPointsPerLoop 16 cleanly separate ready (clean/min5/outlier) from not-ready (noise-drift/dropped)"
  - "No new Maven/Gradle dependency added — pure shared Kotlin over existing libraries, honoring the 03-02 dependency gate"

patterns-established:
  - "Pure, platform-free extraction service that never produces LapEvents and never calls LapEngine — marking is continuous capture, not lap timing"
  - "Extraction result links the unmodified source marking session so future algorithms can recompute without losing raw evidence"

requirements-completed: [SESS-02]

# Metrics
duration: 7min
completed: 2026-06-25
---

# Phase 3 Plan 04: Reference-Line Extraction and Track Review State Summary

**A pure shared algorithm that turns a continuous track-marking run into an averaged closed reference line by finding repeated spatial structure, rejecting outlier loops as diagnostics (never laps), and degrading to an explicit not-save-ready Track Review state on noisy or sparse captures — the riskiest Phase 3 algorithm (RESEARCH A1), isolated with fixture-backed tests.**

## Performance

- **Duration:** ~7 min
- **Completed:** 2026-06-25
- **Tasks:** 2 (TDD: 1 RED test commit + 1 GREEN implementation commit)
- **Files modified:** 3 (3 created, 0 modified)

## Accomplishments
- Implemented `ReferenceLineExtractor` as a pure shared service: project samples to local meters via `LocalProjection`, segment the continuous path into complete loops by angular winding, anchor-resample each loop to a common phase, build an outlier-robust median consensus, reject loops exceeding the RMS deviation floor, and average accepted loops into a `TrackReferenceLine` (D-18).
- The extractor never calls `LapEngine` and never constructs a `LapEvent`; outlier/noisy/dropped sections are recorded as `ExtractionDiagnostic`s (D-06 through D-09). Marking is continuous capture, not lap timing.
- The result links the unmodified source `TrackMarkingSession` and exposes `rawSamples` so future algorithms can recompute without losing original evidence (D-04, D-10).
- Degraded captures yield explicit `NotReadyReason`s (`InsufficientLoops`, `InconsistentLoops`, `SparseSampling`, `NoStructure`) and a null reference line instead of throwing (D-11).
- `TrackReviewState` wraps the extraction with reference readiness, a `GpsQualitySummary`, save-ready vs not-save-ready gating, start/finish + sector editing inputs, and a `toTrack()` builder that refuses to save a not-ready capture (D-12, D-31).
- Fixture-backed Wave 0 tests validate all six fixtures behave as required; `:shared:check` passes (iOS simulator tests SKIPPED on Windows, as expected). No new dependency added.

## Task Commits

Each task was committed atomically (TDD):

1. **Task 1: Add Wave 0 tests for reference-line extraction** - `b9fddd1` (test, RED)
2. **Task 2: Implement ReferenceLineExtractor and Track Review state** - `f91e671` (feat, GREEN)

## Files Created/Modified
- `.../track/ReferenceLineExtractor.kt` - Pure extraction service + result types (`ReferenceLineExtraction`, `ExtractionDiagnostic`, `NotReadyReason`, `DiagnosticKind`).
- `.../track/TrackReviewState.kt` - Track Review domain state with readiness/quality/decision gating and `toTrack()`.
- `.../track/ReferenceLineExtractorTest.kt` - Wave 0 coverage: clean/minimum ready, outlier rejected, raw samples preserved, noise/dropped degrade to not-save-ready.

## Fixture Behavior (calibration)

| Fixture | Detected loops | Accepted | Rejected | Result |
|---------|----------------|----------|----------|--------|
| clean-10-loop | 9 | 9 | 0 | ready |
| minimum-5-loop | 4 | 4 | 0 | ready |
| one-outlier-loop | 9 | 8 | 1 (Outlier ~315m) | ready |
| noise-drift | 7 | 7 | 0 | not ready (InconsistentLoops, ~5m RMS) |
| dropped-low-frequency | 5 | 5 | 0 | not ready (SparseSampling + InconsistentLoops) |

(Loop counts are one below the nominal 10/5/etc. because the final fractional revolution of a continuous run is incomplete and intentionally dropped.)

## Decisions Made
- **Angular-winding segmentation over start-proximity:** accumulating bearing change around the path centroid and splitting per 2π revolution stays reliable on the sparse `dropped-low-frequency` fixture (~88m between samples), where return-to-start proximity detection would miss closures.
- **Anchored closed-loop resampling for phase alignment:** the first implementation resampled each loop bucket from its own first sample, which left ~11m RMS on geometrically identical clean loops (sub-sample phase offset). Anchoring each loop's resampling to the track point nearest the capture's first sample phase-aligns all loops, dropping clean-loop consensus RMS to sub-threshold.
- **Fixture-calibrated thresholds:** `maxConsensusRmsMeters = 3.0` separates clean captures (~0m) from noise/drift (~5m); `minMedianPointsPerLoop = 16` separates resolved loops (48 pts/loop) from sparse capture (~7-8 pts/loop); `outlierRmsFloorMeters = 30.0` rejects the off-track loop (~315m) while keeping clean loops.

## Deviations from Plan

None — plan executed as written. Both tasks completed with the specified files, the algorithm is pure shared Kotlin with no Compose/platform/file-system/`LapEngine` coupling, and no dependency was added. The threshold and segmentation choices above are implementation calibration within the planned algorithm, not scope changes.

## Issues Encountered
- Initial thresholds and a sample-bucket-relative resample produced false not-ready results for clean/minimum/outlier captures (phase-offset RMS ~11m). Diagnosed with a temporary dump test (since removed), then fixed with anchored resampling and fixture-calibrated thresholds. No escalation needed.

## Known Stubs
None. `TrackReferenceLine` (data-shape-only from Plan 03-03) is now populated by this plan's extractor when a capture is reference-ready.

## User Setup Required
None. (Worktree build needs the gitignored `local.properties` copied from the main checkout for the Android SDK path; not committed.)

## Next Phase Readiness
- Plan 03-05 (track review / edit UI) can bind `TrackReviewState`: readiness, quality summary, not-ready reasons, available decisions, and the editable start/finish + sector inputs; `toTrack()` produces the saveable `Track` once start/finish/sectors are placed.
- The extractor is isolated and fixture-backed, containing the highest-uncertainty Phase 3 algorithm away from storage and UI per RESEARCH A1.

## Self-Check: PASSED

All three created files verified on disk; commits `b9fddd1` (test/RED) and `f91e671` (feat/GREEN) verified in git log. `:shared:check` BUILD SUCCESSFUL.

---
*Phase: 03-local-sessions-review-and-export*
*Completed: 2026-06-25*
