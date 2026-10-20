package com.zynergylabs.forager.app.ui.map

// Moved from MapChrome.kt unchanged (UI split Phase 1, PR 7, dispatch 2026-10-19-16).

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.indication
import androidx.compose.foundation.LocalIndication
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.zynergylabs.forager.app.ui.theme.Bark
import com.zynergylabs.forager.app.ui.theme.LocalForagerDarkTheme
import com.zynergylabs.forager.app.ui.theme.Spacing

/**
 * The trigger that minimises [MapIconBar] — fullscreen-fixes dispatch, Item 3. The dispatch
 * specifies the restore affordance precisely ([MapIconBarRestoreHandle]'s own rectangular-outline
 * design) but leaves open how the bar is minimised in the first place — a decision made here rather
 * than flagged, since it is mechanical rather than architectural.
 *
 * **Owner's design for this control:** a drag box on the right side of the icon bar, at mid-height
 * — attached to the bar's own edge rather than stacked above or below its rows, rectangular (not a
 * circle/icon button), and themed the same fill-plus-hairline-border way (the fill at
 * [MAP_CHROME_OVER_MAP_ALPHA]) as [MapIconBar]
 * itself ([MapIconStackButtonColorDark]/[MapIconStackButtonColorLight]) so it reads as an extension
 * of the bar rather than an unrelated new control. Tapping it slides the bar (and, at the call
 * site, [TrailheadControls] alongside it — see that composable's own doc comment) away.
 *
 * Kept as its own [Surface], a sibling of [MapIconBar] at the call site rather than folded into
 * that composable's own Column as a 6th row: [mapIconBarRowAnchorOffset]'s own `rowCount` and the
 * two popups anchored against specific rows (`MapModePicker` (removed 2026-10-19, RECORD -1392), `AddActionTile`) assume exactly 5,
 * re-derived directly against the tree per that function's own doc comment — adding a 6th row here
 * would silently invalidate that arithmetic for an unrelated feature.
 *
 * Sized [MIN_TOUCH_TARGET] wide by 1.5x that tall — clearly a rectangle, not a square icon button,
 * per the owner's own description, while still meeting the touch-target floor in both dimensions
 * (exceeding it in height) rather than trading width for a "thinner" look the way a real drag
 * handle might.
 */
