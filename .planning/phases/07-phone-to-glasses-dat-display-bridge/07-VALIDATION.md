---
phase: 7
slug: phone-to-glasses-dat-display-bridge
status: approved
nyquist_compliant: true
wave_0_complete: true
created: 2026-07-06
---

# Phase 7 — Validation Strategy

> Per-phase validation contract for feedback sampling during execution.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | Kotlin `kotlin.test` / JUnit (KMP). Host tests via AGP `withHostTest`. Instrumented via MockDeviceKit. |
| **Config file** | none — existing `shared` + `androidApp` Gradle setup |
| **Quick run command** | `./gradlew :shared:testAndroidHostTest` |
| **Full suite command** | `./gradlew :shared:testAndroidHostTest :androidApp:assembleDebug` |
| **Instrumented command** | `./gradlew :androidApp:connectedAndroidTest` (API 29+ emulator, MockDeviceKit) |
| **Estimated runtime** | ~5–6 s warm (host tests); build adds ~tens of seconds; instrumented adds emulator boot |

**Split note (from 07-RESEARCH.md):** the DAT SDK is Android-only, so anything
touching `mwdat-*` CANNOT run under `:shared:testAndroidHostTest`. Keep the
`TimingRunSnapshot → HUD content` mapper **pure** in host-testable code; DAT
session-lifecycle / captouch / disconnect scenarios are **instrumented**
(`./gradlew :androidApp:connectedAndroidTest`, API 29+ emulator via MockDeviceKit).

**Shared-type note (07-03):** `GlassesConnectionState` and `GlassesDeviceSummary`
are platform-free shared/commonMain types (no `mwdat-*`); they compile and are
exercised on the fast host path even though the bridge that maps into them is
instrumented.

---

## Sampling Rate

- **After every task commit:** Run `./gradlew :shared:testAndroidHostTest`
- **After every plan wave:** Run the full suite command
- **Before `/gsd:verify-work`:** Full suite must be green
- **Max feedback latency:** ~60 seconds (host); instrumented waves add emulator time

---

## Per-Task Verification Map

| Task ID | Plan | Wave | Requirement | Threat Ref | Secure Behavior | Test Type | Automated Command | File Exists | Status |
|---------|------|------|-------------|------------|-----------------|-----------|-------------------|-------------|--------|
| 07-01 T1 | 01 | 1 | MR-01 | T-07-01, T-07-SC | No PAT/token in tracked files; scoped credentialed feed | build (human-gated) | `./gradlew :androidApp:dependencies --configuration debugRuntimeClasspath` — **gated by T3 PAT** | settings.gradle.kts, libs.versions.toml, build.gradle.kts | ⬜ human-gated |
| 07-01 T2 | 01 | 1 | MR-01 | T-07-02 | BLE/DAM manifest surface, placeholder-bound token | build (human-gated) | `./gradlew :androidApp:assembleDebug` — **gated by T3 PAT** | AndroidManifest.xml | ⬜ human-gated |
| 07-01 T3 | 01 | 1 | MR-01 | T-07-01 | PAT via ignored local config only | checkpoint:human-verify (PAT) | `./gradlew :androidApp:assembleDebug` (post-PAT) | — | ⬜ human-gated |
| 07-02 T1 | 02 | 1 | MR-01 | T-07-05 | Single hoisted controller; no 2nd instance | unit | `./gradlew :shared:testAndroidHostTest` | App.kt, AppShell.kt, GlassesGpsState.kt | ⬜ pending |
| 07-02 T2 | 02 | 1 | MR-02 | T-07-03, T-07-04 | Pure SDK-free mapper; stale fix collapses to `--` | unit (tdd) | `./gradlew :shared:testAndroidHostTest --tests "*HudModelTest*"` | HudModel.kt, HudPage.kt | ⬜ pending |
| 07-02 T3 | 02 | 1 | MR-01/MR-02 | T-07-03 | D-13/14/15 states locked by host tests | unit (tdd) | `./gradlew :shared:testAndroidHostTest --tests "*HudModelTest*"` | HudModelTest.kt | ⬜ pending |
| 07-03 T1 | 03 | 2 | MR-01/MR-03 | T-07-08, T-07-17, T-07-02 | Platform-free shared state/DTO; no STOPPED reuse | build + shared unit | `./gradlew :shared:testAndroidHostTest :androidApp:assembleDebug` | GlassesConnectionState.kt, GlassesDeviceSummary.kt, GlassesBridge.kt | ⬜ pending |
| 07-03 T2 | 03 | 2 | MR-01/MR-03 | T-07-06, T-07-07 | Frame-dedupe; gate-on-STARTED; silent reconnect | build | `./gradlew :androidApp:assembleDebug` | GlassesBridge.kt | ⬜ pending |
| 07-03 T3 | 03 | 2 | MR-03 (D-11) | T-07-07 | Passive HUD (zero input); timing untouched on disconnect | instrumented | `./gradlew :androidApp:connectedAndroidTest --tests "*GlassesLifecycleTest" --tests "*GlassesReconnectTest"` | GlassesLifecycleTest.kt, GlassesReconnectTest.kt | ⬜ pending |
| 07-04 T1 | 04 | 3 | MR-02 | T-07-10 | Palette limited to NONE/CARD/PRIMARY/SECONDARY + carets | build | `./gradlew :androidApp:assembleDebug` | DeltaPill.kt | ⬜ pending |
| 07-04 T2 | 04 | 3 | MR-02 | T-07-03, T-07-09 | Stale dims speed/delta; glanceable pages | build | `./gradlew :androidApp:assembleDebug` | HudRenderer.kt, GlassesBridge.kt | ⬜ pending |
| 07-04 T3 | 04 | 3 | MR-02 | T-07-09, T-07-10 | Per-page + non-timing elements asserted on content tree | instrumented | `./gradlew :androidApp:connectedAndroidTest --tests "*HudRenderSmokeTest"` | HudRenderSmokeTest.kt | ⬜ pending |
| 07-05 T1 | 05 | 4 | MR-02/MR-03 | T-07-12, T-07-18 | Shared UI binds shared types only; no DAT import | build + shared unit | `./gradlew :shared:testAndroidHostTest :androidApp:assembleDebug` | SettingsScreen.kt, App.kt, AppShell.kt | ⬜ pending |
| 07-05 T2 | 05 | 4 | MR-02/MR-03 | T-07-11 | Non-blocking reconnect chip; phone-side page control | build + shared unit | `./gradlew :shared:testAndroidHostTest :androidApp:assembleDebug` | DriveConfigSurface.kt | ⬜ pending |
| 07-05 T3 | 05 | 4 | MR-02/MR-03 | T-07-11 | HUD legible + passive on real 600×600 glasses | checkpoint:human-verify (hardware) | `<human-check>` (real Meta Ray-Ban Display) | — | ⬜ manual |
| 07-06 T1 | 06 | 5 | MR-03 | — | Captouch receive API confirmed before any mapping (Open Q1) | checkpoint:human-verify (hardware) | `<human-check>` (real glasses / Meta Wearables MCP) | — | ⬜ manual/hardware-gated |
| 07-06 T2 | 06 | 5 | MR-03 | T-07-14, T-07-15, T-07-16 | Glasses only trigger; phone owns timing; unmapped events ignored | instrumented (gated on T1) | `./gradlew :androidApp:connectedAndroidTest --tests "*CaptouchInputTest"` | GlassesInput.kt, CaptouchInputTest.kt | ⬜ pending (gated) |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky · human-gated = verification depends on an irreducible human step.*

