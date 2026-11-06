package com.zynergylabs.forager.app.domain

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.zynergylabs.forager.app.data.local.ForagerDatabase
import com.zynergylabs.forager.app.data.repository.RoomTrackRepository
import com.zynergylabs.forager.app.data.repository.RoomWaypointRepository
import com.zynergylabs.forager.app.domain.model.Track
import com.zynergylabs.forager.app.domain.model.TrackPoint
import com.zynergylabs.forager.app.domain.model.Waypoint
import com.zynergylabs.forager.app.domain.model.WaypointDesignation
import com.zynergylabs.forager.app.export.TrackGpxExporter
import java.io.File
import java.time.Instant
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Plan T16, GPX import (dispatch 2026-09-28-634, the owner's answers in RECORD -636), through the one
 * entry both ways in share, [ImportGpxUseCase], over a real in-memory Room database and the real
 * repositories: every assertion reads back what was stored, through the same read seam Records uses
 * ([TrackRepository.getById], which applies the network-fix rule), not what the use case returned.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ImportGpxUseCaseTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private lateinit var database: ForagerDatabase
    private lateinit var tracks: RoomTrackRepository
    private lateinit var waypoints: RoomWaypointRepository
    private val logged = mutableListOf<String>()
    private val errorLog = ErrorLog { tag, message, _ -> logged += "$tag: $message" }
    private var nextId = 0

    /** The import moment: 2026-10-07T12:34:56.789Z, deliberately not on a whole second. */
    private val now = Instant.parse("2026-10-07T12:34:56.789Z").toEpochMilli()

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Application>(), ForagerDatabase::class.java).build()
        tracks = RoomTrackRepository(database.trackDao())
        waypoints = RoomWaypointRepository(database.waypointDao())
    }

    @After
    fun tearDown() = database.close()

    private fun useCase(trackRepository: TrackRepository = tracks) =
        ImportGpxUseCase(trackRepository, waypoints, currentTime = { now }, errorLog = errorLog, idGenerator = { "id-${++nextId}" })

    private fun ms(iso: String) = Instant.parse(iso).toEpochMilli()

    private suspend fun storedTracks(): List<Track> = tracks.getAll().getOrThrow().sortedBy { it.startedAtEpochMillis }

    private suspend fun imported(outcome: GpxImportOutcome): List<Track> {
        assertTrue("expected an import, got $outcome", outcome is GpxImportOutcome.Imported)
        return (outcome as GpxImportOutcome.Imported).trackIds.map { tracks.getById(it).getOrThrow()!! }
    }

    // ---- the app's own export, round trip --------------------------------------------------------

    @Test
    fun `importing the app's own export gives back the same points, times, name and waypoints`() = runTest {
        // A recorded walk as it is stored: whole-second GPS fixes and one network fix (milliseconds), which
        // the read seam hides and the export's <trkseg> leaves out; an origin waypoint and a dropped one.
        val original = Track(id = "walk", name = "Ridge loop", startedAtEpochMillis = ms("2026-09-20T15:00:00Z"), endedAtEpochMillis = ms("2026-09-20T16:00:00Z"), points = emptyList())
        tracks.create(original).getOrThrow()
        tracks.appendPoints(
            "walk",
            listOf(
                TrackPoint(45.5, -122.6, 100.0, 4f, ms("2026-09-20T15:00:00Z")),
                TrackPoint(45.5007, -122.6003, null, 30f, ms("2026-09-20T15:00:05.321Z")),
                TrackPoint(45.501, -122.601, 101.5, 5f, ms("2026-09-20T15:00:10Z")),
                TrackPoint(45.502, -122.602, 103.25, 5f, ms("2026-09-20T15:59:59Z")),
            ),
        ).getOrThrow()
        val originWaypoint = Waypoint("w-origin", 45.5, -122.6, 100.0, "Start", "", ms("2026-09-20T15:00:00Z"), trackId = "walk", designation = WaypointDesignation.ORIGIN)
        val dropped = Waypoint("w-drop", 45.501, -122.601, null, "Chanterelles & co <3", "Under the \"big\" fir", ms("2026-09-20T15:20:00Z"), trackId = "walk")
        waypoints.save(originWaypoint).getOrThrow()
        waypoints.save(dropped).getOrThrow()
        val shown = tracks.getById("walk").getOrThrow()!!
        assertEquals("the walk as Records shows it: three points, the network fix hidden", 3, shown.points.size)
        val file: File = TrackGpxExporter(tmp.newFolder("gpx"), errorLog).write(shown, tracks.getFullRecord("walk").getOrThrow(), listOf(originWaypoint, dropped))

        val back = imported(useCase()(GpxImportFixtures.bytesSource(file.readBytes(), file.name))).single()

        assertEquals("Ridge loop", back.name)
        assertEquals("points and times, exactly as Records showed them", shown.points.map { listOf(it.lat, it.lng, it.altitude, it.timestampEpochMillis) }, back.points.map { listOf(it.lat, it.lng, it.altitude, it.timestampEpochMillis) })
        assertEquals(shown.startedAtEpochMillis, back.startedAtEpochMillis)
        assertEquals(ms("2026-09-20T15:59:59Z"), back.endedAtEpochMillis)
        assertEquals("nothing left out at the read seam", 0, back.excludedPointCount)
        assertEquals(now, back.importedAtEpochMillis)
        assertFalse(back.importedWithoutTimes)
        assertNotEquals("a fresh track id, never the original's", "walk", back.id)
        assertNull("no origin pointer from the file", back.originWaypointId)

        val backWaypoints = waypoints.getForTrack(back.id).getOrThrow()
        assertEquals(
            "waypoints: the same places, names, notes, times and designations",
            listOf(originWaypoint, dropped).map { listOf(it.lat, it.lng, it.altitude, it.name, it.note, it.createdAtEpochMillis, it.designation) },
            backWaypoints.map { listOf(it.lat, it.lng, it.altitude, it.name, it.note, it.createdAtEpochMillis, it.designation) },
        )
        assertTrue("fresh waypoint ids, never the file's", backWaypoints.none { it.id == "w-origin" || it.id == "w-drop" })
        assertEquals("the originals are untouched: still on the recorded walk", listOf(originWaypoint, dropped), waypoints.getForTrack("walk").getOrThrow())
        assertEquals("two tracks now, the original and its import", 2, storedTracks().size)
    }

    @Test
    fun `importing the same file twice makes two tracks and never overwrites a waypoint`() = runTest {
        val first = imported(useCase()(GpxImportFixtures.source(GpxImportFixtures.SHAPE_B))).single()
        val second = imported(useCase()(GpxImportFixtures.source(GpxImportFixtures.SHAPE_B))).single()

        assertNotEquals(first.id, second.id)
        assertEquals(2, waypoints.getForTrack(first.id).getOrThrow().size)
        assertEquals(2, waypoints.getForTrack(second.id).getOrThrow().size)
        assertEquals("four waypoints in all", 4, waypoints.getAll().getOrThrow().size)
    }

    // ---- other apps' files ---------------------------------------------------------------------

    @Test
    fun `a shape A file imports with its name, elevations and times`() = runTest {
        val track = imported(useCase()(GpxImportFixtures.source(GpxImportFixtures.SHAPE_A, "Morning_Hike.gpx"))).single()

        assertEquals("Morning Hike", track.name)
        assertEquals(listOf(100.2, 101.0, 102.4), track.points.map { it.altitude })
        assertEquals(listOf(ms("2026-09-20T15:00:00Z"), ms("2026-09-20T15:00:10Z"), ms("2026-09-20T15:00:20Z")), track.points.map { it.timestampEpochMillis })
        assertEquals(ms("2026-09-20T15:00:00Z"), track.startedAtEpochMillis)
        assertEquals(ms("2026-09-20T15:00:20Z"), track.endedAtEpochMillis)
    }

    @Test
    fun `a shape B file joins its segments, rounds its times to the second, and names its waypoints`() = runTest {
        val track = imported(useCase()(GpxImportFixtures.source(GpxImportFixtures.SHAPE_B))).single()

        assertEquals("the file's own <metadata> name, the track having none", "Chanterelle ridge", track.name)
        assertEquals(
            "both segments, in file order; .400 rounds down, .600 up, and none is hidden as a network fix",
            listOf(ms("2026-09-21T16:00:00Z"), ms("2026-09-21T16:00:06Z"), ms("2026-09-21T16:10:00Z")),
            track.points.map { it.timestampEpochMillis },
        )
        assertEquals(0, track.excludedPointCount)
        val wps = waypoints.getForTrack(track.id).getOrThrow()
        assertEquals(listOf("Waypoint 2", "Patch"), wps.map { it.name })
        val patch = wps.single { it.name == "Patch" }
        assertEquals(ms("2026-09-21T16:05:01Z"), patch.createdAtEpochMillis)
        assertEquals("Golden, under hemlock", patch.note)
        assertEquals(300.0, patch.altitude)
        assertEquals("an untimed waypoint takes the track's start", track.startedAtEpochMillis, wps.single { it.name == "Waypoint 2" }.createdAtEpochMillis)
    }

    @Test
    fun `a shape C file with offset times and no names is named from the file`() = runTest {
        val track = imported(useCase()(GpxImportFixtures.source(GpxImportFixtures.SHAPE_C, "2026-09-22_09-00_Tue.gpx"))).single()

        assertEquals("2026-09-22_09-00_Tue", track.name)
        assertEquals(listOf(ms("2026-09-22T07:00:00Z"), ms("2026-09-22T07:01:00Z")), track.points.map { it.timestampEpochMillis })
        assertEquals(listOf(1200.0, 1205.0), track.points.map { it.altitude })
    }

    // ---- several tracks ------------------------------------------------------------------------

    @Test
    fun `a file with three tracks makes three, and its loose waypoints go with the first`() = runTest {
        val outcome = useCase()(GpxImportFixtures.source(GpxImportFixtures.MULTI_TRACK, "Three days.gpx"))
        val three = imported(outcome)

        assertEquals(listOf("Day one", "Three days (2)", "Day three"), three.map { it.name })
        assertEquals(listOf(2, 2, 2), three.map { it.points.size })
        assertEquals(listOf("Car", "Spring"), waypoints.getForTrack(three[0].id).getOrThrow().map { it.name })
        assertTrue(waypoints.getForTrack(three[1].id).getOrThrow().isEmpty())
        assertTrue(waypoints.getForTrack(three[2].id).getOrThrow().isEmpty())
        assertTrue("every one ended", three.all { it.endedAtEpochMillis != null && it.importedAtEpochMillis == now })
    }

    // ---- no times ------------------------------------------------------------------------------

    @Test
    fun `a file with no times is dated the import moment, keeps its file order, and says it has no times`() = runTest {
        val track = imported(useCase()(GpxImportFixtures.source(GpxImportFixtures.NO_TIMES))).single()
        val start = ms("2026-10-07T12:34:56Z")

        assertTrue(track.importedWithoutTimes)
        assertEquals("the import moment, on a whole second", start, track.startedAtEpochMillis)
        assertEquals("an end time always: a track with none reads as recording", start + 2_000L, track.endedAtEpochMillis)
        assertEquals("every point kept, in file order", listOf(45.5, 45.4, 45.6), track.points.map { it.lat })
        assertEquals("an ordering one second apart, none hidden", listOf(start, start + 1_000L, start + 2_000L), track.points.map { it.timestampEpochMillis })
        val waypoint = waypoints.getForTrack(track.id).getOrThrow().single()
        assertEquals("Waypoint 1", waypoint.name)
        assertEquals(start, waypoint.createdAtEpochMillis)
    }

    @Test
    fun `a recorded walk reads as not imported and with its times`() = runTest {
        tracks.create(Track("rec", null, 1_000L, 2_000L, emptyList())).getOrThrow()

        val walk = tracks.getById("rec").getOrThrow()!!

        assertNull(walk.importedAtEpochMillis)
        assertFalse(walk.importedWithoutTimes)
    }

    // ---- files that are not imported -------------------------------------------------------------

    @Test
    fun `files that cannot be imported say why and store nothing`() = runTest {
        val cases = listOf(
            GpxImportFixtures.BROKEN to GpxImportFailure.UNREADABLE,
            GpxImportFixtures.NOT_GPX to GpxImportFailure.UNREADABLE,
            GpxImportFixtures.BAD_COORDINATES to GpxImportFailure.UNREADABLE,
            GpxImportFixtures.ROUTE_ONLY to GpxImportFailure.ROUTE_ONLY,
            GpxImportFixtures.WAYPOINTS_ONLY to GpxImportFailure.NO_TRACK,
            "" to GpxImportFailure.UNREADABLE,
        )
        for ((xml, reason) in cases) {
            assertEquals("for ${xml.take(60)}", GpxImportOutcome.Failed(reason), useCase()(GpxImportFixtures.source(xml)))
        }
        assertTrue(storedTracks().isEmpty())
        assertTrue(waypoints.getAll().getOrThrow().isEmpty())
        assertEquals("each failure is logged", cases.size, logged.size)
    }

    @Test
    fun `an external entity is never read, and the file is refused`() = runTest {
        val secret = tmp.newFile("secret.txt").apply { writeText("TOP-SECRET") }

        val outcome = useCase()(GpxImportFixtures.source(GpxImportFixtures.externalEntity(secret.absolutePath)))

        assertEquals(GpxImportOutcome.Failed(GpxImportFailure.UNREADABLE), outcome)
        assertTrue(storedTracks().isEmpty())
        assertTrue("the secret appears in nothing logged", logged.none { "TOP-SECRET" in it })
    }

    @Test
    fun `a file over 10 MB is refused without being read`() = runTest {
        val tooBig = ByteArray(GPX_IMPORT_MAX_BYTES + 1) { ' '.code.toByte() }

        assertEquals(GpxImportOutcome.Failed(GpxImportFailure.TOO_BIG), useCase()(GpxImportFixtures.bytesSource(tooBig)))
        assertEquals("the limit is 10 MiB", 10 * 1024 * 1024, GPX_IMPORT_MAX_BYTES)
        assertTrue(storedTracks().isEmpty())
    }

    @Test
    fun `a file that cannot be opened is reported as unreadable and logged`() = runTest {
        val outcome = useCase()(GpxFileSource { GpxFileRead.Failed(java.io.FileNotFoundException("gone")) })

        assertEquals(GpxImportOutcome.Failed(GpxImportFailure.UNREADABLE), outcome)
        assertTrue(logged.single().contains("Couldn't open"))
    }

    @Test
    fun `a save that fails part way removes everything the import wrote`() = runTest {
        var creates = 0
        val failingSecond = object : TrackRepository by tracks {
            override suspend fun createWithPoints(track: Track): Result<Unit> =
                if (++creates == 2) Result.failure(IllegalStateException("disk full")) else tracks.createWithPoints(track)
        }

        val outcome = useCase(failingSecond)(GpxImportFixtures.source(GpxImportFixtures.MULTI_TRACK))

        assertEquals(GpxImportOutcome.Failed(GpxImportFailure.SAVE_FAILED), outcome)
        assertTrue("the first track, already written, is gone again", storedTracks().isEmpty())
        assertTrue(waypoints.getAll().getOrThrow().isEmpty())
        assertTrue(logged.any { "removing the 1 track(s)" in it })
    }
}
