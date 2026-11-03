package com.zynergylabs.forager.app.ui.route

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.layout.layout
import kotlin.math.roundToInt

/*
 * How much of the route card's rows show (dispatch 2026-10-30-08, Amendment 12, RECORD -1910). The owner: "when the card gets
 * expanded, simply have that info stay at the bottom while the card with the info slides up". The card is anchored at the bottom
 * of the map and the total line and buttons sit under the rows, so a line that stays put needs nothing of its own: only the
 * height of the rows changes.
 *
 * The rows are laid out at their full height and clipped to the part showing, measured from their top. So they ride up with the
 * handle, and the lower rows come out from behind the total line ("the rows slide back behind it" on the way down). That height
 * follows the finger during a drag and eases to the end on release or on a tap. It is written during layout, so a frame of the
 * slide lays out again without composing the rows again.
 */

private const val ROUTE_CARD_SLIDE_MILLIS = 220

@Stable
internal class RouteCardReveal(expanded: Boolean) {
    private val settled = Animatable(if (expanded) 1f else 0f)
    private var dragging by mutableStateOf(false)
    private var fullPx = 0

    /** How far this drag has moved, up being negative. */
    var draggedPx by mutableFloatStateOf(0f)
        private set

    /** The rows are in the card: a slide or a drag is under way, or they are open. */
    val showsRows: Boolean get() = dragging || settled.value > 0f

    fun shownPx(full: Int): Int {
        fullPx = full
        return (settled.value * full - draggedPx).roundToInt().coerceIn(0, full)
    }

    suspend fun grab() {
        settled.stop()
        draggedPx = 0f
        dragging = true
    }

    fun dragBy(delta: Float) {
        draggedPx += delta
    }

    /** The finger is off: carry on from where it left the rows, to [expanded]'s end. */
    suspend fun release(expanded: Boolean) {
        val full = fullPx
        if (full > 0) settled.snapTo(shownPx(full).toFloat() / full)
        draggedPx = 0f
        dragging = false
        settle(expanded)
    }

    suspend fun settle(expanded: Boolean) {
        settled.animateTo(if (expanded) 1f else 0f, tween(ROUTE_CARD_SLIDE_MILLIS))
    }
}

/** Lays this out at the height [reveal] shows of it, from its top, the rest clipped. */
internal fun Modifier.routeCardReveal(reveal: RouteCardReveal): Modifier = clipToBounds().layout { measurable, constraints ->
    val placeable = measurable.measure(constraints.copy(minHeight = 0))
    layout(placeable.width, reveal.shownPx(placeable.height)) { placeable.place(0, 0) }
}
