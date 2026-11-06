package com.zynergylabs.forager.app.ui.log

import com.zynergylabs.forager.app.domain.OfflineRegionSummary
import com.zynergylabs.forager.app.domain.model.MushroomLogEntry
import com.zynergylabs.forager.app.domain.model.Region
import com.zynergylabs.forager.app.domain.model.Track
import com.zynergylabs.forager.app.domain.model.Waypoint
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * [buildRecordsLogbook]'s grouping and order, headless. Rewritten whole by RECORD -868 ("In All, separate by category
 * and not by time/day entered, with Finds at the top"): the three cases here asserted the day grouping (days newest
 * first, finds first in their day, grouping in a given zone, the same-millisecond tie across kinds), which the ruling
 * removes. They now assert the kind grouping: sections in chip order, newest first inside, empty kinds left out.
 */
class RecordsLogbookTest {

    private val zone = ZoneId.of("America/Los_Angeles")
    private fun at(date: LocalDate, hour: Int): Long = date.atTime(hour, 0).atZone(zone).toInstant().toEpochMilli()

    private val d1 = LocalDate.of(2026, 9, 26)
    private val d2 = LocalDate.of(2026, 9, 25)

    private fun find(id: String, date: LocalDate, foundAt: Long? = null) =
        MushroomLogEntry.draft(id = id, location = null, date = date).copy(isDraft = false, foundAtEpochMillis = foundAt)
    private fun waypoint(id: String, millis: Long) = Waypoint(id = id, lat = 0.0, lng = 0.0, altitude = null, name = id, note = "", createdAtEpochMillis = millis)
    private fun track(id: String, millis: Long) = Track(id = id, name = null, startedAtEpochMillis = millis, endedAtEpochMillis = null, points = emptyList())
    private fun region(id: Long, millis: Long) = OfflineRegionSummary(id = id, name = "r$id", region = Region(0.0, 0.0, 1), minZoom = 0.0, maxZoom = 1.0, tileCount = 1, sizeBytes = 1, createdAtEpochMillis = millis)

    private fun ids(section: LogbookSection): List<String> = section.finds.map { it.id } + section.timed.map {
        when (it) {
            is TimedRecord.TrackRecord -> it.track.id
            is TimedRecord.WaypointRecord -> it.waypoint.id
            is TimedRecord.OfflineRegionRecord -> "r${it.region.id}"
        }
    }

    @Test
    fun `sections come in chip order, Finds, Tracks, Waypoints, Offline maps, each newest first, whatever the input order`() {
        val sections = buildRecordsLogbook(
            finds = listOf(find("F-old", d2), find("F-new", d1)),
            tracks = listOf(track("T-old", at(d2, 21)), track("T-new", at(d1, 15))),
            waypoints = listOf(waypoint("W-old", at(d2, 20)), waypoint("W-new", at(d1, 9))),
            offlineRegions = listOf(region(1, at(d2, 8)), region(2, at(d1, 12))),
        )

        assertEquals(listOf(RecordType.FINDS, RecordType.TRACKS, RecordType.WAYPOINTS, RecordType.OFFLINE_MAPS), sections.map { it.type })
        assertEquals(listOf("F-new", "F-old"), ids(sections[0]))
        assertEquals(listOf("T-new", "T-old"), ids(sections[1]))
        assertEquals(listOf("W-new", "W-old"), ids(sections[2]))
        assertEquals(listOf("r2", "r1"), ids(sections[3]))
        assertEquals(listOf(2, 2, 2, 2), sections.map { it.recordCount })
    }

    @Test
    fun `finds on one day go by the time they were found, newest first, and a find with no time comes after those with one, in input order`() {
        val section = buildRecordsLogbook(
            finds = listOf(find("no-time-1", d1), find("morning", d1, at(d1, 9)), find("no-time-2", d1), find("afternoon", d1, at(d1, 15)), find("older-day", d2, at(d2, 18))),
            tracks = emptyList(),
            waypoints = emptyList(),
            offlineRegions = emptyList(),
        ).single()

        assertEquals(listOf("afternoon", "morning", "no-time-1", "no-time-2", "older-day"), ids(section))
    }

    @Test
    fun `a kind with no records is left out, header and all`() {
        val sections = buildRecordsLogbook(
            finds = emptyList(),
            tracks = emptyList(),
            waypoints = listOf(waypoint("W", at(d1, 10))),
            offlineRegions = listOf(region(1, at(d1, 11))),
        )

        assertEquals(listOf(RecordType.WAYPOINTS, RecordType.OFFLINE_MAPS), sections.map { it.type })
    }

    @Test
    fun `records on the same millisecond keep their input order`() {
        val t = at(d1, 10)
        val section = buildRecordsLogbook(emptyList(), emptyList(), listOf(waypoint("W1", t), waypoint("W2", t)), emptyList()).single()
        assertEquals(listOf("W1", "W2"), ids(section))
    }

    @Test
    fun `nothing in, nothing out`() {
        assertEquals(emptyList<LogbookSection>(), buildRecordsLogbook(emptyList(), emptyList(), emptyList(), emptyList()))
    }
}
