# LapSight

LapSight is a phone-first lap timing and ghost-delta app for karting, track driving, and cycling. The phone companion app is the source of truth for GPS samples, track profiles, timing state, saved sessions, review data, and future Meta glasses HUD output.

LapSight is not a generic fitness tracker. It is being built as a mounted-phone timing instrument for closed-course and private-track use.

## Current Status

The app has completed the Phase 5 course-profile work and is in the Phase 5.1 field-validation / hardening gate. The Android app is runnable, supports a switchable Phone GPS / Simulated feed, and has been build-validated with Android Fused Location Provider wired into the existing shared provider interface.

Latest local Android builds have also been installed and launched on an ADB-connected Pixel 10 Pro. Remaining MVP gate work is real-world field evidence: outdoor Phone GPS validation, mounted-display UAT, and the final Go / Hardening Required / No-Go decision.

Implemented:

- Kotlin Multiplatform shared domain logic.
- Compose Multiplatform UI shared by Android and iOS.
- Clean-room lap engine with start/finish crossing, sector timing, lap filters, and deterministic replay tests.
- Local-first storage for track captures, V2 course profiles, timing sessions, ghost references, and exports.
- Track setup workflow with closed reference path, start/finish boundary, sectors, profile revisions, duplicate/rename/archive, and wrong-course preflight.
- Mounted-phone Drive UI with portrait and landscape timing surfaces, live lap state, speed trace, GPS diagnostics, recovery for unfinished sessions, a course-first pre-timing screen, and a two-pane landscape cockpit.
- Ghost lap and delta-to-best support gated by exact course compatibility.
- Review UI grouped by sessions, tracks, and raw captures, with push-navigation detail screens, lap x sector tables, telemetry chart/replay, trace rendering, and JSON/GPX export.
- Track detail and editor rendering that use one beautified course map; `Edit course` switches that map into editable mode instead of rendering a second trace.
- Aspect-correct, zoom-to-fit course rendering for Drive, Review, and editor canvases.
- Theme settings for System, Dark, and Light modes, plus unit and display preferences, backed by a shared semantic theme/component layer.
- Android Fused Location Provider feed mapped into the same shared `LocationSampleProvider` and `LocationSample` model used by the simulator.
- Runtime Android fine-location permission request path from the shared UI.

Not implemented yet:

- Live iOS Core Location feed.
- Field-tested GPS smoothing and telemetry quality tuning.
- External GNSS support.
- Meta glasses HUD bridge.

## Real GPS Status

Android now injects an `AndroidFusedLocationSampleProvider` into the same shared `LocationSampleProvider` boundary as the deterministic simulator. The Drive and Settings UI can switch between:

- `PhoneGps`: Android Fused Location Provider fixes, requiring precise location permission.
- `Simulated`: deterministic replay fixtures for regression testing, demos, and offline UAT.

Both feeds produce the same shared `LocationSample` values and are persisted with explicit `LocationSource` provenance, so Review can distinguish real phone GPS sessions from simulated data.

Remaining real-GPS work:

- Validate Android Phone GPS outdoors on a closed course with the current Ready thresholds: 25 m horizontal accuracy, 15 s fix freshness, and 0.9 Hz minimum sample rate.
- Tune smoothing and telemetry quality thresholds with recorded data.
- Implement the iOS Core Location provider against the same shared interface.
- Preserve replay-based tests so algorithmic behavior remains verifiable.

## Project Structure

```text
LapSight/
├─ androidApp/   Android application package, manifest, and activity entry point
├─ iosApp/       Xcode project and SwiftUI entry point
├─ shared/       Shared KMP domain logic, Compose UI, storage, and tests
└─ .planning/    Project context, requirements, roadmap, phase plans, and state
```

## Build And Test

Android debug build:

```powershell
.\gradlew.bat :androidApp:assembleDebug
```

Install on a connected Android device:

```powershell
.\gradlew.bat :androidApp:installDebug
```

Shared tests:

```powershell
.\gradlew.bat :shared:testAndroidHostTest
```

Full local verification used for the latest hardening pass:

```powershell
.\gradlew.bat :shared:testAndroidHostTest :androidApp:assembleDebug
```

Launch after install, if `adb` is not on `PATH`:

```powershell
& "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe" shell monkey -p com.huanfuli.lapsight -c android.intent.category.LAUNCHER 1
```

iOS:

Open `iosApp/` in Xcode on macOS and run the `iosApp` target. iOS runtime verification requires macOS and a simulator or physical iOS device.

## Development Notes

- Android builds require an Android SDK configured through `ANDROID_HOME`, `ANDROID_SDK_ROOT`, or `local.properties`.
- The shared lap engine must remain independent from UI and platform location APIs.
- Algorithmic behavior should be covered with synthetic or recorded replay data.
- Local device screenshots, window dumps, logcat files, spreadsheets, and `.planning/evidence/` captures should stay out of Git.

## Safety Positioning

LapSight is intended for closed courses, karting tracks, private test areas, and training contexts. It must not be positioned for public-road racing.

The app should remain passive while moving. Users should configure sessions while stopped, mount the phone securely, and treat phone GPS as approximate telemetry rather than racing-grade timing equipment.

## Planning Docs

- Project context: `.planning/PROJECT.md`
- Requirements: `.planning/REQUIREMENTS.md`
- Roadmap: `.planning/ROADMAP.md`
- Current state: `.planning/STATE.md`
- Stack research: `.planning/research/STACK.md`
- Lap engine research: `.planning/research/LAP_ENGINE.md`
