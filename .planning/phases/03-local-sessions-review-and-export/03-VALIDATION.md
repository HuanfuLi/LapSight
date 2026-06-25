---
phase: 03
slug: local-sessions-review-and-export
status: approved
nyquist_compliant: true
wave_0_complete: false
created: 2026-06-25
---

# Phase 03 — Validation Strategy

> Per-phase validation contract for feedback sampling during execution.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | `kotlin.test` through shared Kotlin Multiplatform tests |
| **Config file** | `shared/build.gradle.kts` commonTest dependency on `libs.kotlin.test` |
| **Quick run command** | `.\gradlew.bat :shared:testAndroidHostTest` |
| **Full suite command** | `.\gradlew.bat :shared:check` |
| **Estimated runtime** | ~30-90 seconds on the current Windows environment |

---

## Sampling Rate

- **After every task commit:** Run `.\gradlew.bat :shared:testAndroidHostTest`
- **After every plan wave:** Run `.\gradlew.bat :shared:check`
- **Before `$gsd-verify-work`:** `.\gradlew.bat :shared:check` and `.\gradlew.bat :androidApp:assembleDebug` must pass
- **Max feedback latency:** one task commit

---

## Per-Task Verification Map

> Each Wave 0 (RED) test is the first task of its owning plan. The Plan column names the owning `plan / task`; these tests are written before the implementation task in the same plan.

| Task ID | Plan / Task | Wave | Requirement | Threat Ref | Secure Behavior | Test Type | Automated Command | File Exists | Status |
|---------|-------------|------|-------------|------------|-----------------|-----------|-------------------|-------------|--------|
| 03-W0-01 | 03-06 / Task 1 | 0 | SESS-01 | T-03-11/T-03-12 | Draft/session state survives reload and does not become formal history until Save | unit/integration | `.\gradlew.bat :shared:testAndroidHostTest --tests "*SessionControllerTest*"` | ❌ W0 | ⬜ pending |
| 03-W0-02 | 03-03 / Task 1 | 0 | SESS-04 | T-03-07/T-03-08 | Payload/index writes remain recoverable across failed writes | unit/integration | `.\gradlew.bat :shared:testAndroidHostTest --tests "*FileSessionStoreTest*"` | ❌ W0 | ⬜ pending |
| 03-W0-03 | 03-04 / Task 1 | 0 | D-06/D-09/D-11 | T-03-09 | Reference-line extraction uses continuous marking traces and rejects outliers without lap timing | unit/fixture | `.\gradlew.bat :shared:testAndroidHostTest --tests "*ReferenceLineExtractorTest*"` | ❌ W0 | ⬜ pending |
| 03-W0-04 | 03-06 / Task 1 (extended in 03-07 / Task 1) | 0 | SESS-02/SESS-03 | T-03-14/T-03-16 | Review summaries preserve lap list, best lap, duration, sample count, GPS quality, and canonical lat/lon trace data | unit | `.\gradlew.bat :shared:testAndroidHostTest --tests "*ReviewSummaryTest*"` | ❌ W0 | ⬜ pending |
| 03-W0-05 | 03-07 / Task 1 | 0 | SESS-03 | T-03-16/T-03-17 | Trace projection does not mutate saved geographic coordinates | unit | `.\gradlew.bat :shared:testAndroidHostTest --tests "*TraceProjectionTest*"` | ❌ W0 | ⬜ pending |
| 03-W0-06 | 03-08 / Task 1 | 0 | SESS-04 | T-03-20/T-03-22/T-03-24 | JSON export includes schemaVersion, app/build/source metadata, samples, laps, lines, sectors, and quality summary, and the bytes+filename reach the platform share/save surface | unit/golden | `.\gradlew.bat :shared:testAndroidHostTest --tests "*JsonExportTest*"` | ❌ W0 | ⬜ pending |
| 03-W0-07 | 03-08 / Task 1 | 0 | SESS-05 | T-03-21 | GPX export emits valid GPX 1.1 track points with escaped metadata and expected sample count | unit/golden | `.\gradlew.bat :shared:testAndroidHostTest --tests "*GpxExportTest*"` | ❌ W0 | ⬜ pending |
| 03-W0-08 | 03-01 / Task 1 | 0 | D-01/D-05/D-42 | T-03-01/T-03-03 | Simulated GPS feed uses normal business flows and labels demo data as simulated | unit/integration | `.\gradlew.bat :shared:testAndroidHostTest --tests "*SimulatedGpsProviderTest*"` | ❌ W0 | ⬜ pending |
| 03-W0-09 | 03-06 / Task 1 | 0 | SESS-01/D-15 | T-03-11 | Unfinished draft is recoverable on launch with Resume/Save/Discard and does not auto-promote to history | unit/integration | `.\gradlew.bat :shared:testAndroidHostTest --tests "*DraftRecoveryTest*"` | ❌ W0 | ⬜ pending |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

