package com.zynergylabs.forager.app.ui.availability

// Moved unchanged from AvailabilityMapIconCluster.kt (dispatch 2026-10-19-18, UI split Phase 1, PR 10b).

import androidx.compose.ui.unit.Density
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.Animatable
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Job
import androidx.compose.ui.Alignment
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.unit.DpOffset
import com.zynergylabs.forager.app.ui.log.ScreenEdge
import com.zynergylabs.forager.app.ui.map.MAP_ICON_BAR_EDGE_INSET

/**
 * Everything the cluster holds and measures. The user-set values (side, minimised, offset) live in
 * [position], held by the caller so they survive leaving and returning (CLAUDE.md, "UX defaults"); the rest
 * is measurement, re-taken on every mount.
 */
internal class MapIconClusterState(
    val position: MapIconClusterPositionState,
) {
    /** The compact scaffold's rail and punch-hole edges; both null except in a short landscape window, and always null on the tablet. Set each composition. */
    var railPortEdge: ScreenEdge? = null
    var punchHoleEdge: ScreenEdge? = null

    /** A short landscape window's own cluster (Landscape B2, S6), with its own position fields. Compact-only. */
    val landscape: Boolean get() = railPortEdge != null && punchHoleEdge != null

    var isMinimized: Boolean
        get() = if (landscape) position.landscapeIsMinimized else position.isMinimized
        set(value) { if (landscape) position.landscapeIsMinimized = value else position.isMinimized = value }

    /**
     * Whether the cluster is on the window's left. In a short landscape window the side is stored as port or
     * punch-hole (so turning the phone keeps it on the same device edge) and translated here; otherwise it is
     * the portrait side unchanged.
     */
    var isOnLeftSide: Boolean
        get() {
            if (fixedLeft) return true
            return savedOnLeftSide
        }
        set(value) {
            // RECORD -955: while fixed, a side is never written, so the saved one is what returns.
            if (fixedLeft) return
            val port = railPortEdge
            val punch = punchHoleEdge
            if (port != null && punch != null) {
                position.landscapeOnPortSide = (if (value) ScreenEdge.Left else ScreenEdge.Right) == port
            } else {
                position.isOnLeftSide = value
            }
        }

    /** The side the user saved (the one [isOnLeftSide] reads whenever the bar is not [fixedLeft]). */
    val savedOnLeftSide: Boolean
        get() {
            val port = railPortEdge
            val punch = punchHoleEdge
            return if (port != null && punch != null) (if (position.landscapeOnPortSide) port else punch) == ScreenEdge.Left else position.isOnLeftSide
        }

    /**
     * RECORD -955, the owner: "There's no friendly way to keep the icon bar on the right in landscape mode. I say we keep it on the
     * left in landscape"; "The screen left"; "Only have it fixed in a) landscape and b) with navigation mode on. Those two conditions
     * must be met to have it fixed to the left. Else it can be set on the right as an option for the user". True while both hold:
     * the bar is on the screen's left and cannot be dragged or snapped across. Never written into the saved side ([isOnLeftSide]'s
     * setter does nothing while it holds), so when either condition ends the saved side returns. Set by [MapIconCluster].
     */
    var fixedLeft by mutableStateOf(false)

    /** The offset the user last dragged the cluster to, the only thing a drag writes (the single source of truth); [displayedOffsetPx] is what is drawn. */
    var userChosenOffsetPx: Float
        get() = if (landscape) position.landscapeUserChosenOffsetPx else position.userChosenOffsetPx
        set(value) { if (landscape) position.landscapeUserChosenOffsetPx = value else position.userChosenOffsetPx = value }

    val displayedOffsetPx: Animatable<Float, AnimationVector1D>
        get() = if (landscape) position.landscapeDisplayedOffsetPx else position.displayedOffsetPx

    /** Whether this orientation's height was just restored and not yet fitted to measured bounds (RECORD -711); see [MapIconClusterPositionState.snapRestoredPortrait]. */
    var snapRestored: Boolean
        get() = if (landscape) position.snapRestoredLandscape else position.snapRestoredPortrait
        set(value) { if (landscape) position.snapRestoredLandscape = value else position.snapRestoredPortrait = value }

    /** Horizontal drag accumulated only during a gesture, read at its end to decide a side flip, then reset. */
    var horizontalDragPx by mutableFloatStateOf(0f)

    /** The glide from the finger to the cluster's side after a drag (motion Part 1, item 6). See [ClusterGlide]. */
    val glide = ClusterGlide()

    /** The map pane's real measured height and top in the root, written by the caller's content box: what the clamps are against. */
    var mapContentBoxHeightPx by mutableFloatStateOf(0f)
    var mapContentBoxTopInRootPx by mutableFloatStateOf(0f)

    /** The map pane's left in the root, written with the top above; with [clusterBoundsInRoot] it gives the cluster's rectangle in the map's own pixels (attribution clearance, dispatch 2026-09-29-57, item 1). */
    var mapContentBoxLeftInRootPx by mutableFloatStateOf(0f)

    /** The cluster container's measured bounds in the root, written by the container only; null until first measured. */
    var clusterBoundsInRoot: androidx.compose.ui.geometry.Rect? by mutableStateOf(null)

    /** The cluster container's real measured height and width, written by the container only; kept while minimised. */
    var clusterHeightPx by mutableFloatStateOf(0f)
    var clusterWidthPx by mutableFloatStateOf(0f)

    /** The bar's own vertical centre relative to the container's top, so the panels' anchors and handles' mid-height follow the bar, not the container. */
    var centreInClusterPx by mutableFloatStateOf(0f)

    /** The bottom navigation's measured height (the compact tree); 0 where there is none (the tablet). */
    var bottomNavHeightPx by mutableFloatStateOf(0f)

    /** The legend chip's top edge in the content box's coordinates, or null while none shows: a further lowest edge for the cluster. */
    var legendChipTopPx: Float? by mutableStateOf(null)

    /**
     * T11 fixes (RECORD -766, P4): the navigation display's bottom edge in the root while navigating, written by the display;
     * null while not navigating. What the landscape L slides down below ([navigationSlideApplies]).
     */
    var navigationDisplayBottomInRootPx: Float? by mutableStateOf(null)

    /** RECORD -955: the landscape navigation card's bounds in the root while shown, written by the card; for its evening-line limit. */
    var navigationCardBoundsInRoot: androidx.compose.ui.geometry.Rect? by mutableStateOf(null)

    /**
     * T11 fixes: where the L's top would be in the root at a drag offset of 0, at its current height. Measured with the cluster
     * (its top less the offset it was placed at), so the slide's floor is a plain difference and does not move as the bar does.
     */
    var clusterRestTopInRootPx by mutableFloatStateOf(0f)

    /**
     * RECORD -770: in a landscape window, where the bar's top is at the lowest the L may go with the pill beside "+" (the bar
     * alone, at the bottom of what the clamps allow); null until measured. The navigation display reads it to keep the bar's
     * overlap within the Fullscreen row ([eveningLineFits]).
     */
    var navigationLowestBarTopInRootPx: Float? by mutableStateOf(null)

    val sideAlignment: Alignment get() = if (isOnLeftSide) Alignment.CenterStart else Alignment.CenterEnd

    /**
     * Where a panel anchored to the cluster (AddActionTile) opens: the bar's live side and drag offset, plus
     * the bar's own centre relative to the container's. Each caller adds its own row's anchor on top.
     */
    fun panelAnchorOffset(density: Density): DpOffset = with(density) {
        DpOffset(
            x = (if (isOnLeftSide) MAP_ICON_BAR_EDGE_INSET else -MAP_ICON_BAR_EDGE_INSET) + horizontalDragPx.toDp(),
            y = (displayedOffsetPx.value + centreInClusterPx - clusterHeightPx / 2f).toDp(),
        )
    }
}

