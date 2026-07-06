# Phase 7: Phone-to-Glasses DAT Display Bridge - Pattern Map

**Mapped:** 2026-07-06
**Files analyzed:** 15 (7 new bridge sources, 1 new test tree, 7 modified wiring/UI)
**Analogs found:** 13 / 15 (2 have no in-repo analog — external SDK reference instead)

> **Read-this-first for the planner.** The bridge is Android-only glue in a new
> `androidApp/.../glasses/` package. It **polls** the shared `SessionController`
> (never adds Flows to the engine), **maps** platform-free snapshots to a pure
> `HudModel`, and **renders** that model to the DAT flexBox DSL. Every excerpt
> below is a real pattern already in this repo. The DAT SDK lifecycle/sendContent
> shape has **no in-repo analog** — mirror the external DisplayAccess sample
> (`C:\Users\16079\Code\meta-wearables-dat-android\samples\DisplayAccess\...`) and
> the `07-RESEARCH.md` Pattern 1/2/3 excerpts for those two files.

---

## File Classification

| New/Modified File | Role | Data Flow | Closest Analog | Match Quality | Test tier |
|-------------------|------|-----------|----------------|---------------|-----------|
| `shared/.../glasses/HudModel.kt` *(new)* | model / mapper | transform | `shared/.../lap/LapDashState.kt` (`from(...)`) + `TimingRunSnapshot` | exact (role+flow) | host (pure) |
| `shared/.../glasses/HudPage.kt` *(new)* | model (enum) | — | `ghost/DeltaDisplateState.DeltaTone` / `ui/AppShell.AppTab` | exact | host |
| `androidApp/.../glasses/hud/DeltaPill.kt` *(new)* | component (render helper) | transform | `ui/components/StatusChip.kt` + `ghost/DeltaDisplayState.kt` | role-match (concept), DSL differs | instrumented render / host logic |
| `androidApp/.../glasses/hud/HudRenderer.kt` *(new)* | renderer | transform (model→flexBox) | `ui/drive/TimingDashboard.kt` (reads snapshot→UI) | role-match; **DSL is DAT, not Compose** | instrumented |
| `androidApp/.../glasses/GlassesBridge.kt` *(new)* | service / controller | event-driven + polling loop | `androidApp/.../AndroidPhoneLocationProvider.kt` (Android glue behind shared seam) + **external** DisplayViewModel | partial (glue pattern); **lifecycle has no in-repo analog** | instrumented (MockDeviceKit) |
| `shared/.../glasses/GlassesConnectionState.kt` *(new)* | model (sealed state) | event-driven | `session/SessionModels.StartTimingResult` / `session/ReadyGate.ReadyState` | exact (sealed-state idiom) | host |
| `shared/.../glasses/GlassesDeviceSummary.kt` *(new)* | model (DTO) | — | platform-free device-summary DTO (id/name/type/isDisplayCapable) for the shared-UI device picker | exact (data class) | host |
| `androidApp/.../glasses/GlassesInput.kt` *(new)* | input router | event-driven / request-response | `ui/drive/DriveScreen.kt` `onStartTiming`/`onStopTiming` → `sessionController` | role-match | instrumented (MockCaptouchKit) |
| `androidApp/src/androidTest/.../glasses/…` *(new)* | test (instrumented) | — | **no in-repo instrumented test** — external `mockdevice-testing/SKILL.md` | none | instrumented |
| `shared/src/commonTest/.../glasses/HudModelTest.kt` *(new)* | test (host) | — | `shared/src/commonTest/.../lap/LapDashStateTest.kt` | exact | host |
| `androidApp/.../MainActivity.kt` *(modify)* | config / composition root | wiring | itself (existing provider/controller wiring, lines 87–126) | exact | — |
| `androidApp/src/main/AndroidManifest.xml` *(modify)* | config | — | itself (permissions + `<meta-data>` + provider, lines 4–40) | exact | — |
| `androidApp/build.gradle.kts` *(modify)* | config (build) | — | itself (deps + `defaultConfig`, lines 14–34) | exact | — |
| `gradle/libs.versions.toml` *(modify)* | config (version catalog) | — | itself (versions + libraries blocks) | exact | — |
| `settings.gradle.kts` *(modify)* | config (repos) | — | itself (`dependencyResolutionManagement`) | exact | — |
| Shared UI: Settings "Glasses" area + Drive "Cast" toggle/chip *(new/modify)* | component (Compose) | request-response | `ui/SettingsScreen.kt` (`LapCard`+`SegmentedControl`) / `ui/components/StatusChip.kt` / `ui/drive/DriveScreen.kt` | exact | host/compose |
| **KMP seam** — hoist `SessionController` to `MainActivity` *(modify `App.kt`/`AppShell.kt`)* | wiring | — | `ui/AppShell.kt` L98–115 (controller construction) + `App.kt` param plumbing | exact | host |

