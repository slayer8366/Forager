package com.zynergylabs.forager.app.ui.map

import androidx.compose.runtime.saveable.SaverScope
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * RECORD -2192: what the Maps tab's camera memory saves for an activity recreation, and that a value saved by another process
 * (the app killed in the background, then restored) brings nothing back, since the saved route ids it names are counted afresh
 * in a new process. The recreation itself, through the real screen, is `KeepFollowOnRecreateTest`'s; a process restore cannot
 * be run under Robolectric, so the saver is read here directly.
 */
class MapCameraMemoryRecreationTest {

    private val scope = SaverScope { true }

    private fun saved(saver: androidx.compose.runtime.saveable.Saver<MapCameraMemory, String>, id: String?): String? =
        with(saver) { scope.save(MapCameraMemory().apply { appliedCameraRequestId = id }) }

    @Test
    fun `a recreation in the same process brings back the applied request id`() {
        val saver = mapCameraMemorySaver("this-process")
        val value = saved(saver, "saved-route-trip-3")

        assertEquals("saved-route-trip-3", value?.let(saver::restore)?.appliedCameraRequestId)
    }

    @Test
    fun `no applied request saves nothing`() {
        assertNull(saved(mapCameraMemorySaver("this-process"), null))
    }

    @Test
    fun `a value saved by another process restores nothing`() {
        val value = saved(mapCameraMemorySaver("earlier-process"), "saved-route-trip-1")!!

        assertNull(mapCameraMemorySaver("this-process").restore(value))
    }

    @Test
    fun `the app's own saver keeps an id within this process`() {
        val value = saved(MapCameraMemoryAcrossRecreation, "saved-route-trip-2")!!

        assertEquals("saved-route-trip-2", MapCameraMemoryAcrossRecreation.restore(value)?.appliedCameraRequestId)
    }
}
