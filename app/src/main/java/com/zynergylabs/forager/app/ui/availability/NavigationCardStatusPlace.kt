package com.zynergylabs.forager.app.ui.availability

import com.zynergylabs.forager.app.domain.DistanceKind
import com.zynergylabs.forager.app.domain.wayhome.WayHomeNote
import com.zynergylabs.forager.app.ui.map.COMPASS_CALIBRATING_TEXT
import com.zynergylabs.forager.app.ui.map.COMPASS_NORTH_UP_TEXT

/**
 * Dispatch 2026-11-05-10, Amendment 1, H8 (RECORD -2181), the owner, to "At the largest text size, the space under the sunset line is
 * narrow (about a third of the card in landscape). … What should happen at large text?": "Own line when too narrow (Recommended)", the
 * option reading "Where the space is too narrow for its words (largest text, narrow phones), status words go back to their own
 * full-width line under the card, as before. In daytime at that size the right side keeps no width, so "by trail" stays beside the
 * distance. At normal text nothing changes."
 *
 * The planner's reading of "too narrow": the status space is narrower than the widest single word any status can show at that size, so
 * no status ever breaks inside a word there; decided per window and font, never per status, so a status never jumps between places as
 * it changes ([statusBelowTheParts]).
 */

/**
 * Every text the card's status space can show (H2 to H4), as [NavigationReadout]'s status words and the compass notices: the way home's
 * and the way to a waypoint's notes and their short forms, the off-trail notices, the approach, the fix-age warnings at their widest
 * ("59 min"), the straight-line note at its widest, and the missing-origin message. The position statuses (no fix, approximate, last
 * seen) are the strip's and never shown here (RECORD -951).
 */
internal fun cardStatusTexts(): List<String> = buildList {
    add(COMPASS_CALIBRATING_TEXT)
    add(COMPASS_NORTH_UP_TEXT)
    add("Approaching")
    addAll(listOf(OFF_TRAIL_TEXT, OFF_TRAIL_SHORT_TEXT, WAYPOINT_OFF_TRAIL_SHORT_TEXT, BACK_ON_TRAIL_TEXT))
    for (note in WayHomeNote.entries) addAll(listOf(note.text, wayHomeNoteShortText(note)))
    for (note in WAY_TO_NOTES_ON_CARD) add(wayToWaypointNoteShortText(note))
    addAll(listOf("Last fix 59 min ago", "Approaching · last fix 59 min ago", "No fix for 59 min", "59 min old", "No fix 59 min"))
    for (unit in listOf("ft", "m")) add("≈ 8888 $unit ${DistanceKind.STRAIGHT.words}")
    addAll(listOf(NO_ORIGIN_TEXT, NO_ORIGIN_SHORT_TEXT))
}

/** The widest single word of [cardStatusTexts], by [widthOf]: what the status space must hold so that no word breaks inside itself. */
internal fun widestStatusWordPx(widthOf: (String) -> Int): Int =
    cardStatusTexts().flatMap { it.split(' ') }.filter { it.isNotEmpty() && it != "·" }.distinct().maxOf(widthOf)

/**
 * H8: whether the status words take their own full-width line under the parts, decided for this window and font. The status space is
 * the right part at its narrowest: the daytime width the right part keeps ([floorPx], H5 and Q3), or what the left part's widest need
 * ([leftNeedPx]) leaves of the parts ([partsPx], less the divider) where that is less. Too narrow for [widestWordPx], the words go under
 * the parts, as before H2.
 */
internal fun statusBelowTheParts(partsPx: Int, dividerPx: Int, leftNeedPx: Int, floorPx: Int, widestWordPx: Int): Boolean =
    minOf(floorPx, partsPx - dividerPx - leftNeedPx) < widestWordPx
