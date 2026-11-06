package com.zynergylabs.forager.app.domain.mapdata

import com.zynergylabs.forager.app.domain.model.GeoBoundingBox

/**
 * Whether the Trail Map has its detail across an area: what the Offline Maps picker's warning is decided by (known bug 14,
 * dispatch 2026-11-02-02, Amendments 2 and 3). The owner, RECORD -2144, chose "The map server (Recommended)", whose option
 * read in part "When the pin settles and there's signal, the app asks the map server whether it has detailed map there. The
 * warning shows wherever it doesn't [...] It stays right whenever the map is rebuilt. With no signal it can't ask, so no
 * warning, logged."; and for an area partly inside, "If any part is outside".
 *
 * An interface this project owns (CLAUDE.md, Architecture), so the picker's warning is decided and tested without the
 * network. The real one is `data/mapdetail/WorkerTrailMapDetail`; [NoTrailMapDetail] is the explicit "unsupported".
 */
interface TrailMapDetail {
    /**
     * What this session's earlier answers already settle for [area], asking nothing: [AreaDetail.SomeOutside] when a tile of
     * it is already known to have no detail, [AreaDetail.AllInside] when every tile is already known to have it (or to be
     * sea), and `null` otherwise. Never [AreaDetail.NotRun]: a reason not to run is [check]'s to give, once, after the wait.
     */
    fun known(area: GeoBoundingBox): AreaDetail? = null

    /** Asks the map server about [area]. Never throws but for cancellation: every outcome is an [AreaDetail]. */
    suspend fun check(area: GeoBoundingBox): AreaDetail
}

/** One area's answer. */
sealed interface AreaDetail {
    /** Every tile of the area has the map's detail, or is sea, which counts neither way (A7). No line. */
    data object AllInside : AreaDetail

    /** At least one tile has no detail: some of a download here would be blank (the owner, -2144: "If any part is outside"). */
    data object SomeOutside : AreaDetail

    /** No answer, for [reason]. No line, and the reason is logged: never a guess (A6). */
    data class NotRun(val reason: String) : AreaDetail
}

/** A build or a test with no map server check: every area is not run, and says why. */
object NoTrailMapDetail : TrailMapDetail {
    override suspend fun check(area: GeoBoundingBox): AreaDetail = AreaDetail.NotRun("this build has no map server check")
}
