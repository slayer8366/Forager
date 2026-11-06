package com.zynergylabs.forager.app.ui.log

import com.zynergylabs.forager.app.domain.model.MushroomLogEntry
import com.zynergylabs.forager.app.ui.format.displayDate
import com.zynergylabs.forager.app.ui.format.displayTime
import java.time.Instant
import java.time.ZoneId

/**
 * A find's title, the one rule for its tile (Finds grid and Records logbook), its own pages (report and edit form) and its map
 * bubble (data part D; the owner in RECORD -656, -702 and -703).
 *
 * 1. The name the user gave it ([MushroomLogEntry.ownIdentification], never app-generated), so a title never names a species the
 *    user did not pick. -656: "keep the name they gave on the tile instead of the date".
 * 2. Else "Found 2:14 PM" (or "Found 14:14", the phone's 12/24-hour setting) from the time saved when the find was created
 *    ([MushroomLogEntry.foundAtEpochMillis], -703: "Option 1").
 * 3. Else the same from the earliest of its photos taken on its own day ([foundTimeMillis]); a photo from another day (an import)
 *    is not taken as the time it was found.
 * 4. Else `null`: no title text at all (-703: "keep it blank instead of showing "Unnamed find""). A blank title still carries
 *    [findBlankTitleLabel] for a screen reader.
 */
internal fun findTitle(entry: MushroomLogEntry, is24HourClock: Boolean, zone: ZoneId = ZoneId.systemDefault()): String? =
    entry.ownIdentification?.takeIf { it.isNotBlank() }
        ?: (entry.foundAtEpochMillis ?: foundTimeMillis(entry, zone))?.let { "Found ${displayTime(it, is24HourClock, zone)}" }

/** The earliest of [entry]'s photo times that falls on its [MushroomLogEntry.foundOn] in [zone], or `null` when none does. */
internal fun foundTimeMillis(entry: MushroomLogEntry, zone: ZoneId = ZoneId.systemDefault()): Long? =
    entry.photos.mapNotNull { it.createdAtEpochMillis }
        .filter { Instant.ofEpochMilli(it).atZone(zone).toLocalDate() == entry.foundOn }
        .minOrNull()

/** What a screen reader says for a find whose [findTitle] is blank: "Find, Oct 7, 2026" (-703). */
internal fun findBlankTitleLabel(entry: MushroomLogEntry): String = "Find, ${displayDate(entry.foundOn)}"

/** The title on a find's own pages (report and edit form), for tests. */
internal const val FIND_PAGE_TITLE_TAG = "find-page-title"
