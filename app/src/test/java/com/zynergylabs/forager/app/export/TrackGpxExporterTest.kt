package com.zynergylabs.forager.app.export

import com.zynergylabs.forager.app.domain.GpxCodec
import com.zynergylabs.forager.app.domain.model.GpxDocument
import com.zynergylabs.forager.app.domain.model.Track
import com.zynergylabs.forager.app.domain.model.TrackPoint
import com.zynergylabs.forager.app.domain.model.TrackPointRecord
import com.zynergylabs.forager.app.domain.model.Waypoint
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * [TrackGpxExporter] is pure `java.io.File` plus [GpxCodec] — no Android dependency — so this runs
 * as a plain JVM test, no Robolectric needed. See that class's own doc comment, and
 * [com.zynergylabs.forager.app.crash.CrashFileStoreTest] for the same shape applied to its own sibling class.
 */
class TrackGpxExporterTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val track = Track(
        id = "track-1",
        name = null,
        // 2025-08-28T20:53:20Z in the JVM's default zone — the exact value doesn't matter, only
        // that the filename below is derived from it, not from "now".
        startedAtEpochMillis = 1_756_414_400_000L,
        endedAtEpochMillis = 1_756_414_460_000L,
        points = listOf(
            TrackPoint(lat = 45.0, lng = -122.0, altitude = 100.0, accuracyMeters = 5f, timestampEpochMillis = 1_756_414_400_000L),
            TrackPoint(lat = 45.001, lng = -122.0, altitude = 101.0, accuracyMeters = 5f, timestampEpochMillis = 1_756_414_415_000L),
        ),
    )
    private val fullRecord = track.points.map { TrackPointRecord(it, kept = true) }

    @Test
    fun `write creates the export directory if it does not exist yet, and returns a file that exists`() {
        val exportDir = tempFolder.newFolder("cache").resolve("tracks")
        val exporter = TrackGpxExporter(exportDir)

        val file = exporter.write(track, fullRecord = fullRecord, waypoints = emptyList())

        assertTrue(exportDir.isDirectory)
        assertTrue(file.exists())
    }

    @Test
    fun `the filename is derived from the track's own start time, not the export time`() {
        val exporter = TrackGpxExporter(tempFolder.newFolder("tracks"))

        val file = exporter.write(track, fullRecord = fullRecord, waypoints = emptyList())

        val expectedTimestamp = DateTimeFormatter.ofPattern("yyyy-MM-dd-HHmmss")
            .format(Instant.ofEpochMilli(track.startedAtEpochMillis).atZone(ZoneId.systemDefault()))
        assertEquals("forager-track-$expectedTimestamp.gpx", file.name)
    }

    @Test
    fun `exporting the same track twice overwrites the same file rather than creating a second one`() {
        val exporter = TrackGpxExporter(tempFolder.newFolder("tracks"))

        val first = exporter.write(track, fullRecord = fullRecord, waypoints = emptyList())
        // Read before the second write: first and second are two File handles to the identical
        // path, so reading first *after* overwriting would just read the second write's content
        // back, making the two sides of the comparison below trivially equal either way.
        val firstContent = first.readText()
        val doubled = track.copy(points = track.points + track.points)
        val second = exporter.write(doubled, fullRecord = doubled.points.map { TrackPointRecord(it, kept = true) }, waypoints = emptyList())

        assertEquals(first.absolutePath, second.absolutePath)
        assertTrue("expected the second write's extra point to be reflected", second.readText().count { it == '\n' } > firstContent.count { it == '\n' })
    }

    /**
     * The document the exporter builds is the codec's input verbatim, plus one thing the caller does
     * not supply: [exclusionRules], stamped by [TrackGpxExporter.write] itself because which rules
     * the read seam applies is a property of the build, not of the caller (GPX rule-provenance
     * dispatch, 2026-09-09). Spelled out as a literal on the expected side, and asserted directly
     * against the file text as well — a delegation test alone would stay green if the constant and
     * the file moved together to any value at all.
     */
    @Test
    fun `the written file's content is exactly what GpxCodec encode produces for this track`() {
        val exporter = TrackGpxExporter(tempFolder.newFolder("tracks"))

        val file = exporter.write(track, fullRecord = fullRecord, waypoints = emptyList())

        assertEquals(
            GpxCodec.encode(
                GpxDocument(track = track, waypoints = emptyList(), fullRecord = fullRecord, exclusionRules = listOf("timestampMillisNonZero")),
            ),
            file.readText(),
        )
        assertTrue("the file itself must declare the rule set in force", file.readText().contains("rule=\"timestampMillisNonZero\""))
        // The namespace a tester's actual file carries, hand-written: this is the production path,
        // and the URI is permanent in every file it writes.
        assertTrue(
            "the written file must bind the forager vocabulary to the controlled domain",
            file.readText().contains("xmlns:forager=\"https://zynergy-labs.com/forager/gpx/1\""),
        )
    }

    @Test
    fun `waypoints handed to write reach the file, same as GpxCodec encode would produce`() {
        val exporter = TrackGpxExporter(tempFolder.newFolder("tracks"))
        val waypoints = listOf(Waypoint(id = "w1", lat = 45.5, lng = -122.5, altitude = null, name = "Trailhead", note = "", createdAtEpochMillis = 1_756_414_400_000L))

        val file = exporter.write(track, fullRecord = fullRecord, waypoints = waypoints)

        assertEquals(
            GpxCodec.encode(
                GpxDocument(track = track, waypoints = waypoints, fullRecord = fullRecord, exclusionRules = listOf("timestampMillisNonZero")),
            ),
            file.readText(),
        )
        assertTrue("the waypoint's own id must reach the file", file.readText().contains("id=\"w1\""))
    }

    // ---- exports leave the cache after an hour (F5, dispatch 2026-09-28-216, owner "3 A") ------

    private fun File.agedMinutes(minutes: Long): File = apply { check(setLastModified(System.currentTimeMillis() - minutes * 60_000L)) }

    @Test
    fun `write first removes an export older than an hour and keeps one that is not`() {
        val dir = tempFolder.newFolder("tracks")
        val stale = File(dir, "forager-track-old.gpx").apply { writeText("<gpx/>") }.agedMinutes(61)
        val fresh = File(dir, "forager-track-recent.gpx").apply { writeText("<gpx/>") }.agedMinutes(59)

        val written = TrackGpxExporter(dir).write(track, fullRecord = fullRecord, waypoints = emptyList())

        assertFalse("an export 61 minutes old is still in the cache", stale.exists())
        assertTrue("an export 59 minutes old was deleted", fresh.exists())
        assertTrue("the new export is there", written.exists())
    }

    @Test
    fun `write leaves an old file that is not a gpx export alone`() {
        val dir = tempFolder.newFolder("tracks")
        val other = File(dir, "notes.txt").apply { writeText("keep") }.agedMinutes(600)

        TrackGpxExporter(dir).write(track, fullRecord = fullRecord, waypoints = emptyList())

        assertTrue("a file that is not a .gpx export was deleted", other.exists())
    }
}