**Note (07-01):** All three 07-01 tasks are **human-gated by design** — their
automated verification (`:androidApp:assembleDebug` / dependency resolution) cannot
succeed until the user supplies the GitHub `read:packages` PAT at the T3
`checkpoint:human-verify` (Task 3). This is the one irreducible manual step in the
phase; it is not a sampling gap but a hard external dependency (07-RESEARCH.md
"Missing dependencies with no fallback").

---

## Wave 0 Requirements

- [x] Host-testable pure mapper `TimingRunSnapshot → HUD page content` — delivered by **07-02 T2/T3** (`HudModel`/`HudModelTest`), covers MR-02 rendering logic without the DAT SDK.
- [x] MockDeviceKit instrumented harness for session lifecycle / disconnect — delivered by **07-03 T3** (`GlassesLifecycleTest`/`GlassesReconnectTest`), covers MR-01/MR-03 passive-HUD + reconnect behavior. Extended by **07-04 T3** (`HudRenderSmokeTest`) and **07-06 T2** (`CaptouchInputTest`).

*Both Wave 0 scaffolds map to concrete tasks in the final 6-plan set; no MISSING references remain.*

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| GitHub `read:packages` PAT + first DAT resolve/build | MR-01 | Requires the user's GitHub account; Claude cannot mint a PAT | Add `github_token` to gitignored `local.properties`; run `:androidApp:assembleDebug` (07-01 T3) |
| HUD readable + passive on real 600×600 glasses | MR-02, MR-03 | Requires physical Meta Display Glasses; no emulator surface | Cast during a session; verify all pages/idle/stale states legible and no interaction needed while moving (07-05 T3) |
| Real-device captouch tap / tap-and-hold (experimental) | MR-03 (D-07/D-08) | Receive API undocumented in DAT 0.8 | Attempt on hardware / Meta Wearables MCP; if unreachable, phone-side selector remains the guaranteed control (07-06 T1) |

---

## Validation Sign-Off

- [x] All tasks have `<automated>` verify or Wave 0 dependencies (07-01 tasks are gated on the T3 PAT checkpoint; 07-05 T3 and 07-06 T1 are irreducible hardware `human-verify`).
- [x] Sampling continuity: no 3 consecutive tasks without automated verify — the only cluster (07-01 T1–T3) is the irreducible human PAT gate, explicitly noted above.
- [x] Wave 0 covers all MISSING references (pure mapper + MockDeviceKit harness both mapped to tasks).
- [x] No watch-mode flags.
- [x] Feedback latency < 60s on the host path.
- [x] `nyquist_compliant: true` set in frontmatter.

**Approval:** approved (finalized against the 6-plan set; 07-01 config-verification is human-gated by the PAT checkpoint; 07-05 T3 / 07-06 T1 are irreducible hardware gates).
</content>
</invoke>
