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

    /** The markers named by [keys] that the map still draws, at their positions now; a key no longer drawn is left out. */
    fun markersOf(keys: List<FanKey>): List<ProbedMarker>
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

    /** A tap on a stack closes the bubble that is showing (dispatch 2026-09-28-387, Part A). Not the plain tap: that one also leaves fullscreen. */
    fun onCloseBubble()
}

/**
 * A map tap, decided. `SightingsMap`'s click listener hands every tap to [onMapTap], which is the old
 * resolution (`resolveTap`, then `mapTapOutcome`, unchanged) with the fan-out in front of and behind it:
 *
 *  - **A fan is open and the tap is on a fanned marker:** that marker's own outcome, as a tap on it
 *    would have been (the owner's choice, dispatch 2026-09-28-197). The fan stays open behind its bubble.
 *  - **A fan is open and the tap is anywhere else:** the fan folds, and the tap goes on as it would
 *    have (a plain tap dismisses a bubble, a tap on another marker opens that one). **Except** while a
 *    bubble is showing ([bubbleOpen]) and the tap is on empty map: that tap closes the bubble only and
 *    the fan stays, so the next empty tap folds it (dispatch 2026-09-29-57, item 7, amendment -255).
 *  - **The tap resolves to a marker whose touch area overlaps another's** (a stack): the stack
 *    fans out (the owner's choice, dispatch 2026-09-28-197). If a bubble is showing, it is closed (dispatch 2026-09-28-387, Part A, [MapTapSinks.onCloseBubble]);
 *    nothing else is reported.
 *
 * The camera moving folds it unless it is the map following the location ([onCameraMoveStarted]); what the map draws changing, a new style included, folds it only
 * when a member changed ([onContentChanged]).
 */
