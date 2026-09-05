package com.forager.app.data.local

import android.app.Application
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.test.core.app.ApplicationProvider
import com.forager.app.data.repository.RoomTrackRepository
import com.forager.app.data.repository.RoomWaypointRepository
import com.forager.app.domain.model.Track
import com.forager.app.domain.model.Waypoint
import java.io.File
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The 12→13 counterpart to this package's other migration tests — HUD-foundations dispatch,
 * Item 3. Builds a real version-12 database via [LegacyForagerDatabaseV12], seeds one track and
 * one waypoint through the real DAOs, restores the file to its true pre-migration shape (see the
 * `DROP INDEX`/`DROP COLUMN` lines in the test body, and
 * `docs/audits/2026-08-24-migration-fixture-entity-reuse-pitfall.md` for why a fixture built from
 * the shared entity classes needs that), reopens it as [ForagerDatabase] with [MIGRATION_12_13]
 * applied, and asserts: both pre-existing rows survive with `NULL` in the new columns; a waypoint
 * with and without a `trackId`, and a track with an `originWaypointId`, round-trip through the
 * real repositories; and the new `trackId` read path returns exactly the linked waypoints, oldest
 * first.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class TrackOriginWaypointMigrationTest {

    private lateinit var dbFile: File

    @Before
    fun setUp() {
        dbFile = ApplicationProvider.getApplicationContext<Application>().getDatabasePath(TEST_DB_NAME)
        dbFile.delete()
    }

    @After
    fun tearDown() {
        dbFile.delete()
    }

    @Test
    fun `pre-existing tracks and waypoints survive the 12 to 13 migration with null links, and the new columns round-trip`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Application>()

        val legacyTrack = TrackEntity(id = "track-legacy", name = "Before the HUD", startedAtEpochMillis = 1_000L, endedAtEpochMillis = 2_000L)
        val legacyWaypoint = WaypointEntity(id = "wp-legacy", lat = 45.5, lng = -122.6, altitude = 312.0, name = "Old parking", note = "", createdAtEpochMillis = 1_500L)

        val legacyDb = Room.databaseBuilder(context, LegacyForagerDatabaseV12::class.java, dbFile.absolutePath).build()
        try {
            legacyDb.trackDao().insertTrack(legacyTrack)
            legacyDb.waypointDao().upsert(legacyWaypoint)

            // LegacyForagerDatabaseV12 reuses the *production* TrackEntity/WaypointEntity classes,
            // which now carry originWaypointId/trackId (plus trackId's index) — columns a real
            // version-12 install never had. Drop them so the file reaches MIGRATION_12_13 in its
            // true pre-migration shape; the index goes first, or dropping the column it references
            // fails with "no such column". See the fixture-reuse audit named in this class's doc
            // comment — this is the documented treatment, not leftover debugging.
            legacyDb.openHelper.writableDatabase.execSQL("DROP INDEX `index_waypoints_trackId`")
            legacyDb.openHelper.writableDatabase.execSQL("ALTER TABLE `waypoints` DROP COLUMN `trackId`")
            legacyDb.openHelper.writableDatabase.execSQL("ALTER TABLE `tracks` DROP COLUMN `originWaypointId`")
        } finally {
            legacyDb.close()
        }

        // Reopen the same file as the real, current ForagerDatabase — no fallbackToDestructiveMigration,
        // so a missing or wrong MIGRATION_12_13 throws rather than silently wiping the file.
        val migrated = Room.databaseBuilder(context, ForagerDatabase::class.java, dbFile.absolutePath)
            .addMigrations(MIGRATION_12_13)
            .build()

        try {
            val trackRepository = RoomTrackRepository(migrated.trackDao())
            val waypointRepository = RoomWaypointRepository(migrated.waypointDao())

            // Survival, with the honest NULL — no origin, no link — for rows that predate the columns.
            val survivedTrack = trackRepository.getById("track-legacy").getOrThrow()
            assertEquals(
                Track(id = "track-legacy", name = "Before the HUD", startedAtEpochMillis = 1_000L, endedAtEpochMillis = 2_000L, points = emptyList(), originWaypointId = null),
                survivedTrack,
            )
            assertEquals(
                listOf(Waypoint(id = "wp-legacy", lat = 45.5, lng = -122.6, altitude = 312.0, name = "Old parking", note = "", createdAtEpochMillis = 1_500L, trackId = null)),
                waypointRepository.getAll().getOrThrow(),
            )

            // The new columns actually work: a linked waypoint, an unlinked one, and a track with an
            // origin pointer all round-trip through the production repositories.
            val origin = Waypoint(id = "wp-origin", lat = 45.52, lng = -122.68, altitude = 50.0, name = "Trailhead", note = "", createdAtEpochMillis = 3_000L, trackId = "track-new")
            val later = Waypoint(id = "wp-later", lat = 45.53, lng = -122.69, altitude = null, name = "Chanterelle patch", note = "", createdAtEpochMillis = 4_000L, trackId = "track-new")
            val loose = Waypoint(id = "wp-loose", lat = 45.6, lng = -122.7, altitude = null, name = "No recording", note = "", createdAtEpochMillis = 3_500L, trackId = null)
            waypointRepository.save(later).getOrThrow()
            waypointRepository.save(origin).getOrThrow()
            waypointRepository.save(loose).getOrThrow()
            val newTrack = Track(id = "track-new", name = null, startedAtEpochMillis = 3_000L, endedAtEpochMillis = null, points = emptyList(), originWaypointId = "wp-origin")
            trackRepository.create(newTrack).getOrThrow()

            assertEquals(newTrack, trackRepository.getById("track-new").getOrThrow())
            assertEquals(origin, waypointRepository.getById("wp-origin").getOrThrow())
            assertEquals(loose, waypointRepository.getById("wp-loose").getOrThrow())
            // Exactly the linked ones, oldest first, regardless of insertion order.
            assertEquals(listOf(origin, later), waypointRepository.getForTrack("track-new").getOrThrow())
            assertEquals(emptyList<Waypoint>(), waypointRepository.getForTrack("track-legacy").getOrThrow())
            assertNull(waypointRepository.getById("wp-none").getOrThrow())
        } finally {
            migrated.close()
        }
    }

    private companion object {
        const val TEST_DB_NAME = "track-origin-waypoint-migration-test.db"
    }
}

