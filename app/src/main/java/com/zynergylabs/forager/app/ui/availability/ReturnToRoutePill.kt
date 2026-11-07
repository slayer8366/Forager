package com.zynergylabs.forager.app.ui.availability

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.zynergylabs.forager.app.ui.map.LEGEND_ATTRIBUTION_CLEARANCE
import com.zynergylabs.forager.app.ui.map.mapChromeContainerColor
import com.zynergylabs.forager.app.ui.map.mapChromeContentColor
import com.zynergylabs.forager.app.ui.map.mapChromeFill
import com.zynergylabs.forager.app.ui.theme.Spacing
import com.zynergylabs.forager.app.ui.theme.navigationBarContainerColor

internal const val RETURN_TO_ROUTE_TAG = "return-to-route"

/**
 * The pill's words. First "Return to Route", the owner's words (dispatch 2026-09-28-430): "Have a "Return to Route" button
 * appear when panning away". Renamed "Re-center" (dispatch 2026-10-25-08; RECORD -1498 answer 5, the owner: "Rename the
 * current button to Re-center like it does on Google maps, and use the Return to Route for the Off trail notice"; the
 * step path ruled at -1631). Only the words changed; "Return to Route" now belongs to the off-trail notice (plan step 7,
 * PR 3). The constant keeps its name so the pill's history reads in one place.
 */
internal const val RETURN_TO_ROUTE_TEXT = "Re-center"

/** The pill's least height: the minimum touch target, laid out, so what a finger reaches is what is drawn. */
internal val RETURN_TO_ROUTE_PILL_HEIGHT: Dp = 48.dp

/**
 * How far the map's snackbars rise while the pill shows, so a trip-start or network notice sits
 * above it rather than over it (continuation 2026-09-28-432): the pill's own clearance above the
 * nav, its height, and a gap.
 */
internal val RETURN_TO_ROUTE_SNACKBAR_LIFT: Dp = LEGEND_ATTRIBUTION_CLEARANCE + RETURN_TO_ROUTE_PILL_HEIGHT + Spacing.sm

/**
 * "Return to Route" (dispatch 2026-09-28-430, plan task T22): shown while navigating once the user
 * has moved the map away from the navigation view (a drag, or "Reset orientation"); a tap brings
 * the tilted, facing-up view back and it follows again. Content-width at bottom centre, so it
 * covers only what it draws (CLAUDE.md, the `Surface` pitfall), with its fill at
 * [com.zynergylabs.forager.app.ui.map.MAP_CHROME_OVER_MAP_ALPHA] and its content opaque, as every
 * surface over the map ("Nothing fully obstructs the map view"). The snackbar's colour roles, the
 * map's other bottom chrome.
 */
@Composable
internal fun ReturnToRoutePill(onClick: () -> Unit, modifier: Modifier = Modifier, roomPx: Float? = null) {
    if (roomPx == null) {
        ReturnToRoutePillSurface(onClick = onClick, iconOnly = false, modifier = modifier)
        return
    }
    // D27 (RECORD -1976, the owner: "Icon only where room is short (Recommended)"): beside the L, [roomPx] wide, the pill measures its
    // word's width on a probe it never places (no semantics, never drawn or touched) and shows its icon alone where the word does not
    // fit ([reCenterShowsWord]). Measured on every pass, so it follows the font scale and the room, never the window or font.
    SubcomposeLayout(modifier) { constraints ->
        val wordWidth = subcompose(ReturnToRoutePillSlot.Probe) {
            Box(Modifier.clearAndSetSemantics {}) { ReturnToRoutePillContent(iconOnly = false) }
        }.maxOfOrNull { it.measure(Constraints()).width } ?: 0
        val iconOnly = !reCenterShowsWord(wordWidth.toFloat(), roomPx)
        val placeables = subcompose(if (iconOnly) ReturnToRoutePillSlot.IconOnly else ReturnToRoutePillSlot.WithWord) {
            ReturnToRoutePillSurface(onClick = onClick, iconOnly = iconOnly)
        }.map { it.measure(constraints.copy(minWidth = 0, minHeight = 0)) }
        layout(placeables.maxOfOrNull { it.width } ?: 0, placeables.maxOfOrNull { it.height } ?: 0) {
            placeables.forEach { it.place(0, 0) }
        }
    }
}

private enum class ReturnToRoutePillSlot { Probe, WithWord, IconOnly }

/**
 * D34 (RECORD -2005): the pill's size with its word, told to [onMeasured] on every pass, measured on a probe that is never placed (no
 * semantics, never drawn or touched) and takes no room: 0 x 0 in its parent. Its height is at least the 48 dp target, as the pill's.
 */
@Composable
internal fun ReturnToRouteWordProbe(onMeasured: (IntSize) -> Unit) {
    val currentOnMeasured by rememberUpdatedState(onMeasured)
    SubcomposeLayout { _ ->
        val probe = subcompose(Unit) {
            Box(Modifier.clearAndSetSemantics {}.heightIn(min = RETURN_TO_ROUTE_PILL_HEIGHT)) { ReturnToRoutePillContent(iconOnly = false) }
        }.map { it.measure(Constraints()) }
        currentOnMeasured(IntSize(probe.maxOfOrNull { it.width } ?: 0, probe.maxOfOrNull { it.height } ?: 0))
        layout(0, 0) {}
    }
}

@Composable
private fun ReturnToRoutePillSurface(onClick: () -> Unit, iconOnly: Boolean, modifier: Modifier = Modifier) {
    val fill = mapChromeFill(navigationBarContainerColor(), overMap = true)
    val content = MaterialTheme.colorScheme.onSurfaceVariant
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(percent = 50),
        color = fill,
        contentColor = content,
        // Dispatch 2026-11-06-04 (RECORD -2186), the owner: "Add real quick: an outline around the re-enter button", "Same as
        // the chips": the selected map type chip's outline (MapModeChips.kt, mapModeChipBorder), 1 dp in `onSurface`, Bark
        // #3B2E24 by day and #EDE3D0 at night. Drawn inside the pill's bounds, so its size and touch area are unchanged.
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface),
        modifier = modifier
            .heightIn(min = RETURN_TO_ROUTE_PILL_HEIGHT)
            // D27: its icon alone is the minimum touch target, square, so what a finger reaches is what is drawn.
            .then(if (iconOnly) Modifier.size(RETURN_TO_ROUTE_PILL_HEIGHT) else Modifier)
            .testTag(RETURN_TO_ROUTE_TAG)
            .mapChromeContainerColor(fill)
            .mapChromeContentColor(content),
    ) {
        ReturnToRoutePillContent(iconOnly = iconOnly)
    }
}

/** The word with its icon, or (D27) the icon alone, centred, with the word as its content description so TalkBack still reads it. */
@Composable
private fun ReturnToRoutePillContent(iconOnly: Boolean) {
    if (iconOnly) {
        Box(contentAlignment = Alignment.Center) {
            Icon(imageVector = Icons.Filled.MyLocation, contentDescription = RETURN_TO_ROUTE_TEXT, modifier = Modifier.size(18.dp))
        }
        return
    }
    Row(
        modifier = Modifier.padding(horizontal = Spacing.md),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        Icon(imageVector = Icons.Filled.MyLocation, contentDescription = null, modifier = Modifier.size(18.dp))
        Text(text = RETURN_TO_ROUTE_TEXT, style = MaterialTheme.typography.labelLarge)
    }
}
