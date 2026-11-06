package com.zynergylabs.forager.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

/**
 * Dispatch 2026-09-28-510: which position the map, the HUD and the strip show, from the gated GPS fix,
 * the newest refused (approximate) reading and the platform's last known location. Every age is a
 * pinned literal against one clock reading; "lost" is the HUD's own five minutes ([LOST_AFTER_MILLIS]).
 */
class ShownPositionTest {

    private val now = 1_700_000_000_000L

    /** A GPS fix [ageMillis] old, accepted by the live gate (5 m). */
    private fun gps(ageMillis: Long) = LocationFix.Update(45.5, -122.6, 100.0, 5f, now - ageMillis, provider = FixProvider.GPS)

    /** A network reading [ageMillis] old (milliseconds off the whole second), refused by the gate (120 m). */
    private fun network(ageMillis: Long) = LocationFix.Update(45.51, -122.61, null, 120f, now - ageMillis + 123, provider = FixProvider.NETWORK)

    /** The platform's last known location, [ageMillis] old. */
    private fun platform(ageMillis: Long) = LocationFix.Update(45.6, -122.7, null, 30f, now - ageMillis, provider = FixProvider.GPS)

    @Test
    fun `nothing at all is None`() {
        assertEquals(ShownPosition.None, shownPosition(null, null, null, now))
    }

    @Test
    fun `a fresh GPS fix is precise`() {
        val fix = gps(1_000)
        assertEquals(ShownPosition.Precise(fix), shownPosition(fix, null, null, now))
    }

    @Test
    fun `a network reading over 50 m with no GPS fix yet is approximate`() {
        val reading = network(1_000)
        assertEquals(ShownPosition.Approximate(reading), shownPosition(null, reading, null, now))
    }

    @Test
    fun `a fresh GPS fix stays shown over a newer approximate reading`() {
        val fix = gps(10_000)
        assertEquals(ShownPosition.Precise(fix), shownPosition(fix, network(1_000), null, now))
    }

    @Test
    fun `a stale GPS fix, 2 min old, still holds over a newer approximate reading (the owner's answer 2)`() {
        val fix = gps(120_000)
        assertEquals(ShownPosition.Precise(fix), shownPosition(fix, network(1_000), null, now))
    }

    @Test
    fun `once the GPS fix is lost, a newer approximate reading is shown`() {
        val reading = network(10_000)
        assertEquals(ShownPosition.Approximate(reading), shownPosition(gps(360_000), reading, null, now))
    }

    @Test
    fun `a GPS fix replaces the approximate reading the moment it arrives`() {
        val fix = gps(0)
        assertEquals(ShownPosition.Precise(fix), shownPosition(fix, network(5_000), null, now))
    }

    @Test
    fun `a lost GPS fix with only an older approximate reading is the last known position, the GPS fix`() {
        val fix = gps(360_000)
        assertEquals(ShownPosition.LastKnown(fix), shownPosition(fix, network(400_000), null, now))
    }

    @Test
    fun `an approximate reading that is itself lost is the last known position`() {
        val reading = network(360_000)
        assertEquals(ShownPosition.LastKnown(reading), shownPosition(null, reading, null, now))
    }

    @Test
    fun `with no live reading the platform's last known location is shown as last known, however old`() {
        val known = platform(7_200_000)
        assertEquals(ShownPosition.LastKnown(known), shownPosition(null, null, known, now))
    }

    @Test
    fun `a live reading of either kind wins over the platform's last known location`() {
        assertEquals(ShownPosition.Precise(gps(1_000)), shownPosition(gps(1_000), null, platform(10), now))
        assertEquals(ShownPosition.Approximate(network(1_000)), shownPosition(null, network(1_000), platform(10), now))
    }

    @Test
    fun `last known is the newest of the lost GPS fix and the platform's`() {
        assertEquals(ShownPosition.LastKnown(platform(60_000)), shownPosition(gps(360_000), null, platform(60_000), now))
        assertEquals(ShownPosition.LastKnown(gps(360_000)), shownPosition(gps(360_000), null, platform(900_000), now))
    }

    @Test
    fun `in place of GPS is an approximate reading, or a last known one that is not the held GPS fix`() {
        val fix = gps(360_000)
        val approximate = ShownPosition.Approximate(network(1_000))
        assertSame(approximate, approximate.inPlaceOfGps(fix))
        // The held GPS fix, lost: today's display ("No fix for N min"), not "Last seen".
        assertNull(ShownPosition.LastKnown(fix).inPlaceOfGps(fix))
        val lostReading = ShownPosition.LastKnown(network(360_000))
        assertSame(lostReading, lostReading.inPlaceOfGps(fix))
        val known = ShownPosition.LastKnown(platform(7_200_000))
        assertSame(known, known.inPlaceOfGps(null))
        assertNull(ShownPosition.Precise(gps(1_000)).inPlaceOfGps(gps(1_000)))
        assertNull(ShownPosition.None.inPlaceOfGps(null))
    }

    @Test
    fun `the clock wakes when the GPS fix or the approximate reading turns lost, whichever is first`() {
        assertEquals(290_000L, millisUntilShownPositionChanges(gps(10_000), null, now))
        // The network reading's stamp is 123 ms after the whole second, so it is 99,877 ms old: 300,000 - 99,877.
        assertEquals(200_123L, millisUntilShownPositionChanges(gps(10_000), network(100_000), now))
        // 59,877 ms old: 300,000 - 59,877. The lost GPS fix adds nothing.
        assertEquals(240_123L, millisUntilShownPositionChanges(gps(360_000), network(60_000), now))
        assertNull(millisUntilShownPositionChanges(gps(300_000), network(360_000), now))
        assertNull(millisUntilShownPositionChanges(null, null, now))
    }
}
