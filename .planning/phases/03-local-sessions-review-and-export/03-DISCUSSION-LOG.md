# Phase 3: Local Sessions, Review, and Export - Discussion Log

> **Audit trail only.** Do not use as input to planning, research, or execution agents.
> Decisions are captured in CONTEXT.md — this log preserves the alternatives considered.

**Date:** 2026-06-25T12:40:04-04:00
**Phase:** 3-Local Sessions, Review, and Export
**Areas discussed:** Session save semantics, Deterministic GPS mock and fixture system, Track capture and reference line semantics, Session and track data boundary, Review information hierarchy, Track trace rendering, App navigation and review entry points, Export strategy, Demo and replay product behavior

---

## Session Save Semantics

| Decision Point | Options Considered | Selected / User Direction |
|---|---|---|
| When active session data is persisted | Auto-draft + Save/Discard after Stop; only save after explicit Save; auto-save formal session on Stop | Auto-draft while active, then explicit Save or Discard after Stop |
| Draft recovery | Prompt to recover unfinished session; keep draft in Session List; discard short drafts automatically | Prompt on launch to Resume, Save, or Discard |
| Stop behavior | End summary with Save/Discard; return to list; stay on dash with draft banner | Stop opens a simple end summary/sheet with Save and Discard |

**Notes:** The user accepted explicit draft recovery and Stop summary. Phase 3 should keep the session state explicit and avoid silent formal saves.

---

## Deterministic GPS Mock and Fixture System

| Decision Point | Options Considered | Selected / User Direction |
|---|---|---|
| Need for mock before UAT | Build deterministic GPS mock first; ad-hoc test data; wait for real GPS | Build deterministic GPS mock/fixture system first |
| Mock data shape | Real geographic lat/lon time series; abstract x/y; minimal synthetic points | Real lat/lon time series using `LocationSample` |
| Generation structure | Generator plus committed fixture files; static JSON only; hardcoded generator only | Both deterministic generators and committed fixtures |
| Scenario coverage | Full UAT fixture set; clean laps only; real-world recordings only | Clean 10 loops, minimum 5 loops, bad/outlier loop, noise/drift, dropped samples, multi-session best-lap scenario |

**Notes:** The user emphasized that real UAT cannot require going to a track every time. Mock data must form closed-loop GPS trajectories with time sequence and realistic coordinates.

---

## Track Capture and Reference Line Semantics

| Decision Point | Options Considered | Selected / User Direction |
|---|---|---|
| One lap vs multiple loops | Multiple-lap capture; single-lap capture; manual drawing | Multiple loops because GPS captures the user's line, not exact track geometry |
| Handling mistakes/outliers | User excludes bad traces; average blindly; auto-detect only | Final direction: algorithmic outlier filtering from the full trace, with inspectable review |
| Reference line vs ghost | Separate reference line and ghost candidate; fastest lap as track geometry; averaged line as ghost | Keep TrackReferenceLine separate from fastest-lap GhostCandidate |
| Start/finish before capture | Record first then derive reference line; require start/finish first; support both | Motorsport default is record first; cycling can optionally pre-seed start/finish |
| Out/in lap semantics | Algorithmic extraction; hard-coded rules; manual classification | Do not hard-code out-lap/in-lap by time/distance/half-lap |
| Lap splitting during marking | No lap splitting; infer temporary lap cuts; require start/finish first | No lap splitting during Track Marking |

**Notes:** A correction was made during discussion: before start/finish is set, the system cannot legitimately split laps. Track marking is one continuous multi-loop trace. The algorithm extracts a reference line from repeated spatial structure; lap timing begins only after a Track has confirmed start/finish.

---

## Session and Track Data Boundary

| Decision Point | Options Considered | Selected / User Direction |
|---|---|---|
| Entity boundaries | Separate TrackMarkingSession/TrackReferenceLine/Track/TimingSession/Lap/GhostCandidate; one Session model; defer Track | Separate the entities |
| Ghost relationship | Per-track fastest valid lap; independent ghost files; no ghost data yet | Per-track derived fastest valid lap candidate |
| Storage structure | Metadata index + canonical JSON payloads; scan payloads for lists; full database-first | Metadata index plus canonical JSON payload files |
| Schema metadata | Version from v1; informal now; full migration framework first | Version schema from v1 with stable IDs, timestamps, source, app/build metadata |

**Notes:** The user accepted separating Track setup data from formal TimingSession data. Ghost/delta UI remains out of scope, but the data relationship must be ready.