@Composable
internal fun MapIconBarMinimizeHandle(
    onMinimize: () -> Unit,
    modifier: Modifier = Modifier,
    /**
     * Direct owner request, layered on top of Item 3: the bar (and both its handles) can be
     * dragged to either screen edge. `false` (the bar's own original right-side position) rounds
     * the corners facing into the map, matching [MapIconBarRestoreHandle]'s own "rounded toward
     * the map, square toward the true edge" reasoning; `true` mirrors that shape for the left edge
     * so the rounding still faces inward rather than reading backwards once the bar is over there.
     */
    onLeftSide: Boolean = false,
    /**
     * The tap box's height. [HANDLE_DEFAULT_TAP_HEIGHT] (72 dp) everywhere it has always been; the landscape L passes 48 dp, one row's
     * height, so with the rows 48 dp apart the box is centred on the locate row and reaches neither the compass nor the Layers row
     * (owner's ruling (c), continuation 2026-09-28-172). The mark is 48 dp tall either way and fills the shorter box exactly.
     */
    tapHeight: Dp = HANDLE_DEFAULT_TAP_HEIGHT,
) {
    val isDarkTheme = LocalForagerDarkTheme.current
    val shape = if (onLeftSide) {
        RoundedCornerShape(topEnd = Spacing.xs, bottomEnd = Spacing.xs, topStart = 0.dp, bottomStart = 0.dp)
    } else {
        RoundedCornerShape(topStart = Spacing.xs, bottomStart = Spacing.xs, topEnd = 0.dp, bottomEnd = 0.dp)
    }
    // Box + Modifier.clickable, matching MapBarIconButton's own proven pattern elsewhere in this
    // file, rather than a Material3 Surface(onClick = ...) — confirmed, via an isolated standalone
    // test, that either shape correctly invokes onMinimize on its own; picked Box+clickable for
    // consistency with every other row in this bar rather than for a functional difference between
    // the two. (A Surface(onClick=...) version of this control was tried first and, embedded in the
    // real AvailabilityScreen tree, never invoked onMinimize under Robolectric even once across ten
    // retried performClick() calls — but the same isolated test proved that specific composable
    // shape innocent; the actual cause was the pre-existing, documented
    // docs/audits/2026-08-31-search-dropdown-dismiss-chip-unmount.md harness bug, which the
    // integration test this control needed happened to trigger via searchAReferenceRegion()'s own
    // still-open SearchDropdown, not this Surface/Box choice.)
    //
    // Icon-bar-drag-refinements dispatch, Item 1: the outer Box below is the full 48×72dp tap
    // target, undecorated — the same "hit area never shrinks, only the visible mark does, inset by
    // padding" pattern [MapIconBarRestoreHandle] already established (see that composable's own doc
    // comment and CLAUDE.md's own documented pitfall on this exact class of control). The owner's
    // own device report: the first version drew the fill across the *entire* tap width, reading as
    // a wide slab eating most of the bar's own width — this narrows the drawn mark to
    // [HANDLE_VISIBLE_MARK_WIDTH] while the tap target itself stays exactly what it was.
    //
    // Fullscreen-slide-out-fixes dispatch, Item 3: the mark straddles the bar's own outer edge
    // rather than sitting centered in the tap box. The tap box's outer side is flush with the true
    // screen edge and the bar's outer edge is [MAP_ICON_BAR_EDGE_INSET] in from it — so a mark
    // centered in the box sat 19dp in from the true edge, 11dp *inside* the bar, exactly the "sits
    // within the bar's width" the owner saw. Aligning the mark to the box's outer side, padded by
    // [HANDLE_MARK_EDGE_PADDING], centers it on the bar's edge: half overlapping the bar, half
    // protruding outward, mirrored per side. Padding only — the tap box never moves or shrinks.
    //
    // Handle-tap-area dispatch (owner finding on device): **the tap box is [HANDLE_TAP_WIDTH]
    // wide, deliberately below MIN_TOUCH_TARGET in that one dimension. This is a recorded
    // exception, not an oversight — do not "fix" it back to 48dp.** At 48dp the box reached 40dp
    // of the bar's own 48dp width and, centred on the bar's centre, sat squarely over the locate
    // row: a tap at locate's own centre (32dp in from the screen edge) hit this handle and
    // minimised the whole cluster when the user meant to find their position. Composition order
    // is the mechanism — this handle is composed after the container, so it wins every point
    // both cover. On this surface an over-large target steals taps from a neighbouring control;
    // a slightly small target that requires deliberation is better than one that fires when the
    // user meant something else. Width, not height, is the excepted dimension: the box is centred
    // on the locate row, so a shorter box would still cover locate's centre — only a narrower one
    // gives the row back. At 20dp from the screen edge the visible mark (10dp wide, centred 8dp
    // in) sits fully inside with 7dp to spare, the handle owns only the outer 12dp of the locate
    // row — 0–20dp from the edge *inclusive*, hit-testing gives a boundary pixel to the box — and
    // every point past 20dp — the whole glyph and its centre — belongs to locate.
    // The height stays 72dp; the drag gesture is unchanged but must now start within 20dp of
    // the edge, on or beside the mark.
    // Motion Part 1 (dispatch 2026-09-28-652, item 2, B7): the press highlight is the drawn mark's own shape, not the 20 x 72 tap
    // box, which reached over bare map and over the locate row's outer edge. The tap box, its recorded 20 dp width and its
    // clickable are unchanged; the clickable only stops drawing its own ripple, and the mark draws it instead.
    val interactionSource = remember { MutableInteractionSource() }
    Box(
        modifier = modifier
            .size(width = HANDLE_TAP_WIDTH, height = tapHeight)
            .clickable(interactionSource = interactionSource, indication = null, onClick = onMinimize)
            .semantics { contentDescription = "Hide map controls" }
            .testTag("map-icon-bar-minimize-handle"),
    ) {
        Box(
            modifier = Modifier
                .align(if (onLeftSide) Alignment.CenterStart else Alignment.CenterEnd)
                .padding(
                    start = if (onLeftSide) HANDLE_MARK_EDGE_PADDING else 0.dp,
                    end = if (onLeftSide) 0.dp else HANDLE_MARK_EDGE_PADDING,
                )
                .height(HANDLE_MARK_HEIGHT)
                .width(HANDLE_VISIBLE_MARK_WIDTH)
                .testTag("map-icon-bar-minimize-handle-mark")
                .mapChromeContainerColor(if (isDarkTheme) MapIconStackButtonColorDark else MapIconStackButtonColorLight)
                .background(color = if (isDarkTheme) MapIconStackButtonColorDark else MapIconStackButtonColorLight, shape = shape)
                .border(
                    width = 1.dp,
                    color = if (isDarkTheme) MAP_ICON_STACK_BORDER_COLOR_DARK else MAP_ICON_STACK_BORDER_COLOR_LIGHT,
                    shape = shape,
                )
                // Clips only the highlight drawn below; the mark takes no touches, so the clip moves none.
                .clip(shape)
                .indication(interactionSource, LocalIndication.current),
        )
    }
}

