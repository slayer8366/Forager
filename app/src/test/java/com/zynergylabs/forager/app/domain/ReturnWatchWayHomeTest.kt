package com.zynergylabs.forager.app.domain

import com.zynergylabs.forager.app.domain.model.LatLng
import com.zynergylabs.forager.app.domain.model.TrackPoint
import com.zynergylabs.forager.app.domain.model.TrackRecordingMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The off-track alert while a way home along trails is shown (dispatch 2026-10-25-08, plan step 7 PR 1; RECORD -1631).
 *
 * Hazel's case (the way home part 2 report, section 7; seen on the S22, 2026-10-25-hazel-s22-way-home-2-report.md
 * step 8): the walker follows the app's own trail line where it runs about 80 m from the way they walked out, and
 * before this change got the alert 15 s later. Now the trail parts shown on a return count as on course beside the
 * path, and the one alert is for a walker far from both.
 *
 * Synthetic ground, no real place: the walk out runs north from the start along one meridian; the trail line runs
 * parallel to it [TRAIL_EAST_METERS] east. Readings carry no accuracy, so the line is 40 m exactly.
 */
class ReturnWatchWayHomeTest {

    private val delivered = mutableListOf<Alert>()

    /** What the way home shows for each track id asked; tests change it mid-return. */
    private var shown: ShownTrail? = null
    private val askedFor = mutableListOf<String>()

    private fun begunWatch() = ReturnWatch(
        computeReturnToStart = ComputeReturnToStartUseCase(),
        alertDelivery = { delivered += it },
        wayHomeTrail = { id -> askedFor += id; shown },
    ).apply {
        begin(TRACK, TrackRecordingMode.HIGH_ACCURACY)
        setStartPoint(TRACK, point(lat = 45.0, metersEast = 0.0, t = 1_000L))
        // The walk out: 45.000 to 45.003 north, every 0.0005° (56 m), kept by the service.
        (0..6).forEach { i -> onKeptPoint(point(lat = 45.0 + i * 0.0005, metersEast = 0.0, t = 1_000L + i * 10_000L)) }
    }

    private fun point(lat: Double, metersEast: Double, t: Long) =
        TrackPoint(lat = lat, lng = -122.0 + metersEast / METERS_PER_DEGREE_LNG, altitude = null, accuracyMeters = null, timestampEpochMillis = t)

    /** A trail line [metersEast] east of the walk out, from its far end back toward the start. */
    private fun trail(generation: Long, metersEast: Double = TRAIL_EAST_METERS) =
        ShownTrail(generation, (6 downTo 0).map { i -> LatLng(45.0 + i * 0.0005, -122.0 + metersEast / METERS_PER_DEGREE_LNG) })

    /** Six GPS readings 5 s apart from [fromMillis], walking south from 45.0025 at [metersEast]: 25 s, past the 15 s hold. */
    private fun ReturnWatch.walk(metersEast: Double, fromMillis: Long) {
        repeat(6) { i -> onFix(point(lat = 45.0025 - i * 0.0002, metersEast = metersEast, t = fromMillis + i * 5_000L), FixProvider.GPS) }
    }

    @Test
    fun `following the way home's trail line 80 m from the walked path gives no alert`() {
        shown = trail(generation = 1)
        val watch = begunWatch()
        watch.startReturn(TRACK)

        watch.walk(metersEast = TRAIL_EAST_METERS, fromMillis = 100_000L)

        assertEquals("following the app's own trail line must not alert", emptyList<Alert>(), delivered)
        assertFalse(watch.state.value.isOffTrack)
    }

    @Test
    fun `far from both the trail line and the walked path alerts once`() {
        shown = trail(generation = 1)
        val watch = begunWatch()
        watch.startReturn(TRACK)

        // 80 m west: 80 m from the path, 160 m from the trail line.
        watch.walk(metersEast = -TRAIL_EAST_METERS, fromMillis = 100_000L)

        assertEquals(listOf(Alert(kind = AlertKind.OFF_TRACK, overridesSilence = false)), delivered)
        assertTrue(watch.state.value.isOffTrack)
    }

    @Test
    fun `a trail line shown earlier on the return still counts after a re-route shows another`() {
        shown = trail(generation = 1)
        val watch = begunWatch()
        watch.startReturn(TRACK)
        watch.onFix(point(lat = 45.003, metersEast = TRAIL_EAST_METERS, t = 90_000L), FixProvider.GPS)

        // A re-route shows a trail line further east; the walker stays on the first one.
        shown = trail(generation = 2, metersEast = 400.0)
        watch.walk(metersEast = TRAIL_EAST_METERS, fromMillis = 100_000L)

        assertEquals(emptyList<Alert>(), delivered)
    }

    @Test
    fun `a new return forgets the trail lines of the last one`() {
        shown = trail(generation = 1)
        val watch = begunWatch()
        watch.startReturn(TRACK)
        watch.onFix(point(lat = 45.003, metersEast = TRAIL_EAST_METERS, t = 90_000L), FixProvider.GPS)
        watch.stopReturn(TRACK)

        // The next Return, with no way home along trails shown (it is still being worked out, or it is the track).
        shown = null
        watch.startReturn(TRACK)
        watch.walk(metersEast = TRAIL_EAST_METERS, fromMillis = 200_000L)

        assertEquals(listOf(Alert(kind = AlertKind.OFF_TRACK, overridesSilence = false)), delivered)
    }

    @Test
    fun `the watch asks for the way home of the track it is returning on`() {
        shown = trail(generation = 1)
        val watch = begunWatch()
        watch.startReturn(TRACK)

        watch.onFix(point(lat = 45.003, metersEast = TRAIL_EAST_METERS, t = 90_000L), FixProvider.GPS)

        assertEquals(listOf(TRACK), askedFor.distinct())
    }

    private companion object {
        const val TRACK = "track-1"
        const val TRAIL_EAST_METERS = 80.0

        /** Metres per degree of longitude at 45° N, near enough for tens of metres (as ReturnWatchTest uses). */
        const val METERS_PER_DEGREE_LNG = 78_620.0
    }
}
