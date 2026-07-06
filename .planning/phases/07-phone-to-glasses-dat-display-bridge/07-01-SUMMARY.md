---
phase: 07-phone-to-glasses-dat-display-bridge
plan: 01
subsystem: infra
tags: [gradle, android, meta-dat-sdk, github-packages, manifest, bluetooth]

# Dependency graph
requires: []
provides:
  - "GitHub Packages Maven feed wired into dependencyResolutionManagement with GITHUB_TOKEN/local.properties credential"
  - "mwdat 0.8.0 version catalog aliases (mwdat-core, mwdat-display, mwdat-mockdevice)"
  - "androidApp implementation deps on libs.mwdat.core / libs.mwdat.display"
  - "Developer Mode (0/0) manifestPlaceholders for mwdat_application_id / mwdat_client_token"
  - "BLUETOOTH / BLUETOOTH_CONNECT / INTERNET manifest permissions"
  - "DAT meta-data (APPLICATION_ID, CLIENT_TOKEN, DAM_ENABLED=true) bound to placeholders"
  - "BROWSABLE/VIEW registration-callback intent-filter (scheme lapsight) on MainActivity"
  - "android-minSdk raised from 24 to 29 (required by mwdat-display AAR)"
affects: [07-02, 07-03, 07-04, 07-05, 07-06]

# Tech tracking
tech-stack:
  added: ["com.meta.wearable:mwdat-core:0.8.0", "com.meta.wearable:mwdat-display:0.8.0"]
  patterns: ["local.properties-backed Gradle credential lookup (GITHUB_TOKEN env fallback)"]

key-files:
  created: []
  modified:
    - settings.gradle.kts
    - gradle/libs.versions.toml
    - androidApp/build.gradle.kts
    - androidApp/src/main/AndroidManifest.xml

key-decisions:
  - "android-minSdk bumped 24 -> 29: manifest merger rejected mwdat-display's own minSdk 29 floor; no compatible-library alternative exists (first-party Meta SDK)."
  - "Registration callback intent-filter uses URL scheme 'lapsight' (not specified in RESEARCH/PATTERNS; chosen to match applicationId convention)."
  - "mwdat-mockdevice alias defined but not added to main implementation — reserved for 07-03's androidTestImplementation per plan instruction."

patterns-established:
  - "GitHub Packages credentialed Maven repo pattern: java.util.Properties loaded from local.properties in settings.gradle.kts, GITHUB_TOKEN env var takes precedence over the local.properties github_token key."

requirements-completed: [MR-01]

# Metrics
duration: 14min (Tasks 1-2) + checkpoint wait + 6min (Task 3 verification)
completed: 2026-07-06
---

# Phase 7 Plan 01: Meta DAT SDK Build Enablement Summary

**androidApp now compiles against `com.meta.wearable:mwdat-core`/`mwdat-display` 0.8.0 with DAM/BLE manifest surface declared, after bumping `android-minSdk` from 24 to 29 to satisfy the SDK's own manifest floor.**

## Performance

- **Duration:** ~14 min for Tasks 1-2 (config/manifest edits), then a blocking human checkpoint for the GitHub PAT, then ~6 min for Task 3 verification once the PAT was supplied.
- **Started:** 2026-07-06T16:36:17-04:00 (base commit fa30c61)
- **Completed:** 2026-07-06T16:50:33-04:00
- **Tasks:** 3 (2 auto + 1 human checkpoint)
- **Files modified:** 4

## Accomplishments
- Meta GitHub Packages Maven feed resolves `mwdat-core`/`mwdat-display` 0.8.0 given a `read:packages` PAT (via `GITHUB_TOKEN` env or `local.properties` `github_token`, never committed).
- `androidApp` declares the DAT permission/DAM manifest surface (BLUETOOTH/BLUETOOTH_CONNECT/INTERNET permissions, APPLICATION_ID/CLIENT_TOKEN/DAM_ENABLED meta-data, registration-callback intent-filter) needed by every downstream 07-0x glasses plan.
- `./gradlew :androidApp:assembleDebug` is green end-to-end with the DAT SDK on the classpath, after raising `android-minSdk` to 29 (the value `mwdat-display:0.8.0` itself requires).

## Task Commits

Each task was committed atomically:

1. **Task 1: Wire the GitHub Packages feed, version catalog, and DAT deps** - `aae589d` (feat)
2. **Task 2: Declare the DAT permissions and DAM metadata in the manifest** - `027c00b` (feat)
3. **Task 3: Provide the GitHub read:packages PAT and confirm the DAT build resolves** - checkpoint resolved by user (PAT supplied via gitignored `local.properties`); verification fix committed as `6c8b587` (fix: minSdk 24 -> 29)

_Note: Task 3 is a human checkpoint, not an auto task — no separate "task commit" beyond the minSdk fix discovered during its verification._

