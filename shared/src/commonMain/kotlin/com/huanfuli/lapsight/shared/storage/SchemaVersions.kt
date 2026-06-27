package com.huanfuli.lapsight.shared.storage

/**
 * Canonical schema versions for saved/exported payloads (D-25).
 *
 * Every persisted payload embeds one of these constants so that future loads can
 * detect and migrate older formats instead of silently misreading them. Schema
 * versioning is intentionally established with the first saved format and never
 * postponed.
 */
const val CURRENT_TRACK_SCHEMA_VERSION: Int = 1

/** Schema version for timing-session payloads and their index rows (D-25). */
const val CURRENT_SESSION_SCHEMA_VERSION: Int = 1

/**
 * Schema version for persisted ghost reference-lap payloads (D-05, D-25).
 *
 * A reference payload embeds the raw best-lap samples and a precomputed progress
 * curve so future loads can detect/migrate older formats instead of misreading
 * them.
 */
const val CURRENT_GHOST_REFERENCE_SCHEMA_VERSION: Int = 1

// --- Phase 5 frozen-version dispatch constants (D-12..D-15) --------------------
//
// The V1 payload DTOs are FROZEN: each embeds the literal `1` directly (not the
// mutable `CURRENT_*` constant above) so a future bump can never re-serialize the
// old shape under a new number. The V2 profile/session/reference payloads embed
// the literal `2`. These two constants exist for the migration dispatch layer
// (`SchemaMigrations`) to branch on a named value; they are intentionally NOT used
// as DTO property defaults, which stay literal.

/** Literal schema version emitted by every frozen `*PayloadV1` / `ReviewIndex` DTO. */
const val SCHEMA_VERSION_V1: Int = 1

/** Literal schema version emitted by every V2 profile/session/reference payload. */
const val SCHEMA_VERSION_V2: Int = 2
