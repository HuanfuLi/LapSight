---
phase: 7
slug: phone-to-glasses-dat-display-bridge
status: draft
nyquist_compliant: false
wave_0_complete: false
created: 2026-07-06
---

# Phase 7 — Validation Strategy

> Per-phase validation contract for feedback sampling during execution.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | Kotlin `kotlin.test` / JUnit (KMP). Host tests via AGP `withHostTest`. |
| **Config file** | none — existing `shared` + `androidApp` Gradle setup |
| **Quick run command** | `./gradlew :shared:testAndroidHostTest` |
| **Full suite command** | `./gradlew :shared:testAndroidHostTest :androidApp:assembleDebug` |
| **Estimated runtime** | ~5–6 s warm (host tests); build adds ~tens of seconds |

**Split note (from 07-RESEARCH.md):** the DAT SDK is Android-only, so anything
touching `mwdat-*` CANNOT run under `:shared:testAndroidHostTest`. Keep the
`TimingRunSnapshot → HUD content` mapper **pure** in host-testable code; DAT
session-lifecycle / captouch / disconnect scenarios are **instrumented**
(`./gradlew :androidApp:connectedAndroidTest`, API 29+ emulator via MockDeviceKit).

---

## Sampling Rate

- **After every task commit:** Run `./gradlew :shared:testAndroidHostTest`
- **After every plan wave:** Run the full suite command
- **Before `/gsd:verify-work`:** Full suite must be green
- **Max feedback latency:** ~60 seconds

---

## Per-Task Verification Map

| Task ID | Plan | Wave | Requirement | Threat Ref | Secure Behavior | Test Type | Automated Command | File Exists | Status |
|---------|------|------|-------------|------------|-----------------|-----------|-------------------|-------------|--------|
| _(to be filled by planner)_ | | | MR-01/02/03 | | | unit/instrumented | | | ⬜ pending |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

---

## Wave 0 Requirements

- [ ] Host-testable pure mapper `TimingRunSnapshot → HUD page content` (covers MR-02 rendering logic without DAT SDK)
- [ ] MockDeviceKit instrumented harness for session lifecycle / disconnect (covers MR-01/MR-03 passive-HUD + reconnect behavior)

*Refined by the planner against the final plan set.*

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| HUD readable + passive on real 600×600 glasses | MR-02, MR-03 | Requires physical Meta Display Glasses; no emulator surface | Cast during a session; verify all pages/idle/stale states legible and no interaction needed while moving |
| Real-device captouch tap / tap-and-hold (experimental) | D-07/D-08 | Receive API undocumented in DAT 0.8 | Attempt on hardware; if unreachable, phone-side selector remains the guaranteed control |

*If none: "All phase behaviors have automated verification."*

---

## Validation Sign-Off

- [ ] All tasks have `<automated>` verify or Wave 0 dependencies
- [ ] Sampling continuity: no 3 consecutive tasks without automated verify
- [ ] Wave 0 covers all MISSING references
- [ ] No watch-mode flags
- [ ] Feedback latency < 60s
- [ ] `nyquist_compliant: true` set in frontmatter

**Approval:** pending