---

## Pattern Assignments

### `shared/.../glasses/HudModel.kt` (model / mapper, transform) — HOST-TESTABLE

This is the phase's keystone pure file: `TimingRunSnapshot (+ GPS/ready state + page + now + flashUntil) → HudModel`. Copy the **`from(...)` factory + formatting-on-the-struct** idiom exactly from `LapDashState`. Keep it free of any `com.meta.wearable.*` type so it runs under `:shared:testAndroidHostTest` (or `:androidApp:testDebugUnitTest`).

**Analog A — the `from(...)` factory + derived formatted labels** (`shared/.../lap/LapDashState.kt:40-107`):
```kotlin
data class LapDashState( /* … all fields pre-formatted-ready … */ ) {
    val currentLapLabel: String get() = currentLapMillis.formatLapTime()   // format on the struct
    val speedKmhLabel: String
        get() = speedMetersPerSecond?.let { (it * 3.6).roundToInt().toString() } ?: "--"

    companion object {
        fun from(isRunning: Boolean, fixStatus: GpsFixStatus, courseName: String,
                 timing: LapTimingState, latestSample: LocationSample?): LapDashState { … }
    }
}
// Shared formatter — REUSE, never re-derive tenths on Android:
fun Long?.formatLapTime(): String { if (this == null) return "--:--.---"; … }  // L97-107
```

**Analog B — the exact input struct the mapper reads** (`shared/.../session/SessionModels.kt:412-461`): `TimingRunSnapshot` already carries all five MR-02 readouts + `deltaDisplay: DeltaDisplayState` + sector fields + `sampleRateHz`/`accuracyMeters` + `referenceLapMillis`, and ships an `inactive()` neutral instance (L436-459) — use it directly for D-14/D-15 `--` states; do not invent parallel fields.

**Delta tone/sign source** (`shared/.../ghost/DeltaDisplayState.kt:34-77`): `DeltaDisplayState.text` already bakes the sign (`-0.218s`/`+0.421s`/`--`) and `tone: DeltaTone {Faster,Slower,Neutral}`. Map `DeltaTone.Faster → CARET_DOWN`, `Slower → CARET_UP`, `Neutral → no icon` (D-03/D-14). **Do not reformat the delta string** — pass `deltaDisplay.text` through.

**D-05 tenths rule:** truncate `currentLapMillis` to tenths for the HUD (`1:23.4`); the ms→string reference is `formatLapTime()` above — write a tenths variant in the mapper, never a second lap clock.

---

### `shared/.../glasses/HudPage.kt` (enum)

**Analog** (`shared/.../ghost/DeltaDisplayState.kt:13` and `ui/AppShell.kt:55`):
```kotlin
enum class DeltaTone { Faster, Slower, Neutral }   // small, documented, platform-free enum
enum class AppTab { Drive, Review, Settings }
```
Model D-01 as `enum class HudPage { DELTA_ONLY, FOCUSED, TELEMETRY }`; cycle order is the enum order (captouch tap advances by `ordinal`).

---

### `androidApp/.../glasses/hud/HudRenderer.kt` (renderer, model→flexBox DSL)

Maps `HudModel` → `display.sendContent { flexBox { … } }`. **No in-repo Compose analog transfers 1:1** — the DSL is DAT's server-driven UI (`com.meta.wearable.dat.display.views`), not Compose. Use `07-RESEARCH.md` Pattern 3 (L306-325) and the external `DisplayViewModel.kt` for the concrete DSL. The *transferable* in-repo pattern is the **read-snapshot-then-choose-layout** structure of the phone dash.

