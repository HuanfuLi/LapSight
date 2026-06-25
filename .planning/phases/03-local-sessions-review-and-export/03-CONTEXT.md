# Phase 3: Local Sessions, Review, and Export - Context

**Gathered:** 2026-06-25T12:40:04-04:00
**Status:** Ready for planning

<domain>

## Phase Boundary

Phase 3 delivers the local-first data foundation for LapSight: deterministic GPS mock input, track marking, saved tracks, saved timing sessions, review screens, and export. The user must be able to create a track from a multi-loop GPS marking session, use that saved track for formal timing sessions, review saved history later, and export full-fidelity data for replay/debugging.

This phase should still respect the phone-first MVP boundary. It must not build the Meta glasses app, external GNSS integration, cloud sync, public-road racing features, map-tile navigation, or realtime ghost delta UI. Ghost work in Phase 3 is limited to preserving the data boundary needed to identify a per-track fastest valid lap later.

</domain>

<decisions>

## Implementation Decisions

### Deterministic GPS Mock and Fixture System

- **D-01:** Phase 3 must start by building a deterministic GPS mock/fixture system. Track marking, review, export, and UAT cannot depend on physically driving/riding 5-10 real laps for every test pass.
- **D-02:** Mock GPS data must be realistic latitude/longitude time-series data using the existing `LocationSample` shape: timestamp, latitude, longitude, accuracy, speed, heading, altitude, and source.
- **D-03:** The simulator should replace only the GPS provider layer. It must not add a separate demo-only business workflow for starting/stopping sessions.
- **D-04:** Provide both deterministic generators with fixed seeds and committed fixture files for key scenarios. Required scenarios: clean 10-loop marking/timing input, minimum 5-loop input, one bad/outlier loop, GPS noise/drift, dropped/low-frequency samples, and multiple sessions under one track for fastest-lap selection.
- **D-05:** Product UI exposes a simple Drive-screen button/toggle that starts a background simulated GPS feed. Once enabled, simulated samples flow continuously as if the phone were physically moving around the track, even before the user starts track marking or timing.

### Track Marking and Reference Line Semantics

- **D-06:** Track marking is not lap timing. A `TrackMarkingSession` is one continuous GPS trace that may contain 5-10 repeated loops, pit/paddock movement, mistakes, stops, and GPS drift.
- **D-07:** In the default motorsport/karting flow, the user must not be required to press Start exactly at the start/finish line. Users often leave from pit/paddock and only reach the real start/finish after an out segment.
- **D-08:** Do not split track marking into laps before start/finish exists. During track marking there is no confirmed start/finish line, so the app must not show lap times or depend on lap segmentation.
- **D-09:** The track reference line is extracted from the full continuous multi-loop marking trace. The algorithm should identify repeated spatial structure and filter outlier sections by shape/similarity/continuity/GPS quality rather than hard-coding out-lap, in-lap, time, distance, or half-lap rules.
- **D-10:** Save raw marking samples so future algorithms can recompute a better `TrackReferenceLine` without losing original evidence.
- **D-11:** After reference extraction, Track Review lets the user inspect the result and set/adjust start/finish and sector lines. Formal timing starts only after a saved `Track` has a confirmed start/finish line.
- **D-12:** A preset start/finish flow may exist as an optional convenience for cycling/fitness users who can begin physically at the start line, but the motorsport default is record-first, derive reference line, then set/adjust start/finish.

### Session Save Semantics

- **D-13:** Active timing sessions should persist continuously as auto-drafts.
- **D-14:** After Stop, show a simple end summary screen or sheet with explicit Save and Discard actions. Do not silently turn every stopped draft into a formal session.
- **D-15:** On app launch, if an unfinished draft exists, prompt the user to Resume, Save, or Discard.
- **D-16:** Discarded drafts do not become formal review history. Raw implementation details of deletion/recovery can be decided during planning, but the product state must stay explicit.

### Domain Entities and Data Boundary

