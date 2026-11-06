package com.zynergylabs.forager.app.ui.format

import android.text.format.DateFormat
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * The app's one date and clock formatter (data part D, RECORD -656 and -702).
 *
 * A calendar date reads "Oct 7, 2026" everywhere (the owner, -656: "Use 'Oct 7, 2026' everywhere"). Data parts A and C each
 * added a copy (`ui/log/EntryDates.kt`'s `formatEntryDate`, `ui/availability/DisplayDates.kt`'s `displayDate`); part D asked for
 * one shared formatter, so both moved here and the copies are gone. A clock time follows the phone's 12- or 24-hour setting
 * ("2:14 PM" or "14:14"; -656: "times follow the phone's 12- or 24-hour setting"), read once per composition by
 * [is24HourClock] and passed in, so the pure functions here stay testable without an Android context.
 *
 * The phone's language names the month, read at each call, so a language change is picked up without a restart.
 */
internal fun displayDate(date: LocalDate): String =
    date.format(DateTimeFormatter.ofPattern(DATE_PATTERN, Locale.getDefault()))

/** A clock time, "2:14 PM" or "14:14" by [is24HourClock], in [zone]. */
internal fun displayTime(epochMillis: Long, is24HourClock: Boolean, zone: ZoneId = ZoneId.systemDefault()): String =
    Instant.ofEpochMilli(epochMillis).atZone(zone)
        .format(DateTimeFormatter.ofPattern(if (is24HourClock) TIME_24_PATTERN else TIME_12_PATTERN, Locale.getDefault()))

/** A date and a clock time, "Oct 7, 2026, 2:14 PM" or "Oct 7, 2026, 14:14", in [zone]. */
internal fun displayDateTime(epochMillis: Long, is24HourClock: Boolean, zone: ZoneId = ZoneId.systemDefault()): String =
    "${displayDate(Instant.ofEpochMilli(epochMillis).atZone(zone).toLocalDate())}, ${displayTime(epochMillis, is24HourClock, zone)}"

/** The phone's 12- or 24-hour setting, as Android reports it ([DateFormat.is24HourFormat]). */
@Composable
internal fun is24HourClock(): Boolean = DateFormat.is24HourFormat(LocalContext.current)

private const val DATE_PATTERN = "MMM d, yyyy"
private const val TIME_12_PATTERN = "h:mm a"
private const val TIME_24_PATTERN = "HH:mm"
