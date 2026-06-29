# LapSight — Third-Party Licenses, Clean-Room Attestation, and Privacy Note

**Compiled:** 2026-06-29 (Phase 5.1 hardening gate, Plan 04)
**Covers:** ARCH-03 (document all third-party code and licenses), ARCH-04 (GPL projects studied as references, not copied), and the ARCH-03 local-GPS privacy note.
**Source of truth:** `gradle/libs.versions.toml`, `shared/build.gradle.kts`, `androidApp/build.gradle.kts`.

---

## 1. ARCH-03 — Third-Party Dependency Inventory

LapSight adds **no new external packages in Phase 5.1**. The dependency set below is the complete third-party surface of the shipping Android phone app and the shared KMP domain. Every runtime dependency is permissively licensed (Apache-2.0) except the Google Play Services Location SDK (a proprietary Google SDK, Android-app only) and test-only JUnit (EPL-1.0). **No dependency is GPL/AGPL/LGPL-runtime-linked.**

### Runtime dependencies

| Dependency | Version | Module | Used in | License |
|------------|---------|--------|---------|---------|
| Kotlin stdlib / Multiplatform | 2.4.0 | `org.jetbrains.kotlin` | shared + app | Apache-2.0 |
| Compose Multiplatform (runtime, foundation, ui, components-resources) | 1.11.1 | `org.jetbrains.compose.*` | shared UI + app | Apache-2.0 |
| Compose Material 3 | 1.11.0-alpha07 | `org.jetbrains.compose.material3` | shared UI | Apache-2.0 |
| Compose Material Icons (core) | 1.7.3 | `org.jetbrains.compose.material:material-icons-core` | shared UI | Apache-2.0 |
| kotlinx-serialization-json | 1.11.0 | `org.jetbrains.kotlinx:kotlinx-serialization-json` | shared (canonical JSON, export/replay) | Apache-2.0 |
| kotlinx-coroutines-core | 1.11.0 | `org.jetbrains.kotlinx:kotlinx-coroutines-core` | shared | Apache-2.0 |
| Okio | 3.17.0 | `com.squareup.okio:okio` | shared storage/export **I/O seam only** | Apache-2.0 |
| AndroidX Lifecycle (viewmodel-compose, runtime-compose) | 2.11.0-beta01 | `org.jetbrains.androidx.lifecycle:*` | shared UI | Apache-2.0 |
| AndroidX Activity Compose | 1.13.0 | `androidx.activity:activity-compose` | Android app | Apache-2.0 |
| AndroidX Core KTX | 1.19.0 | `androidx.core:core-ktx` | Android app | Apache-2.0 |
| AndroidX AppCompat | 1.7.1 | `androidx.appcompat:appcompat` | Android app | Apache-2.0 |
| Google Play Services — Location | 21.4.0 | `com.google.android.gms:play-services-location` | Android app (Fused Location Provider) | **Proprietary** — Android Software Development Kit License Agreement (Google) |

### Build / tooling / test-only dependencies (not shipped in the runtime classpath)

| Dependency | Version | Scope | License |
|------------|---------|-------|---------|
| Android Gradle Plugin (AGP) | 9.0.1 | build | Apache-2.0 |
| Compose / Kotlin Gradle plugins | per catalog | build | Apache-2.0 |
| Compose UI Tooling / Preview | 1.11.1 | debug/tooling | Apache-2.0 |
| JUnit | 4.13.2 | test | EPL-1.0 |
| kotlin-test / kotlin-test-junit | 2.4.0 | test | Apache-2.0 |
| AndroidX Test (ext-junit, espresso) | 1.3.0 / 3.7.0 | instrumented test | Apache-2.0 |

### License compliance notes

- **Apache-2.0** dependencies require attribution and a copy of the license; this file plus the upstream `NOTICE`/`LICENSE` files packaged in each artifact satisfy that. `androidApp/build.gradle.kts` already excludes duplicate `META-INF/{AL2.0,LGPL2.1}` packaging entries.
- **Google Play Services Location** is distributed under the Android SDK License Agreement (proprietary, redistribution as part of an app is permitted; the SDK itself is not modified or copied into source). It is used solely through the `LocationSampleProvider` boundary (`AndroidFusedLocationSampleProvider`) and never enters the shared clean-room engine.
- **JUnit (EPL-1.0)** is a test-only dependency and is not shipped in the app runtime.
- **No GPL / AGPL / LGPL** dependency is present in any scope.

