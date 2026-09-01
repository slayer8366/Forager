package com.forager.app.domain

import java.time.LocalDate
import java.time.ZoneId

/**
 * The one place a device-local calendar day is translated into both storage shapes this database
 * uses for "when" — Journal Stage 2a dispatch, owner decision #1: "day boundaries are device-local."
 * A find at 9pm belongs to that evening's trip, not tomorrow's; this type is what makes that true
 * for every date-scoped query rather than each one reasoning about it separately.
 *
 * ## The problem this reconciles
 *
 * [MushroomLogEntryEntity.foundOn] is a bare `yyyy-MM-dd` string with **no stored timezone** — it
 * records whatever `LocalDate.now()` (device-default zone, implicitly) resolved to at write time,
 * via [RoomMushroomLogRepository]'s existing `foundOn.toString()`/`LocalDate.parse(foundOn)`
 * round-trip. [TrackEntity.startedAtEpochMillis]/[TrackEntity.endedAtEpochMillis],
 * [WaypointEntity.createdAtEpochMillis], and [OfflineRegionEntity.createdAtEpochMillis] are all raw
 * UTC epoch millis, with no day boundary baked in at all.
 *
 * There is no way to recover *which* zone produced a stored [foundOnKey] after the fact — the
 * column simply doesn't carry one. This type does not attempt to: it treats [foundOnKey] as already
 * being the correct device-local day (true for every row this codebase has ever written, since
 * `LocalDate.now()` always resolves against the device's zone at call time) and independently
 * computes the epoch-millis boundaries for that same calendar date against **[zone] as it is right
 * now**, at query time — not some zone recorded in the past. A device that changes timezone between
 * writing a row and querying for it is a real edge case this cannot close (no column exists to close
 * it with); the query always reflects "this calendar date, in the zone the device is in right now."
 *
 * ## Why [ZoneId]-aware arithmetic, not `+ 86_400_000L`
 *
 * [epochMillisStartInclusive]/[epochMillisEndExclusive] are computed via [LocalDate.atStartOfDay],
 * not by adding a fixed 24-hour millisecond offset to midnight UTC — a day is not always exactly 24
 * hours of wall-clock time in a zone that observes daylight saving (a "spring forward" day is 23
 * hours; a "fall back" day is 25). [LocalDate.atStartOfDay] resolves the correct local midnight for
 * the zone given (and, per its own contract, the first valid instant after a spring-forward gap on
 * the rare day local midnight itself doesn't exist), so the half-open range this produces always
 * spans exactly the calendar day a person on that day would recognize, regardless of its true
 * wall-clock duration. See `LocalDayRangeTest`'s DST-transition-day case, which asserts this against
 * a real spring-forward date rather than trusting the reasoning above alone.
 *
 * ## The range is half-open: `[start, end)`
 *
 * [epochMillisEndExclusive] is the *next* day's own start, not this day's last millisecond —
 * matching this codebase's existing half-open convention elsewhere and avoiding an off-by-one at the
 * boundary. A query comparing against these two fields uses `>= start AND < end`, never `<= end`.
 */
data class LocalDayRange(
    val date: LocalDate,
    /** [MushroomLogEntryEntity.foundOn]'s own format — see this class's doc comment for why no zone conversion applies to this half. */
    val foundOnKey: String,
    val epochMillisStartInclusive: Long,
    val epochMillisEndExclusive: Long,
) {
    companion object {
        /** [zone] defaults to the device's current zone — pass an explicit one only in a test that needs a specific, fixed zone rather than wherever the test happens to run. */
        fun of(date: LocalDate, zone: ZoneId = ZoneId.systemDefault()): LocalDayRange {
            val startOfDay = date.atStartOfDay(zone).toInstant().toEpochMilli()
            val startOfNextDay = date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
            return LocalDayRange(
                date = date,
                foundOnKey = date.toString(),
                epochMillisStartInclusive = startOfDay,
                epochMillisEndExclusive = startOfNextDay,
            )
        }
    }
}
