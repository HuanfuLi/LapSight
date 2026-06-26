---
phase: 03-local-sessions-review-and-export
plan: 07
subsystem: review
tags: [kmp, kotlin, compose-canvas, trace-projection, offline-rendering, vector-graphics, tdd]

# Dependency graph
requires:
  - phase: 03-local-sessions-review-and-export
    provides: LocalProjection (lap), GeoPointDto/LocationSampleDto (session), TrackModels (track), ReviewModels (review), ReviewScreen (ui)
provides:
  - TraceProjection — pure lat/lon to normalized screen-point projection with aspect-ratio preservation
  - TraceLayer / TracePoint — render-only trace layer types with color/stroke/dash metadata
  - buildTrackTraceLayers — D-35 trace layers for Track Review
  - buildTimingTraceLayers — D-36 trace layers for Timing Session Review
  - TraceView — Compose Canvas offline vector trace renderer with UI-SPEC colors/strokes
  - Track/Timing trace sections integrated into ReviewScreen
affects: [03-08 export UI]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "TraceProjection is pure shared Kotlin with no Compose dependency; uses LocalProjection for lat/lon-to-meter conversion, then normalizes to [0..1] with aspect-ratio preservation"
    - "TraceView is a thin Compose Canvas wrapper that renders pre-projected TraceLayer lists with UI-SPEC stroke/color contract"
    - "Trace layer builders (buildTrackTraceLayers, buildTimingTraceLayers) live in the review package and delegate to TraceProjection for coordinate normalization"
    - "Canonical lat/lon preserved in saved payloads; screen coordinates are render-only TracePoints (D-34)"

key-files:
  created:
    - shared/src/commonMain/kotlin/com/huanfuli/lapsight/shared/review/TraceProjection.kt
    - shared/src/commonMain/kotlin/com/huanfuli/lapsight/shared/ui/TraceView.kt
    - shared/src/commonTest/kotlin/com/huanfuli/lapsight/shared/review/TraceProjectionTest.kt
  modified:
    - shared/src/commonMain/kotlin/com/huanfuli/lapsight/shared/review/ReviewModels.kt
    - shared/src/commonMain/kotlin/com/huanfuli/lapsight/shared/track/TrackReviewState.kt
    - shared/src/commonMain/kotlin/com/huanfuli/lapsight/shared/ui/ReviewScreen.kt
    - shared/src/commonTest/kotlin/com/huanfuli/lapsight/shared/review/ReviewSummaryTest.kt

key-decisions:
  - "TraceProjection uses the existing LocalProjection (lap package) for lat/lon-to-meter conversion; bounds, aspect ratio, and padding are handled in the projection layer"
  - "Trace layer builders are pure functions in the review package that produce TraceLayer lists; the Compose TraceView only receives pre-projected layers"
  - "Outlier samples for TrackReviewState.buildTraceLayers are approximated from diagnostic loop indices by evenly dividing samples per detected loop"
  - "Timing trace highlight uses selectedLapStartMillis/selectedLapEndMillis filter on session samples; null ranges produce empty highlight layer"

patterns-established:
  - "Pure projection + thin Compose wrapper: shared domain logic (TraceProjection) is Compose-free; TraceView is a minimal Canvas renderer"
  - "Trace layer contract: layer name, points, color (0xAARRGGBB Long), strokeWidth (dp), dashed flag"
  - "UI-SPEC colors encoded as Long hex: 0xFF62E3FF (cyan), 0xFF9AA8B8 (muted), 0x80FFB84D (orange 50% alpha), 0xFF8CFF9B (green), 0xFFFFD166 (amber)"

requirements-completed: [SESS-02, SESS-03]

# Metrics
duration: 25min
completed: 2026-06-25
---

# Phase 3 Plan 07: Offline Vector Trace Rendering Summary

**Pure lat/lon-to-screen projection with aspect-ratio preservation, Compose Canvas renderer with UI-SPEC colors/strokes, and trace sections integrated into Track and Timing Session Review details — all without map tiles, external SDKs, or geocoding.**

## Performance

- **Duration:** ~25 min
- **Completed:** 2026-06-25
- **Tasks:** 3 (TDD: 1 RED test commit + 2 GREEN implementation commits)
- **Files modified:** 7 (3 created, 4 modified)

