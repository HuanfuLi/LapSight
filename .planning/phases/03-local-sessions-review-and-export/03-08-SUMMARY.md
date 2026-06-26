---
phase: 03-local-sessions-review-and-export
plan: 08
subsystem: export
tags: [kmp, kotlin, json, gpx, export, share, tdd, fileprovider]

# Dependency graph
requires:
  - phase: 03-local-sessions-review-and-export
    provides: LocalSessionStore (file/in-memory), TrackPayloadV1, TimingSessionPayloadV1, ReviewScreen, ReviewModels
provides:
  - JsonExportService — canonical JSON export from saved LocalSessionStore payloads using FileSessionStore.canonicalJson
  - GpxExportService — GPX 1.1 compatibility export with XML escaping and ISO 8601 timestamps
  - ExportFileNames — sanitized LapSight-prefixed filenames with path traversal/control char stripping
  - LocalExportWriter — atomic staging under app-private exports/ directory
  - ExportShareTarget — platform boundary with Android (FileProvider + ACTION_SEND) and iOS (stub) actuals
  - Export buttons on Track Review and Timing Session Review detail screens
affects: [04-ghost-lap-live-delta, future PWA/web analysis tools]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "ExportShareTarget follows constructor injection pattern (like OrientationController) — platform implementations injected through App → AppShell → ReviewScreen"
    - "Export services accept LocalSessionStore via constructor and produce ByteArray; no side effects beyond reading from store"
    - "JSON export reuses FileSessionStore.canonicalJson config (prettyPrint=true, encodeDefaults=true, explicitNulls=false, ignoreUnknownKeys=true) for consistent formatting"
    - "GPX timestamps derived from session start epoch + sample elapsedMillis using shared gregorian calendar math"
    - "Filename sanitization uses [a-zA-Z0-9._-] whitelist with path/traversal/XML/control char stripping"

key-files:
  created:
    - shared/src/commonMain/kotlin/com/huanfuli/lapsight/shared/export/ExportModels.kt — ExportArtifact, ExportResult, ExportShareResult
    - shared/src/commonMain/kotlin/com/huanfuli/lapsight/shared/export/ExportFileNames.kt — LapSight-prefixed sanitized filenames (D-41)
    - shared/src/commonMain/kotlin/com/huanfuli/lapsight/shared/export/JsonExportService.kt — canonical JSON export service (D-38)
    - shared/src/commonMain/kotlin/com/huanfuli/lapsight/shared/export/GpxExportService.kt — GPX 1.1 export service (D-39)
    - shared/src/commonMain/kotlin/com/huanfuli/lapsight/shared/export/LocalExportWriter.kt — atomic export file staging
    - shared/src/commonMain/kotlin/com/huanfuli/lapsight/shared/export/ExportShareTarget.kt — common interface + NoOpExportShareTarget
    - shared/src/androidMain/kotlin/com/huanfuli/lapsight/shared/export/ExportShareTarget.android.kt — Android FileProvider + ACTION_SEND
    - shared/src/iosMain/kotlin/com/huanfuli/lapsight/shared/export/ExportShareTarget.ios.kt — iOS stub (compiles cross-platform)
    - shared/src/commonTest/kotlin/com/huanfuli/lapsight/shared/export/JsonExportTest.kt — 7 Wave 0 tests (SESS-04, D-38, D-40, D-42)
    - shared/src/commonTest/kotlin/com/huanfuli/lapsight/shared/export/GpxExportTest.kt — 9 Wave 0 tests (SESS-05, D-39, D-41, T-03-21)
    - androidApp/src/main/res/xml/file_paths.xml — FileProvider cache-path for export files
  modified:
    - shared/src/commonMain/kotlin/com/huanfuli/lapsight/shared/ui/ReviewScreen.kt — export buttons on Track/Timing detail
    - shared/src/commonMain/kotlin/com/huanfuli/lapsight/shared/ui/AppShell.kt — ExportShareTarget plumbing
    - shared/src/commonMain/kotlin/com/huanfuli/lapsight/shared/App.kt — ExportShareTarget injection
    - androidApp/src/main/kotlin/com/huanfuli/lapsight/MainActivity.kt — AndroidExportShareTarget wiring
    - androidApp/src/main/AndroidManifest.xml — FileProvider declaration

key-decisions:
  - "ExportShareTarget uses plain Kotlin interface (not expect/actual class) with platform-specific constructors, matching the existing OrientationController injection pattern"
  - "JSON export reuses FileSessionStore.canonicalJson for consistent formatting between saved and exported payloads"
  - "Android share target writes bytes to cacheDir and uses FileProvider for content URIs (avoids FileUriExposedException on Android 7+)"
  - "iOS share target is a compile-safe stub; full UIActivityViewController integration deferred to iOS runtime testing"
  - "No batch export, no automatic exports, no cloud sync — all exports are explicit button taps on detail screens only (D-40)"

