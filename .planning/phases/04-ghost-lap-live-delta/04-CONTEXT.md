# Phase 04: Ghost Lap + Live Delta - Context

**Gathered:** 2026-06-25T18:05:42-04:00
**Status:** Ready for planning

<domain>

## Phase Boundary

Phase 4 delivers phone-first ghost lap comparison for a saved Track: persist/select the current Track's fastest valid lap as a reference, calculate current-lap delta against that reference by equivalent progress distance, and render a minimal live delta on the mounted-phone Drive/Timing dash.

In scope: reference-lap data model/storage, progress-distance delta engine behavior, live dash delta readout, same-session best-lap reference update, and deterministic simulated-GPS UAT.

Out of scope: map ghost animation, dense telemetry charts, cloud/shared ghosts, external GNSS precision tuning, Meta glasses HUD rendering, real Android/iOS GPS providers, and any public-road racing positioning.

</domain>

<decisions>

## Implementation Decisions

### Reference Lap Selection and Update Timing

- **D-01:** By default, live delta uses the fastest valid lap for the current saved Track. The user should not need to manually select a reference before every session.
- **D-02:** If a new fastest valid lap is completed during an active TimingSession, it immediately becomes the reference for following laps in the same session. The user must not need to stop, save, and start a new session after setting a purple/best lap.
- **D-03:** Reference laps are Track-scoped. A reference must belong to the same Track as the active TimingSession.
- **D-04:** Demo/Simulated laps must not become reference laps for real Track data. Simulated data may be saved locally for UAT, but must remain isolated from real best/ghost calculations.

### Reference Lap Data Shape

- **D-05:** Store both raw best-lap samples and a precomputed progress curve. Raw samples preserve replay/debug/export and future algorithm improvements; the progress curve powers realtime delta without rescanning raw GPS points on every sample.
- **D-06:** The progress curve is distance/progress-indexed, not timestamp-only. It should include `progressMeters` or normalized progress, elapsed time, lat/lon or local point, speed, heading, accuracy, and optional sector metadata where available.
- **D-07:** Phase 4 should make the progress curve data-ready for later telemetry work, but must not build professional telemetry charts in this phase.

### Delta Calculation Semantics

- **D-08:** Current-lap progress is built in realtime from incoming samples.
- **D-09:** For each current sample, compute current `progressMeters`, look up the equivalent progress point on the reference progress curve, and calculate `deltaMillis = currentLapElapsedMillis - referenceElapsedMillis`.
- **D-10:** Positive delta means slower than reference and displays as `+time`. Negative delta means faster than reference and displays as `-time`.
- **D-11:** Suppress the delta number when the current lap has not started, no reference exists, GPS quality is too poor, or current progress cannot be matched confidently to the reference curve.
- **D-12:** When a completed lap becomes the new fastest valid lap, rebuild/update the reference lap immediately so the next lap chases the new best.

### Live Dash Presentation

- **D-13:** Live delta is the second core readout after Current Lap. It should be visible in normal Drive/Timing mode and strongly visible in fullscreen mode, but must not visually compete with the current lap timer.
- **D-14:** The moving dashboard shows only sign plus seconds, e.g. `-0.218s` when faster and `+0.421s` when slower. Do not show words like "ahead", "behind", "faster", or "slower" in the driving readout.
- **D-15:** Use minimal color/state only: negative/faster delta in green, positive/slower delta in orange/red, unavailable/suppressed delta as a neutral placeholder.
- **D-16:** Do not add map ghost animation, telemetry charts, or dense analysis UI in Phase 4. The page already has many live metrics, so delta UI must stay extremely compact.

### No-Reference and Low-Confidence States

- **D-17:** When no valid live delta is available, show `--` for delta. Do not show explanatory text in the main driving UI.
- **D-18:** Do not keep a stale delta on screen as if it were live. If the live delta is not trustworthy, replace it with `--`.
- **D-19:** Current lap, last lap, best lap, speed, and existing GPS quality indicators continue to work normally when delta is unavailable. Only the live delta readout is suppressed.

### Demo and Replay UAT Behavior

- **D-20:** Reuse Phase 3's provider-layer simulator model. The simulator replaces only the GPS provider layer and must not introduce a demo-only timing/session workflow.
- **D-21:** The simulated GPS feed is an independent, continuously running virtual GPS source. A developer starts the feed first; it continues to output samples as if the phone is physically moving around the track, even when no TimingSession is active.
- **D-22:** TimingSession start/stop uses the normal business flow. When a TimingSession starts, it consumes only samples received after session start from the same provider. Stopping a TimingSession does not require stopping the simulated GPS feed.
- **D-23:** Phase 4 UAT needs a deterministic continuous scenario with variable lap pace. It must cover slower laps that produce positive delta, faster laps that produce negative delta, and a new fastest lap that immediately becomes the reference for the following lap in the same active session.
- **D-24:** All simulated samples, sessions, references, exports, and review rows must remain visibly Demo/Simulated and must not mix into real per-track fastest-lap or ghost-candidate calculations.

