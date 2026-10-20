package com.zynergylabs.forager.app.ui.availability

// Moved unchanged from AvailabilityCompactMapUi.kt (dispatch 2026-10-19-14, UI split Phase 1, PR 9a).

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.zynergylabs.forager.app.domain.MapIconClusterPlacement
import androidx.compose.runtime.mutableFloatStateOf

/**
 * The icon cluster's position on the compact Map tab, as one holder so it can live *above*
 * [CompactMapTab] — in `compactMainScaffold`, which persists across tab changes — rather than in
 * the tab's own `remember`s, which are disposed with it on every tab switch. Direct owner request:
 * "switching between tabs resets the icon position; have it remember positions when switching in
 * and out of maps." That reversed the icon-bar-position-memory dispatch's working rule that
 * nothing survives a tab change, and the owner then made the general principle explicit —
 * CLAUDE.md, "UX defaults": user-set state survives navigating away and back by default, and an
 * unrequested reset is a bug unless the exception is stated for the case. Persisting across app
 * restarts was a separate per-case decision; since RECORD -711 the side and height persist (below),
 * and the minimised flags do not.
 *
 * Holds the cluster's user-set state: [userChosenOffsetPx] (the single source of truth, written
 * only by drags), [displayedOffsetPx] (the Animatable that is always the clamp of it under the
 * bounds in force — kept too, so returning to the tab does not glide in from zero),
 * [isOnLeftSide], and [isMinimized] (the fullscreen-fixes dispatch's "minimising resets when the
 * user leaves the Map tab" was a planner rule with no owner exception behind it, so the default
 * applies). The cluster's measurements (heights, the bar's centre) are not here — they are re-measured
 * on every mount and mean nothing across one. On return, the tab's bounds-change effect re-clamps
 * the memory under the bounds then in force (the tab change also exited fullscreen), so a position
 * chosen low in fullscreen comes back above the nav, and re-entering fullscreen glides it down
 * again — the same behaviour a fullscreen exit already has, now also across a tab change.
 *
 * **Side and height persist across restarts since RECORD -711** (the owner: "have the map icon bar persist
 * between restarts so left handed users don't need to change it every time they open the app", then
 * "Side and height (Recommended)"); the minimised flags stay session-only. [placement] reads the four
 * stored values out, [applyPlacement] puts a stored set back once at launch, and [settledCount] counts
 * the drags that have ended, which is what `AvailabilityScreen` stores on. Until [placementApplied] is
 * true the cluster is not drawn at all (`MapIconCluster` returns early), so a restored left side is never
 * preceded by a frame on the right. A holder made with nothing to wait for starts applied.
 */
internal class MapIconClusterPositionState(awaitingPlacement: Boolean = false) {
    var userChosenOffsetPx by mutableFloatStateOf(0f)
    val displayedOffsetPx = Animatable(0f)
    var isOnLeftSide by mutableStateOf(false)
    var isMinimized by mutableStateOf(false)

    // Landscape B2 (S6): a short landscape window's own cluster position, separate from the
    // portrait one above so that turning the phone neither carries a portrait drag into landscape
    // nor loses it on the way back. The side is stored as port or punch-hole, not left or right,
    // and translated to a window side from the current port edge where the cluster is anchored,
    // so turning between ROTATION_90 and ROTATION_270 keeps the cluster on the same device edge.
    // Defaults to the punch-hole side. Side and height persist like the portrait ones (RECORD -711); minimised does not.
    var landscapeOnPortSide by mutableStateOf(false)
    var landscapeUserChosenOffsetPx by mutableFloatStateOf(0f)
    val landscapeDisplayedOffsetPx = Animatable(0f)
    var landscapeIsMinimized by mutableStateOf(false)

    /** False until a stored placement has been applied; the cluster is not drawn before. See the class doc. */
    var placementApplied by mutableStateOf(!awaitingPlacement)
        private set

    /** Increments each time a drag of the cluster ends (or is cancelled after moving it): the moment its side and height are stored. */
    var settledCount by mutableIntStateOf(0)
        private set

    fun onDragSettled() {
        settledCount++
    }

    /**
     * Set by [applyPlacement] for each orientation, cleared by the cluster once it has clamped that
     * orientation's restored height against measured bounds: the first fit of a restored height is a
     * snap, not the glide a bounds change gets, so a height stored in a taller window does not visibly
     * slide in from beyond the current one's limits.
     */
    var snapRestoredPortrait = false
    var snapRestoredLandscape = false

    /** The side and height of both orientations as stored values, the heights in dp. */
    fun placement(density: Density): MapIconClusterPlacement = with(density) {
        MapIconClusterPlacement(
            portraitOnLeft = isOnLeftSide,
            portraitOffsetDp = userChosenOffsetPx.toDp().value,
            landscapeOnPortSide = landscapeOnPortSide,
            landscapeOffsetDp = landscapeUserChosenOffsetPx.toDp().value,
        )
    }

    /**
     * Puts a stored [placement] back, once: the sides as stored, the heights as the user-chosen memory and
     * as what is drawn. The heights are not clamped here, because no bounds are measured yet; the cluster
     * clamps what it draws against the window it is in (`MapIconCluster`'s bounds effect), with a snap the
     * first time ([snapRestoredPortrait]). The memory keeps the stored height, as it keeps a drag's height
     * through a fullscreen change, so a larger window later gets it back. Does nothing once applied.
     */
    suspend fun applyPlacement(placement: MapIconClusterPlacement, density: Density) {
        if (placementApplied) return
        val portraitPx = with(density) { placement.portraitOffsetDp.dp.toPx() }
        val landscapePx = with(density) { placement.landscapeOffsetDp.dp.toPx() }
        isOnLeftSide = placement.portraitOnLeft
        userChosenOffsetPx = portraitPx
        displayedOffsetPx.snapTo(portraitPx)
        landscapeOnPortSide = placement.landscapeOnPortSide
        landscapeUserChosenOffsetPx = landscapePx
        landscapeDisplayedOffsetPx.snapTo(landscapePx)
        snapRestoredPortrait = true
        snapRestoredLandscape = true
        placementApplied = true
    }
}

/** [awaitingPlacement]: `true` only for a host that will apply a stored placement (`AvailabilityScreen`); see [MapIconClusterPositionState]. */
@Composable
internal fun rememberMapIconClusterPositionState(awaitingPlacement: Boolean = false): MapIconClusterPositionState =
    remember { MapIconClusterPositionState(awaitingPlacement) }