/**
 * The minimise handle's default tap-box height, and the drawn mark's height: the mark was the box less [Spacing.md] above and below,
 * 72 - 24 = 48. Now a constant so a shorter box (the landscape L's, [MapIconBarMinimizeHandle]'s `tapHeight`) leaves the mark as it
 * was, 48 tall, filling that box exactly.
 */
internal val HANDLE_DEFAULT_TAP_HEIGHT = MIN_TOUCH_TARGET * 1.5f
private val HANDLE_MARK_HEIGHT = HANDLE_DEFAULT_TAP_HEIGHT - Spacing.md * 2

/** How much of [MapIconBarMinimizeHandle]'s / [MapIconBarRestoreHandle]'s own 48dp-wide tap target is actually drawn — see either composable's own doc comment for why this is much narrower than the hit area itself. */
private val HANDLE_VISIBLE_MARK_WIDTH = 10.dp

/**
 * [MapIconBarMinimizeHandle]'s tap-box width, measured inward from the screen edge — a recorded
 * exception below [MIN_TOUCH_TARGET] in this one dimension; see the comment at its use for why.
 * The test that pins the exception asserts the literal 20dp, deliberately not this constant — a
 * drift here back to 48dp must fail that test, not be absorbed by it.
 */
private val HANDLE_TAP_WIDTH = 20.dp

/**
 * Padding between a handle's tap box's outer side and its visible mark, chosen so the mark's own
 * centre lands exactly on [MapIconBar]'s outer edge ([MAP_ICON_BAR_EDGE_INSET] in from the true
 * screen edge, which the tap box is flush with): half the mark overlaps the bar, half protrudes.
 * Derived, not a magic number — see [MapIconBarMinimizeHandle]'s own Item 3 comment.
 */
private val HANDLE_MARK_EDGE_PADDING = (MAP_ICON_BAR_EDGE_INSET - HANDLE_VISIBLE_MARK_WIDTH / 2).coerceAtLeast(0.dp)

