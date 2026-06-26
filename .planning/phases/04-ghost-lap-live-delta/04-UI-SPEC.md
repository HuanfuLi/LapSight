---
phase: 04
slug: ghost-lap-live-delta
status: approved
shadcn_initialized: false
preset: none
created: 2026-06-25
reviewed_at: 2026-06-25
---

# Phase 4 — UI Design Contract

> Visual and interaction contract for Ghost Lap + Live Delta.
> Platform: Kotlin Multiplatform + Compose Multiplatform. shadcn does not apply.

---

## Design System

| Property | Value |
|----------|-------|
| Tool | Compose Multiplatform + Material3, continuing the existing mounted-phone dash |
| Component library | Existing Material3 `Card`, `Button`, `AlertDialog`; add only small purpose-built `DeltaReadout` / `TimingMetric` composables if needed |
| Icon library | No new icons required in Phase 4 |
| Font | System Material3 font; timing and delta numerals use tabular figures via `fontFeatureSettings = "tnum"` |

Phase 4 must not introduce a dense telemetry screen, map ghost animation, or a new navigation destination. The live delta appears inside the existing Drive/Timing surface.

---

## Spacing Scale

Use the existing 4dp rhythm:

| Token | Value | Usage |
|-------|-------|-------|
| xs | 4dp | Inline gaps |
| sm | 8dp | Compact metric spacing |
| md | 12dp | Compact card padding |
| lg | 16dp | Default card/page padding |
| xl | 24dp | Major section gap |
| 2xl | 32dp | Large timing separation |

No new small controls are added while timing is active. Existing Stop/orientation controls keep at least 48dp touch targets.

---

## Typography

| Role | Portrait | Compact landscape | Usage |
|------|----------|-------------------|-------|
| Primary display | 52sp Black | 40sp Black | Current lap time remains the largest timing value |
| Delta display | 40–44sp Black | 32–40sp Black | Live delta, second-most prominent readout |
| Secondary metric | 24–28sp Bold/Black | 20–24sp Bold/Black | Last/best/laps/speed |
| Label/body | 11–16sp | 10–14sp | Non-driving helper text and captions |

Delta must never visually dominate current lap time.

---

## Color

| Role | Value | Usage |
|------|-------|-------|
| Background | `#05070A` | Timing surface |
| Surface | `#101722` | Metric cards |
| Accent cyan | `#62E3FF` | Current lap / primary timing accent |
| Faster | `#8CFF9B` | Negative delta, current lap ahead |
| Slower | `#FF9F43` or `#FF6B6B` | Positive delta, current lap behind |
| Neutral | `#9AA8B8` | Unavailable delta `--` |
| Demo amber | `#FFD166` | Existing DEMO simulated GPS badge |

Color is semantic only for the delta sign/state; do not add animation, flashing, or attention-grabbing effects.

---

## Copywriting Contract

| Surface / State | Copy |
|-----------------|------|
| Faster delta | `-0.218s` |
| Slower delta | `+0.421s` |
| No reference / unavailable | `--` |
| New reference source | No new driving-surface copy; the reference updates silently |
| Demo source | Existing `DEMO — simulated GPS` badge remains |

Live moving UI rule: delta is sign plus seconds only. Do not show words like `ahead`, `behind`, `faster`, `slower`, `ghost`, or explanatory text beside the delta value while timing is active.

Non-driving review/debug text may explain why delta is unavailable, but Phase 4 does not require a new explanatory screen.

---

## Interaction Contract

- Default reference selection is automatic: current saved Track's fastest valid lap.
- A newly completed fastest lap inside the active session becomes the reference for following laps immediately.
- Users do not manually choose a ghost in Phase 4.
- No dedicated "ghost test" button is added. The existing provider-level simulated GPS feed starts independently; normal Start Timing / Stop / Save flow consumes provider samples.
- Timing must still work when no reference exists; delta shows `--`.

---

## Timing Surface Layout

Priority order while timing is active:

1. Current lap time — primary display.
2. Delta value — second core readout, value-only.
3. Last lap / best lap / lap count / speed / accuracy — compact secondary metrics.
4. Stop/orientation controls — existing safe controls.
5. DEMO badge — visible whenever simulated source is active.

Landscape should favor a two-column or compact grid: current lap on the left/top, delta directly adjacent or below, secondary metrics compressed. Portrait can stack current lap then delta then secondary metrics.

---

## Delta States

| State | Value | Color | Required behavior |
|-------|-------|-------|-------------------|
| Faster than reference | `-0.218s` | Green `#8CFF9B` | Negative sign retained |
| Slower than reference | `+0.421s` | Orange/red | Positive sign retained |
| No reference | `--` | Neutral muted | Do not reuse stale delta |
| Poor confidence | `--` | Neutral muted | Do not imply precision |
| Awaiting lap start | `--` | Neutral muted | Other timing metrics continue |

---

## Safety Constraints

- Closed-course/private-track safety language remains visible on non-active Drive surfaces.
- Active timing UI stays passive and glanceable.
- No public-road racing positioning, leaderboards, or competitive road language.
- No map tiles, charts, telemetry panels, or dense trace comparison in Phase 4.

---

## Checker Sign-Off

- [x] Dimension 1 Copywriting: PASS
- [x] Dimension 2 Visuals: PASS
- [x] Dimension 3 Color: PASS
- [x] Dimension 4 Typography: PASS
- [x] Dimension 5 Spacing: PASS
- [x] Dimension 6 Registry Safety: PASS

**Approval:** approved for Phase 4 planning.
