package com.zynergylabs.forager.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Dispatch 2026-10-28-13, Amendment 4 (RECORD -1808, the owner: "Keep the steadying for 10 min (Recommended)") and the
 * planner's relay of the owner's 10 s hold (RECORD -1809, "10 seconds (Recommended)"): [KeptLiveHeight], the steadying
 * kept across the barometer's stops. Pure, on made-up walks only. The ViewModel-path cases (9 and 11 minutes away, the
 * screen off and on) are in AvailabilityScreenSeaLevelHeightTest.
 *
 * Every walk here sits at 72 m above sea level, at a steady 1000 hPa, so the anchor is 72 m over the barometer's
 * height. A fix at 80 m after the return tells the two answers apart: a fresh steadying shows it as it comes (80 m),
 * the kept one shows the barometer plus the anchor (72 m, plus the existing pull's small share of the 8 m).
 */
class KeptLiveHeightTest {

    private val logged = mutableListOf<String>()
    private val errorLog = ErrorLog { tag, message, _ -> logged += "$tag: $message" }

    private fun known(meters: Double) = SeaLevelHeight.Known(meters, aboveRawMeters = 21.6)

    private fun reading(second: Long) = PressureReading(1000.0f, second * 1_000_000_000L)

    private fun meters(shown: ShownHeight?): Double = (shown as ShownHeight.Known).meters

    /** Fixes and readings once a second at 72 m, from 0 s to [lastSecond]: past 90 s the first minute and the blend are over. */
    private fun walk(lastSecond: Long): KeptLiveHeight = KeptLiveHeight(errorLog).also { h ->
        h.onBarometerStarted()
        for (second in 0L..lastSecond) {
            h.onPressure(reading(second))
            h.onFix(known(72.0), T0 + second * 1_000L)
        }
    }

    /**
     * RECORD -1809: back before the first barometer reading, a fix does not change the shown height, so the strip never
     * shows plain GPS for a moment and then steps to the steadied height when the reading lands.
     */
    @Test
    fun `back on the map, a fix before the first barometer reading holds the shown height`() {
        val h = walk(lastSecond = 120)
        h.onBarometerStopped()
        // Five minutes away.
        h.onBarometerStarted()
        assertNull("the first fix back, before any reading, holds the height", h.onFix(known(80.0), T0 + 421_000L))
        assertNull("so does one 5 s later", h.onFix(known(80.0), T0 + 426_000L))

        val shown = meters(h.onPressure(reading(427)))
        assertEquals("the first reading shows the barometer plus the kept anchor, not the fix's 80 m", 72.0, shown, 0.1)
    }

    /**
     * RECORD -1809: the hold lasts at most [LIVE_HEIGHT_RETURN_HOLD_MILLIS], timed from the first fix back. With no reading
     * by then the steadying starts fresh and shows plain GPS, so a barometer that never speaks cannot freeze the strip,
     * and the fallback is logged.
     */
    @Test
    fun `with no barometer reading the hold ends after 10 s and the fix is shown`() {
        val h = walk(lastSecond = 120)
        h.onBarometerStopped()
        h.onBarometerStarted()
        assertNull("held at the first fix back", h.onFix(known(80.0), T0 + 421_000L))
        assertNull("still held 9 s on", h.onFix(known(80.0), T0 + 430_000L))
        assertEquals("nothing logged while the hold lasts", emptyList<String>(), logged)

        val after = h.onFix(known(80.0), T0 + 432_000L)
        assertEquals("11 s on, no reading: fresh, and the fix shown as it comes", 80.0, meters(after), 0.001)
        assertEquals("the fallback is logged once", 1, logged.size)
    }

    /** Proposal accepted by the planner: an anchor whose first minute was not over is not kept. */
    @Test
    fun `a barometer stopped inside its first minute starts fresh on the return`() {
        val h = walk(lastSecond = 30)
        h.onBarometerStopped()
        h.onBarometerStarted()
        h.onPressure(reading(150))
        val shown = h.onFix(known(80.0), T0 + 151_000L)

        assertEquals("a fresh first minute: the fix as it comes", 80.0, meters(shown), 0.001)
    }

    /**
     * The owner: "steadied at once, with no first minute and no blend". Stopped at 75 s, halfway through the 30 s blend,
     * and back 5 s later: the kept anchor is shown in full. Resuming the blend would read about 74.4 m here (0.3 of the
     * way from the anchor to the fix), a fresh steadying 80 m.
     */
    @Test
    fun `a barometer stopped during the blend comes back steadied at once`() {
        val h = walk(lastSecond = 75)
        h.onBarometerStopped()
        h.onBarometerStarted()
        h.onPressure(reading(80))
        val shown = meters(h.onFix(known(80.0), T0 + 81_000L))

        // The existing pull: 8 m × 6 s / 60 min, about 0.013 m.
        assertEquals("the kept anchor in full", 72.0, shown, 0.05)
    }

    private companion object {
        const val T0 = 1_700_000_000_000L
    }
}
