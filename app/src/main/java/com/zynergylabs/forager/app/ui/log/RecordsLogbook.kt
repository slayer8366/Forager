package com.zynergylabs.forager.app.ui.log

import com.zynergylabs.forager.app.domain.OfflineRegionSummary
import com.zynergylabs.forager.app.domain.model.MushroomLogEntry
import com.zynergylabs.forager.app.domain.model.Track
import com.zynergylabs.forager.app.domain.model.Waypoint

/**
 * A record in the All logbook that carries a time: everything but a find.
 * [epochMillis] is what it is ordered by within its section.
 */
internal sealed interface TimedRecord {
    val epochMillis: Long

    data class TrackRecord(val track: Track) : TimedRecord {
        override val epochMillis: Long get() = track.startedAtEpochMillis
    }

    data class WaypointRecord(val waypoint: Waypoint) : TimedRecord {
        override val epochMillis: Long get() = waypoint.createdAtEpochMillis
    }

    data class OfflineRegionRecord(val region: OfflineRegionSummary) : TimedRecord {
        override val epochMillis: Long get() = region.createdAtEpochMillis
    }
}

/**
 * One section of the All logbook: one kind of record, newest first. A [RecordType.FINDS] section holds [finds] and no
 * [timed]; every other section holds [timed] and no [finds].
 */
internal data class LogbookSection(
    val type: RecordType,
    val finds: List<MushroomLogEntry> = emptyList(),
    val timed: List<TimedRecord> = emptyList(),
) {
    val recordCount: Int get() = finds.size + timed.size
}

/**
 * The All logbook's grouping and order. Pure Kotlin, no Compose, so it is unit-testable headless (CLAUDE.md,
 * Architecture).
 *
 * - **By kind, in chip order** (RECORD -868, the owner: "In All, separate by category and not by time/day entered,
 *   with Finds at the top"; "Finds, Tracks, Waypoints, Offline maps (Recommended)"): Finds, Tracks, Waypoints, Offline
 *   maps. Until -868 All was grouped by day (journal redesign J1, S4), days newest first and a day's finds first.
 * - **A kind with no records is left out**, header and all (the planner's call, RECORD -871).
 * - **Newest first inside each section.** Tracks by start, waypoints and regions by creation. A find carries a date,
 *   [MushroomLogEntry.foundOn], and sometimes the time it was found, [MushroomLogEntry.foundAtEpochMillis] (null for
 *   older finds and for a find created for another day). So finds go by date, newest first, and within a day by that
 *   time, newest first, a find with no time after those with one. The planner's call (RECORD -871) was "most recently
 *   saved first"; the app stores no time a find was saved, so the time it was found, written once when it is created,
 *   is the nearest it has (recorded in the dispatch's report).
 * - **Stable among equals.** Equal keys keep their input order ([finds] is `MushroomLogUiState.entries`, the Finds
 *   gallery's own order); Kotlin's `sortedWith` and `sortedByDescending` are stable.
 *
 * [finds] should be committed finds only: the owner's answer 3 ("Committed finds only"), the same list the Finds chip
 * counts.
 */
internal fun buildRecordsLogbook(
    finds: List<MushroomLogEntry>,
    tracks: List<Track>,
    waypoints: List<Waypoint>,
    offlineRegions: List<OfflineRegionSummary>,
): List<LogbookSection> = listOf(
    LogbookSection(
        type = RecordType.FINDS,
        finds = finds.sortedWith(
            compareByDescending<MushroomLogEntry> { it.foundOn }.thenByDescending { it.foundAtEpochMillis ?: Long.MIN_VALUE },
        ),
    ),
    LogbookSection(type = RecordType.TRACKS, timed = tracks.map { TimedRecord.TrackRecord(it) }.sortedByDescending { it.epochMillis }),
    LogbookSection(type = RecordType.WAYPOINTS, timed = waypoints.map { TimedRecord.WaypointRecord(it) }.sortedByDescending { it.epochMillis }),
    LogbookSection(type = RecordType.OFFLINE_MAPS, timed = offlineRegions.map { TimedRecord.OfflineRegionRecord(it) }.sortedByDescending { it.epochMillis }),
).filter { it.recordCount > 0 }
