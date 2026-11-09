package com.zynergylabs.forager.app.domain

import com.zynergylabs.forager.app.domain.model.TrackPoint

/**
 * The height the app reads for each of a walk's points, one per point, `null` where the point has none: what Climb
 * (and Descent) are computed from ([ComputeTrackStatisticsUseCase]) and what the height chart draws ([heightProfileOf]),
 * on the journal entry and the Records track sheet alike (RECORD -867, the owner: "Both places (Recommended)").
 *
 * Read-time only: nothing stored changes. A point's stored `altitude` stays the GPS height the phone reported, so a GPX
 * export is unchanged (RECORD -867).
 *
 * **Barometer height** ([barometricHeights]) for a walk recorded with the barometer: at least half its points carry a
 * pressure (RECORD -864, the owner: "Use the barometer (Recommended)"; part 5). Otherwise **smoothed GPS height**
 * ([smoothedGpsHeights]): a phone with no barometer, and every walk recorded before version 21. The owner, RECORD -864:
 * "Measuring height is wonky, on a flat walk it showed fluctuating values ... The phone was in my pocket."
 */
fun trackHeights(points: List<TrackPoint>): List<Double?> {
    val withPressure = points.count { it.pressureHpa != null }
    return if (withPressure > 0 && withPressure * 2 >= points.size) barometricHeights(points) else smoothedGpsHeights(points)
}

/**
 * GPS heights with their spikes and short jitter taken out, in two passes over each unbroken run of points that carry a
 * height (a point with none breaks the run, as it breaks Climb's running reference, so nothing is bridged across a gap):
 *
 * 1. a median of up to [HEIGHT_MEDIAN_MAX_SIDE] points each side, which removes a lone spike such as the sharp drop on
 *    the owner's Oct 7 chart without moving a real slope;
 * 2. a mean over the points within [HEIGHT_SMOOTHING_HALF_WINDOW_MILLIS] each side, which averages out jitter.
 *
 * Both windows are **symmetric**, the same number of points each side of the one being smoothed, shrinking toward a
 * run's ends. So a steady climb or descent comes through exactly (a straight line's centred median and mean are the
 * line itself), a run's first and last heights are kept as recorded, and a run of one or two points is unchanged. A
 * one-sided window would pull every walk's ends toward its middle and shave its Climb.
 *
 * Starting values, not measured ones (RECORD -867: "The sensor limits in step 0 are starting values, to be confirmed by
 * the device check"). What a 60-second window cannot remove is GPS height that wanders slowly over many minutes, which is
 * what the barometer is for.
 */
internal fun smoothedGpsHeights(points: List<TrackPoint>): List<Double?> {
    val heights = MutableList<Double?>(points.size) { null }
    var start = 0
    while (start < points.size) {
        if (points[start].altitude == null) {
            start++
            continue
        }
        var end = start
        while (end + 1 < points.size && points[end + 1].altitude != null) end++
        val run = points.subList(start, end + 1)
        val times = run.map { it.timestampEpochMillis }
        val raw = run.map { it.altitude!! }
        val medians = raw.indices.map { i -> symmetricWindow(raw, times, i, HEIGHT_MEDIAN_MAX_SIDE).sorted().let { it[it.size / 2] } }
        medians.indices.forEach { i -> heights[start + i] = symmetricWindow(medians, times, i, Int.MAX_VALUE).average() }
        start = end + 1
    }
    return heights
}

/**
 * The values around index [i]: the same number each side, at most [maxSide], within the run, and none more than
 * [HEIGHT_SMOOTHING_HALF_WINDOW_MILLIS] from point [i]'s time. Always an odd count, so its median is one of its values.
 */
private fun symmetricWindow(values: List<Double>, times: List<Long>, i: Int, maxSide: Int): List<Double> {
    var side = 0
    while (
        side < maxSide &&
        i - side - 1 >= 0 &&
        i + side + 1 < values.size &&
        times[i] - times[i - side - 1] <= HEIGHT_SMOOTHING_HALF_WINDOW_MILLIS &&
        times[i + side + 1] - times[i] <= HEIGHT_SMOOTHING_HALF_WINDOW_MILLIS
    ) {
        side++
    }
    return values.subList(i - side, i + side + 1)
}

/** Half the smoothing window: 30 s each side of a point. A starting value (see [smoothedGpsHeights]). */
const val HEIGHT_SMOOTHING_HALF_WINDOW_MILLIS: Long = 30_000L

/** The spike filter's reach: up to 2 points each side, a 5-point median. A starting value (see [smoothedGpsHeights]). */
const val HEIGHT_MEDIAN_MAX_SIDE: Int = 2
