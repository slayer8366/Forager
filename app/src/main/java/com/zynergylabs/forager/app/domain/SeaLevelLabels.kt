package com.zynergylabs.forager.app.domain

import com.zynergylabs.forager.app.domain.model.Track

/**
 * One drawn track of a [HeightProfile.Drawn]: its highest and lowest stored heights, where to convert them, and
 * whether they came from a GPX file (dispatch 2026-10-28-13, Amendment 1).
 *
 * [reference] is the track's first point that carries a GPS height (`null` when none does: its heights are then the
 * barometer's standard atmosphere, with no GPS level to convert). [imported] is [Track.importedAtEpochMillis] set: a
 * file's `<ele>` is taken to be above sea level already (the owner: "Show as the file gave them (Recommended)").
 */
data class ProfileTrack(
    val highestMeters: Double,
    val lowestMeters: Double,
    val reference: HeightReference?,
    val imported: Boolean,
)

/** A stored point's position and raw height, to learn the sea-level offset there from. */
data class HeightReference(val lat: Double, val lng: Double, val rawMeters: Double)

internal fun profileTrackOf(track: Track, samples: List<HeightSample>): ProfileTrack = ProfileTrack(
    highestMeters = samples.maxOf { it.altitudeMeters },
    lowestMeters = samples.minOf { it.altitudeMeters },
    reference = track.points.firstOrNull { it.altitude != null }?.let { HeightReference(it.lat, it.lng, it.altitude!!) },
    imported = track.importedAtEpochMillis != null,
)

/** The height chart's two labels (dispatch 2026-10-28-13). */
sealed interface ProfileLabels {
    /** Above sea level. */
    data class Shown(val highestMeters: Double, val lowestMeters: Double) : ProfileLabels

    /** The phone cannot convert: no height number is shown, never the raw one. */
    data object Unsupported : ProfileLabels
}

/**
 * The chart's highest and lowest heights above sea level: each recorded track's own put on sea level by the offset the
 * converter gives at its [ProfileTrack.reference] (the geoid changes by about 0.01 m across a walk, the altitude
 * report row 8), an imported track's as the file gave them. One track the phone cannot convert, or one recorded track
 * with no GPS height at all (Amendment 3, N8), makes the labels [ProfileLabels.Unsupported].
 */
suspend fun HeightProfile.Drawn.seaLevelLabels(converter: SeaLevelConverter): ProfileLabels {
    var highest = Double.NEGATIVE_INFINITY
    var lowest = Double.POSITIVE_INFINITY
    for (track in tracks) {
        val offset = when {
            track.imported -> 0.0
            // Amendment 3, N8 (RECORD -1797): no GPS height, so its heights are the barometer's standard atmosphere,
            // with nothing to put them on sea level by. No numbers, rather than labelling them as sea level.
            track.reference == null -> return ProfileLabels.Unsupported
            else -> when (val sea = converter.seaLevel(track.reference.lat, track.reference.lng, track.reference.rawMeters)) {
                is SeaLevelHeight.Known -> sea.aboveRawMeters
                SeaLevelHeight.Unsupported, SeaLevelHeight.NoHeight -> return ProfileLabels.Unsupported
            }
        }
        highest = maxOf(highest, track.highestMeters + offset)
        lowest = minOf(lowest, track.lowestMeters + offset)
    }
    return ProfileLabels.Shown(highestMeters = highest, lowestMeters = lowest)
}
