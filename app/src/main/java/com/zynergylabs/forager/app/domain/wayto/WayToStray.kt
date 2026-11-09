package com.zynergylabs.forager.app.domain.wayto

import com.zynergylabs.forager.app.domain.wayhome.WAY_HOME_STRAY_METERS
import com.zynergylabs.forager.app.domain.wayhome.WAY_HOME_STRAY_REARM_METERS

/**
 * When the way to a waypoint re-routes (dispatch 2026-10-26-11, step 6 PR 3; the step path's step 5): the way home's rule
 * (`WayHomeWatch.judgeStray`; RECORD -1613, and -1625: "re-routes once" means one per stray), on the Maps tab's own fixes
 * rather than the recording service's, since a waypoint navigation needs no recording.
 *
 * - More than [WAY_HOME_STRAY_METERS] (50 m) from the line shown: one re-route from where the walker is.
 * - Once per stray: the next stray counts only after a fix under [WAY_HOME_STRAY_REARM_METERS] (45 m) of the line, the
 *   same strict "under" as the way home's, so a fix at exactly 45 m does not re-arm it.
 * - A stray while a route is already being worked out starts nothing and does not spend the stray, as on the way home.
 *
 * Quiet: it only says what to do; nothing here sounds or vibrates (the step path's step 5). Pure: no clock, no Android,
 * one fix's distance at a time.
 */
class WayToStray {
    private var armed = true

    /** What to do for a fix [metersFromLine] from the line shown, [searching] while a route is being worked out. */
    fun judge(metersFromLine: Double, searching: Boolean): StrayVerdict {
        if (metersFromLine < WAY_HOME_STRAY_REARM_METERS) {
            armed = true
            return StrayVerdict.STAY
        }
        if (metersFromLine <= WAY_HOME_STRAY_METERS) return StrayVerdict.STAY
        if (searching) return StrayVerdict.SEARCHING
        if (!armed) return StrayVerdict.ALREADY_REROUTED
        armed = false
        return StrayVerdict.REROUTE
    }
}

/** What [WayToStray.judge] says for one fix. */
enum class StrayVerdict {
    /** On or near the line: nothing to do. */
    STAY,

    /** Strayed: re-route from here. */
    REROUTE,

    /** Strayed while a route is already being worked out: nothing new starts. */
    SEARCHING,

    /** Still on the stray that already re-routed once. */
    ALREADY_REROUTED,
}
