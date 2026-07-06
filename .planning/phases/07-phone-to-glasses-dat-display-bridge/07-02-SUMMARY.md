# 07-02 Plan Summary — SessionController Seam + Pure HudModel Mapper

**Phase:** 07 — Phone-to-Glasses DAT Display Bridge
**Plan:** 02
**Status:** Complete
**Requirements:** MR-01, MR-02

## Objective

Deliver the read-side foundation of the bridge: (1) hoist the single
`SessionController` so `androidApp` can reach the SAME instance the phone dash
drives (the MR-01 keystone seam), and (2) a pure, host-testable `HudModel`
mapper that projects `TimingRunSnapshot` (+ GPS/ready state) into per-page HUD
content covering every MR-02 readout and the D-13/D-14/D-15 non-timing states.

## Work Completed / Task Commits

1. **Task 1 — Hoist `SessionController`, add `GlassesGpsState`** — `51d4dbc`
   - Added `onSessionControllerReady: (SessionController) -> Unit = {}` to
     `App()` and `AppShell()`, threaded through, and invoked exactly once via
     `LaunchedEffect(sessionController) { onSessionControllerReady(sessionController) }`
     (keyed on the instance, never re-fired on recomposition).
   - `MainActivity` now captures the hoisted controller into a nullable
     `private var sessionController: SessionController?` via
     `onSessionControllerReady = { sessionController = it }`. No second
     `SessionController(...)` is constructed anywhere in `androidApp`.
   - Added `shared/.../glasses/GlassesGpsState.kt` (platform-free: `fixStatus`,
     `accuracyMeters`, `sampleRateHz`, `ready`), with `GlassesGpsState.idle()`
     and `GlassesGpsState.from(fixStatus, accuracyMeters, sampleRateHz,
     thresholds = ReadyThresholds.Default)`.
2. **Task 2 — Pure `HudModel` mapper + `HudPage` enum** — `bdcfec4`
   - `shared/.../glasses/HudPage.kt`: `enum class HudPage { DELTA_ONLY, FOCUSED, TELEMETRY }` (D-01), cycle order = ordinal.
   - `shared/.../glasses/HudModel.kt`: `HudModel.from(run: TimingRunSnapshot, gps: GlassesGpsState, page: HudPage, nowEpochMs: Long, flashUntilEpochMs: Long?, speedUnit: SpeedUnit = KilometersPerHour): HudModel`.
     Produces `deltaText`/`deltaCaret` (passthrough + Faster→Down/Slower→Up/Neutral→None),
     a tenths-precision `clockText` (D-05, `"1:23.4"`) that swaps to a sector-flash
     label inside the flash window (D-04), `lastLapLabel`/`bestLapLabel` via the
     existing `formatLapTime()`, a unit-applied `speedLabel`, `lapCount`, and the
     `isIdle` (D-13) / `isStaleFix` (D-15) / `isNeutralDelta` (D-14) flags.
     Imports zero `com.meta.wearable.*`/Compose types.
3. **Task 3 — Host tests** — `5e276a8`
   - `shared/src/commonTest/.../glasses/HudModelTest.kt`: per-page readouts,
     delta tone/sign → caret + passthrough text, D-14 neutral-pill geometry,
     D-13 idle, D-15 stale-fix (asserts the clock keeps advancing across two
     synthetic beats while speed/delta collapse to `--`), tenths formatting,
     and the D-04 sector-flash window entering/reverting.

## Files Created/Modified

- `shared/src/commonMain/kotlin/com/huanfuli/lapsight/shared/App.kt` — `onSessionControllerReady` param, threaded to `AppShell`.
- `shared/src/commonMain/kotlin/com/huanfuli/lapsight/shared/ui/AppShell.kt` — accepts the callback, invokes it once via `LaunchedEffect`.
- `androidApp/src/main/kotlin/com/huanfuli/lapsight/MainActivity.kt` — captures the hoisted controller.
- `shared/src/commonMain/kotlin/com/huanfuli/lapsight/shared/glasses/GlassesGpsState.kt` — new, platform-free GPS/ready snapshot.
- `shared/src/commonMain/kotlin/com/huanfuli/lapsight/shared/glasses/HudPage.kt` — new, page enum.
- `shared/src/commonMain/kotlin/com/huanfuli/lapsight/shared/glasses/HudModel.kt` — new, pure mapper.
- `shared/src/commonTest/kotlin/com/huanfuli/lapsight/shared/glasses/HudModelTest.kt` — new, host tests.

## Decisions Made

### RESEARCH Open Q2 — resolved: the exact GPS/ready accessor for D-13

`DriveScreen`'s `dashReadyState(snapshot: DriveMarkingSnapshot)` is,
technically, platform-free Kotlin (`DriveMarkingSnapshot`/`DriveMarkingController`
import zero Compose), but it is only ever produced inside a
`DriveMarkingController` that `DriveScreen` `remember`s over the live
`LocationSampleProvider` (`DriveScreen.kt:79-81`) — i.e. reachable only from
inside that Composable, not from the hoisted `SessionController` seam or from
`androidApp` outside Compose. Per the plan's documented fallback, this plan
introduces `GlassesGpsState` instead:

- `GlassesGpsState` carries `fixStatus`, `accuracyMeters`, `sampleRateHz`,
  `ready`, and `GlassesGpsState.from(...)` applies the **same**
  `ReadyThresholds.Default` (25 m accuracy / 0.9 Hz — the dash's own
  `gpsQualityWarning` thresholds) to decide `ready`.
