package com.zynergylabs.forager.app.domain

import kotlin.math.abs
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/**
 * The live height the map shows: one value for the strip, the navigation card and the Return control (dispatch
 * 2026-10-28-13, RECORD -1792; the owner: "Yes, same as the strip (Recommended)").
 */
sealed interface ShownHeight {
    /**
     * [meters] above sea level. [aboveRawMeters] is the latest conversion's sea level minus raw height (see
     * [SeaLevelHeight.Known.aboveRawMeters]); [seaLevelOf] uses it to put a stored raw height, such as the start's, on
     * the same footing.
     */
    data class Known(val meters: Double, val aboveRawMeters: Double) : ShownHeight {
        /** A stored raw height from near here, above sea level. The geoid changes by about 0.01 m across a walk (the altitude report, row 8). */
        fun seaLevelOf(rawMeters: Double): Double = rawMeters + aboveRawMeters
    }

    /** No height to show: the phone cannot convert, or no fix has carried one yet. Shown as "Alt unavailable". */
    data object Unavailable : ShownHeight
}

/** A phone with no barometer, the default where none is given: emits `null` once and completes, as [PressureProvider] says. */
object NoBarometer : PressureProvider {
    override val readings: Flow<PressureReading?> = flowOf(null)
}

/**
 * The barometer's anchoring of [barometricHeights], run forward in time so it can drive a live reading (dispatch
 * 2026-10-28-13; the owner: "Sea level + barometer (Recommended)", "Follow the barometer (Recommended)"). Pure, and
 * holds no clock: every time it uses comes in with a fix or a reading. One instance per live collection.
 *
 * The same rule and the same constants, nothing new tuned (the dispatch: "with its existing settings"):
 * 1. **The barometer's height** is the standard atmosphere's ([pressureAltitudeMeters]) for the median of the readings
 *    in the last [BARO_WINDOW_HALF_MILLIS], on the sensor's own clock, inside [PRESSURE_MIN_HPA]..[PRESSURE_MAX_HPA].
 *    Live there is no "after", so the window is the trailing half of the journal's centred one.
 * 2. **The anchor** (sea-level GPS height minus the barometer's) is the median of the differences over the first
 *    [BARO_INITIAL_ANCHOR_MILLIS] of fixes that have both. After that each fix's sea-level height moves it a fraction
 *    dt / [BARO_ANCHOR_TIME_CONSTANT_MILLIS] toward itself, skipping one more than [BARO_ANCHOR_MAX_DISAGREEMENT_METERS]
 *    off. While the first minute's anchor forms it is the median so far; it is not shown until that minute is over (below).
 * 3. **Shown** = the barometer's height + the anchor, once the first minute and the blend after it are over.
 *
 * **The first minute** (Amendment 3, RECORD -1797; the owner: "Show plain GPS until settled (Recommended)"): while the
 * anchor forms, each fix's converted GPS height is shown as it comes, so the forming anchor never steps the reading
 * (Sorrel's review measured 7.0 m at 13 s on the 2026-10-28 walk's stored points). In that minute the reading is the
 * latest GPS height carried on by the barometer since that fix, so a GPS gap still follows the barometer.
 *
 * **The blend** (Amendment 3): GPS's own error can still differ from the steadied height at the minute, so the anchor's
 * share goes from none to all over [LIVE_HEIGHT_BLEND_MILLIS] after it, on the fixes' own clock, rather than stepping.
 * Shown = barometer + (GPS over barometer at the latest fix) × (1 − w) + anchor × w. With fixes coming, that is the GPS
 * height blended into the steadied one; in a GPS gap the blend waits for fixes and the barometer is still followed.
 *
 * Not carried over from [barometricHeights]: its step limit and its mean pass, which look both ways in time. The
 * report's simulation of this rule (docs/audits/2026-10-28-altitude-report.md, section 3) left them out too.
 *
 * **No barometer** ([onNoBarometer]): each fix's sea-level height is shown as it comes, unsmoothed. **GPS gone** (no
 * fixes, or fixes with no height): the anchor holds and the reading follows the barometer. **A phone that cannot
 * convert**: "unavailable" whatever the barometer says, since the barometer alone gives only the standard atmosphere's
 * height, which is not sea level.
 */
