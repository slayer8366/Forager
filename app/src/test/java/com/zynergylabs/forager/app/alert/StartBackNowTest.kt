package com.zynergylabs.forager.app.alert

import android.Manifest
import android.app.Application
import android.app.Notification
import android.app.NotificationManager
import android.text.format.DateFormat
import androidx.test.core.app.ApplicationProvider
import com.zynergylabs.forager.app.domain.Alert
import com.zynergylabs.forager.app.domain.AlertKind
import com.zynergylabs.forager.app.domain.ComputeSundownCountdownUseCase
import com.zynergylabs.forager.app.domain.CurrentTimeProvider
import com.zynergylabs.forager.app.domain.FixFreshness
import com.zynergylabs.forager.app.domain.FixProvider
import com.zynergylabs.forager.app.domain.ReturnWalkingTime
import com.zynergylabs.forager.app.domain.SundownAlertDetail
import com.zynergylabs.forager.app.domain.SundownPreferencesRepository
import com.zynergylabs.forager.app.domain.SundownWatch
import com.zynergylabs.forager.app.domain.WalkBack
import com.zynergylabs.forager.app.domain.model.LatLng
import com.zynergylabs.forager.app.domain.model.SundownCountdown
import com.zynergylabs.forager.app.domain.model.Track
import com.zynergylabs.forager.app.domain.model.TrackPoint
import com.zynergylabs.forager.app.domain.model.Waypoint
import com.zynergylabs.forager.app.domain.returnWalkingTime
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.util.Date

