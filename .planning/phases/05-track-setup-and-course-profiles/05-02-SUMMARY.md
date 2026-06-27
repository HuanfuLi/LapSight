---
phase: 05-track-setup-and-course-profiles
plan: 2
subsystem: storage
tags: [kotlin-multiplatform, okio, schema-migration, course-profiles, side-by-side-migration, idempotence]

# Dependency graph
requires:
  - phase: 05-track-setup-and-course-profiles
    plan: 1
    provides: SchemaMigrations version dispatch + pure V1→V2 mapping, V2 profile/session/reference contracts, literal V1 fixture
provides:
  - LocalSessionStore.migrate / saveProfile / loadProfile / listActiveProfiles contract
  - MigrationResult / MigrationSkip result types
  - FileSessionStore side-by-side V1→V2 migration (payload-first/index-last, retained originals, fault-recoverable, idempotent)
  - InMemorySessionStore mirror of the same observable migration
  - ProfileIndex commit marker (profiles-index.json)
affects: [05-03-current-selection, 05-07-complete-sector-timing, 05-10-ghost-compatibility, 05-13-wrong-course-preflight]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "Side-by-side migration: V2 payloads written to new dirs, V1 originals never mutated"
    - "Payload-first / index-last single commit point; profile index gates listActiveProfiles for fault recovery"
    - "Cross-store equivalence via shared SchemaMigrations dispatch (in-memory encodes held V1 object then decodes through the same path)"

key-files:
  created:
    - shared/src/commonTest/kotlin/com/huanfuli/lapsight/shared/storage/StoreMigrationTest.kt
  modified:
    - shared/src/commonMain/kotlin/com/huanfuli/lapsight/shared/storage/LocalSessionStore.kt
    - shared/src/commonMain/kotlin/com/huanfuli/lapsight/shared/storage/FileSessionStore.kt
    - shared/src/commonMain/kotlin/com/huanfuli/lapsight/shared/storage/InMemorySessionStore.kt

key-decisions:
  - "V2 written to distinct dirs (profiles/, sessions-v2/, references-v2/) so migration is strictly additive and every V1 original survives"
  - "profiles-index.json is the single migration commit point written last; listActiveProfiles reads it, so an interrupted (index-faulted) migration exposes no half-migrated profile and is recoverable on retry"
  - "Unsafe ids are rejected by SchemaMigrations decode BEFORE any Okio path is built; corrupt/unsafe sources are skipped and reported in MigrationResult.skipped, never failing the whole run"
  - "In-memory store mirrors the file store by encoding its held V1 object and decoding through the same SchemaMigrations dispatch, guaranteeing identical observable results"
  - "Migration never writes a current selection (D-01/D-04); the selection storage arrives in Plan 05-03"

patterns-established:
  - "Pattern: additive versioned migration — new V2 layout beside frozen V1 files, deterministic identities make re-runs idempotent and lossless"

requirements-completed: [SC-01, SC-03]

# Metrics
duration: ~30min
completed: 2026-06-27
---

# Phase 5 Plan 02: Side-by-Side V1→V2 Store Migration Summary

**Idempotent, fault-recoverable side-by-side V1→V2 migration wired into the file and in-memory stores: typed V2 profile operations, payload-first/index-last commit, retained V1 originals, and a fault-injected migration test seeded from the literal V1 fixture.**

## Performance

- **Duration:** ~30 min
- **Tasks:** 1 (TDD: RED → GREEN)
- **Files modified:** 4 (1 created, 3 modified)

## Accomplishments
- Added `migrate`, `saveProfile`, `loadProfile`, and `listActiveProfiles` to the `LocalSessionStore` contract plus `MigrationResult`/`MigrationSkip` result types.
- Implemented the file-store migration: scan each V1 directory, decode-and-validate every source payload through `SchemaMigrations` before writing, emit V2 profile/session/reference payloads into distinct side-by-side directories, and write the `profiles-index.json` commit marker last.
- Made migration idempotent (deterministic V2 identities → one logical profile and one first revision per source Track on every re-run) and lossless (V1 originals remain on disk and still load as V1).
- Made migration fault-recoverable: a write fault injected at the index commit leaves all V1 originals readable and no listable profile, and a retry completes the commit (T-05-04).
- Rejected unsafe opaque ids before any path is built (decode returns `Corrupt`; the source is skipped and reported), satisfying T-05-03.
- Mirrored the exact observable behavior in `InMemorySessionStore` by routing its held V1 objects through the same `SchemaMigrations` dispatch.

## Task Commits

TDD gate sequence (RED → GREEN):