class LiveHeight {
    private val readings = ArrayDeque<PressureReading>()
    private var hasNoBarometer = false
    private var latestSeaLevel: SeaLevelHeight? = null
    private var aboveRaw: Double? = null
    private var latestFixAt: Long? = null
    private var gpsOverBarometer: Double? = null

    private val initialDifferences = mutableListOf<Double>()
    private var firstDifferenceAt: Long? = null
    private var anchor: Double? = null
    private var anchorAt = 0L

    /** Resumed from a [Kept] anchor (Amendment 4): the anchor is shown in full, with no blend. */
    private var resumed = false

    /**
     * What a settled steadying keeps through a stop of the barometer (Amendment 4, RECORD -1808): the anchor and the
     * time of its last pull, the latest fix, and [lastReadingNanos], the last barometer reading on the sensor's clock,
     * which [KeptLiveHeight] measures the absence from. The readings themselves are not kept: the 60 s median starts
     * again on new readings only.
     */
    class Kept internal constructor(
        internal val anchor: Double,
        internal val anchorAt: Long,
        internal val firstDifferenceAt: Long,
        internal val aboveRaw: Double,
        internal val latestSeaLevel: SeaLevelHeight,
        internal val latestFixAt: Long,
        val lastReadingNanos: Long,
    )

    /**
     * The anchor to keep through a stop, or `null` when there is none worth keeping: no barometer, no fix, or an anchor
     * whose first minute was not over (a part-formed anchor is not one; the planner accepted this, RECORD -1809).
     */
    fun keep(): Kept? {
        if (hasNoBarometer) return null
        val kept = anchor ?: return null
        val first = firstDifferenceAt ?: return null
        val fixAt = latestFixAt ?: return null
        if (fixAt - first <= BARO_INITIAL_ANCHOR_MILLIS) return null
        val offset = aboveRaw ?: return null
        val seaLevel = latestSeaLevel ?: return null
        val lastReading = readings.lastOrNull() ?: return null
        return Kept(kept, anchorAt, first, offset, seaLevel, fixAt, lastReading.elapsedRealtimeNanos)
    }

    /** The barometer said there is none. Fixes are then shown as they come. */
    fun onNoBarometer(): ShownHeight? {
        hasNoBarometer = true
        readings.clear()
        return shown()
    }

    /** A barometer reading. Returns the height to show now, or `null` while no fix has arrived (nothing to show yet). */
    fun onPressure(reading: PressureReading): ShownHeight? {
        if (reading.hPa.toDouble() in PRESSURE_MIN_HPA..PRESSURE_MAX_HPA) {
            readings.addLast(reading)
            while (reading.elapsedRealtimeNanos - readings.first().elapsedRealtimeNanos > BARO_WINDOW_HALF_MILLIS * NANOS_PER_MILLI) readings.removeFirst()
        }
        return shown()
    }

    /** A fix's sea-level height, at the fix's own time. Returns the height to show now. */
    fun onFix(seaLevel: SeaLevelHeight, atEpochMillis: Long): ShownHeight {
        latestSeaLevel = seaLevel
        latestFixAt = atEpochMillis
        if (seaLevel is SeaLevelHeight.Known) {
            aboveRaw = seaLevel.aboveRawMeters
            barometerMeters()?.let { baro ->
                moveAnchor(seaLevel.meters, baro, atEpochMillis)
                gpsOverBarometer = seaLevel.meters - baro
            }
        }
        return shown() ?: ShownHeight.Unavailable
    }

