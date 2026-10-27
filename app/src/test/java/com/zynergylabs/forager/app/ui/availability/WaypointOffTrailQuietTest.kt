package com.zynergylabs.forager.app.ui.availability

import com.zynergylabs.forager.app.domain.AlertDelivery
import com.zynergylabs.forager.app.domain.ReturnWatch
import com.zynergylabs.forager.app.domain.wayhome.WayHomeOffTrail
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Step 6 PR 4 (dispatch 2026-10-27-01; RECORD -1725, "quietly, with no sound and no vibration"): no path of the way to a
 * waypoint's off-trail state can alert. In this app every alert, sound and vibration goes through [AlertDelivery]
 * (`alert/AndroidAlertDelivery.kt` is the one implementation, and the only main file naming the platform's vibrator or
 * tone APIs, as of this PR's base), so the check is that nothing on these paths holds one: not the ViewModel that owns
 * the navigation, not [AvailabilityWaypointRoute] or its per-navigation state, not the rule. A structural check, stated
 * as such: it would miss an alert reached some other way, such as a function handed in that delivers one.
 *
 * The positive control shows the check can see one: [ReturnWatch], which does alert, holds an [AlertDelivery].
 */
class WaypointOffTrailQuietTest {

    private fun alertHolders(type: Class<*>): List<String> =
        (type.declaredFields.map { it.type to "field ${it.name}" } +
            type.declaredConstructors.flatMap { c -> c.parameterTypes.map { it to "constructor parameter" } })
            .filter { (t, _) -> AlertDelivery::class.java.isAssignableFrom(t) }
            .map { (_, where) -> "${type.simpleName}: $where" }

    @Test
    fun `positive control - the check sees the return watch's alert delivery`() {
        assertTrue(alertHolders(ReturnWatch::class.java).isNotEmpty())
    }

    @Test
    fun `nothing on the waypoint's off-trail paths holds an alert delivery`() {
        val following = AvailabilityWaypointRoute::class.java.declaredClasses.single { it.simpleName == "Following" }
        val types = listOf(AvailabilityViewModel::class.java, AvailabilityWaypointRoute::class.java, following, WayHomeOffTrail::class.java)
        assertEquals(emptyList<String>(), types.flatMap(::alertHolders))
    }
}
