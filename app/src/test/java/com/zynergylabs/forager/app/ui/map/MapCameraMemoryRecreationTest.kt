package com.zynergylabs.forager.app.ui.map

import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.SaverScope
import com.zynergylabs.forager.app.domain.model.LatLng
import com.zynergylabs.forager.app.domain.model.Region
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.ObjectInputStream
import java.io.ObjectOutputStream

/**
 * RECORD -2192: what the Maps tab's camera memory saves for an activity recreation (the applied camera request id, the applied
 * search frame id and the saved camera), and that a value saved by another process (the app killed in the background, then
 * restored) brings nothing back, since the saved route ids it names are counted afresh in a new process. The recreation itself,
 * through the real screen, is `KeepFollowOnRecreateTest`'s and `MapCameraOnRecreateTest`'s; a process restore cannot be run under
 * Robolectric, so the saver is read here directly.
 */
class MapCameraMemoryRecreationTest {

    private val scope = SaverScope { true }

    private val camera = MapCameraSnapshot(
        target = LatLng(45.4123, -121.7531),
        zoom = 15.25,
        bearing = 30.0,
        tilt = 20.0,
        following = false,
        appliedTarget = Region(45.40, -121.70, 10) to LatLng(45.41, -121.71),
    )

    private fun saved(saver: Saver<MapCameraMemory, SavedMapCameraMemory>, memory: MapCameraMemory): SavedMapCameraMemory? =
        with(saver) { scope.save(memory) }

    private fun memory(id: String? = null, frame: Int = 0, snapshot: MapCameraSnapshot? = null) = MapCameraMemory().apply {
        appliedCameraRequestId = id
        appliedSearchFrameId = frame
        saved = snapshot
    }

    @Test
    fun `a recreation in the same process brings back the applied request id`() {
        val saver = mapCameraMemorySaver("this-process")
        val value = saved(saver, memory(id = "saved-route-trip-3"))

        assertEquals("saved-route-trip-3", value?.let(saver::restore)?.appliedCameraRequestId)
    }

    @Test
    fun `a recreation in the same process brings back the applied search frame and the camera`() {
        val saver = mapCameraMemorySaver("this-process")
        val restored = saved(saver, memory(frame = 4, snapshot = camera))?.let(saver::restore)

        assertEquals(4, restored?.appliedSearchFrameId)
        assertEquals(camera, restored?.saved)
    }

    @Test
    fun `a recreation in the same process brings back the camera's padding`() {
        val saver = mapCameraMemorySaver("this-process")
        val padded = camera.copy(padding = listOf(0.0, 270.0, 1544.0, 0.0))
        val value = saved(saver, memory(snapshot = padded))!!
        val bytes = ByteArrayOutputStream().also { out -> ObjectOutputStream(out).use { it.writeObject(value) } }.toByteArray()
        val back = ObjectInputStream(ByteArrayInputStream(bytes)).use { it.readObject() } as SavedMapCameraMemory

        assertEquals(listOf(0.0, 270.0, 1544.0, 0.0), saver.restore(back)?.saved?.padding)
    }

    @Test
    fun `a camera with no applied target comes back with none`() {
        val saver = mapCameraMemorySaver("this-process")
        val plain = camera.copy(following = true, appliedTarget = null)

        assertEquals(plain, saved(saver, memory(snapshot = plain))?.let(saver::restore)?.saved)
    }

    @Test
    fun `the saved value survives Java serialization as a Bundle holds it`() {
        val value = saved(mapCameraMemorySaver("this-process"), memory(id = "saved-route-trip-5", frame = 2, snapshot = camera))!!
        val bytes = ByteArrayOutputStream().also { out -> ObjectOutputStream(out).use { it.writeObject(value) } }.toByteArray()
        val back = ObjectInputStream(ByteArrayInputStream(bytes)).use { it.readObject() }

        assertEquals(value, back)
    }

    @Test
    fun `nothing applied and no camera saves nothing`() {
        assertNull(saved(mapCameraMemorySaver("this-process"), memory()))
    }

    @Test
    fun `a value saved by another process restores nothing`() {
        val value = saved(mapCameraMemorySaver("earlier-process"), memory(id = "saved-route-trip-1", frame = 3, snapshot = camera))!!

        assertNull(mapCameraMemorySaver("this-process").restore(value))
    }

    @Test
    fun `the app's own saver keeps an id within this process`() {
        val value = saved(MapCameraMemoryAcrossRecreation, memory(id = "saved-route-trip-2"))!!

        assertEquals("saved-route-trip-2", MapCameraMemoryAcrossRecreation.restore(value)?.appliedCameraRequestId)
    }
}
