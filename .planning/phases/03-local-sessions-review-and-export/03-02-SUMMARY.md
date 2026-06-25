---
phase: 03-local-sessions-review-and-export
plan: 02
subsystem: build-dependencies
type: checkpoint
tags: [supply-chain, dependency-gate, human-verification]

# Dependency graph
requires:
  - phase: 03-local-sessions-review-and-export
    provides: Phase 3 vertical slice (03-01) and the new-artifact requirements from research/UI-spec
provides:
  - Human-approved Maven/Gradle coordinate set for all Phase 3 dependency additions
affects: [03-03 storage foundation (Gradle edits), 03-08 export, 03-05/03-06/03-07 UI icons]

key-files:
  created: []
  modified: []

key-decisions:
  - "All four flagged coordinates verified against live registries before approval"
  - "material-icons-core corrected from non-existent 1.11.1 to last-published 1.7.3"

requirements-completed: []

# Metrics
completed: 2026-06-25
---

# Phase 3 Plan 02: Dependency Verification Gate — Summary

**Blocking human supply-chain checkpoint. No source files modified. Verified the four
new Maven/Gradle coordinates required by later Phase 3 plans against live registries,
corrected one invalid coordinate, and recorded the user-approved set that downstream
plans (starting 03-03) may add — and nothing else.**

## Gate Outcome

Precondition confirmed before verification: `gradle/libs.versions.toml`,
`build.gradle.kts`, and `shared/build.gradle.kts` contained none of the new artifacts
(automated check PASS — no silent install).

### Verification method

Each coordinate was checked against its authoritative registry's `maven-metadata.xml`:
- Gradle Plugin Portal (`plugins.gradle.org/m2/...`) for the serialization plugin marker.
- Maven Central (`repo1.maven.org/maven2/...`) for the three libraries.

slopcheck had marked coordinates #2 and #3 `SUS`; both proved to be the **current latest
releases** (false alarms). The audit missed the real defect: coordinate #4 did not exist.

### Approved coordinate set (FINAL)

| # | Coordinate | Approved version | Registry status | Note |
|---|-----------|------------------|-----------------|------|
| 1 | `org.jetbrains.kotlin.plugin.serialization:org.jetbrains.kotlin.plugin.serialization.gradle.plugin` | `2.4.0` | Exists (Plugin Portal; latest `2.4.20-Beta1`) | Matches project `kotlin = 2.4.0` |
| 2 | `org.jetbrains.kotlinx:kotlinx-serialization-json` | `1.11.0` | Latest release on Maven Central | Approved as-is |
| 3 | `com.squareup.okio:okio` | `3.17.0` | Latest release on Maven Central | Approved as-is |
| 4 | `org.jetbrains.compose.material:material-icons-core` | `1.7.3` | **Corrected** — requested `1.11.1` does not exist; artifact frozen at `1.7.3` | Decoupled from CMP runtime; compatible with `composeMultiplatform = 1.11.1`; satisfies UI-SPEC `NavigationBarItem` icons |

### User decisions
- Chose to verify all coordinates against registries before approving.
- Approved #1–#3 exactly as flagged.
- Approved the `material-icons-core` correction to **`1.7.3`** (the last published version)
  rather than dropping icons.

## Downstream contract

Plan 03-03 (and any later plan touching Gradle files) may add **only** the four approved
coordinates above. `material-icons-core` MUST be pinned to `1.7.3`, not `1.11.1`. Any
`[SLOP]`-classified package remains forbidden. Mitigates threats T-03-05 and T-03-SC.

## Self-Check: PASSED

No Gradle files were modified during this checkpoint. Final approved coordinates recorded.
