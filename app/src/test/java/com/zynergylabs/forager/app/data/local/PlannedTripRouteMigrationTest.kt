package com.zynergylabs.forager.app.data.local

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.testing.MigrationTestHelper
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.zynergylabs.forager.app.data.repository.RoomPlannedTripRepository
import com.zynergylabs.forager.app.domain.ErrorLog
import com.zynergylabs.forager.app.domain.model.LatLng
import com.zynergylabs.forager.app.domain.model.PlannedTrip
import com.zynergylabs.forager.app.domain.route.PlottedRoute
import com.zynergylabs.forager.app.domain.route.RouteLeg
import com.zynergylabs.forager.app.domain.route.RouteStop
import com.zynergylabs.forager.app.domain.route.StraightReason
import java.time.LocalDate
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Existing trips survive the 22 → 23 migration (dispatch 2026-10-30-02, step 1): a real version-22 database, made by
 * Room's migration test helper from `22.json` and holding three trips, is opened the way the app opens it
 * ([ForagerDatabase.create], release path, so a missing migration would crash rather than wipe), and the trips are read
 * through [RoomPlannedTripRepository] — every field compared, each date included. Then an undated trip with a route is
 * saved and read back as the same value, which is the new columns' reader.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class PlannedTripRouteMigrationTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private lateinit var helper: MigrationTestHelper
    private var database: ForagerDatabase? = null
    private val logged = mutableListOf<String>()

    @Before
    fun setUp() {
        SchemaAssets.install(context.assets)
        helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), ForagerDatabase::class.java)
    }

    @After
    fun tearDown() {
        database?.close()
    }

    @Test
    fun `three trips in a real version 22 database keep every field, their dates included, after the migration`() = runBlocking {
        helper.createDatabase(NAME, 22).use { db ->
            TRIPS.forEach { trip ->
                val cv = ContentValues().apply {
                    put("id", trip.id); put("name", trip.name); put("lat", trip.location.lat); put("lng", trip.location.lng)
                    put("date", trip.date.toString())
                }
                check(db.insert("planned_trips", SQLiteDatabase.CONFLICT_ABORT, cv) != -1L)
            }
        }

        val repository = openRepository()

        assertEquals(TRIPS.sortedBy { it.id }, repository.getAll().getOrThrow().sortedBy { it.id })
        assertEquals("nothing was refused", emptyList<String>(), logged)
    }

    @Test
    fun `after the migration an undated trip with a route saves and reads back as the same value`() = runBlocking {
        helper.createDatabase(NAME, 22).close()
        val repository = openRepository()

        repository.save(ROUTE_TRIP).getOrThrow()

        assertEquals(listOf(ROUTE_TRIP), repository.getAll().getOrThrow())
        assertEquals("nothing was refused", emptyList<String>(), logged)
    }

    private fun openRepository(): RoomPlannedTripRepository {
        val db = ForagerDatabase.create(context, isDebug = false, name = NAME).also { database = it }
        return RoomPlannedTripRepository(db.plannedTripRouteDao(), ErrorLog { _, message, error -> logged += "$message :: ${error.message}" })
    }

    private companion object {
        const val NAME = "route-migration.db"

        val TRIPS = listOf(
            PlannedTrip("trip-a", "Chanterelle weekend", LatLng(45.4301, -122.2869), LocalDate.of(2026, 11, 14)),
            PlannedTrip("trip-b", "Morels", LatLng(44.05, -121.31), LocalDate.of(2027, 4, 20)),
            PlannedTrip("trip-c", "Hedgehogs", LatLng(46.12, -123.9), LocalDate.of(2026, 12, 1)),
        )

        private val A = LatLng(45.2, -122.5)
        private val B = LatLng(45.22, -122.52)
        private val C = LatLng(45.25, -122.49)

        val ROUTE_TRIP = PlannedTrip(
            id = "route-1",
            name = "Ridge run",
            location = A,
            date = null,
            route = PlottedRoute(
                stops = listOf(RouteStop("Creek pin", A), RouteStop("Big oak", B), RouteStop("Spring", C)),
                legs = listOf(
                    RouteLeg.Trail(listOf(A, LatLng(45.201, -122.501), LatLng(45.219, -122.519), B), meters = 2480.5, trailFrom = 1, trailTo = 2),
                    RouteLeg.Straight(listOf(B, C), meters = 4100.25, reason = StraightReason.IN_DIFFERENT_AREAS),
                ),
            ),
        )
    }
}