**Analog (structure only)** — `ui/drive/TimingDashboard.kt` reads `TimingRunSnapshot`/`LapDashState` and lays out hero + footer metrics; mirror the same "pick fields per page, hero = delta+current-lap co-equal (D-02), footer = last/best/speed" decomposition, emitting flexBox `text(...)` with `TextStyle.HEADING/BODY/META` (per `07-UI-SPEC.md` Typography).

**Palette constraint (D-03, hard):** only `FlexBoxBackground={NONE,CARD}`, `TextColor={PRIMARY,SECONDARY}`, `CornerRadius={NONE,SMALL,MEDIUM}`, `IconName` enum (`CARET_UP`/`CARET_DOWN`). No hue. See `07-UI-SPEC.md` Color table and RESEARCH Pitfall 1.

---

### `androidApp/.../glasses/hud/DeltaPill.kt` (component)

The CARD "pill" — caret icon + `deltaDisplay.text`. Conceptually the glasses twin of the phone's `StatusChip`.

**Analog** (`shared/.../ui/components/StatusChip.kt:17-60`): a small tone-driven pill — `enum ChipTone {…}` + a `toneColor()` mapping. Copy the **tone→presentation switch** shape, but map tone to *icon/emphasis* (not color): 
```kotlin
// StatusChip idiom to mirror (tone drives presentation):
enum class ChipTone { Ready, Caution, Error, Neutral, Demo, Accent }
private fun ChipTone.toneColor(): Color = when (this) { … }   // ← becomes: DeltaTone → IconName/TextColor
```
Keep the pill geometry identical whether a reference exists or not (D-14): render the CARD + `--` with no icon when `DeltaTone.Neutral`.

---

### `androidApp/.../glasses/GlassesBridge.kt` (service / lifecycle + 2 Hz poll loop)

Two responsibilities: (1) the **Android-only glue** idiom, which the repo already models; (2) the **DAT session lifecycle**, which it does not — mirror the external sample.

**Analog for the glue boundary** (`androidApp/.../AndroidPhoneLocationProvider.kt:20-60`): an Android class that wraps platform engines and is handed to the shared app, exposing only platform-free types across the seam and owning its own `start()/stop()/reset()` lifecycle. The bridge is the mirror image — it *consumes* shared `SessionController` and *feeds* the Android SDK — but the "Android class owns platform lifecycle, shared sees no platform type" discipline is identical. Note `MainActivity.onDestroy()` (L133-136) already calls `phoneGpsProvider?.stop()`; add `glassesBridge?.stop()` the same way.

**The 2 Hz poll loop (D-05/D-06)** — copy the existing Drive poll cadence, `ui/drive/DriveScreen.kt:184-212`:
```kotlin
LaunchedEffect(...) {
    while (snapshot.isDemoFeedRunning || timingActive || rawRecordingActive) {
        delay(100L)                                   // bridge uses delay(500L) for ~2 Hz
        …
        timingRun = sessionController.timingRunSnapshot()   // ← the exact poll the bridge repeats
    }
}
```
The bridge's loop reads `sessionController.timingRunSnapshot()` (+ `snapshot()` for GPS/ready) each beat, builds `HudModel`, optional frame-dedupe, then `display.sendContent { HudRenderer.render(model) }`. RESEARCH Pattern 2 (L284-304) is the target shape.

**DAT lifecycle + silent reconnect (D-11)** — **no in-repo analog.** Use RESEARCH Pattern 1 (L265-282) and external `DisplayViewModel.kt`/`WearablesRepository.kt`: `createSession(SpecificDeviceSelector)` → collect `session.state`/`session.errors` on scoped jobs → `addDisplay()` → gate loop on `DisplayState.STARTED`; on error/`STOPPED` stop loop and re-create session in background without alerting.

---

### `shared/.../glasses/GlassesConnectionState.kt` (sealed state → StateFlow, platform-free)

