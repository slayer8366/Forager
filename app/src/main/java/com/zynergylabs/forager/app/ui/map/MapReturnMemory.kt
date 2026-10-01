package com.zynergylabs.forager.app.ui.map

import android.util.Log
import androidx.compose.ui.geometry.Offset
import com.zynergylabs.forager.app.domain.model.RecordPoint
import com.zynergylabs.forager.app.ui.map.fanout.FanKey
import com.zynergylabs.forager.app.ui.map.fanout.FannedFrom
import com.zynergylabs.forager.app.ui.map.layers.MapLayerIds

/**
 * What the Maps tab is given back after a find opened from it is left with Back (dispatch 2026-09-29-57,
 * amendment -256 and, for the shape, amendment -262 item 8, the owner's "Remember and reopen").
 *
 * **Only a return request is remembered**, not the tab's state: the find's id, the keys of the fan that was
 * open (if one was), and the bubble's anchor and bearing, since a bubble cannot be placed without them (see the
 * report's Decisions). The camera is not here: it comes back through [MapCameraMemory], as it always did. The
 * bubble and the fan are rebuilt when the Maps tab is created again: the bubble from the find (still drawn or not),
 * the fan through `MapTapHandler.openFanFor`, with whichever members still exist.
 *
 * Plain Kotlin with no Compose or MapLibre type but an [Offset] (a value), so the rules are headless-testable
 * (`MapReturnMemoryTest`). Held by `AvailabilityScreen` beside its `MapCameraMemory`, so it outlives the tab.
 */
data class MapReturnRequest(
    val findId: String,
    /** The member keys of the fan that was open when "Open in Journal" was tapped, or empty. */
    val fanKeys: List<FanKey>,
    val anchorPx: Offset,
    val bearingDeg: Float,
)

class MapReturnMemory(
    /** Where a reopen that cannot happen is reported (logged, never silent); a test passes its own. */
    private val warn: (String) -> Unit = { message -> Log.w(MAP_RETURN_LOG_TAG, message) },
) {
    /** Written by the map while a fan is open (its members' keys), empty while none is; read when "Open in Journal" is tapped. */
    var openFanKeys: List<FanKey> = emptyList()

    /** The fan the tapped marker was just picked from, kept until the next map tap or the return (dispatch 2026-09-28-381); `null` when the last tap was not on a fanned icon. */
    var fannedFrom: FannedFrom? = null

    private var request: MapReturnRequest? = null
    private var bubbleRestore: MapReturnRequest? = null
    private var fanRestore: List<FanKey>? = null

    /** Remembers that [findId] was opened from the map, with the fan that was open then and the bubble's anchor. */
    fun remember(findId: String, anchorPx: Offset, bearingDeg: Float) {
        // A find picked from a fan: that fan folded on the tap, so the fan to bring back is the one it was picked from (dispatch 2026-09-28-381). Any
        // other find: the fan open now, if one is, as before.
        val pickedFrom = fannedFrom?.takeIf { it.tapped == FanKey(MapLayerIds.FINDS, findId) }?.members
        request = MapReturnRequest(findId, pickedFrom ?: openFanKeys.toList(), anchorPx, bearingDeg)
    }

    /** The user left the find any other way (another tab, an edit, another record): Back does what it did before. */
    fun forget() {
        request = null
        fannedFrom = null
    }

    /**
     * The find [findId]'s report was closed (Back or its own arrow). `true` when it is the remembered one: the
     * request is then handed to the Maps tab that is about to be created. A different find's close forgets the
     * request (it belongs to a record the user has since left).
     */
    fun onFindClosed(findId: String): Boolean {
        val remembered = request ?: return false
        request = null
        fannedFrom = null // used up: the fan that comes back is open again, and the next round reads it
        if (remembered.findId != findId) return false
        // What comes back is decided here: the find's bubble and its fan together, as the owner has it today (-262 item 8). Bringing back the fan alone
        // would be `bubbleRestore = null` when there are fan keys.
        bubbleRestore = remembered
        fanRestore = remembered.fanKeys.takeIf { it.isNotEmpty() }
        return true
    }

    /** The find [findId] was deleted from its page: the same return, without its bubble, and a fan that no longer counts it. */
    fun onFindDeleted(findId: String): Boolean {
        val remembered = request ?: return false
        request = null
        fannedFrom = null
        if (remembered.findId != findId) return false
        bubbleRestore = null
        fanRestore = remembered.fanKeys.filterNot { it.layerId == MapLayerIds.FINDS && it.featureId == findId }.takeIf { it.isNotEmpty() }
        return true
    }

    /** The fan keys waiting for the new map (read-only, for tests); `null` when none. */
    val pendingFanKeys: List<FanKey>? get() = fanRestore

    /** The bubble to reopen, once, from the find's own marker in [findMarkers]; `null` when there is none or the find is no longer drawn. */
    fun takeBubble(findMarkers: List<RecordPoint>): TappedMapThing? {
        val remembered = bubbleRestore ?: return null
        bubbleRestore = null
        val at = findMarkers.firstOrNull { it.recordId == remembered.findId }?.at
        if (at == null) {
            warn("Find ${remembered.findId} is not drawn on the map; its bubble was not reopened.")
            return null
        }
        return TappedMapThing(
            MapBubbleTarget.FeatureTarget(MapBubbleKind.FIND, MapLayerIds.FINDS, remembered.findId, at),
            remembered.anchorPx,
            remembered.bearingDeg,
        )
    }

    /** The fan keys to reopen, once; `null` when none. */
    fun takeFanKeys(): List<FanKey>? {
        val keys = fanRestore
        fanRestore = null
        return keys
    }

    /** The Maps tab left composition without using what was waiting: it does not keep it for a later map. */
    fun clearRestore() {
        bubbleRestore = null
        fanRestore = null
    }
}

private const val MAP_RETURN_LOG_TAG = "MapReturnMemory"