@Composable
internal fun rememberMapIconClusterState(
    position: MapIconClusterPositionState,
    railPortEdge: ScreenEdge? = null,
    punchHoleEdge: ScreenEdge? = null,
): MapIconClusterState {
    val state = remember { MapIconClusterState(position) }
    state.railPortEdge = railPortEdge
    state.punchHoleEdge = punchHoleEdge
    return state
}

/**
 * The cluster's glide after a drag (motion Part 1, dispatch 2026-09-28-652 item 6; scout B14, B15). Holds no layout: the
 * cluster is laid out where it belongs the moment the finger lifts, as it always was, and [clusterGlideDraw] draws it from
 * [fromLeftInRootPx] (where it was under the finger) to that layout as [remaining] runs from 1 to 0.
 */
internal class ClusterGlide {
    /** How much of the glide is still to run: 1 at the finger, 0 landed (and whenever none is running). On the cluster's own navigation spec. */
    var remaining by mutableFloatStateOf(0f)

    /** The running glide, cancelled by a new drag. */
    internal var job: Job? = null

    /** The glided box's left edge in the root when the finger lifted. */
    var fromLeftInRootPx = 0f

    internal var clusterCoordinates: LayoutCoordinates? = null
    internal var handleCoordinates: LayoutCoordinates? = null

    /** The left edge in the root of whichever box is showing: the restore handle's while [minimized], the cluster's otherwise. */
    fun leftInRootPx(minimized: Boolean): Float? =
        (if (minimized) handleCoordinates else clusterCoordinates)?.takeIf { it.isAttached }?.positionInRoot()?.x
}