- **D-17:** Model these as separate concepts:
  - `TrackMarkingSession`: raw continuous GPS capture used to create a track; no laps.
  - `TrackReferenceLine`: derived closed-loop geographic polyline from a marking session.
  - `Track`: saved course entity containing reference line, start/finish, sector lines, metadata, and link to source marking session.
  - `TimingSession`: formal lap-timing run linked to a saved Track.
  - `Lap`: timing result that exists only inside a TimingSession.
  - `GhostCandidate`: per-Track derived fastest valid lap candidate for future ghost/delta work.
- **D-18:** Keep `TrackReferenceLine` separate from the fastest lap. The reference line is a multi-loop spatial reference for track context; the fastest valid lap is a later ghost candidate.
- **D-19:** A `TimingSession` must associate with a saved `Track`. Without a Track/start-finish configuration, the app can record/mark a track but should not enter formal lap timing.
- **D-20:** `GhostCandidate` is derived from the fastest valid lap across TimingSessions for the same Track. Phase 3 may compute/store enough data for this, but realtime ghost delta UI belongs to Phase 4.

### Local Storage and Schema

- **D-21:** Use local-first storage with a lightweight metadata index plus canonical JSON payload files.
- **D-22:** Indexes serve list screens and summaries: track ID/name/createdAt/reference summary/best lap; session ID/track ID/date/duration/best lap/sample count/source.
- **D-23:** Heavy payloads are independent JSON files: Track payload, TrackMarkingSession payload, TimingSession payload.
- **D-24:** Internal saved JSON and exported JSON should share the same versioned schema where practical.
- **D-25:** Every saved payload must include `schemaVersion`, stable IDs, created timestamp, source metadata, and app/build metadata where available. Do not postpone schema versioning.

### App Navigation and Review Entry Points

- **D-26:** Phase 3 should introduce a persistent three-tab bottom navigation shell:
  - Drive / Timing: primary mounted-phone operating surface.
  - Review: saved history list and review/detail entry.
  - Settings: preferences and future account/login/device/GPS-provider settings.
- **D-27:** Review must be reachable from the bottom Review tab, not only immediately after completing a marking session or timing session.
- **D-28:** Review history should include both saved TimingSessions and TrackMarkingSessions/Tracks, with each row opening the appropriate detail screen.
- **D-29:** Drive / Timing must provide a fullscreen mounted-phone mode that maximizes essential timing information and hides nonessential navigation/chrome while moving.

### Review Information Hierarchy

- **D-30:** Track Review and Timing Session Review are separate flows.
- **D-31:** Track Review confirms whether a Track can be saved/used. It prioritizes reference line preview, save readiness, GPS/capture quality summary, start/finish editing, sector editing, and Save/Re-record/Discard actions. It must not show lap times for the marking session.
- **D-32:** Timing Session Review prioritizes Track name, date, total duration, best lap, lap list, sector splits, GPS quality, session trace over reference line, export actions, and per-track best/ghost-candidate status such as "new track best" when available.

### Trace Rendering

- **D-33:** Use an offline vector trace view in Phase 3. Do not integrate Google/Apple map tiles yet.
- **D-34:** Latitude/longitude GPS samples and derived geographic polylines are canonical. Screen coordinates are only rendering projections and must not replace saved geographic data.
- **D-35:** Track Review trace shows the full marking trace as context, extracted `TrackReferenceLine` highlighted, start/finish, sector lines, and noisy/outlier sections when available.
- **D-36:** Timing Session Review trace shows `TrackReferenceLine` as baseline, the session trace, start/finish, sector lines, and hooks for selected/best lap highlighting.
- **D-37:** Defer speed heatmaps, 3D/elevation analysis, map matching, and map-tile backgrounds.

### Export Strategy

- **D-38:** JSON is the primary canonical/debug export format. It must preserve Track, TrackMarkingSession raw samples, TrackReferenceLine, TimingSession raw samples, lap events, sector events, start/finish, sector lines, GPS quality summary, schemaVersion, appVersion/build, and source metadata.
- **D-39:** GPX is a compatibility export for external GPS tools. Phase 3 should at least export track/session GPS points. Complex LapSight-specific structures can remain full-fidelity only in JSON.
- **D-40:** Export actions live on Review detail screens: Track Review exports a Track bundle; Timing Session Review exports a Session bundle. Batch export from the Review list is deferred.
- **D-41:** Use readable LapSight-prefixed filenames containing entity type, track name when available, date/time, and extension, e.g. `LapSight_Track_<trackName>_<date>.json` and `LapSight_Session_<trackName>_<date>.gpx`.

