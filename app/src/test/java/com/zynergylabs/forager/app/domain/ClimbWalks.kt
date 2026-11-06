package com.zynergylabs.forager.app.domain

import com.zynergylabs.forager.app.domain.model.TrackPoint
import com.zynergylabs.forager.app.domain.wayhome.Ground
import kotlin.math.ceil
import kotlin.math.hypot
import kotlin.math.pow

/**
 * Synthetic walks with known heights for the climbing allowance's tests (dispatch 2026-10-26-19 Amendment 1): straight
 * legs between [Stop]s on [Ground], walked at 1.2 m/s with a point every 5 s, the height changing evenly along each leg,
 * and a two-minute stand at every stop so the barometer's one-minute windows keep each stop's level.
 */
internal object ClimbWalks {
    /** A place on [Ground], metres east and north of its base, and the true height there. */
    data class Stop(val east: Double, val north: Double, val height: Double)

    enum class Heights {
        /** Pressure from the true height, and GPS altitude the true height: the barometric heights the allowance reads. */
        BAROMETER,

        /** No pressure; GPS altitude the true height. A phone with no barometer (RECORD -1689, C3). */
        GPS_ONLY,

        /** Pressure and GPS altitude both level: the same walk on flat ground. */
        FLAT,
    }

    private const val STEP_MILLIS = 5_000L
    private const val SPEED = 1.2
    private const val STAND_MILLIS = 120_000L

    /** The walk through [stops], ending at [endAt]. */
    fun walk(stops: List<Stop>, heights: Heights, endAt: Long): List<TrackPoint> {
        val samples = ArrayList<Triple<Double, Double, Double>>() // east, north, height
        fun stand(stop: Stop) = repeat((STAND_MILLIS / STEP_MILLIS).toInt()) { samples += Triple(stop.east, stop.north, stop.height) }
        stand(stops.first())
        for ((a, b) in stops.zipWithNext()) {
            val steps = ceil(hypot(b.east - a.east, b.north - a.north) / (SPEED * STEP_MILLIS / 1_000.0)).toInt().coerceAtLeast(1)
            for (s in 1..steps) {
                val t = s.toDouble() / steps
                samples += Triple(a.east + (b.east - a.east) * t, a.north + (b.north - a.north) * t, a.height + (b.height - a.height) * t)
            }
            stand(b)
        }
        val startAt = endAt - (samples.size - 1) * STEP_MILLIS
        return samples.mapIndexed { i, (east, north, height) ->
            val at = Ground.at(east, north)
            val moving = i > 0 && (samples[i - 1].first != east || samples[i - 1].second != north)
            val level = if (heights == Heights.FLAT) 0.0 else height
            TrackPoint(
                lat = at.lat,
                lng = at.lng,
                altitude = 100.0 + level,
                accuracyMeters = 4f,
                timestampEpochMillis = startAt + i * STEP_MILLIS,
                speedMetersPerSecond = if (moving) SPEED.toFloat() else 0f,
                speedAccuracyMetersPerSecond = 0.5f,
                pressureHpa = if (heights == Heights.GPS_ONLY) null else pressureAt(100.0 + level).toFloat(),
            )
        }
    }

    /** The standard atmosphere's pressure at [heightMeters], the inverse of [pressureAltitudeMeters]. */
    fun pressureAt(heightMeters: Double): Double = STANDARD_SEA_LEVEL_HPA * (1.0 - heightMeters / 44_330.0).pow(5.255)

    /**
     * Out over a hill to a far point: up 40 m over 400 m, then down 20 m over 200 m. Stood at the far point, the way
     * back climbs 20 m and descends 40 m: 60 m, which at 2.2 s per metre is 132 s.
     */
    val overAHill = listOf(Stop(0.0, 0.0, 0.0), Stop(0.0, 400.0, 40.0), Stop(0.0, 600.0, 20.0))
}
