package com.zynergylabs.forager.app.ui.route

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex

/*
 * The route card's edges (dispatch 2026-11-06-05, Amendment 1, RECORD -2187): its ✕ (step 3). Its outline, and since Amendment 6, A2,
 * its tucked strip's (the tab's before it), are drawn with its fill (RouteCardFace.kt). Kept out of RouteCard.kt, which is near the 400-line
 * mark (.claude/skills/modular-code/SKILL.md).
 */

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