class MapTapHandler(
    private val fan: MarkerFanOutState,
    private val probe: MapProbe,
    private val drawOrder: () -> List<MapLayerSpec>,
    private val sinks: MapTapSinks,
    private val space: FanSpace = FanSpace.Unbounded,
    private val bubbleOpen: () -> Boolean = { false },
    /** Whether a layer's switch is on. A record on a layer switched off is not drawn, so it is not a fan member. */
    private val layerDrawn: (String) -> Boolean = { true },
    /** Where a fallback is reported, never silent: the host passes `Log.w`; a test passes its own. */
    private val warn: (String) -> Unit = {},
) {
    fun onMapTap(at: LatLng, xPx: Float, yPx: Float) {
        val density = probe.density
        var holdFanForEmptyTap = false
        val bubbleWasUp = bubbleOpen()
        if (fan.isOpen) {
            val picked = fanMemberAt(liveMembers(), fan.progress, xPx / density, yPx / density)
            if (picked != null) {
                dispatch(mapTapOutcome(TapHit(picked.key.layerId, picked.key.featureId)), at, xPx, yPx)
                return
            }
            // One layer at a time (amendment -255): with a bubble showing, a tap on empty map closes the bubble and
            // leaves the fan, which the next one folds. A tap that lands on something else folds the fan as it did.
            holdFanForEmptyTap = bubbleOpen()
            if (!holdFanForEmptyTap) fan.fold()
        }

        val order = drawOrder()
        val tappable = tappableLayerIds(order)
        val winner = resolveTap(
            pointHits = probe.hitsAt(xPx, yPx, tappable),
            boxHits = { probe.hitsInBox(xPx, yPx, TAP_BOX_HALF_DP * density, tappable) },
            drawOrder = order,
        )
        if (holdFanForEmptyTap && winner != null) fan.fold()
        if (winner != null && openStackAround(winner, order, xPx, yPx)) {
            // A tap on a stack closes a bubble that is showing, as the fan opens (the owner, dispatch 2026-09-28-387, Part A: "1 yes"). By the bubble's
            // own sink, not the plain tap, which also leaves fullscreen. Before this the bubble stayed, now beside the new fan.
            if (bubbleWasUp) sinks.onCloseBubble()
            return
        }
        dispatch(mapTapOutcome(winner), at, xPx, yPx)
    }

    /**
     * Opens a fan over the markers [keys] name, as the one they were in when the user left the map (dispatch 2026-09-29-57,
     * item 8: Back from a find opened on the map). A key whose marker is no longer there is dropped; fewer than two left
     * opens nothing. `true` when a fan opened.
     */
    fun openFanFor(keys: List<FanKey>): Boolean {
        val stack = drawnMarkersOf(keys)
        if (stack.size < 2) return false
        openFan(stack, drawOrder())
        return true
    }

    /**
     * The markers [keys] name that the map draws now: a key whose layer does not fan, or is switched off, or whose record
     * is no longer drawn, is left out (the last is [MapProbe.markersOf]'s own; a record is never guessed at).
     */
    private fun drawnMarkersOf(keys: List<FanKey>): List<ProbedMarker> {
        val fanLayers = fanOutLayerIds(drawOrder())
        return probe.markersOf(keys.filter { it.layerId in fanLayers && layerDrawn(it.layerId) }).distinctBy { it.key }
    }

    /**
     * The camera started to move. The user's touch, or a move the app made because the user asked, folds the fan; the map re-centring itself while it
     * follows the location ([CameraMoveCause.LOCATION_FOLLOW]) does not: the user did not mean it, and the fan travels with the map (its copies are
     * placed from where the markers are, and its touch areas are read from the same places, [liveMembers]). A fan that is carried off screen is left
     * alone; Back still folds it (dispatch 2026-09-28-380).
     */
    fun onCameraMoveStarted(cause: CameraMoveCause = CameraMoveCause.UNKNOWN) {
        if (cause == CameraMoveCause.LOCATION_FOLLOW) return
        fan.fold()
    }

    /**
     * What the map draws changed (its records, its layer switches): the fan folds only if its members changed (intent
     * 2026-09-28-274, the owner's "Fold only if members change"). A fan whose members are all still drawn, where they
     * were, is left alone, not even reopened; if some are gone it is opened again over the survivors, through
     * [openFanFor]; if fewer than two remain, or a member moved, the rule is the same one: the survivors, at their
     * current places, or nothing. A switched-off layer takes its members out, and folds the fan when none are left.
     */
    fun onContentChanged() {
        if (!fan.isOpen) {
            fan.fold()
            return
        }
        val members = fan.members
        val drawn = drawnMarkersOf(members.map { it.key })
        val unchanged = drawn.size == members.size && drawn.all { d -> members.any { it.key == d.key && it.lat == d.lat && it.lng == d.lng } }
        if (unchanged) return
        if (!openFanFor(drawn.map { it.key })) fan.fold()
    }

    /**
     * The open fan's members with their markers' places now. A member's stored place is where its marker was when the fan opened; the map may have moved
     * since (the location follower carries it), and the copies are drawn from where the markers are, so a tap is tested against the same places. The
     * marker is located from its record's coordinates through the projection ([MapProbe.markersOf]), not from what is drawn, so a hidden original is found.
     * A member the map cannot locate keeps its stored place and the host is told.
     */
    private fun liveMembers(): List<FanMember> {
        val located = probe.markersOf(fan.members.map { it.key }).associateBy { it.key }
        return fan.members.map { member ->
            val now = located[member.key]
            if (now == null) {
                warn("Fan member ${member.key.layerId}/${member.key.featureId} is not located on the map; its touch area stays where it was when the fan opened.")
                member
            } else {
                member.copy(trueXDp = now.xPx / probe.density, trueYDp = now.yPx / probe.density)
            }
        }
    }

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

        openFan(stack, order)
        return true
    }

    /** Fans [stack] (at least two markers), top layer first, from where they are on screen now. */
    private fun openFan(stack: List<ProbedMarker>, order: List<MapLayerSpec>) {
        val density = probe.density
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
