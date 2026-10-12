package com.zynergylabs.forager.app.alert

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.zynergylabs.forager.app.data.diagnostics.FileReturnRecord
import com.zynergylabs.forager.app.domain.Alert
import com.zynergylabs.forager.app.domain.AlertAudibility
import com.zynergylabs.forager.app.domain.AlertAudibilityState
import com.zynergylabs.forager.app.domain.AlertDeliveryOutcome
import com.zynergylabs.forager.app.domain.AlertKind
import com.zynergylabs.forager.app.domain.ComputeReturnToStartUseCase
import com.zynergylabs.forager.app.domain.CurrentTimeProvider
import com.zynergylabs.forager.app.domain.DoNotDisturbFilter
import com.zynergylabs.forager.app.domain.FixProvider
import com.zynergylabs.forager.app.domain.ReturnWatch
import com.zynergylabs.forager.app.domain.RingerMode
import com.zynergylabs.forager.app.domain.SundownAlertDetail
import com.zynergylabs.forager.app.domain.WalkBack
import com.zynergylabs.forager.app.domain.model.TrackPoint
import com.zynergylabs.forager.app.domain.model.TrackRecordingMode
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Dispatch 2026-09-28-685, fix 3 (the owner, RECORD -678: "Fix: record it as skipped (Recommended)").
 * On the 2026-10-07 S22 walk the phone was on silent, Android dropped all four off-track buzzes
 * (`ignored_for_ringer_mode`), and the Return record said `vibration=done` for each. Now it says
 * `vibration=skipped(phone on silent)`, and the alert's behaviour is unchanged: the vibration is still
 * issued, with the same override (none, for off-track: the owner's 2026-09-11 ruling).
 *
 * Through the real entry point: [ReturnWatch.onFix] walking off track, delivering through the real
 * [AndroidAlertDelivery] into the real [FileReturnRecord], read back as the line it wrote. The ringer
 * is faked behind [AlertAudibility], the seam the trip-start warning already reads; the vibration
 * and the notification are the delivery's own seams, recorded.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class SkippedVibrationRecordTest {

    @get:Rule
    val folder = TemporaryFolder()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val now = 1_791_057_600_000L // 2026-10-03T20:00:00Z

    /** Every vibration issued, as the override it was issued with. */
    private val issued = mutableListOf<Boolean>()

    private fun ringer(mode: RingerMode) = object : AlertAudibility {
        override fun current() = AlertAudibilityState(ringerMode = mode, doNotDisturbOn = false, notificationsEnabled = true)
    }

    private fun delivery(audibility: AlertAudibility, dnd: DoNotDisturbFilter = DoNotDisturbFilter.OFF) =
        AndroidAlertDelivery(context, { _, _ -> true }, { _, overridesSilence -> issued += overridesSilence }, audibility, { dnd })

    /** Walks off track with [audibility] behind the delivery; returns the record's delivery line, without its time. */
    private fun offTrackRecordLine(audibility: AlertAudibility, dnd: DoNotDisturbFilter = DoNotDisturbFilter.OFF): String {
        val file = folder.root.resolve("return-record-${System.nanoTime()}.log")
        val watch = ReturnWatch(ComputeReturnToStartUseCase(), delivery(audibility, dnd), FileReturnRecord(file, CurrentTimeProvider { now }))
        watch.begin("track-1", TrackRecordingMode.HIGH_ACCURACY)
        watch.setStartPoint("track-1", TrackPoint(45.0, -122.0, null, 4f, 1_000L))
        watch.startReturn("track-1")
        // Steadily away from the start, as AlertDeliveryOutcomeTest's collection: the fourth reading goes off track.
        (0..5).forEach { i -> watch.onFix(TrackPoint(45.001 + i * 0.001, -122.0, null, 4f, 2_000L + i * 5_000L), FixProvider.GPS) }
        return file.readLines().single { " alert-delivery " in it }.substringAfter(' ')
    }

    @Test
    fun `on a silenced phone the off-track buzz is recorded as skipped, and still issued as before`() {
        assertEquals(
            "alert-delivery track=track-1 notification=posted vibration=skipped(phone on silent)",
            offTrackRecordLine(ringer(RingerMode.SILENT)),
        )
        assertEquals("issued once, without overriding silence (unchanged)", listOf(false), issued)
    }

    @Test
    fun `on Vibrate and on normal ringing the off-track buzz is recorded as done`() {
        for (mode in listOf(RingerMode.VIBRATE, RingerMode.NORMAL)) {
            assertEquals(mode.name, "alert-delivery track=track-1 notification=posted vibration=done", offTrackRecordLine(ringer(mode)))
        }
        assertEquals(listOf(false, false), issued)
    }

    @Test
    fun `a sundown alert overrides silence, so on a silenced phone its buzz is not skipped`() {
        val sunset = now + 3_600_000L
        val alert = Alert(AlertKind.LEAVE_BY, overridesSilence = true, sundown = SundownAlertDetail(sunset, WalkBack.Unknown, sunset - 3_600_000L))
        assertEquals(
            AlertDeliveryOutcome(notificationPosted = true, notificationProblem = null, vibrated = true, vibrationProblem = null, vibrationSkipped = null),
            delivery(ringer(RingerMode.SILENT)).deliverReporting(alert),
        )
        assertEquals(listOf(true), issued)
    }

    @Test
    fun `a ringer that cannot be read leaves the outcome as before, and the buzz is still issued`() {
        val unreadable = object : AlertAudibility {
            override fun current(): AlertAudibilityState = throw SecurityException("no audio service")
        }
        assertEquals(
            AlertDeliveryOutcome(notificationPosted = true, notificationProblem = null, vibrated = true, vibrationProblem = null),
            delivery(unreadable).deliverReporting(Alert(AlertKind.OFF_TRACK, overridesSilence = false)),
        )
        assertEquals(listOf(false), issued)
    }

    @Test
    fun `a vibration that threw is still recorded as failed, not skipped, on a silenced phone`() {
        val throwing = AndroidAlertDelivery(context, { _, _ -> true }, { _, _ -> throw IllegalStateException("no vibrator") }, ringer(RingerMode.SILENT), { DoNotDisturbFilter.TOTAL_SILENCE })
        assertEquals(
            AlertDeliveryOutcome(notificationPosted = true, notificationProblem = null, vibrated = false, vibrationProblem = "IllegalStateException"),
            throwing.deliverReporting(Alert(AlertKind.OFF_TRACK, overridesSilence = false)),
        )
    }

    // ── Amendment 1 (RECORD -694; the owner: "Yes, cover Do Not Disturb (Recommended)") ──

    @Test
    fun `under Do Not Disturb the off-track buzz is recorded as skipped for Do Not Disturb, on any ringer, and still issued`() {
        for (filter in listOf(DoNotDisturbFilter.PRIORITY, DoNotDisturbFilter.ALARMS_ONLY, DoNotDisturbFilter.TOTAL_SILENCE)) {
            for (mode in listOf(RingerMode.NORMAL, RingerMode.SILENT)) {
                assertEquals(
                    "$filter, $mode",
                    "alert-delivery track=track-1 notification=posted vibration=skipped(Do Not Disturb)",
                    offTrackRecordLine(ringer(mode), filter),
                )
            }
        }
        assertEquals("issued every time, as before", List(6) { false }, issued)
    }

    @Test
    fun `with Do Not Disturb off or unreadable, the ringer decides as before`() {
        assertEquals("alert-delivery track=track-1 notification=posted vibration=done", offTrackRecordLine(ringer(RingerMode.VIBRATE), DoNotDisturbFilter.OFF))
        assertEquals("alert-delivery track=track-1 notification=posted vibration=skipped(phone on silent)", offTrackRecordLine(ringer(RingerMode.SILENT), DoNotDisturbFilter.UNKNOWN))
        val throwingDnd = AndroidAlertDelivery(context, { _, _ -> true }, { _, overridesSilence -> issued += overridesSilence }, ringer(RingerMode.SILENT), { throw SecurityException("no") })
        assertEquals(
            "phone on silent",
            throwingDnd.deliverReporting(Alert(AlertKind.OFF_TRACK, overridesSilence = false)).vibrationSkipped,
        )
    }

    @Test
    fun `a sundown alert is skipped only under total silence, since alarms pass priority and alarms-only`() {
        val sunset = now + 3_600_000L
        val alert = Alert(AlertKind.LEAVE_BY, overridesSilence = true, sundown = SundownAlertDetail(sunset, WalkBack.Unknown, sunset - 3_600_000L))
        for (filter in listOf(DoNotDisturbFilter.OFF, DoNotDisturbFilter.PRIORITY, DoNotDisturbFilter.ALARMS_ONLY)) {
            assertEquals(filter.name, null, delivery(ringer(RingerMode.SILENT), filter).deliverReporting(alert).vibrationSkipped)
        }
        val silenced = delivery(ringer(RingerMode.NORMAL), DoNotDisturbFilter.TOTAL_SILENCE).deliverReporting(alert)
        assertEquals("Do Not Disturb", silenced.vibrationSkipped)
        assertEquals(false, silenced.vibrated)
    }
}
