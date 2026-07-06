# Phase 7: Phone-to-Glasses DAT Display Bridge - Research

**Researched:** 2026-07-06
**Domain:** Meta Wearables Device Access Toolkit (DAT) `mwdat-display` on Android + KMP shared-state consumption
**Confidence:** HIGH on SDK API surface and the KMP read seam; MEDIUM on refresh-rate ceiling; LOW on real-device captouch receive API (documented gap)

## Summary

Phase 7 adds an Android-only passive HUD on Meta Ray-Ban Display glasses, driven by the phone
app's existing timing state. The Meta DAT SDK (`com.meta.wearable:mwdat-*`, version **0.8.0**)
is fetched from Meta's GitHub Packages Maven repo, requires DAM (`DAM_ENABLED=true`) plus
Bluetooth/Internet permissions, and renders via a declarative `display.sendContent { flexBox { … } }`
DSL where **each send replaces the entire screen**. The lifecycle is
`Wearables.initialize` → `startRegistration` → `REGISTERED` → `createSession(selector)` →
`session.start()` → `DeviceSessionState.STARTED` → `session.addDisplay()` → `DisplayState.STARTED`
→ `sendContent`. This is verified directly against the cloned SDK skills and the DisplayAccess
sample.

Two findings materially affect the locked CONTEXT decisions and must be surfaced to the
planner and to discuss-phase:

1. **The colored delta pill (D-03) is NOT achievable with the SDK's palette.** The display DSL
   exposes **no arbitrary color**: `FlexBoxBackground` is only `{NONE, CARD}`, `TextColor` is
   only `{PRIMARY, SECONDARY}`, and `CornerRadius` is only `{NONE, SMALL, MEDIUM}` (no pill/full).
   Ahead/behind therefore **cannot** be encoded with green/red background or text color. The
   redundant-encoding intent survives via **CARD background + directional icon (`CARET_UP` /
   `CARET_DOWN`) + explicit sign + value text** — but color drops out of the encoding set.

2. **The real-device captouch receive API (D-07/D-08 temple tap / tap-and-hold) is undocumented.**
   `MockCaptouchKit` simulates `tap`/`tapAndHold` for tests, implying a real gesture API exists,
   but no captouch event flow is exposed on `Display`, `DeviceSession`, or `Wearables` in the
   public 0.8 reference or SDK skills. The **only documented glasses→phone input path** is
   `button`/clickable-`flexBox` `onClick` callbacks routed back to the phone. This is a
   HIGH-value open question the planner must resolve (hardware validation / Meta MCP) or design
   around using on-screen clickable elements.

**Primary recommendation:** Build the bridge as Android-only glue in `androidApp` that polls the
existing shared `SessionController.timingRunSnapshot()` on its own ~2 Hz loop and maps it to
`sendContent` screens. Consume only platform-free shared types (`TimingRunSnapshot`,
`DeltaDisplayState`, `ReadyState`) so the clean-room boundary holds. Design the delta as
CARD + caret-icon + sign + text (no color), and route page-cycle / start-stop through documented
clickable callbacks until the captouch receive API is confirmed on hardware.

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions
- **D-01:** Three selectable HUD pages: **DELTA-ONLY** (delta pill + current lap), **FOCUSED**
  (driving default: delta pill + current-lap hero, small footer of last/best/speed), **TELEMETRY**
  (Focused plus lap counter).
- **D-02:** Hero is the **delta pill + current lap, co-equal** (two large readouts); last/best/speed
  as a small footer.
- **D-03:** Delta rendered as a **colored pill**: sign + arrow icon + value (`▼ -0.34` ahead /
  `▲ +0.34` behind); **background color reinforces ahead/behind**. Redundant encoding
  (shape + color + icon + sign). Exact enum values to be confirmed in research; the *intent*
  (colored delta pill, arrow up/down) stands. → **See Research Finding: color is not available;
  see Pitfall 1 and the D-03 fallback design.**
- **D-04:** **No always-on sector row.** Transient **sector flash**: on each sector crossing the
  hero current-lap clock slot briefly shows the sector split (`S2 +0.12`) for ~1.5 s, then reverts
  to the running clock. Implemented as a "flash-until" window sampled by the normal render loop.
- **D-05:** Push live-ticking readouts (current-lap clock, delta) at **~2 Hz with tenths precision**
  (`1:23.4`), matching the phone GPS fix rate.
- **D-06:** **Single uniform render-and-push loop** at ~2 Hz. No separate immediate-event path;
  lap completion and sector flash appear on the next beat (≤~0.5 s latency). Frame-dedupe of
  byte-identical screens is planner's discretion.
- **D-07:** Page selected two ways: (a) **phone-side page selector** on Drive screen; (b) **captouch
  TAP** on temple cycles DELTA-ONLY → FOCUSED → TELEMETRY.
- **D-08:** **Captouch TAP-AND-HOLD reserved for start/end session** (stationary action). Glasses
  only emit the input; the **phone owns and executes** start/stop.
- **D-09:** Neural-band (sEMG) input is **NOT available** to DAT apps. Glasses input limited to
  captouch (`tap`/`tapAndHold`, testable via `MockCaptouchKit`) and display button/clickable callbacks.
- **D-10:** One-time registration/pairing/device-selection lives in a **Settings "Glasses" area**;
  a compact **"Cast to glasses" toggle + connection status** lives on the Drive screen.
- **D-11:** **Silent auto-reconnect** on mid-session disconnect. Phone timing unaffected; bridge
  retries in background; Drive shows a small non-blocking **status chip**; HUD resumes automatically.
  No mid-drive alerts.
- **D-12:** Firmware / update-glasses-app prompts (`DEVICE_UPDATE_REQUIRED`,
  `DAT_APP_ON_THE_GLASSES_UPDATE_REQUIRED`) handled in the **Settings pairing flow** via
  `openFirmwareUpdate` / `openDATGlassesAppUpdate` — not surfaced mid-drive.
- **D-13:** **GPS-status idle screen** when casting is active but timing hasn't started / GPS not
  Ready: show fix state + accuracy and "Waiting for GPS" → "Ready — start timing", using the
  phone's existing Ready thresholds (25 m accuracy, 15 s freshness, 0.9 Hz — `ReadyGate`).
- **D-14:** **Neutral `--` pill** when no reference lap selected: keep the pill in place showing
  `--` with no color/arrow so layout geometry is identical with/without a reference.
