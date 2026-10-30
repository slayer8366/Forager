package com.zynergylabs.forager.app.data.repository

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.zynergylabs.forager.app.data.local.ForagerDatabase
import com.zynergylabs.forager.app.data.local.PlannedTripEntity
import com.zynergylabs.forager.app.data.local.PlannedTripRouteLegEntity
import com.zynergylabs.forager.app.data.local.PlannedTripRouteStopEntity
import com.zynergylabs.forager.app.domain.ErrorLog
import com.zynergylabs.forager.app.domain.TrackPathCodec
import com.zynergylabs.forager.app.domain.model.LatLng
import com.zynergylabs.forager.app.domain.model.PlannedTrip
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * A stored route that is not a valid route is refused on read, and logged (dispatch 2026-10-30-02 Amendment 1, "Leg
 * kind: ... Add a test that a row with both set, or neither, is rejected on read and logged"). The rows are written
 * through the DAO, as a damaged or hand-edited database would hold them; the trip still loads, without its route.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class StoredRouteRefusalTest {

    private lateinit var database: ForagerDatabase
    private lateinit var repository: RoomPlannedTripRepository
    private val logged = mutableListOf<String>()

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Application>(), ForagerDatabase::class.java).build()
        repository = RoomPlannedTripRepository(database.plannedTripRouteDao(), ErrorLog { _, message, error -> logged += "$message :: ${error.message}" })
    }

    @After
    fun tearDown() = database.close()

    @Test
    fun `a leg with both its trail part and a straight reason is refused and logged`() =
        assertRefused(leg(trailFrom = 0, trailTo = 1, straightReason = "NO_REGION_COVERS"), "neither a trail leg nor a straight one")

    @Test
    fun `a leg with neither its trail part nor a straight reason is refused and logged`() =
        assertRefused(leg(trailFrom = null, trailTo = null, straightReason = null), "neither a trail leg nor a straight one")

    @Test
    fun `a leg with only half its trail part is refused and logged`() =
        assertRefused(leg(trailFrom = 0, trailTo = null, straightReason = null), "neither a trail leg nor a straight one")

    @Test
    fun `a straight reason this build does not know is refused and logged`() =
        assertRefused(leg(trailFrom = null, trailTo = null, straightReason = "WENT_FISHING"), "unknown straight reason 'WENT_FISHING'")

    @Test
    fun `a well formed straight leg is read, so the refusals above are about their shape`() = runTest {
        store(leg(trailFrom = null, trailTo = null, straightReason = "NO_REGION_COVERS"))

        val trip = repository.getAll().getOrThrow().single()

        assertEquals(2, trip.route?.stops?.size)
        assertEquals(emptyList<String>(), logged)
    }

    private fun assertRefused(leg: PlannedTripRouteLegEntity, why: String) = runTest {
        store(leg)

        val trips = repository.getAll().getOrThrow()

        assertEquals("the trip still loads, without its route", listOf(PlannedTrip("t1", "Ridge run", A, null)), trips)
        assertEquals("one refusal logged", 1, logged.size)
        assertTrue("the log names why: ${logged.single()}", logged.single().contains(why))
    }

    private suspend fun store(leg: PlannedTripRouteLegEntity) {
        val dao = database.plannedTripRouteDao()
        dao.save(
            PlannedTripEntity("t1", "Ridge run", A.lat, A.lng, date = null),
            stops = listOf(PlannedTripRouteStopEntity("t1", 0, "Creek pin", A.lat, A.lng), PlannedTripRouteStopEntity("t1", 1, "Big oak", B.lat, B.lng)),
            legs = listOf(leg),
        )
    }

    private fun leg(trailFrom: Int?, trailTo: Int?, straightReason: String?) =
        PlannedTripRouteLegEntity("t1", 0, TrackPathCodec.encode(listOf(A, B)), 2480.5, trailFrom, trailTo, straightReason)

    private companion object {
        val A = LatLng(45.2, -122.5)
        val B = LatLng(45.22, -122.52)
    }
}