## Files Created/Modified
- `settings.gradle.kts` - Added `java.util.Properties`-backed `local.properties` loader and the `maven.pkg.github.com/facebook/meta-wearables-dat-android` repo (GITHUB_TOKEN env, falling back to `github_token` property) to `dependencyResolutionManagement.repositories`.
- `gradle/libs.versions.toml` - Added `[versions] mwdat = "0.8.0"`, `mwdat-core`/`mwdat-display`/`mwdat-mockdevice` library aliases, and bumped `android-minSdk` from `"24"` to `"29"`.
- `androidApp/build.gradle.kts` - Added `implementation(libs.mwdat.core)` and `implementation(libs.mwdat.display)` beside `libs.google.playServices.location`; added `manifestPlaceholders["mwdat_application_id"] = "0"` and `["mwdat_client_token"] = "0"` inside `defaultConfig`.
- `androidApp/src/main/AndroidManifest.xml` - Added `BLUETOOTH`/`BLUETOOTH_CONNECT`/`INTERNET` `<uses-permission>` entries; added a BROWSABLE/VIEW registration-callback `<intent-filter>` (scheme `lapsight`) on `MainActivity`; added `APPLICATION_ID`/`CLIENT_TOKEN`/`DAM_ENABLED` `<meta-data>` entries bound to the `${mwdat_*}` placeholders.

## Decisions Made
- **minSdk 24 -> 29:** `./gradlew :androidApp:assembleDebug` failed manifest merge with `uses-sdk:minSdkVersion 24 cannot be smaller than version 29 declared in library [com.meta.wearable:mwdat-display:0.8.0]`. No compatible-library alternative exists for a first-party Meta SDK, so `android-minSdk` in `gradle/libs.versions.toml` was raised to `29`; `shared/build.gradle.kts` and `androidApp/build.gradle.kts` both read this catalog value, so no other file needed a matching edit.
- **Registration callback URL scheme:** RESEARCH.md and PATTERNS.md left the scheme unspecified ("your URL scheme" / "myexampleapp" placeholder in the cloned SDK sample). Chose `lapsight` to match the app's `applicationId` (`com.huanfuli.lapsight`) convention. This is a placeholder value a later phase (07-03, where `Wearables.startRegistration` is actually invoked) can revisit if a different scheme is preferred.
- **mwdat-mockdevice deferred:** alias added to the version catalog per Task 1's acceptance criteria, but deliberately left out of `androidApp/build.gradle.kts` `implementation` — the plan explicitly reserves it for 07-03 to add as `androidTestImplementation`.

## Deviations from Plan

None beyond what the plan itself anticipated. Task 1's action block explicitly called for verifying `android-minSdk = 24` against the AAR's minSdk "on first sync" and bumping it if the manifest merger demanded a higher floor — this occurred exactly as flagged, once Task 3 supplied a working PAT to actually run the merge.

## Issues Encountered
- The first `./gradlew :androidApp:assembleDebug` run failed with the anticipated manifest-merge minSdk conflict (`mwdat-display:0.8.0` requires minSdk >= 29). Resolved by bumping `android-minSdk` from `24` to `29` in `gradle/libs.versions.toml` (see Decisions Made). Re-run succeeded (`BUILD SUCCESSFUL`, 57 tasks, `:androidApp:assembleDebug` executed).
- `assembleDebug` logged benign "Unable to strip the following libraries" warnings for several `mwdat-core`/Facebook-infra native `.so` files (packaged unstripped) — not a build failure, no action needed.

## User Setup Required
The user already completed the one required external step for this plan: creating a GitHub PAT (classic, `read:packages` scope) and adding `github_token=<PAT>` to the gitignored `local.properties`. Verified `local.properties` remains untracked (`git ls-files` confirms no match) and the token value does not appear anywhere in tracked files (`git grep` for the literal token value returns nothing). No further setup is required for this plan; production `mwdat_application_id`/`mwdat_client_token` credentials from the Wearables Developer Center remain explicitly out of scope (deferred to production signing, per the plan).

## Next Phase Readiness
- `androidApp` builds cleanly against the DAT SDK 0.8.0 classpath; `android-minSdk` is now 29 project-wide (via the shared version catalog), which downstream 07-0x plans should treat as the new floor rather than re-deriving it.
- The DAM/BLE manifest surface (permissions, meta-data, registration callback) is in place for 07-03 to wire `Wearables.initialize`/`startRegistration` against.
- The `lapsight` URL scheme chosen for the registration callback is a placeholder — 07-03 (or the Settings "Glasses" area work) should confirm or revise it before shipping.
- No blockers for 07-02+; the one-time human PAT dependency for this repo is now resolved and persisted locally (gitignored), so subsequent plans in this phase should not need to re-request it unless the local.properties is lost.

---
*Phase: 07-phone-to-glasses-dat-display-bridge*
*Completed: 2026-07-06*
