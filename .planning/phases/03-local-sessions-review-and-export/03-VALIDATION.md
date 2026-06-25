---
phase: 03
slug: local-sessions-review-and-export
status: draft
nyquist_compliant: false
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

| Task ID | Plan | Wave | Requirement | Threat Ref | Secure Behavior | Test Type | Automated Command | File Exists | Status |
|---------|------|------|-------------|------------|-----------------|-----------|-------------------|-------------|--------|
| 03-W0-01 | TBD | 0 | SESS-01 | T-03-01 | Draft/session state survives reload and does not become formal history until Save | unit/integration | `.\gradlew.bat :shared:testAndroidHostTest --tests "*SessionControllerTest*"` | ❌ W0 | ⬜ pending |
| 03-W0-02 | TBD | 0 | SESS-01 | T-03-02 | Payload/index writes remain recoverable across failed writes | unit/integration | `.\gradlew.bat :shared:testAndroidHostTest --tests "*FileSessionStoreTest*"` | ❌ W0 | ⬜ pending |
| 03-W0-03 | TBD | 0 | D-06/D-09/D-11 | T-03-03 | Reference-line extraction uses continuous marking traces and rejects outliers without lap timing | unit/fixture | `.\gradlew.bat :shared:testAndroidHostTest --tests "*ReferenceLineExtractorTest*"` | ❌ W0 | ⬜ pending |
| 03-W0-04 | TBD | 0 | SESS-02/SESS-03 | T-03-04 | Review summaries preserve lap list, best lap, duration, sample count, GPS quality, and canonical lat/lon trace data | unit | `.\gradlew.bat :shared:testAndroidHostTest --tests "*ReviewSummaryTest*"` | ❌ W0 | ⬜ pending |
| 03-W0-05 | TBD | 0 | SESS-03 | T-03-05 | Trace projection does not mutate saved geographic coordinates | unit | `.\gradlew.bat :shared:testAndroidHostTest --tests "*TraceProjectionTest*"` | ❌ W0 | ⬜ pending |
| 03-W0-06 | TBD | 0 | SESS-04 | T-03-06 | JSON export includes schemaVersion, app/build/source metadata, samples, laps, lines, sectors, and quality summary | unit/golden | `.\gradlew.bat :shared:testAndroidHostTest --tests "*JsonExportTest*"` | ❌ W0 | ⬜ pending |
| 03-W0-07 | TBD | 0 | SESS-05 | T-03-07 | GPX export emits valid GPX 1.1 track points with escaped metadata and expected sample count | unit/golden | `.\gradlew.bat :shared:testAndroidHostTest --tests "*GpxExportTest*"` | ❌ W0 | ⬜ pending |
| 03-W0-08 | TBD | 0 | D-01/D-05/D-42 | T-03-08 | Simulated GPS feed uses normal business flows and labels demo data as simulated | unit/integration | `.\gradlew.bat :shared:testAndroidHostTest --tests "*SimulatedGpsProviderTest*"` | ❌ W0 | ⬜ pending |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

---

## Wave 0 Requirements

- [ ] `shared/src/commonTest/kotlin/com/huanfuli/lapsight/shared/session/SessionControllerTest.kt` — covers SESS-01 and draft transitions.
- [ ] `shared/src/commonTest/kotlin/com/huanfuli/lapsight/shared/storage/FileSessionStoreTest.kt` — covers atomic payload/index writes and recovery.
- [ ] `shared/src/commonTest/kotlin/com/huanfuli/lapsight/shared/track/ReferenceLineExtractorTest.kt` — covers D-06 through D-11 extraction semantics.
- [ ] `shared/src/commonTest/kotlin/com/huanfuli/lapsight/shared/export/JsonExportTest.kt` — covers SESS-04.
- [ ] `shared/src/commonTest/kotlin/com/huanfuli/lapsight/shared/export/GpxExportTest.kt` — covers SESS-05.
- [ ] `shared/src/commonTest/kotlin/com/huanfuli/lapsight/shared/review/ReviewSummaryTest.kt` — covers SESS-02 and SESS-03 summary/trace state.
- [ ] Fixture payloads or builders for clean 10-loop, minimum 5-loop, outlier, noise/drift, dropped samples, and multi-session best-candidate scenarios.

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| Mounted-phone fullscreen Drive/Timing mode is readable and hides nonessential chrome | SAFE-01, SAFE-02, D-29 | Visual readability and distraction level require device/screen inspection | Build Android debug APK, launch on device, enable simulator, switch fullscreen, verify large timing fields, visible safety/GPS state, and hidden bottom navigation |
| Review tab navigation opens Track Review and Timing Session Review from saved history | SESS-02, SESS-03, D-26-D-32 | Compose UI navigation/state can be partially unit-tested but final flow needs app interaction | Create demo track/session with simulated GPS, save both, open Review tab, open each detail, verify correct detail type and trace/summary content |
| Platform export handoff works | SESS-04, SESS-05 | Shared exporters can be unit-tested, but Android/iOS share/save surfaces are platform behavior | From Review detail, export JSON and GPX; verify files can be saved/shared and filenames are LapSight-prefixed |
| iOS runtime behavior | PLAT-02, Phase 3 UI/storage | Current environment is Windows without Xcode | Run the same smoke flow on macOS/Xcode when available |

---

## Validation Sign-Off

- [ ] All tasks have `<automated>` verify or Wave 0 dependencies
- [ ] Sampling continuity: no 3 consecutive tasks without automated verify
- [ ] Wave 0 covers all missing test files above
- [ ] No watch-mode flags
- [ ] Feedback latency is one task commit or less
- [ ] `nyquist_compliant: true` set in frontmatter after Wave 0 tests exist and plans map task IDs

**Approval:** pending
