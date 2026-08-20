package com.forager.app.domain

/**
 * A simple distance-trending-away heuristic — not real route-deviation detection. While actively
 * returning to a track's start point, if the live distance back has been net *increasing* rather
 * than decreasing across the most recent readings, something is taking the walker the wrong way:
 * could be terrain forcing a detour, could be genuinely drifting off course. This can't tell those
 * apart, and doesn't try to — see `docs/plans/forager-navigator-plan.md` §4 on why a stronger claim
 * ("you are off the trail") isn't one this app can back with the data it has.
 *
 * [recentDistancesMeters] is oldest first. Only the most recent [WINDOW_SIZE] readings are
 * considered, so an old downward trend can't mask a fresh upward one, and fewer than that many
 * readings is never enough to call a trend at all.
 */
class DetectOffTrackUseCase {
    operator fun invoke(recentDistancesMeters: List<Double>): Boolean {
        if (recentDistancesMeters.size < WINDOW_SIZE) return false
        val window = recentDistancesMeters.takeLast(WINDOW_SIZE)
        return window.last() - window.first() > NET_INCREASE_THRESHOLD_METERS
    }

    companion object {
        private const val WINDOW_SIZE = 3

        // Comfortably above ordinary GPS jitter (a stationary fix can wander several meters
        // between readings) so a phone sitting still doesn't read as "off track."
        private const val NET_INCREASE_THRESHOLD_METERS = 25.0
    }
}