patterns-established:
  - "ExportShareTarget interface + platform constructors — follows OrientationController injection pattern"
  - "Pure shared export services produce ByteArray; platform actuals own UI presentation"
  - "Export DTO reuse: JSON export serializes the same TrackPayloadV1/TimingSessionPayloadV1 used by FileSessionStore"

requirements-completed: [SESS-04, SESS-05]

# Metrics
duration: ~30min
completed: 2026-06-25
---

# Phase 3 Plan 08: Explicit JSON and GPX Export with Platform Share Handoff Summary

**Canonical JSON export from saved payloads, GPX 1.1 GPS interchange with XML escaping, sanitized LapSight-prefixed filenames, and platform share handoff via Android ACTION_SEND — all triggered from explicit Review detail export buttons.**

## Performance

- **Duration:** ~30 min
- **Completed:** 2026-06-25
- **Tasks:** 3 (TDD: 1 RED + 2 GREEN)
- **Files created:** 11
- **Files modified:** 5

## Accomplishments

- `JsonExportService` exports full-fidelity Track and TimingSession canonical JSON using `FileSessionStore.canonicalJson` config (D-38). Includes schemaVersion, raw samples, laps, sector events, GPS quality, start/finish, sector lines, app/build metadata, and source metadata.
- `GpxExportService` generates GPX 1.1 XML with raw GPS track points (lat/lon), optional elevation (`<ele>`), and ISO 8601 timestamps derived from session start epoch + sample elapsedMillis (D-39). All text and attributes are XML-escaped (T-03-21). No LapSight custom extensions.
- `ExportFileNames` produces LapSight-prefixed filenames with sanitized track/session name tokens (`[a-zA-Z0-9._-]` only), yyyyMMdd date format, and path traversal/control/XML character stripping (D-41). Patterns: `LapSight_Track_<name>_<date>.json`, `LapSight_Session_<name>_<date>.json`/`.gpx`.
- `LocalExportWriter` stages export artifacts atomically under an app-private `exports/` directory using Okio `FileSystem` — never exposes canonical payload paths (T-03-22).
- `ExportShareTarget` interface defines the platform boundary. Android actual (`AndroidExportShareTarget`) stages bytes to `cacheDir/export/`, creates a `FileProvider` content URI, and launches `Intent.ACTION_SEND` (SESS-04/SESS-05 reachably delivered). iOS actual (`IosExportShareTarget`) is a compile-safe stub.
- ReviewScreen: Track Review detail shows "Export JSON" button. Timing Session Review detail shows "Export JSON" and "Export GPX" buttons. Success shows "Exported {filename}" (green), failure shows "Export failed. Check device storage and try again." (red, UI-SPEC copy). Cancelled shares are no-ops.
- `ExportShareTarget` is constructor-injected through `App` → `AppShell` → `ReviewScreen`, following the same injection pattern as `OrientationController` and `LocalSessionStore`. `MainActivity` wires the Android actual with the activity Context.
- AndroidManifest declares `FileProvider` with `com.huanfuli.lapsight.fileprovider` authority and `cache-path` for export files.
- No batch export, no automatic exports on Save/Stop/launch, no cloud sync, no map tiles, no new Gradle dependencies.

## Task Commits

1. **Task 1 RED: Add Wave 0 tests for JSON, GPX, filenames, and export boundaries** - `e10c5a5` (test)
2. **Task 2 GREEN: Implement JSON/GPX export services and local export writer** - `a98dc93` (feat)
3. **Task 3 GREEN: Wire explicit Review detail export actions** - `ea67823` (feat)

## Files Created/Modified

