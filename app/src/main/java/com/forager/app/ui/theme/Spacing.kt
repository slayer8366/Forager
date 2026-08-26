package com.forager.app.ui.theme

import androidx.compose.ui.unit.dp

/**
 * Understory step 3: promoted out of `AvailabilityScreen.kt`'s private `Spacing` object into the
 * theme, so every screen shares one scale instead of each file re-inventing gap values ad hoc. The
 * first four steps and their rationale are unchanged from that object; `xl`/`xxl` are new, added
 * because sheet/dialog padding and empty-state breathing room were being improvised without a
 * named step to reach for.
 */
object Spacing {
    /** Within a tightly related group — a line and its own subtext, one card's internal rows. */
    val xs = 4.dp

    /** Between related but distinct items — chips in a row, a card's own sub-sections. */
    val sm = 8.dp

    /** A card's outer padding, and the standard gap between sibling cards. */
    val md = 12.dp

    /** Screen-level padding, and the gap between major regions of a tab. */
    val lg = 16.dp

    /** Sheet and dialog internal padding. */
    val xl = 24.dp

    /** Empty-state and first-run breathing room. */
    val xxl = 32.dp
}
