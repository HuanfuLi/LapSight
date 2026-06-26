---
phase: 04
slug: ghost-lap-live-delta
status: approved
nyquist_compliant: true
wave_0_complete: false
created: 2026-06-25
---

# Phase 04 — Validation Strategy

> Per-phase validation contract for Ghost Lap + Live Delta.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| Framework | `kotlin.test` through shared Kotlin Multiplatform tests |
| Quick run command | `.\gradlew.bat :shared:testAndroidHostTest` |
| Full suite command | `.\gradlew.bat :shared:check` |
| Android build command | `.\gradlew.bat :androidApp:assembleDebug` |
| Fixture source | `GpsFixtureLibrary` plus provider-layer `SimulatedGpsProvider` |

---

## Sampling Rate

- After each implementation task: run the targeted shared tests named in that task.
- After each plan wave: run `.\gradlew.bat :shared:testAndroidHostTest`.
- Before `$gsd-verify-work 4`: run `.\gradlew.bat :shared:check` and `.\gradlew.bat :androidApp:assembleDebug`.
- Max feedback latency: one task commit.

---

## Per-Task Verification Map

| Task ID | Plan / Task | Wave | Requirement | Threat Ref | Secure/Safe Behavior | Test Type | Automated Command | File Exists | Status |
|---------|-------------|------|-------------|------------|----------------------|-----------|-------------------|-------------|--------|
| 04-W0-01 | 04-01 / Task 1 | 0 | GHOST-02/GHOST-04 | T-04-01/T-04-02 | Progress curve is monotonic, interpolatable, and testable from synthetic samples | unit | `.\gradlew.bat :shared:testAndroidHostTest --tests "*ProgressCurveBuilderTest*"` | ❌ W0 | ⬜ pending |
| 04-W0-02 | 04-01 / Task 1 | 0 | GHOST-02/GHOST-03 | T-04-01/T-04-03 | Delta sign, unavailable state, and no-stale behavior are deterministic | unit | `.\gradlew.bat :shared:testAndroidHostTest --tests "*LiveDeltaEngineTest*"` | ❌ W0 | ⬜ pending |
| 04-W0-03 | 04-02 / Task 1 | 0 | GHOST-01/GHOST-04 | T-04-04/T-04-05 | Full reference payload persists and simulated references do not pollute real Track refs | storage/integration | `.\gradlew.bat :shared:testAndroidHostTest --tests "*GhostReferenceStoreTest*"` | ❌ W0 | ⬜ pending |
| 04-W0-04 | 04-02 / Task 1 | 0 | GHOST-01/GHOST-02 | T-04-04/T-04-06 | Recorder loads persisted best, updates same-session best immediately, and only commits long-term ref on Save | integration | `.\gradlew.bat :shared:testAndroidHostTest --tests "*TimingGhostIntegrationTest*"` | ❌ W0 | ⬜ pending |
| 04-W0-05 | 04-03 / Task 1 | 0 | GHOST-03 | T-04-07 | UI state formats `-0.218s`, `+0.421s`, `--` and clears stale values | unit | `.\gradlew.bat :shared:testAndroidHostTest --tests "*DeltaDisplayStateTest*"` | ❌ W0 | ⬜ pending |
| 04-W0-06 | 04-04 / Task 1 | 0 | GHOST-01..GHOST-04 | T-04-08/T-04-09 | Variable-pace simulated feed covers slower, faster, and in-session new-best update through normal timing flow | fixture/integration | `.\gradlew.bat :shared:testAndroidHostTest --tests "*GhostVariablePaceFixtureTest*"` | ❌ W0 | ⬜ pending |

---

## Wave 0 Requirements

- [ ] `ProgressCurveBuilderTest.kt` — progress curve model and interpolation baseline.
- [ ] `LiveDeltaEngineTest.kt` — realtime delta math, signs, suppression, stale clearing.
- [ ] `GhostReferenceStoreTest.kt` — payload persistence, schema validation, real/sim isolation.
- [ ] `TimingGhostIntegrationTest.kt` — recorder/controller reference loading and same-session update.
- [ ] `DeltaDisplayStateTest.kt` — display formatting and color/state mapping.
- [ ] `GhostVariablePaceFixtureTest.kt` — provider-layer deterministic UAT fixture.

---

## Manual / Device UAT

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| Android Drive timing shows value-only delta | GHOST-03, SAFE-01, SAFE-02 | Visual hierarchy/readability requires device inspection | Install debug APK, open Drive, use demo feed + saved Track, Start Timing, verify current lap primary and delta secondary as `--`, `+x.xxxs`, `-x.xxxs` |
| Demo GPS remains provider-layer only | GHOST-01..04 | Need to verify product flow, not special test buttons | Start demo feed before timing, then use normal Start/Stop/Save; no ghost-specific start button should appear |
| Same-session new best updates reference | GHOST-01/GHOST-02 | End-to-end timing flow uses recorder, engine, storage, UI | Run variable-pace scenario; after a purple/new-best lap, next lap's delta compares against the new reference without starting a new session |
| Simulated sessions stay Demo-labeled | GHOST-04 | Visual/source isolation crosses Drive and Review | Save simulated session; verify Review row/detail still show Demo/Simulated and real ghost selection is not polluted |
| iOS runtime behavior | PLAT-02 carryover | Current environment is Windows without Xcode | Repeat smoke flow on macOS/Xcode when available |

---

## Validation Sign-Off

- [x] All plans include automated verification.
- [x] Wave 0 tests cover algorithm, storage, recorder integration, UI state, and deterministic UAT.
- [x] No watch-mode or real GPS dependency is required.
- [x] Safety-critical stale/unavailable delta behavior is explicitly tested.
- [x] `nyquist_compliant: true` at planning time.

**Approval:** approved for Phase 4 planning.