    private fun moveAnchor(gps: Double, baro: Double, at: Long) {
        val first = firstDifferenceAt ?: at.also { firstDifferenceAt = it; anchorAt = it }
        if (at - first <= BARO_INITIAL_ANCHOR_MILLIS) {
            initialDifferences += gps - baro
            // The journal's own pick for the median: the upper middle of an even count (BarometricHeight.kt).
            anchor = initialDifferences.sorted()[initialDifferences.size / 2]
            return
        }
        val current = anchor ?: return
        val disagreement = gps - (baro + current)
        if (abs(disagreement) <= BARO_ANCHOR_MAX_DISAGREEMENT_METERS) {
            val fraction = ((at - anchorAt).toDouble() / BARO_ANCHOR_TIME_CONSTANT_MILLIS).coerceIn(0.0, 1.0)
            anchor = current + disagreement * fraction
        }
        anchorAt = at
    }

    private fun shown(): ShownHeight? {
        val seaLevel = latestSeaLevel ?: return null
        if (seaLevel is SeaLevelHeight.Unsupported) return ShownHeight.Unavailable
        val offset = aboveRaw ?: return ShownHeight.Unavailable
        val baro = barometerMeters()
        val anchored = anchor
        if (!hasNoBarometer && baro != null && anchored != null) {
            val gps = gpsOverBarometer ?: anchored
            val w = anchorShare()
            return ShownHeight.Known(baro + if (w >= 1.0) anchored else gps + (anchored - gps) * w, offset)
        }
        return (seaLevel as? SeaLevelHeight.Known)?.let { ShownHeight.Known(it.meters, it.aboveRawMeters) } ?: ShownHeight.Unavailable
    }

    /** The anchor's share of the shown height: 0 through the first minute, rising to 1 over [LIVE_HEIGHT_BLEND_MILLIS]. */
    private fun anchorShare(): Double {
        if (resumed) return 1.0
        val first = firstDifferenceAt ?: return 0.0
        val at = latestFixAt ?: return 0.0
        val since = at - (first + BARO_INITIAL_ANCHOR_MILLIS)
        if (since <= 0) return 0.0
        return (since.toDouble() / LIVE_HEIGHT_BLEND_MILLIS).coerceAtMost(1.0)
    }

    private fun barometerMeters(): Double? {
        if (hasNoBarometer || readings.isEmpty()) return null
        val sorted = readings.map { it.hPa.toDouble() }.sorted()
        val middle = sorted.size / 2
        val median = if (sorted.size % 2 == 1) sorted[middle] else (sorted[middle - 1] + sorted[middle]) / 2.0
        return pressureAltitudeMeters(median)
    }

    companion object {
        private const val NANOS_PER_MILLI = 1_000_000L

        /**
         * A steadying carried on from [kept] (Amendment 4): steadied at once, with no first minute and no blend. The
         * anchor's existing pull carries on unchanged, its dt counted from the anchor's last pull, so across the gap.
         */
        fun resumedFrom(kept: Kept): LiveHeight = LiveHeight().apply {
            anchor = kept.anchor
            anchorAt = kept.anchorAt
            firstDifferenceAt = kept.firstDifferenceAt
            aboveRaw = kept.aboveRaw
            latestSeaLevel = kept.latestSeaLevel
            latestFixAt = kept.latestFixAt
            resumed = true
        }
    }
}

/**
 * How long the live height takes to go from the GPS height to the steadied one after the first minute (Amendment 3,
 * RECORD -1797). Proposed by the coder, for the owner to confirm: 30 s. At fixes once a second a gap of g metres
 * moves the reading g / 30 a second, so a gap up to 9 m moves it under 1 ft a fix and the strip ticks one unit at a
 * time. Stored walks put the gap at the minute at 0.4 m (2026-10-28), 3.6 m and 4.0 m (2026-10-24); a GPS error of the
 * first fixes' size, 8 m, would take the full 30 s at about 0.27 m a second. Shorter steps more per fix; longer keeps
 * GPS's error on screen longer.
 */
const val LIVE_HEIGHT_BLEND_MILLIS: Long = 30_000L
