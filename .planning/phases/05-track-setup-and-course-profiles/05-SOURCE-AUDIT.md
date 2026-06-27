# Phase 05 Multi-Source Coverage Audit

| Source | ID | Feature / Constraint | Plan | Status | Notes |
|---|---|---|---|---|---|
| GOAL | — | Save and reuse start/finish and course configuration | 05-01..05-09 | COVERED | Migration, selection, editing, timing, compatibility, and preflight form the complete vertical flow. |
| REQ | — | No formal Phase 5 requirement IDs exist | — | EXCLUDED | Per ROADMAP and planning brief; no REQUIREMENTS.md entries invented. |
| GOAL | SC-01 | Save named Track/course profiles | 05-01, 05-02, 05-05 | COVERED | Stable profiles, explicit selection, and lifecycle. |
| GOAL | SC-02 | Edit start/finish and optional Sector lines | 05-03, 05-04 | COVERED | Offline editor plus complete-Sector timing semantics. |
| GOAL | SC-03 | Reuse prior course setup for new sessions | 05-02, 05-06, 05-08 | COVERED | Persisted profile/direction through normal Timing flow. |
| GOAL | SC-04 | Detect obvious wrong-direction or wrong-course use | 05-06, 05-08, 05-09 | COVERED | Directional boundaries, matcher confidence, and whole-course preflight. |
| CONTEXT | D-01 | Explicit current Track; never newest fallback | 05-01, 05-02, 05-05 | COVERED | Migration leaves null; selection persists; archive clears. |
| CONTEXT | D-02 | Drive selector and Track-detail set-current action | 05-02 | COVERED | End-to-end UI/storage slice. |
| CONTEXT | D-03 | Block Timing without timing-ready selection | 05-02, 05-05 | COVERED | Typed unavailable state and direct action. |
| CONTEXT | D-04 | Manual remembered selection; no nearby recommendation | 05-01, 05-02 | COVERED | No location-based selector logic. |
| CONTEXT | D-05 | Editable confirmed start/finish on offline trace | 05-03 | COVERED | Editor validates before revision save. |
| CONTEXT | D-06 | Sectors are complete intervals covering the lap | 05-04 | COVERED | Independent complete-Sector replay gate. |
| CONTEXT | D-07 | Disabled or 2–6 Sectors; enabled defaults to 3 | 05-03, 05-04 | COVERED | Editor and engine validation. |
| CONTEXT | D-08 | Generate N-1 equal arc-length boundaries | 05-03 | COVERED | ClosedReferencePath editor tests. |
| CONTEXT | D-09 | Perpendicular generated boundaries; no endpoint edit | 05-03 | COVERED | Tangent/normal derivation only. |
| CONTEXT | D-10 | Drag boundary only along reference line | 05-03 | COVERED | Progress-only constrained drag. |
| CONTEXT | D-11 | Direction order; duration separate from Split | 05-04, 05-06 | COVERED | SectorResult and Reverse transform. |
| CONTEXT | D-12 | Stable profile identity and immutable revisions | 05-01, 05-03, 05-05 | COVERED | V2 aggregate and append-only operations. |
| CONTEXT | D-13 | Geometry changes create revisions | 05-01, 05-03, 05-05 | COVERED | Validated appendRevision path. |
| CONTEXT | D-14 | Latest active selection; historical versions retained | 05-01, 05-03, 05-05 | COVERED | History/detail and session snapshot. |
| CONTEXT | D-15 | Sector-only compatibility; geometry changes reset history | 05-01, 05-05, 05-07 | COVERED | Explicit geometryCompatibilityId policy and full key. |
| CONTEXT | D-16 | Archive instead of delete; independent duplicate | 05-05, 05-07 | COVERED | Repository/Review flow and independent Ghost history. |
| CONTEXT | D-17 | No continuous wrong-direction state/pause/warning | 05-06, 05-08, 05-09 | COVERED | Directional boundaries and preflight-only check. |
| CONTEXT | D-18 | Recorded/reverse configurations share physical geometry | 05-06, 05-07 | COVERED | Direction-specific CourseDefinition and reference slots. |
| CONTEXT | D-19 | Remember direction; isolate fastest/Ghost history | 05-06, 05-07 | COVERED | Persisted direction and full compatibility key. |
| CONTEXT | D-20 | Reversing never pauses Timing/Sectors/Ghost/raw capture | 05-04, 05-06, 05-07, 05-08, 05-09 | COVERED | Replay and integration continuity. |
| CONTEXT | D-21 | Directional boundaries reject opposite crossings | 05-06 | COVERED | Explicit orientation from first crossing. |
| CONTEXT | D-22 | Whole-course conservative preflight | 05-09 | COVERED | Closed-path distance with uncertainty. |
| CONTEXT | D-23 | Explicit override persisted and visible in Review | 05-09 | COVERED | CoursePreflightSnapshot. |
| CONTEXT | D-24 | Active timing continues; delta suppresses/resumes | 05-08, 05-09 | COVERED | Independent Ghost matcher gate and end-to-end replay. |
| RESEARCH | R-01 | Freeze V1 DTOs and use explicit V1→V2 dispatch | 05-01 | COVERED | Literal fixtures and idempotent migration. |
| RESEARCH | R-02 | Persist exact selection and inject iOS file store | 05-02 | COVERED | Relaunch behavior and macOS gate. |
| RESEARCH | R-03 | Reuse one closed-path primitive for course geometry | 05-03, 05-07, 05-08 | COVERED | Editor, matcher, and preflight share it. |
| RESEARCH | R-04 | Replace complete-Sector semantics independently | 05-04 | COVERED | Separate green storage/replay gate. |
| RESEARCH | R-05 | Preserve lifecycle/history and compatibility identity | 05-05, 05-07 | COVERED | Archive/duplicate and full key. |
| RESEARCH | R-06 | Replace Ghost matcher independently | 05-08 | COVERED | Separate from migration, compatibility, and Sector plans. |
| RESEARCH | R-07 | Integrate conservative preflight and runtime UAT | 05-09 | COVERED | Android plus explicit macOS/iOS gate. |
| RESEARCH | R-08 | No new dependency or package install | 05-01..05-09 | COVERED | Pure Kotlin over existing stack. |
| RESEARCH | R-09 | Enforce local JSON/input/security boundaries | 05-01..05-09 | COVERED | Every plan has a concrete STRIDE register. |

## Excluded Deferred Scope

Nearby/community Track matching, cloud/shared Tracks, map tiles, external GNSS, telemetry charts, and Meta glasses output are explicitly deferred and do not appear in any plan.

## Result

All GOAL, success-criterion, RESEARCH, and D-01 through D-24 items are covered. No source item is missing.
