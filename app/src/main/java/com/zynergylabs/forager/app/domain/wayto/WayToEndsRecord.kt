package com.zynergylabs.forager.app.domain.wayto

import com.zynergylabs.forager.app.domain.dropoff.StreetDropOff
import java.util.Locale

/**
 * What each end of one way-to search became, for the log (dispatch 2026-11-05-07, A1 and A2; the owner: "Add one log
 * line first (Recommended)", the option reading "each Navigate logs what your end of the route became (a pick-up point,
 * the trail, or refused) and why. Nothing changes on screen."). [StreetDropOffEnds] records it as it plans the two ends,
 * and [FindWayToWaypointUseCase] logs [line] once per search that reaches the ends step.
 *
 * It says what each end was planned as, before the one re-run and the gates on the legs drawn. A search refused after
 * that is followed by today's straight-line line, which says why, word for word (A3).
 *
 * **No position** (A2): lengths, ranks and reasons only, never a coordinate, a tile, a file name or a street name. That
 * is why a by-trail end is named by its reason's kind ([ByTrailBecause]); its own line, logged beside this one, keeps
 * the full reason as it always has (the planner, RECORD -2149, F2).
 */
data class WayToEndsRecord(val walker: WayToEndBecame, val waypoint: WayToEndBecame) {

    fun line(): String = "The way to the waypoint's ends: the walker's end ${walker.words(walkers = true)}; the waypoint's end ${waypoint.words(walkers = false)}."

    companion object {
        /** For a [WayToEnds] that records nothing (only a test's): said, never silently missing (RECORD -2149). */
        const val NOT_RECORDED = "The way to the waypoint's ends were not recorded by the ends step."
    }
}

/** What one end became. */
sealed interface WayToEndBecame {
    /** Moved to a pick-up (the walker's end) or a drop-off (the waypoint's), chosen at [rank], its straight leg [legMeters] long. */
    data class MovedTo(val rank: StreetDropOff.Rank, val legMeters: Double) : WayToEndBecame

    /** The engine's end kept and its leg gated: [StreetDropOff.classify] found no street end. */
    data object NotAStreetEnd : WayToEndBecame

    /**
     * The engine's end kept and its lead-in gated: [StreetDropOff.classify] could not tell, the end's own tile not being
     * in the map read. The walker's end only; at the waypoint's end the same is refused.
     */
    data object StreetOrTrailUntold : WayToEndBecame

    /**
     * The walker's end only: the fix, reporting [accuracyMeters] (null: none), is not good enough to look for a pick-up
     * ([StreetDropOffEnds.PICK_UP_FIX_ACCURACY_METERS]), so the engine's end is kept and the lead-in gated.
     */
    data class NotJudged(val accuracyMeters: Float?) : WayToEndBecame

    /** By trail as today, because of [because]. */
    data class ByTrail(val because: ByTrailBecause) : WayToEndBecame

    /** Refused with [note] while it was planned. */
    data class Refused(val note: WayToWaypointNote) : WayToEndBecame

    /** The walker's end only: never planned, the waypoint's end being refused first. */
    data object NotReached : WayToEndBecame
}

/** Why an end went by trail, by kind: one for each way [StreetDropOffEnds] reads the map round an end (A1, A4, A5). */
enum class ByTrailBecause(val words: String) {
    MAP_UNREADABLE("the map could not be read"),
    NO_FENCE_MARK("a stored tile in reach has no fence mark"),
    WAYS_UNREADABLE("the route ways could not be read"),
}

private fun WayToEndBecame.words(walkers: Boolean): String {
    val leg = if (walkers) "lead-in" else "leg"
    val kept = "keeps the engine's end and gates its $leg"
    return when (this) {
        is WayToEndBecame.MovedTo -> {
            val what = if (walkers) "pick-up" else "drop-off"
            "moves to a $what at ${rank.words()} (rank ${rank.ordinal + 1} of ${StreetDropOff.Rank.entries.size}, ${rank.name}), its leg ${legMeters.toInt()} m"
        }
        WayToEndBecame.NotAStreetEnd -> "$kept: not a street end (no building within ${StreetDropOff.BUILDING_METERS.toInt()} m, " +
            "and not built-up land with a public street within ${StreetDropOff.STREET_METERS.toInt()} m)"
        WayToEndBecame.StreetOrTrailUntold -> "$kept: street or trail could not be told (the map tile there is not stored or was not read)"
        is WayToEndBecame.NotJudged -> {
            val limit = StreetDropOffEnds.PICK_UP_FIX_ACCURACY_METERS.toInt()
            val fix = accuracyMeters?.let { "${String.format(Locale.ROOT, "%.1f", it)} m, worse than $limit m" } ?: "no accuracy"
            "is not judged for a pick-up, the fix reporting $fix, so $kept"
        }
        is WayToEndBecame.ByTrail -> "goes by trail: ${because.words}"
        is WayToEndBecame.Refused -> "is refused (${note.name})"
        WayToEndBecame.NotReached -> "is not reached, the waypoint's end being refused first"
    }
}

/** The dispatch's own words for each rank (2026-11-05-07, A1). */
private fun StreetDropOff.Rank.words(): String = when (this) {
    StreetDropOff.Rank.ACCESS_WAY -> "the private way's mouth"
    StreetDropOff.Rank.ADDRESS_STREET -> "the addressed street"
    StreetDropOff.Rank.FRONTAGE -> "the nearest public street"
}
