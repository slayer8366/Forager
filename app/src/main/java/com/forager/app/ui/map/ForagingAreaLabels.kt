package com.forager.app.ui.map

import com.forager.app.domain.model.ForagingArea

/**
 * The single wording of the connector disclaimer.
 *
 * Both the caption under the map and the dashed polyline's own info window read from here, so
 * the two can never drift apart and leave one of them implying a walking route. The dashed line
 * style and this sentence are the whole honesty mechanism for the ordering feature — see
 * [com.forager.app.domain.ClusterForagingAreasUseCase] for why a drawn path is not shipped.
 */
const val VISITING_ORDER_DISCLAIMER: String =
    "Dashed lines show visiting order only: straight-line proximity between area centres, " +
        "not a walking route. This app has no trail, terrain, or land-access data, so how you " +
        "actually get between these areas — or whether you may — is not something it can tell you."

/**
 * One-line summary of what an area has produced: the three facts that actually inform the
 * decision. Six species beats twelve of the same thing, and an area whose newest hit is 2011 is
 * a different proposition from one with hits last season.
 *
 * No pluralisation of "observations" because an area always holds at least
 * [com.forager.app.domain.ClusterForagingAreasUseCase.MIN_OBSERVATIONS_PER_AREA] of them.
 */
fun foragingAreaSummary(area: ForagingArea): String {
    val recency = area.mostRecentYear
        ?.let { year -> "most recent $year" }
        // Never guess a year for undated observations; say plainly that there isn't one.
        ?: "no dated observations"
    val undatedNote = if (area.mostRecentYear != null && area.undatedObservationCount > 0) {
        " (${area.undatedObservationCount} undated)"
    } else {
        ""
    }
    val species = if (area.distinctSpeciesCount == 1) "1 species" else "${area.distinctSpeciesCount} species"
    return "${area.observationCount} observations · $species · $recency$undatedNote"
}