1. **RED — `test(05-2)`** - `bc05971` — contract additions + stubs + `StoreMigrationTest` (6 cases, all failing at runtime via stubs).
2. **GREEN — `feat(05-2)`** - `b1e5ae4` — full file + in-memory implementation; focused test and `:shared:check` green.

No REFACTOR commit was needed (implementation landed clean).

## Files Created/Modified
- `storage/StoreMigrationTest.kt` (created) - side-by-side/retained-originals, no-current-selection, idempotence, fault-recovery, unsafe-ID, and cross-store equivalence gates seeded from the literal V1 fixture.
- `storage/LocalSessionStore.kt` (modified) - V2 migration + profile contract and `MigrationResult`/`MigrationSkip`.
- `storage/FileSessionStore.kt` (modified) - V2 layout dirs/constants, `migrate`/`saveProfile`/`loadProfile`/`listActiveProfiles`, `readProfileIndex`/`listJsonPayloads` helpers, and the `ProfileIndex` commit marker.
- `storage/InMemorySessionStore.kt` (modified) - V2 maps + profile index and the mirrored migration via shared dispatch.

## Decisions Made
- V2 lives in its own directories (`profiles/`, `sessions-v2/`, `references-v2/`) keyed by deterministic identities so migration is additive; the V1 `tracks/`, `sessions/`, `references/` files are never touched.
- `profiles-index.json` is the lone commit point (written last). `listActiveProfiles` reads it, which is what makes the payload-first/index-last ordering observable and the fault case recoverable.
- Reference V2 filename is `<profileId>__<direction>__<real|sim>.json`, carrying the full compatibility-key dimensions into the path while keeping real/simulated slots apart (D-04).

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] Worktree missing gitignored `local.properties` (Android SDK location)**
- **Found during:** Task 1 (first test run)
- **Issue:** `:shared:testAndroidHostTest` needs `local.properties` (`sdk.dir`); the parallel worktree does not inherit the main checkout's gitignored copy.
- **Fix:** Copied `local.properties` from the main checkout into the worktree. File is gitignored and was NOT committed.
- **Committed in:** N/A (gitignored build config).

**2. [Rule 3 - Blocking] Duplicate file-private test helper class names across the same package**
- **Found during:** Task 1 (RED compile)
- **Issue:** `StoreMigrationTest` redeclared `InMemoryFileSystem`/`FailingWritesFileSystem` (also file-private in `FileSessionStoreTest`); Kotlin rejected the same-named private top-level classes in the same package as a visibility clash.
- **Fix:** Renamed this file's helpers to `MigrationInMemoryFileSystem`/`MigrationFailingWritesFileSystem`. The injected-filesystem/fault pattern is unchanged.
- **Committed in:** `bc05971` (RED commit).

---

**Total deviations:** 2 auto-fixed (both Rule 3 - blocking, environment/toolchain). No scope change.

## Issues Encountered
- None beyond the two blocking environment/compile fixes. `:shared:check` is green; pre-existing compiler warnings in unrelated files (lap/review tests, `ReviewScreen`, `StoragePaths` expect/actual) were left untouched (out of scope).

## Known Stubs
- The in-memory store's `sessionsV2`/`referencesV2` maps are populated by migration but not yet read by any public operation — V2 session/reference read paths are introduced by later plans (05-07/05-10). Migration counts and side-by-side persistence are fully exercised; this is the planned division of work, not an unresolved gap.

## Threat Flags
None — no security surface beyond the plan's `<threat_model>` (T-05-03 path safety and T-05-04 migration integrity are both implemented and tested).

## TDD Gate Compliance
- Plan is `type: execute` with one `tdd="true"` task. RED commit `bc05971` (`test(...)`) lands the failing `StoreMigrationTest`; GREEN commit `b1e5ae4` (`feat(...)`) implements until green. Both gate commits leave the focused class and `:shared:check` green.

## User Setup Required
None.

## Next Phase Readiness
- `loadProfile`/`listActiveProfiles` and the profile index are ready for Plan 05-03 (current-selection storage), which can persist `CurrentTrackSelection` alongside `profiles-index.json` without a fallback heuristic.
- Migrated V2 sessions/references are persisted side-by-side for 05-07 (complete-Sector timing) and 05-10 (Ghost compatibility).

## Self-Check: PASSED
- Created file verified present: `StoreMigrationTest.kt`.
- Commits verified in git log: `bc05971` (RED test), `b1e5ae4` (GREEN feat).
- `:shared:testAndroidHostTest` (StoreMigrationTest + FileSessionStoreTest) and full `:shared:check` BUILD SUCCESSFUL; iOS test compilation succeeds (run skipped on Windows as documented).

---
*Phase: 05-track-setup-and-course-profiles*
*Completed: 2026-06-27*
