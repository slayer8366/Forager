package com.forager.app.export

import com.forager.app.domain.GpxCodec
import com.forager.app.domain.model.GpxDocument
import com.forager.app.domain.model.Track
import com.forager.app.domain.model.TrackPoint
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * [TrackGpxExporter] is pure `java.io.File` plus [GpxCodec] — no Android dependency — so this runs
 * as a plain JVM test, no Robolectric needed. See that class's own doc comment, and
 * [com.forager.app.crash.CrashFileStoreTest] for the same shape applied to its own sibling class.
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

    @Test
    fun `write creates the export directory if it does not exist yet, and returns a file that exists`() {
        val exportDir = tempFolder.newFolder("cache").resolve("tracks")
        val exporter = TrackGpxExporter(exportDir)

        val file = exporter.write(track)

        assertTrue(exportDir.isDirectory)
        assertTrue(file.exists())
    }

    @Test
    fun `the filename is derived from the track's own start time, not the export time`() {
        val exporter = TrackGpxExporter(tempFolder.newFolder("tracks"))

        val file = exporter.write(track)

        val expectedTimestamp = DateTimeFormatter.ofPattern("yyyy-MM-dd-HHmmss")
            .format(Instant.ofEpochMilli(track.startedAtEpochMillis).atZone(ZoneId.systemDefault()))
        assertEquals("forager-track-$expectedTimestamp.gpx", file.name)
    }

    @Test
    fun `exporting the same track twice overwrites the same file rather than creating a second one`() {
        val exporter = TrackGpxExporter(tempFolder.newFolder("tracks"))

        val first = exporter.write(track)
        // Read before the second write: first and second are two File handles to the identical
        // path, so reading first *after* overwriting would just read the second write's content
        // back, making the two sides of the comparison below trivially equal either way.
        val firstContent = first.readText()
        val second = exporter.write(track.copy(points = track.points + track.points))

        assertEquals(first.absolutePath, second.absolutePath)
        assertTrue("expected the second write's extra point to be reflected", second.readText().count { it == '\n' } > firstContent.count { it == '\n' })
    }

    @Test
    fun `the written file's content is exactly what GpxCodec encode produces for this track`() {
        val exporter = TrackGpxExporter(tempFolder.newFolder("tracks"))

        val file = exporter.write(track)

        assertEquals(
            GpxCodec.encode(GpxDocument(track = track, waypoints = emptyList())),
            file.readText(),
        )
    }
}
