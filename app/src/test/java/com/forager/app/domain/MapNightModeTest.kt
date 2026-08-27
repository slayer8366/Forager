package com.forager.app.domain

import com.forager.app.domain.MapNightMode.NightModeHold
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The override state machine from [MapNightMode]: what the map draws, and when a hold expires. */
class MapNightModeTest {

    @Test
    fun `with no hold, the sun decides`() {
        assertTrue(MapNightMode.resolve(automaticNight = true, hold = null))
        assertFalse(MapNightMode.resolve(automaticNight = false, hold = null))
    }

    @Test
    fun `a hold placed under the current conditions overrides them`() {
        val forcedNightInDaylight = MapNightMode.toggled(automaticNight = false, hold = null)
        assertEquals(NightModeHold(nightWanted = true, automaticWhenPlaced = false), forcedNightInDaylight)
        assertTrue(MapNightMode.resolve(automaticNight = false, hold = forcedNightInDaylight))
    }

    /**
     * The behaviour the hold exists for: it is scoped to the situation it was placed in. Forcing
     * night mode on under canopy in the afternoon must not still be forcing it at 2am, by which
     * point the automatic answer agrees anyway — and, more importantly, must not still be forcing
     * it *off* the following evening.
     */
    @Test
    fun `a hold expires when the automatic decision changes`() {
        val forcedDayAtDusk = MapNightMode.toggled(automaticNight = true, hold = null)!!
        assertFalse("while it is still night, the hold applies", MapNightMode.resolve(true, forcedDayAtDusk))
        assertFalse("morning comes: the sun agrees anyway", MapNightMode.resolve(false, forcedDayAtDusk))
        assertTrue(
            "the following dusk must not still be held to day",
            MapNightMode.resolve(automaticNight = true, hold = forcedDayAtDusk.copy(automaticWhenPlaced = false)),
        )
    }

    @Test
    fun `pressing twice returns to automatic rather than leaving an agreeing hold`() {
        val once = MapNightMode.toggled(automaticNight = false, hold = null)
        assertTrue(MapNightMode.resolve(false, once))

        val twice = MapNightMode.toggled(automaticNight = false, hold = once)
        assertNull("a hold that agrees with the sun is no hold at all", twice)
        assertFalse(MapNightMode.resolve(false, twice))
    }

    @Test
    fun `toggling always flips what is currently on screen`() {
        for (automatic in listOf(true, false)) {
            for (hold in listOf(null, NightModeHold(true, automatic), NightModeHold(false, automatic))) {
                val before = MapNightMode.resolve(automatic, hold)
                val after = MapNightMode.resolve(automatic, MapNightMode.toggled(automatic, hold))
                assertEquals(
                    "automatic=$automatic hold=$hold should have flipped $before",
                    !before,
                    after,
                )
            }
        }
    }

    @Test
    fun `an expired hold reads as no hold for the UI too`() {
        val placedAtNight = NightModeHold(nightWanted = false, automaticWhenPlaced = true)
        assertTrue(MapNightMode.isHeld(automaticNight = true, hold = placedAtNight))
        assertFalse(MapNightMode.isHeld(automaticNight = false, hold = placedAtNight))
        assertFalse(MapNightMode.isHeld(automaticNight = true, hold = null))
    }

    /**
     * Toggling an expired hold must act on what is on screen now, not on what the stale hold said.
     * Otherwise a press could appear to do nothing.
     */
    @Test
    fun `toggling an expired hold flips the current appearance, not the stale one`() {
        val stale = NightModeHold(nightWanted = true, automaticWhenPlaced = false)
        // It is now night; the hold was placed in daylight, so it has expired and the map is dark.
        assertTrue(MapNightMode.resolve(automaticNight = true, hold = stale))
        val next = MapNightMode.toggled(automaticNight = true, hold = stale)
        assertFalse("the press should have turned it light", MapNightMode.resolve(true, next))
    }
}