---

## 2. ARCH-04 — Clean-Room Attestation (GPL not copied)

**Attestation:** No GPL-licensed source code from DovesLapTimer, DovesDataViewer, or any other GPL project has been copied into LapSight. The shared lap/ghost/session/track/storage/export engine is a clean-room implementation written for this project. GPL projects were studied **only as behavioral references**, consistent with AGENTS.md ("Do not copy GPL-licensed code from DovesLapTimer or DovesDataViewer unless the project license decision explicitly allows it" and "Open-source references are research inputs, not direct copy sources").

**Evidence supporting the attestation:**

1. **Verified-clean source grep (Phase 5.1 Plan 04 audit, re-confirmed this session):** a scoped search of `shared/src` for `doves`, `dovesl`, `gpl`, `GPL`, `Doves` returns **zero matches**. (The prior RESEARCH grep reported the same; this audit re-ran it.)
2. **Clean-room architecture boundary verified (ARCH-01 / D-45):** the algorithm engine files in `shared/src/commonMain/.../{lap,ghost,session,track}` import zero Compose/Android/iOS/`java.*` symbols, and `lap/` contains no `Random`/wall-clock/`System.*` calls — the engine is pure and self-contained, not lifted from a platform-coupled GPL codebase. Okio appears only in the three storage/export **I/O** files (`FileSessionStore.kt`, `StoragePaths.kt`, `LocalExportWriter.kt`), which is the legitimate multiplatform persistence seam, not algorithm logic.
3. **No network / analytics / third-party SDK in `commonMain`** (grep for ktor/okhttp/retrofit/firebase/`java.net`/analytics: zero hits) — the shared domain pulls in no opaque third-party code paths.

See `5.1-CODE-REVIEW.md` (Plan 04) Section 12 and the Threat Register closure (T-5.1-05) for the full audit record.

---

## 3. ARCH-03 — Privacy Note: Local GPS Traces

LapSight is a **local-first** application. GPS location traces are **sensitive personal data** and are handled as follows:

- **Local-only storage.** All recorded samples, timing sessions, tracks, ghost references, and exports are persisted to **app-private storage** on the device (`StoragePaths` app-private root; exports staged under an app-private `exports/` directory by `LocalExportWriter`). Nothing is written outside the app sandbox by the app itself.
- **No upload, no cloud, no accounts.** There is no network code in the shared domain (`commonMain` contains no ktor/okhttp/retrofit/firebase/`java.net` imports) and no backend, sync, or login. Location data never leaves the device except when the **user explicitly** invokes the platform share sheet to export a session (JSON/GPX), at which point the user chooses the destination.
- **No analytics or telemetry.** There is no analytics SDK, crash-reporting upload, or usage telemetry in any scope.
- **User-controlled sharing only.** Export uses the platform `ExportShareTarget`; the user decides whether and where to share a file. Export filenames derived from user-entered track names are sanitized against path traversal and filesystem-unsafe characters (`ExportFileNames.sanitizeNameToken`).
- **Location permission.** Precise location is requested from the shared UI path on Android; there is no silent fallback from Phone GPS to Simulated. The user can switch to the Simulated feed at any time.

This satisfies the ARCH-03 privacy-documentation owed for the Phase 5.1 hardening gate (threat T-5.1-04, Information Disclosure — mitigated).

---

## 4. Open Product Decision — Application License (tracked risk)

LapSight's **own** application license is **not yet decided** (`STATE.md` Review Checklist: "Confirm app license policy" — unchecked; ROADMAP Open Decision 1). This is a product/legal decision to resolve before public distribution. It is **not** a Phase 5.1 core-timing blocker: the core is verified clean-room (ARCH-04) and dependency-clean (no GPL; all runtime deps permissive or proprietary-SDK). Recorded here and in `5.1-CODE-REVIEW.md` as a tracked risk.

---

*Phase: 05.1-mvp-field-validation-and-hardening-gate — Plan 04, Task 2*
*Compiled: 2026-06-29*