- **D-15:** **Stale fix mid-lap:** the current-lap clock **keeps advancing** (wall-clock, matching
  the phone). Speed and delta go to `--`/dim, plus a small **"GPS" warning glyph**.

### Claude's Discretion
- Exact flexBox composition, `TextStyle`/`TextColor`/`IconName` selection, spacing, and font-size
  hierarchy within each page (subject to confirming the SDK's actual enums — D-03).
- Whether to explicitly dedupe identical 2 Hz frames (D-06).
- Precise geometry of the Drive-screen cast toggle / status chip and the Settings "Glasses" area,
  consistent with the existing tokenized theme (`ui/Theme.kt`, `ui/Spacing.kt`).

### Deferred Ideas (OUT OF SCOPE)
- Confirm the DAT color/background palette (D-03) and adapt the delta-pill design — a research
  task within this phase (DONE — see below).
- Speed units on glasses should mirror the phone's `DisplaySettings` (km/h vs mph) — confirm during
  planning/UI work.
- Fine-tune sector-flash duration / page scope on real glasses (~1.5 s, any page with a clock).
- Explicit frame-dedupe of identical 2 Hz frames (D-06) — planner's call.
- iOS glasses support — out of scope for Phase 7 (Android-only); revisit after Android bridge is
  validated on hardware.
</user_constraints>

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|------------------|
| MR-01 | Phone app exposes live timing state to a Meta Wearables DAT Display bridge | The shared `SessionController.timingRunSnapshot(): TimingRunSnapshot` and `snapshot()` already carry every readout the HUD needs (current/last/best/reference lap, delta, speed, accuracy, sectors, sample rate). No new lap math. Seam: expose the app's single `SessionController` instance to the Android bridge; see "KMP Integration Seam." |
| MR-02 | Glasses HUD can show current lap, last lap, best lap, speed, and delta | All five values exist on `TimingRunSnapshot` with shared formatting (`formatLapTime()`, `DeltaDisplayState.text`, `speedKmhLabel`). Rendered via `sendContent { flexBox { text(...) } }` using `TextStyle` HEADING/BODY/META hierarchy. |
| MR-03 | Glasses HUD remains passive; no interaction required while driving/riding | The ~2 Hz uniform push loop (D-06) requires zero glasses interaction to display. Page/session control is optional (phone-side selector is the primary, MR-03-safe path). Captouch is a stationary convenience only. |
</phase_requirements>

## Architectural Responsibility Map

| Capability | Primary Tier | Secondary Tier | Rationale |
|------------|-------------|----------------|-----------|
| GPS acquisition, lap detection, ghost/delta math | Shared engine (phone) | — | Single source of truth (MR-01, ARCH-01). Glasses duplicate zero logic. |
| Live timing state snapshot | Shared `SessionController` | — | `timingRunSnapshot()` is platform-free; both Compose UI and the Android bridge read it. |
| Glasses session lifecycle (register/session/display) | `androidApp` DAT bridge | — | DAT SDK is Android-only; belongs in `androidApp`, never in `shared`. |
| HUD rendering (flexBox screens) | `androidApp` DAT bridge | — | Uses Android-only `com.meta.wearable.dat.display.*` types; maps shared snapshot → `sendContent`. |
| Glasses→phone input (page cycle / start-stop) | `androidApp` DAT bridge | Shared `SessionController` (executes start/stop) | Bridge receives the callback and delegates to the shared controller (D-08). |
| Cast toggle / status chip / Settings "Glasses" area | Shared Compose UI | `androidApp` bridge (provides connection StateFlow) | UI is Compose Multiplatform but only rendered on Android; state provided by the bridge. |

## Standard Stack

### Core
| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| `com.meta.wearable:mwdat-core` | 0.8.0 | SDK init, registration, device selection, `DeviceSession` lifecycle | Required base for any DAT app [VERIFIED: cloned SDK `getting-started/SKILL.md` + `libs.versions.toml`] |
| `com.meta.wearable:mwdat-display` | 0.8.0 | `Display` capability, `sendContent` flexBox DSL, icons/buttons/images | The `mwdat-display` capability named by the phase [VERIFIED: cloned SDK `display-access/SKILL.md`] |
| `com.meta.wearable:mwdat-mockdevice` | 0.8.0 | `MockDeviceKit` / `MockCaptouchKit` for hardware-free tests | Enables automated lifecycle/permission/captouch scenarios [VERIFIED: cloned SDK `mockdevice-testing/SKILL.md` + CHANGELOG 0.8.0] |

### Supporting (already in repo — reuse, do not add)
| Library | Version | Purpose | When to Use |
|---------|---------|---------|-------------|
| `kotlinx.coroutines` (Flow) | existing | Collect `session.state`, `session.errors`, `display.state`; run the 2 Hz loop | Bridge state observation + render loop |
| `androidx.lifecycle` (`lifecycleScope`/`viewModelScope`) | existing | Scope for SDK Flow collectors | Mirror DisplayAccess sample's ViewModel pattern |
| `com.google.android.gms:play-services-location` | existing (`libs.google.playServices.location`) | already provides phone GPS | No change; timing state already flows from it |

### Alternatives Considered
| Instead of | Could Use | Tradeoff |
|------------|-----------|----------|
| `SpecificDeviceSelector(deviceId)` (user-picked) | `AutoDeviceSelector(filter = { it.isDisplayCapable() })` | Auto is simpler but bypasses the D-10 Settings device picker. Use Specific for the Settings flow; Auto is acceptable only for a "connect to the one obvious device" shortcut. |
| Polling `timingRunSnapshot()` on the bridge's own 2 Hz loop | Add a `StateFlow<TimingRunSnapshot>` to `SessionController` and collect it | A StateFlow is cleaner reactively, but the existing app is **poll-based** (UI already polls `timingRunSnapshot()` on a `delay(100L)` loop). Polling matches D-06 exactly and avoids touching the shared engine. Prefer polling unless a Flow is trivially added. |

**Installation (Gradle — `androidApp` module):**

`settings.gradle.kts` (add the GitHub Packages Maven repo to `dependencyResolutionManagement`):
```kotlin
maven {
    url = uri("https://maven.pkg.github.com/facebook/meta-wearables-dat-android")
    credentials {
        username = ""
        password = System.getenv("GITHUB_TOKEN")
            ?: localProperties.getProperty("github_token")
    }
}
```
`gradle/libs.versions.toml`:
```toml
[versions]
mwdat = "0.8.0"
[libraries]
mwdat-core = { group = "com.meta.wearable", name = "mwdat-core", version.ref = "mwdat" }
mwdat-display = { group = "com.meta.wearable", name = "mwdat-display", version.ref = "mwdat" }
mwdat-mockdevice = { group = "com.meta.wearable", name = "mwdat-mockdevice", version.ref = "mwdat" }
```
`androidApp/build.gradle.kts`:
```kotlin
dependencies {
    implementation(libs.mwdat.core)
    implementation(libs.mwdat.display)
    implementation(libs.mwdat.mockdevice) // or androidTestImplementation if only used in instrumented tests
}
android {
    defaultConfig {
        manifestPlaceholders["mwdat_application_id"] = "0" // 0 = Developer Mode
        manifestPlaceholders["mwdat_client_token"] = "0"
    }
}
```

**Version verification:** `mwdat = "0.8.0"` read directly from the cloned SDK's own
`getting-started/SKILL.md` (`[versions] mwdat = "0.8.0"`) and confirmed by CHANGELOG entry
`## [0.8.0] - 2026-06-25` [VERIFIED: cloned SDK]. Artifacts are hosted on **GitHub Packages**
(not Maven Central / npm / PyPI), so a **GitHub PAT with `read:packages` scope** is required in
`local.properties` (`github_token=…`) or the `GITHUB_TOKEN` env var — currently **absent** from
this repo's `local.properties`.

## Package Legitimacy Audit

> All three packages are **first-party Meta artifacts** published to Meta's official GitHub
> Packages registry (`maven.pkg.github.com/facebook/meta-wearables-dat-android`), not to a public
> registry (npm/PyPI/crates). slopcheck targets npm/PyPI/crates and does not apply to a scoped,
> credentialed GitHub Packages Maven feed. Legitimacy is established by provenance: the artifacts,
> their versions, and their coordinates were read directly from the officially-cloned SDK repo.

| Package | Registry | Age | Downloads | Source Repo | slopcheck | Disposition |
|---------|----------|-----|-----------|-------------|-----------|-------------|
| `com.meta.wearable:mwdat-core` | GitHub Packages (Meta) | v0.8.0 (2026-06-25) | first-party | github.com/facebook/meta-wearables-dat-android | N/A (Maven, not npm/PyPI) | Approved [VERIFIED: cloned SDK] |
| `com.meta.wearable:mwdat-display` | GitHub Packages (Meta) | v0.8.0 | first-party | same | N/A | Approved [VERIFIED: cloned SDK] |
| `com.meta.wearable:mwdat-mockdevice` | GitHub Packages (Meta) | v0.8.0 | first-party | same | N/A | Approved [VERIFIED: cloned SDK] |

**Packages removed due to slopcheck [SLOP] verdict:** none
**Packages flagged as suspicious [SUS]:** none

## Architecture Patterns

### System Architecture Diagram

```
   PHONE GPS ─▶ LocationSampleProvider ─▶ Lap Engine ─▶ SessionController  (SHARED, platform-free)
                                                              │  timingRunSnapshot(): TimingRunSnapshot
                                                              │  snapshot(): GPS/ready state
                                                              ▼
   ┌──────────────────────── androidApp (Android-only DAT bridge) ────────────────────────┐
   │                                                                                        │
   │  GlassesBridge  ── polls SessionController @ ~2 Hz ──▶  HudModel (page, delta,        │
   │       │                                                  clock, flash-until window)    │
   │       │                                                        │                        │
   │       │                                                        ▼                        │
   │       │                                          HudRenderer.render(page) ──▶ flexBox   │
   │       │                                                        │                        │
   │       ▼                                                        ▼                        │
   │  DAT session lifecycle:                          display.sendContent { flexBox { … } }  │
   │  Wearables.initialize → startRegistration →                    │ (replaces whole screen)│
   │  REGISTERED → createSession(SpecificDeviceSelector)            │                        │
   │  → session.start() → DeviceSessionState.STARTED               │                        │
   │  → session.addDisplay() → DisplayState.STARTED  ──────────────┘                        │
   │       ▲                                                                                 │
   │       │  session.errors / display failures / disconnect ─▶ silent auto-reconnect (D-11)│
   │       │                                                                                 │
   │  clickable/button onClick  ◀── glasses input (documented path) ── temple captouch (?)   │
   │       │                                                                                 │
   │       └─▶ delegates start/stop to shared SessionController (D-08)                       │
   └────────────────────────────────────────────────────────────────────────────────────────┘
                    │ connection StateFlow (registered / connecting / connected / reconnecting)
                    ▼
   Shared Compose UI: Drive-screen cast toggle + status chip (D-10/D-11); Settings "Glasses" area
                      (register / pick device / firmware+app update prompts, D-12)
```

The **glasses render pipeline is a pure read-projection** of shared timing state — the arrow from
`SessionController` into the bridge is one-directional for display. The only reverse arrow is glasses
input → `SessionController.startTiming/stop` (D-08), which flows through the same public API the
phone UI already uses.

### Recommended Project Structure (androidApp module)
```
androidApp/src/main/kotlin/com/huanfuli/lapsight/glasses/
├── GlassesBridge.kt          # SDK lifecycle: init, registration, session, addDisplay, reconnect
├── GlassesConnectionState.kt # sealed state exposed as StateFlow to the Compose UI (chip/toggle)
├── hud/
│   ├── HudModel.kt           # platform-free-ish view model derived from TimingRunSnapshot + page + flash window
│   ├── HudPage.kt            # enum DELTA_ONLY, FOCUSED, TELEMETRY (D-01)
│   ├── HudRenderer.kt        # maps HudModel → sendContent { flexBox { … } } per page
│   └── DeltaPill.kt          # the CARD + caret-icon + sign + value composition (D-03 fallback)
└── GlassesInput.kt           # routes clickable/captouch callbacks → page cycle / SessionController
androidApp/src/androidTest/kotlin/…/glasses/   # MockDeviceKit instrumented tests
```

### Pattern 1: Session lifecycle (mirror DisplayAccess sample)
**What:** Register → create session → wait `STARTED` → `addDisplay()` → wait `DisplayState.STARTED`
before sending content. Collect `session.state`, `session.errors`, and `display.state` on separate
scoped jobs; guard shared `session`/`display` refs with a lock; recreate (never reuse) a session
after a terminal `STOPPED`.
**When to use:** The whole bridge connection flow.
```kotlin
// Source: cloned SDK plugins/mwdat-android/skills/display-access/SKILL.md + samples/DisplayAccess DisplayViewModel.kt
Wearables.createSession(SpecificDeviceSelector(deviceId)).fold(
    onSuccess = { session ->
        scope.launch { session.errors.collect { handleError(it) } }
        scope.launch { session.state.collect { if (it == DeviceSessionState.STARTED && display == null) attachDisplay(session) } }
        session.start() // returns Unit; async failures surface on session.errors
    },
    onFailure = { error, _ -> handleError(error) },
)
// in attachDisplay: session.addDisplay().fold(onSuccess = { d -> scope.launch { d.state.collect { if (it == DisplayState.STARTED) enableSending() } } }, …)
```

### Pattern 2: Uniform ~2 Hz render-and-push loop (D-05/D-06)
**What:** One coroutine loop that, each beat, reads the latest shared snapshot, decides whether a
sector-flash window is active, renders the active page, and calls `sendContent`. No separate
event path.
```kotlin
// Bridge owns `page`, `flashUntilEpochMs`; reads shared controller each beat.
scope.launch {
    while (isActive && displayReady) {
        val run = sessionController.timingRunSnapshot()   // platform-free shared type
        val gps = sessionController.snapshot()            // GPS/ready state for D-13/D-15
        val model = HudModel.from(run, gps, page, now(), flashUntilEpochMs)
        if (model != lastSentModel) {                     // optional frame-dedupe (D-06 discretion)
            display.sendContent { HudRenderer.render(model) }.onFailure { … }
            lastSentModel = model
        }
        delay(500L)                                       // ~2 Hz (D-05)
    }
}
```
Render tenths only (`1:23.4`) per D-05 — do NOT re-derive high-rate millis; format from the
snapshot's `currentLapMillis` truncated to tenths, or from wall-clock + base for D-15 continuity.

### Pattern 3: Delta pill — D-03 fallback composition (no color available)
**What:** Encode ahead/behind with **shape + icon + sign + text** (color removed).
```kotlin
// FlexBoxBackground has only NONE|CARD; TextColor only PRIMARY|SECONDARY; CornerRadius NONE|SMALL|MEDIUM.
flexBox(padding = 12, background = FlexBoxBackground.CARD /* the "pill" */, direction = Direction.ROW, gap = 6,
        crossAlignment = Alignment.CENTER) {
    when (tone) {
        DeltaTone.Faster  -> icon(name = IconName.CARET_DOWN)   // ahead / negative
        DeltaTone.Slower  -> icon(name = IconName.CARET_UP)     // behind / positive
        DeltaTone.Neutral -> {} // D-14: no icon, just "--"
    }
    text(deltaDisplay.text, style = TextStyle.HEADING)          // e.g. "-0.34s" / "+0.34s" / "--"
}
```
The sign (`+`/`-`) is already baked into `DeltaDisplayState.text`, and the direction convention
(negative = faster) matches the existing dashboard. CARET_DOWN/CARET_UP are confirmed `IconName`
values. `CARET_UP` for "behind" reads as "worse/up" and `CARET_DOWN` for "ahead" — confirm the
arrow-direction mapping with the user (CONTEXT D-03 wrote `▼ ahead / ▲ behind`, which matches
CARET_DOWN=ahead, CARET_UP=behind).

### Anti-Patterns to Avoid
- **Putting any DAT type in `shared/`.** All `com.meta.wearable.dat.*` imports must stay in
  `androidApp`. The bridge reads shared platform-free snapshots only. Verified boundary: the shared
  engine imports zero Compose/platform types (Phase 5.1 review).
- **Reusing a stopped `DeviceSession`.** After terminal `STOPPED`, create a new session.
- **`video(...)` inside a `flexBox`.** Video must be the root content; irrelevant here (no video).
- **Sending content before `DisplayState.STARTED`.** Gate the render loop on it.
- **Re-implementing lap/delta math for tenths.** Format from the existing snapshot; never recompute.
- **Blocking captouch/clickable callbacks.** Keep them fast; delegate to the bridge/controller.

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| Lap timing / delta on glasses | A second lap engine or delta calc | Read `TimingRunSnapshot.deltaDisplay` + lap millis | MR-01 / ARCH-01; the shared struct is formatting-complete |
| Time string formatting | Custom `M:SS.mmm` on Android | Shared `Long?.formatLapTime()` / `DeltaDisplayState` | Identical strings across phone/glasses; already tested |
| GPS-ready thresholds (D-13) | New accuracy/freshness constants | `ReadyThresholds.Default` (25 m / 15 s / 0.9 Hz) + `ReadyGate` | One conservative gate, already used by the dash |
| Device discovery / metadata | Custom BLE scanning | `Wearables.devices` + `Wearables.devicesMetadata[id]` | SDK owns pairing/connection/compatibility |
| Session/display state machine | Ad-hoc connection booleans | `DeviceSessionState` / `DisplayState` Flows | Typed lifecycle; sample-proven pattern |
| Firmware / app-update prompts | Custom update UX | `Wearables.openFirmwareUpdate` / `openDATGlassesAppUpdate` | Built-in, handles the Meta AI hand-off (D-12) |
| Icon glyphs | Bitmap arrows | `IconName.CARET_UP/CARET_DOWN` (built-in) | "Do not invent string icon names" — enum only |

**Key insight:** The glasses layer is a thin *projection + input router*. Nearly all "hard" work
(timing, delta, GPS quality, formatting, BLE, pairing) is already owned by either the shared engine
or the DAT SDK. The phase's real work is (a) Gradle/manifest/credential wiring, (b) the lifecycle
state machine + silent reconnect, (c) three flexBox page layouts on 600×600, and (d) the connection
UX surfaces.

## Runtime State Inventory

> This is not a rename/refactor phase, but it introduces external-credential and OS-registered
> state worth listing so the planner accounts for it.

| Category | Items Found | Action Required |
|----------|-------------|------------------|
| Stored data | None — the bridge stores no new persistent timing data (reads shared snapshots). A remembered "last paired device id" may be added for reconnect convenience. | Optional: persist selected `DeviceIdentifier` for D-11 reconnect |
| Live service config | Meta AI app on the phone holds registration/pairing state; DAT-app-on-glasses + firmware versions live on the device, not in git. | Handled via SDK `startRegistration` / update actions (D-12) |
| OS-registered state | Android manifest gains DAT `meta-data` (`APPLICATION_ID`, `CLIENT_TOKEN`, `DAM_ENABLED`) + `BLUETOOTH`/`BLUETOOTH_CONNECT`/`INTERNET` permissions; a callback URI scheme for registration. | Add to `androidApp/src/main/AndroidManifest.xml` |
| Secrets/env vars | **GitHub PAT (`read:packages`)** for the GitHub Packages Maven repo — currently absent from `local.properties`. Developer-mode uses `application_id=0`, `client_token=0`; production needs real Wearables Developer Center credentials. | Add `github_token` to `local.properties`; document credential setup |
| Build artifacts | Adding `mwdat-*` may raise the effective `minSdk` (SDK targets Android 10+/API 29 test devices; current `android-minSdk = 24`). | Verify the AAR's `minSdk`; bump `androidApp` minSdk if the merge fails |

## Common Pitfalls

### Pitfall 1: Designing the delta around color that does not exist (D-03)
**What goes wrong:** The plan assumes a green/red delta pill; the SDK has no color API, so tasks
that "set the pill background to green when ahead" cannot be implemented.
**Why it happens:** `FlexBoxBackground` = `{NONE, CARD}` and `TextColor` = `{PRIMARY, SECONDARY}`
only — verified against official 0.8 reference pages. The phone app uses full Compose colors
(`deltaFaster`/`deltaSlower`); the glasses cannot.
**How to avoid:** Encode ahead/behind with **CARD background + `CARET_DOWN`/`CARET_UP` icon +
signed value text** (Pattern 3). Treat color as unavailable. Surface this to discuss-phase so the
D-03 "colored pill" decision is amended to "iconic pill."
**Warning signs:** Any task referencing a background/text color enum value other than
NONE/CARD/PRIMARY/SECONDARY.

### Pitfall 2: Relying on an undocumented captouch receive API (D-07/D-08)
**What goes wrong:** Planning `session.captouch.tap` / a gesture Flow that isn't in the public API;
implementation stalls.
**Why it happens:** `MockCaptouchKit` simulates `tap`/`tapAndHold`, but the *real-device* receive
API is not exposed on `Display`/`DeviceSession`/`Wearables` in the 0.8 reference or SDK skills. The
only documented glasses→phone input is `button`/clickable-`flexBox` `onClick` callbacks.
**How to avoid:** (a) Make the **phone-side page selector the primary** control (MR-03-safe, always
works). (b) For glasses-side control, prefer documented clickable elements; treat temple captouch
tap/tapAndHold as an *enhancement gated on hardware confirmation*. (c) Add a `checkpoint:human-verify`
to confirm the captouch receive API on real glasses (or via the Meta Wearables MCP) before
committing to the D-07/D-08 gesture mapping.
**Warning signs:** A task that subscribes to a captouch event without a cited API reference.

### Pitfall 3: Assuming a safe 2 Hz send rate without a documented ceiling (D-05/D-06)
**What goes wrong:** BLE flicker, latency, or throttling under a naive uniform re-send.
**Why it happens:** No update-rate limit or throttling guidance is documented for `sendContent`.
Each send replaces the whole screen over BLE.
**How to avoid:** Implement the **optional frame-dedupe** (skip byte-identical models) from D-06 by
default; keep the loop at 500 ms; make the interval a tunable constant; validate on hardware.
**Warning signs:** Continuous sends of unchanged content; visible flicker on device.

### Pitfall 4: Sending content before the display is ready / after disconnect
**What goes wrong:** `sendContent` fails or throws; the HUD appears frozen.
**Why it happens:** Content is only valid between `DisplayState.STARTED` and a stop/disconnect.
**How to avoid:** Gate the loop on `DisplayState.STARTED`; on `session.errors` / `STOPPED`, stop the
loop and enter silent-reconnect (D-11) without alerting the driver.

### Pitfall 5: Leaking the DAT SDK into the shared module
**What goes wrong:** Breaks the KMP clean-room boundary (ARCH-01) and iOS compilation.
**How to avoid:** All DAT code in `androidApp`. The bridge consumes only `TimingRunSnapshot`,
`SessionControllerSnapshot`, `DeltaDisplayState`, `ReadyState` (all platform-free).

## KMP Integration Seam (deep dive — MR-01)

**How the phone exposes state today (verified):**
- `SessionController` (shared, platform-free) owns timing. It exposes **pull methods**, not Flows:
  - `fun timingRunSnapshot(): TimingRunSnapshot` — every HUD readout (current/last/best/reference
    lap millis, `currentLapNumber`, `lapCount`, `speedMetersPerSecond`, `accuracyMeters`,
    `sampleRateHz`, `headingDegrees`, sector fields, `deltaDisplay: DeltaDisplayState`, `isActive`).
  - `fun snapshot(): SessionControllerSnapshot` — draft/GPS-facing state for the D-13 idle screen.
  - `fun startTiming(...)` / `fun stop()` — the exact API the phone UI uses; the bridge routes D-08
    start/stop here.
- The Drive UI is **already poll-based**: `DriveScreen` calls `sessionController.timingRunSnapshot()`
  inside a `LaunchedEffect { while(active) { delay(100L); … } }` loop. There is **no
  `StateFlow<TimingRunSnapshot>`** today.
- The current-lap clock is re-derived from base + wall-clock (`RunningLapTimeText`), which is exactly
  the D-15 "keep advancing on stale fix" behavior — the glasses should mirror this.

**Where `SessionController` lives:** It is constructed **inside** the shared `App`/`AppShell`
composition. `MainActivity` does **not** currently hold a reference; it only passes providers/stores
into `App()`. **This is the key wiring gap:** to feed the glasses from `androidApp`, the bridge needs
a handle to the *same* `SessionController` instance the UI drives.

**Recommended seam (two viable options — planner picks):**
1. **Hoist the controller** (preferred for cleanliness): construct the single `SessionController`
   in the composition root / a shared `AppState` holder, expose it to `MainActivity` (e.g., via a
   callback the shared `App` invokes with the controller, or a small shared holder object), and hand
   it to `GlassesBridge`. The bridge polls it on its own 2 Hz loop. Zero engine changes; zero Compose
   in the bridge.
2. **Add a reactive seam**: give `SessionController` a `StateFlow<TimingRunSnapshot>` and a
   GPS/ready `StateFlow`, updated wherever the recorder already updates. The bridge collects them.
   Cleaner reactively but touches the shared engine and changes the current poll model.

Given D-06 is explicitly a poll loop, **Option 1 (hoist + poll) is the lowest-risk, most consistent
choice.** Either way, the bridge only ever sees platform-free types, so the clean-room boundary holds.

**Speed units (deferred idea):** `speedMetersPerSecond` is raw on the snapshot; the phone converts
via `DisplaySettings.speedUnit` (km/h ×3.6 / mph ×2.2369). The glasses HUD should read the same
`DriveDisplaySettings` (already loaded in `MainActivity`) so units mirror the phone.

## Code Examples

### Manifest additions (androidApp/src/main/AndroidManifest.xml)
```xml
<!-- Source: cloned SDK getting-started/SKILL.md + display-access/SKILL.md -->
<uses-permission android:name="android.permission.BLUETOOTH" />
<uses-permission android:name="android.permission.BLUETOOTH_CONNECT" />
<uses-permission android:name="android.permission.INTERNET" />
<application ...>
    <meta-data android:name="com.meta.wearable.mwdat.APPLICATION_ID" android:value="${mwdat_application_id}" />
    <meta-data android:name="com.meta.wearable.mwdat.CLIENT_TOKEN"  android:value="${mwdat_client_token}" />
    <meta-data android:name="com.meta.wearable.mwdat.DAM_ENABLED"   android:value="true" />
    <!-- registration callback: add BROWSABLE VIEW intent-filter with your URL scheme -->
</application>
```

### SDK init (Application.onCreate)
```kotlin
// Source: cloned SDK getting-started/SKILL.md
Wearables.initialize(this).onFailure { error, _ -> Log.e(TAG, error.description) }
```

### Registration + device picker (Settings "Glasses" area, D-10/D-12)
```kotlin
// Source: samples/DisplayAccess WearablesViewModel.kt + WearablesRepository.kt
Wearables.startRegistration(activity)                 // → collect Wearables.registrationState until REGISTERED
Wearables.registrationState.collect { … }
Wearables.registrationErrorStream.collect { … }
Wearables.devices.collect { ids -> /* per-id */ Wearables.devicesMetadata[id]?.collect { device ->
    // show device.name, device.deviceType.description, device.linkState, device.isDisplayCapable(),
    // device.compatibility == DeviceCompatibility.DEVICE_UPDATE_REQUIRED → Wearables.openFirmwareUpdate(activity)
}}
// createSession failure or session.errors == DAT_APP_ON_THE_GLASSES_UPDATE_REQUIRED → Wearables.openDATGlassesAppUpdate(activity)
```

### MockDeviceKit instrumented test scaffold (androidTest)
```kotlin
// Source: cloned SDK mockdevice-testing/SKILL.md + CHANGELOG 0.8.0 (MockCaptouchKit)
val kit = MockDeviceKit.getInstance(context)
kit.enable()                                           // registrationState → Registered by default
val device = kit.pairGlasses(GlassesModel.RAYBAN_META).getOrThrow()
device.powerOn(); device.unfold(); device.don()
// captouch simulation: device.services.captouch.tap() / .tapAndHold()  (MockCaptouchKit)
// disconnection: device.doff(); device.fold(); device.powerOff()
```

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| Implicit CoreUX session, global `Wearables` session APIs | Explicit `DeviceSession` (`addDisplay`/`addStream`, `session.state`/`session.errors`) | 0.7.0 (2026-05) | Use `DeviceSession` APIs; older global session docs are stale |
| `SessionError` / `SessionState` | `DeviceSessionError` / `DeviceSessionState` (adds thermal/battery/peak-power + `DAT_APP_ON_THE_GLASSES_UPDATE_REQUIRED`) | 0.7.0 | Handle the new terminal/error cases |
| `pairRaybanMeta()` / `MockRaybanMeta` | `pairGlasses(GlassesModel.RAYBAN_META)` / `MockGlasses` | 0.8.0 | Use the new factory; old names removed |
| Camera-only DAT | `mwdat-display` capability (FlexBox/Text/Button/Image/Icon + video) requiring DAM | 0.7.0 | Display needs `DAM_ENABLED=true` |

**Deprecated/outdated:** `MockDisplaylessGlasses`→`MockGlasses`; `Capability`/`BaseCapability`
interfaces removed (capabilities are now `Closeable` with `stop()`); `Display.clearDisplay()` added
in 0.8 to blank the screen without stopping the display.

## Assumptions Log

| # | Claim | Section | Risk if Wrong |
|---|-------|---------|---------------|
| A1 | A real-device captouch receive API exists but is undocumented; temple tap/tapAndHold will reach the app somehow | Pitfall 2, D-07/D-08 | HIGH — if there is truly no receive API, D-07/D-08 gesture mapping is impossible; must fall back to on-screen clickable controls. Gate with hardware/MCP verification. |
| A2 | `sendContent` at ~2 Hz is safe over BLE with no throttling penalty | D-05/D-06, Pitfall 3 | MEDIUM — may need dedupe or a slower rate; tunable constant + hardware validation mitigates |
| A3 | The AAR `minSdk` is ≤ current build min (24) or a bump to ~26/29 is acceptable | Runtime State Inventory | LOW-MEDIUM — a forced minSdk bump slightly narrows device support; verify on first Gradle sync |
| A4 | `CARET_DOWN` = ahead/faster and `CARET_UP` = behind/slower matches the user's `▼ ahead / ▲ behind` intent | Pattern 3, D-03 | LOW — cosmetic; confirm arrow direction with user |
| A5 | Exposing the existing `SessionController` instance to `androidApp` (hoist) is acceptable and does not require an engine refactor | KMP Integration Seam | MEDIUM — if the composition can't cleanly hand out the controller, Option 2 (StateFlow) is the fallback |
| A6 | `ButtonStyle` = `{PRIMARY, SECONDARY}`, `ImageSize` includes `FILL`, `Direction` = `{ROW, COLUMN}`, `Alignment` includes `CENTER`, `IconStyle` includes `FILLED` | Standard Stack / examples | LOW — observed in the sample, not enumerated from the reference; the HUD barely uses buttons/images |

## Open Questions (RESOLVED)

1. **Real-device captouch receive API (D-07/D-08).** — **DEFERRED (hardware-gated).**
   - What we know: `MockCaptouchKit` simulates `tap`/`tapAndHold` (0.8 CHANGELOG); documented app
     input is `onClick` on `button`/clickable `flexBox` routed to the phone.
   - What's unclear: How (or whether) a Display app subscribes to raw temple captouch gestures on
     real hardware — no such Flow is in the 0.8 reference, integration guide, or SDK skills.
   - **Resolution:** Deferred to the hardware-gated **07-06 Task 1 `checkpoint:human-verify`**, which
     confirms the captouch receive API on real glasses (or the Meta Wearables MCP) BEFORE any D-07/D-08
     gesture mapping is implemented. The phone-side page selector (07-05) is the guaranteed MR-03-safe
     control regardless of the outcome; 07-06 closes this question in its SUMMARY.

2. **Exact source of GPS fix status / `ReadyState` for the D-13 idle screen.** — **RESOLVED-IN-PLAN.**
   - What we know: `TimingRunSnapshot` carries `accuracyMeters` + `sampleRateHz`; `ReadyGate` +
     `ReadyThresholds.Default` compute Ready; the raw/GPS controller `snapshot()` carries fix state.
   - What's unclear: Which exact shared accessor the bridge should poll for pre-timing fix status
     (the Drive screen uses a `snapshot`/`dashReadyState` combination).
   - **Resolution:** Resolved by **07-02 Task 1** — the bridge reuses the same GPS/ready accessor
     `DriveScreen` uses for `dashReadyState(snapshot)`, surfaced as the platform-free `GlassesGpsState`
     alongside `timingRunSnapshot()` when the controller is hoisted. The exact accessor chosen is
     documented in the 07-02 SUMMARY.

3. **`sendContent` practical refresh ceiling and flicker behavior.** — **MITIGATED.**
   - What we know: whole-screen replacement over BLE; no documented rate limit.
   - **Resolution:** Mitigated by default frame-dedupe + a 500 ms tunable interval constant
     (07-03/07-04), bounding BLE traffic without a documented ceiling; validated on real hardware in
     **07-05 Task 3**, with observed behavior recorded to tune the sector-flash duration (deferred idea).


## Environment Availability

| Dependency | Required By | Available | Version | Fallback |
|------------|------------|-----------|---------|----------|
| Cloned Meta DAT SDK (reference) | All SDK work | ✓ | 0.8.0 | — (canonical local ref) |
| GitHub Packages Maven feed | Fetching `mwdat-*` artifacts | ✗ (no token) | — | Add `github_token` (PAT `read:packages`) to `local.properties` |
| GitHub PAT (`read:packages`) | Gradle auth to the feed | ✗ | — | User must create one (blocking without it) |
| Meta AI app on phone | Registration + pairing at runtime | unknown | — | Required for real-device use; `MockDeviceKit` for automated tests |
| Real Meta Ray-Ban Display glasses | Hardware validation of HUD/captouch/rate | unknown | — | `MockDeviceKit`/`MockCaptouchKit` for lifecycle/input tests (no rendering fidelity) |
| Wearables Developer Center app credentials | Production `APPLICATION_ID`/`CLIENT_TOKEN` | ✗ | — | Developer Mode uses `0`/`0`; production needs real credentials |
| Android emulator/device API 29+ for instrumented tests | MockDeviceKit tests | unknown | — | CI must provide an emulator; these are `androidTest`, not JVM host tests |

**Missing dependencies with no fallback:**
- **GitHub PAT with `read:packages`** — without it, Gradle cannot resolve `mwdat-*`. First setup task.

**Missing dependencies with fallback:**
- Real glasses → `MockDeviceKit`/`MockCaptouchKit` for automated lifecycle/permission/disconnect/captouch.
- Production Meta credentials → Developer Mode (`application_id=0`, `client_token=0`).

## Validation Architecture

### Test Framework
| Property | Value |
|----------|-------|
| Framework | JUnit4. **Shared** host tests: Kotlin/JVM via `:shared:testAndroidHostTest` (per STATE.md). **Bridge** tests: Android **instrumented** (`androidTest`) with `MockDeviceKit` — DAT SDK is Android-only and cannot run in shared host tests. |
| Config file | none new for shared; instrumented tests need an emulator/device (API 29+) |
| Quick run command | `./gradlew :shared:testAndroidHostTest` (shared mapping logic) |
| Full suite command | `./gradlew :shared:testAndroidHostTest` + `./gradlew :androidApp:connectedAndroidTest` (MockDeviceKit) + `./gradlew :androidApp:assembleDebug` |

### Phase Requirements → Test Map
| Req ID | Behavior | Test Type | Automated Command | File Exists? |
|--------|----------|-----------|-------------------|-------------|
| MR-01 | `TimingRunSnapshot` → `HudModel` projection is correct (all 5 readouts, delta tone/sign, `--` states D-14/D-15) | unit (pure, no SDK) | `./gradlew :androidApp:testDebugUnitTest --tests "*HudModel*"` (or shared if mapper is platform-free) | ❌ Wave 0 |
| MR-02 | Each page (DELTA-ONLY/FOCUSED/TELEMETRY) renders the required elements | unit on the model + instrumented render smoke | `:androidApp:testDebugUnitTest` + `:androidApp:connectedAndroidTest` | ❌ Wave 0 |
| MR-03 | HUD requires no glasses interaction to display; passive loop drives it | instrumented (MockDeviceKit lifecycle, no input) | `./gradlew :androidApp:connectedAndroidTest --tests "*GlassesLifecycle*"` | ❌ Wave 0 |
| D-11 | Disconnect (`doff`/`fold`/`powerOff`) → silent reconnect; phone timing unaffected | instrumented (MockDeviceKit) | `:androidApp:connectedAndroidTest --tests "*Reconnect*"` | ❌ Wave 0 |
| D-07/D-08 | Captouch tap → page cycle; tapAndHold → start/stop delegates to controller | instrumented (`MockCaptouchKit`) — gated on real-API confirmation (Open Q1) | `:androidApp:connectedAndroidTest --tests "*Captouch*"` | ❌ Wave 0 |

### Sampling Rate
- **Per task commit:** `./gradlew :shared:testAndroidHostTest` (fast; covers mapper if placed shared)
- **Per wave merge:** add `:androidApp:assembleDebug` + relevant `:androidApp:connectedAndroidTest`
- **Phase gate:** full suite green + `:androidApp:assembleDebug` succeeds before `/gsd:verify-work`

### Wave 0 Gaps
- [ ] `shared/.../glasses/HudModel.kt` + host unit test — MR-01/MR-02 mapping (mapper is pure and
      platform-free in `shared/commonMain`, host-tested without the SDK — resolved in 07-02)
- [ ] `androidApp/src/androidTest/.../glasses/` MockDeviceKit test base (mirror the skill's
      `MockDeviceKitTestCase`) — MR-03/D-11/D-07/D-08
- [ ] Gradle: add `mwdat-mockdevice` (as `androidTestImplementation` if only used in tests) and an
      emulator to CI for `connectedAndroidTest`
- [ ] Confirm whether the delta/page mapper can live in `shared` (platform-free) to run under the
      fast `:shared:testAndroidHostTest` path

## Security Domain

> `security_enforcement` is absent from config → treated as enabled. This phase is a local BLE
> display feature with no auth/PII/network-data-handling of its own, so most ASVS categories are N/A.

### Applicable ASVS Categories
| ASVS Category | Applies | Standard Control |
|---------------|---------|-----------------|
| V2 Authentication | no | No app-level user auth; Meta AI owns device registration |
| V3 Session Management | no | DAT `DeviceSession` is device-local, SDK-managed |
| V4 Access Control | yes (light) | Bluetooth runtime permission gate; request `BLUETOOTH_CONNECT` before init |
| V5 Input Validation | yes (light) | Glasses input is limited to enum gestures/clickable callbacks; validate the routed action, ignore unexpected events |
| V6 Cryptography | no | No app crypto; BLE link security handled by the platform/SDK |
| V14 Config / Secrets | yes | **GitHub PAT and Meta client token are secrets** — keep in `local.properties`/env, never commit; ensure `.gitignore` covers `local.properties` |

### Known Threat Patterns for this stack
| Pattern | STRIDE | Standard Mitigation |
|---------|--------|---------------------|
| Committing the GitHub PAT / Meta `CLIENT_TOKEN` | Information Disclosure | Manifest placeholders from `local.properties`/env; `.gitignore` `local.properties`; `0`/`0` in Developer Mode |
| Distracting/blocking HUD while moving | (Safety, MR-03) | Passive loop; no required interaction; no mid-drive alerts (D-11); phone-side controls primary |
| Stale delta shown as live on GPS loss | Tampering (data integrity) | D-15: delta/speed → `--`+glyph; clock advances on wall-clock; `DeltaDisplayState` already collapses unavailable to `--` |

## Sources

### Primary (HIGH confidence)
- Cloned SDK (canonical, external): `C:\Users\16079\Code\meta-wearables-dat-android`
  - `plugins/mwdat-android/skills/display-access/SKILL.md` — Display DSL, lifecycle, device selection, rules
  - `plugins/mwdat-android/skills/getting-started/SKILL.md` — Gradle/manifest/init, `mwdat = "0.8.0"`
  - `plugins/mwdat-android/skills/permissions-registration/SKILL.md` — registration + permissions
  - `plugins/mwdat-android/skills/session-lifecycle/SKILL.md` — `DeviceSessionState` values
  - `plugins/mwdat-android/skills/mockdevice-testing/SKILL.md` — MockDeviceKit test scaffold
  - `samples/DisplayAccess/app/.../display/DisplayViewModel.kt`, `wearables/WearablesViewModel.kt`, `WearablesRepository.kt`
  - `CHANGELOG.md` — 0.8.0 / 0.7.0 API history, `MockCaptouchKit`, DAM, error cases
- LapSight repo (verified by reading):
  - `shared/.../lap/LapDashState.kt`, `session/SessionModels.kt` (`TimingRunSnapshot`),
    `session/SessionController.kt`, `session/ReadyGate.kt`, `ghost/DeltaDisplayState.kt`,
    `ui/drive/TimingDashboard.kt`, `ui/drive/DriveScreen.kt`, `androidApp/.../MainActivity.kt`,
    `androidApp/build.gradle.kts`, `gradle/libs.versions.toml`, `.planning/config.json`
- Official 0.8 API reference enum pages (CITED):
  - `.../com_meta_wearable_dat_display_views_flexboxbackground` → `NONE`, `CARD`
  - `.../com_meta_wearable_dat_display_views_textcolor` → `PRIMARY`, `SECONDARY`
  - `.../com_meta_wearable_dat_display_views_textstyle` → `HEADING`, `BODY`, `META`
  - `.../com_meta_wearable_dat_display_views_cornerradius` → `NONE`, `SMALL`, `MEDIUM`
  - `.../com_meta_wearable_dat_display_views_iconname` → includes `CARET_UP/DOWN`, `ARROW_*`, `TRIANGLE_*` (150+ total)
  - `.../com_meta_wearable_dat_display_display` (Display interface: `state`, `sendContent`, `clearDisplay`, `stop`, `close`)

### Secondary (MEDIUM confidence)
- Meta Wearables docs (develop/build-integration, mock-device-kit) — confirmed *absence* of a
  documented real-device captouch receive API and of a documented `sendContent` rate limit.

### Tertiary (LOW confidence)
- WebSearch summary stating "custom gesture controls like taps and swipes aren't offered; you can
  listen for standard events like pause, resume, and stop" — directional support for Open Q1;
  reconcile against hardware/MCP before locking D-07/D-08.

## Metadata

**Confidence breakdown:**
- Standard stack / Gradle / lifecycle: HIGH — read directly from the cloned SDK + working sample
- Display DSL palette (D-03 resolution): HIGH — enum values from official 0.8 reference pages
- KMP read seam (MR-01): HIGH on the snapshot types; MEDIUM on the exact controller-hoist mechanics
- Captouch receive API (D-07/D-08): LOW — documented gap; flagged as the top open question
- 2 Hz refresh safety (D-05/D-06): MEDIUM — no documented ceiling; needs hardware validation

**Research date:** 2026-07-06
**Valid until:** ~2026-08-06 for the SDK surface (0.8.0 is recent, 2026-06-25); re-verify if a
new `mwdat` release lands. Repo-side findings valid until the shared session/UI wiring changes.