### the agent's Discretion

Planner/executor may choose exact class names, DTO boundaries, interpolation strategy, and storage wiring, as long as the behavior above is preserved, the lap engine remains independent from UI/platform APIs, and all algorithmic behavior is testable with synthetic/replay data.

</decisions>

<canonical_refs>

## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Project Scope and Requirements

- `.planning/PROJECT.md` — Project scope: phone-first lap timing and ghost delta app; future glasses are downstream only.
- `.planning/REQUIREMENTS.md` — GHOST-01 through GHOST-04 and safety/GPS constraints.
- `.planning/ROADMAP.md` — Phase 4 goal, success criteria, and implementation notes.
- `.planning/STATE.md` — Current planning/execution state before Phase 4 planning.
- `AGENTS.md` — Workspace rules: phone companion is source of truth, clean-room lap engine, testable algorithms, explicit safety language.

### Prior Phase Context

- `.planning/phases/02-clean-room-lap-engine-v0/02-SUMMARY.md` — Existing shared lap engine, timing lines, sectors, replay fixtures, and dash integration.
- `.planning/phases/03-local-sessions-review-and-export/03-CONTEXT.md` — Phase 3 decisions for simulated GPS provider, Track/TimingSession model split, Review tab, export, and simulated-data isolation.
- `.planning/phases/03-local-sessions-review-and-export/03-01-SUMMARY.md` — Implemented provider-layer simulated GPS feed and deterministic fixtures.
- `.planning/phases/03-local-sessions-review-and-export/03-UI-SPEC.md` — Existing Drive/Timing UI density, fullscreen behavior, colors, and mounted-phone display constraints.

### Code Integration Points

- `shared/src/commonMain/kotlin/com/huanfuli/lapsight/shared/LocationSampleProvider.kt` — Provider boundary used by simulated GPS now and real GPS later.
- `shared/src/commonMain/kotlin/com/huanfuli/lapsight/shared/SimulatedGpsProvider.kt` — Existing continuous provider-layer simulator.
- `shared/src/commonMain/kotlin/com/huanfuli/lapsight/shared/fixtures/GpsFixtureLibrary.kt` — Existing deterministic GPS fixtures, including multi-session fastest-candidate data.

</canonical_refs>

<code_context>

## Existing Code Insights

### Reusable Assets

- `LocationSample` and `LocationSource` already carry elapsed time, lat/lon, accuracy, speed, heading, altitude, and source metadata needed by reference laps and progress curves.
- `LocationSampleProvider` is the correct seam for both simulated GPS and future real GPS. Phase 4 should consume this seam through normal timing/session logic.
- `SimulatedGpsProvider` already starts/stops independently from timing state and wraps samples with forward-moving timestamps.
- `GpsFixtureLibrary` already has deterministic geographic loops and multi-session best-candidate data; Phase 4 should add or select a ghost-delta-specific continuous variable-pace scenario if current fixtures are insufficient.
- Phase 2 lap engine/replay code provides the established pattern for deterministic algorithm tests.

### Established Patterns

- Lap engine/domain logic stays independent from UI and platform APIs.
- UI renders derived state; it must not own lap/ghost calculation.
- Synthetic replay and deterministic host tests are mandatory for algorithmic behavior.
- Simulated data must carry visible Demo/Simulated metadata.
- The mounted-phone dashboard prioritizes glanceable, passive UI while moving.

### Integration Points

- Add reference/ghost data structures near existing shared session/track/lap domain models, not in platform UI code.
- Extend lap timing state or dash state with a compact delta readout model.
- Wire storage so the current Track's fastest valid lap persists as a reference across sessions.
- Wire session completion so a new in-session best immediately updates the reference for following laps.
- Drive/Timing UI consumes only formatted delta state such as `-0.218s`, `+0.421s`, or `--`.

</code_context>

<specifics>

## Specific Ideas

- Delta display examples are exactly `-0.218s`, `+0.421s`, and `--`.
- The user explicitly prefers no words in the live delta readout because the page will contain many metrics.
- The simulator must validate the real business path: start simulated GPS first, then start/stop TimingSessions normally; do not use a special ghost test button that bypasses session logic.
- New best lap behavior must support drivers continuing to push after setting a faster lap during the same session.

</specifics>

<deferred>

## Deferred Ideas

- Map ghost animation.
- Cloud/shared ghost laps.
- External GNSS precision tuning.
- Meta glasses HUD delta rendering.
- Post-MVP professional telemetry charts over progress distance.
- Post-MVP delta-over-distance chart.
- Post-MVP speed curve comparison by progress.
- Post-MVP sector speed comparison.
- Post-MVP corner entry/exit speed analysis.
- Post-MVP multi-lap telemetry overlay.
- Future IMU/throttle/brake/steering channels aligned to the progress curve.

After MVP, remind the user to revisit telemetry charts/data because the Phase 4 progress curve is intentionally shaped to support that future work.

</deferred>

---

*Phase: 04-Ghost Lap + Live Delta*
*Context gathered: 2026-06-25T18:05:42-04:00*
