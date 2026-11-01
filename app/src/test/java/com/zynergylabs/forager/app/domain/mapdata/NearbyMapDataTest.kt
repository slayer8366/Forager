package com.zynergylabs.forager.app.domain.mapdata

import com.zynergylabs.forager.app.routing.DropOffTestData
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.io.IOException

/**
 * Reading the map data round a point from the offline basemap, and what the stored tile set says it carries (way-to
 * dispatch 2026-10-28, PR 2). The two TileJSONs are the `vector_layers` of the one-area proof's two builds (installed jar
 * and patch 0003), with the tile URL replaced; see the PR. The planner's rule: a tile set with no barriers layer reports
 * fences not checked, never a pass.
 */
class NearbyMapDataTest {

    private val tile = DropOffTestData.resource(DropOffTestData.TILE).readBytes()
    private val fixtureTile = TileId(15, 9875, 12393)
    private fun tileJson(name: String) = File(checkNotNull(javaClass.classLoader?.getResource("mapdata/$name")).toURI()).readText()

    private class FakeBasemap(
        val json: Result<String?>,
        val tiles: Map<TileId, ByteArray>,
        val failTile: Throwable? = null,
    ) : OfflineBasemap {
        val asked = mutableListOf<TileId>()
        override suspend fun tileJson() = json
        override suspend fun tile(id: TileId): Result<ByteArray?> {
            asked += id
            return failTile?.let { Result.failure(it) } ?: Result.success(tiles[id])
        }
    }

    private fun read(basemap: OfflineBasemap, radius: Double = 100.0) =
        runBlocking { ReadNearbyMapData(basemap)(DropOffTestData.point("pin_a"), radius) }

    @Test
    fun `a tile set built with patch 0003 carries fences, address streets and culverts`() {
        val data = read(FakeBasemap(Result.success(tileJson("tilejson-with-0003.json")), mapOf(fixtureTile to tile))).getOrThrow()
        assertEquals(TileSetSchema(Coverage.Checked, Coverage.Checked, Coverage.Checked), data.schema)
        assertEquals(1, data.features.barriers.size)
        assertEquals(emptyList<TileId>(), data.missingTiles)
    }

    @Test
    fun `a tile set from before patch 0003 says fences, streets and culverts were not checked, even with the tile stored`() {
        val data = read(FakeBasemap(Result.success(tileJson("tilejson-before-0003.json")), mapOf(fixtureTile to tile))).getOrThrow()
        assertEquals(Coverage.NotInTileSet("the tile set has no barriers layer"), data.schema.fences)
        assertEquals(Coverage.NotInTileSet("the tile set has no addr_street field"), data.schema.addressStreets)
        assertEquals(Coverage.NotInTileSet("the tile set has no tunnel on water lines (no barriers layer: built before patch 0003)"), data.schema.culverts)
    }

    @Test
    fun `no stored TileJSON is nothing checked`() {
        val data = read(FakeBasemap(Result.success(null), mapOf(fixtureTile to tile))).getOrThrow()
        assertEquals(Coverage.NotInTileSet("no TileJSON is stored for the basemap"), data.schema.fences)
        assertTrue(data.schema.addressStreets is Coverage.NotInTileSet && data.schema.culverts is Coverage.NotInTileSet)
    }

    @Test
    fun `a TileJSON that is not JSON is nothing checked, with why`() {
        val fences = read(FakeBasemap(Result.success("{not json"), mapOf(fixtureTile to tile))).getOrThrow().schema.fences
        assertTrue("$fences", fences is Coverage.NotInTileSet && fences.reason.startsWith("the stored TileJSON is not JSON"))
    }

    @Test
    fun `every tile the circle touches is asked for, and the ones not stored are listed`() {
        // 1,000 m round pin A reaches into the eight tiles round the fixture's (934 m across); only the fixture's is stored.
        val basemap = FakeBasemap(Result.success(tileJson("tilejson-with-0003.json")), mapOf(fixtureTile to tile))
        val data = read(basemap, radius = 1000.0).getOrThrow()
        assertEquals(9, basemap.asked.size)
        assertEquals(basemap.asked - fixtureTile, data.missingTiles)
        assertEquals(4, data.features.buildings.size)
    }

    @Test
    fun `a stored tile that is not a tile fails, naming the tile`() {
        val result = read(FakeBasemap(Result.success(tileJson("tilejson-with-0003.json")), mapOf(fixtureTile to tile.copyOf(tile.size - 7))))
        val e = result.exceptionOrNull()
        assertTrue("$result", e is MvtFormatException && e.message!!.startsWith("tile 15/9875/12393: "))
    }

    @Test
    fun `a store that cannot be read fails with its error`() {
        val broken = IOException("disk")
        assertEquals(broken, read(FakeBasemap(Result.success(tileJson("tilejson-with-0003.json")), emptyMap(), failTile = broken)).exceptionOrNull())
        assertEquals(broken, read(FakeBasemap(Result.failure(broken), emptyMap())).exceptionOrNull())
    }

    @Test
    fun `fences, culverts and address streets come from each tile's barrier mark, not from the TileJSON`() {
        val old = DropOffTestData.resource(DropOffTestData.TILE_BEFORE_0003).readBytes()
        val unmarked = read(FakeBasemap(Result.success(tileJson("tilejson-with-0003.json")), mapOf(fixtureTile to old))).getOrThrow()
        val why = Coverage.NotInTileSet("tile 15/9875/12393 has no barrier mark: it was built before fences were carried")
        assertEquals(why, unmarked.fences(fixtureTile))
        assertEquals(why, unmarked.culverts(fixtureTile))
        assertEquals(why, unmarked.addressStreets(fixtureTile))
        val marked = read(FakeBasemap(Result.success(tileJson("tilejson-before-0003.json")), mapOf(fixtureTile to tile))).getOrThrow()
        assertEquals(Coverage.Checked, marked.fences(fixtureTile))
        assertEquals(Coverage.Checked, marked.addressStreets(fixtureTile))
    }
}
