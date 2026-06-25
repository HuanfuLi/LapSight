# Phase 3: Local Sessions, Review, and Export - Research

**Researched:** 2026-06-25
**Domain:** Kotlin Multiplatform local session persistence, GPS fixture simulation, review, and export
**Confidence:** HIGH for stack/storage/export patterns; MEDIUM for reference-line extraction thresholds

<user_constraints>
## User Constraints (from CONTEXT.md)

Source for this entire section: [VERIFIED: .planning/phases/03-local-sessions-review-and-export/03-CONTEXT.md]

### Locked Decisions

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

### Deferred Ideas (OUT OF SCOPE)

- Real Android Fused Location Provider and iOS Core Location providers.
- Realtime ghost delta UI.
- External GNSS support.
- Meta glasses HUD bridge.
- Map-tile-backed trace rendering.
- Nearby-track auto matching from latitude/longitude.
- Batch export from the Review list.
- Speed heatmaps, braking/acceleration analysis, 3D/elevation analysis, and map matching.
- Cloud accounts, sync, shared tracks, login, and social features.
</user_constraints>

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|------------------|
| SESS-01 | User can start, stop, save, and discard a timing session. [VERIFIED: .planning/REQUIREMENTS.md] | Use provider-layer `LocationSample` flow, explicit draft state machine, canonical draft/session payloads, and Save/Discard transitions. [VERIFIED: codebase grep; VERIFIED: .planning/phases/03-local-sessions-review-and-export/03-CONTEXT.md] |
| SESS-02 | User can review a saved session with lap list, best lap, total duration, and GPS quality summary. [VERIFIED: .planning/REQUIREMENTS.md] | Persist `LapEvent`, sector events, raw samples, and computed `SessionSummary` in index rows, while preserving replayable payloads. [VERIFIED: codebase grep; ASSUMED] |
| SESS-03 | User can view a simple track trace for a saved session. [VERIFIED: .planning/REQUIREMENTS.md] | Render canonical lat/lon via local projection into Compose Canvas; do not persist screen coordinates. [CITED: https://developer.android.com/develop/ui/compose/graphics/draw/overview; VERIFIED: .planning/phases/03-local-sessions-review-and-export/03-CONTEXT.md] |
| SESS-04 | User can export a session as JSON. [VERIFIED: .planning/REQUIREMENTS.md] | Use `kotlinx.serialization-json` for versioned DTOs and reuse the same payload schema for saved and exported JSON where practical. [CITED: https://github.com/Kotlin/kotlinx.serialization; VERIFIED: Maven metadata] |
| SESS-05 | User can export a session as GPX or another common GPS interchange format. [VERIFIED: .planning/REQUIREMENTS.md] | Emit GPX 1.1 track points from raw sample lat/lon plus optional elevation/time; keep LapSight-specific data in JSON. [CITED: https://www.topografix.com/gpx/1/1/] |
</phase_requirements>

## Project Constraints (from AGENTS.md)

- LapSight is a phone-first lap timing and ghost delta app; the phone companion app remains the source of truth for GPS, sessions, lap engine state, and future Meta glasses HUD output. [VERIFIED: AGENTS.md]
- Do not treat the product as a generic fitness tracker or smartwatch dashboard. [VERIFIED: AGENTS.md]
- Use Kotlin Multiplatform for shared domain logic and Compose Multiplatform for the initial shared UI unless real-device validation changes the direction. [VERIFIED: AGENTS.md; VERIFIED: .planning/research/STACK.md]
- Android location uses Fused Location Provider and iOS location uses Core Location later; Phase 3 keeps real providers deferred per context. [VERIFIED: AGENTS.md; VERIFIED: .planning/phases/03-local-sessions-review-and-export/03-CONTEXT.md]
- Keep the lap engine independent from UI and platform APIs. [VERIFIED: AGENTS.md]
- Every algorithmic behavior must be testable with synthetic or recorded replay data. [VERIFIED: AGENTS.md]
- Safety language must stay explicit: closed-course/private-track use, passive UI while moving, and no public-road racing positioning. [VERIFIED: AGENTS.md]
- Do not build the glasses app before the phone companion produces reliable timing state. [VERIFIED: AGENTS.md]
- Do not copy GPL-licensed code from DovesLapTimer or DovesDataViewer unless a project license decision explicitly allows it. [VERIFIED: AGENTS.md]

## Summary

Phase 3 should be planned as a vertical local-data foundation, not as a UI-only history screen. [VERIFIED: .planning/ROADMAP.md; VERIFIED: .planning/phases/03-local-sessions-review-and-export/03-CONTEXT.md] The phase must first refactor the current replay-owned demo path into a provider-layer simulated GPS feed that emits the existing `LocationSample` model, because all track marking, timing, storage, review, and export flows must consume the same sample stream. [VERIFIED: codebase grep; VERIFIED: .planning/phases/03-local-sessions-review-and-export/03-CONTEXT.md]

Use versioned Kotlin data transfer objects serialized with `kotlinx.serialization-json`, stored with Okio in app-private local files, and indexed by a lightweight metadata file for Review lists. [CITED: https://github.com/Kotlin/kotlinx.serialization; CITED: https://square.github.io/okio/file_system/; VERIFIED: .planning/phases/03-local-sessions-review-and-export/03-CONTEXT.md] The planner should add human verification checkpoints before installing the new Maven artifacts because `slopcheck` 0.6.1 flagged Maven coordinates as `SUS`, even though the packages are documented by official upstream projects and verified on Maven/Gradle registries. [VERIFIED: slopcheck; VERIFIED: Maven metadata; CITED: https://github.com/Kotlin/kotlinx.serialization; CITED: https://square.github.io/okio/]

Reference-line extraction is the main uncertain technical area. [ASSUMED] The recommended MVP approach is a deterministic, test-first spatial repeat detector: convert raw samples to local meters, find repeated proximity/heading anchors, split candidate loops from those anchors, score loops by closure, continuity, GPS quality, length, and shape similarity, reject outliers by robust median/MAD-style thresholds, and average accepted loops by normalized distance to produce a `TrackReferenceLine` while preserving all raw marking samples. [ASSUMED]

**Primary recommendation:** Plan Phase 3 around a shared `session/track/storage/export` domain slice: simulated provider -> track marking/timing recorders -> versioned JSON file store -> Review list/detail -> JSON/GPX export, with Compose UI only rendering state and traces. [VERIFIED: codebase grep; ASSUMED]

## Architectural Responsibility Map

| Capability | Primary Tier | Secondary Tier | Rationale |
|------------|--------------|----------------|-----------|
| Simulated GPS feed | Shared Domain (`commonMain`) | Compose UI toggle | Samples must flow through the same provider boundary as future real GPS; UI only starts/stops feed exposure. [VERIFIED: .planning/phases/03-local-sessions-review-and-export/03-CONTEXT.md; ASSUMED] |
| Track marking capture | Shared Domain (`commonMain`) | Local Storage | Marking is continuous sample capture and reference extraction, independent of Compose/platform APIs. [VERIFIED: AGENTS.md; VERIFIED: .planning/phases/03-local-sessions-review-and-export/03-CONTEXT.md] |
| Reference-line extraction | Shared Domain (`commonMain`) | Tests/fixtures | Algorithmic behavior must be replay-testable and must not depend on UI or platform APIs. [VERIFIED: AGENTS.md; ASSUMED] |
| Formal timing sessions | Shared Domain (`commonMain`) | Existing `LapEngine` | `TimingSession` feeds samples into the clean-room `LapEngine` only after a saved `Track` supplies `CourseDefinition`. [VERIFIED: codebase grep; VERIFIED: .planning/phases/03-local-sessions-review-and-export/03-CONTEXT.md] |
| Metadata index and payload files | Local Storage | Platform app directory adapter | Index/payload persistence belongs to the storage layer; platform code only supplies app-private root paths/share targets. [CITED: https://square.github.io/okio/file_system/; CITED: https://developer.android.com/training/data-storage/app-specific; CITED: https://kotlinlang.org/docs/multiplatform/multiplatform-expect-actual.html] |
| Review list/detail state | Shared Presentation + Compose UI | Local Storage | Review rows come from metadata index; detail screens load full payloads and render derived state. [VERIFIED: .planning/phases/03-local-sessions-review-and-export/03-CONTEXT.md; ASSUMED] |
| Offline trace rendering | Compose UI | Shared projection helpers | Canonical lat/lon stays in saved data; Canvas receives projected screen points for display only. [CITED: https://developer.android.com/develop/ui/compose/graphics/draw/overview; VERIFIED: codebase grep] |
| JSON export | Shared Export Service | Platform share/file handoff | JSON should reuse canonical payload DTOs; platform code only writes/shares the exported file. [CITED: https://github.com/Kotlin/kotlinx.serialization; VERIFIED: .planning/phases/03-local-sessions-review-and-export/03-CONTEXT.md] |
| GPX export | Shared Export Service | Platform share/file handoff | GPX is a compatibility boundary containing GPS track points; LapSight-specific structures remain in JSON. [CITED: https://www.topografix.com/gpx/1/1/; VERIFIED: .planning/phases/03-local-sessions-review-and-export/03-CONTEXT.md] |

## Standard Stack

### Core

| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| Kotlin Multiplatform Gradle plugin | 2.4.0 | Shared Android/iOS domain and UI module compilation. | Already used by the project in `gradle/libs.versions.toml`. [VERIFIED: codebase grep] |
| Android Kotlin Multiplatform library plugin | AGP 9.0.1 | Android target integration for the shared KMP module. | Already used by `shared/build.gradle.kts`; Android documents the Android-KMP plugin as the supported tool for Android targets in KMP libraries. [VERIFIED: codebase grep; CITED: https://developer.android.com/kotlin/multiplatform/plugin] |
| Compose Multiplatform | 1.11.1 | Shared UI for Drive, Review, Settings, trace views, and state rendering. | Already used by the project; Compose Multiplatform supports Android/iOS shared UI. [VERIFIED: codebase grep; CITED: https://kotlinlang.org/compose-multiplatform/] |
| Compose Material3 | 1.11.0-alpha07 | Existing Material components including buttons/cards; use `NavigationBar`/`NavigationBarItem` for the 3-tab shell. | Material3 navigation bars are intended for 3-5 persistent destinations. [VERIFIED: codebase grep; CITED: https://kotlinlang.org/api/compose-multiplatform/material3/androidx.compose.material3/-navigation-bar.html] |
| kotlinx.coroutines | 1.11.0 | Provider-layer simulated GPS stream and UI/state collection. | Already used by the project in `commonMain`. [VERIFIED: codebase grep] |
| Kotlin serialization Gradle plugin | 2.4.0 | Compiler plugin for `@Serializable` DTOs. | The serialization plugin version must match the Kotlin compiler version; the project uses Kotlin 2.4.0. [CITED: https://github.com/Kotlin/kotlinx.serialization; VERIFIED: Gradle Plugin Portal metadata] `org.jetbrains.kotlin.plugin.serialization:org.jetbrains.kotlin.plugin.serialization.gradle.plugin` [WARNING: slopcheck flagged as suspicious - verify before using.] |
| kotlinx.serialization JSON | 1.11.0 | Canonical saved/exported JSON schemas. | Official docs show `kotlinx-serialization-json` as stable JSON support on all supported platforms. [CITED: https://kotlinlang.org/docs/serialization.html; VERIFIED: Maven metadata] `org.jetbrains.kotlinx:kotlinx-serialization-json` [WARNING: slopcheck flagged as suspicious - verify before using.] |
| Okio | 3.17.0 | Multiplatform file reads/writes, paths, fake filesystem tests, and atomic file moves. | Okio documents testable multiplatform `FileSystem`, `FakeFileSystem`, and `atomicMove()`. [CITED: https://square.github.io/okio/file_system/; VERIFIED: Maven metadata] `com.squareup.okio:okio` [WARNING: slopcheck flagged as suspicious - verify before using.] |
| Kotlin stdlib `kotlin.time.Clock` / `Instant` | Kotlin 2.4 API | Created timestamps, UTC ISO strings, and export point times derived from session start plus elapsed millis. | Kotlin 2.4 API docs provide `Clock`, `Instant`, `fromEpochMilliseconds`, `toEpochMilliseconds`, and ISO string conversion. [CITED: https://kotlinlang.org/api/core/kotlin-stdlib/kotlin.time/-instant/; CITED: https://kotlinlang.org/api/core/kotlin-stdlib/kotlin.time/-clock/] |

### Supporting

| Library | Version | Purpose | When to Use |
|---------|---------|---------|-------------|
| kotlin.test | 2.4.0 | Shared host tests for serializers, reference extraction, store round trips, exporter output, and recovery. | Already configured in `shared/src/commonTest`; continue using it for pure shared tests. [VERIFIED: codebase grep] |
| Compose Resources | 1.11.1 via existing Compose plugin | Committed raw fixture files that the app/tests can load when needed. | Use `composeResources/files/fixtures/*.json` for app-bundled demo fixtures, because Compose Multiplatform documents raw files with `Res.readBytes(path)`. [CITED: https://kotlinlang.org/docs/multiplatform/compose-multiplatform-resources-usage.html; ASSUMED] |
| Existing `LapEngine`, `ReplayRunner`, `ReplayFixtures` | Project code | Timing-session result generation and fixture regression baseline. | Reuse after a `Track` has confirmed start/finish and sectors; do not reimplement lap timing in storage/UI. [VERIFIED: codebase grep; VERIFIED: AGENTS.md] |

### Alternatives Considered

| Instead of | Could Use | Tradeoff |
|------------|-----------|----------|
| Okio file store | SQLDelight or Room | Database indexes are useful later, but locked Phase 3 decisions require canonical JSON payload files and only a lightweight metadata index. [VERIFIED: .planning/phases/03-local-sessions-review-and-export/03-CONTEXT.md; ASSUMED] |
| Okio file store | AndroidX DataStore | DataStore is better for preferences/small typed state than large independent session payload files. [ASSUMED] |
| Kotlin stdlib `Instant` | `kotlinx-datetime` | `kotlinx-datetime` 0.8.0 exists, but its docs state the library is experimental and Phase 3 only needs UTC instants/ISO strings. [CITED: https://github.com/Kotlin/kotlinx-datetime; CITED: https://kotlinlang.org/api/core/kotlin-stdlib/kotlin.time/-instant/] |
| State-based local routes | Navigation Compose | Phase 3 has three top-level tabs plus simple detail drills; avoid a new navigation dependency until deep links/back-stack restoration become real requirements. [CITED: https://kotlinlang.org/api/compose-multiplatform/material3/androidx.compose.material3/-navigation-bar.html; ASSUMED] |
| Offline vector trace | Google/Apple map tiles | Map tiles are explicitly deferred; Canvas is sufficient for normalized GPS traces. [VERIFIED: .planning/phases/03-local-sessions-review-and-export/03-CONTEXT.md; CITED: https://developer.android.com/develop/ui/compose/graphics/draw/overview] |

**Installation:**

```toml
# gradle/libs.versions.toml
[versions]
kotlinx-serialization = "1.11.0"
okio = "3.17.0"

[libraries]
kotlinx-serialization-json = { module = "org.jetbrains.kotlinx:kotlinx-serialization-json", version.ref = "kotlinx-serialization" }
okio = { module = "com.squareup.okio:okio", version.ref = "okio" }

[plugins]
kotlinSerialization = { id = "org.jetbrains.kotlin.plugin.serialization", version.ref = "kotlin" }
```

```kotlin
// build.gradle.kts
plugins {
    alias(libs.plugins.kotlinSerialization) apply false
}

// shared/build.gradle.kts
plugins {
    alias(libs.plugins.kotlinSerialization)
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.okio)
        }
    }
}
```

**Version verification:** The current project uses Kotlin 2.4.0, Compose Multiplatform 1.11.1, coroutines 1.11.0, and Material3 1.11.0-alpha07 in `gradle/libs.versions.toml`. [VERIFIED: codebase grep] Direct Maven metadata returned `kotlinx-serialization-json` release `1.11.0` with `lastUpdated=20260409180901` and Okio release `3.17.0` with `lastUpdated=20260311215919`. [VERIFIED: Maven metadata] Gradle Plugin Portal metadata contains `org.jetbrains.kotlin.plugin.serialization` version `2.4.0`, matching the project compiler. [VERIFIED: Gradle Plugin Portal metadata]

## Package Legitimacy Audit

> Required because this phase recommends external Maven/Gradle packages. `slopcheck` 0.6.1 was installed and available, but this version does not support the requested `--json` flag. [VERIFIED: slopcheck] `slopcheck install --ecosystem maven ...` flagged all Maven coordinates as `SUS` with low-download/no-source messages; this appears to be a low-fidelity Maven heuristic because official project docs and registry metadata identify the upstream projects. [VERIFIED: slopcheck; CITED: https://github.com/Kotlin/kotlinx.serialization; CITED: https://square.github.io/okio/]

| Package | Registry | Age | Downloads | Source Repo | slopcheck | Disposition |
|---------|----------|-----|-----------|-------------|-----------|-------------|
| `org.jetbrains.kotlin.plugin.serialization:org.jetbrains.kotlin.plugin.serialization.gradle.plugin` | Gradle Plugin Portal/Maven | Version 2.4.0 present; project-match plugin POM verified. [VERIFIED: Gradle Plugin Portal metadata] | Slopcheck reported "Only 77 downloads"; Maven download count not independently verified. [VERIFIED: slopcheck] | https://github.com/JetBrains/kotlin [CITED: Gradle plugin POM] | SUS | Flagged - planner must add `checkpoint:human-verify` before install/apply. |
| `org.jetbrains.kotlinx:kotlinx-serialization-json` | Maven Central | 1.11.0 published 2026-04-09/10. [VERIFIED: Maven metadata; CITED: https://github.com/Kotlin/kotlinx.serialization/blob/master/CHANGELOG.md] | Slopcheck reported "Only 33 downloads"; Maven download count not independently verified. [VERIFIED: slopcheck] | https://github.com/Kotlin/kotlinx.serialization [CITED: https://github.com/Kotlin/kotlinx.serialization] | SUS | Flagged - planner must add `checkpoint:human-verify` before install. |
| `com.squareup.okio:okio` | Maven Central | 3.17.0 published 2026-03-11. [VERIFIED: Maven metadata; CITED: https://github.com/square/okio/blob/master/CHANGELOG.md] | Slopcheck reported "Only 78 downloads"; Maven download count not independently verified. [VERIFIED: slopcheck] | https://github.com/square/okio [CITED: https://square.github.io/okio/] | SUS | Flagged - planner must add `checkpoint:human-verify` before install. |

**Packages removed due to slopcheck [SLOP] verdict:** none. [VERIFIED: slopcheck]
**Packages flagged as suspicious [SUS]:** Kotlin serialization Gradle plugin, `kotlinx-serialization-json`, and Okio. [VERIFIED: slopcheck]

## Architecture Patterns

### System Architecture Diagram

```text
Drive tab
  |
  | user enables simulated feed / starts marking or timing
  v
LocationSampleProvider (Simulated now; real platform providers later)
  |
  | Flow<LocationSample>
  v
SessionController
  |                         \
  | marking mode             \ timing mode requires saved Track
  v                          v
TrackMarkingRecorder       TimingSessionRecorder -> LapEngine(CourseDefinition)
  |                          |
  | raw continuous samples    | raw samples + LapEvent/SectorEvent state
  v                          v
ReferenceLineExtractor     DraftSessionStore
  |                          |
  v                          v
Track Review              Stop Summary -> Save or Discard
  |                          |
  | Save Track               | Save TimingSession
  v                          v
LocalSessionStore (metadata index + payload JSON files)
  |
  v
Review tab list -> Track detail / Session detail
  |              \
  |               \ offline vector trace (Canvas projection only)
  v
ExportService -> JSON bundle or GPX track file -> platform share/save boundary
```

This data flow keeps raw lat/lon samples canonical and routes all algorithmic behavior through testable shared code. [VERIFIED: AGENTS.md; VERIFIED: .planning/phases/03-local-sessions-review-and-export/03-CONTEXT.md; ASSUMED]

### Recommended Project Structure

```text
shared/src/commonMain/kotlin/com/huanfuli/lapsight/shared/
+-- gps/
|   +-- LocationSampleProvider.kt        # Flow boundary for simulated and future real providers
|   +-- SimulatedGpsProvider.kt          # deterministic provider-layer feed
|   +-- GpsQualitySummary.kt             # sample-count, accuracy, rate, source rollups
+-- track/
|   +-- TrackModels.kt                   # TrackMarkingSession, TrackReferenceLine, Track
|   +-- ReferenceLineExtractor.kt        # repeated-structure extraction
|   +-- TrackReviewState.kt              # save readiness + quality flags
+-- session/
|   +-- SessionModels.kt                 # TimingSession, SessionDraft, Lap persistence DTOs
|   +-- SessionController.kt             # start/stop/save/discard/recover state machine
|   +-- TimingSessionRecorder.kt         # feeds LapEngine after Track exists
+-- storage/
|   +-- LocalSessionStore.kt             # repository API
|   +-- FileSessionStore.kt              # Okio implementation
|   +-- StoragePaths.kt                  # platform-supplied app-private roots
|   +-- SchemaVersions.kt                # v1 constants and migration stubs
+-- export/
|   +-- JsonExportService.kt             # canonical/debug bundle
|   +-- GpxExportService.kt              # compatibility GPS track export
|   +-- ExportFileNames.kt               # safe LapSight-prefixed names
+-- review/
|   +-- ReviewModels.kt                  # list rows/detail state
+-- ui/
    +-- AppShell.kt                      # Drive/Review/Settings shell
    +-- DriveScreen.kt                   # mounted-phone controls and fullscreen mode
    +-- ReviewScreen.kt                  # history list/detail routing
    +-- TraceView.kt                     # offline vector trace rendering

shared/src/commonMain/composeResources/files/fixtures/
+-- clean-10-loop.json
+-- minimum-5-loop.json
+-- one-outlier-loop.json
+-- noise-drift.json
+-- dropped-low-frequency.json
+-- multi-session-best-candidate.json

shared/src/commonTest/kotlin/com/huanfuli/lapsight/shared/
+-- gps/
+-- track/
+-- session/
+-- storage/
+-- export/
```

The fixture location uses Compose Multiplatform raw resources, which are documented as loadable via `Res.readBytes(path)` from `composeResources/files`. [CITED: https://kotlinlang.org/docs/multiplatform/compose-multiplatform-resources-usage.html] Keep the reference extractor and storage repository independent from Compose resource APIs; Compose resources are only the committed fixture delivery mechanism. [ASSUMED]

### Pattern 1: Provider-Layer Simulated GPS

**What:** Replace the current `DemoLapSession` sample ownership with a `LocationSampleProvider` interface that emits `LocationSample` as a continuous stream. [VERIFIED: codebase grep; VERIFIED: .planning/phases/03-local-sessions-review-and-export/03-CONTEXT.md]

**When to use:** Use for all Drive, Track Marking, and Timing flows in Phase 3 so the simulator proves the real workflow rather than a demo-only workflow. [VERIFIED: .planning/phases/03-local-sessions-review-and-export/03-CONTEXT.md]

**Example:**

```kotlin
// Source: existing LocationSample model and phase D-03/D-05.
interface LocationSampleProvider {
    val samples: kotlinx.coroutines.flow.Flow<LocationSample>
    fun start()
    fun stop()
}

class SimulatedGpsProvider(
    private val fixture: List<LocationSample>,
    private val tickMillis: Long = 250L,
) : LocationSampleProvider {
    private val running = kotlinx.coroutines.flow.MutableStateFlow(false)

    override val samples = running.flatMapLatest { isRunning ->
        if (!isRunning) emptyFlow() else flow {
            var index = 0
            while (true) {
                emit(fixture[index % fixture.size])
                index += 1
                delay(tickMillis)
            }
        }
    }

    override fun start() { running.value = true }
    override fun stop() { running.value = false }
}
```

### Pattern 2: Versioned Canonical JSON Payloads

**What:** Define storage/export DTOs with `@Serializable`, explicit `schemaVersion`, stable IDs, created timestamps, source metadata, app metadata, and raw samples. [CITED: https://github.com/Kotlin/kotlinx.serialization; VERIFIED: .planning/phases/03-local-sessions-review-and-export/03-CONTEXT.md]

**When to use:** Use for Track payloads, TrackMarkingSession payloads, TimingSession payloads, metadata index rows, and JSON export bundles. [VERIFIED: .planning/phases/03-local-sessions-review-and-export/03-CONTEXT.md]

**Example:**

```kotlin
// Source: Kotlin serialization setup docs.
@kotlinx.serialization.Serializable
data class TimingSessionPayloadV1(
    val schemaVersion: Int = 1,
    val id: String,
    val trackId: String,
    val createdAtEpochMillis: Long,
    val source: SessionSourceDto,
    val app: AppMetadataDto,
    val samples: List<LocationSampleDto>,
    val laps: List<LapEventDto>,
    val sectors: List<SectorEventDto>,
    val course: CourseDefinitionDto,
    val gpsQuality: GpsQualitySummaryDto,
)

private val canonicalJson = kotlinx.serialization.json.Json {
    prettyPrint = true
    explicitNulls = false
    encodeDefaults = true
    ignoreUnknownKeys = true
}
```

### Pattern 3: Atomic File Store with Index Plus Payloads

**What:** Write each heavy payload independently, then update a small `index.json` for list screens. Use temp files and atomic move to reduce partial-write corruption. [CITED: https://square.github.io/okio/file_system/; VERIFIED: .planning/phases/03-local-sessions-review-and-export/03-CONTEXT.md]

**When to use:** Use for Save Track, Save TimingSession, draft checkpoint, draft recovery, and export file creation. [ASSUMED]

**Example:**

```kotlin
// Source: Okio FileSystem docs; adapted for LapSight storage.
class FileSessionStore(
    private val fileSystem: okio.FileSystem,
    private val root: okio.Path,
    private val json: kotlinx.serialization.json.Json,
) {
    fun writeTimingSession(payload: TimingSessionPayloadV1) {
        val path = root / "sessions" / "${payload.id}.json"
        val tmp = root / "tmp" / "${payload.id}.json.tmp"
        fileSystem.createDirectories(path.parent!!)
        fileSystem.createDirectories(tmp.parent!!)
        fileSystem.write(tmp) {
            writeUtf8(json.encodeToString(payload))
        }
        fileSystem.atomicMove(tmp, path)
    }
}
```

### Pattern 4: Continuous Marking Trace, Then Reference Extraction

**What:** `TrackMarkingSession` stores one continuous trace with no `Lap` objects; extraction produces `TrackReferenceLine` and quality diagnostics after capture. [VERIFIED: .planning/phases/03-local-sessions-review-and-export/03-CONTEXT.md]

**When to use:** Use whenever a user records a track before start/finish exists. [VERIFIED: .planning/phases/03-local-sessions-review-and-export/03-CONTEXT.md]

**Recommended extraction algorithm:** Convert samples to local meters; remove invalid/very poor quality samples for extraction only; build spatial hash buckets; find repeated anchor candidates where points return within a radius with compatible heading and enough separation in time/distance; derive candidate loops between anchors; resample each candidate by normalized arclength; score loops by closure distance, length consistency, heading continuity, GPS quality, and shape RMS distance to the median loop; reject outliers; average accepted loops into a closed reference polyline; preserve raw samples and rejected-loop diagnostics. [ASSUMED]

### Pattern 5: Offline Vector Trace Rendering

**What:** Project saved lat/lon into local meters, normalize into the view bounds, and draw polylines/markers with Compose Canvas. [VERIFIED: codebase grep; CITED: https://developer.android.com/develop/ui/compose/graphics/draw/overview]

**When to use:** Use for Track Review and Timing Session Review in Phase 3; do not load map tiles. [VERIFIED: .planning/phases/03-local-sessions-review-and-export/03-CONTEXT.md]

**Example:**

```kotlin
// Source: Compose Canvas docs; projection reuses existing LocalProjection pattern.
@Composable
fun TraceView(points: List<LocalPoint>, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val bounds = points.boundsOrNull() ?: return@Canvas
        val fitted = points.map { point -> point.fitInto(bounds, size) }
        for (i in 1 until fitted.size) {
            drawLine(
                color = Color.Cyan,
                start = fitted[i - 1],
                end = fitted[i],
                strokeWidth = 3f,
            )
        }
    }
}
```

### Anti-Patterns to Avoid

- **Timing a marking session:** Track marking has no confirmed start/finish, so it must not produce `Lap` records or lap times. [VERIFIED: .planning/phases/03-local-sessions-review-and-export/03-CONTEXT.md]
- **Demo-only business flow:** The simulator must replace the GPS provider only; do not create separate demo buttons that bypass start/stop/save/review/export behavior. [VERIFIED: .planning/phases/03-local-sessions-review-and-export/03-CONTEXT.md]
- **Index as the full source of truth:** The metadata index is for list summaries; payload JSON files preserve canonical details. [VERIFIED: .planning/phases/03-local-sessions-review-and-export/03-CONTEXT.md]
- **Screen-coordinate persistence:** Persist lat/lon and derived geographic polylines, not Canvas coordinates. [VERIFIED: .planning/phases/03-local-sessions-review-and-export/03-CONTEXT.md]
- **Mixing simulated data into real best-lap state:** Demo/Simulated source metadata must exclude demo samples from real per-track fastest-lap calculations. [VERIFIED: .planning/phases/03-local-sessions-review-and-export/03-CONTEXT.md]

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| JSON serialization | Manual string concatenation/parsing | `kotlinx.serialization-json` | Stable JSON support exists for all supported platforms and handles typed DTOs. [CITED: https://kotlinlang.org/docs/serialization.html] |
| Cross-platform file APIs | Separate ad hoc Android/iOS file implementations for every repository method | Okio `FileSystem` plus platform-provided root paths | Okio provides common `Path`, read/write, fake filesystem tests, and atomic moves. [CITED: https://square.github.io/okio/file_system/] |
| Lap timing | New session-specific crossing/lap logic | Existing `LapEngine` and `CourseDefinition` | Phase 2 built and tested the clean-room engine; Phase 3 should persist its outputs and raw inputs. [VERIFIED: codebase grep; VERIFIED: AGENTS.md] |
| UTC instants | Custom date formatter/parser | Kotlin `kotlin.time.Instant` and `Clock` | Kotlin 2.4 documents epoch millis and ISO string conversion in stdlib. [CITED: https://kotlinlang.org/api/core/kotlin-stdlib/kotlin.time/-instant/] |
| Full-fidelity GPS interchange | Custom GPX extensions as primary format | Canonical JSON for full fidelity, GPX 1.1 only for GPS track points | GPX is a GPS interchange schema; LapSight-specific structures belong in JSON. [CITED: https://www.topografix.com/gpx/1/1/; VERIFIED: .planning/phases/03-local-sessions-review-and-export/03-CONTEXT.md] |
| Map renderer | Google/Apple map integration | Compose Canvas vector trace | Map tiles are deferred and Canvas supports precise custom drawing. [VERIFIED: .planning/phases/03-local-sessions-review-and-export/03-CONTEXT.md; CITED: https://developer.android.com/develop/ui/compose/graphics/draw/overview] |
| App navigation framework | Premature deep-link/back-stack framework | Material3 `NavigationBar` plus explicit local route state | The phase needs 3 persistent tabs and simple detail drills; Material3 documents navigation bars for 3-5 destinations. [CITED: https://kotlinlang.org/api/compose-multiplatform/material3/androidx.compose.material3/-navigation-bar.html; ASSUMED] |

**Key insight:** Phase 3 complexity is in data boundaries, replayability, and crash-safe persistence; custom serializers, custom cross-platform file abstractions, or duplicated lap logic add risk without proving the product. [ASSUMED]

## Common Pitfalls

### Pitfall 1: Splitting Track Marking into Laps
**What goes wrong:** The app shows lap times or builds track geometry from fake laps before the user confirms a start/finish line. [VERIFIED: .planning/phases/03-local-sessions-review-and-export/03-CONTEXT.md]
**Why it happens:** Existing Phase 2 replay fixtures are lap-oriented, so it is tempting to reuse that model for marking. [VERIFIED: codebase grep; ASSUMED]
**How to avoid:** Keep `TrackMarkingSession` as one continuous trace and only create `Lap` inside `TimingSession`. [VERIFIED: .planning/phases/03-local-sessions-review-and-export/03-CONTEXT.md]
**Warning signs:** Marking review has lap list, best lap, or `LapEngine` calls before Track save. [ASSUMED]

### Pitfall 2: Corrupting Drafts on Crash or Backgrounding
**What goes wrong:** An active timing session disappears or loads a partial JSON file after process death. [ASSUMED]
**Why it happens:** Direct writes to final JSON paths can leave truncated files. [CITED: https://square.github.io/okio/file_system/; ASSUMED]
**How to avoid:** Write to temp files, atomically move into place, and keep an index state that distinguishes `draft`, `stoppedPendingSave`, and `saved`. [CITED: https://square.github.io/okio/file_system/; ASSUMED]
**Warning signs:** Save/discard code updates index before payload writes complete, or tests never inject write failures. [ASSUMED]

### Pitfall 3: Treating GPX as Full-Fidelity Export
**What goes wrong:** Laps, sectors, start/finish, schema versions, and app metadata are lost because GPX is the only export. [VERIFIED: .planning/phases/03-local-sessions-review-and-export/03-CONTEXT.md]
**Why it happens:** GPX is familiar to GPS tools but is not LapSight's canonical schema. [CITED: https://www.topografix.com/gpx/1/1/; ASSUMED]
**How to avoid:** Make JSON the primary export and GPX a compatibility export for GPS points. [VERIFIED: .planning/phases/03-local-sessions-review-and-export/03-CONTEXT.md]
**Warning signs:** GPX exporter receives `LapEvent` objects but JSON export tests are missing. [ASSUMED]

### Pitfall 4: Using Wall Clock for Lap Durations
**What goes wrong:** Time adjustments or replay speed alter lap durations and point times. [CITED: https://kotlinlang.org/api/core/kotlin-stdlib/kotlin.time/-instant/; ASSUMED]
**Why it happens:** `Clock.System` is useful for event timestamps but not monotonic interval measurement. [CITED: https://kotlinlang.org/api/core/kotlin-stdlib/kotlin.time/-instant/]
**How to avoid:** Keep `LocationSample.elapsedMillis` and `LapEngine` interpolation as the timing basis; use `Instant` only for session creation/export absolute timestamps. [VERIFIED: codebase grep; CITED: https://kotlinlang.org/api/core/kotlin-stdlib/kotlin.time/-instant/]
**Warning signs:** Tests depend on real time, `delay`, or current system clock to calculate lap durations. [ASSUMED]

### Pitfall 5: Letting Simulated Data Affect Real Ghost Candidates
**What goes wrong:** Demo sessions become the per-track best lap and pollute future ghost/delta state. [VERIFIED: .planning/phases/03-local-sessions-review-and-export/03-CONTEXT.md]
**Why it happens:** Demo data is saved locally for UAT and looks like normal sessions unless source metadata is enforced. [VERIFIED: .planning/phases/03-local-sessions-review-and-export/03-CONTEXT.md]
**How to avoid:** Persist `source=Simulated` on samples, tracks, sessions, index rows, and exports, and filter ghost-candidate calculations by source. [VERIFIED: codebase grep; VERIFIED: .planning/phases/03-local-sessions-review-and-export/03-CONTEXT.md]
**Warning signs:** `bestLapForTrack(trackId)` has no source filter. [ASSUMED]

## Code Examples

Verified patterns from official sources and local code:

### JSON Round Trip Test

```kotlin
// Source: Kotlin serialization docs and LapSight schema decision.
@Test
fun timingSessionPayloadRoundTrips() {
    val payload = sampleTimingSessionPayload()
    val encoded = canonicalJson.encodeToString(payload)
    val decoded = canonicalJson.decodeFromString<TimingSessionPayloadV1>(encoded)

    assertEquals(1, decoded.schemaVersion)
    assertEquals(payload.samples, decoded.samples)
    assertEquals(payload.laps, decoded.laps)
}
```

### File Store Failure Injection

```kotlin
// Source: Okio FileSystem docs describe FakeFileSystem and ForwardingFileSystem.
@Test
fun saveDoesNotUpdateIndexWhenPayloadWriteFails() {
    val fs = FakeFileSystem()
    val store = FileSessionStore(
        fileSystem = FailingWritesFileSystem(fs),
        root = "/lapsight".toPath(),
        json = canonicalJson,
    )

    assertFails { store.writeTimingSession(sampleTimingSessionPayload()) }
    assertFalse(fs.exists("/lapsight/index.json".toPath()))
}
```

### GPX Track Export Boundary

```kotlin
// Source: GPX 1.1 schema; track points use lat/lon attributes, optional ele/time.
fun gpxTrackPoint(sample: LocationSample, startedAtEpochMillis: Long): String {
    val time = Instant
        .fromEpochMilliseconds(startedAtEpochMillis + sample.elapsedMillis)
        .toString()
    val elevation = sample.altitudeMeters?.let { "<ele>$it</ele>" }.orEmpty()
    return buildString {
        append("""<trkpt lat="${sample.latitude}" lon="${sample.longitude}">""")
        append(elevation)
        append("<time>")
        append(xmlEscape(time))
        append("</time></trkpt>")
    }
}
```

### Review Route State Without New Navigation Package

```kotlin
// Source: Material3 NavigationBar docs; adapted for current app shell.
enum class RootTab { Drive, Review, Settings }

sealed interface AppRoute {
    data object Drive : AppRoute
    data object ReviewList : AppRoute
    data class TrackDetail(val trackId: String) : AppRoute
    data class SessionDetail(val sessionId: String) : AppRoute
    data object Settings : AppRoute
}
```

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| Session-owned demo replay that advances only while timing UI runs | Provider-layer simulated GPS feed that can run before marking/timing starts | Phase 3 decision D-03/D-05, 2026-06-25 [VERIFIED: .planning/phases/03-local-sessions-review-and-export/03-CONTEXT.md] | Validates real product workflows instead of demo-only timing shortcuts. |
| Manual or platform-specific JSON handling | Kotlin serialization plugin plus `kotlinx-serialization-json` | Current official docs show JSON artifact `1.11.0`; Maven metadata verified. [CITED: https://github.com/Kotlin/kotlinx.serialization; VERIFIED: Maven metadata] | Keeps saved/exported payloads typed, versioned, and testable. |
| JVM/Android-only file APIs in shared logic | Okio `FileSystem` with platform root path injection | Okio 3.x documents multiplatform paths/file systems and atomic moves. [CITED: https://square.github.io/okio/file_system/] | Lets storage tests run in common/host tests with fake filesystems. |
| External map tiles for any trace | Offline vector trace with Compose Canvas | Phase 3 decision D-33 and Compose drawing docs. [VERIFIED: .planning/phases/03-local-sessions-review-and-export/03-CONTEXT.md; CITED: https://developer.android.com/develop/ui/compose/graphics/draw/overview] | Avoids map SDK/API-key scope while proving review trace usability. |
| `kotlinx-datetime` for simple instants | Kotlin stdlib `kotlin.time.Instant`/`Clock` | Kotlin 2.4 API docs list `Instant`/`Clock` since Kotlin 2.3. [CITED: https://kotlinlang.org/api/core/kotlin-stdlib/kotlin.time/-instant/; CITED: https://kotlinlang.org/api/core/kotlin-stdlib/kotlin.time/-clock/] | Avoids an extra experimental dependency for Phase 3 timestamp needs. |

**Deprecated/outdated:**

- Using `kotlinx.datetime.Instant`/`Clock` for new code is not recommended here because the Kotlin stdlib now provides `kotlin.time.Instant` and `Clock`, and `kotlinx-datetime` documents migration away from its older Instant/Clock types. [CITED: https://github.com/Kotlin/kotlinx-datetime; CITED: https://kotlinlang.org/api/core/kotlin-stdlib/kotlin.time/-instant/]
- Using map tiles in Phase 3 is out of scope by locked decision. [VERIFIED: .planning/phases/03-local-sessions-review-and-export/03-CONTEXT.md]
- Copying GPL lap-timer/viewer algorithms remains forbidden unless the project license decision changes. [VERIFIED: AGENTS.md]

## Assumptions Log

| # | Claim | Section | Risk if Wrong |
|---|-------|---------|---------------|
| A1 | Reference-line extraction can be built with spatial anchors, loop scoring, robust outlier rejection, and normalized-distance averaging without a third-party geometry library. | Summary, Architecture Patterns | Planner may under-scope algorithm complexity or need a spike task. |
| A2 | Draft persistence can be implemented with atomic JSON checkpoints plus explicit draft states, without a database. | Architecture Patterns, Common Pitfalls | Crash recovery could lose recent samples if checkpoint cadence is too sparse. |
| A3 | Phase 3 can use explicit route state plus Material3 bottom navigation instead of adding Navigation Compose. | Standard Stack, Code Examples | Back-stack restoration or deep links may need a navigation library later. |
| A4 | A constrained GPX writer with XML escaping tests is sufficient; a full XML library is not required for Phase 3 export. | Don't Hand-Roll, Code Examples | Invalid XML escaping would make GPX unusable in external tools. |
| A5 | Compose raw resources are an acceptable place for committed demo fixture JSON files used by the app. | Recommended Project Structure | KMP resource access could create test/platform friction and require a different fixture path. |
| A6 | Session data is sensitive enough to keep in app-private storage by default, but Phase 3 does not require at-rest encryption unless the user decides so. | Security Domain | Privacy expectations may require encrypted storage or retention controls earlier. |

## Open Questions

1. **Package install checkpoint**
   - What we know: The recommended packages are official upstream Maven/Gradle artifacts and registry metadata verifies current versions. [CITED: https://github.com/Kotlin/kotlinx.serialization; CITED: https://square.github.io/okio/; VERIFIED: Maven metadata]
   - What's unclear: `slopcheck` flagged Maven coordinates as `SUS`, likely from weak Maven heuristics rather than real slopsquatting evidence. [VERIFIED: slopcheck]
   - Recommendation: Planner must include a human verification checkpoint before applying the serialization plugin and adding Okio/serialization dependencies. [VERIFIED: slopcheck]

2. **Discard deletion semantics**
   - What we know: Discarded drafts must not become formal review history. [VERIFIED: .planning/phases/03-local-sessions-review-and-export/03-CONTEXT.md]
   - What's unclear: Whether discard should physically delete immediately, tombstone for recovery, or keep a debug-only recovery window. [ASSUMED]
   - Recommendation: Plan immediate removal from index/history and physical deletion of draft payloads for Phase 3; leave recovery/tombstones deferred unless the user requests them. [ASSUMED]

3. **Platform share surface**
   - What we know: JSON and GPX export must work from Review detail screens. [VERIFIED: .planning/phases/03-local-sessions-review-and-export/03-CONTEXT.md]
   - What's unclear: Whether Phase 3 needs native share sheets on both Android and iOS, or just writes export files and exposes a platform-specific save/share action. [ASSUMED]
   - Recommendation: Plan a platform export boundary (`expect/actual` or injected interface) so shared export services return bytes/filename, and Android/iOS own the share/save handoff. [CITED: https://kotlinlang.org/docs/multiplatform/multiplatform-expect-actual.html; ASSUMED]

## Environment Availability

| Dependency | Required By | Available | Version | Fallback |
|------------|-------------|-----------|---------|----------|
| Gradle wrapper | Builds/tests | Yes | Gradle 9.1.0 from `.\gradlew.bat --version` | None needed. [VERIFIED: shell] |
| JVM | Gradle launcher | Yes | Java 23.0.2 launcher; Gradle daemon configured for Java 21 via `gradle-daemon-jvm.properties`. [VERIFIED: shell; VERIFIED: codebase grep] | Use Gradle toolchain/daemon config already present. |
| Android SDK | Android build | Yes | `local.properties` points to `C:\Users\16079\AppData\Local\Android\Sdk`; directory exists. [VERIFIED: shell] | None for local Android build. |
| adb | Android device UAT | No | Not found on PATH. [VERIFIED: shell] | Build APK; device UAT requires Android platform tools on PATH or manual install path. |
| xcodebuild | iOS runtime UAT | No | Not found on Windows. [VERIFIED: shell] | Requires macOS/Xcode for iOS runtime checks. |
| ctx7 | Documentation lookup | No | Not found. [VERIFIED: shell] | Used official docs/web sources. |
| slopcheck | Package legitimacy gate | Yes | 0.6.1; no `--json` support. [VERIFIED: shell] | Text audit used; human checkpoint required for SUS packages. |
| Network/Maven Central | Version verification | Yes | Maven metadata fetched successfully. [VERIFIED: shell] | None needed. |

**Missing dependencies with no fallback:**

- `xcodebuild` for iOS runtime UAT on this Windows machine. [VERIFIED: shell]

**Missing dependencies with fallback:**

- `adb` is missing from PATH; automated build can still run, but on-device Android UAT needs platform tools or manual install. [VERIFIED: shell]
- `ctx7` is missing; official docs and registry metadata were used instead. [VERIFIED: shell]

## Validation Architecture

### Test Framework

| Property | Value |
|----------|-------|
| Framework | `kotlin.test` through shared KMP tests. [VERIFIED: codebase grep] |
| Config file | `shared/build.gradle.kts` commonTest dependency on `libs.kotlin.test`; no separate test config file. [VERIFIED: codebase grep] |
| Quick run command | `.\gradlew.bat :shared:testAndroidHostTest` [VERIFIED: shell] |
| Full suite command | `.\gradlew.bat :shared:check` [VERIFIED: shell] |

### Phase Requirements -> Test Map

| Req ID | Behavior | Test Type | Automated Command | File Exists? |
|--------|----------|-----------|-------------------|--------------|
| SESS-01 | Start, stop, auto-draft, Save, Discard, reopen/recover draft states. | Unit/integration | `.\gradlew.bat :shared:testAndroidHostTest --tests "*SessionControllerTest*"` | No - Wave 0 |
| SESS-02 | Review summary has lap list, best lap, total duration, sample count, and GPS quality summary. | Unit | `.\gradlew.bat :shared:testAndroidHostTest --tests "*ReviewSummaryTest*"` | No - Wave 0 |
| SESS-03 | Saved session trace projects into stable normalized screen points without changing canonical lat/lon. | Unit | `.\gradlew.bat :shared:testAndroidHostTest --tests "*TraceProjectionTest*"` | No - Wave 0 |
| SESS-04 | JSON export preserves samples, laps, start/finish, sector lines, schemaVersion, app/build metadata, and source metadata. | Unit/golden | `.\gradlew.bat :shared:testAndroidHostTest --tests "*JsonExportTest*"` | No - Wave 0 |
| SESS-05 | GPX export emits valid GPX 1.1 track points with escaped text and expected sample count. | Unit/golden | `.\gradlew.bat :shared:testAndroidHostTest --tests "*GpxExportTest*"` | No - Wave 0 |
| D-09 | Reference extractor derives reference line from repeated spatial structure and rejects one outlier loop. | Unit/fixture | `.\gradlew.bat :shared:testAndroidHostTest --tests "*ReferenceLineExtractorTest*"` | No - Wave 0 |
| D-13/D-15 | Draft survives store reload and prompts as unfinished on launch. | Unit/integration | `.\gradlew.bat :shared:testAndroidHostTest --tests "*DraftRecoveryTest*"` | No - Wave 0 |

### Sampling Rate

- **Per task commit:** `.\gradlew.bat :shared:testAndroidHostTest` [VERIFIED: shell]
- **Per wave merge:** `.\gradlew.bat :shared:check` [VERIFIED: shell]
- **Phase gate:** `.\gradlew.bat :shared:check` plus `.\gradlew.bat :androidApp:assembleDebug`; iOS runtime UAT remains human/macOS-gated on this machine. [VERIFIED: shell; VERIFIED: .planning/STATE.md]

### Wave 0 Gaps

- [ ] `shared/src/commonTest/kotlin/com/huanfuli/lapsight/shared/session/SessionControllerTest.kt` - covers SESS-01 and draft transitions. [ASSUMED]
- [ ] `shared/src/commonTest/kotlin/com/huanfuli/lapsight/shared/storage/FileSessionStoreTest.kt` - covers atomic payload/index writes and recovery. [ASSUMED]
- [ ] `shared/src/commonTest/kotlin/com/huanfuli/lapsight/shared/track/ReferenceLineExtractorTest.kt` - covers D-06 through D-11 extraction semantics. [ASSUMED]
- [ ] `shared/src/commonTest/kotlin/com/huanfuli/lapsight/shared/export/JsonExportTest.kt` - covers SESS-04. [ASSUMED]
- [ ] `shared/src/commonTest/kotlin/com/huanfuli/lapsight/shared/export/GpxExportTest.kt` - covers SESS-05. [ASSUMED]
- [ ] `shared/src/commonTest/kotlin/com/huanfuli/lapsight/shared/review/ReviewSummaryTest.kt` - covers SESS-02 and SESS-03 summary/trace state. [ASSUMED]
- [ ] Fixture payload files for clean 10-loop, minimum 5-loop, outlier, noise/drift, dropped samples, and multi-session best-candidate scenarios. [VERIFIED: .planning/phases/03-local-sessions-review-and-export/03-CONTEXT.md; ASSUMED]

## Security Domain

### Applicable ASVS Categories

| ASVS Category | Applies | Standard Control |
|---------------|---------|------------------|
| V2 Authentication | No | No accounts/login in Phase 3; do not introduce auth state. [VERIFIED: .planning/phases/03-local-sessions-review-and-export/03-CONTEXT.md] |
| V3 Session Management | No | Product "session" is driving/timing data, not authenticated web session management. [VERIFIED: .planning/REQUIREMENTS.md; ASSUMED] |
| V4 Access Control | Local-only | Keep stored payloads in app-private storage; exports require explicit user action. [CITED: https://developer.android.com/training/data-storage/app-specific; VERIFIED: .planning/phases/03-local-sessions-review-and-export/03-CONTEXT.md] |
| V5 Input Validation | Yes | Validate schemaVersion, required IDs, coordinate ranges, elapsed millis monotonicity, finite numbers, and source metadata during decode/import/load. [CITED: https://owasp.org/www-project-application-security-verification-standard/; ASSUMED] |
| V6 Cryptography | No new crypto | Do not hand-roll encryption; if encryption becomes required, use platform secure storage/crypto APIs in a separate decision. [ASSUMED] |
| Mobile storage risk | Yes | GPS session history can expose sensitive location patterns; default to app-private storage and explicit exports. [CITED: https://owasp.org/www-project-mobile-top-10/2023-risks/m9-insecure-data-storage; CITED: https://developer.android.com/training/data-storage/app-specific; ASSUMED] |

### Known Threat Patterns for KMP Local Session Storage

| Pattern | STRIDE | Standard Mitigation |
|---------|--------|---------------------|
| Malformed local JSON payload causes crash or unsafe state | Tampering/DoS | Decode with typed DTOs, schemaVersion checks, range validation, and corrupt-file quarantine. [CITED: https://kotlinlang.org/docs/serialization.html; ASSUMED] |
| Export filename path traversal or invalid filename | Tampering | Generate filenames from stable IDs/sanitized track names; never accept path separators from user-controlled names. [ASSUMED] |
| Accidental public exposure of app-private files | Information Disclosure | Store canonical payloads under internal app-specific storage; share only explicit export copies. [CITED: https://developer.android.com/training/data-storage/app-specific; ASSUMED] |
| Demo data indistinguishable from real data | Integrity | Persist source metadata on every sample/session/export and filter simulated source from real best-lap candidates. [VERIFIED: .planning/phases/03-local-sessions-review-and-export/03-CONTEXT.md] |
| XML injection in GPX names/metadata | Tampering | Escape XML text/attributes and golden-test special characters. [CITED: https://www.topografix.com/gpx/1/1/; ASSUMED] |

## Sources

### Primary (HIGH confidence)

- `.planning/phases/03-local-sessions-review-and-export/03-CONTEXT.md` - locked Phase 3 decisions, deferred items, entity boundaries, storage/export requirements. [VERIFIED: codebase read]
- `.planning/REQUIREMENTS.md` - SESS-01 through SESS-05 requirement text and traceability. [VERIFIED: codebase read]
- `.planning/ROADMAP.md` - Phase 3 goal, success criteria, and local-first/export notes. [VERIFIED: codebase read]
- `.planning/STATE.md` - Phase 2 completion state and remaining runtime gaps. [VERIFIED: codebase read]
- `AGENTS.md` - project constraints, clean-room/GPL rules, phone-first boundary. [VERIFIED: codebase read]
- `shared/src/commonMain/kotlin/com/huanfuli/lapsight/shared/GpsProbeModels.kt` - `LocationSample`, `LocationSource`, GPS state. [VERIFIED: codebase read]
- `shared/src/commonMain/kotlin/com/huanfuli/lapsight/shared/lap/*` - existing lap engine, timing lines, replay runner, fixtures, and dash state. [VERIFIED: codebase read]
- Kotlin serialization docs and README - JSON support, plugin/runtime setup. https://kotlinlang.org/docs/serialization.html and https://github.com/Kotlin/kotlinx.serialization [CITED]
- Okio FileSystem docs - multiplatform file system, fake filesystem, atomicMove. https://square.github.io/okio/file_system/ [CITED]
- GPX 1.1 schema documentation - WGS84 lat/lon bounds, track point schema. https://www.topografix.com/gpx/1/1/ [CITED]
- Kotlin stdlib `Instant` and `Clock` API docs. https://kotlinlang.org/api/core/kotlin-stdlib/kotlin.time/-instant/ and https://kotlinlang.org/api/core/kotlin-stdlib/kotlin.time/-clock/ [CITED]
- Compose Multiplatform Material3 `NavigationBar` API docs. https://kotlinlang.org/api/compose-multiplatform/material3/androidx.compose.material3/-navigation-bar.html [CITED]
- Jetpack Compose graphics/Canvas docs. https://developer.android.com/develop/ui/compose/graphics/draw/overview [CITED]
- Kotlin expect/actual docs. https://kotlinlang.org/docs/multiplatform/multiplatform-expect-actual.html [CITED]
- Android app-specific storage docs. https://developer.android.com/training/data-storage/app-specific [CITED]

### Secondary (MEDIUM confidence)

- Maven Central direct metadata for `org.jetbrains.kotlinx:kotlinx-serialization-json` and `com.squareup.okio:okio`; Gradle Plugin Portal metadata for `org.jetbrains.kotlin.plugin.serialization`. [VERIFIED: shell]
- Kotlinx serialization changelog and Okio changelog release dates. [CITED: https://github.com/Kotlin/kotlinx.serialization/blob/master/CHANGELOG.md; CITED: https://github.com/square/okio/blob/master/CHANGELOG.md]
- OWASP ASVS and Mobile Top 10 storage pages for validation/storage threat framing. [CITED: https://owasp.org/www-project-application-security-verification-standard/; CITED: https://owasp.org/www-project-mobile-top-10/2023-risks/m9-insecure-data-storage]

### Tertiary (LOW confidence)

- Reference-line extraction thresholds and exact scoring strategy are architecture recommendations based on project constraints and existing local projection utilities, not verified against a dedicated library or production algorithm. [ASSUMED]
- Fixture file location under Compose raw resources is recommended from Compose resource docs but should be validated in the first Wave 0 task. [ASSUMED]

## Metadata

**Confidence breakdown:**

- Standard stack: HIGH - Existing KMP/Compose stack is verified in Gradle files; new serialization/Okio versions are verified via official docs and registry metadata, with slopcheck SUS checkpoints noted. [VERIFIED: codebase grep; VERIFIED: Maven metadata; VERIFIED: slopcheck]
- Architecture: HIGH for storage/export/provider boundaries because they are locked in CONTEXT.md; MEDIUM for reference extraction details because thresholds need fixture-driven tuning. [VERIFIED: .planning/phases/03-local-sessions-review-and-export/03-CONTEXT.md; ASSUMED]
- Pitfalls: HIGH for marking/session/export boundary pitfalls from locked decisions; MEDIUM for draft checkpoint and file corruption mitigations until implementation chooses exact write cadence. [VERIFIED: .planning/phases/03-local-sessions-review-and-export/03-CONTEXT.md; ASSUMED]

**Research date:** 2026-06-25
**Valid until:** 2026-07-02 for package versions and Compose/Kotlin docs; architectural decisions remain valid until CONTEXT.md changes.
