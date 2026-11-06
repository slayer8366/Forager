package com.zynergylabs.forager.app.domain.model

/**
 * A length of time as the owner writes it elsewhere in the app ("1 h 12 min", the sundown line's
 * style, `formatSundownDuration`), for the journal entry's report and editor (dispatch 2026-09-28-667,
 * data part A). Replaces "1h 12m" there, whose "m" sat next to a distance and read as metres
 * ("412 m · 48m", the data scout, section A1). Rounded down to the minute, so it never claims more time
 * than there was. Not the sundown formatter itself, which says "less than 1 min" in a countdown's words.
 *
 * The Journal list card still writes "2h 10m" through `trackSubtitle`; that card is not one of this
 * part's screens and is left as it was.
 */
fun formatTimeSpan(millis: Long): String {
    val minutes = millis.coerceAtLeast(0L) / 60_000L
    val hours = minutes / 60
    val rest = minutes % 60
    return when {
        minutes == 0L -> "Under 1 min"
        hours == 0L -> "$minutes min"
        rest == 0L -> "$hours h"
        else -> "$hours h $rest min"
    }
}
