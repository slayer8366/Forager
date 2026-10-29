package com.zynergylabs.forager.app.location

import androidx.test.core.app.ApplicationProvider
import com.zynergylabs.forager.app.domain.ErrorLog
import com.zynergylabs.forager.app.domain.SeaLevelHeight
import java.io.IOException
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Dispatch 2026-10-28-13: the platform adapter's own rules. Whether Robolectric runs the platform's real conversion with
 * its geoid model is not known, so the conversion itself is a device step (the PR's S22 steps); these are the rules
 * around it.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AndroidSeaLevelConverterTest {

    private val logged = mutableListOf<String>()
    private val log = ErrorLog { _, message, _ -> logged += message }

    @Test
    fun `below Android 14 the height is unsupported, logged once, never the raw height`() = runTest {
        val converter = AndroidSeaLevelConverter(ApplicationProvider.getApplicationContext(), log, StandardTestDispatcher(testScheduler), sdkInt = 33)

        assertEquals(SeaLevelHeight.Unsupported, converter.seaLevel(45.52, -122.68, 50.0))
        assertEquals(SeaLevelHeight.Unsupported, converter.seaLevel(45.52, -122.68, 51.0))
        assertEquals("logged once, not per fix", 1, logged.size)
    }

    @Test
    fun `a fix with no height is no height, and nothing is logged`() = runTest {
        val converter = AndroidSeaLevelConverter(ApplicationProvider.getApplicationContext(), log, StandardTestDispatcher(testScheduler), sdkInt = 33)

        assertEquals(SeaLevelHeight.NoHeight, converter.seaLevel(45.52, -122.68, null))
        assertEquals(0, logged.size)
    }

    /**
     * Amendment 3, N6 (RECORD -1797): an exception from the platform's conversion other than the two it documents is
     * "unsupported", logged, and not a crash of the fix collection. Through a stand-in for the platform call on a phone
     * at API 34.
     */
    @Test
    fun `any exception from the platform's conversion is unsupported and logged, not thrown`() = runTest {
        val converter = AndroidSeaLevelConverter(
            ApplicationProvider.getApplicationContext(), log, StandardTestDispatcher(testScheduler), sdkInt = 34,
            conversion = { _, _, _ -> throw IllegalStateException("a made-up platform failure") },
        )

        assertEquals(SeaLevelHeight.Unsupported, converter.seaLevel(45.52, -122.68, 50.0))
        assertEquals(1, logged.size)
    }

    /**
     * Amendment 3, N7: each reason is logged once, not only the first reason of the process. The geoid model fails, then
     * the platform refuses a location twice, then the model fails again: two lines, one per reason.
     */
    @Test
    fun `each reason for unsupported is logged once, not only the first`() = runTest {
        var calls = 0
        val converter = AndroidSeaLevelConverter(
            ApplicationProvider.getApplicationContext(), log, StandardTestDispatcher(testScheduler), sdkInt = 34,
            conversion = { _, _, _ ->
                calls++
                if (calls == 1 || calls == 4) throw IOException("a made-up model failure") else throw IllegalArgumentException("a made-up refusal")
            },
        )

        repeat(4) { assertEquals(SeaLevelHeight.Unsupported, converter.seaLevel(45.52, -122.68, 50.0)) }
        assertEquals("one line per reason: $logged", 2, logged.size)
        assertTrue(logged[0], logged[0].contains("geoid model"))
        assertTrue(logged[1], logged[1].contains("refused"))
    }
}
