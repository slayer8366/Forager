package com.zynergylabs.forager.app.domain.mapdata

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.zip.Deflater
import java.util.zip.GZIPOutputStream

/** MapLibre's stored rows, as `StoredTiles` reads them (way-to dispatch 2026-10-28, PR 2). */
class StoredTilesTest {

    private val tile = "an mvt body".repeat(20).toByteArray()

    private fun deflate(b: ByteArray): ByteArray {
        val d = Deflater()
        d.setInput(b)
        d.finish()
        val out = ByteArray(b.size + 64)
        val n = d.deflate(out)
        d.end()
        return out.copyOf(n)
    }

    private fun gzip(b: ByteArray) = ByteArrayOutputStream().also { o -> GZIPOutputStream(o).use { it.write(b) } }.toByteArray()

    @Test
    fun `a row flagged compressed is zlib Deflate`() {
        assertArrayEquals(tile, StoredTiles.bytesOf(deflate(tile), compressed = true))
    }

    @Test
    fun `a row not flagged is as served`() {
        assertArrayEquals(tile, StoredTiles.bytesOf(tile, compressed = false))
    }

    @Test
    fun `a body still in gzip is unwrapped, compressed or not`() {
        assertArrayEquals(tile, StoredTiles.bytesOf(gzip(tile), compressed = false))
        assertArrayEquals(tile, StoredTiles.bytesOf(deflate(gzip(tile)), compressed = true))
    }

    @Test
    fun `a compressed row cut short fails`() {
        val cut = deflate(tile).let { it.copyOf(it.size / 2) }
        assertThrows(IOException::class.java) { StoredTiles.bytesOf(cut, compressed = true) }
    }

    @Test
    fun `the basemap's TileJSON is the one with a buildings layer, and its first tiles entry is the template`() {
        val trails = """{"tiles":["https://x.invalid/reference-trails/{z}/{x}/{y}.mvt"],"vector_layers":[{"id":"fs_trails"}]}"""
        val basemap = """{"tiles":["https://x.invalid/us/{z}/{x}/{y}.mvt"],"vector_layers":[{"id":"roads"},{"id":"buildings"}]}"""
        val picked = StoredTiles.basemapSource(listOf("not json", trails, basemap))
        assertEquals(StoredTiles.BasemapSource(basemap, "https://x.invalid/us/{z}/{x}/{y}.mvt"), picked)
        assertNull(StoredTiles.basemapSource(listOf(trails)))
    }
}
