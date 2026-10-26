package com.zynergylabs.forager.app.domain

import com.zynergylabs.forager.app.domain.model.LatLng
import com.zynergylabs.forager.app.domain.wayhome.WAY_HOME_LINE_STEP_METERS
import com.zynergylabs.forager.app.domain.wayhome.WAY_HOME_TRAIL_JOIN_METERS
import com.zynergylabs.forager.app.domain.wayhome.WayHomeLine
import com.zynergylabs.forager.app.domain.wayhome.WayHomePlace
import com.zynergylabs.forager.app.domain.wayhome.WayHomeProgress
import com.zynergylabs.forager.app.domain.wayhome.WayHomeShown
import com.zynergylabs.forager.app.domain.wayhome.WayHomeWatch
import com.zynergylabs.forager.app.domain.wayhome.metersAheadOfLineStart
import com.zynergylabs.forager.app.domain.wayhome.metersPastLineStart

// What ReturnWatch is given of the way home shown, and when its dotted part counts as on course (dispatch 2026-10-25-09).
// Out of ReturnWatch.kt, which had grown in each of the last three PRs (Sorrel's 402-2).

/**
 * The lines of a way home as [ReturnWatch] is given them: [generation] is the way home's own (`WayHomeShown.generation`),
 * so the watch keeps each trail part once; [points] is the trail part, and [trackBack] the dotted part from the walker's
 * newest stored point back to where they left the trail (RECORD -1635), each in walking order. [line] is the whole line
 * and [place] the walker's place on it as the way-home watch last found it, which [isHeadingBackAlong] reads.
 */
data class ShownTrail(
    val generation: Long,
    val points: List<LatLng>,
    val trackBack: List<LatLng> = emptyList(),
    val line: WayHomeLine? = null,
    val place: WayHomePlace? = null,
)

/**
 * What [ReturnWatch] is given for [trackId] from the way home shown now (`WayHomeWatch.shown`) and the walker's place on
 * it ([progress], `WayHomeWatch.progressFor`): `null` when it is for another track, still being worked out, or the track
 * alone (no line along trails). Here rather than in `AppContainer` so the mapping is tested (dispatch 2026-10-25-09).
 */
fun shownTrailOf(shown: WayHomeShown?, progress: WayHomeProgress?, trackId: String): ShownTrail? {
    if (shown == null || shown.trackId != trackId) return null
    val line = shown.line ?: return null
    val wayHome = line.wayHome
    return ShownTrail(shown.generation, wayHome.trail.points, wayHome.trackBack.points, line, (progress as? WayHomeProgress.Placed)?.place)
}

/**
 * [shownTrailOf] read from [watch], as `AppContainer` hands it to [ReturnWatch] (dispatch 2026-10-25-09 Amendment 2).
 *
 * The line is read before the place, and that order is what keeps them about the same line: a new line is published
 * with the place cleared, under the way-home watch's lock (`WayHomeWatch.launchJob`), and only that watch's `onFix` sets
 * the place again, on the service's collector, which is inside [ReturnWatch.onFix] now and so cannot run. So a place read
 * after the line is either for that line or `null`.
 *
 * **One reading behind.** The service gives each fix to [ReturnWatch] before `WayHomeWatch` (TrackRecordingService.kt:223
 * before :239), so the place read here is the last reading's. That half of [isHeadingBackAlong] starts and stops counting
 * one reading late, a second at the usual rate, against a 15 s hold; the other half reads this reading.
 *
 * **Why not the place published on `WayHomeShown`** (the other route RECORD -1640 offered): `shown` would then change on
 * every fix, and the recording screen collects it, so every collector of the way home would run once a second for a
 * figure only this watch reads. That is a change beyond the rule; this route changes only the lock note above.
 */
fun shownTrailOf(watch: WayHomeWatch, trackId: String): ShownTrail? {
    val shown = watch.shown.value
    return shownTrailOf(shown, watch.progressFor(trackId), trackId)
}

