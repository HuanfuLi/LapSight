---
phase: 01
name: Mobile Walking Skeleton + GPS Probe
type: implementation
wave: 1
depends_on: []
requirements:
  - PLAT-01
  - PLAT-02
  - PLAT-03
  - PLAT-04
  - PLAT-05
  - GPS-01
  - GPS-02
  - GPS-03
  - GPS-04
  - SAFE-01
  - SAFE-02
  - SAFE-03
  - ARCH-01
  - ARCH-03
  - ARCH-04
files_modified:
  - settings.gradle.kts
  - build.gradle.kts
  - gradle.properties
  - gradle/**
  - androidApp/**
  - iosApp/**
  - shared/**
  - README.md
autonomous: true
---

# Phase 1 Plan: Mobile Walking Skeleton + GPS Probe

## Objective

Create a runnable Kotlin Multiplatform / Compose Multiplatform mobile skeleton for LapSight and replace the template app with a GPS probe dash that can run from simulator-backed state immediately.

## Acceptance Criteria

- Android/iOS project structure exists and follows the AGP 9-compatible `androidApp` + `shared` + `iosApp` split.
- Shared Compose UI shows LapSight GPS probe information.
- Shared code defines platform-independent GPS probe models.
- Dash has portrait/landscape-friendly layout assumptions and large glanceable fields.
- UI includes closed-course and GPS accuracy limitation language.
- Build/test verification is attempted and documented.

## Tasks

### Task 1: Import KMP mobile-shared skeleton

**Action:** Copy the official KMP wizard mobile-shared template into the repository, excluding its `.git` directory.

**Verify:**
- `settings.gradle.kts`, `androidApp`, `iosApp`, and `shared` exist.
- Template package/app names are renamed to LapSight equivalents.

### Task 2: Replace template shared UI with LapSight dash

**Action:** Implement shared Compose UI that displays speed, GPS fix state, horizontal accuracy, update rate, elapsed time, sample count, and start/stop/reset controls.

**Verify:**
- UI code is in shared/commonMain.
- The screen does not depend on Android-only APIs.

### Task 3: Add shared GPS probe state model and simulator

**Action:** Add common Kotlin models for `LocationSample`, `GpsFixStatus`, and `GpsProbeState`, plus a simple simulator controller emitting deterministic sample-like data.

**Verify:**
- Shared tests cover basic formatting/state updates where practical.
- Simulator works without permissions.

### Task 4: Wire Android/iOS entry points to shared app

**Action:** Ensure Android activity and iOS root view load the shared Compose `App()`.

**Verify:**
- Android manifest includes location permissions needed for later real GPS work.
- iOS project remains structurally intact.

### Task 5: Document run commands and environment limits

**Action:** Update README with Android/iOS run commands, current scope, and known local verification limits.

**Verify:**
- README explains that Phase 1 uses a simulator-backed GPS probe and real provider wiring is next.

### Task 6: Run verification

**Action:** Run available Gradle tasks:

- `.\gradlew.bat :shared:check`
- `.\gradlew.bat :androidApp:assembleDebug` if Android SDK is configured

**Verify:**
- Passing tasks are recorded.
- Environment/tooling blockers are recorded with exact command output.

## Success Criteria

1. Repository contains a coherent KMP/CMP mobile project.
2. Shared GPS probe dash is implemented.
3. Project has a clear path for real Android/iOS location providers.
4. At least host/shared Gradle verification is attempted.
5. Changes are committed with a Phase 1 summary.

## Risks

- Local environment may not have Android SDK.
- iOS build cannot be verified on Windows.
- Official template versions may need minor dependency fixes after first Gradle sync.

---

*Plan created: 2026-06-25*