- This plan does **not** wire a live pre-timing GPS feed into that factory —
  doing so would require either (a) a second consumer of the single
  `LocationSampleProvider.nextSample()`/`drainPending()` queue (which would
  race with `DriveMarkingController`'s own polling and drop samples meant for
  Track marking/Review), or (b) touching `AndroidPhoneLocationProvider.kt` /
  `DriveScreen.kt`, neither of which is in this plan's `files_modified`. That
  wiring is intentionally left to **07-03**, where the actual `GlassesBridge`
  is constructed in `MainActivity` (which already holds a `phoneGpsProvider`
  reference) and can supply real `fixStatus`/`accuracyMeters`/`sampleRateHz`
  values into `GlassesGpsState.from(...)` without touching this plan's scope.
  While a run is active, the bridge can instead read `accuracyMeters`/
  `sampleRateHz` straight off `TimingRunSnapshot` (already populated from the
  live sample) rather than probing the feed a second time.

### `isIdle` vs `isStaleFix` — mutually exclusive by construction

The plan text described D-13 idle as triggered by "GlassesGpsState not ready
**or** no active run." Read literally that would make `isIdle` and
`isStaleFix` both true simultaneously whenever a run is active but GPS quality
drops — which is ambiguous for the renderer (07-04) that branches on them as
distinct screens. This plan defines them as mutually exclusive by construction:

- `isIdle = !run.isActive` — the pre-timing GPS-status screen (D-13); its
  exact copy ("Waiting for GPS" vs "Ready — start timing") is selected by
  `gpsReady`, which the model always carries.
- `isStaleFix = run.isActive && !gps.ready` — the mid-lap degraded-GPS state
  (D-15), only possible while a run is active.

### D-15 "clock keeps advancing" — achieved by NOT touching the clock

`TimingRunSnapshot.currentLapMillis`/`sessionElapsedMillis` are sample-driven
(verified in `TimingSessionRecorder`/`LapEngine`: they only advance when a new
GPS sample arrives — mirroring why the phone dash's own `RunningLapTimeText`
needs a wall-clock re-basing hack in Compose). Since `HudModel.from` is a pure,
stateless function invoked fresh every ~2 Hz beat by the (future) bridge poll
loop, this plan deliberately does **not** attempt to re-derive or freeze the
clock inside the mapper: `clockText` is always `run.currentLapMillis` at
tenths precision, and "keeps advancing on a stale fix" falls out naturally as
long as the caller keeps polling a `TimingRunSnapshot` whose engine keeps
receiving samples. Only `deltaText`/`speedLabel` are explicitly collapsed to
`--` when `isStaleFix`. `HudModelTest.clockKeepsAdvancingAcrossBeatsWhileStaleAsCurrentLapMillisAdvances`
documents this contract with two synthetic beats.

### D-04 sector-flash label format

`TimingRunSnapshot` exposes only the raw `latestSectorSplitMillis` (an
absolute split duration), not a per-sector delta-to-reference. The CONTEXT
example ("`S2 +0.12`") reads like a delta, but computing one would require new
math the engine does not expose (violates RESEARCH "Don't Hand-Roll"). This
plan formats the raw split at tenths precision instead — `"S2 +12.3s"` — and
documents the substitution here rather than inventing a delta. The exact
label format remains Claude's-discretion per CONTEXT.md and can be revisited
in 07-04 (the renderer) if real-hardware review wants something else.

## Verification

- `./gradlew :shared:testAndroidHostTest` — green (full suite, including the
  15 new `HudModelTest` cases).
- `./gradlew :shared:testAndroidHostTest --tests "*HudModelTest*"` — green.
- `./gradlew :androidApp:assembleDebug` — green (confirms the new `App`/
  `AppShell` signature and `MainActivity` capture compile end to end).
- `grep -rn "com.meta.wearable\|androidx.compose" shared/.../glasses/*.kt` —
  no matches other than a doc comment describing the boundary; `HudModel`,
  `HudPage`, and `GlassesGpsState` import zero DAT/Compose types.

## Deviations from Plan

None — plan executed as written. The GPS-accessor design choice above is the
plan's own documented fallback path (Task 1's `<read_first>`/`<action>`
explicitly anticipates it and asks for the choice to be recorded in this
SUMMARY), not an unplanned deviation.

## Issues Encountered

- This worktree had no `local.properties`; created one with `sdk.dir` pointed
  at the existing Android SDK (matching the main worktree's value) so Gradle
  could resolve the Android SDK. Gitignored; not committed.

## Next Phase Readiness

- **07-03** (`GlassesBridge` DAT lifecycle + 2 Hz poll loop) can now: (a) reach
  the live `SessionController` via `MainActivity`'s captured field, and (b)
  call `HudModel.from(sessionController.timingRunSnapshot(), gps, page, now(),
  flashUntilEpochMs)` once it supplies a real `GlassesGpsState` (constructed
  from `phoneGpsProvider`'s live state pre-timing, or from
  `TimingRunSnapshot`'s own accuracy/rate fields once a run is active).
- No blockers. The clean-room boundary holds: `shared/.../glasses/*` imports
  zero DAT/Compose types; only `androidApp` will import `com.meta.wearable.*`
  starting in 07-03.

---
*Phase: 07-phone-to-glasses-dat-display-bridge*
*Completed: 2026-07-06*
