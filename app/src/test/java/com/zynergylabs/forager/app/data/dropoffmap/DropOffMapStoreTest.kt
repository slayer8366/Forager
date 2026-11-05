package com.zynergylabs.forager.app.data.dropoffmap

import com.zynergylabs.forager.app.domain.mapdata.RegionDropOffMap
import com.zynergylabs.forager.app.domain.mapdata.TileId
import com.zynergylabs.forager.app.routing.DropOffTestData
import java.io.File
import java.io.IOException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * [DropOffMapStore]: the drop-off map copy's folders, one per area under `offline_basemap` (dispatch 2026-11-04-09,
 * the Phase 1 report section 2): tiles written whole or not at all, read from any area, empty answers and the whole
 * record, an area's delete, and the clean-up of what no area owns.
 */
class DropOffMapStoreTest {

    @get:Rule
    val temp = TemporaryFolder()

    private val logs = mutableListOf<String>()
    private val root by lazy { File(temp.root, "offline_basemap") }
    private val store by lazy { DropOffMapStore(root, { message, _ -> logs += message }) }

    private val marked = DropOffTestData.resource(DropOffTestData.TILE).readBytes()
    private val a = TileId(15, 9875, 12393)
    private val b = TileId(15, 9876, 12393)

    private fun inPass(regionId: Long, block: () -> Unit) {
        assertTrue("a pass for $regionId could begin", store.beginPass(regionId))
        try {
            block()
        } finally {
            store.endPass(regionId)
        }
    }

    private fun read(id: TileId) = runBlocking { store.tile(id) }.getOrThrow()

    @Test
    fun `a written tile reads back as the same bytes, held by its own area only, and is stored compressed`() {
        inPass(3) { store.write(3, a, marked) }

        assertArrayEquals(marked, read(a))
        assertTrue(store.holds(3, a))
        assertFalse(store.holds(8, a))
        assertNull(read(b))
        val status = store.status(3)
        assertEquals(1, status.tiles)
        assertTrue("stored in ${status.sizeBytes} bytes, the tile is ${marked.size}", status.sizeBytes in 1 until marked.size)
    }

    @Test
    fun `a tile one area holds is read whichever area the reader is in`() {
        inPass(8) { store.write(8, b, marked) }
        assertArrayEquals(marked, read(b))
    }

    @Test
    fun `an empty answer is held, so it is not asked for again, and reads as no tile`() {
        inPass(3) { store.markEmpty(3, a) }

        assertTrue(store.holds(3, a))
        assertNull(read(a))
        assertEquals(RegionDropOffMap(tiles = 0, emptyTiles = 1, sizeBytes = 0L, whole = false), store.status(3))
    }

    @Test
    fun `an area is whole only once its record is written, and its size counts every file`() {
        inPass(3) {
            store.write(3, a, marked)
            store.markEmpty(3, b)
            assertFalse(store.isWhole(3))
            store.markWhole(3)
        }

        assertTrue(store.isWhole(3))
        val status = store.status(3)
        assertEquals(1, status.tiles)
        assertEquals(1, status.emptyTiles)
        assertTrue(status.whole)
        assertEquals(File(root, "3").listFiles()!!.sumOf { it.length() }, status.sizeBytes)
    }

    @Test
    fun `a second pass for an area already in one is refused, and the first's end frees it`() {
        assertTrue(store.beginPass(3))
        assertFalse(store.beginPass(3))
        store.endPass(3)
        assertTrue(store.beginPass(3))
        store.endPass(3)
    }

    @Test
    fun `deleting an area's copy removes its folder, and another area's copy of the same tile still reads`() {
        inPass(3) { store.write(3, a, marked) }
        inPass(8) { store.write(8, a, marked) }

        store.delete(3)

        assertFalse(File(root, "3").exists())
        assertEquals(RegionDropOffMap.NONE, store.status(3))
        assertArrayEquals(marked, read(a))
    }

    @Test
    fun `a write after the area's copy was deleted fails, rather than making its folder again`() {
        assertTrue(store.beginPass(3))
        store.delete(3)
        try {
            store.write(3, a, marked)
            fail("the write should have failed")
        } catch (e: IOException) {
            // expected
        } finally {
            store.endPass(3)
        }
        assertFalse(File(root, "3").exists())
    }

    @Test
    fun `discarding a tile removes it from every area that holds it and their whole records, and logs why`() {
        inPass(3) {
            store.write(3, a, marked)
            store.markWhole(3)
        }
        inPass(8) {
            store.write(8, a, marked)
            store.write(8, b, marked)
            store.markWhole(8)
        }

        runBlocking { store.discard(a, "it does not decode") }

        assertNull(read(a))
        assertFalse(store.holds(3, a) || store.holds(8, a))
        assertTrue(store.holds(8, b))
        assertFalse(store.isWhole(3) || store.isWhole(8))
        assertEquals(logs.toString(), 2, logs.count { "15/9875/12393" in it && "it does not decode" in it })
    }

    @Test
    fun `a stored tile that cannot be unwrapped is discarded when read, logged, and reads as no tile`() {
        inPass(3) {
            store.write(3, a, marked)
            store.markWhole(3)
        }
        val file = File(root, "3").listFiles()!!.single { it.length() > 0 && it.name != "whole" }
        file.writeBytes(file.readBytes().copyOf(10))

        assertNull(read(a))
        assertFalse(store.holds(3, a))
        assertFalse(store.isWhole(3))
        assertTrue(logs.toString(), logs.any { "15/9875/12393" in it })
    }

    @Test
    fun `the clean-up removes copies no area owns, keeps the given ids and a running pass, and leaves names it never writes`() {
        inPass(3) { store.write(3, a, marked) }
        inPass(5) { store.write(5, a, marked) }
        inPass(9) { store.write(9, a, marked) }
        File(root, "notes.txt").writeText("not ours")
        assertTrue(store.beginPass(11))

        store.cleanOrphans(keepIds = setOf(3L))

        assertEquals(setOf("3", "11", "notes.txt"), root.list()!!.toSet())
        assertTrue(logs.toString(), logs.any { "5" in it && "no region 5" in it })
        assertTrue(logs.toString(), logs.any { "notes.txt" in it && "left alone" in it })
        store.endPass(11)
    }

    @Test
    fun `an area with no folder holds nothing`() {
        assertEquals(RegionDropOffMap.NONE, store.status(42))
        assertFalse(store.isWhole(42))
        assertFalse(store.holds(42, a))
    }
}
