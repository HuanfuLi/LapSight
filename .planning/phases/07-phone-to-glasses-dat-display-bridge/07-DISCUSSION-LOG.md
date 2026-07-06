# Phase 7: Phone-to-Glasses DAT Display Bridge - Discussion Log

> **Audit trail only.** Do not use as input to planning, research, or execution agents.
> Decisions are captured in CONTEXT.md — this log preserves the alternatives considered.

**Date:** 2026-07-06
**Phase:** 7-phone-to-glasses-dat-display-bridge
**Areas discussed:** HUD layout & delta encoding, Update cadence / refresh model, Connection & activation UX, Non-timing HUD states

---

## HUD layout & delta encoding

### HUD hero (active timing)
| Option | Description | Selected |
|--------|-------------|----------|
| Delta (ahead/behind) | Delta pill is the hero; lap times small below | |
| Current lap time | Running clock is the hero | |
| Delta + current lap co-equal | Two large readouts share top billing, small footer | ✓ |

**User's choice:** Delta + current lap co-equal.

### Delta encoding (re-answered once)
| Option | Description | Selected |
|--------|-------------|----------|
| Sign + arrow icon | Leading sign + built-in arrow icon; robust on monochrome | ✓ (refined) |
| Sign + color only | Colored value, no icon | |
| Word label + sign | "AHEAD/BEHIND" text | |

**User's choice:** Sign + arrow icon **+ colored pill** — put the delta in a pill whose background color reinforces ahead/behind (redundant encoding).
**Notes:** User re-answered this question deliberately. Flagged that DAT `FlexBoxBackground`/`TextColor` are enums — pill background color availability is a research confirmation, intent stands regardless.

### Page set
| Option | Description | Selected |
|--------|-------------|----------|
| Two pages | Focused + Telemetry | |
| Three pages | Delta-only + Focused + Telemetry | ✓ |
| One rich page for v1 | Single telemetry page, multi-page as fast-follow | |

**User's choice:** Three pages.
**Notes:** User wants the glasses to mirror the phone app's multi-page navigation feel.

### Page switching (passive constraint)
| Option | Description | Selected |
|--------|-------------|----------|
| Phone-side + captouch tap | Phone selector + temple tap cycles pages | ✓ |
| Phone-side only | Page chosen on phone; glasses take no input | |
| Auto by context + phone override | Bridge auto-picks page by lap state | |

**User's choice:** Phone-side + captouch tap; **tap-and-hold reserved for start/end session**.
**Notes:** User asked whether DAT supports neural-band input — confirmed NO (third-party capabilities are display + camera; captouch tap/tapAndHold is the only glasses-side gesture, testable via MockCaptouchKit). Phone remains timing source of truth; glasses emit input events only.

### Sectors
| Option | Description | Selected |
|--------|-------------|----------|
| Latest sector only (always-on) | Persistent latest-sector line | |
| All sectors row | Row of every sector split | |
| No sectors | Sectors phone-only | |
| Transient flash ~1.5 s | Sector split briefly takes over the hero clock slot then reverts | ✓ |

**User's choice:** Transient sector flash, ~1.5 s (chosen after being shown ASCII mockups of the three pages).
**Notes:** User proposed the transient idea themselves ("display sector time in the laptime position for a few seconds after passing the sector line; always-on seems unnecessary") and asked for ASCII UI illustrations before deciding. First flash-duration answer (~2.5 s) was later superseded by ~1.5 s.

---

## Update cadence / refresh model

### Push rate
| Option | Description | Selected |
|--------|-------------|----------|
| ~2 Hz, tenths | Twice/sec, tenths precision; matches GPS fix rate | ✓ |
| ~1 Hz, tenths | Lightest, but visibly jumpy | |
| ~4 Hz, tenths | Smoother, doubles BLE traffic for precision GPS can't justify | |

**User's choice:** ~2 Hz, tenths.

### Event immediacy
| Option | Description | Selected |
|--------|-------------|----------|
| Immediate on events | Events push instantly + dedupe identical frames | |
| Next beat only | Single uniform 2 Hz loop; no special-casing | ✓ |
| Immediate, no dedupe | Events instant, tick always sends | |

**User's choice:** Next beat only.
**Notes:** Single uniform render-and-push loop samples all state (incl. sector-flash window); lap-complete/flash land within ~0.5 s. Frame-dedupe left to planner.

---

## Connection & activation UX

### Where setup vs casting lives
| Option | Description | Selected |
|--------|-------------|----------|
| Settings setup + Drive cast toggle | Rare setup in Settings; per-session toggle on Drive | ✓ |
| Everything on Drive | All inline on Drive | |
| Settings only, auto-cast | HUD auto-activates when connected + timing starts | |

**User's choice:** Settings setup + Drive cast toggle.

### Mid-session disconnect
| Option | Description | Selected |
|--------|-------------|----------|
| Silent auto-reconnect | Timing unaffected; background retry; small status chip | ✓ |
| Reconnect + notify | Same + toast on drop/recover | |
| Drop and require re-cast | Manual re-cast needed | |

**User's choice:** Silent auto-reconnect.

---

## Non-timing HUD states

### Idle / GPS-not-ready
| Option | Description | Selected |
|--------|-------------|----------|
| GPS-status idle screen | Fix + accuracy + Waiting→Ready message | ✓ |
| Minimal Ready/Wait badge | Single word only | |
| Idle logo / standby | LapSight standby screen | |

**User's choice:** GPS-status idle screen (uses phone Ready thresholds 25 m / 15 s / 0.9 Hz).

### No reference lap selected
| Option | Description | Selected |
|--------|-------------|----------|
| Drop pill, promote current lap | Hide pill; current lap sole hero | |
| Neutral '--' pill | Pill stays showing '--', no color/arrow | ✓ |
| Auto-hide Delta-only page | Pill drop + remove Delta-only from cycle | |

**User's choice:** Neutral '--' pill (consistent geometry with/without a reference).

### Stale GPS fix mid-lap
| Option | Description | Selected |
|--------|-------------|----------|
| Tick on, flag stale values | Clock advances; speed/delta go '--'/dim + GPS glyph | ✓ |
| Freeze until fix returns | Hold HUD + 'GPS LOST' banner | |
| No special handling | Keep last values, no cue | |

**User's choice:** Tick on, flag stale values.

---

## Claude's Discretion

- Exact flexBox composition, TextStyle/TextColor/IconName selection, spacing, and font-size hierarchy per page (pending SDK enum confirmation).
- Whether to explicitly dedupe identical 2 Hz frames.
- Precise geometry of the Drive cast toggle / status chip and the Settings "Glasses" area, within the existing tokenized theme.

## Deferred Ideas

- Confirm DAT color/background palette (`FlexBoxBackground`/`TextColor`/`IconName`) and adapt the delta pill (research task in-phase).
- Glasses speed units mirror phone `DisplaySettings` (km/h vs mph) — confirm during planning.
- Fine-tune sector-flash duration / page scope on real glasses.
- Explicit frame-dedupe — planner's call if traffic/flicker warrants.
- iOS glasses support — out of scope for Phase 7; revisit after Android bridge validated on hardware.
