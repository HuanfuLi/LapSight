# Phase 04: Ghost Lap + Live Delta - Discussion Log

> **Audit trail only.** Do not use as input to planning, research, or execution agents.
> Decisions are captured in CONTEXT.md — this log preserves the alternatives considered.

**Date:** 2026-06-25T18:05:42-04:00
**Phase:** 04-Ghost Lap + Live Delta
**Areas discussed:** Reference lap selection and update timing, Reference lap data shape, Delta calculation semantics, Live dash presentation, No-reference and low-confidence states, Demo and replay UAT behavior

---

## Reference Lap Selection and Update Timing

| Option | Description | Selected |
|--------|-------------|----------|
| Auto-use fastest valid lap for current Track | Default reference is the current Track's best valid lap. | ✓ |
| Require explicit user-selected reference | User must choose the reference before each session. | |
| Use last saved reference only | Reference changes only after explicit save/session boundary. | |

**User's choice:** Automatically use the fastest valid lap for the current Track.
**Notes:** User clarified that if a new fastest lap happens during the same active session, it must immediately become the reference for following laps. Drivers should not need to stop/save/restart after setting a purple/best lap.

---

## Reference Lap Data Shape

| Option | Description | Selected |
|--------|-------------|----------|
| Raw samples + precomputed progress curve | Preserve replay/debug/export while enabling efficient realtime delta. | ✓ |
| Raw samples only | Simpler storage but inefficient for live delta. | |
| Progress curve only | Efficient but loses evidence for replay/debug/future algorithms. | |

**User's choice:** Store both raw samples and a distance/progress-indexed progress curve.
**Notes:** User asked whether the progress curve can later support pro racing/game-style telemetry charts and data. Answer: yes. Those capabilities were added to deferred ideas/backlog after MVP.

---

## Delta Calculation Semantics

| Option | Description | Selected |
|--------|-------------|----------|
| Distance/progress-matched delta | Compare current elapsed time against reference elapsed time at equivalent progress. | ✓ |
| Nearest coordinate delta | Match by spatial nearest point; deferred as a possible later refinement. | |
| Sector-only delta | Too coarse for live ghost delta. | |

**User's choice:** Calculate live delta by current progress against reference progress.
**Notes:** Positive delta means slower and displays as `+time`; negative delta means faster and displays as `-time`. Suppress delta when there is no trustworthy match.

---

## Live Dash Presentation

| Option | Description | Selected |
|--------|-------------|----------|
| Second core readout after Current Lap | Visible in normal mode and strong in fullscreen, but not larger than current lap timer. | ✓ |
| Small secondary status only | Too easy to miss while driving. | |
| Large primary metric replacing Current Lap | Would compete with the primary lap timer. | |

**User's choice:** Delta is a compact second core readout.
**Notes:** User explicitly corrected the UI copy: do not use many words. Use only sign plus seconds, e.g. `-0.218s` or `+0.421s`. Keep the UI extremely compact because the page needs to show many live metrics.

---

## No-Reference and Low-Confidence States

| Option | Description | Selected |
|--------|-------------|----------|
| Minimal `--` placeholder | Keeps driving UI compact and avoids stale/untrusted numbers. | ✓ |
| Detailed status text | Too much text for the mounted-phone driving surface. | |
| Keep last known delta visible | Risky because stale delta could look live. | |

**User's choice:** Show `--` whenever live delta is unavailable or untrustworthy.
**Notes:** Current/Last/Best, speed, and GPS quality can remain visible. Only live delta is suppressed.

---

## Demo and Replay UAT Behavior

| Option | Description | Selected |
|--------|-------------|----------|
| Continuous provider feed consumed by normal sessions | Simulated GPS starts first, runs independently, and normal TimingSessions consume it. | ✓ |
| Generate a fixed replay only after Start Session | Would not validate the real business path. | |
| Bypass session flow with a special ghost test button | Would test a demo path instead of the product path. | |

**User's choice:** Reuse Phase 3's continuous provider-layer simulated GPS model.
**Notes:** The developer starts simulated GPS first. It continues to play as if the phone is physically moving around the track, whether or not a TimingSession is active. When the session starts, it reads samples from that live provider from that moment forward. UAT needs variable lap pace to validate `+Xs`, `-Xs`, and in-session new best reference updates.

---

## the agent's Discretion

- Exact class names, DTO boundaries, interpolation implementation, and storage wiring are left to planner/executor discretion.
- Constraints are fixed: clean-room shared lap engine, no UI/platform coupling in algorithmic logic, deterministic replay/synthetic testability, and Demo/Simulated isolation.

## Deferred Ideas

- Map ghost animation.
- Cloud/shared ghost laps.
- External GNSS precision tuning.
- Meta glasses HUD delta rendering.
- Post-MVP professional telemetry charts over progress distance.
- Delta-over-distance chart.
- Speed curve comparison by progress.
- Sector speed comparison.
- Corner entry/exit speed analysis.
- Multi-lap telemetry overlay.
- Future IMU/throttle/brake/steering channels aligned to progress curve.
