package com.zynergylabs.forager.app.alert

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.zynergylabs.forager.app.domain.Alert
import com.zynergylabs.forager.app.domain.AlertAudibility
import com.zynergylabs.forager.app.domain.AlertAudibilityState
import com.zynergylabs.forager.app.domain.AlertKind
import com.zynergylabs.forager.app.domain.BackByAlertDetail
import com.zynergylabs.forager.app.domain.DoNotDisturbFilter
import com.zynergylabs.forager.app.domain.RingerMode
import com.zynergylabs.forager.app.domain.VIBRATION_SKIPPED_DO_NOT_DISTURB
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * What the record says about the back-by alert's buzz (dispatch 2026-09-28-645 merged with the small fixes' skipped-buzz
 * record, dispatch 2026-09-28-685, fix 3 and Amendment 1; RECORD -687). Back by overrides silence, as the sundown alerts
 * do, so it is recorded exactly as they are: on a silenced phone the buzz plays (alarm usage) and is not called skipped;
 * under Do Not Disturb's total silence it is skipped and says so; under alarms only, alarms come through. Through the real
 * [AndroidAlertDelivery.deliverReporting] with the ringer and Do Not Disturb faked behind their seams; the vibration is the
 * delivery's own seam, recorded.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class BackByVibrationRecordTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val issued = mutableListOf<Boolean>()
    private val backBy = Alert(AlertKind.BACK_BY, overridesSilence = true, backBy = BackByAlertDetail("t1", 1_791_057_600_000L))

    private fun ringer(mode: RingerMode) = object : AlertAudibility {
        override fun current() = AlertAudibilityState(ringerMode = mode, doNotDisturbOn = false, notificationsEnabled = true)
    }

    private fun deliver(mode: RingerMode, dnd: DoNotDisturbFilter) =
        AndroidAlertDelivery(context, { _, _ -> true }, { _, overridesSilence -> issued += overridesSilence }, ringer(mode), { dnd })
            .deliverReporting(backBy)

    @Test
    fun `on a silenced phone the back-by buzz is issued with the override and not recorded as skipped`() {
        val outcome = deliver(RingerMode.SILENT, DoNotDisturbFilter.OFF)
        assertTrue("vibrated", outcome.vibrated)
        assertNull("not skipped: it breaks through silent mode", outcome.vibrationSkipped)
        assertEquals("issued once, overriding silence", listOf(true), issued)
    }

    @Test
    fun `under Do Not Disturb's total silence it is recorded as skipped, Do Not Disturb`() {
        val outcome = deliver(RingerMode.NORMAL, DoNotDisturbFilter.TOTAL_SILENCE)
        assertFalse("not counted as vibrated", outcome.vibrated)
        assertEquals(VIBRATION_SKIPPED_DO_NOT_DISTURB, outcome.vibrationSkipped)
        assertEquals("still issued: the behaviour is unchanged", listOf(true), issued)
    }

    @Test
    fun `under Do Not Disturb's alarms only it comes through and is not recorded as skipped`() {
        val outcome = deliver(RingerMode.SILENT, DoNotDisturbFilter.ALARMS_ONLY)
        assertTrue(outcome.vibrated)
        assertNull(outcome.vibrationSkipped)
    }
}