/**
 * Version 12 of [ForagerDatabase], reconstructed for this test from the *same* entity and DAO
 * classes production code used at that version — none of them are copied, so this can't drift
 * from what a real version-12 install actually wrote. Exposes the two DAOs this test seeds
 * through. `exportSchema = false` matches every other `LegacyForagerDatabaseVn` fixture in this
 * package.
 *
 * **Not a faithful copy of the true version-12 `tracks`/`waypoints` shape on its own** — the shared
 * entity classes gained the two [MIGRATION_12_13] columns, so Room builds these "legacy" tables
 * with them already present; the test body drops them back out before migrating (see its own
 * comment).
 */
@Database(
    entities = [
        PlannedTripEntity::class,
        CachedSearchEntity::class,
        MushroomLogEntryEntity::class,
        LogPhotoEntity::class,
        LogEntryPhotoCrossRef::class,
        TrackEntity::class,
        TrackPointEntity::class,
        WaypointEntity::class,
        OfflineRegionEntity::class,
        CartographyEntryEntity::class,
        CartographyEntryTrackRefEntity::class,
        CartographyEntryWaypointRefEntity::class,
        CartographyEntryOfflineRegionRefEntity::class,
        CartographyEntryFindRefEntity::class,
        CartographyEntryPhotoRefEntity::class,
    ],
    version = 12,
    exportSchema = false,
)
internal abstract class LegacyForagerDatabaseV12 : RoomDatabase() {
    abstract fun trackDao(): TrackDao
    abstract fun waypointDao(): WaypointDao
}