---

## Review Information Hierarchy

| Decision Point | Options Considered | Selected / User Direction |
|---|---|---|
| Review flow structure | Separate Track Review and Timing Session Review; one generic screen; only Timing Review | Separate flows |
| Track Review priority | Reference line/setup; lap timing; raw GPS table | Reference line preview, quality, start/finish, sectors, Save/Re-record/Discard |
| Timing Review priority | Lap/session performance; map first; export/debug first | Track/date/duration/best lap/lap list/sectors/GPS quality/trace/export/track-best status |

**Notes:** Track Review must not show lap times because Track Marking has no laps. Timing Session Review is for formal saved timing runs.

---

## Track Trace Rendering

| Decision Point | Options Considered | Selected / User Direction |
|---|---|---|
| Rendering base | Offline vector trace; Google/Apple map tiles; no trace | Offline vector trace first |
| Canonical coordinates | Persist lat/lon; convert to local x/y only; keep only display coordinates | Persist canonical lat/lon and project to screen |
| Track Review trace | Reference-line setup view; timing-focused view; raw point cloud | Full marking trace plus highlighted reference line, start/finish, sectors, outlier/noisy indication |
| Timing Review trace | Reference plus session trace; speed heatmap; map-matched trace | Reference line plus session trace; defer heatmaps/map matching |

**Notes:** The user specifically noted that even without map tiles, latitude/longitude must be preserved for future nearby-track matching and shared track recognition.

---

## App Navigation and Review Entry Points

| Decision Point | Options Considered | Selected / User Direction |
|---|---|---|
| Review entry | Persistent Review tab; only after completion; hidden debug/history | Persistent Review tab with history list |
| Top-level navigation | Three tabs: Drive/Timing, Review, Settings; single screen with modal history; separate track/session nav | Three-tab bottom navigation |
| Mounted-phone mode | Fullscreen Drive/Timing; always show full chrome; Review-first layout | Fullscreen mode that hides nonessential UI while moving |

**Notes:** The user raised that Review cannot only be reachable at session completion. The bottom nav should include Drive/Timing, Review, and Settings. Drive/Timing needs a fullscreen mode for mounted-phone use.

---

## Export Strategy

| Decision Point | Options Considered | Selected / User Direction |
|---|---|---|
| Primary export | Complete canonical/debug JSON; summary JSON; GPX only | Complete JSON |
| GPX role | Compatibility track points; full custom extensions; no GPX | GPX for external compatibility, JSON for full fidelity |
| Export entry | Review detail screens; batch list export; hidden debug export | Export from Track Review and Timing Session Review details |
| Filenames | Readable LapSight-prefixed names; opaque IDs; user-named every time | Readable names with entity type, track name, date/time, extension |

**Notes:** The user accepted JSON as canonical/debug export and GPX as compatible GPS export.

---

## Demo and Replay Product Behavior

| Decision Point | Options Considered | Selected / User Direction |
|---|---|---|
| Demo exposure | Background simulated GPS feed behind normal flows; separate demo workflow; hidden fixture runner | Background simulated GPS feed from Drive screen |
| Business logic validation | Mock only GPS provider; mock whole timing workflow; manual shortcuts | Mock only the GPS provider layer |
| Demo labeling | Visible Demo/source metadata; indistinguishable from real; prevent saving | Visible Demo labels and source metadata everywhere |
| Fixture control | Simple demo toggle; full fixture picker; no user-visible demo | Simple product demo control, detailed fixtures reserved for tests |

**Notes:** The user explicitly rejected separate demo-only start/end business buttons because they would not prove the real app workflow. The simulator should pretend the phone is physically moving on track, while users operate the normal Track Marking, Start, Stop, Save, Review, and Export flows.

---

## the agent's Discretion

- Exact reference-line extraction algorithm and thresholds, as long as it derives from the continuous trace and preserves raw samples.
- Exact local storage backend/file layout, as long as it is local-first, versioned, index-backed for lists, and export-compatible.
- Exact UI component structure and styling, as long as the three-tab shell, Review entry, and fullscreen mounted-phone mode are preserved.

## Deferred Ideas

- Real Android/iOS GPS providers.
- Realtime ghost delta UI.
- External GNSS support.
- Meta glasses HUD bridge.
- Map-tile trace rendering.
- Nearby-track auto matching from latitude/longitude.
- Batch export.
- Speed heatmaps, 3D/elevation analysis, and map matching.
- Cloud accounts, sync, shared tracks, login, and social features.
