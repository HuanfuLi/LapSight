# Phase 7: Phone-to-Glasses DAT Display Bridge - Context

**Gathered:** 2026-07-06
**Status:** Ready for planning

<domain>
## Phase Boundary

Deliver an **Android-only, passive HUD** on Meta Ray-Ban Display glasses, fed by
the phone app's *existing* timing state. The bridge reads summarized live timing
(`LapDashState` + live-delta) and renders it on the 600×600 display via the Meta
Wearables DAT `mwdat-display` capability (`session.addDisplay()` →
`display.sendContent { flexBox { … } }`, where each send **replaces** the prior
screen).

**In scope:** the display bridge, the glasses HUD rendering (pages, delta pill,
sector flash, non-timing states), the phone-side connection/cast UX, and the
captouch input mapping.

**Out of scope / locked constraints (NOT up for discussion):**
- Phone remains the **single source of truth** for GPS, lap detection, storage,
  ghost/delta logic (MR-01, PROJECT constraint). The glasses layer duplicates
  **zero** timing logic — it renders summarized state and emits input events.
- HUD is **passive — no complex interaction while driving/riding** (MR-03 / SAFE).
- Bridge is **Android-only** (DAT SDK is Android; KMP shared state feeds it; iOS
  glasses support is not in this phase).
- Required content elements are fixed by success criteria: current lap, last lap,
  best lap, speed, delta (MR-02).

**Requirements:** MR-01, MR-02, MR-03 (see `.planning/REQUIREMENTS.md`).

</domain>

<decisions>
## Implementation Decisions

### HUD Layout & Pages
- **D-01:** Three selectable HUD pages, mirroring the phone's multi-page feel:
  - **DELTA-ONLY** — delta pill + current lap only (hot-lap glance).
  - **FOCUSED** (driving default) — delta pill + current lap hero, small footer of
    last / best / speed.
  - **TELEMETRY** — same as Focused plus a lap counter (delta, current, last, best,
    speed, lap count).
- **D-02:** Hero is the **delta pill + current lap, co-equal** (two large readouts),
  with last/best/speed as a small footer where present.
- **D-03:** Delta is rendered as a **colored pill** containing sign + arrow icon +
  value (e.g. `▼ -0.34` ahead / `▲ +0.34` behind); the pill **background color**
  reinforces ahead/behind. Intentionally **redundant encoding** (shape + color +
  icon + sign) for glanceability on a limited display. NOTE: exact
  `FlexBoxBackground` / `TextColor` / `IconName` values the SDK exposes must be
  confirmed in research — the *intent* (colored delta pill, arrow up/down) stands
  regardless of which enum values are available.

### Sectors
- **D-04:** **No always-on sector row.** Instead a **transient sector flash**: on
  each sector-line crossing, the hero **current-lap clock slot** briefly shows the
  sector split (e.g. `S2 +0.12`) for **~1.5 s**, then reverts to the running clock.
  Applies on any page that shows the clock. Implemented as state (a "flash-until"
  window) sampled by the normal render loop — see D-06.

### Update Cadence / Refresh Model
- **D-05:** Push live-ticking readouts (current-lap clock, delta) at **~2 Hz with
  tenths precision** (`1:23.4`). This matches the phone GPS fix rate — no
  fabricated precision.
