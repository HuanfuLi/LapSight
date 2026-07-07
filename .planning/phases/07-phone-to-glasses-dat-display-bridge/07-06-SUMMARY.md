---
phase: 07-phone-to-glasses-dat-display-bridge
plan: 06
subsystem: glasses-input
tags: [meta-dat-sdk, display-click, captouch, mr-03, hardware-gated]

requires:
  - phase: 07-05
    provides: "Phone-side HUD page selector and guaranteed MR-03-safe control path"
provides:
  - "Documented DAT Display click fallback for cycling HUD pages"
  - "Explicit closure of raw captouch tap/tap-and-hold as unavailable in DAT 0.8 public API"
  - "Defensive ignored-event routing for unsupported glasses input events"
affects: [MR-03]

key-files:
  created:
    - androidApp/src/main/kotlin/com/huanfuli/lapsight/glasses/GlassesInput.kt
    - androidApp/src/androidTest/kotlin/com/huanfuli/lapsight/glasses/CaptouchInputTest.kt
  modified:
    - androidApp/src/main/kotlin/com/huanfuli/lapsight/glasses/GlassesBridge.kt
    - androidApp/src/main/kotlin/com/huanfuli/lapsight/glasses/hud/HudRenderer.kt
    - androidApp/src/androidTest/kotlin/com/huanfuli/lapsight/glasses/HudRenderSmokeTest.kt

key-decisions:
  - "DAT 0.8 public APIs do not expose a confirmed real-device raw captouch receive Flow for temple tap/tap-and-hold."
  - "07-06 therefore closes with the approved clickable fallback: a Display root click cycles HUD pages."
  - "Tap-and-hold start/stop is not implemented because the phone-side controls remain the guaranteed safe path and speculative input handling would be unsafe."
  - "Unknown or unsupported glasses-originated events are ignored."

requirements-completed: [MR-03]
duration: partial-session
completed: 2026-07-07
---

# Phase 7 Plan 06: Experimental Captouch Input Summary

07-06 is complete as a hardware/API-gated fallback, not as full raw captouch
start/stop support.

## Accomplishments

- Confirmed the plan's blocking gate outcome: real raw temple captouch receive
  events are not available through the public DAT 0.8 display APIs used by this
  app.
- Added `GlassesInput`, a small defensive router for glasses-originated actions.
- Added a documented Display clickable fallback: clicking the root HUD content
  cycles pages `DELTA_ONLY -> FOCUSED -> TELEMETRY -> DELTA_ONLY`.
- Wired the clickable fallback through `HudRenderer.render(..., onClick = ...)`
  and `GlassesBridge`.
- Added Android test coverage for page cycling and ignored unsupported/unknown
  events.

## Deliberate Non-Implementation

- Tap-and-hold start/stop is not implemented. Without a confirmed real-device
  raw captouch receive API, implementing this would be speculative.
- Glasses never own timing. Phone-side start/stop and the 07-05 phone page
  selector remain the guaranteed MR-03-safe control path.
- No neural-band path exists.

## Automated Verification

- `./gradlew.bat :shared:testAndroidHostTest :androidApp:assembleDebug :androidApp:compileDebugAndroidTestKotlin` - passed on 2026-07-07.
- `CaptouchInputTest` compiles in the Android instrumentation test source set.
- `HudRenderSmokeTest.rootClickHandlerIsAttachedWhenProvided` verifies the DAT
  root click handler is attached and callable.

## Deviations from Plan

- The original best-case path described tap and tap-and-hold if the real
  captouch receive API was confirmed. That API was not confirmed, so the plan
  closed on the allowed fallback path instead.
- The plan's written connected-test command was not run against the user's real
  device. This is intentional: connected Android test flows have previously
  caused app data loss in this project. Compile-time androidTest validation was
  used instead.

## Remaining Risks

- If Meta exposes a real-device captouch receive API in a later DAT SDK, this can
  be reopened as a new plan.
- The root Display click fallback is a convenience only; it is not required for
  the accepted Phase 7 HUD path.

## Self-Check

PASSED. The plan is closed per its hardware-gated fallback rule: unsupported
raw captouch events are not acted on, the phone remains the timing owner, and
the optional fallback cannot block Phase 7 completion.

---
*Phase: 07-phone-to-glasses-dat-display-bridge*
*Completed: 2026-07-07*
