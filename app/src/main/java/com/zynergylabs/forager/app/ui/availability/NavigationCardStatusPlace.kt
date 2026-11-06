package com.zynergylabs.forager.app.ui.availability

import com.zynergylabs.forager.app.domain.DistanceKind
import com.zynergylabs.forager.app.domain.wayhome.WayHomeNote
import com.zynergylabs.forager.app.ui.map.COMPASS_CALIBRATING_TEXT

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
    add(CARD_NO_COMPASS_TEXT)
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

/**
 * H10 (RECORD -2181), the owner: "The more urgent one (Recommended)", the option reading "Where status words take their own line (large
 * text), only one shows: "No fix 6 min" ahead of "No compass". The card stays one line taller at most, and the route card keeps its
 * buttons. At normal text both show as now." The planner's reading: "under H8, one status line at most, by the existing priority.
 * Fix-age warnings come ahead of the compass notice, which comes ahead of other words."
 *
 * So, of the status words ([status], already the readout's one status by its own order, a fix's age ahead of every other) and the
 * compass notice ([notice]), the pair to draw: the status alone where it warns of the fix's age ([statusIsFixAge]); else the notice
 * alone where there is one; else the status words. Used only under the parts (H8); in the right part both show, as before.
 */
internal fun oneStatusLine(status: String, notice: String?, statusIsFixAge: Boolean): Pair<String, String?> = when {
    statusIsFixAge && status.isNotEmpty() -> status to null
    notice != null -> "" to notice
    else -> status to null
}

/** H9: the card's north-up notice, the owner's "No compass". The strip's own words ("Compass unavailable") are unchanged. */
internal const val CARD_NO_COMPASS_TEXT = "No compass"