### Demo Data Labeling and Isolation

- **D-42:** All simulator-generated samples, tracks, sessions, review rows, and exports must carry visible Demo/Simulated source metadata.
- **D-43:** Demo data may be saved locally for UAT, but must not mix into real per-track fastest-lap/ghost-candidate calculations.
- **D-44:** The product UI should expose only a small, clear demo control. Detailed fixture scenario selection belongs in tests/development, not the main user flow.

### the agent's Discretion

- The exact implementation of reference-line extraction is left to planning/research, as long as it respects the product semantics above: no start/finish-dependent lap splitting during marking, preserve raw samples, derive from repeated spatial structure, and expose an inspectable Track Review result.
- The exact storage backend and file layout may be chosen during planning, provided it remains local-first, versioned, index-backed for lists, and JSON-export-compatible.
- UI styling and component decomposition are flexible, but must preserve the mounted-phone safety constraints and the three-tab shell.

</decisions>

<canonical_refs>

## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Project and Planning

- `.planning/PROJECT.md` — phone-first product scope, closed-course safety, GPL constraints, KMP/CMP direction, and future Meta glasses boundary.
- `.planning/REQUIREMENTS.md` — Phase 3 requirement IDs SESS-01 through SESS-05 and related GPS/GHOST/SAFE/ARCH constraints.
- `.planning/ROADMAP.md` — Phase 3 goal, success criteria, and local-first/export notes.
- `.planning/STATE.md` — current repository state: Phase 2 complete, simulator/replay-backed lap timing exists, real GPS/storage/ghost/maps deferred.
- `AGENTS.md` — workspace-specific agent rules: phone companion is source of truth; do not build glasses app first; keep lap engine independent and testable.

### Research and Prior Phase Context

- `.planning/research/STACK.md` — stack direction for KMP, Compose Multiplatform, Android Fused Location Provider, and iOS Core Location.
- `.planning/research/LAP_ENGINE.md` — lap engine reference research and clean-room/GPL boundary.
- `.planning/phases/01-mobile-walking-skeleton-gps-probe/01-CONTEXT.md` — Phase 1 decisions for mounted-phone dash, GPS probe models, simulator fallback, and safety copy.
- `.planning/phases/02-clean-room-lap-engine-v0/02-SUMMARY.md` — Phase 2 delivered shared lap engine, timing lines, sectors, replay runner, fixtures, and dash integration.
- `.planning/phases/02-clean-room-lap-engine-v0/02-VERIFICATION.md` — Phase 2 verification and remaining UAT/platform gaps.

### Existing Source Integration Points

- `shared/src/commonMain/kotlin/com/huanfuli/lapsight/shared/GpsProbeModels.kt` — `LocationSample`, `LocationSource`, `GpsFixStatus`, and existing simulator state.
- `shared/src/commonMain/kotlin/com/huanfuli/lapsight/shared/lap/TimingLines.kt` — `StartFinishLine`, `SectorLine`, `CourseDefinition`.
- `shared/src/commonMain/kotlin/com/huanfuli/lapsight/shared/lap/LapModels.kt` — `LapEvent`, `SectorEvent`, `LapTimingState`, reject reasons, sector state.
- `shared/src/commonMain/kotlin/com/huanfuli/lapsight/shared/lap/LapEngine.kt` — existing engine that TimingSession should feed after a Track has start/finish and sectors.
- `shared/src/commonMain/kotlin/com/huanfuli/lapsight/shared/lap/ReplayRunner.kt` — deterministic replay primitive for tests and fixture validation.
- `shared/src/commonMain/kotlin/com/huanfuli/lapsight/shared/lap/ReplayFixtures.kt` — current synthetic fixtures; Phase 3 should replace/extend with richer geographic multi-loop fixtures.
- `shared/src/commonMain/kotlin/com/huanfuli/lapsight/shared/lap/DemoLapSession.kt` — current demo driver; Phase 3 should evolve from session-owned replay into provider-layer simulated GPS feed.
- `shared/src/commonMain/kotlin/com/huanfuli/lapsight/shared/App.kt` — current Compose dash; Phase 3 should refactor into a three-tab shell plus fullscreen Drive/Timing surface.

