# LapSight

LapSight is a phone-first lap timing and ghost delta app for karting, track driving, and cycling. It is built with Kotlin Multiplatform and Compose Multiplatform. Phase 1 delivered a simulator-backed GPS probe dash; Phase 2 adds a clean-room shared lap engine with sector timing, driven from deterministic replay data.

The phone app is the source of truth for GPS, timing state, session data, and future Meta Display Glasses HUD output.

## Current Status

Implemented in Phase 1:

- Kotlin Multiplatform project structure.
- Android app entry point.
- iOS Xcode app entry point.
- Shared Compose Multiplatform UI.
- Simulator-backed GPS probe state.
- Closed-course and GPS accuracy messaging.

Implemented in Phase 2:

- Clean-room shared lap engine (start/finish line crossing detection).
- Local equirectangular meter projection around a session origin.
- Segment-crossing geometry with interpolated crossing timestamps.
- Direction, minimum-lap-time, cooldown, speed, and accuracy filters.
- Sector-line model, sector crossing detection, and per-lap split timing.
- Deterministic replay runner and synthetic fixtures (covered by tests).
- Mounted-phone dash showing current/last/best lap, lap count, speed, accuracy, and compact sector splits.

Lap timing is still **simulator/replay-backed**: the dash advances a deterministic replay through the engine. Real GPS providers will feed the same engine in a later phase without changing lap logic or UI.

Not implemented yet:

- Real Android Fused Location Provider.
- Real iOS Core Location provider.
- Session persistence, review, and export.
- Ghost lap / delta-to-best.
- Maps, external GNSS, and the Meta glasses HUD bridge.

## Project Structure

```text
LapSight/
├─ androidApp/   Android application package and activity entry point
├─ iosApp/       Xcode project and SwiftUI entry point
├─ shared/       Shared KMP models, state, tests, and Compose UI
└─ .planning/    GSD project docs, requirements, and roadmap
```

## Running

Android:

```powershell
.\gradlew.bat :androidApp:assembleDebug
```

iOS:

Open `iosApp/` in Xcode on macOS and run the `iosApp` target.

Shared checks:

```powershell
.\gradlew.bat :shared:check
```

## Environment Notes

- Android builds require an Android SDK configured through `ANDROID_HOME`, `ANDROID_SDK_ROOT`, or `local.properties`.
- iOS builds require macOS and Xcode.
- Phase 1 can run from simulated GPS samples. Real phone GPS provider wiring is the next implementation step.

## Safety Positioning

LapSight is intended for closed courses, karting tracks, private test areas, and training contexts. It should not encourage public-road racing. Phone GPS is not racing-grade timing equipment; the app surfaces GPS quality so users can judge whether data is usable.

## Planning Docs

- Project context: `.planning/PROJECT.md`
- Requirements: `.planning/REQUIREMENTS.md`
- Roadmap: `.planning/ROADMAP.md`
- Phase 1 plan: `.planning/phases/01-mobile-walking-skeleton-gps-probe/01-PLAN.md`
- Phase 2 plan: `.planning/phases/02-clean-room-lap-engine-v0/02-PLAN.md`
- Phase 2 verification: `.planning/phases/02-clean-room-lap-engine-v0/02-VERIFICATION.md`

## Sources

- Kotlin Multiplatform: https://kotlinlang.org/docs/multiplatform/
- Compose Multiplatform: https://kotlinlang.org/docs/compose-multiplatform-and-jetpack-compose.html
- Android KMP plugin: https://developer.android.com/kotlin/multiplatform/plugin
- KMP wizard template: https://github.com/Kotlin/kmp-wizard