/**
 * The restore control for a minimised [MapIconBar] — fullscreen-fixes dispatch, Item 3.
 * [MapIconBar] holds the only way out of fullscreen, and that same dispatch's Item 2 slides
 * `ForagerBottomNav` fully off-screen while fullscreen, so a minimised bar with no restore control
 * would leave a fullscreen user stuck on a bare map with no way back. This handle is composed in
 * [MapIconBar]'s place whenever the bar is minimised, in every state — independent of
 * `isMapFullscreen`, per this feature's own "do not tie it to isMapFullscreen" instruction — so it
 * is always there to tap.
 *
 * The tappable region is a full [MIN_TOUCH_TARGET] square, the same floor every other control in
 * this file meets; the "small rectangular outline peeking out from the right edge" the project
 * owner asked for is a thin bordered rectangle centered inside it via padding — visually much
 * smaller than the tap target, without the tap target itself shrinking to match. That distinction
 * matters here specifically: this is a small `Surface`-shaped control sitting at the map's own
 * edge, precisely the shape of thing that has silently swallowed map touches three times before
 * (see this file's own history on [MapIconBar] and `CLAUDE.md`'s "Known pitfalls") — but the fix
 * for that failure mode is a full-size hit area with a smaller visible mark inside it, not a
 * smaller hit area, which is a different (and here, wrong) way to shrink a control.
 *
 * Filled, at the standing over-map value ([MapIconStackButtonColorDark]/[MapIconStackButtonColorLight],
 * [MAP_CHROME_OVER_MAP_ALPHA]) — persist-fullscreen dispatch, Item 2, from the owner's device
 * pass: the bare outline read as a white mark over the map and was hard to spot against pale
 * terrain. The cluster it stands in for layers 0.6 under 0.5 to composite to 0.8, but a lone
 * handle has nothing beneath it to composite against, so the single value that matches the
 * cluster's weight is the composite itself, not either layer. Findability, not prominence: the
 * size, the padding-inset mark, the outline and the full 48dp tap box are all unchanged; only the
 * fill is new. Rounding only the two corners facing into the map (the edge nearer the screen's
 * own edge stays square) so the shape itself reads as something tucked against, and emerging
 * from, that edge. Legibility over real terrain in either theme is a device check.
 */
@Composable
internal fun MapIconBarRestoreHandle(
    onRestore: () -> Unit,
    modifier: Modifier = Modifier,
    /** See [MapIconBarMinimizeHandle]'s own `onLeftSide` doc comment — same reasoning, mirrored shape. */
    onLeftSide: Boolean = false,
) {
    val isDarkTheme = LocalForagerDarkTheme.current
    val outlineShape = if (onLeftSide) {
        RoundedCornerShape(topEnd = 3.dp, bottomEnd = 3.dp, topStart = 0.dp, bottomStart = 0.dp)
    } else {
        RoundedCornerShape(topStart = 3.dp, bottomStart = 3.dp, topEnd = 0.dp, bottomEnd = 0.dp)
    }
    // Fullscreen-slide-out-fixes dispatch, Item 3 (extended to this handle on the owner's own
    // call): the outline straddles where the bar's own outer edge sits — [HANDLE_MARK_EDGE_PADDING]
    // in from the tap box's outer side — rather than centered in the tap box, so it genuinely
    // peeks from the edge instead of floating 19dp inboard of it. Same padding-only mechanism as
    // [MapIconBarMinimizeHandle]; the 48dp tap box itself never moves or shrinks.
    // Motion Part 1 (dispatch 2026-09-28-652, item 2, B8): the press highlight is the outline's own shape rather than the whole
    // 48 x 48 box over bare map. The 48 dp tap box and its clickable are unchanged.
    val interactionSource = remember { MutableInteractionSource() }
    Box(
        modifier = modifier
            .size(MIN_TOUCH_TARGET)
            .clickable(interactionSource = interactionSource, indication = null, onClick = onRestore)
            .semantics { contentDescription = "Show map controls" },
    ) {
        Box(
            modifier = Modifier
                .align(if (onLeftSide) Alignment.CenterStart else Alignment.CenterEnd)
                .padding(
                    start = if (onLeftSide) HANDLE_MARK_EDGE_PADDING else 0.dp,
                    end = if (onLeftSide) 0.dp else HANDLE_MARK_EDGE_PADDING,
                )
                .padding(vertical = Spacing.md)
                .fillMaxHeight()
                .width(HANDLE_VISIBLE_MARK_WIDTH)
                .testTag("map-icon-bar-restore-handle-mark")
                .mapChromeContainerColor(if (isDarkTheme) MapIconStackButtonColorDark else MapIconStackButtonColorLight)
                .background(
                    color = if (isDarkTheme) MapIconStackButtonColorDark else MapIconStackButtonColorLight,
                    shape = outlineShape,
                )
                .border(
                    width = 1.5.dp,
                    color = if (isDarkTheme) Color.White.copy(alpha = 0.7f) else Bark.copy(alpha = 0.7f),
                    shape = outlineShape,
                )
                .clip(outlineShape)
                .indication(interactionSource, LocalIndication.current),
        )
    }
}