- **D-06:** **Single uniform render-and-push loop** at ~2 Hz. The loop reads the
  latest timing state (including whether we're inside a sector-flash window) and
  sends the active page. **No separate immediate-event path** — lap completion and
  the sector flash simply appear on the next beat (≤~0.5 s latency, acceptable).
  Frame-dedupe of byte-identical screens is left to the planner's discretion if BLE
  traffic / flicker warrants it.

### Page Switching / Captouch Input
- **D-07:** Active page is selected two ways: (a) a **phone-side page selector** on
  the Drive screen (fully passive on the glasses, MR-03-safe), and (b) a **captouch
  TAP** on the temple cycles DELTA-ONLY → FOCUSED → TELEMETRY.
- **D-08:** **Captouch TAP-AND-HOLD is reserved for start/end session** (a
  stationary action, within the passive-while-moving rule). The glasses only emit
  the input event; the **phone still owns and executes** timing start/stop.
- **D-09:** Neural-band (sEMG wristband) input is **NOT available** to third-party
  DAT apps — do not design around it. Glasses-side input is limited to captouch
  (`tap` / `tapAndHold`, testable via `MockCaptouchKit`) and display
  button/clickable callbacks routed to the phone.

### Connection & Activation UX
- **D-10:** Split rare setup from frequent use: **one-time registration / pairing /
  device selection lives in a "Glasses" area under Settings**; a compact **"Cast to
  glasses" toggle + connection status lives on the Drive screen** for per-session
  on/off.
- **D-11:** **Silent auto-reconnect** on mid-session disconnect (out of range, low
  battery, session error). Phone timing continues **unaffected**; the bridge retries
  the session in the background; Drive shows a small **non-blocking status chip**
  ("Glasses reconnecting…"); the HUD resumes automatically. **No mid-drive alerts.**
- **D-12:** Firmware / "update glasses app" prompts (`DeviceCompatibility.
  DEVICE_UPDATE_REQUIRED`, `DAT_APP_ON_THE_GLASSES_UPDATE_REQUIRED`) are handled in
  the **Settings pairing flow** via the SDK's built-in `openFirmwareUpdate` /
  `openDATGlassesAppUpdate` actions — not surfaced mid-drive.

### Non-Timing HUD States
- **D-13:** **GPS-status idle screen** when casting is active but timing hasn't
  started / GPS isn't Ready: show fix state + accuracy and a clear "Waiting for GPS"
  → "Ready — start timing" message, using the **phone's existing Ready thresholds**
  (25 m accuracy, 15 s freshness, 0.9 Hz — see `ReadyGate`).
- **D-14:** **Neutral `--` pill** when no reference lap is selected: keep the delta
  pill in place showing `--` with no color/arrow so the layout geometry is identical
  with or without a reference. Timing still works fully; you just get no gap.
- **D-15:** **Stale fix mid-lap**: the current-lap clock **keeps advancing**
  (wall-clock, matching the phone). Speed and delta (which need live fixes) go to
  `--` / dim, plus a small **"GPS" warning glyph**, so a stale delta never looks
  live.

### Claude's Discretion
- Exact flexBox composition, `TextStyle`/`TextColor`/`IconName` selection, spacing,
  and font-size hierarchy within each page (subject to confirming the SDK's actual
  enums — D-03).
- Whether to explicitly dedupe identical 2 Hz frames (D-06).
- Precise geometry of the Drive-screen cast toggle / status chip and the Settings
  "Glasses" area, consistent with existing tokenized theme (`ui/Theme.kt`,
  `ui/Spacing.kt`).

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Meta Wearables DAT Android SDK (external clone — canonical implementation reference)
- `C:\Users\16079\Code\meta-wearables-dat-android` — the locally cloned Meta DAT
  Android SDK (external to the LapSight repo). Canonical per ROADMAP Phase 7 notes.
- `C:\Users\16079\Code\meta-wearables-dat-android\plugins\mwdat-android\skills\display-access\SKILL.md`
  — the `mwdat-display` API: dependency setup, DAM/manifest metadata, registration,
  device selection (`AutoDeviceSelector`/`SpecificDeviceSelector`, `isDisplayCapable()`),
  session lifecycle (`createSession` → `DeviceSessionState.STARTED` → `addDisplay()`
  → `DisplayState.STARTED`), `sendContent { flexBox { text/button/icon/image } }`
  (each call **replaces** content), error/update flows, and captouch note.
- `C:\Users\16079\Code\meta-wearables-dat-android\samples\DisplayAccess\README.md`
  — the DisplayAccess sample flow and architecture (WearablesViewModel /
  DisplayViewModel / AppScaffold) — the reference app pattern to mirror.
- `C:\Users\16079\Code\meta-wearables-dat-android\plugins\mwdat-android\skills\`
  — companion skills to read as needed: `getting-started`,
  `permissions-registration` (SDK init, app credentials, BLUETOOTH_CONNECT),
  `session-lifecycle`, and `mockdevice-testing` (`MockDeviceKit` / `MockCaptouchKit`
  for automated lifecycle, permission, disconnection, and captouch scenarios
  without hardware).

### LapSight timing state the bridge consumes (do NOT reimplement)
- `shared/src/commonMain/kotlin/com/huanfuli/lapsight/shared/lap/LapDashState.kt`
  — presentation-ready timing state: lap count, current/last/best lap millis +
  formatted labels, latest sector label/split, `sectorSummaries`, speed
  (`speedKmhLabel`), accuracy, fix status. This is the primary struct the HUD reads.
- `shared/src/commonMain/kotlin/com/huanfuli/lapsight/shared/session/SessionController.kt`
  — the timing source of truth (start/stop, timing state, recovery).
- `shared/src/commonMain/kotlin/com/huanfuli/lapsight/shared/session/ReadyGate.kt`
  — Ready thresholds (25 m / 15 s / 0.9 Hz) reused for the D-13 idle screen.
- `shared/src/commonMain/kotlin/com/huanfuli/lapsight/shared/ghost/` — live-delta /
  `DeltaDisplayState` models feeding the delta pill (D-03) and no-ref state (D-14).

### Project / roadmap anchors
- `.planning/ROADMAP.md` — Phase 7 goal, success criteria, and implementation notes
  (Android-first, consume phone-owned state, MockDeviceKit for automation).
- `.planning/REQUIREMENTS.md` §MR-01/MR-02/MR-03.
- `.planning/PROJECT.md` — "MR path" constraint (glasses consume summarized state,
  not a duplicated lap engine).

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- `LapDashState` (shared): already the exact summarized, formatting-complete timing
  view the phone dash renders. The glasses HUD should consume the same struct so
  Android/iOS/glasses render identical strings — no new lap math.
- `ReadyGate` (shared): reuse its thresholds for the glasses GPS-status idle screen
  (D-13).
- Live-delta / `DeltaDisplayState` (shared `ghost/`): the ahead/behind value + state
  driving the delta pill.
- Tokenized theme + spacing (`ui/Theme.kt`, `ui/Spacing.kt`, from Phase 5.1
  hardening): the phone-side cast toggle / Settings glasses area should flow through
  these semantic tokens (0 inline hex / 0 inline `.sp`).

### Established Patterns
- KMP shared domain + Compose Multiplatform UI, platform-native services behind a
  shared interface (cf. `LocationSampleProvider`). The DAT bridge is **Android-only
  glue** in `androidApp`, subscribing to shared timing state — it must NOT push
  Compose/platform types into the shared engine (clean-room boundary, verified in
  5.1 code review: engine imports zero Compose/platform).
- The phone stays authoritative; peripheral layers consume summarized state. The DAT
  bridge is a new consumer of the same seam, analogous to how the phone dash consumes
  `LapDashState`.

### Integration Points
- **Read side:** subscribe to the phone's live `LapDashState` + delta stream
  (whatever the Drive screen already collects) and map it to glasses `sendContent`
  screens.
- **Control side (D-08):** captouch tap-and-hold → route to `SessionController`
  start/stop; captouch tap → local page-cycle state.
- **UX surfaces:** new Settings "Glasses" area (registration/device pick) + Drive
  screen cast toggle/status chip.
- **Manifest/build:** `androidApp` needs the `mwdat-core` + `mwdat-display`
  dependencies, DAT manifest metadata (`APPLICATION_ID`, `CLIENT_TOKEN`,
  `DAM_ENABLED=true`), Meta developer credentials, and `BLUETOOTH_CONNECT` /
  `BLUETOOTH` / `INTERNET` permissions.

</code_context>

<specifics>
## Specific Ideas

- "Like the phone app, multiple selectable pages" — user explicitly wants the glasses
  to mirror the phone's multi-page navigation model (drove D-01).
- Delta as a **colored pill** (user's phrasing) with sign + arrow inside — a specific
  visual the downstream UI work should honor (D-03).
- Sector time should appear **only transiently in the lap-time position** after a
  sector crossing, not as always-on data (user's refinement → D-04).
- Captouch **tap** = page cycle; **tap-and-hold** = start/end session (user's mapping
  → D-07/D-08).

</specifics>

<deferred>
## Deferred Ideas

- **Confirm the DAT color/background palette** actually exposed by
  `FlexBoxBackground` / `TextColor` / `IconName` and adapt the delta-pill design
  (D-03) — a research task within this phase, not a separate phase.
- **Speed units on the glasses** should mirror the phone's `DisplaySettings`
  (km/h vs mph) — confirm during planning/UI work.
- **Fine-tune sector-flash duration / page scope** on real glasses (currently
  ~1.5 s, any page with a clock).
- **Explicit frame-dedupe** of identical 2 Hz frames — planner's call if BLE
  traffic / flicker warrants (D-06).
- iOS glasses support — out of scope for Phase 7 (Android-only per D-domain);
  revisit only after the Android bridge is validated on real hardware.

None of these expand the phase scope — they are refinements/confirmations inside
the Phase 7 boundary.

</deferred>

---

*Phase: 7-phone-to-glasses-dat-display-bridge*
*Context gathered: 2026-07-06*
