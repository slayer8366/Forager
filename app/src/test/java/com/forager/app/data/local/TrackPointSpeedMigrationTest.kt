package com.forager.app.data.local

import android.app.Application
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.test.core.app.ApplicationProvider
import com.forager.app.data.repository.RoomTrackRepository
import com.forager.app.domain.model.Track
import com.forager.app.domain.model.TrackPoint
import java.io.File
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The 14→15 counterpart to [WaypointDesignationMigrationTest] — the return-estimate dispatch's
 * Doppler speed columns on `track_points`. A real version-14 file via [LegacyForagerDatabaseV14],
 * restored to its true pre-migration shape (the shared entity leaks both new columns into the
 * fixture; see the two `DROP COLUMN`s and `docs/audits/2026-08-24-migration-fixture-entity-reuse-
 * pitfall.md`), reopened with [MIGRATION_14_15]: the legacy point survives with `null` in both
 * speed fields and every other field intact, its rowid included; a point recorded after the
 * migration round-trips its speed and accuracy; a post-migration network-style point with neither
 * reads back `null`, not zero. Whole-second stamps throughout — the read seam excludes anything
 * else as a network fix.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class TrackPointSpeedMigrationTest {

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
    fun `a pre-existing point survives 14 to 15 with no speed, and points with and without speed round-trip`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Application>()

        val legacyDb = Room.databaseBuilder(context, LegacyForagerDatabaseV14::class.java, dbFile.absolutePath).build()
        try {
            legacyDb.trackDao().insertTrack(TrackEntity(id = "track-1", name = "Before speed", startedAtEpochMillis = 1_000L, endedAtEpochMillis = 9_000L, originWaypointId = null))
            legacyDb.trackDao().insertPoints(
                listOf(
                    TrackPointEntity(id = 7L, trackId = "track-1", lat = 45.5, lng = -122.6, altitude = 312.0, accuracyMeters = 8.5f, timestampEpochMillis = 2_000L, speedMetersPerSecond = null, speedAccuracyMetersPerSecond = null),
                ),
            )
            // The shared TrackPointEntity now declares both speed columns, which a real version-14
            // install never had — dropped so the file reaches MIGRATION_14_15 in its true shape.
            legacyDb.openHelper.writableDatabase.execSQL("ALTER TABLE `track_points` DROP COLUMN `speedMetersPerSecond`")
            legacyDb.openHelper.writableDatabase.execSQL("ALTER TABLE `track_points` DROP COLUMN `speedAccuracyMetersPerSecond`")
        } finally {
            legacyDb.close()
        }

        val migrated = Room.databaseBuilder(context, ForagerDatabase::class.java, dbFile.absolutePath)
            .addMigrations(MIGRATION_14_15)
            .build()
        try {
            val repository = RoomTrackRepository(migrated.trackDao())

            val legacyPoint = TrackPoint(lat = 45.5, lng = -122.6, altitude = 312.0, accuracyMeters = 8.5f, timestampEpochMillis = 2_000L, speedMetersPerSecond = null, speedAccuracyMetersPerSecond = null)
            assertEquals(
                Track(id = "track-1", name = "Before speed", startedAtEpochMillis = 1_000L, endedAtEpochMillis = 9_000L, points = listOf(legacyPoint)),
                repository.getById("track-1").getOrThrow(),
            )
            assertEquals(listOf(7L), migrated.trackDao().getPointsForTrack("track-1").map { it.id })

            val withSpeed = TrackPoint(lat = 45.51, lng = -122.61, altitude = 300.0, accuracyMeters = 3.79f, timestampEpochMillis = 3_000L, speedMetersPerSecond = 0.96f, speedAccuracyMetersPerSecond = 0.69f)
            val withoutSpeed = TrackPoint(lat = 45.52, lng = -122.62, altitude = null, accuracyMeters = 17.2f, timestampEpochMillis = 4_000L, speedMetersPerSecond = null, speedAccuracyMetersPerSecond = null)
            repository.appendPoints("track-1", listOf(withSpeed, withoutSpeed)).getOrThrow()

            assertEquals(listOf(legacyPoint, withSpeed, withoutSpeed), repository.getById("track-1").getOrThrow()!!.points)
        } finally {
            migrated.close()
        }
    }

    private companion object {
        const val TEST_DB_NAME = "track-point-speed-migration-test.db"
    }
}

/** Version 14 of [ForagerDatabase] from the shared entity/DAO classes — see [LegacyForagerDatabaseV12] for the pattern and its caveat. */
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
    version = 14,
    exportSchema = false,
)
internal abstract class LegacyForagerDatabaseV14 : RoomDatabase() {
    abstract fun trackDao(): TrackDao
}
