package com.zynergylabs.forager.app.ui.availability

import android.util.Log
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.absolutePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.zynergylabs.forager.app.ui.format.is24HourClock
import com.zynergylabs.forager.app.ui.map.CrowdedStackList
import com.zynergylabs.forager.app.ui.map.MAP_ICON_BAR_EDGE_INSET
import com.zynergylabs.forager.app.ui.map.MapFeatureTap
import com.zynergylabs.forager.app.ui.map.MapRecordSources
import com.zynergylabs.forager.app.ui.map.MapRenderMode
import com.zynergylabs.forager.app.ui.map.TappedMapThing
import com.zynergylabs.forager.app.ui.map.crowdedListRows
import com.zynergylabs.forager.app.ui.map.fanout.CrowdedStackBackHandlers
import com.zynergylabs.forager.app.ui.map.fanout.CrowdedStackHost
import com.zynergylabs.forager.app.ui.theme.Spacing

/**
 * The Maps tab's crowded-stack list and its Back steps (dispatch 2026-10-27-07, steps 3 and 4). The list shows while
 * [host] holds one and no bubble does ([tapped]): a row opens its record's bubble "as from the map", through the same
 * [onFeatureTap] a map tap uses, pointing at the stack's spot; while that bubble shows the list waits under it, and Back
 * closes the bubble and brings the list back. Composed after the bubble, over the map, at the bottom, above the bottom bar.
 * [backEnabled] is the bubble's own gate.
 *
 * **Clear of the icon cluster's column**, on whichever side the cluster is ([cluster]): its edge inset, its measured width
 * and the gap beside it, as the legend moves inboard of it. The cluster is composed after the list, so without this it sat
 * over the rows' cluster-side end and took the touches there (seen on CI: a real touch at 90% across the first two rows
 * reached the cluster, run 40580543726). Cleared at any height, because the user drags the cluster up and down and the list
 * is not to make them move it.
 */
@Composable
internal fun BoxScope.CompactMapCrowdedList(
    host: CrowdedStackHost,
    tapped: TappedMapThing?,
    bubbleSources: MapRecordSources,
    renderMode: MapRenderMode,
    onFeatureTap: (MapFeatureTap) -> Unit,
    controlsPadding: PaddingValues,
    cluster: MapIconClusterState,
    backEnabled: Boolean,
) {
    val shown = host.list
    if (shown != null && tapped == null) {
        val is24 = is24HourClock()
        val rows = remember(shown, bubbleSources, is24) {
            crowdedListRows(shown.keys, bubbleSources, is24, warn = { message -> Log.w(CROWDED_LIST_LOG_TAG, message) })
        }
        if (rows.isEmpty()) {
            // Every record went while the list was up: nothing to list, said so, and the list closes.
            LaunchedEffect(shown) {
                Log.w(CROWDED_LIST_LOG_TAG, "Crowded list: none of its ${shown.keys.size} records is there any more; it closes.")
                host.closeList()
            }
        } else {
            val clusterColumn = MAP_ICON_BAR_EDGE_INSET + with(LocalDensity.current) { cluster.clusterWidthPx.toDp() } + Spacing.sm
            val clusterOnLeft = cluster.isOnLeftSide
            CrowdedStackList(
                rows = rows,
                night = renderMode.night,
                onRow = { key ->
                    onFeatureTap(MapFeatureTap(key.layerId, key.featureId, Offset(shown.anchorXPx, shown.anchorYPx), shown.bearingDeg, shown.at))
                },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(controlsPadding)
                    .absolutePadding(
                        left = if (clusterOnLeft) clusterColumn else 16.dp,
                        right = if (clusterOnLeft) 16.dp else clusterColumn,
                        bottom = renderMode.bottomInset + 16.dp,
                    ),
            )
        }
    }
    CrowdedStackBackHandlers(host, bubbleShown = tapped != null, backEnabled = backEnabled)
}

private const val CROWDED_LIST_LOG_TAG = "CrowdedStackList"
