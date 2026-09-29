package com.zynergylabs.forager.app.data.local

import android.app.Application
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.test.core.app.ApplicationProvider
import com.zynergylabs.forager.app.data.repository.RoomCartographyEntryRepository
import com.zynergylabs.forager.app.domain.model.CartographyEntry
import com.zynergylabs.forager.app.domain.model.FindDecision
import com.zynergylabs.forager.app.domain.model.OfflineRegionDecision
import com.zynergylabs.forager.app.domain.model.PhotoAttachment
import com.zynergylabs.forager.app.domain.model.TrackDecision
import com.zynergylabs.forager.app.domain.model.WaypointDecision
import java.io.File
import java.time.LocalDate
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The 15→16 counterpart to [TrackPointSpeedMigrationTest] — J8's `shownOnMap` on
 * `cartography_entries`. A real version-15 file via [LegacyForagerDatabaseV15], seeded through the
 * shared DAO with three entries (two saved, one draft, with decisions of every kind and a photo) and
 * then restored to its true pre-migration shape (the shared entity leaks the new column into the
 * fixture; see the `DROP COLUMN` and `docs/audits/2026-08-24-migration-fixture-entity-reuse-pitfall.md`).
 * Reopened with [MIGRATION_15_16]: every entry survives with every field and every decision intact,
 * and reads `shownOnMap = false`. Then the one writer, `setShownOnMap`, sets that column alone.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class CartographyEntryShownOnMapMigrationTest {

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

    private val saved = CartographyEntry(
        id = "entry-saved",
        date = LocalDate.of(2026, 9, 12),
        text = "Chanterelles on the ridge.",
        tags = listOf("chanterelle", "ridge"),
        isDraft = false,
        updatedAtEpochMillis = 5_000L,
        findDecisions = listOf(FindDecision(findId = "find-1", foundOn = LocalDate.of(2026, 9, 12), ownIdentification = "Chanterelle", hasPhotos = true, kept = true)),
        trackDecisions = listOf(
            TrackDecision(trackId = "track-1", name = "Ridge Loop", distanceMeters = 3200.0, durationMillis = 5_400_000L, pointCount = 240, kept = true),
            TrackDecision(trackId = "track-2", name = "Withheld", distanceMeters = 800.0, durationMillis = 900_000L, pointCount = 60, kept = false),
        ),
        waypointDecisions = listOf(WaypointDecision(waypointId = "waypoint-1", name = "Trailhead", lat = 45.4, lng = -122.6, kept = true)),
        offlineRegionDecisions = listOf(OfflineRegionDecision(offlineRegionId = 7L, name = "Ridge", lat = 45.4, lng = -122.6, radiusKm = 10, kept = true)),
        photos = listOf(PhotoAttachment(photoId = "photo-1", attachedAtEpochMillis = 6_000L)),
    )
    private val savedWordless = CartographyEntry.draft(id = "entry-wordless", date = LocalDate.of(2026, 9, 5), updatedAtEpochMillis = 2_000L)
        .copy(isDraft = false, photos = listOf(PhotoAttachment(photoId = "photo-2", attachedAtEpochMillis = 2_500L)))
    private val draft = CartographyEntry.draft(id = "entry-draft", date = LocalDate.of(2026, 9, 20), updatedAtEpochMillis = 9_000L)
        .copy(text = "Half a day", tags = listOf("scouting"))

    private suspend fun migratedDatabase(): ForagerDatabase {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val legacyDb = Room.databaseBuilder(context, LegacyForagerDatabaseV15::class.java, dbFile.absolutePath).build()
        try {
            val legacyRepository = RoomCartographyEntryRepository(legacyDb.cartographyEntryDao())
            listOf(saved, savedWordless, draft).forEach { legacyRepository.save(it).getOrThrow() }
            // The shared CartographyEntryEntity now declares shownOnMap, which a real version-15
            // install never had — dropped so the file reaches MIGRATION_15_16 in its true shape.
            legacyDb.openHelper.writableDatabase.execSQL("ALTER TABLE `cartography_entries` DROP COLUMN `shownOnMap`")
            // Likewise the fixture must list CartographyEntryTrackPathEntity, because the shared DAO now has queries
            // against it (F3) and Room checks them against the fixture's schema; a real version-15 install had no
            // such table, so it is dropped (its index goes with it) and MIGRATION_16_17 creates it, as it would on a phone.
            legacyDb.openHelper.writableDatabase.execSQL("DROP TABLE `cartography_entry_track_paths`")
        } finally {
            legacyDb.close()
        }
        return Room.databaseBuilder(context, ForagerDatabase::class.java, dbFile.absolutePath)
            .addMigrations(MIGRATION_15_16, MIGRATION_16_17)
            .build()
    }

    @Test
    fun `every entry survives 15 to 16 with every field and decision, and reads shownOnMap false`() = runTest {
        val migrated = migratedDatabase()
        try {
            val repository = RoomCartographyEntryRepository(migrated.cartographyEntryDao())
            assertEquals(saved, repository.getById(saved.id).getOrThrow())
            assertEquals(savedWordless, repository.getById(savedWordless.id).getOrThrow())
            assertEquals(draft, repository.getById(draft.id).getOrThrow())
            assertEquals(setOf(saved.id, savedWordless.id), repository.getAll().getOrThrow().map { it.id }.toSet())
            assertEquals(listOf(draft.id), repository.getAllDrafts().getOrThrow().map { it.id })
            val all = repository.getAll().getOrThrow() + repository.getAllDrafts().getOrThrow()
            assertEquals(3, all.size)
            all.forEach { assertFalse("${it.id} reads shownOnMap false after the migration", it.shownOnMap) }
            // The rebuilt table's column, read directly: 0 on every row, not NULL.
            migrated.openHelper.readableDatabase.query("SELECT COUNT(*) FROM cartography_entries WHERE shownOnMap = 0").use { c ->
                assertTrue(c.moveToFirst())
                assertEquals(3, c.getInt(0))
            }
            // The kept-item warnings still read the refs, which the rebuild did not touch.
            assertEquals(1, repository.countEntriesReferencingTrack("track-1").getOrThrow())
            assertEquals(0, repository.countEntriesReferencingTrack("track-2").getOrThrow())
        } finally {
            migrated.close()
        }
    }

    @Test
    fun `after 15 to 16, setShownOnMap sets that column alone, and a save carries it`() = runTest {
        val migrated = migratedDatabase()
        try {
            val repository = RoomCartographyEntryRepository(migrated.cartographyEntryDao())
            repository.setShownOnMap(saved.id, true).getOrThrow()
            // Only the flag changed: the writing, the decisions and the edit stamp are the stored ones.
            assertEquals(saved.copy(shownOnMap = true), repository.getById(saved.id).getOrThrow())
            assertEquals(savedWordless, repository.getById(savedWordless.id).getOrThrow())

            repository.setShownOnMap(saved.id, false).getOrThrow()
            assertEquals(saved, repository.getById(saved.id).getOrThrow())

            // The entry's own save writes whatever the entry carries.
            repository.save(savedWordless.copy(shownOnMap = true)).getOrThrow()
            assertTrue(repository.getById(savedWordless.id).getOrThrow()!!.shownOnMap)

            // A toggle that reaches no stored entry is a failure, never taken as done.
            assertTrue(repository.setShownOnMap("no-such-entry", true).isFailure)
        } finally {
            migrated.close()
        }
    }

    private companion object {
        const val TEST_DB_NAME = "cartography-shown-on-map-migration-test.db"
    }
}

/** Version 15 of [ForagerDatabase] from the shared entity/DAO classes — see [LegacyForagerDatabaseV12] for the pattern and its caveat. */
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
        CartographyEntryTrackPathEntity::class,
    ],
    version = 15,
    exportSchema = false,
)
internal abstract class LegacyForagerDatabaseV15 : RoomDatabase() {
    abstract fun cartographyEntryDao(): CartographyEntryDao
}
