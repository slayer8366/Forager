package com.zynergylabs.forager.app.domain.mapdata

import com.zynergylabs.forager.app.routing.DropOffTestData
import java.io.IOException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The read order the way-to reads tiles in (dispatch 2026-11-04-09, B1 P3: "MapLibre's marked tile first, then the copy's
 * marked tile, then exactly today's"), branch by branch, over a given MapLibre store and a given copy.
 */
class LayeredOfflineBasemapTest {

    private val tile = TileId(15, 9875, 12393)
    private val marked = DropOffTestData.resource(DropOffTestData.TILE).readBytes()
    private val unmarked = DropOffTestData.resource(DropOffTestData.TILE_BEFORE_0003).readBytes()
    private val notATile = byteArrayOf(0x1a, 0x7f, 0x00)

    private class MapLibre(val bytes: Result<ByteArray?>, val json: Result<String?> = Result.success("{}")) : OfflineBasemap {
        override suspend fun tileJson() = json
        override suspend fun tile(id: TileId) = bytes
    }

    private class Copy(val bytes: Result<ByteArray?>) : DropOffTileCopy {
        var reads = 0
        val discarded = mutableListOf<Pair<TileId, String>>()
        override suspend fun tile(id: TileId): Result<ByteArray?> {
            reads++
            return bytes
        }
        override suspend fun discard(id: TileId, reason: String) {
            discarded += id to reason
        }
    }

    private val logs = mutableListOf<String>()

    private fun read(mapLibre: OfflineBasemap, copy: DropOffTileCopy) =
        runBlocking { LayeredOfflineBasemap(mapLibre, copy) { message, _ -> logs += message }.tile(tile) }

    @Test
    fun `MapLibre's tile with the mark is read, and the copy is not asked`() {
        val copy = Copy(Result.success(marked))
        assertArrayEquals(marked, read(MapLibre(Result.success(marked)), copy).getOrThrow())
        assertEquals(0, copy.reads)
    }

    @Test
    fun `MapLibre's tile without the mark gives way to the copy's marked tile`() {
        assertArrayEquals(marked, read(MapLibre(Result.success(unmarked)), Copy(Result.success(marked))).getOrThrow())
    }

    @Test
    fun `a tile MapLibre does not hold is read from the copy when it holds it marked`() {
        assertArrayEquals(marked, read(MapLibre(Result.success(null)), Copy(Result.success(marked))).getOrThrow())
    }

    @Test
    fun `MapLibre's unmarked tile, with no copy of it, is read exactly as today`() {
        assertArrayEquals(unmarked, read(MapLibre(Result.success(unmarked)), Copy(Result.success(null))).getOrThrow())
    }

    @Test
    fun `a tile neither holds is not stored, as today`() {
        assertNull(read(MapLibre(Result.success(null)), Copy(Result.success(null))).getOrThrow())
    }

    @Test
    fun `MapLibre's tile that does not decode is handed on as today, so the reader fails on it, and the copy is not asked`() {
        val copy = Copy(Result.success(marked))
        assertArrayEquals(notATile, read(MapLibre(Result.success(notATile)), copy).getOrThrow())
        assertEquals(0, copy.reads)
    }

    @Test
    fun `a MapLibre store that cannot be read fails the read, as today, and the copy is not asked`() {
        val copy = Copy(Result.success(marked))
        val result = read(MapLibre(Result.failure(IOException("mbgl-offline.db is locked"))), copy)
        assertEquals("mbgl-offline.db is locked", result.exceptionOrNull()?.message)
        assertEquals(0, copy.reads)
    }

    @Test
    fun `a copy's tile that does not decode is discarded with the reason, and MapLibre's bytes are read as today`() {
        val copy = Copy(Result.success(notATile))
        assertArrayEquals(unmarked, read(MapLibre(Result.success(unmarked)), copy).getOrThrow())
        assertEquals(listOf(tile), copy.discarded.map { it.first })
        assertTrue(copy.discarded.single().second, "does not decode" in copy.discarded.single().second)
    }

    @Test
    fun `a copy's tile without the mark is discarded too, and MapLibre's bytes are read as today`() {
        val copy = Copy(Result.success(unmarked))
        assertNull(read(MapLibre(Result.success(null)), copy).getOrThrow())
        assertEquals(listOf(tile), copy.discarded.map { it.first })
        assertTrue(copy.discarded.single().second, "no barrier mark" in copy.discarded.single().second)
    }

    @Test
    fun `a copy that cannot be read is logged, and MapLibre's bytes are read as today`() {
        val result = read(MapLibre(Result.success(unmarked)), Copy(Result.failure(IOException("offline_basemap is unreadable"))))
        assertArrayEquals(unmarked, result.getOrThrow())
        assertTrue("logged: $logs", logs.any { "15/9875/12393" in it && "copy" in it })
    }

    @Test
    fun `the TileJSON is MapLibre's, as today`() {
        val json = """{"tiles":["https://tiles.example.invalid/us/{z}/{x}/{y}.mvt"]}"""
        val read = runBlocking { LayeredOfflineBasemap(MapLibre(Result.success(null), Result.success(json)), Copy(Result.success(marked))) { _, _ -> }.tileJson() }
        assertEquals(json, read.getOrThrow())
    }
}