**Analog** (`shared/.../session/SessionModels.kt:479-512` `StartTimingResult`, and `session/ReadyGate.kt:90-96` `ReadyState`): the repo's idiom for lifecycle/decision states is a `sealed interface` with `data class`/`data object` cases, never bare booleans:
```kotlin
sealed interface ReadyState {
    data object Ready : ReadyState
    data class NotReady(val reasons: List<ReadyBlocker>) : ReadyState
}
```
Model connection as e.g. `sealed interface GlassesConnectionState { data object Idle/Registered/Connecting/Connected; data class Reconnecting(...)/Error(...) }` and expose it to Compose as a `StateFlow` (the chip/toggle collect it).

---

### `androidApp/.../glasses/GlassesInput.kt` (input router)

Routes glasses `onClick`/captouch → page-cycle (local) or start/stop (delegate to shared controller, D-08). **Never re-implement timing.**

**Analog** (`ui/drive/DriveScreen.kt:350-433`): the UI already routes user intent straight into `sessionController` and pattern-matches the result:
```kotlin
onStartTiming = { … when (val result = sessionController.startTiming(...)) {
        is StartTimingResult.Started -> { … }
        is StartTimingResult.Blocked -> { … } } }
onStopTiming = { … sessionController.stop() … }   // L420-433 (wrapped in recorderMutex.withLock)
```
Copy the "receive event → delegate to the single controller via its public API → react to the sealed result" flow. Keep callbacks fast (RESEARCH anti-pattern: don't block captouch callbacks).

---

### `shared/src/commonTest/.../glasses/HudModelTest.kt` (host test)

**Analog** (`shared/src/commonTest/.../lap/LapDashStateTest.kt:1-52`): plain `kotlin.test` JUnit, asserts formatted labels and factory output. Mirror it for `HudModel.from(...)`: assert all five MR-02 readouts, delta tone/sign, and the D-14 (`--` neutral pill) / D-15 (clock advances, speed/delta `--`) states — **no SDK types**, so it runs on the fast host path.

---

### `androidApp/.../MainActivity.kt` (composition root wiring) — MODIFY

**Analog: itself, L87-136.** `onCreate` already builds Android providers/stores and passes them into `App(...)`; `onDestroy` stops them. Add the same shape for the bridge: construct `GlassesBridge` (or receive the hoisted `SessionController` via the seam below), init `Wearables` (RESEARCH "SDK init" — `Wearables.initialize(this)`), and stop the bridge in `onDestroy`. The permission-callback idiom for `BLUETOOTH_CONNECT` mirrors the existing `requestLocationPermissions` `ActivityResultContracts.RequestMultiplePermissions()` (L37-40, 111-120).

---

### KMP seam — hoist `SessionController` (MODIFY `App.kt` / `AppShell.kt`) — **required enabler**

**The wiring gap (verified):** `SessionController` is constructed **inside** `AppShell` composition (`ui/AppShell.kt:98-115`), and `MainActivity` holds no reference. To feed the bridge from `androidApp`, the *same* instance must reach `MainActivity`.

**Analog for the construction site** (`ui/AppShell.kt:98-115`):
```kotlin
val sessionController = remember {
    SessionController(store = sessionStore, sourceForTrack = { _ -> … })
}
```
**Analog for parameter plumbing** (`shared/.../App.kt:36-73`): `App(...)` already threads platform objects (`orientationController`, `phoneGpsProvider`, stores) down into `AppShell`. Add a callback param (e.g. `onSessionControllerReady: (SessionController) -> Unit = {}`) threaded `App → AppShell`, invoked once after `remember`, so `MainActivity` captures the hoisted controller and hands it to `GlassesBridge`. This is RESEARCH "Option 1 (hoist + poll)" — zero engine changes, no Flow added, clean-room boundary holds (bridge only ever sees `TimingRunSnapshot`/`SessionControllerSnapshot`).

---

### Phone-side UI — Settings "Glasses" area + Drive "Cast" toggle/chip (D-10/D-11) — NEW/MODIFY

**Settings area analog** (`ui/SettingsScreen.kt:62-140`): group inside a `LapCard(title = …)` with `SegmentedControl`/`LapSwitchRow` and a `LapSightTheme.colors.statusCaution` note line. Reuse verbatim for the Glasses registration/device-picker card:
```kotlin
LapCard(title = s.locationSource) {
    SegmentedControl(options = …, selectedIndex = …, onSelect = …, optionEnabled = …)
    sourceNote?.let { Text(it, color = LapSightTheme.colors.statusCaution, style = …bodySmall) }
    LapSwitchRow(label = …, supporting = …, checked = …, enabled = …)   // L135-140
}
```
**Cast toggle + status chip analog:** `StatusChip(text, tone = ChipTone.Caution)` (`ui/components/StatusChip.kt`) for the "Glasses reconnecting…" chip (D-11); place the toggle on the Drive config surface (`ui/drive/DriveConfigSurface.kt`) next to existing controls. Copywriting is fixed by `07-UI-SPEC.md` ("Cast to glasses", "Waiting for GPS", "Ready — start timing", "Glasses reconnecting…").

---

### Build/manifest wiring — MODIFY (all "itself" analogs)

- **`gradle/libs.versions.toml`** (L1-46): add `mwdat = "0.8.0"` under `[versions]` and `mwdat-core/-display/-mockdevice` under `[libraries]`, following the exact `{ module/group+name, version.ref }` shape already used (e.g. L46 `google-playServices-location`). RESEARCH L162-170 gives literal entries.
- **`androidApp/build.gradle.kts`** (L14-34): add `implementation(libs.mwdat.core/display)` beside `libs.google.playServices.location` (L18); add `manifestPlaceholders["mwdat_application_id"]="0"` / `["mwdat_client_token"]="0"` inside `defaultConfig` (L28-34). Verify AAR `minSdk` vs `android-minSdk=24` (libs.versions.toml L4) — bump if merge fails (RESEARCH A3).
- **`androidApp/src/main/AndroidManifest.xml`** (L4-40): add `BLUETOOTH`/`BLUETOOTH_CONNECT`/`INTERNET` `<uses-permission>` beside the existing location ones (L4-5); add the three `<meta-data>` (`APPLICATION_ID`/`CLIENT_TOKEN`/`DAM_ENABLED`) inside `<application>` next to the existing FileProvider `<meta-data>` block (L37-39); add the registration-callback intent-filter. RESEARCH L453-465 gives literal XML.
- **`settings.gradle.kts`** *(modify)*: add the GitHub Packages Maven repo to `dependencyResolutionManagement` with the PAT credential (RESEARCH L151-161). New secret `github_token` in `local.properties` (already gitignored — keep it so).

---

## Shared Patterns

### Pattern: Sealed-state, never a boolean
**Source:** `shared/.../session/ReadyGate.kt:90-96` (`ReadyState`), `session/SessionModels.kt:479-521` (`StartTimingResult`, `SaveDraftResult`).
**Apply to:** `GlassesConnectionState.kt`, any captouch/click result routing in `GlassesInput.kt`.
```kotlin
sealed interface ReadyState {
    data object Ready : ReadyState
    data class NotReady(val reasons: List<ReadyBlocker>) : ReadyState
}
```

### Pattern: Format-on-the-struct + shared formatter reuse
**Source:** `shared/.../lap/LapDashState.kt:28-38, 97-107`; `ghost/DeltaDisplayState.kt:60-77`.
**Apply to:** `HudModel.kt` — expose pre-formatted glasses labels; reuse `Long?.formatLapTime()` and `DeltaDisplayState.text`; never recompute timing/delta (RESEARCH "Don't Hand-Roll").

### Pattern: Poll the single `SessionController` on a `delay` loop
**Source:** `shared/.../ui/drive/DriveScreen.kt:184-212`.
**Apply to:** `GlassesBridge.kt` render loop (`delay(500L)` for ~2 Hz, D-06). Guard shared mutations with a `Mutex().withLock` exactly as DriveScreen does around `sessionController` calls (L202-207, L303-309, L425-429).

### Pattern: Android-only class behind a shared seam; stopped in `onDestroy`
**Source:** `androidApp/.../AndroidPhoneLocationProvider.kt:20-60`; `MainActivity.kt:97-102, 133-136`.
**Apply to:** `GlassesBridge.kt` construction + teardown; keep all `com.meta.wearable.*` imports in `androidApp` (RESEARCH anti-pattern / Pitfall 5).

### Pattern: Tokenized theme, zero inline hex / `.sp`
**Source:** `shared/.../ui/Spacing.kt` (`LapSightTheme.spacing.*`), `ui/components/StatusChip.kt`, `ui/SettingsScreen.kt`.
**Apply to:** all phone-side Glasses UI (Settings card, Drive toggle/chip). Glasses HUD is exempt — it uses the DAT DSL's `flexBox(padding=12, gap=6)` per `07-UI-SPEC.md`.

### Pattern: Route intent → single controller public API → match sealed result
**Source:** `shared/.../ui/drive/DriveScreen.kt:350-433`.
**Apply to:** `GlassesInput.kt` (D-08 start/stop delegation), phone-side cast toggle.

### Pattern: Host-test the pure mapper with `kotlin.test`
**Source:** `shared/src/commonTest/.../lap/LapDashStateTest.kt:1-52`.
**Apply to:** `HudModelTest.kt`. Keep the mapper SDK-free so it runs under `:shared:testAndroidHostTest` / `:androidApp:testDebugUnitTest`.

---

## No Analog Found

Files with no close in-repo match — the planner should use the **external DAT SDK** (`C:\Users\16079\Code\meta-wearables-dat-android`) + `07-RESEARCH.md` code excerpts instead:

| File | Role | Data Flow | Reason / Source to use |
|------|------|-----------|------------------------|
| `androidApp/.../glasses/GlassesBridge.kt` (lifecycle half) | service | event-driven | No DAT session/registration/reconnect lifecycle exists in-repo. Mirror external `DisplayViewModel.kt`/`WearablesRepository.kt` + RESEARCH Pattern 1 (L265-282) and "Registration + device picker" (L473-484). |
| `androidApp/.../glasses/hud/HudRenderer.kt` (DSL half) | renderer | transform | No `sendContent { flexBox { … } }` DSL usage in-repo (repo UI is Compose). Mirror external sample + RESEARCH Pattern 3 (L306-325); palette limited to `NONE/CARD`, `PRIMARY/SECONDARY`, `CARET_UP/DOWN`. |
| `androidApp/src/androidTest/.../glasses/…` | test (instrumented) | — | No existing `androidTest` tree in `androidApp`. Bootstrap from external `mockdevice-testing/SKILL.md` (RESEARCH L486-495: `MockDeviceKit`/`MockCaptouchKit`); add emulator (API 29+) to CI for `connectedAndroidTest`. |

---

## Metadata

**Analog search scope:** `androidApp/src/**`, `shared/src/commonMain/**` (session, ghost, lap, ui, ui/drive, ui/components), `shared/src/commonTest/**`, `gradle/`, root Gradle files.
**Files scanned:** ~20 read in full/targeted; key analogs: `LapDashState.kt`, `SessionModels.kt` (`TimingRunSnapshot`), `SessionController.kt`, `ReadyGate.kt`, `DeltaDisplayState.kt`, `DriveScreen.kt`, `AppShell.kt`, `App.kt`, `SettingsScreen.kt`, `StatusChip.kt`, `Spacing.kt`, `AndroidPhoneLocationProvider.kt`, `MainActivity.kt`, `AndroidManifest.xml`, `androidApp/build.gradle.kts`, `libs.versions.toml`, `LapDashStateTest.kt`.
**Clean-room note:** every mapper/model/state file (`HudModel`, `HudPage`, `GlassesConnectionState`) must import **zero** `com.meta.wearable.*` and **zero** Compose types so the pure mapper is host-testable and the KMP boundary (ARCH-01, verified in Phase 5.1) holds; only `HudRenderer`, `DeltaPill` (render half), `GlassesBridge`, `GlassesInput`, and `MainActivity` touch the DAT SDK, all inside `androidApp`.
**Pattern extraction date:** 2026-07-06
