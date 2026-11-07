package com.zynergylabs.forager.app.ui.route

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex

/*
 * The route card's edges (dispatch 2026-11-06-05, Amendment 1, RECORD -2187): its outline (step 6) and its ✕ (step 3). Kept out of
 * RouteCard.kt, which is near the 400-line mark (.claude/skills/modular-code/SKILL.md).
 */

/** The screen edge a card or its tab sits flush against, which its outline leaves open (step 6). [NONE]: outlined all round. */
enum class RouteCardEdge { NONE, LEFT, RIGHT, BOTTOM }

/** The outline's width: Re-center's (#505, `ReturnToRoutePill.kt`), 1 dp. */
internal val ROUTE_CARD_OUTLINE_WIDTH: Dp = 1.dp

/** How far the outline's box reaches past the open edge, so the card's own clip cuts that side's stroke off. */
private val OUTLINE_PAST_EDGE = 2.dp

/**
 * Step 6, the owner: the card gets "the same 1 dp `onSurface` outline as the Re-center pill (#505), on the three sides facing the map,
 * not the side flush to the screen edge". Drawn over the card's content, inside its bounds, in [shape]; the box it is drawn on reaches
 * [OUTLINE_PAST_EDGE] past [openEdge], where the card's clip (its Surface's shape) cuts it, so that side carries no stroke. It takes no
 * touch and changes no size.
 */
@Composable
internal fun BoxScope.RouteCardOutline(shape: Shape, openEdge: RouteCardEdge) {
    Box(
        Modifier
            .matchParentSize()
            .layout { measurable, constraints ->
                val past = OUTLINE_PAST_EDGE.roundToPx()
                val w = constraints.maxWidth + if (openEdge == RouteCardEdge.LEFT || openEdge == RouteCardEdge.RIGHT) past else 0
                val h = constraints.maxHeight + if (openEdge == RouteCardEdge.BOTTOM) past else 0
                val placeable = measurable.measure(androidx.compose.ui.unit.Constraints.fixed(w, h))
                layout(constraints.maxWidth, constraints.maxHeight) { placeable.place(if (openEdge == RouteCardEdge.LEFT) -past else 0, 0) }
            }
            .border(BorderStroke(ROUTE_CARD_OUTLINE_WIDTH, MaterialTheme.colorScheme.onSurface), shape)
            .testTag(ROUTE_CARD_OUTLINE_TAG),
    )
}

/**
 * Step 3, the owner: "X clears the route" ("Closing removes the card and the route from the map, as if the route was dismissed"). On a
 * saved route's card only (while plotting, Cancel does it, as today), at its top end corner over the handle's row, a 48 dp touch target
 * (Material's minimum), square, drawn as a 20 dp ✕. Above the handle in touch order, so a tap there is the ✕'s, not the handle's.
 */
@Composable
internal fun BoxScope.RouteCardCloseButton(onClose: () -> Unit) {
    // A square target, not IconButton's: IconButton clips its click to a circle, so the corners of its 48 dp took no touch.
    Box(
        modifier = Modifier
            .align(Alignment.TopEnd)
            .zIndex(2f)
            .size(ROUTE_CARD_CLOSE_TOUCH)
            .testTag(ROUTE_CARD_CLOSE_TAG)
            .clickable(role = Role.Button, onClickLabel = null, onClick = onClose),
        contentAlignment = Alignment.Center,
    ) {
        Icon(Icons.Filled.Close, contentDescription = "Close route", modifier = Modifier.size(20.dp))
    }
}

/** The ✕'s touch target, square. The saved route's name keeps clear of it by this less the card's side padding. */
internal val ROUTE_CARD_CLOSE_TOUCH: Dp = 48.dp

const val ROUTE_CARD_CLOSE_TAG = "route-card-close"
const val ROUTE_CARD_OUTLINE_TAG = "route-card-outline"
