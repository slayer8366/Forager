package com.zynergylabs.forager.app.ui.map.layers

/**
 * One feature a tap query returned: the layer it was queried on and the feature's own id (the
 * `featureId` property the pure builders write, or a sighting's `observationId`); `null` when the
 * feature carries none.
 */
data class TapHit(val layerId: String, val featureId: String?)

/**
 * The side of the square box, in dp, that a tap queries when nothing lies exactly under the tap
 * point (owner's ruling 4 on 2026-09-27-30: a finger-sized box, so thin lines are hittable).
 */
const val TAP_BOX_DP = 48f

/** The layers of [drawOrder] a tap is queried against: every layer whose [TapGroup] has a precedence. */
fun tappableLayerIds(drawOrder: List<MapLayerSpec>): List<String> =
    drawOrder.filter { it.tapGroup.precedence != null }.map { it.id }

/**
 * Which of [hits] a tap goes to (L0 design ruling 3): markers first, then lines and area outlines,
 * then colour fields; within a group, the layer drawn on top in [drawOrder]; within one layer, the
 * first hit the query returned. `null` when no hit is on a tappable layer of [drawOrder].
 */
fun tapWinner(hits: List<TapHit>, drawOrder: List<MapLayerSpec>): TapHit? {
    val heightOf = drawOrder.withIndex().associate { it.value.id to it.index }
    val precedenceOf = drawOrder.associate { it.id to it.tapGroup.precedence }
    // minWithOrNull keeps the first of equal elements, which is the "first hit within one layer" rule.
    return hits
        .filter { precedenceOf[it.layerId] != null }
        .minWithOrNull(
            compareBy<TapHit> { precedenceOf.getValue(it.layerId) }
                .thenByDescending { heightOf.getValue(it.layerId) },
        )
}

/**
 * A tap resolved in two stages: [pointHits], what lies exactly under the tap point, decides when any
 * marker or line is there; only when none is does [boxHits] run (the finger-sized box, [TAP_BOX_DP]).
 *
 * **Colour fields (M1).** A cell lies under nearly every tap, so it is held back until both stages
 * have found no marker or line: it wins only when it is under the finger and nothing else is at the
 * point or in the box (planner's M1 ruling, "a group that loses to any marker or line within the
 * box", and the ruling on Q5: a cell counts only at the point stage and never wins through the box,
 * so a tap on an empty cell beside a scored one reads out nothing).
 */
fun resolveTap(pointHits: List<TapHit>, boxHits: () -> List<TapHit>, drawOrder: List<MapLayerSpec>): TapHit? {
    val cellLayers = drawOrder.filter { it.tapGroup == TapGroup.COLOUR_FIELD }.mapTo(HashSet()) { it.id }
    val (pointCells, pointOthers) = pointHits.partition { it.layerId in cellLayers }
    return tapWinner(pointOthers, drawOrder)
        ?: tapWinner(boxHits().filterNot { it.layerId in cellLayers }, drawOrder)
        ?: tapWinner(pointCells, drawOrder)
}

/**
 * What one resolved tap does (M1; owner's ruling 1, "Bubble only"): a sighting goes to
 * `onSightingTap` (the host's plain `onTap` when its id no longer resolves, as before); any other
 * winner with an id goes to `onFeatureTap` and **nothing else**, as sighting taps already behave; a
 * tap on nothing tappable is a plain `onTap`. A winner with no id cannot name its record: it is
 * logged and treated as a plain tap.
 */
sealed interface MapTapOutcome {
    data class OnSighting(val observationId: Long?) : MapTapOutcome
    data class OnFeature(val layerId: String, val featureId: String) : MapTapOutcome
    data class UnidentifiedFeature(val layerId: String) : MapTapOutcome
    data object Plain : MapTapOutcome
}

/** [winner]'s [MapTapOutcome]; see that type. */
fun mapTapOutcome(winner: TapHit?): MapTapOutcome = when {
    winner == null -> MapTapOutcome.Plain
    winner.layerId == MapLayerIds.SIGHTINGS -> MapTapOutcome.OnSighting(winner.featureId?.toLongOrNull())
    winner.featureId == null -> MapTapOutcome.UnidentifiedFeature(winner.layerId)
    else -> MapTapOutcome.OnFeature(winner.layerId, winner.featureId)
}