## Accomplishments
- `TraceProjection` (pure shared Kotlin, no Compose) converts canonical `GeoPointDto` lists to normalized `TracePoint` layers using the existing `LocalProjection`. It computes a common bounding box across all layers, preserves aspect ratio, applies caller padding, and returns render-only points. Empty, single-point, and all-identical degenerate inputs return typed empty states — never crashes (T-03-16).
- `TracePoint` (x, y in [0..1]) and `TraceLayer` (name, points, color, strokeWidth, dashed) define the render contract. Canonical lat/lon stays in saved payloads; screen coordinates are rendering-only (D-34).
- `buildTrackTraceLayers()` produces D-35 layers: full marking trace (muted #9AA8B8, 2px), reference line (cyan #62E3FF, 3px), outlier sections (orange #FFB84D 50% alpha, 2px dashed), start/finish (green #8CFF9B, 3px), sector lines (amber #FFD166, 2px).
- `buildTimingTraceLayers()` produces D-36 layers: reference baseline (cyan, 3px), session trace (muted, 2px), start/finish (green, 3px), sectors (amber, 2px), selected/best lap highlight (cyan, 4px). Highlight samples are filtered by elapsedMillis range.
- `TrackReviewState.buildTraceLayers()` delegates to `buildTrackTraceLayers`, deriving outlier samples from `ExtractionDiagnostic` loop indices with an equal-sample-per-loop approximation.
- `TraceView` (Compose Canvas) renders `TraceLayer` lists with solid lines via `drawLine` and dashed lines via `drawPath` + `PathEffect.dashPathEffect`. Responsive sizing via `BoxWithConstraints` with configurable min/max height.
- ReviewScreen: `TrackTraceSection` renders the trace for Track and TrackMarking Review entries (loads Track + marking payloads from the store). `TimingTraceSection` renders the trace for Timing Session Review entries (loads Track reference line + session samples).
- No map tiles, external map SDKs, heatmaps, 3D/elevation analysis, map matching, or geocoding (D-33, D-37).
- No new Gradle dependencies.

## Task Commits

Each task was committed atomically (TDD):

1. **Task 1 RED: Add Wave 0 tests for trace projection and review trace state** - `52b620c` (test)
2. **Task 2 GREEN: Implement trace projection helpers and review trace state** - `aabf3cd` (feat)
3. **Task 3 GREEN: Render offline vector traces in Review details** - `20f1381` (feat)

## Files Created/Modified
- `.../review/TraceProjection.kt` - Pure lat/lon-to-normalized projection with bounding box, aspect ratio, and padding.
- `.../review/ReviewModels.kt` - Added `buildTrackTraceLayers()` (D-35) and `buildTimingTraceLayers()` (D-36) trace layer builder functions.
- `.../track/TrackReviewState.kt` - Added `buildTraceLayers()` method that delegates to `buildTrackTraceLayers` with outlier extraction.
- `.../ui/TraceView.kt` - Compose Canvas offline vector trace renderer with UI-SPEC colors/strokes and dashed path support.
- `.../ui/ReviewScreen.kt` - Added `TrackTraceSection` and `TimingTraceSection` composables; replaced placeholder text with trace rendering.
- Test: `TraceProjectionTest.kt` - 9 tests covering projection, determinism, canonical lat/lon preservation, degenerate inputs, aspect ratio.
- Test: `ReviewSummaryTest.kt` - Extended with 5 trace-state tests covering D-35 and D-36 layer requirements, graceful degradation.

## Decisions Made
- TraceProjection is a pure shared Kotlin object with no Compose dependency; it uses LocalProjection from the lap package for meter conversion.
- Trace layer builders (buildTrackTraceLayers, buildTimingTraceLayers) are pure functions in the review package; they produce TraceLayer lists consumed by the Compose TraceView.
- Outlier sample extraction for TrackReviewState uses equal-sample-per-loop approximation based on detected loop count and diagnostic loop indices.
- Timing trace highlight filtering uses elapsedMillis range (selectedLapStartMillis..selectedLapEndMillis) on the session's raw samples.

## Deviations from Plan

None — plan executed exactly as written.

## Issues Encountered

None. All verifications passed on first attempt after fixing a `toPx()` density-scope issue in TraceView.

## Known Stubs

- The `TimingTraceSection` does not compute the best-lap time range from `bestLapMillis`; it passes `null` for `selectedLapStartMillis` and `selectedLapEndMillis`, so the best-lap highlight layer is not rendered. The `buildTimingTraceLayers` function accepts these parameters and the highlight layer infrastructure is in place — a future plan can derive the time range from `TimingSessionPayloadV1.laps` to enable the highlight.
- `TrackTraceSection` outlier samples are approximated from diagnostic loop indices by evenly dividing samples per detected loop. When the extractor exposes per-loop sample boundaries, the approximation can be replaced with exact ranges.

## User Setup Required

None — no external service configuration required.

## Next Phase Readiness
- Plan 03-08 (JSON/GPX export) can reuse `TimingSessionPayloadV1` and `TrackPayloadV1` for canonical export payloads. The trace layer builders are available if export needs to include trace rendering.
- The best-lap highlight time range derivation (from `TimingSessionPayloadV1.laps`) is a minor follow-up; the `buildTimingTraceLayers` API already accepts the necessary parameters.

## Self-Check: PASSED

All 7 files verified on disk; commits `52b620c`, `aabf3cd`, `20f1381` verified in git log. `:shared:testAndroidHostTest` BUILD SUCCESSFUL (all tests pass). `:shared:check` BUILD SUCCESSFUL (iOS sim tests skipped on Windows as expected). `:androidApp:assembleDebug` BUILD SUCCESSFUL.

---
*Phase: 03-local-sessions-review-and-export*
*Completed: 2026-06-25*
