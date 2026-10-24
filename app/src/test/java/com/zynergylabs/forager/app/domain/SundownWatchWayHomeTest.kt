package com.zynergylabs.forager.app.domain

import com.zynergylabs.forager.app.domain.model.LatLng
import com.zynergylabs.forager.app.domain.model.SundownCountdown
import com.zynergylabs.forager.app.domain.model.Track
import com.zynergylabs.forager.app.domain.model.TrackPoint
import com.zynergylabs.forager.app.domain.model.Waypoint
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Sundown's walk back along the way home shown (dispatch 2026-10-24-10 Amendment 1, PR C; RECORD -1589, "app-wide",
 * "Track until Return"): with a way home placed for the recording, the start-back time is worked out from that line's
 * distance ([returnWalkingTimeAlong]); with none, from the track, as before. Same fixture as `SundownWatchLineTest`.
 */
class SundownWatchWayHomeTest {

    private val hour = 3_600_000L
    private val origin = Waypoint("origin", 45.0, -122.0, null, "Start", "", 0L)
    private val morning = 1_791_010_800_000L
    private val baseSunset = (ComputeSundownCountdownUseCase()(morning, LatLng(origin.lat, origin.lng), morning, 0L) as SundownCountdown.Known).sunsetAtEpochMillis

    private var now = baseSunset - 3 * hour
    private var track: Track? = null
    private var along: PathHome? = null

    private val watch = SundownWatch(
        alertDelivery = { },
        clock = CurrentTimeProvider { now },
        preferences = object : SundownPreferencesRepository {
            override suspend fun getDarknessMarginMinutes() = Result.success(60)
            override suspend fun setDarknessMarginMinutes(minutes: Int) = Result.success(Unit)
            override suspend fun getAlertsEnabled() = Result.success(true)
            override suspend fun setAlertsEnabled(enabled: Boolean) = Result.success(Unit)
        },
        readTrack = { id -> Result.success(track?.takeIf { it.id == id }) },
        readWaypoint = { id -> Result.success(origin.takeIf { it.id == id }) },
        isReturning = { true },
        errorLog = { _, _, _ -> },
        wayHomeFor = { along },
    )

    private val points = (0..20 * 12).map { i ->
        val metres = i * 5 * 1.2
        TrackPoint(origin.lat + metres / 111_195.0, origin.lng, null, 4f, baseSunset - 6 * hour + i * 5_000L, 1.2f, 0.5f)
    }

    private fun startBackAt(): Long {
        track = Track("t1", null, points.first().timestampEpochMillis, null, points, originWaypointId = origin.id)
        watch.begin("t1")
        watch.onFix(points.last().copy(timestampEpochMillis = now / 1_000L * 1_000L), FixProvider.GPS)
        runBlocking { watch.tick() }
        return (watch.shown.value!!.line as SundownLine.BeforeSunset).startBackAtEpochMillis!!
    }

    private fun sunset() = (ComputeSundownCountdownUseCase()(morning, LatLng(points.last().lat, points.last().lng), morning, 0L) as SundownCountdown.Known).sunsetAtEpochMillis

    private fun assertSameSecond(expected: Long, actual: Long) = assertTrue("$actual within a second of $expected", kotlin.math.abs(actual - expected) < 1_000L)

    @Test
    fun `with a way home shown the start-back time is worked out along it`() {
        along = PathHome(trackMeters = 3_000.0, hopMeters = 0.0, hopBand = HopBand.NONE, originLegMeters = null, pointCount = 0)
        val t = Track("t1", null, points.first().timestampEpochMillis, null, points, originWaypointId = origin.id)
        val alongTime = (returnWalkingTimeAlong(t, along!!, FixFreshness.FRESH) as ReturnWalkingTime.Estimate).walkingMillis
        val trackTime = (returnWalkingTime(t, origin, LatLng(points.last().lat, points.last().lng), FixFreshness.FRESH) as ReturnWalkingTime.Estimate).walkingMillis
        assertTrue("the two figures differ, so the test can tell them apart", kotlin.math.abs(alongTime - trackTime) > 60_000L)
        assertSameSecond(sunset() - hour - alongTime, startBackAt())
    }

    @Test
    fun `with none the start-back time is the track's, as before`() {
        along = null
        val t = Track("t1", null, points.first().timestampEpochMillis, null, points, originWaypointId = origin.id)
        val trackTime = (returnWalkingTime(t, origin, LatLng(points.last().lat, points.last().lng), FixFreshness.FRESH) as ReturnWalkingTime.Estimate).walkingMillis
        assertSameSecond(sunset() - hour - trackTime, startBackAt())
    }
}
