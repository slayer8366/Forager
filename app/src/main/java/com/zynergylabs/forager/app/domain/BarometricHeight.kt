package com.zynergylabs.forager.app.domain

import com.zynergylabs.forager.app.domain.model.TrackPoint
import kotlin.math.abs
import kotlin.math.pow

/**
 * Heights from the barometer, re-anchored to GPS height (RECORD -864, the owner: "Use the barometer (Recommended)";
 * RECORD -867, the planner's calls). One per point, `null` where the point has none to give. Pure: no Android, no clock.
 *
 * The barometer gives height *change* to well under a metre; GPS gives height above sea level, wandering by tens of
 * metres. So the shape of the walk comes from pressure, and its level from GPS, slowly:
 *
 * 1. **Pressure to height** ([pressureAltitudeMeters]): the standard-atmosphere formula. Only differences are used, so
 *    the sea-level reference does not matter.
 * 2. **Operating limits** (CLAUDE.md: a reported range is not a safe range). A reading outside
 *    [PRESSURE_MIN_HPA]..[PRESSURE_MAX_HPA] is not used. Then, over each unbroken run of readings, three passes, each
 *    over a window of time ([BARO_WINDOW_HALF_MILLIS] each side), not a count of samples:
 *    - a **median** of the readings in the window, which takes out a squeeze in a pocket, a puff from a car's vents and
 *      a phone whose pressure swings while it is carried;
 *    - a **step limit**: a step in that median faster than [BARO_MAX_VERTICAL_SPEED_METERS_PER_SECOND], a speed set for
 *      walking, is taken out and the level carries on from where it was (a car door closed with the vents on); its step
 *      back is taken out too. A real change taken out this way (a lift) comes back slowly through the anchor;
 *    - a **mean** of the limited heights in the window.
 *    **At a walk's start and end** the window keeps its width in time by using what there is on the one side, so the
 *    last height is still a median and mean of the last minute's readings, never one reading on its own.
 *    Why (RECORD -889): the first version used a 5-point median that shrank toward a walk's ends, so the first and last
 *    heights were single readings, and a 2 m/s limit that let a carried phone's swings through. On the S22 test phone's
 *    flat pocket walk, whose pressure swung by up to 1.4 hPa (about 12 m) between points 5 to 8 s apart, the chart ended
 *    in a false drop of 9.4 m (the owner: "I dropped into the earth at the end").
 * 3. **Re-anchoring** to GPS: the first anchor is the median of (GPS height − barometric height) over the points with
 *    both in the first [BARO_INITIAL_ANCHOR_MILLIS]; after that each point with a GPS height moves the anchor a
 *    fraction dt / [BARO_ANCHOR_TIME_CONSTANT_MILLIS] toward it, so GPS's short wander averages out while weather drift
 *    (about 1 hPa an hour in unsettled weather, some 8 m) is followed within about that much. A GPS height more than
 *    [BARO_ANCHOR_MAX_DISAGREEMENT_METERS] from the current height is not used for the anchor. With no GPS height at
 *    all, the anchor is 0 and the heights are the standard atmosphere's, still right in their changes.
 *
 * Every limit here is a starting value, not a measured one (RECORD -867), for the device check to confirm.
 *
 * A point with no usable pressure gets `null`; [trackHeights] uses this only for a walk where most points carry
 * pressure, so that is a reading missed at the very start, before the sensor's first sample.
 */
