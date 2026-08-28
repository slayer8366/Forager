package com.forager.app.data.local

import android.app.Application
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.test.core.app.ApplicationProvider
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * [ForagerDatabase.create]'s destructive-fallback split — see that class's own doc comment
 * ("Destructive fallback, debug-only") for why release must throw on a missing migration path
 * instead of silently wiping the database, while debug keeps the escape hatch.
 *
 * The registered chain in [ForagerDatabase.create] only covers version 3 onward
 * ([MIGRATION_3_4] through [MIGRATION_8_9]) — there has never been a `MIGRATION_1_2`/`MIGRATION_2_3`
 * in this codebase (versions 1-3 predate `exportSchema = true`, see [ForagerDatabase]'s own doc
 * comment). A real version-2 database is therefore a genuinely uncovered version gap today, not a
 * contrived one, and stands in for "a future migration nobody registered" without needing to fake
 * one: the mechanism under test — does an unregistered gap throw or silently wipe — doesn't care
 * whether the gap is this real historical one or a hypothetical future one.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ForagerDatabaseDestructiveFallbackTest {

    private lateinit var dbFile: File

    @Before
    fun setUp() {
        // Must match the literal "forager.db" ForagerDatabase.create() hardcodes internally — a
        // custom test-only name would seed a database create() never actually opens, silently
        // making both tests below pass against a fresh, unrelated database instead of the legacy
        // fixture (caught by mutation-checking this test itself: it did exactly that at first).
        dbFile = ApplicationProvider.getApplicationContext<Application>().getDatabasePath("forager.db")
        dbFile.delete()
    }

    @After
    fun tearDown() {
        dbFile.delete()
    }

    @Test
    fun `a version gap with no registered migration throws under the release path`() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val legacyDb = Room.databaseBuilder(context, LegacyForagerDatabaseV2::class.java, dbFile.absolutePath).build()
        runBlocking { legacyDb.plannedTripDao().upsert(PlannedTripEntity(id = "trip-1", name = "Chanterelle Ridge", lat = 45.51, lng = -122.66, date = "2026-09-01")) }
        legacyDb.close()

        // Room opens the connection (and runs its migration check) lazily on first access, not at
        // build() — a plain create() call with no query would pass this test regardless of isDebug.
        assertThrows(IllegalStateException::class.java) {
            runBlocking { ForagerDatabase.create(context, isDebug = false).plannedTripDao().getAll() }
        }
    }

    @Test
    fun `the same version gap falls back instead of throwing under the debug path`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val legacyDb = Room.databaseBuilder(context, LegacyForagerDatabaseV2::class.java, dbFile.absolutePath).build()
        legacyDb.plannedTripDao().upsert(PlannedTripEntity(id = "trip-1", name = "Chanterelle Ridge", lat = 45.51, lng = -122.66, date = "2026-09-01"))
        legacyDb.close()

        // Must not throw. If it does, this test fails with that exception, which is the point:
        // proving isDebug actually branches behavior, not that it's inert.
        val migrated = ForagerDatabase.create(context, isDebug = true)
        assertTrue(
            "the destructive fallback must actually have wiped the old schema, not coincidentally opened it",
            migrated.plannedTripDao().getAll().isEmpty(),
        )
        migrated.close()
    }
}

/**
 * Version 2 of [ForagerDatabase] — adds `planned_trips.name` per that class's own doc comment.
 * [ForagerDatabase.create]'s registered chain starts at [MIGRATION_3_4], so this version has no
 * migration path to anything the real database recognizes, on purpose (see this file's own class
 * doc comment on why that's exactly the gap this test needs).
 */
@Database(entities = [PlannedTripEntity::class], version = 2, exportSchema = false)
internal abstract class LegacyForagerDatabaseV2 : RoomDatabase() {
    abstract fun plannedTripDao(): PlannedTripDao
}
