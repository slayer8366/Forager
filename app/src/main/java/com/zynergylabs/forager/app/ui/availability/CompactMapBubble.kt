package com.zynergylabs.forager.app.ui.availability

// Moved unchanged from AvailabilityCompactMapUi.kt (dispatch 2026-10-19-14, UI split Phase 1, PR 9a).

import androidx.compose.foundation.layout.BoxScope
import com.zynergylabs.forager.app.ui.map.WaypointNavigationOrigin
import com.zynergylabs.forager.app.ui.log.RecordDetailsTarget
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.zynergylabs.forager.app.ui.map.MapBubbleLayer
import com.zynergylabs.forager.app.ui.map.MapReturnMemory
import com.zynergylabs.forager.app.ui.map.MapRecordSources
import com.zynergylabs.forager.app.ui.map.TappedMapThing
import com.zynergylabs.forager.app.ui.map.MapRenderMode

/**
 * The tapped thing's bubble, moved out of [CompactMapTab] unchanged (RECORD -741): see that function's comments where it is called.
 * Its [minY] and [backEnabled] are worked out there, from state the tab owns.
 */
@Composable
internal fun BoxScope.CompactMapBubble(
    tapped: TappedMapThing?,
    onCloseBubble: () -> Unit,
    bubbleSources: MapRecordSources,
    returnMemory: MapReturnMemory,
    renderMode: MapRenderMode,
    controlsPadding: PaddingValues,
    cluster: MapIconClusterState,
    noticeInsetDp: Dp,
    minY: Dp,
    backEnabled: Boolean,
    navigation: CompactMapNavigation,
) {
    val context = LocalContext.current
    val landscapeCluster = cluster.landscape
    val waypointReopen = navigation.waypointReopen
    val onWaypointReopenConsumed = navigation.onWaypointReopenConsumed
    // Dispatch 2026-09-28-535 (RECORD -534): no label under the dot. The owner: "the bubble message is
    // repeating what the strip says. One has to go, and my vote is for the bubble message." The strip
    // below, or the HUD while navigating, carries the words.
    // minY = compassStripClearance, a real measurement of the strip's own type style: the
    // strip is composed after this in the same Box (so its own controls win any overlap)
    // and is full-width against the map's top edge, so a glyph tapped near the top would
    // otherwise anchor a bubble under that strip's band, where its taps (the close button's
    // included) would never reach it (CLAUDE.md, the Surface pitfall). See
    // compassStripClearance's own comment for why it is a one-time text measurement.
    //
    // Back closes the bubble (M1), except while something above the map owns Back: the
    // Tools drawer (intent 2026-09-28-28's precedence), the add menu or a picker.
    // The bubble's clamp box stops short of the rail (the measured width, in controlsPadding) and, on its
    // side, of the L: the value already worked out for the search notice above, 8 + the L's measured width + 8,
    // and inside the rail's own padding because the L is (MapIconCluster applies controlsPadding first).
    // Only in the landscape L; portrait's cluster is a column at the edge and keeps the old clamp.
    val bubbleLayoutDirection = LocalLayoutDirection.current
    val bubbleClusterInset = if (landscapeCluster) noticeInsetDp else 0.dp
    val bubbleInsetLeft = controlsPadding.calculateLeftPadding(bubbleLayoutDirection) + if (cluster.isOnLeftSide) bubbleClusterInset else 0.dp
    val bubbleInsetRight = controlsPadding.calculateRightPadding(bubbleLayoutDirection) + if (cluster.isOnLeftSide) 0.dp else bubbleClusterInset
    MapBubbleLayer(
        tapped = tapped,
        onDismiss = onCloseBubble,
        // Item 8: "Open in Journal" remembers where the find was opened from (its id, the fan open then, the bubble's
        // anchor) before the screen switches to the Journal. `tapped` is read here, in composition, so the closure holds
        // the bubble that is showing, not the null the dismissal inside the layer leaves behind.
        sources = bubbleSources.copy(
            onOpenFind = bubbleSources.onOpenFind?.let { open ->
                val shown = tapped
                val remembering: (String) -> Unit = { id ->
                    returnMemory.remember(id, shown?.anchorPx ?: Offset.Zero, shown?.bearingDeg ?: 0f)
                    open(id)
                }
                remembering
            },
            // Part B of dispatch 2026-09-28-387: a bubble's "kept in" line remembers the bubble it was tapped in, and the fan open then, before it opens the entry.
            onOpenEntry = bubbleSources.onOpenEntry?.let { open ->
                val shown = tapped
                val remembering: (String) -> Unit = { entryId ->
                    if (shown != null) returnMemory.rememberEntryOpen(entryId, shown)
                    open(entryId)
                }
                remembering
            },
        ),
        forecast = renderMode.forecast,
        onViewSightingOnINaturalist = { sighting ->
            launchINaturalistObservation(context, sighting.observationId)
            onCloseBubble()
        },
        minY = minY,
        backEnabled = backEnabled,
        insetLeft = bubbleInsetLeft,
        insetRight = bubbleInsetRight,
        // Dispatch -502, Amendment 1: Back ended a waypoint navigation started in the waypoint's details sheet here.
        reopenDetails = (waypointReopen as? WaypointNavigationOrigin.MapDetails)?.let { RecordDetailsTarget.WaypointDetails(it.waypointId) },
        onReopenDetailsConsumed = onWaypointReopenConsumed,
    )
}
