package com.zynergylabs.forager.app.ui.map

import android.util.Log
import androidx.compose.ui.geometry.Offset
import com.zynergylabs.forager.app.domain.model.RecordPoint
import com.zynergylabs.forager.app.ui.map.fanout.FanKey
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

    private var request: MapReturnRequest? = null
    private var bubbleRestore: MapReturnRequest? = null
    private var fanRestore: List<FanKey>? = null

    /** Remembers that [findId] was opened from the map, with the fan that was open then and the bubble's anchor. */
    fun remember(findId: String, anchorPx: Offset, bearingDeg: Float) {
        request = MapReturnRequest(findId, openFanKeys.toList(), anchorPx, bearingDeg)
    }

    /** The user left the find any other way (another tab, an edit, another record): Back does what it did before. */
    fun forget() {
        request = null
        entryRequest = null
    }

    /**
     * The find [findId]'s report was closed (Back or its own arrow). `true` when it is the remembered one: the
     * request is then handed to the Maps tab that is about to be created. A different find's close forgets the
     * request (it belongs to a record the user has since left).
     */
    fun onFindClosed(findId: String): Boolean {
        val remembered = request ?: return false
        request = null
        if (remembered.findId != findId) return false
        bubbleRestore = remembered
        fanRestore = remembered.fanKeys.takeIf { it.isNotEmpty() }
        return true
    }

    /** The find [findId] was deleted from its page: the same return, without its bubble, and a fan that no longer counts it. */
    fun onFindDeleted(findId: String): Boolean {
        val remembered = request ?: return false
        request = null
        if (remembered.findId != findId) return false
        bubbleRestore = null
        fanRestore = remembered.fanKeys.filterNot { it.layerId == MapLayerIds.FINDS && it.featureId == findId }.takeIf { it.isNotEmpty() }
        return true
    }

    // The way back from a journal entry opened from a bubble's "kept in" line (dispatch 2026-09-28-387, Part B). A path of its own beside the find's, so a find
    // closing can never clear it and the find's flow is untouched. What comes back is decided in [onEntryClosed]: the bubble that was showing and the fan that
    // was open, together, as for a find (the owner: "back out one step at a time, going exactly the way they came").

    private class EntryReturn(val entryId: String, val bubble: TappedMapThing, val fanKeys: List<FanKey>)

    private var entryRequest: EntryReturn? = null
    private var entryBubbleRestore: TappedMapThing? = null

    /** Remembers that [entryId] was opened from [bubble], with the fan that was open then. */
    fun rememberEntryOpen(entryId: String, bubble: TappedMapThing) {
        entryRequest = EntryReturn(entryId, bubble, openFanKeys.toList())
    }

    /**
     * The entry [entryId]'s report was left. `true` when it is the remembered entry and it was left from its report ([fromReport]): the bubble and the fan are
     * then handed to the Maps tab that is about to be created. Left from its editor, the request is forgotten and nothing returns (as editing a find forgets its
     * origin). A different entry closing leaves the request alone: the Journal swaps the open entry when a "kept in" line is tapped over another one.
     */
    fun onEntryClosed(entryId: String, fromReport: Boolean): Boolean {
        val remembered = entryRequest ?: return false
        if (remembered.entryId != entryId) return false
        entryRequest = null
        if (!fromReport) return false
        entryBubbleRestore = remembered.bubble
        fanRestore = remembered.fanKeys.takeIf { it.isNotEmpty() }
        return true
    }

    /** The entry's bubble to reopen, once, if its record is still there ([sources] is what the bubble is read from); `null` and logged when not. */
    fun takeEntryBubble(sources: MapRecordSources): TappedMapThing? {
        val bubble = entryBubbleRestore ?: return null
        entryBubbleRestore = null
        val target = bubble.target as? MapBubbleTarget.FeatureTarget ?: return null
        // Only whether the record is still there is read, never the bubble's text, so the clock setting changes nothing here.
        if (mapBubbleContentFor(target, sources, is24HourClock = false) == null) {
            warn("${target.layerId}/${target.featureId} is no longer there; its bubble was not reopened.")
            return null
        }
        return bubble
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
        entryBubbleRestore = null
    }
}

private const val MAP_RETURN_LOG_TAG = "MapReturnMemory"
