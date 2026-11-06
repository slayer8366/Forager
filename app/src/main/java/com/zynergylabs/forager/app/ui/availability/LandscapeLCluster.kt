package com.zynergylabs.forager.app.ui.availability

// Moved unchanged from AvailabilityMapIconCluster.kt (dispatch 2026-10-19-18, UI split Phase 1, PR 10b).

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalDensity
import com.zynergylabs.forager.app.ui.log.ScreenEdge
import com.zynergylabs.forager.app.ui.theme.Spacing
import kotlin.math.roundToInt

/**
 * Gap between [MapIconBar]'s bottom edge and [ControlPill]'s top edge — matches [MapIconBar]'s own
 * `Spacing.sm` inset from the screen edge, so the pill reads as continuing the same margin rather
 * than sitting at an arbitrarily different distance. Icon-bar-unify-container dispatch: now the
 * cluster container Column's own `spacedBy`, no longer an offset from a measured bottom edge —
 * the gap was structural (produced by `Modifier.offset`, not padding in a shared parent), and
 * unifying the container is what changed how it is expressed. It is filled by the container at
 * [com.zynergylabs.forager.app.ui.map.MAP_ICON_CLUSTER_CONTAINER_ALPHA] and no longer passes touches to the
 * map, which is intentional; the two `@Ignore`d gap-touch tests in
 * `AvailabilityScreenMapIconStackTest` now carry a false premise on top of the Robolectric reason
 * they were parked for, and are left for the owner's own separate look.
 */
internal val CONTROL_PILL_GAP_BELOW_MAP_ICON_BAR = Spacing.sm

/**
 * The icon cluster in a short landscape window, as an L (dispatch 2026-09-28-160; the owner: "Oh yeah on either side it looks like an
 * L. On the right side it just looks like an inverse L", and, on the height, "A"). [bar] (MapIconBar, five 48 dp rows, 240 dp) on top, an
 * 8 dp gap, then [pill] (ControlPill turned horizontal, 96 by 48): its outer end flush with the bar's outer edge, so record sits
 * exactly under the bar's column and return extends inboard, towards the middle of the screen; mirrored with the cluster's side. 296 dp in all.
 *
 * Replaces the side-by-side `ShortLandscapeClusterRow` (Part 1 layout fixes, "option A", planner message `2026-09-28-99`), which
 * sat inside a filled container; this draws nothing around the two shapes. The Column has no fill and no pointer input, so what is
 * outside them is the map's. Compact-only: the tablet never sets [MapIconClusterState.landscape].
 */
@Composable
internal fun LandscapeLCluster(
    onLeftSide: Boolean,
    /**
     * T11 fixes (RECORD -766, P4; the owner: "Have the recording pill to the left side of the + button on the icon bar, rather
     * than below it. Same L, more compact", while navigating on the display's side): 0 is the pill under the bar, as above; 1
     * is the pill beside "+", the bar's last row, on its inboard side, its bottom on the bar's bottom, so the L is the bar's
     * 240 dp tall. Between the two the pill moves across first and then up (an L-shaped path), so at no point does it pass over
     * a bar button: every button keeps its own touches mid-move, and the move is in the layout, so touches follow it.
     */
    pillBeside: Float = 0f,
    bar: @Composable () -> Unit,
    pill: @Composable () -> Unit,
) {
    val gapPx = with(LocalDensity.current) { CONTROL_PILL_GAP_BELOW_MAP_ICON_BAR.roundToPx() }
    androidx.compose.ui.layout.Layout(contents = listOf(bar, pill)) { (barMeasurables, pillMeasurables), _ ->
        val loose = androidx.compose.ui.unit.Constraints()
        val b = barMeasurables.first().measure(loose)
        val p = pillMeasurables.first().measure(loose)
        val across = (pillBeside * 2f).coerceIn(0f, 1f)
        val up = (pillBeside * 2f - 1f).coerceIn(0f, 1f)
        // The pill's outer end, from the bar's outer edge inward: 0 under the bar (flush with it), the bar's width and the gap
        // beside "+". Its top: the gap under the bar, then level with "+".
        val pillInset = ((b.width + gapPx) * across).roundToInt()
        val pillTop = (b.height + gapPx + (-gapPx - p.height) * up).roundToInt()
        val width = maxOf(b.width, pillInset + p.width)
        val height = maxOf(b.height, pillTop + p.height)
        layout(width, height) {
            if (onLeftSide) {
                b.place(0, 0)
                p.place(pillInset, pillTop)
            } else {
                b.place(width - b.width, 0)
                p.place(width - pillInset - p.width, pillTop)
            }
        }
    }
}

/**
 * T11 fixes (RECORD -766, P4): whether the landscape L slides below the navigation display. Only in a landscape window (the
 * L), only while navigating, and only with the L on the display's side, which is the rail side (the display sits in the rail
 * side's top corner): the right at ROTATION_90, the left at ROTATION_270. The owner's words were "Right side only, left side
 * doesn't get this"; asked whether "right" meant the screen's right or the display's side, the owner answered "Follow the
 * display (Recommended)" (RECORD -768): a bar on the display's side, at either rotation.
 * Portrait, and the L on the other side, never change.
 */
internal fun navigationSlideApplies(landscape: Boolean, isNavigating: Boolean, clusterOnLeftSide: Boolean, displayEdge: ScreenEdge?): Boolean =
    landscape && isNavigating && displayEdge != null && clusterOnLeftSide == (displayEdge == ScreenEdge.Left)
