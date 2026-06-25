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
     * crossing, learned from the first accepted crossing. Used by the direction
     * gate so later laps must cross from the same side. Stays null until a
     * crossing with a non-zero side is seen (a crossing that starts exactly on
     * the line has side 0 and must not lock the gate — see WR-01).
     */
    private var expectedStartFinishSign: Double? = null

    var state: LapTimingState = LapTimingState.initial(course)
        private set

    /** Reset the engine to its initial state for a fresh session. */
    fun reset() {
        projection = null
        detector = null
        previous = null
        lastStartFinishAcceptMillis = null
        expectedStartFinishSign = null
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
            LapPhase.AwaitingStart -> startFirstLap(candidate)
            LapPhase.Timing -> completeLap(candidate)
        }
    }

    private fun startFirstLap(candidate: CrossingCandidate) {
        learnDirection(candidate)
        lastStartFinishAcceptMillis = candidate.crossingMillis
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

        // Direction gate.
        if (config.enforceDirection && !directionMatches(candidate)) {
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

        lastStartFinishAcceptMillis = candidate.crossingMillis
        state = state.copy(
            lapCount = lapNumber,
            currentLapNumber = lapNumber + 1,
            currentLapStartMillis = candidate.crossingMillis,
            currentLapElapsedMillis = 0,
            lastLapMillis = completed.durationMillis,
            bestLapMillis = best,
            completedLaps = state.completedLaps + completed,
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
