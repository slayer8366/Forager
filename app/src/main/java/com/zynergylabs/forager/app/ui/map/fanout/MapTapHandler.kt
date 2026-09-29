package com.zynergylabs.forager.app.ui.map.fanout

import com.zynergylabs.forager.app.domain.model.LatLng
import com.zynergylabs.forager.app.ui.map.layers.MapLayerSpec
import com.zynergylabs.forager.app.ui.map.layers.MapTapOutcome
import com.zynergylabs.forager.app.ui.map.layers.TapHit
import com.zynergylabs.forager.app.ui.map.layers.mapTapOutcome
import com.zynergylabs.forager.app.ui.map.layers.resolveTap
import com.zynergylabs.forager.app.ui.map.layers.tappableLayerIds

/**
 * What the map SDK is asked at a tap, behind an interface this project owns so the tap logic does
 * not depend on the vendor's query API. All positions are screen px, in the map view's own
 * coordinates. `SightingsMap` implements it over `MapLibreMap.queryRenderedFeatures` and its
 * projection; the tests implement it over a projection of their own.
 */
interface MapProbe {
    val density: Float

    /** What the given layers draw exactly at the point. */
    fun hitsAt(xPx: Float, yPx: Float, layerIds: List<String>): List<TapHit>

    /** What the given layers draw within a square of half-side [halfPx] about the point. */
    fun hitsInBox(xPx: Float, yPx: Float, halfPx: Float, layerIds: List<String>): List<TapHit>

    /** The markers of the given layers whose own position is within a square of half-side [halfPx] about the point. */
    fun markersInBox(xPx: Float, yPx: Float, halfPx: Float, layerIds: List<String>): List<ProbedMarker>
}

/**
 * Where a tap goes once decided: the four outcomes the click listener always had ([MapTapOutcome]),
 * with the tap's position in px. The host turns each into its own callback.
 */
interface MapTapSinks {
    fun onPlainTap()
    fun onSightingTap(observationId: Long?, xPx: Float, yPx: Float)
    fun onFeatureTap(layerId: String, featureId: String, xPx: Float, yPx: Float, at: LatLng)
    fun onUnidentifiedFeature(layerId: String)
}

/**
 * A map tap, decided. `SightingsMap`'s click listener hands every tap to [onMapTap], which is the old
 * resolution (`resolveTap`, then `mapTapOutcome`, unchanged) with the fan-out in front of and behind it:
 *
 *  - **A fan is open and the tap is on a fanned marker:** that marker's own outcome, as a tap on it
 *    would have been (the owner's rule 4). The fan stays open behind its bubble.
 *  - **A fan is open and the tap is anywhere else:** the fan folds, and the tap goes on as it would
 *    have (a plain tap dismisses a bubble, a tap on another marker opens that one).
 *  - **The tap resolves to a marker whose touch area overlaps another's** (a stack, rule 1): the stack
 *    fans out and nothing else is reported (rule 2).
 *
 * The camera moving, or what the map draws changing, also folds it ([onCameraMoveStarted],
 * [onContentChanged]).
 */
