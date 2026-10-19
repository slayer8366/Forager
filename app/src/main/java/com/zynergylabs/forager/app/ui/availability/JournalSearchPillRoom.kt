package com.zynergylabs.forager.app.ui.availability

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Dispatch 2026-10-18-08, RECORD -1330 to -1333 (the owner: "Hide the strip to have the chips line sit as a comfortable header.
 * When the search icon is tapped to reveal the search bar, then the line with the chips can slide down along with the search bar
 * to make room"; "No, the maps do not move at all"): on the Journal in a landscape window the switch line is the header and
 * slides down with the search pill, while a map under it keeps its place.
 *
 * The scaffold measures the pill's slot as it lays it out, frame by frame through its slide ([shownHeightPx]), and states the
 * pill's full height ([fullHeightPx]). The slot is measured before the Journal's content in the same layout pass (the pill is
 * above it in one Column, and the content takes the weighted rest), so a map reading both in its own measure ([journalSearchGap])
 * sees this frame's value, not the last one's: the room the pill has not yet taken is exactly the room the map stands below,
 * and the map's top and size do not move. Rejected: carrying the height through `onSizeChanged`, which reports after the layout
 * pass, so the map would trail the slide by a frame, the stutter the owner reported (-1318).
 */
internal class JournalSearchPillRoom {
    /** The pill's full height in landscape, the `landscapeHeight` the scaffold passes it. A state, so a change relays the map. */
    var fullHeightPx: Int by mutableIntStateOf(0)

    /**
     * The pill's slot as last measured: 0 while hidden, its full height while shown, between the two through its slide. Written
     * during layout, so not a state; a reader measured after it in the same pass reads the current frame.
     */
    var shownHeightPx: Int = 0
}

/** The Journal's [JournalSearchPillRoom] in a landscape window; null everywhere else, where nothing reads it. */
internal val LocalJournalSearchPillRoom = staticCompositionLocalOf<JournalSearchPillRoom?> { null }

/** RECORD -1330: the small gap between the Journal's switch line and a map under it, with the pill shown. */
internal val JOURNAL_MAP_TOP_GAP: Dp = 8.dp

/** The scaffold's side: records the pill slot's measured height in [room]. */
internal fun Modifier.measuresJournalSearchPill(room: JournalSearchPillRoom): Modifier = layout { measurable, constraints ->
    val placeable = measurable.measure(constraints)
    room.shownHeightPx = placeable.height
    layout(placeable.width, placeable.height) { placeable.place(0, 0) }
}

/**
 * A map's side, after its height is set (fillMaxHeight or weight): stands the map below the room the pill has not taken plus
 * [JOURNAL_MAP_TOP_GAP], so its top and its height hold wherever the switch line is. No-op without a [room].
 */
internal fun Modifier.journalSearchGap(room: JournalSearchPillRoom?): Modifier = if (room == null) {
    this
} else {
    layout { measurable, constraints ->
        if (!constraints.hasBoundedHeight) {
            val unbounded = measurable.measure(constraints)
            return@layout layout(unbounded.width, unbounded.height) { unbounded.place(0, 0) }
        }
        val gap = (room.fullHeightPx - room.shownHeightPx).coerceAtLeast(0) + JOURNAL_MAP_TOP_GAP.roundToPx()
        val height = (constraints.maxHeight - gap).coerceAtLeast(0)
        val placeable = measurable.measure(constraints.copy(minHeight = minOf(constraints.minHeight, height), maxHeight = height))
        layout(placeable.width, placeable.height + gap) { placeable.place(0, gap) }
    }
}
