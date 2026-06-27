# Phase 5: Track Setup and Course Profiles - Discussion Log

> **Audit trail only.** Do not use as input to planning, research, or execution agents.
> Decisions are captured in CONTEXT.md — this log preserves the alternatives considered.

**Date:** 2026-06-26
**Phase:** 05-Track Setup and Course Profiles
**Areas discussed:** Track selection and reuse, start/finish and Sector editor, Track changes and historical data, wrong-course and Course Direction handling

---

## Track Selection and Reuse

| Option | Description | Selected |
|--------|-------------|----------|
| Persist explicit current Track | Remember the user's current Track and show it on Drive | ✓ |
| Select before every Session | Require repeated selection for every TimingSession | |
| Automatically choose newest | Continue the current newest-timing-ready fallback | |

**User's choice:** Persist an explicit current Track; provide selection on Drive and `Set as current track` in Review.
**Notes:** Timing is blocked with a Track-selection action when no timing-ready Track is selected. Nearby recommendation is useful later but must not silently switch Tracks.

---

## Start/Finish and Sector Editor

| Option | Description | Selected |
|--------|-------------|----------|
| Complete course intervals | `N` Sectors use start/finish plus `N-1` boundaries and cover the entire lap | ✓ |
| Independent Sector lines | Treat each intermediate line itself as a Sector | |
| Free-form boundary drawing | User draws and reshapes every intermediate timing line | |
| Generated constrained boundaries | Generate perpendicular boundaries and allow position-only dragging along the reference line | ✓ |

**User's choice:** Sector means a complete course interval. Sector timing is off or 2–6 Sectors, default 3; the system generates `N-1` perpendicular boundaries at equal reference-line arc-length positions.
**Notes:** Users drag only the boundary's position along the reference line. The system recomputes the local normal; users do not move endpoints. Sector order follows Course Direction. Research covered FIA timing sectors, AiM/RaceStudio, RaceChrono, and Gran Turismo.

---

## Track Changes and Historical Data

| Option | Description | Selected |
|--------|-------------|----------|
| Immutable geometry revisions | Geometry changes create a revision under a stable logical profile | ✓ |
| Overwrite in place | Mutate the Track used by historical Sessions and Ghosts | |
| Unrelated Track per edit | Every edit creates a separate profile with no version relationship | |
| Archive | Hide from active selection but preserve history | ✓ |
| Hard delete | Remove Track, revisions, Sessions, and Ghosts | |

**User's choice:** Metadata can update in place; geometry and Sector changes create revisions. Old revisions remain in version history, deletion means archive, and duplication creates an independent profile.
**Notes:** Sector-only revisions retain Ghost compatibility. Reference-line or start/finish changes do not. The later Course Direction discussion clarified that direction is a selectable variant under a revision, with direction-specific fastest laps and Ghosts.

---

## Wrong-Course and Course Direction Handling

| Option | Description | Selected |
|--------|-------------|----------|
| Broad preflight against full reference line | Block only when clearly far from the selected course | ✓ |
| Require start/finish proximity | Require the user to begin near start/finish | |
| Hard block on mismatch | Never allow Timing after a mismatch | |
| Explicit override | Allow `Still use this track` and record the override | ✓ |
| Continuous wrong-direction state | Detect reverse progress and pause timing/delta | |
| Selectable Course Direction | Support recorded/reverse variants with directional timing lines | ✓ |
| Bidirectional line triggers | Let either crossing direction complete laps/Sectors | |

**User's choice:** Use a broad wrong-course preflight with an explicit recorded override. Do not pause or warn during reverse movement; support recorded and reverse Course Directions instead.
**Notes:** Stops, reversing, and turnarounds remain part of the current lap and never pause its clock. Opposite-direction line crossings do not complete timing events. During unreliable course matching, Timing and GPS continue while Ghost delta temporarily shows `--`.

---

## the agent's Discretion

- Conservative wrong-course and progress-confidence thresholds.
- Reference-line tangent smoothing, generated boundary length, snapping, and minimum boundary spacing.
- Exact storage migration and profile/revision UI wording.
- Exact start/finish manipulation and validation affordance within the offline trace editor.

## Deferred Ideas

- Recommend nearby Tracks from live latitude/longitude with explicit confirmation.
- Match against Tracks saved by other users.
- Cloud/shared Track libraries and other post-MVP capabilities.
