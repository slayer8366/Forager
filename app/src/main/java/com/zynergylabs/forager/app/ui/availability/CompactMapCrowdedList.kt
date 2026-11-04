package com.zynergylabs.forager.app.ui.availability

import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.zynergylabs.forager.app.ui.format.is24HourClock
import com.zynergylabs.forager.app.ui.map.AnchoredAtScreenPoint
import com.zynergylabs.forager.app.ui.map.BUBBLE_TAIL_LENGTH
import com.zynergylabs.forager.app.ui.map.CROWDED_BUBBLE_MAX_CARD_HEIGHT
import com.zynergylabs.forager.app.ui.map.CrowdedListRow
import com.zynergylabs.forager.app.ui.map.CrowdedStackBubble
import com.zynergylabs.forager.app.ui.map.MapFeatureTap
import com.zynergylabs.forager.app.ui.map.MapRecordSources
import com.zynergylabs.forager.app.ui.map.MapRenderMode
import com.zynergylabs.forager.app.ui.map.TappedMapThing
import com.zynergylabs.forager.app.ui.map.crowdedListRows
import com.zynergylabs.forager.app.ui.map.fanout.CrowdedList
import com.zynergylabs.forager.app.ui.map.fanout.CrowdedStackBackHandlers
import com.zynergylabs.forager.app.ui.map.fanout.CrowdedStackHost
import com.zynergylabs.forager.app.ui.motion.MapPopUp
import com.zynergylabs.forager.app.ui.motion.rememberLastShown

/**
 * The Maps tab's crowded-stack list and its Back steps (dispatch 2026-10-27-07, steps 3 and 4), as a floating bubble since
 * dispatch 2026-11-04-02 (RECORD -2039, -2040). The bubble shows while [host] holds a list and no bubble does ([tapped]).
 *
 * **Anchored to the stack** (A1): its tail's tip on the list's anchor, the tapped marker, through the standard bubble's
 * [AnchoredAtScreenPoint], and moved there again as the map moves ([CrowdedStackHost.followList], called where a record's bubble
 * is re-anchored). Clamped as the standard bubble is: below [minY] (the search bar and the strip), clear of the rail; and, on
 * the cluster's side, clear of the cluster's column in both orientations (S6), [clusterInset], as the old list was (its rows
 * took touches meant for the cluster on CI, run 40580543726). The route card, the navigation card and Re-center are composed
 * after it and draw over it where they meet, as they do over the standard bubble (S6, the standard rule).
 *
 * **No taller than [CROWDED_BUBBLE_MAX_CARD_HEIGHT]** (S1), nor than the room between [minY] and the bottom of the map less the
 * controls' padding and [MapRenderMode.bottomInset]; the rows scroll inside.
 *
 * **Opens and shuts with the standard bubble's motion** (A5, S4): [MapPopUp], fading and growing from the stack, and fading
 * alone under Reduce Motion. The row-to-record switch is the same motion: the list leaves as the record's bubble comes, both
 * about the same point.
 *
 * **Closing** (A2, S3): X, Back, or a tap on the map closes it. A row opens its record's bubble "as from the map", through the
 * same [onFeatureTap] a map tap uses, pointing at the stack's spot; while that bubble shows the list waits under it. From there
 * Back closes the bubble and returns to the list (the handler here, composed after the bubble's, so asked first); the bubble's
 * X, a map tap and its actions close both (the tab closes the list with the bubble, and the map's tap handler closes the list).
 */
@Composable
internal fun BoxScope.CompactMapCrowdedList(
    host: CrowdedStackHost,
    tapped: TappedMapThing?,
    bubbleSources: MapRecordSources,
    renderMode: MapRenderMode,
    onFeatureTap: (MapFeatureTap) -> Unit,
    /** Closes the row's bubble alone, for Back's return to the list. */
    onCloseBubbleOnly: () -> Unit,
    controlsPadding: PaddingValues,
    cluster: MapIconClusterState,
    /** The cluster's column on its side: its edge inset, its measured width and the gap beside it (`noticeInsetDp`). */
    clusterInset: Dp,
    minY: Dp,
    backEnabled: Boolean,
) {
    val listed = host.list
    val is24 = is24HourClock()
    val rows = remember(listed, bubbleSources, is24) {
        listed?.let { crowdedListRows(it.keys, bubbleSources, is24, warn = { message -> Log.w(CROWDED_LIST_LOG_TAG, message) }) } ?: emptyList()
    }
    if (listed != null && rows.isEmpty()) {
        // Every record went while the list was up: nothing to list, said so, and the list closes.
        LaunchedEffect(listed) {
            Log.w(CROWDED_LIST_LOG_TAG, "Crowded list: none of its ${listed.keys.size} records is there any more; it closes.")
            host.closeList()
        }
    }
    val resolved = if (listed != null && rows.isNotEmpty() && tapped == null) ShownList(listed, rows) else null
    // What showed, kept while it leaves (MapPopUp's own note).
    val shown = rememberLastShown(resolved)
    val direction = LocalLayoutDirection.current
    val clusterOnLeft = cluster.isOnLeftSide
    val insetLeft = controlsPadding.calculateLeftPadding(direction) + if (clusterOnLeft) clusterInset else 0.dp
    val insetRight = controlsPadding.calculateRightPadding(direction) + if (clusterOnLeft) 0.dp else clusterInset
    val bottom = controlsPadding.calculateBottomPadding() + renderMode.bottomInset
    MapPopUp(
        visible = resolved != null,
        pivot = { shown?.let { Offset(it.list.anchorXPx, it.list.anchorYPx) } ?: Offset.Zero },
        modifier = Modifier.fillMaxSize(),
    ) {
        shown?.let { bubble ->
            // The box the bubble is clamped into stops at the controls' bottom padding, so its card is never under the bar;
            // its top is the map's, so the anchor's coordinates are the map's own.
            BoxWithConstraints(Modifier.fillMaxSize().padding(bottom = bottom)) {
                val room = maxHeight - minY - BUBBLE_TAIL_LENGTH * 2
                val cap = minOf(CROWDED_BUBBLE_MAX_CARD_HEIGHT, room).coerceAtLeast(0.dp)
                AnchoredAtScreenPoint(
                    anchorPx = Offset(bubble.list.anchorXPx, bubble.list.anchorYPx),
                    bearingDeg = bubble.list.bearingDeg,
                    modifier = Modifier.fillMaxSize(),
                    minY = minY,
                    insetLeft = insetLeft,
                    insetRight = insetRight,
                ) { tip ->
                    CrowdedStackBubble(
                        rows = bubble.rows,
                        night = renderMode.night,
                        tipInBubble = tip,
                        maxCardHeight = cap,
                        onRow = { key ->
                            onFeatureTap(MapFeatureTap(key.layerId, key.featureId, Offset(bubble.list.anchorXPx, bubble.list.anchorYPx), bubble.list.bearingDeg, bubble.list.at))
                        },
                        onDismiss = { host.closeList() },
                    )
                }
            }
        }
    }
    // S3: from a row's bubble, Back returns to the list. Composed after the bubble's own Back (CompactMapBubble), so asked first.
    if (listed != null && tapped != null && backEnabled) {
        BackHandler { onCloseBubbleOnly() }
    }
    CrowdedStackBackHandlers(host, bubbleShown = tapped != null, backEnabled = backEnabled)
}

/** A list with its rows, as it is shown. */
private data class ShownList(val list: CrowdedList, val rows: List<CrowdedListRow>)

private const val CROWDED_LIST_LOG_TAG = "CrowdedStackList"
