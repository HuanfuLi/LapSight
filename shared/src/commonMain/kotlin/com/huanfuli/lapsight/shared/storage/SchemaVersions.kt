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
