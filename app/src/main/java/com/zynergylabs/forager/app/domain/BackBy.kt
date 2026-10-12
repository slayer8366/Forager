package com.zynergylabs.forager.app.domain

import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * "Back by": a check-in time the walker sets while recording, which alerts if they are still out
 * when it passes (dispatch 2026-09-28-645, plan task T15; Amendments 1 and 2, RECORD -646 and -647).
 * The owner's path: `Record > the strip's gear > Back by > +1 h, +2 h, +3 h or Pick a time`. Only
 * while recording; off unless set; no Settings switch. What it decides is [BackByWatch]'s; this file
 * holds the rules that need no state.
 */
sealed interface BackByChoice {
    /** "+1 h", "+2 h", "+3 h": counted from now, never from a time already set (Amendment 1's menu). */
    data class HoursFromNow(val hours: Int) : BackByChoice

    /** "Pick a time…": a clock time; one not later than now means tomorrow (the owner, Amendment 1: "A picked time earlier than now means tomorrow"). */
    data class AtTime(val hour: Int, val minute: Int) : BackByChoice
}

/** The three quick choices, in the menu's order. */
val BACK_BY_QUICK_HOURS: List<Int> = listOf(1, 2, 3)

/**
 * The moment [choice] names, from [nowEpochMillis] in [zone]. A picked time that is not later than
 * now is tomorrow's, the way an alarm clock sets the next occurrence. Built on the zone's calendar,
 * so a picked 3:30 PM is 3:30 PM on the clock across a daylight-saving change.
 */
fun backByAtFor(choice: BackByChoice, nowEpochMillis: Long, zone: ZoneId): Long = when (choice) {
    is BackByChoice.HoursFromNow -> nowEpochMillis + choice.hours * HOUR_MILLIS
    is BackByChoice.AtTime -> {
        val now = ZonedDateTime.ofInstant(Instant.ofEpochMilli(nowEpochMillis), zone)
        val today = ZonedDateTime.of(now.toLocalDate(), LocalTime.of(choice.hour, choice.minute), zone)
        val at = if (today.isAfter(now)) today else today.plusDays(1)
        at.toInstant().toEpochMilli()
    }
}

/**
 * "+30 min" on the alert (the owner: "alerts again in 30 minutes"): the new back-by time, from now.
 * The strip and the next alert then say this time (Amendment 1: "After '+30 min' it shows the new time").
 */
const val BACK_BY_LATER_MILLIS: Long = 30L * 60L * 1_000L

/**
 * The strip's "Back by 3:30 PM" shows only in the last hour before the time (the owner: "Yes, show
 * in last hour (Recommended)"), and stays once the time has passed, while the reminder is still set.
 * At exactly one hour it shows, as the sundown line shows at exactly its window.
 */
const val BACK_BY_LINE_WINDOW_MILLIS: Long = 60L * 60L * 1_000L

/** Whether the strip (or the navigation display) shows the back-by line at [nowEpochMillis]. */
fun backByLineShown(backByAtEpochMillis: Long, nowEpochMillis: Long): Boolean =
    backByAtEpochMillis - nowEpochMillis <= BACK_BY_LINE_WINDOW_MILLIS

/**
 * What [BackByWatch] publishes for the recording [trackId]: the time set, and the clock at the
 * moment it published, which the line's window is read against. Republished on every tick, so the
 * window opens within one tick of its time.
 */
data class BackByShown(val trackId: String, val backByAtEpochMillis: Long, val nowEpochMillis: Long) {
    val lineShown: Boolean get() = backByLineShown(backByAtEpochMillis, nowEpochMillis)
}

/**
 * What the screen may do with Back by: set and clear it, and read it. [BackByWatch] in production;
 * [NoBackBy] where nothing is wired, so a ViewModel built without it shows none and sets none.
 */
interface BackByControl {
    val shown: kotlinx.coroutines.flow.StateFlow<BackByShown?>

    /** Sets the back-by time for [trackId]; `false` when refused (a different recording is being watched). */
    fun set(trackId: String, atEpochMillis: Long): Boolean

    /** Clears it for [trackId]: the menu's "Clear". */
    fun clear(trackId: String)

    /** A choice made with no recording on the screen, so nothing is set: recorded, not silent (dispatch 2026-09-28-796). */
    fun setWithNoRecording()
}

/** No Back by: shows none, sets none. */
object NoBackBy : BackByControl {
    override val shown: kotlinx.coroutines.flow.StateFlow<BackByShown?> = kotlinx.coroutines.flow.MutableStateFlow(null)
    override fun set(trackId: String, atEpochMillis: Long): Boolean = false
    override fun clear(trackId: String) = Unit
    override fun setWithNoRecording() = Unit
}

private const val HOUR_MILLIS = 60L * 60L * 1_000L
