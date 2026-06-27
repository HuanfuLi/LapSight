package com.huanfuli.lapsight.shared.lap

import com.huanfuli.lapsight.shared.LocationSample
import kotlin.math.min

/**
 * Deterministic clean-room lap engine.
 *
 * Consumes a stream of [LocationSample]s for a single session and maintains
 * [LapTimingState]. It is independent of Compose, Android, iOS, and storage:
 * everything runs in plain Kotlin over a [CourseDefinition].
 *
 * Lifecycle:
 * 1. [AwaitingStart][LapPhase.AwaitingStart]: until the first accepted
 *    start/finish crossing.
 * 2. First accepted crossing starts lap 1.
 * 3. Each subsequent accepted start/finish crossing finishes the current lap,
 *    updates last/best/lap count, and opens the next lap.
 * 4. Accepted sector crossings inside the current lap record per-sector splits,
 *    de-duplicated per lap.
 * 5. Rejected crossings preserve state and expose a [LapRejectReason].
 *
 * The origin is taken from the first observed sample so geometry stays accurate.
 *
 * @param course start/finish line and ordered sector lines.
 * @param config filter tuning. Use [LapEngineConfig.lenientForTests] in tests.
 */
class LapEngine(
    private val course: CourseDefinition,
    private val config: LapEngineConfig = LapEngineConfig(),
) {
    private var projection: LocalProjection? = null
    private var detector: CrossingDetector? = null
    private var previous: LocationSample? = null

    /** Time of the last accepted start/finish crossing (cooldown / min-lap). */
    private var lastStartFinishAcceptMillis: Long? = null

    /**
     * Expected sign of [CrossingCandidate.signedSide] for a valid start/finish
     * crossing. When the course supplies an EXPLICIT accepted side
     * ([CourseDefinition.acceptedStartFinishSign], a Recorded/Reverse config) it is
     * seeded here so the orientation is enforced from the first crossing (D-18,
     * D-21). Otherwise it is learned from the first accepted crossing and stays null
     * until a crossing with a non-zero side is seen (a crossing that starts exactly
     * on the line has side 0 and must not lock the gate — see WR-01).
     */
    private var expectedStartFinishSign: Double? = course.acceptedStartFinishSign

    /**
     * True when the course declares an explicit accepted approach side (a
     * direction-specific Recorded/Reverse config). Explicit orientation is enforced
     * unconditionally — even the first start/finish crossing and regardless of
     * [LapEngineConfig.enforceDirection] — so an opposite-direction crossing never
     * starts or completes a lap (D-21). It is never relearned at runtime.
     */
    private val explicitDirection: Boolean = course.acceptedStartFinishSign != null

    /**
     * Index into [CourseDefinition.orderedSectors] of the next intermediate
     * boundary expected to close a complete Sector interval (D-06, D-11). Only a
     * crossing of this exact boundary advances V2 Sector state; duplicate,
     * out-of-order, and backward/opposite crossings leave it untouched (D-20).
     * When it equals the boundary count, the final Sector is awaiting the
     * accepted start/finish crossing.
     */
    private var nextBoundaryIndex: Int = 0

    /**
     * Interpolated open time of the in-progress complete Sector (the prior
     * accepted boundary crossing, or the lap start for Sector 1). Used to derive
     * the adjacent-crossing [SectorResult.durationMillis].
     */
    private var sectorOpenMillis: Long? = null

    var state: LapTimingState = LapTimingState.initial(course)
        private set

    /** Reset the engine to its initial state for a fresh session. */
    fun reset() {
        projection = null
        detector = null
        previous = null
        lastStartFinishAcceptMillis = null
        expectedStartFinishSign = course.acceptedStartFinishSign
        nextBoundaryIndex = 0
        sectorOpenMillis = null
        state = LapTimingState.initial(course)
    }

    /**
     * Feed one sample. Returns the updated [LapTimingState]. Pure with respect
     * to inputs: same sequence of samples always yields the same states.
     */
    fun onSample(sample: LocationSample): LapTimingState {
        val proj = projection ?: LocalProjection(GeoPoint(sample.latitude, sample.longitude)).also {
            projection = it
            detector = CrossingDetector(it)
        }
        val det = detector!!

        val prev = previous
        if (prev == null) {
            previous = sample
            state = state.copy(currentLapElapsedMillis = liveElapsed(sample.elapsedMillis))
            return state
        }

        val movement = MovementSegment(
            startLocal = proj.toLocal(GeoPoint(prev.latitude, prev.longitude)),
            endLocal = proj.toLocal(GeoPoint(sample.latitude, sample.longitude)),
            startMillis = prev.elapsedMillis,
            endMillis = sample.elapsedMillis,
            horizontalAccuracyMeters = worstAccuracy(prev, sample),
            speedMetersPerSecond = sample.speedMetersPerSecond,
        )

        // A single (possibly low-frequency) movement segment can cross the
        // start/finish line and one or more sector lines at once. Collect every
        // crossing on this segment and apply them in interpolated-time order so
        // a sector split is never silently dropped just because the same segment
        // also produced a start/finish crossing (see WR-03/WR-06).
        val crossings = mutableListOf<PendingCrossing>()
        det.detectStartFinish(course.startFinish, movement)?.let {
            crossings += PendingCrossing(it, sector = null)
        }
        for (sector in course.orderedSectors) {
            det.detectSector(sector, movement)?.let {
                crossings += PendingCrossing(it, sector = sector)
            }
        }
        // Order by interpolated crossing time, then by position along the segment
        // (ratio) so simultaneous interpolated timestamps are still applied in
        // physical order rather than insertion order.
        crossings.sortedWith(
            compareBy({ it.candidate.crossingMillis }, { it.candidate.ratio }),
        ).forEach { pending ->
            val sector = pending.sector
            if (sector == null) {
                handleStartFinish(pending.candidate, movement)
            } else {
                handleSectorCrossing(sector, pending.candidate, movement)
            }
        }

        previous = sample
        state = state.copy(currentLapElapsedMillis = liveElapsed(sample.elapsedMillis))
        return state
    }

    private fun handleStartFinish(candidate: CrossingCandidate, movement: MovementSegment) {
        val reject = qualityReject(movement)
        if (reject != null) {
            state = state.copy(lastRejectReason = reject)
            return
        }

        when (state.phase) {
            LapPhase.AwaitingStart -> {
                // An explicit accepted orientation gates even the FIRST crossing, so a
                // wrong-direction opening pass never starts timing (D-21). There is no
                // learned-first-crossing fallback for a direction-specific course.
                if (explicitDirection && !directionMatches(candidate)) {
                    state = state.copy(lastRejectReason = LapRejectReason.WrongDirection)
                    return
                }
                startFirstLap(candidate)
            }
            LapPhase.Timing -> completeLap(candidate)
        }
    }

    private fun startFirstLap(candidate: CrossingCandidate) {
        learnDirection(candidate)
        lastStartFinishAcceptMillis = candidate.crossingMillis
        // Open complete-Sector tracking: Sector 1 begins at the lap crossing.
        nextBoundaryIndex = 0
        sectorOpenMillis = candidate.crossingMillis
        state = state.copy(
            phase = LapPhase.Timing,
            currentLapNumber = 1,
            currentLapStartMillis = candidate.crossingMillis,
            currentLapElapsedMillis = 0,
            sectors = resetSectors(),
            lastRejectReason = null,
        )
    }

    private fun completeLap(candidate: CrossingCandidate) {
        val lapStart = state.currentLapStartMillis ?: return

        // Direction gate. An explicit accepted orientation (Recorded/Reverse) is
        // always enforced; the legacy learned gate stays opt-in via the config. A
        // wrong-way crossing — including a mid-lap turnaround — is rejected here
        // BEFORE any cooldown/min-lap bookkeeping, so timing simply continues with
        // no false completion and no pause (D-17, D-21).
        if ((explicitDirection || config.enforceDirection) && !directionMatches(candidate)) {
            state = state.copy(lastRejectReason = LapRejectReason.WrongDirection)
            return
        }

        // Cooldown.
        lastStartFinishAcceptMillis?.let { last ->
            if (candidate.crossingMillis - last < config.crossingCooldownMillis) {
                state = state.copy(lastRejectReason = LapRejectReason.Cooldown)
                return
            }
        }

        // Minimum lap duration.
        val lapDuration = candidate.crossingMillis - lapStart
        if (lapDuration < config.minLapDurationMillis) {
            state = state.copy(lastRejectReason = LapRejectReason.BelowMinLapDuration)
            return
        }

        // Only now that every gate has passed do we anchor the expected
        // direction — never from a crossing we end up rejecting (covers the case
        // where the first lap opened exactly on the line and its side was 0).
        learnDirection(candidate)

        val lapNumber = state.currentLapNumber ?: 1
        val completed = LapEvent(lapNumber, lapStart, candidate.crossingMillis)
        val best = state.bestLapMillis?.let { min(it, completed.durationMillis) }
            ?: completed.durationMillis

        // D-06: the accepted start/finish crossing closes the FINAL complete Sector
        // at the same interpolated timestamp as the lap — but only when every
        // intermediate boundary was observed in order (otherwise coverage is
        // explicitly incomplete and no final interval is emitted, D-20).
        val boundaries = course.orderedSectors
        val finalSectorResult: SectorResult? =
            if (boundaries.isNotEmpty() && nextBoundaryIndex == boundaries.size) {
                val open = sectorOpenMillis ?: lapStart
                val order = boundaries.size + 1
                SectorResult(
                    lapNumber = lapNumber,
                    sectorId = "sector-$order",
                    sectorOrder = order,
                    startedAtMillis = open,
                    endedAtMillis = candidate.crossingMillis,
                    durationMillis = candidate.crossingMillis - open,
                    cumulativeSplitMillis = candidate.crossingMillis - lapStart,
                )
            } else {
                null
            }

        lastStartFinishAcceptMillis = candidate.crossingMillis
        // Reopen complete-Sector tracking for the next lap at this crossing.
        nextBoundaryIndex = 0
        sectorOpenMillis = candidate.crossingMillis
        state = state.copy(
            lapCount = lapNumber,
            currentLapNumber = lapNumber + 1,
            currentLapStartMillis = candidate.crossingMillis,
            currentLapElapsedMillis = 0,
            lastLapMillis = completed.durationMillis,
            bestLapMillis = best,
            completedLaps = state.completedLaps + completed,
            latestSectorResult = finalSectorResult ?: state.latestSectorResult,
            completedSectorResults = finalSectorResult
                ?.let { state.completedSectorResults + it }
                ?: state.completedSectorResults,
            sectors = resetSectors(),
            lastRejectReason = null,
        )
    }

    private fun handleSectorCrossing(
        sector: SectorLine,
        candidate: CrossingCandidate,
        movement: MovementSegment,
    ) {
        // Sector splits only count inside a running lap.
        if (state.phase != LapPhase.Timing || state.currentLapStartMillis == null) {
            markSectorRejected(sector.id, LapRejectReason.SectorBeforeLapStart)
            return
        }

        val quality = qualityReject(movement)
        if (quality != null) {
            markSectorRejected(sector.id, quality)
            return
        }

        // V2 complete-Sector advancement runs independently of the legacy
        // line-centric split below. Only the next expected boundary in order
        // closes an interval; duplicate / out-of-order / backward crossings are
        // ignored by the order gate (D-06, D-11, D-20).
        maybeAdvanceSector(sector, candidate)

        val existing = state.sectors.firstOrNull { it.sectorId == sector.id }
        if (existing != null && existing.status == SectorStatus.Crossed) {
            markSectorRejected(sector.id, LapRejectReason.DuplicateSector)
            return
        }

        val lapStart = state.currentLapStartMillis!!
        val lapNumber = state.currentLapNumber ?: 1
        val splitMillis = candidate.crossingMillis - lapStart
        val event = SectorEvent(
            lapNumber = lapNumber,
            sectorId = sector.id,
            sectorOrder = sector.order,
            crossingMillis = candidate.crossingMillis,
            splitMillis = splitMillis,
        )
        state = state.copy(
            sectors = state.sectors.map {
                if (it.sectorId == sector.id) {
                    it.copy(
                        status = SectorStatus.Crossed,
                        splitMillis = splitMillis,
                        lastRejectReason = null,
                    )
                } else {
                    it
                }
            },
            latestSector = event,
            lastRejectReason = null,
        )
    }

    /**
     * Close a complete Sector interval iff [sector] is the next expected
     * intermediate boundary in order (D-06, D-11). A crossing of any other
     * boundary — an already-passed one (duplicate / backward / opposite) or a
     * future one (out-of-order) — leaves the expected state untouched (D-20).
     *
     * Emits a [SectorResult] carrying both the adjacent-crossing duration and the
     * separate cumulative Split from the lap start, then advances the expected
     * boundary and opens the next interval at this crossing.
     */
    private fun maybeAdvanceSector(sector: SectorLine, candidate: CrossingCandidate) {
        if (state.phase != LapPhase.Timing) return
        val lapStart = state.currentLapStartMillis ?: return
        val boundaries = course.orderedSectors
        val idx = boundaries.indexOfFirst { it.id == sector.id }
        if (idx < 0 || idx != nextBoundaryIndex) return

        val open = sectorOpenMillis ?: lapStart
        val close = candidate.crossingMillis
        val order = idx + 1
        val result = SectorResult(
            lapNumber = state.currentLapNumber ?: 1,
            sectorId = "sector-$order",
            sectorOrder = order,
            startedAtMillis = open,
            endedAtMillis = close,
            durationMillis = close - open,
            cumulativeSplitMillis = close - lapStart,
        )
        nextBoundaryIndex = idx + 1
        sectorOpenMillis = close
        state = state.copy(
            latestSectorResult = result,
            completedSectorResults = state.completedSectorResults + result,
        )
    }

    private fun markSectorRejected(sectorId: String, reason: LapRejectReason) {
        state = state.copy(
            sectors = state.sectors.map {
                if (it.sectorId == sectorId) it.copy(lastRejectReason = reason) else it
            },
            lastRejectReason = reason,
        )
    }

    private fun resetSectors(): List<SectorTimingState> = course.orderedSectors.map {
        SectorTimingState(
            sectorId = it.id,
            sectorName = it.name,
            sectorOrder = it.order,
        )
    }

    private fun qualityReject(movement: MovementSegment): LapRejectReason? {
        config.maxHorizontalAccuracyMeters?.let { maxAcc ->
            val acc = movement.horizontalAccuracyMeters
            if (acc != null && acc > maxAcc) return LapRejectReason.PoorAccuracy
        }
        if (movement.effectiveSpeed() < config.minSpeedMetersPerSecond) {
            return LapRejectReason.TooSlow
        }
        return null
    }

    /**
     * Record the approach side of an accepted crossing as the expected
     * direction, but only from a non-degenerate side: a crossing that starts
     * exactly on the line has side 0 and must not lock the gate (WR-01). The
     * side is learned once, from the first accepted crossing with a clear side.
     */
    private fun learnDirection(candidate: CrossingCandidate) {
        if (expectedStartFinishSign == null) {
            val sign = signOf(candidate.signedSide)
            if (sign != 0.0) expectedStartFinishSign = sign
        }
    }

    /**
     * A crossing matches the learned direction when it approaches the line from
     * the same side as the first accepted crossing (the sign of the 2D cross
     * product). This half-plane check is robust to heading noise on
     * low-frequency GPS. The check is skipped while the expected side is
     * unlearned or the candidate sits exactly on the line (side 0).
     */
    private fun directionMatches(candidate: CrossingCandidate): Boolean {
        val expected = expectedStartFinishSign ?: return true
        val sign = signOf(candidate.signedSide)
        if (sign == 0.0) return true
        return sign == expected
    }

    private fun liveElapsed(nowMillis: Long): Long? {
        val start = state.currentLapStartMillis ?: return null
        return (nowMillis - start).coerceAtLeast(0)
    }

    private fun worstAccuracy(a: LocationSample, b: LocationSample): Double? {
        val accA = a.horizontalAccuracyMeters
        val accB = b.horizontalAccuracyMeters
        return when {
            accA == null -> accB
            accB == null -> accA
            else -> kotlin.math.max(accA, accB)
        }
    }

    private fun signOf(value: Double): Double = when {
        value > 0.0 -> 1.0
        value < 0.0 -> -1.0
        else -> 0.0
    }

    /**
     * A crossing detected on the current movement segment, tagged with the
     * sector it belongs to ([sector] == null means the start/finish line).
     */
    private data class PendingCrossing(
        val candidate: CrossingCandidate,
        val sector: SectorLine?,
    )
}