internal fun barometricHeights(points: List<TrackPoint>): List<Double?> {
    val baro: List<Double?> = points.map { point -> point.pressureHpa?.toDouble()?.takeIf { it in PRESSURE_MIN_HPA..PRESSURE_MAX_HPA }?.let(::pressureAltitudeMeters) }

    val times = points.map { it.timestampEpochMillis }

    // The median over the window, per unbroken run of usable readings (a missing one breaks the run, so nothing is
    // bridged across it).
    val filtered = MutableList<Double?>(points.size) { null }
    runsOf(baro).forEach { run ->
        run.forEach { i -> filtered[i] = median(run.filter { j -> abs(times[j] - times[i]) <= BARO_WINDOW_HALF_MILLIS }.map { baro[it]!! }) }
    }

    // The step limit: a step in the median faster than a walker climbs is taken out, and the level carries on from where
    // it was. A step back the other way is taken out the same way.
    var stepsTakenOut = 0.0
    var previous: Double? = null
    var previousAt = 0L
    val stepped = filtered.mapIndexed { i, height ->
        if (height == null) return@mapIndexed null
        val at = times[i]
        previous?.let { before ->
            val seconds = (at - previousAt).coerceAtLeast(1L) / 1_000.0
            if (abs(height - before) / seconds > BARO_MAX_VERTICAL_SPEED_METERS_PER_SECOND) stepsTakenOut -= height - before
        }
        previous = height
        previousAt = at
        height + stepsTakenOut
    }

    // The mean over the window.
    val limited = MutableList<Double?>(points.size) { null }
    runsOf(stepped).forEach { run ->
        run.forEach { i -> limited[i] = run.filter { j -> abs(times[j] - times[i]) <= BARO_WINDOW_HALF_MILLIS }.map { stepped[it]!! }.average() }
    }

    // The anchor: a slow pull toward GPS height.
    val firstAt = points.indices.firstOrNull { limited[it] != null }?.let { points[it].timestampEpochMillis } ?: return limited
    val initialDifferences = points.indices
        .filter { i -> limited[i] != null && points[i].altitude != null && points[i].timestampEpochMillis - firstAt <= BARO_INITIAL_ANCHOR_MILLIS }
        .map { i -> points[i].altitude!! - limited[i]!! }
        .sorted()
    var anchor = if (initialDifferences.isEmpty()) 0.0 else initialDifferences[initialDifferences.size / 2]
    var anchorAt = firstAt
    return points.indices.map { i ->
        val height = limited[i] ?: return@map null
        val at = points[i].timestampEpochMillis
        val gps = points[i].altitude
        if (gps != null && at - firstAt > BARO_INITIAL_ANCHOR_MILLIS) {
            val disagreement = gps - (height + anchor)
            if (abs(disagreement) <= BARO_ANCHOR_MAX_DISAGREEMENT_METERS) {
                val fraction = ((at - anchorAt).toDouble() / BARO_ANCHOR_TIME_CONSTANT_MILLIS).coerceIn(0.0, 1.0)
                anchor += disagreement * fraction
            }
            anchorAt = at
        }
        height + anchor
    }
}

/** Height in metres for a pressure in hPa, by the standard atmosphere (sea level 1013.25 hPa). */
fun pressureAltitudeMeters(hPa: Double): Double = 44_330.0 * (1.0 - (hPa / STANDARD_SEA_LEVEL_HPA).pow(1.0 / 5.255))

/** The indices of each unbroken run of non-null [values], in order. */
private fun runsOf(values: List<Double?>): List<List<Int>> {
    val runs = mutableListOf<List<Int>>()
    var current = mutableListOf<Int>()
    values.forEachIndexed { i, value ->
        if (value == null) {
            if (current.isNotEmpty()) runs += current
            current = mutableListOf()
        } else {
            current += i
        }
    }
    if (current.isNotEmpty()) runs += current
    return runs
}

/** The median; for an even count, the mean of the middle two. */
private fun median(values: List<Double>): Double {
    val sorted = values.sorted()
    val middle = sorted.size / 2
    return if (sorted.size % 2 == 1) sorted[middle] else (sorted[middle - 1] + sorted[middle]) / 2.0
}

const val STANDARD_SEA_LEVEL_HPA: Double = 1013.25

/** Readings outside this range are not used: about 5,500 m above sea level down to below the lowest land. */
const val PRESSURE_MIN_HPA: Double = 500.0
const val PRESSURE_MAX_HPA: Double = 1100.0

/**
 * Faster than this, a step in the median height is a pressure transient, not a walker: 0.5 m/s, where steep stairs climbed
 * briskly are about 0.3 m/s. Was 2 m/s (RECORD -889). A starting value for the device check.
 */
const val BARO_MAX_VERTICAL_SPEED_METERS_PER_SECOND: Double = 0.5

/**
 * Half the barometer's smoothing window: 60 s each side of a point, so a minute's readings either side at most (RECORD
 * -889). At 45 s the S22 walk still ended 6.3 m low, by a port of this code; at 60 s, 1.2 m. A starting value.
 */
const val BARO_WINDOW_HALF_MILLIS: Long = 60_000L

/** The first anchor is taken over this long from the first reading. */
const val BARO_INITIAL_ANCHOR_MILLIS: Long = 60_000L

/**
 * How slowly the anchor follows GPS: an hour. Chosen over 20 minutes on synthetic walks (2026-10-11, part 5): at 20 minutes
 * GPS height wandering ±17 m over three-quarters of an hour came back through the anchor as 15 m of spread and 20 m of
 * false climb on a flat walk; at an hour, 6 m and none. The cost is weather: a strong 1 hPa an hour fall is followed
 * within about 8 m. A starting value for the device check.
 */
const val BARO_ANCHOR_TIME_CONSTANT_MILLIS: Long = 60 * 60_000L

/** A GPS height this far from the current height is not used to move the anchor. */
const val BARO_ANCHOR_MAX_DISAGREEMENT_METERS: Double = 30.0
