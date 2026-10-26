package com.zynergylabs.forager.app.domain.wayto

/** STUB for the fail-first commit (dispatch 2026-10-26-11): never re-routes. */
class WayToStray {
    fun judge(metersFromLine: Double, searching: Boolean): StrayVerdict = StrayVerdict.STAY
}

/** What [WayToStray.judge] says for one fix. */
enum class StrayVerdict { STAY, REROUTE, SEARCHING, ALREADY_REROUTED }
