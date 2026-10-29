package com.zynergylabs.forager.app.location

import androidx.test.core.app.ApplicationProvider
import com.zynergylabs.forager.app.domain.ErrorLog
import com.zynergylabs.forager.app.domain.SeaLevelHeight
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
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
}
