# LapSight

LapSight is a phone-first lap timing and ghost delta app for karting, track driving, and cycling. Phase 1 is a Kotlin Multiplatform / Compose Multiplatform mobile walking skeleton with a simulator-backed GPS probe dash.

The phone app is the source of truth for GPS, timing state, session data, and future Meta Display Glasses HUD output.

## Current Status

Implemented in Phase 1:

- Kotlin Multiplatform project structure.
- Android app entry point.
- iOS Xcode app entry point.
- Shared Compose Multiplatform UI.
- Simulator-backed GPS probe state.
- Closed-course and GPS accuracy messaging.

Not implemented yet:

- Real Android Fused Location Provider.
- Real iOS Core Location provider.
- Lap engine.
- Session persistence.
- Ghost lap / delta-to-best.
- Meta glasses HUD bridge.

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

## Sources

- Kotlin Multiplatform: https://kotlinlang.org/docs/multiplatform/
- Compose Multiplatform: https://kotlinlang.org/docs/compose-multiplatform-and-jetpack-compose.html
- Android KMP plugin: https://developer.android.com/kotlin/multiplatform/plugin
- KMP wizard template: https://github.com/Kotlin/kmp-wizard
