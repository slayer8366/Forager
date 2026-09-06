package com.forager.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The owner's copy (alert-delivery dispatch, Item 3), pinned as literals — not read back from the constants the function uses. */
class AlertAudibilityWarningTest {
    private fun state(ringer: RingerMode = RingerMode.NORMAL, dnd: Boolean = false, notifications: Boolean = true) =
        AlertAudibilityState(ringerMode = ringer, doNotDisturbOn = dnd, notificationsEnabled = notifications)

    @Test
    fun `an audible phone warns of nothing`() {
        assertNull(alertAudibilityWarning(state()))
    }

    @Test
    fun `vibrate mode is not a silenced state for a vibration`() {
        assertNull(alertAudibilityWarning(state(ringer = RingerMode.VIBRATE)))
    }

    @Test
    fun `a silenced ringer says so without telling the user to change it`() {
        assertEquals(
            "Your phone is silenced. If you go off track, the alert may not be felt.",
            alertAudibilityWarning(state(ringer = RingerMode.SILENT)),
        )
    }

    @Test
    fun `Do Not Disturb takes precedence over a silenced ringer`() {
        assertEquals(
            "Do Not Disturb is on. If you go off track, the alert may not be felt.",
            alertAudibilityWarning(state(ringer = RingerMode.SILENT, dnd = true)),
        )
    }

    @Test
    fun `notifications off stands alone on an otherwise audible phone`() {
        assertEquals(
            "Notifications are off for Forager, so an off-track alert won't show on screen.",
            alertAudibilityWarning(state(notifications = false)),
        )
    }

    @Test
    fun `notifications off is appended to a silence warning`() {
        assertEquals(
            "Your phone is silenced. If you go off track, the alert may not be felt. " +
                "Notifications are off for Forager, so an off-track alert won't show on screen.",
            alertAudibilityWarning(state(ringer = RingerMode.SILENT, notifications = false)),
        )
    }
}
