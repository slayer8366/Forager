package com.zynergylabs.forager.app.map

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import com.zynergylabs.forager.app.domain.mapdata.Coverage
import com.zynergylabs.forager.app.domain.mapdata.ReadNearbyMapData
import com.zynergylabs.forager.app.domain.mapdata.TileId
import com.zynergylabs.forager.app.routing.DropOffTestData
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.util.zip.Deflater

/**
 * [MapLibreOfflineBasemap] on a store laid out as MapLibre Native 13.5.0 lays out `mbgl-offline.db`: its `resources`
 * and `tiles` tables, with the columns `offline_schema.sql` gives them at tag `android-v13.5.0` (way-to dispatch
 * 2026-10-28, PR 2). The store here is built by the test, not by MapLibre: what MapLibre writes on a phone is a device
 * check, not something this can prove.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class MapLibreOfflineBasemapTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val template = "https://tiles.example.invalid/us/{z}/{x}/{y}.mvt"
    private val fixtureTile = TileId(15, 9875, 12393)
    private val tile = DropOffTestData.resource(DropOffTestData.TILE).readBytes()
    private fun tileJson(name: String) =
        File(checkNotNull(javaClass.classLoader?.getResource("mapdata/$name")).toURI()).readText()

    private fun deflate(b: ByteArray): ByteArray {
        val d = Deflater()
        d.setInput(b)
        d.finish()
        val out = ByteArray(b.size + 1024)
        val n = d.deflate(out)
        d.end()
        return out.copyOf(n)
    }

    /** The two tables MapLibre's schema has that this reads, column for column as offline_schema.sql declares them. */
    private fun store(tileJson: String?, compressTiles: Boolean = true, otherSources: List<String> = emptyList()): File {
        val file = File(tmp.root, "mbgl-offline.db")
        SQLiteDatabase.openOrCreateDatabase(file, null).use { db ->
            db.execSQL(
                "CREATE TABLE resources (id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT, url TEXT NOT NULL, kind INTEGER NOT NULL, " +
                    "expires INTEGER, modified INTEGER, etag TEXT, data BLOB, compressed INTEGER NOT NULL DEFAULT 0, " +
                    "accessed INTEGER NOT NULL, must_revalidate INTEGER NOT NULL DEFAULT 0, UNIQUE (url))",
            )
            db.execSQL(
                "CREATE TABLE tiles (id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT, url_template TEXT NOT NULL, pixel_ratio INTEGER NOT NULL, " +
                    "z INTEGER NOT NULL, x INTEGER NOT NULL, y INTEGER NOT NULL, expires INTEGER, modified INTEGER, etag TEXT, data BLOB, " +
                    "compressed INTEGER NOT NULL DEFAULT 0, accessed INTEGER NOT NULL, must_revalidate INTEGER NOT NULL DEFAULT 0, " +
                    "UNIQUE (url_template, pixel_ratio, z, x, y))",
            )
            var url = 0
            fun resource(kind: Int, json: String, modified: Long) = db.insertOrThrow("resources", null, ContentValues().apply {
                put("url", "https://tiles.example.invalid/source-${url++}.json"); put("kind", kind); put("modified", modified)
                put("data", deflate(json.toByteArray())); put("compressed", 1); put("accessed", 0)
            })
            otherSources.forEach { resource(2, it, modified = 10) }
            if (tileJson != null) resource(2, tileJson, modified = 5)
            resource(1, """{"version":8}""", modified = 20) // a style, kind 1: never read as a source
            db.insertOrThrow("tiles", null, ContentValues().apply {
                put("url_template", template); put("pixel_ratio", 1); put("z", fixtureTile.z); put("x", fixtureTile.x); put("y", fixtureTile.y)
                put("data", if (compressTiles) deflate(tile) else tile); put("compressed", if (compressTiles) 1 else 0); put("accessed", 0)
            })
        }
        return file
    }

    @Test
    fun `reads the basemap's TileJSON and a stored tile, decompressed`() = runBlocking {
        val basemap = MapLibreOfflineBasemap(store(tileJson("tilejson-with-0003.json")))
        assertEquals(tileJson("tilejson-with-0003.json"), basemap.tileJson().getOrThrow())
        assertArrayEquals(tile, basemap.tile(fixtureTile).getOrThrow())
        assertNull(basemap.tile(TileId(15, 9876, 12393)).getOrThrow())
    }

    @Test
    fun `an uncompressed tile row is read as it is`() = runBlocking {
        assertArrayEquals(tile, MapLibreOfflineBasemap(store(tileJson("tilejson-with-0003.json"), compressTiles = false)).tile(fixtureTile).getOrThrow())
    }

    @Test
    fun `the overlay's TileJSON is passed over for the basemap's`() = runBlocking {
        val trails = """{"tiles":["https://tiles.example.invalid/reference-trails/{z}/{x}/{y}.mvt"],"vector_layers":[{"id":"fs_trails"}]}"""
        val basemap = MapLibreOfflineBasemap(store(tileJson("tilejson-with-0003.json"), otherSources = listOf(trails)))
        assertEquals(tileJson("tilejson-with-0003.json"), basemap.tileJson().getOrThrow())
    }

    @Test
    fun `through the domain reader, a store from before patch 0003 says fences were not checked`() = runBlocking {
        val data = ReadNearbyMapData(MapLibreOfflineBasemap(store(tileJson("tilejson-before-0003.json"))))(DropOffTestData.point("pin_a"), 100.0).getOrThrow()
        assertEquals(Coverage.NotInTileSet("the tile set has no barriers layer"), data.schema.fences)
        assertEquals(4, data.features.buildings.size)
    }

    @Test
    fun `no store file is no basemap stored, not a failure`() = runBlocking {
        val basemap = MapLibreOfflineBasemap(File(tmp.root, "absent.db"))
        assertNull(basemap.tileJson().getOrThrow())
        assertNull(basemap.tile(fixtureTile).getOrThrow())
    }

    @Test
    fun `a file that is not a database fails`() = runBlocking {
        val junk = tmp.newFile("junk.db").apply { writeText("not sqlite") }
        assertTrue(MapLibreOfflineBasemap(junk).tileJson().isFailure)
    }
}