- `.../export/ExportModels.kt` — ExportArtifact(data class), ExportResult(sealed), ExportShareResult(sealed)
- `.../export/ExportFileNames.kt` — forTrack(), forTimingSession(), sanitizeNameToken(), formatDate()
- `.../export/JsonExportService.kt` — exportTrack(), exportTrackMarking(), exportTimingSession()
- `.../export/GpxExportService.kt` — exportTimingSession() with GPX 1.1 XML generation
- `.../export/LocalExportWriter.kt` — write() with atomic temp-file staging
- `.../export/ExportShareTarget.kt` — interface + NoOpExportShareTarget
- `.../export/ExportShareTarget.android.kt` — AndroidExportShareTarget (FileProvider + ACTION_SEND)
- `.../export/ExportShareTarget.ios.kt` — IosExportShareTarget (stub)
- `.../export/JsonExportTest.kt` — 7 RED→GREEN tests (D-38 fields, entity IDs, source metadata, special chars, share contract)
- `.../export/GpxExportTest.kt` — 9 RED→GREEN tests (GPX 1.1 structure, sample counts, elevation/time, filename patterns, escaping, date format, ASCII whitelist)
- `androidApp/src/main/res/xml/file_paths.xml` — FileProvider paths
- `shared/.../ui/ReviewScreen.kt` — Modified: export buttons on Track/Timing detail
- `shared/.../ui/AppShell.kt` — Modified: ExportShareTarget plumbing
- `shared/.../App.kt` — Modified: ExportShareTarget injection
- `androidApp/.../MainActivity.kt` — Modified: AndroidExportShareTarget wiring
- `androidApp/.../AndroidManifest.xml` — Modified: FileProvider declaration

## Decisions Made

- ExportShareTarget is a plain Kotlin interface (not expect/actual class) with platform-specific constructors, following the OrientationController pattern.
- JSON export reuses `FileSessionStore.canonicalJson` for consistent formatting between saved and exported payloads.
- Android share target writes bytes to `cacheDir/export/` and uses `FileProvider` for content URIs.
- iOS share target is a compile-safe stub; full UIActivityViewController integration deferred to iOS runtime testing.
- No batch export, no automatic exports — all exports are explicit button taps (D-40).

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] Fixed KMP-incompatible javaClass reflection in tests**
- **Found during:** Task 1 (RED test compilation)
- **Issue:** `javaClass?.simpleName` is JVM-only, doesn't compile in Kotlin Multiplatform commonTest
- **Fix:** Replaced with `is ExportShareResult.Shared` type check
- **Files modified:** JsonExportTest.kt
- **Committed in:** e10c5a5

**2. [Rule 3 - Blocking] Fixed androidApp build failure: androidx.core version conflict**
- **Found during:** Task 2 (androidApp:assembleDebug)
- **Issue:** `androidx.core:core:1.19.0` requires AGP 9.1.0+; project has AGP 9.0.1
- **Fix:** Removed explicit `androidx.core.ktx` dependency from androidApp build.gradle.kts; FileProvider is available transitively through existing dependencies
- **Files modified:** androidApp/build.gradle.kts (reverted addition)
- **Committed in:** a98dc93

**3. [Rule 1 - Bug] Fixed test assertion for single-word track names**
- **Found during:** Task 2 (GREEN test run)
- **Issue:** `exportFileNamesDateIsInYyyyMmDdFormat` expected `parts.size >= 5` but single-word names produce 4 underscore-separated parts
- **Fix:** Changed assertion to `parts.size >= 4`
- **Files modified:** GpxExportTest.kt
- **Committed in:** a98dc93

---

**Total deviations:** 3 auto-fixed (2 bug, 1 blocking)
**Impact on plan:** All auto-fixes necessary for correctness and build. No scope creep.

## Issues Encountered

- AGP version mismatch with androidx-core required removing an unnecessary explicit dependency.
- KMP `JsonPrimitive.int` is JVM-only; used `jsonPrimitive.content.toInt()` instead.

## Known Stubs

- `IosExportShareTarget` is a compile-safe stub that returns `Saved` without presenting `UIActivityViewController`. Full iOS wiring deferred to macOS/Xcode runtime testing environment.
- `LocalExportWriter` is implemented but not wired into the export button flow; the Android share target handles its own file staging. The writer is available for future use (e.g., saving a local copy before sharing).

## Threat Flags

None — all threat mitigations (T-03-19 through T-03-24) are addressed:
- T-03-19: Exports require explicit button taps (no automatic/batch)
- T-03-20: ExportFileNames strips path traversal and control chars
- T-03-21: GpxExportService escapes XML special characters
- T-03-22: Exports stage separate copies; canonical paths never exposed
- T-03-23: Source metadata (Demo/Simulated) preserved in exports
- T-03-24: ExportShareTarget carries only explicitly chosen entity artifacts

## User Setup Required

None — no external service configuration required.

## Next Phase Readiness

- Phase 3 is **COMPLETE** (8/8 plans executed).
- SESS-01 through SESS-05 are satisfied: track marking, timing sessions, review, trace rendering, and export with platform share handoff.
- Ready for Phase 4: Ghost Lap + Live Delta, or Phase 3 verification (`/gsd-verify-work`).
- iOS runtime verification (Xcode, UIActivityViewController) requires macOS environment.

---

*Phase: 03-local-sessions-review-and-export*
*Completed: 2026-06-25*
