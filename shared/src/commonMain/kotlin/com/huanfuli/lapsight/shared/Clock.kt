package com.huanfuli.lapsight.shared

/**
 * Platform clock boundary returning wall-clock epoch milliseconds.
 *
 * Used wherever a saved artifact (track, marking session, timing session) needs a
 * stable created timestamp / id seed. Shared code depends only on this expect;
 * each platform supplies the actual. Tests inject a fixed `now` lambda so saved
 * payloads stay deterministic.
 */
expect fun nowEpochMillis(): Long
