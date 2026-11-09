package com.zynergylabs.forager.app.data.local

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.testing.MigrationTestHelper
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.zynergylabs.forager.app.data.repository.RoomPlannedTripRepository
import com.zynergylabs.forager.app.domain.ErrorLog
import com.zynergylabs.forager.app.domain.TrackPathCodec
import com.zynergylabs.forager.app.domain.model.LatLng
import com.zynergylabs.forager.app.domain.model.PlannedTrip
import com.zynergylabs.forager.app.domain.route.PlottedRoute
import com.zynergylabs.forager.app.domain.route.RouteLeg
import com.zynergylabs.forager.app.domain.route.RouteStop
import com.zynergylabs.forager.app.domain.route.RouteStopLink
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
 * Dispatch 2026-11-06-05, Amendment 2 (RECORD -2187), the owner: "Forager is released now, so this database change runs on real
 * phones. It needs a proper migration with a test from the current version." Users' phones are at 22 (main), testers' at 23
 * (pre-main). A real version-22 database made from `22.json` holding trips is migrated to 23 by the registered [MIGRATION_22_23]
 * and validated against `23.json`; routes with their stops and legs are written into it at 23, as a version-23 build writes them;
 * then it is opened the way the app opens it ([ForagerDatabase.create], release path, so a missing 23-to-24 migration throws
 * rather than wipes) and read through [RoomPlannedTripRepository]. Every trip, route, stop and leg comes back whole, each stop
 * with no link. Then the link's own rule: a stop saved from now on keeps its link, and deleting the waypoint or trip clears it
 * while the stop, its name and its spot stay.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class RouteStopLinkMigrationTest {

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
    fun `routes and stops saved at 23, on a database that came from 22, every one survives 23 to 24 with no link`() = runBlocking {
        helper.createDatabase(NAME, 22).use { db ->
            insertTrip(db, PLAIN_TRIP.id, PLAIN_TRIP.name, PLAIN_TRIP.location, PLAIN_TRIP.date.toString())
        }
        helper.runMigrationsAndValidate(NAME, 23, true, MIGRATION_22_23).use { db ->
            for (trip in listOf(ROUTE_ONE, ROUTE_TWO)) {
                insertTrip(db, trip.id, trip.name, trip.location, trip.date?.toString())
                val route = trip.route!!
                route.stops.forEachIndexed { i, stop ->
                    val cv = ContentValues().apply {
                        put("tripId", trip.id); put("stopIndex", i); put("name", stop.name); put("lat", stop.position.lat); put("lng", stop.position.lng)
                    }
                    check(db.insert("planned_trip_route_stops", SQLiteDatabase.CONFLICT_ABORT, cv) != -1L)
                }
                route.legs.forEachIndexed { i, leg ->
                    val straight = leg as RouteLeg.Straight
                    val cv = ContentValues().apply {
                        put("tripId", trip.id); put("legIndex", i); put("path", TrackPathCodec.encode(leg.line)); put("meters", leg.meters)
                        putNull("trailFrom"); putNull("trailTo"); put("straightReason", straight.reason.name)
                    }
                    check(db.insert("planned_trip_route_legs", SQLiteDatabase.CONFLICT_ABORT, cv) != -1L)
                }
            }
        }

        val repository = openRepository()

        assertEquals(listOf(PLAIN_TRIP, ROUTE_ONE, ROUTE_TWO).sortedBy { it.id }, repository.getAll().getOrThrow().sortedBy { it.id })
        assertEquals("every old stop reads back with no link", listOf<RouteStopLink?>(null, null, null, null, null), repository.getAll().getOrThrow().flatMap { it.route?.stops.orEmpty() }.map { it.link })
        assertEquals("five stop rows, none lost", 5L, database!!.openHelper.readableDatabase.query("SELECT COUNT(*) FROM planned_trip_route_stops").use { it.moveToFirst(); it.getLong(0) })
        assertEquals("nothing was refused", emptyList<String>(), logged)
    }

    @Test
    fun `a stop saved from now on keeps its link, and deleting the waypoint or trip clears it while the stop stays`() = runBlocking {
        helper.createDatabase(NAME, 22).close()
        val repository = openRepository()
        val db = database!!
        db.waypointDao().upsert(WaypointEntity(id = "wp-creek", lat = A.lat, lng = A.lng, altitude = null, name = "Creek pin", note = "", createdAtEpochMillis = 1L))
        repository.save(PLAIN_TRIP).getOrThrow()
        val linked = ROUTE_ONE.copy(
            route = ROUTE_ONE.route!!.copy(
                stops = listOf(
                    RouteStop("Creek pin", A, RouteStopLink.Waypoint("wp-creek")),
                    RouteStop("Big oak", B),
                    RouteStop(PLAIN_TRIP.name, PLAIN_TRIP.location, RouteStopLink.Trip(PLAIN_TRIP.id)),
                ),
                legs = listOf(straight(A, B, 120.0), straight(B, PLAIN_TRIP.location, 340.0)),
            ),
        )
        repository.save(linked).getOrThrow()
        assertEquals("the links read back as saved", linked, repository.getAll().getOrThrow().single { it.id == linked.id })

        db.waypointDao().deleteById("wp-creek")
        repository.delete(PLAIN_TRIP.id).getOrThrow()

        val after = repository.getAll().getOrThrow().single { it.id == linked.id }
        val unlinked = linked.route!!.stops.map { it.copy(link = null) }
        assertEquals("the stops stay, names and spots unchanged, with the links cleared", unlinked, after.route!!.stops)
        assertEquals("the legs are untouched", linked.route!!.legs, after.route!!.legs)
        assertEquals("both columns are NULL on disk", 0L, db.openHelper.readableDatabase.query("SELECT COUNT(*) FROM planned_trip_route_stops WHERE linkedWaypointId IS NOT NULL OR linkedTripId IS NOT NULL").use { it.moveToFirst(); it.getLong(0) })
        assertEquals("nothing was refused", emptyList<String>(), logged)
    }

    private fun insertTrip(db: androidx.sqlite.db.SupportSQLiteDatabase, id: String, name: String, at: LatLng, date: String?) {
        val cv = ContentValues().apply {
            put("id", id); put("name", name); put("lat", at.lat); put("lng", at.lng)
            if (date == null) putNull("date") else put("date", date)
        }
        check(db.insert("planned_trips", SQLiteDatabase.CONFLICT_ABORT, cv) != -1L)
    }

    private fun openRepository(): RoomPlannedTripRepository {
        val db = ForagerDatabase.create(context, isDebug = false, name = NAME).also { database = it }
        return RoomPlannedTripRepository(db.plannedTripRouteDao(), ErrorLog { _, message, error -> logged += "$message :: ${error.message}" })
    }

    private companion object {
        const val NAME = "route-link-migration.db"

        val A = LatLng(45.2, -122.5)
        val B = LatLng(45.22, -122.52)
        val C = LatLng(45.25, -122.49)
        val D = LatLng(45.27, -122.47)

        fun straight(from: LatLng, to: LatLng, meters: Double) = RouteLeg.Straight(listOf(from, to), meters, StraightReason.NO_REGION_COVERS)

        val PLAIN_TRIP = PlannedTrip("trip-plain", "Chanterelle weekend", LatLng(45.4301, -122.2869), LocalDate.of(2026, 11, 14))

        val ROUTE_ONE = PlannedTrip(
            id = "route-1", name = "Ridge run", location = A, date = null,
            route = PlottedRoute(listOf(RouteStop("Creek pin", A), RouteStop("Big oak", B), RouteStop("Spring", C)), listOf(straight(A, B, 2480.5), straight(B, C, 4100.25))),
        )
        val ROUTE_TWO = PlannedTrip(
            id = "route-2", name = "Spring loop", location = C, date = LocalDate.of(2026, 11, 20),
            route = PlottedRoute(listOf(RouteStop("Spring", C), RouteStop("Saddle", D)), listOf(straight(C, D, 900.0))),
        )
    }
}