/**
 * Whether the walker at [here] is heading back along [line]'s dotted part, toward home (dispatch 2026-10-25-09 Amendment
 * 2; RECORD -1640, the owner: "A: dotted line only (Recommended)", "Yes, a pause counts (Recommended)", and "Coder
 * proposes, Sorrel checks (Recommended)" for the figure). A re-routed line starts at the walker's newest stored point,
 * at the far end of their stray, and runs back along it; so heading back is being ahead of that start, toward home, and
 * walking out is being at it or past it.
 *
 * - **Past the line's start, never** ([metersPastLineStart] above 0, the test #398 added for the figure, RECORD -1630):
 *   the walker is beyond the start along the way out, which is walking out along the stray however far along the line
 *   they once were.
 * - **Otherwise, heading back** when either:
 *   - their [place] has moved off the line's first node (its distance along the line above 0) **and** the fix is over
 *     [WAY_HOME_TRAIL_JOIN_METERS] (25 m) from that node. The place is the earliest node within 25 m of the fix, never
 *     moving back while one from it on is within 50 m (`placeOnWayHome`), so it leaves the first node only once the
 *     walker is over 25 m from it, ahead along the line, and it holds where they stop; or
 *   - they are at least [WAY_HOME_LINE_STEP_METERS] (10 m) ahead of the start along the line's first stretch
 *     ([metersAheadOfLineStart]), which counts the first 25 m the place cannot.
 *
 * **A pause counts:** both halves hold while the walker stands still where they were heading back. **Held at the start**
 * (within 10 m of it, either side, or behind it) is walking out: the alert comes after the hold, as before #402.
 *
 * **Why the place half stops at 25 m from the start** (dispatch 2026-10-26-03; RECORD -1645; Sorrel's 402a2-1): the
 * place does not move back (`placeOnWayHome`, the needle's rule, RECORD -1613), so once it has left the first node,
 * after 30 m back along the line or one reading that jumps that far toward home, it stays off it when the walker walks
 * out again to the start and stands there. Without the 25 m, every reading that wandered a metre toward home counted
 * there and restarted the hold, so a walker held at the start was never alerted. Within 25 m of the start a place that
 * did move back would be the first node again, so inside it the place half reads as that place would, and the node-step
 * half alone decides; beyond it nothing changes. The 25 m is the place's own reach, no figure of its own.
 * Rejected: (a) only while the fix is ahead of the start at all (`metersAheadOfLineStart > 0`), which is what "not past
 * the start" above already gives, so it changes nothing; and the place half dropped, which stops counting a walker
 * heading back along a line that turned within a node step of its start once they are on the next part (the bent
 * stray in ReturnWatchDottedLineTest), and that is following the dotted line home.
 * **What it costs:** a line that curls back to within 25 m of its own start, and a walker following it there who is
 * under 10 m ahead along its first stretch: those readings no longer count by the place. Behind the start such a line
 * already did not count (the first rule above).
 *
 * **Why 10 m:** it is the line's own node step, the finest figure the line is drawn and judged at, and no figure of its
 * own. A smaller one (any distance ahead, 0 m) lets a walker who walked out and stopped at the start read as heading back
 * on every reading that wanders a few metres toward home, and one such reading restarts the 15 s hold, so they might
 * never be alerted. The place alone (no second half) starts counting only 25 m back along the line, at walking pace
 * about 18 s, longer than the hold.
 *
 * **What it costs, said plainly:** a walker who goes a few metres past the re-route point before turning back can still
 * be alerted in their first 10 m back, while within 40 m of nothing else counted. The line's start lags the walker by up
 * to one store write (RECORD -1636, dispatch 2026-10-25-10 is to close it), and while it does that lag is added to the
 * 10 m. The first stretch gives the direction, so a stray that turned within a node step of the start reads ahead or past
 * by that stretch, as [metersPastLineStart] does.
 *
 * A stale or jumping fix: a GPS fix the way-home gate refuses (over 50 m accuracy) leaves the place where it was, so it
 * holds as a pause does; one that jumps ahead counts for that reading by the second half, and by the first half only
 * while the walker stays short of the start. A network fix is not judged at all (OffTrackJudge.kt:85).
 */
fun isHeadingBackAlong(line: WayHomeLine, place: WayHomePlace?, here: LatLng): Boolean {
    if (metersPastLineStart(line, here) > 0.0) return false
    val placeAlong = place?.let { line.alongMeters.getOrNull(it.index) } ?: 0.0
    val placeCounts = placeAlong > 0.0 && line.nodes.first().let { GeoDistance.metersBetween(it, here) > WAY_HOME_TRAIL_JOIN_METERS }
    return placeCounts || metersAheadOfLineStart(line, here) >= WAY_HOME_LINE_STEP_METERS
}
