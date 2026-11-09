package com.zynergylabs.forager.app.ui.map

import com.zynergylabs.forager.app.domain.JournalEntryOnMap
import com.zynergylabs.forager.app.ui.log.journalEntryCountLabel
import java.time.LocalDate
import com.zynergylabs.forager.app.ui.format.displayDate

/** The report menu's item for an entry not shown on the map (J8, the dispatch's copy). */
const val SHOW_ON_MAP_LABEL = "Show on map"

/** The report menu's item for an entry shown on the map (J8, the dispatch's copy). */
const val HIDE_FROM_MAP_LABEL = "Hide from map"

/** The Layers sheet's switch for every highlight at once (J8, the dispatch's copy). */
const val JOURNAL_ENTRIES_OVERLAY_LABEL = "Journal entries"

/** The chip list's action for one entry (J8, the dispatch's copy). */
const val HIDE_JOURNAL_ENTRY_LABEL = "Hide"

/** The chip list's action for every entry (J8, the dispatch's copy). */
const val HIDE_ALL_JOURNAL_ENTRIES_LABEL = "Hide all"

/** The Maps-tab chip's text (J8, the dispatch's pattern): "N journal entries on map", singular for one. */
fun journalEntriesChipLabel(count: Int): String =
    if (count == 1) "1 journal entry on map" else "$count journal entries on map"

/**
 * An entry's date as the Journal's report header shows it, "Oct 7, 2026": the map's journal menu and its bubbles' date lines.
 * The planner's earlier ISO answer (Q3, `2026-09-12`) is superseded by the owner's "Use 'Oct 7, 2026' everywhere" (RECORD -656,
 * applied by -702).
 */
fun journalEntryDateLabel(date: LocalDate): String = displayDate(date)

/** A bubble date line's accessibility label (owner's Q2 ruling, "Tap a date line"). */
fun openEntryAccessibilityLabel(date: String): String = "Open entry $date"

/** One keeping entry as a bubble line: its id, its date and the line's accessibility label. */
data class EntryLine(val entryId: String, val date: String, val accessibilityLabel: String)

/** What a highlighted record's bubble says about the shown entries that keep it. */
sealed interface KeptInEntriesLines {
    /** Three or fewer: one tappable date line each. */
    data class Dates(val lines: List<EntryLine>) : KeptInEntriesLines

    /** More than three: one tappable count line that opens an untitled list of [entries]. */
    data class Count(val label: String, val entries: List<EntryLine>) : KeptInEntriesLines
}

/**
 * What a highlighted record's bubble says about [entries], the shown entries that keep it (owner's Q2
 * ruling, "Tap a date line"): nothing for none; one date line each for up to three; past three, one
 * line, "In N journal entries" (was "Kept in"; the owner, RECORD -671) — the photo bubble's own wording and plural
 * ([journalEntryCountLabel]) — over the list of their dates.
 */
fun keptInEntriesLines(entries: List<JournalEntryOnMap>): KeptInEntriesLines? {
    if (entries.isEmpty()) return null
    val lines = entries.map { entry ->
        val date = journalEntryDateLabel(entry.date)
        EntryLine(entry.entryId, date, openEntryAccessibilityLabel(date))
    }
    return if (lines.size <= MAX_ENTRY_DATE_LINES) {
        KeptInEntriesLines.Dates(lines)
    } else {
        KeptInEntriesLines.Count("In ${journalEntryCountLabel(lines.size)}", lines)
    }
}

/** The most keeping entries a bubble lists one by one (the dispatch: "Where there are more than three, show a count"). */
private const val MAX_ENTRY_DATE_LINES = 3