</canonical_refs>

<code_context>

## Existing Code Insights

### Reusable Assets

- `LocationSample` already carries the exact fields Phase 3 needs for canonical GPS samples: elapsed time, lat/lon, horizontal accuracy, speed, heading, altitude, and source.
- `LocationSource` already distinguishes `Simulated`, `PhoneGps`, and `ExternalGnss`; Phase 3 should preserve and surface this in saved data, review rows, and exports.
- `StartFinishLine`, `SectorLine`, and `CourseDefinition` already model the two-point timing lines required once a Track is ready for formal timing.
- `LapEvent`, `SectorEvent`, and `LapTimingState` already provide the timing result structures that TimingSession persistence should capture.
- `ReplayRunner` and `ReplayFixtures` provide the current deterministic test pattern, but Phase 3 needs a richer GPS fixture library that models continuous multi-loop track marking and long-running simulated provider behavior.
- `DemoLapSession` proves a replay-driven dash path, but its current lifecycle is session-owned. Phase 3 should move simulation down into a GPS provider/feed abstraction so normal business flows consume it unchanged.

### Established Patterns

- Shared domain logic lives in `shared/src/commonMain` and must remain independent of Compose, Android, iOS, and storage APIs.
- UI renders derived state; lap logic stays in the shared engine and replay/domain layers.
- Synthetic replay and deterministic host tests are mandatory for algorithmic behavior.
- Safety language must stay visible: closed-course/private-track use, GPS accuracy limitations, passive/glanceable interaction while moving.
- Orientation is explicitly user-controlled for mounted-phone safety; Phase 3 fullscreen mode should preserve that intent.

### Integration Points

- Add a provider/feed abstraction that can emit `LocationSample` from either simulated GPS now or real platform GPS later.
- Add shared domain/storage models for TrackMarkingSession, TrackReferenceLine, Track, TimingSession, and export payloads.
- Connect formal TimingSession sample streams into the existing `LapEngine` only after a Track supplies `CourseDefinition`.
- Refactor the existing `LapSightApp`/dash into a Drive/Timing tab inside a bottom navigation shell.
- Add Review tab list/detail flows backed by the local metadata index and canonical payload files.
- Add export services that serialize the same canonical payloads used by local storage where practical.

</code_context>

<specifics>

## Specific Ideas

- Track marking should collect 5-10 repeated loops when possible, but should not represent those loops as timed laps until a Track has start/finish.
- Motorsports/karting default starts from pit/paddock: record first, extract reference line, then set or adjust start/finish.
- Cycling/fitness may later use a quicker flow where the user starts at the physical start/finish and pre-seeds the line.
- Review history should include both track-marking/track entries and timing-session entries.
- Drive/Timing fullscreen mode should hide bottom navigation and nonessential controls while preserving large, high-contrast timing fields.
- Demo mode should be a background simulated GPS feed started from the first/Drive screen; users then exercise the normal Track Marking, Start, Stop, Save, Review, and Export flows.

</specifics>

<deferred>

## Deferred Ideas

- Real Android Fused Location Provider and iOS Core Location providers.
- Realtime ghost delta UI.
- External GNSS support.
- Meta glasses HUD bridge.
- Map-tile-backed trace rendering.
- Nearby-track auto matching from latitude/longitude.
- Batch export from the Review list.
- Speed heatmaps, braking/acceleration analysis, 3D/elevation analysis, and map matching.
- Cloud accounts, sync, shared tracks, login, and social features.

</deferred>

---

*Phase: 03-local-sessions-review-and-export*
*Context gathered: 2026-06-25T12:40:04-04:00*