---

## Wave 0 Requirements

- [ ] `shared/src/commonTest/kotlin/com/huanfuli/lapsight/shared/SimulatedGpsProviderTest.kt` — covers D-01/D-05/D-42 (owned by 03-01 Task 1).
- [ ] `shared/src/commonTest/kotlin/com/huanfuli/lapsight/shared/fixtures/GpsFixtureLibraryTest.kt` — covers D-04 fixture scenarios (owned by 03-01 Task 1).
- [ ] `shared/src/commonTest/kotlin/com/huanfuli/lapsight/shared/storage/FileSessionStoreTest.kt` — covers atomic payload/index writes and recovery (owned by 03-03 Task 1).
- [ ] `shared/src/commonTest/kotlin/com/huanfuli/lapsight/shared/track/ReferenceLineExtractorTest.kt` — covers D-06 through D-12 extraction semantics (owned by 03-04 Task 1).
- [ ] `shared/src/commonTest/kotlin/com/huanfuli/lapsight/shared/session/SessionControllerTest.kt` — covers SESS-01 and draft transitions (owned by 03-06 Task 1).
- [ ] `shared/src/commonTest/kotlin/com/huanfuli/lapsight/shared/session/DraftRecoveryTest.kt` — covers D-13/D-15 launch recovery (owned by 03-06 Task 1).
- [ ] `shared/src/commonTest/kotlin/com/huanfuli/lapsight/shared/review/ReviewSummaryTest.kt` — covers SESS-02/SESS-03 summary state (owned by 03-06 Task 1; trace-state assertions extended by 03-07 Task 1).
- [ ] `shared/src/commonTest/kotlin/com/huanfuli/lapsight/shared/review/TraceProjectionTest.kt` — covers SESS-03 projection (owned by 03-07 Task 1).
- [ ] `shared/src/commonTest/kotlin/com/huanfuli/lapsight/shared/export/JsonExportTest.kt` — covers SESS-04 and the share-target handoff contract (owned by 03-08 Task 1).
- [ ] `shared/src/commonTest/kotlin/com/huanfuli/lapsight/shared/export/GpxExportTest.kt` — covers SESS-05 (owned by 03-08 Task 1).
- [ ] Fixture payloads or builders for clean 10-loop, minimum 5-loop, outlier, noise/drift, dropped samples, and multi-session best-candidate scenarios (owned by 03-01 Task 2).

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| Mounted-phone fullscreen Drive/Timing mode is readable and hides nonessential chrome | SAFE-01, SAFE-02, D-29 | Visual readability and distraction level require device/screen inspection | Build Android debug APK, launch on device, enable simulator, switch fullscreen, verify large timing fields, visible safety/GPS state, and hidden bottom navigation |
| Review tab navigation opens Track Review and Timing Session Review from saved history | SESS-02, SESS-03, D-26-D-32 | Compose UI navigation/state can be partially unit-tested but final flow needs app interaction | Create demo track/session with simulated GPS, save both, open Review tab, open each detail, verify correct detail type and trace/summary content |
| Platform export handoff works | SESS-04, SESS-05 | Shared exporters and the `ExportShareTarget` contract are unit-tested, but Android/iOS share/save surfaces are platform behavior | From Review detail, export JSON and GPX; confirm the Android share sheet (`ACTION_SEND`) / iOS `UIActivityViewController` opens and the file can be saved/shared with LapSight-prefixed filenames |
| iOS runtime behavior | PLAT-02, Phase 3 UI/storage | Current environment is Windows without Xcode | Run the same smoke flow on macOS/Xcode when available, including the iOS `ExportShareTarget` actual |

---

## Validation Sign-Off

- [x] All tasks have `<automated>` verify or Wave 0 dependencies
- [x] Sampling continuity: no 3 consecutive tasks without automated verify
- [x] Wave 0 covers all missing test files above and each row maps to an owning plan/task
- [x] No watch-mode flags
- [x] Feedback latency is one task commit or less
- [x] `nyquist_compliant: true` — every plan task carries an automated verify or a Wave 0 dependency, and all Wave 0 rows map to plan/task IDs

**Note on `wave_0_complete`:** remains `false` at planning time because the Wave 0 test files do not yet exist on disk; it flips to `true` once each plan's Task 1 (RED) is executed and the listed test files are committed.

**Approval:** Approved (planning) — 2026-06-25. Wave 0 coverage is fully mapped to the final 8-plan set.
