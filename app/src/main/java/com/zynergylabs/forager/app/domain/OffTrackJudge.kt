package com.zynergylabs.forager.app.domain

import com.zynergylabs.forager.app.domain.model.LatLng
import com.zynergylabs.forager.app.domain.model.TrackPoint
import kotlin.math.hypot

/** How far beyond the path, plus the reading's own reported accuracy, counts as off it. The owner: "About 40 m". Provisional. */
const val OFF_TRACK_LINE_METERS = 40.0

/** How long a walker must be off the path before it alerts. The owner: "About 15 seconds". Provisional. */
const val OFF_TRACK_HOLD_MILLIS = 15_000L

/** How many counting readings, at least, must be off the path before it alerts. The planner's number. Provisional. */
const val OFF_TRACK_MIN_READINGS = 3

/** How long back on the path re-arms the alert. The owner: "back on the path for a little while"; the planner's number. Provisional. */
const val OFF_TRACK_REARM_MILLIS = 10_000L

/**
 * What [OffTrackJudge.next] decided for one reading. [isOffTrack] is what the screen shows (the
 * Return button's off-track colour); [alert] is true on the one reading that goes off, once per stray.
 */
data class OffTrackVerdict(val isOffTrack: Boolean, val alert: Boolean)

/**
 * "Off track", redefined to fit the walker (dispatch 2026-09-28-425, plan task T21; the owner's
 * choices, 2026-10-03, and `RECORD.md` -402: the meaning of off track fits the user, not the
 * software). It replaces `DetectOffTrackUseCase`, which read three readings' distance to the start
 * rising by 25 m, so that walking back toward the start along the other side of a street, with a
 * network reading claiming 400 m accuracy among GPS readings at 4 m, alerted (the owner's walk:
 * five alerts, four false).
 *
 * ## The rule
 *
 * - **Measured against the path walked out** ([path]): the track as it stood when Return was
 *   tapped. Not the whole track, which keeps being recorded on the way back and would always read
 *   near zero. The distance is to the nearest point of the path's line, not to its nearest
 *   recorded point.
 * - **Only GPS readings count.** A network reading counts neither toward going off nor as being back
 *   on: it is skipped, and the verdict stays as it was. A live reading is told by the provider the
 *   platform reported ([next]'s `provider`, dispatch 2026-09-28-527), so an unknown provider is
 *   skipped too. A reading with no provider, a stored point in a replay of a saved walk, and the
 *   path itself, which is stored points, are told by the timestamp rule ([isNetworkProviderFix],
 *   the rule the tracks are read with). Its cost, measured on the owner's walk: 2 of 648 GPS
 *   readings carry sub-second stamps too, and are skipped with the network ones; on a live reading
 *   that no longer happens.
 * - **Off the path** is further from it than [OFF_TRACK_LINE_METERS] plus the reading's own
 *   reported accuracy, so a reading that says it is poor gets a wider line. No ceiling on that
 *   accuracy: the walk had no poor GPS reading (worst 9.9 m) to choose one from.
 * - **Going off** is off the path for at least [OFF_TRACK_HOLD_MILLIS], by at least
 *   [OFF_TRACK_MIN_READINGS] counting readings, with none back on the path in between (a reading
 *   back on restarts it). Then **one** alert.
 * - **Back on, and re-armed:** within the line for at least [OFF_TRACK_REARM_MILLIS]. Only then can
 *   it alert again, and only then does [OffTrackVerdict.isOffTrack] clear. A stray that lasts is not
 *   reminded of ("once per stray", the owner's choice; the 120 s repeat it replaces is gone).
 * - **An empty path decides nothing.**
 *
 * Every figure is provisional, named once above, and is for the owner's next walk to test.
 *
 * Time is the readings' own, not a clock: the judge is pure, and a replay of a stored walk runs it
 * as the phone did.
 */
class OffTrackJudge(path: List<TrackPoint>) {
    private val line: List<LatLng> = path.filterNot { it.isNetworkProviderFix() }.map { LatLng(it.lat, it.lng) }

    private var offSinceMillis: Long? = null
    private var offReadings = 0
    private var onSinceMillis: Long? = null
    private var armed = true
    private var offTrack = false

    /**
     * [provider] is the live reading's, as the platform reported it (dispatch 2026-09-28-527); `null` for a
     * reading that has none, a stored point, which the timestamp rule judges instead. Not a default
     * standing in for a provider: a stored point genuinely has none.
     */
    fun next(reading: TrackPoint, provider: FixProvider? = null): OffTrackVerdict {
        val counts = provider?.mayAct ?: !reading.isNetworkProviderFix()
        if (line.isEmpty() || !counts) return OffTrackVerdict(offTrack, alert = false)
        val t = reading.timestampEpochMillis
        // A reading with no accuracy (null, not reported) widens the line by nothing: it is judged
        // as if it were exact, at OFF_TRACK_LINE_METERS alone. That is the rule today, stated here
        // (dispatch 2026-09-28-658, scout item R8), not decided here: the sampler and the live-fix
        // gate let a null through as "not reported", and this treats it as 0 m. Changing it changes
        // when the off-track alert fires, which is the owner's call, and was taken to them.
        val limit = OFF_TRACK_LINE_METERS + (reading.accuracyMeters?.toDouble() ?: 0.0)
        var alert = false
        if (metersToLine(LatLng(reading.lat, reading.lng)) > limit) {
            onSinceMillis = null
            val since = offSinceMillis ?: t.also { offSinceMillis = it; offReadings = 0 }
            offReadings++
            if (armed && t - since >= OFF_TRACK_HOLD_MILLIS && offReadings >= OFF_TRACK_MIN_READINGS) {
                armed = false
                offTrack = true
                alert = true
            }
        } else {
            offSinceMillis = null
            offReadings = 0
            val since = onSinceMillis ?: t.also { onSinceMillis = it }
            if (!armed && t - since >= OFF_TRACK_REARM_MILLIS) {
                armed = true
                offTrack = false
            }
        }
        return OffTrackVerdict(offTrack, alert)
    }

    /** Metres from [p] to the nearest point of the path's line, on a flat projection about [p]; tens to hundreds of metres, where its error is far below the fixes'. */
    private fun metersToLine(p: LatLng): Double {
        if (line.size == 1) return GeoDistance.metersBetween(p, line[0])
        val (metersPerDegreeLat, metersPerDegreeLng) = GeoDistance.metersPerDegree(p.lat)
        var best = Double.MAX_VALUE
        for (i in 0 until line.lastIndex) {
            val ax = (line[i].lng - p.lng) * metersPerDegreeLng
            val ay = (line[i].lat - p.lat) * metersPerDegreeLat
            val bx = (line[i + 1].lng - p.lng) * metersPerDegreeLng
            val by = (line[i + 1].lat - p.lat) * metersPerDegreeLat
            val vx = bx - ax
            val vy = by - ay
            val lengthSquared = vx * vx + vy * vy
            val s = if (lengthSquared == 0.0) 0.0 else (-(ax * vx + ay * vy) / lengthSquared).coerceIn(0.0, 1.0)
            best = minOf(best, hypot(ax + s * vx, ay + s * vy))
        }
        return best
    }
}
