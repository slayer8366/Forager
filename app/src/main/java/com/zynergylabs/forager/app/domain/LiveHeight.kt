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
 *    off. While the first minute's anchor forms it is the median so far, so the reading can step in that minute.
 * 3. **Shown** = the barometer's height + the anchor.
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

    private val initialDifferences = mutableListOf<Double>()
    private var firstDifferenceAt: Long? = null
    private var anchor: Double? = null
    private var anchorAt = 0L

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
        if (seaLevel is SeaLevelHeight.Known) {
            aboveRaw = seaLevel.aboveRawMeters
            barometerMeters()?.let { baro -> moveAnchor(seaLevel.meters, baro, atEpochMillis) }
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
        if (!hasNoBarometer && baro != null && anchored != null) return ShownHeight.Known(baro + anchored, offset)
        return (seaLevel as? SeaLevelHeight.Known)?.let { ShownHeight.Known(it.meters, it.aboveRawMeters) } ?: ShownHeight.Unavailable
    }

    private fun barometerMeters(): Double? {
        if (hasNoBarometer || readings.isEmpty()) return null
        val sorted = readings.map { it.hPa.toDouble() }.sorted()
        val middle = sorted.size / 2
        val median = if (sorted.size % 2 == 1) sorted[middle] else (sorted[middle - 1] + sorted[middle]) / 2.0
        return pressureAltitudeMeters(median)
    }

    private companion object {
        const val NANOS_PER_MILLI = 1_000_000L
    }
}