/**
 * Dispatch 2026-09-28-685, fix 4 (the owner, RECORD -678: "'Start back now' (Recommended)"): a sundown
 * alert that fires when its leave-by time is already gone never names that time. On the 2026-10-07 S22
 * walk a margin change at about 5:08 put leave-by at 5:07, and the alert said "start by 5:07 PM" at 5:08.
 * It still fires once.
 *
 * Through the real entry point: [SundownWatch.tick] on a fake clock, with the real countdown, decision and
 * walk-back estimate, delivering through the real [AndroidAlertDelivery], read as the posted notification.
 * Only the vibration is a recorded seam.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class StartBackNowTest {

    private val minute = 60_000L
    private val hour = 60 * minute

    private val origin = Waypoint("origin", 45.0, -122.0, null, "Start", "", 0L)
    private val morning = 1_791_010_800_000L // 2026-10-03T07:00:00Z

    private lateinit var context: Application
    private lateinit var watch: SundownWatch
    private val clock = object : CurrentTimeProvider {
        var now = 0L
        override fun nowEpochMillis() = now
    }
    private val preferences = object : SundownPreferencesRepository {
        var margin = 60
        override suspend fun getDarknessMarginMinutes() = Result.success(margin)
        override suspend fun setDarknessMarginMinutes(minutes: Int) = Result.success(Unit).also { margin = minutes }
        override suspend fun getAlertsEnabled() = Result.success(true)
        override suspend fun setAlertsEnabled(enabled: Boolean) = Result.success(Unit)
    }
    private var track: Track? = null

    /** Every alert delivered, as (kind, posted text). */
    private val posted = mutableListOf<Pair<AlertKind, String>>()

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        shadowOf(context).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        val android = AndroidAlertDelivery(context, ::postNotificationFor, { _, _ -> })
        watch = SundownWatch(
            alertDelivery = { alert: Alert ->
                android.deliver(alert)
                posted += alert.kind to postedText()
            },
            clock = clock,
            preferences = preferences,
            readTrack = { id -> Result.success(track?.takeIf { it.id == id }) },
            readWaypoint = { id -> Result.success(origin.takeIf { it.id == id }) },
            isReturning = { false },
            errorLog = { _, _, _ -> },
        )
    }

    private fun postedText(): String {
        val n = shadowOf(context.getSystemService(NotificationManager::class.java)).getNotification(SUNDOWN_NOTIFICATION_ID)
        return n.extras.getCharSequence(Notification.EXTRA_TEXT).toString()
    }

    private fun clockTime(epochMillis: Long) = DateFormat.getTimeFormat(context).format(Date(epochMillis))

    /** A walk north at 1.2 m/s for [minutes], one point every 5 s: past fifteen minutes, so the pace is measured. */
    private fun walkOut(minutes: Int, startAt: Long): List<TrackPoint> =
        (0..minutes * 12).map { i ->
            val metres = i * 5 * 1.2
            TrackPoint(origin.lat + metres / 111_195.0, origin.lng, null, 4f, startAt + i * 5_000L, 1.2f, 0.5f)
        }

    private fun trackOf(points: List<TrackPoint>) = Track("t1", null, points.first().timestampEpochMillis, null, points, originWaypointId = origin.id)

    private fun sunsetAt(point: TrackPoint) =
        (ComputeSundownCountdownUseCase()(morning, LatLng(point.lat, point.lng), morning, 0L) as SundownCountdown.Known).sunsetAtEpochMillis

    private fun walkBackMillis(points: List<TrackPoint>): Long {
        val here = points.last()
        val estimate = returnWalkingTime(trackOf(points), origin, LatLng(here.lat, here.lng), FixFreshness.FRESH) as ReturnWalkingTime.Estimate
        assertFalse("precondition: a measured pace", estimate.isAtLeast)
        return estimate.walkingMillis
    }

    /** One tick at [at], after a fresh GPS fix at [here]. */
    private fun tickAt(at: Long, here: TrackPoint) {
        clock.now = at
        watch.onFix(here.copy(timestampEpochMillis = at / 1_000L * 1_000L), FixProvider.GPS)
        runBlocking { watch.tick() }
    }

    @Test
    fun `a recording started past the leave-by time says start back now, names no time, and fires once`() {
        val sunsetGuess = sunsetAt(walkOut(20, morning).last())
        val points = walkOut(20, sunsetGuess - 3 * hour)
        track = trackOf(points)
        val here = points.last()
        val walkBack = walkBackMillis(points)
        val leaveBy = sunsetAt(here) - hour - walkBack

        watch.begin("t1")
        for (t in (leaveBy + 5 * minute)..(leaveBy + 20 * minute) step 15_000L) tickAt(t, here)

        assertEquals(
            listOf(AlertKind.LEAVE_BY to "The walk back the way you came is about ${formatWalkDuration(walkBack)}. Start back now to finish before dark."),
            posted,
        )
    }

    @Test
    fun `a margin change that puts leave-by behind now gives start back now on the leave-by alert`() {
        val sunsetGuess = sunsetAt(walkOut(20, morning).last())
        val points = walkOut(20, sunsetGuess - 3 * hour)
        track = trackOf(points)
        val here = points.last()
        val walkBack = walkBackMillis(points)
        val sunset = sunsetAt(here)

        // Margin 30 min: the heads-up fires 30 min before leave-by, with its time, still ahead.
        preferences.margin = 30
        val leaveBy30 = sunset - 30 * minute - walkBack
        watch.begin("t1")
        tickAt(leaveBy30 - 29 * minute, here)
        assertEquals(1, posted.size)
        assertEquals(AlertKind.HEADS_UP, posted[0].first)
        assertTrue("the heads-up names its time: ${posted[0].second}", posted[0].second.endsWith("start by ${clockTime(leaveBy30)}."))

        // The S22 case: the margin raised to 1 h 30, so leave-by is an hour earlier, already gone.
        preferences.margin = 90
        val leaveBy90 = sunset - 90 * minute - walkBack
        tickAt(leaveBy30 - 28 * minute, here)
        assertTrue("precondition: leave-by is behind now", leaveBy90 < clock.now - minute)
        assertEquals(2, posted.size)
        assertEquals(
            AlertKind.LEAVE_BY to "The walk back the way you came is about ${formatWalkDuration(walkBack)}. Start back now to finish before dark.",
            posted[1],
        )
        tickAt(leaveBy30 - 27 * minute, here)
        assertEquals("once", 2, posted.size)
    }

    @Test
    fun `a leave-by alert decided within its own minute still names its time`() {
        val sunsetGuess = sunsetAt(walkOut(20, morning).last())
        val points = walkOut(20, sunsetGuess - 3 * hour)
        track = trackOf(points)
        val here = points.last()
        val walkBack = walkBackMillis(points)
        val leaveBy = sunsetAt(here) - hour - walkBack

        watch.begin("t1")
        tickAt(leaveBy - 29 * minute, here) // the heads-up
        // The leave-by, on time: ticks a second apart across it. The watch computes sunset from the live fix, which can
        // differ from this test's by a fraction of a second, so the alert fires within a second or two of [leaveBy].
        var t = leaveBy - 2_000L
        while (posted.size < 2 && t <= leaveBy + 5_000L) {
            tickAt(t, here)
            t += 1_000L
        }
        val firedAt = clock.now
        assertEquals("precondition: fired in leave-by's own minute (fired $firedAt, leave-by $leaveBy)", leaveBy / minute, firedAt / minute)
        assertEquals(AlertKind.LEAVE_BY, posted[1].first)
        assertEquals(
            "The walk back the way you came is about ${formatWalkDuration(walkBack)}. To finish it before dark, start by ${clockTime(leaveBy)}.",
            posted[1].second,
        )
    }

    /** The text for each walk back, at the boundary: a start-by minute already gone, and one not yet. */
    @Test
    fun `the start-by minute decides it, for measured and at-least alike, and unknown names no time either way`() {
        val android = AndroidAlertDelivery(context, ::postNotificationFor, { _, _ -> })
        val sunset = morning + 12 * hour
        val leaveBy = sunset - 2 * hour + 59_999L // hh:mm:59.999
        fun text(walkBack: WalkBack, decidedAt: Long): String {
            android.deliver(Alert(AlertKind.LEAVE_BY, overridesSilence = true, sundown = SundownAlertDetail(sunset, walkBack, leaveBy, decidedAtEpochMillis = decidedAt)))
            return postedText()
        }
        val sameMinute = leaveBy - 30_000L
        val nextMinute = leaveBy + 1L
        assertEquals("The walk back the way you came is about 45 min. To finish it before dark, start by ${clockTime(leaveBy)}.", text(WalkBack.About(45 * minute), sameMinute))
        assertEquals("The walk back the way you came is about 45 min. Start back now to finish before dark.", text(WalkBack.About(45 * minute), nextMinute))
        assertEquals("The walk back is at least 20 min. To finish it before dark, start by ${clockTime(leaveBy)} at the latest.", text(WalkBack.AtLeast(20 * minute), sameMinute))
        assertEquals("The walk back is at least 20 min. Start back now to finish before dark.", text(WalkBack.AtLeast(20 * minute), nextMinute))
        assertEquals("The walk back is unknown.", text(WalkBack.Unknown, nextMinute))
    }
}