class MapTapHandler(
    private val fan: MarkerFanOutState,
    private val probe: MapProbe,
    private val drawOrder: () -> List<MapLayerSpec>,
    private val sinks: MapTapSinks,
    private val space: FanSpace = FanSpace.Unbounded,
) {
    fun onMapTap(at: LatLng, xPx: Float, yPx: Float) {
        val density = probe.density
        if (fan.isOpen) {
            val picked = fanMemberAt(fan.members, fan.progress, xPx / density, yPx / density)
            if (picked != null) {
                dispatch(mapTapOutcome(TapHit(picked.key.layerId, picked.key.featureId)), at, xPx, yPx)
                return
            }
            fan.fold()
        }

        val order = drawOrder()
        val tappable = tappableLayerIds(order)
        val winner = resolveTap(
            pointHits = probe.hitsAt(xPx, yPx, tappable),
            boxHits = { probe.hitsInBox(xPx, yPx, TAP_BOX_HALF_DP * density, tappable) },
            drawOrder = order,
        )
        if (winner != null && openStackAround(winner, order, xPx, yPx)) return
        dispatch(mapTapOutcome(winner), at, xPx, yPx)
    }

    /** The camera started to move, by a gesture or by the app: the copies are placed in screen space, so the fan folds. */
    fun onCameraMoveStarted() = fan.fold()

    /** What the map draws changed (its records, its layer switches, its style): the fan folds. */
    fun onContentChanged() = fan.fold()

    private fun FanRect.scaled(by: Float) = FanRect(left * by, top * by, right * by, bottom * by)

    private fun openStackAround(winner: TapHit, order: List<MapLayerSpec>, xPx: Float, yPx: Float): Boolean {
        // Only the owner's own records fan (the owner's "1 A"): a sighting dot is not in this list, so a tap that
        // resolves to one, and a dot stacked under records, are left as they were before the fan-out.
        val markerLayers = fanOutLayerIds(order)
        if (winner.layerId !in markerLayers || winner.featureId == null) return false
        val density = probe.density
        val nearby = probe.markersInBox(xPx, yPx, STACK_QUERY_HALF_DP * density, markerLayers)
        val self = nearby.firstOrNull { it.key == FanKey(winner.layerId, winner.featureId) } ?: return false
        val stack = stackOf(self, nearby, density)
        if (stack.size < 2) return false

        val heightOf = order.withIndex().associate { it.value.id to it.index }
        val topFirst = stack.sortedWith(compareByDescending<ProbedMarker> { heightOf.getValue(it.key.layerId) }.thenBy { it.key.featureId })
        // The ring is about the stack's centre, so its markers are a touch size apart whatever their true spots (each
        // within a touch size of the tapped one), and it is then shifted as a whole to stay on screen and off the
        // controls; each member's displacement is from its own true position to its place, and the true positions
        // (where the legs end) do not move.
        val trueDp = topFirst.map { (it.xPx / density) to (it.yPx / density) }
        val centreX = trueDp.map { it.first }.average().toFloat()
        val centreY = trueDp.map { it.second }.average().toFloat()
        val ring = fanOffsets(topFirst.size)
        val placement = fanShift(
            centreX, centreY, ring,
            bounds = space.boundsPx()?.let { it.scaled(1f / density) },
            keepOuts = space.keepOutsPx().map { it.scaled(1f / density) },
        )
        fan.open(
            topFirst.mapIndexed { i, m ->
                val (trueX, trueY) = trueDp[i]
                val offset = FanOffset(
                    centreX + ring[i].xDp + placement.shift.xDp - trueX,
                    centreY + ring[i].yDp + placement.shift.yDp - trueY,
                )
                FanMember(m.key, m.lat, m.lng, trueX, trueY, offset)
            },
        )
        return true
    }

    private fun dispatch(outcome: MapTapOutcome, at: LatLng, xPx: Float, yPx: Float) {
        when (outcome) {
            is MapTapOutcome.OnSighting -> sinks.onSightingTap(outcome.observationId, xPx, yPx)
            is MapTapOutcome.OnFeature -> sinks.onFeatureTap(outcome.layerId, outcome.featureId, xPx, yPx, at)
            is MapTapOutcome.UnidentifiedFeature -> {
                sinks.onUnidentifiedFeature(outcome.layerId)
            }
            MapTapOutcome.Plain -> sinks.onPlainTap()
        }
    }

    private companion object {
        /** The box `resolveTap` queries when nothing lies exactly under the tap: `TAP_BOX_DP` across. */
        const val TAP_BOX_HALF_DP = com.zynergylabs.forager.app.ui.map.layers.TAP_BOX_DP / 2f

        /**
         * How far from the tap to ask for markers when looking for a stack: a marker whose touch area
         * overlaps the tapped one's is under [FAN_TOUCH_DP] from it, and the tapped one is at most a
         * glyph's height (under 32 dp) from the tap, so twice the touch size reaches all of them.
         */
        const val STACK_QUERY_HALF_DP = 2 * FAN_TOUCH_DP
    }
}
