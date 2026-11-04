package com.zynergylabs.forager.app.domain.relief

import com.zynergylabs.forager.app.domain.ErrorLog
import com.zynergylabs.forager.app.domain.GroundHeight
import com.zynergylabs.forager.app.domain.GroundHeightUnknown
import com.zynergylabs.forager.app.domain.model.LatLng
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.atan
import kotlin.math.roundToInt
import kotlin.math.sinh

/**
 * Dispatch 2026-11-03-07, C2: the ground height at a point from relief tiles, headless (B4), against small fixture tiles
 * with known posts. Mount Hood's z11 tile, 331/733, and its east neighbour carry the fixtures.
 */
class ReliefGroundHeightSourceTest {

    private val world = (1 shl ReliefGroundHeightSource.RELIEF_ZOOM) * ReliefGroundHeightSource.RELIEF_TILE_SIZE

    /** The point at global pixel position ([gx], [gy]) at z11; a pixel's centre is (i + 0.5, j + 0.5). */
    private fun at(gx: Double, gy: Double) = LatLng(
        lat = Math.toDegrees(atan(sinh(PI * (1 - 2 * gy / world)))),
        lng = gx / world * 360.0 - 180.0,
    )

    /** A tile whose pixel (px, py) is [height](px, py) metres, Terrarium-encoded to 1/256 m. */
    private fun tile(height: (Int, Int) -> Double) = ReliefTile(256, IntArray(256 * 256) { i -> terrarium(height(i % 256, i / 256)) })

    private fun terrarium(metres: Double): Int {
        val v = ((metres + 32768.0) * 256).roundToInt()
        val r = v shr 16
        val g = (v shr 8) and 0xff
        val b = v and 0xff
        return (r shl 16) or (g shl 8) or b
    }

    /**
     * Tiles by key, each found tile with a fingerprint ("v1" unless [fingerprints] says otherwise), as the real store
     * gives one; a test changes [tiles] or [fingerprints] to stand for a region deleted or a tile rewritten.
     */
    private class FakeStore(var tiles: Map<Triple<Int, Int, Int>, ReliefTileRead>) : ReliefTileStore {
        val reads = mutableListOf<Triple<Int, Int, Int>>()
        var fingerprints: Map<Triple<Int, Int, Int>, String> = emptyMap()

        private fun fingerprintOf(key: Triple<Int, Int, Int>) = fingerprints[key] ?: "v1"

        override fun tile(z: Int, x: Int, y: Int): ReliefTileRead {
            val key = Triple(z, x, y)
            reads += key
            return when (val read = tiles[key] ?: ReliefTileRead.NotStored) {
                is ReliefTileRead.Found -> ReliefTileRead.Found(read.tile, fingerprintOf(key))
                else -> read
            }
        }

        override fun linkedFingerprint(z: Int, x: Int, y: Int): String? {
            val key = Triple(z, x, y)
            return if (tiles[key] is ReliefTileRead.Found) fingerprintOf(key) else null
        }
    }

    private val logLines = mutableListOf<String>()
    private val log = ErrorLog { _, message, _ -> logLines += message }

    private fun TestScope.source(store: ReliefTileStore) = ReliefGroundHeightSource(store, log, StandardTestDispatcher(testScheduler))

    private val hood = Triple(11, 331, 733)
    private val east = Triple(11, 332, 733)

    // Tile 331/733: 1000 + px + 2 py, so bilinear interpolation of it is exact, and differs from the nearest post's.
    private val slope = ReliefTileRead.Found(tile { px, py -> 1000.0 + px + 2.0 * py })

    @Test
    fun `a pixel's centre reads that post's height`() = runTest {
        val store = FakeStore(mapOf(hood to slope))
        val height = source(store).groundHeightAt(at(331 * 256 + 100.5, 733 * 256 + 50.5))
        assertEquals(1000.0 + 100 + 2 * 50, (height as GroundHeight.Known).metres, 1e-6)
    }

    @Test
    fun `a point between posts is interpolated from the four around it`() = runTest {
        val store = FakeStore(mapOf(hood to slope))
        // A quarter of the way from post (100, 50) to (101, 51) in x, three quarters in y.
        val height = source(store).groundHeightAt(at(331 * 256 + 100.75, 733 * 256 + 51.25))
        assertEquals("bilinear, not the nearest post (1201 or 1203)", 1000.0 + 100.25 + 2 * 50.75, (height as GroundHeight.Known).metres, 1e-6)
    }

    @Test
    fun `near a tile's edge the neighbour tile's pixels are read, not this tile's edge again`() = runTest {
        val store = FakeStore(
            mapOf(
                hood to ReliefTileRead.Found(tile { px, _ -> 1000.0 + px }),
                east to ReliefTileRead.Found(tile { px, _ -> 2000.0 + px }),
            ),
        )
        // Halfway between hood's last column (1255 m) and east's first (2000 m).
        val height = source(store).groundHeightAt(at(331 * 256 + 256.0, 733 * 256 + 10.5))
        assertEquals(1627.5, (height as GroundHeight.Known).metres, 1e-6)
        assertTrue("both tiles were read: ${store.reads}", store.reads.containsAll(listOf(hood, east)))
    }

    @Test
    fun `a real stored pixel decodes by Terrarium`() {
        // The spike's summit pixel, read back from the SDK's database (run 41752813862): rgb (141, 42, 5).
        assertEquals(3370.01953125, terrariumMetres((141 shl 16) or (42 shl 8) or 5), 0.0)
        assertEquals("alpha is ignored", 3370.01953125, terrariumMetres((0xff shl 24) or (141 shl 16) or (42 shl 8) or 5), 0.0)
    }

    @Test
    fun `a tile no region holds gives no height, logged once for that tile`() = runTest {
        val store = FakeStore(emptyMap())
        val source = source(store)
        val point = at(331 * 256 + 100.5, 733 * 256 + 50.5)

        assertEquals(GroundHeight.Unknown(GroundHeightUnknown.NOT_DOWNLOADED), source.groundHeightAt(point))
        assertEquals(GroundHeight.Unknown(GroundHeightUnknown.NOT_DOWNLOADED), source.groundHeightAt(point))

        assertEquals("asked again, since a download may have stored it since", 2, store.reads.size)
        assertEquals(listOf("No downloaded region holds relief tile 11/331/733: no ground height there."), logLines)
    }

    @Test
    fun `a tile stored with no heights gives no height, logged once`() = runTest {
        val source = source(FakeStore(mapOf(hood to ReliefTileRead.Empty)))
        val point = at(331 * 256 + 100.5, 733 * 256 + 50.5)

        assertEquals(GroundHeight.Unknown(GroundHeightUnknown.NO_DATA), source.groundHeightAt(point))
        assertEquals(GroundHeight.Unknown(GroundHeightUnknown.NO_DATA), source.groundHeightAt(point))

        assertEquals(1, logLines.size)
        assertTrue(logLines.single(), logLines.single().startsWith("Relief tile 11/331/733 was stored with no content"))
    }

    @Test
    fun `a store this build cannot read gives no height anywhere, logged once`() = runTest {
        val store = ReliefTileStore { _, _, _ -> ReliefTileRead.Unsupported("user_version 7, not 6") }
        val source = source(store)

        assertEquals(GroundHeight.Unknown(GroundHeightUnknown.UNSUPPORTED), source.groundHeightAt(at(331 * 256 + 100.5, 733 * 256 + 50.5)))
        assertEquals(GroundHeight.Unknown(GroundHeightUnknown.UNSUPPORTED), source.groundHeightAt(at(100.5, 100.5)))

        assertEquals(listOf("The offline tile store cannot be read for relief: user_version 7, not 6. No ground heights this run."), logLines)
    }

    @Test
    fun `a failed read gives no height and is logged with its error`() = runTest {
        val boom = IllegalStateException("disk")
        val errors = mutableListOf<Throwable>()
        val source = ReliefGroundHeightSource(ReliefTileStore { _, _, _ -> ReliefTileRead.Failed(boom) }, { _, _, e -> errors += e }, StandardTestDispatcher(testScheduler))

        assertEquals(GroundHeight.Unknown(GroundHeightUnknown.READ_FAILED), source.groundHeightAt(at(331 * 256 + 100.5, 733 * 256 + 50.5)))

        assertEquals(listOf<Throwable>(boom), errors)
    }

    @Test
    fun `a tile of another size is not read as relief`() = runTest {
        val small = ReliefTileRead.Found(ReliefTile(2, IntArray(4)))
        val source = source(FakeStore(mapOf(hood to small)))

        assertEquals(GroundHeight.Unknown(GroundHeightUnknown.UNSUPPORTED), source.groundHeightAt(at(331 * 256 + 100.5, 733 * 256 + 50.5)))
    }

    @Test
    fun `a tile once read is kept, so a second bubble nearby reads no disk`() = runTest {
        val store = FakeStore(mapOf(hood to slope))
        val source = source(store)

        source.groundHeightAt(at(331 * 256 + 100.5, 733 * 256 + 50.5))
        source.groundHeightAt(at(331 * 256 + 120.5, 733 * 256 + 60.5))

        assertEquals(listOf(hood), store.reads)
    }

    @Test
    fun `a kept tile no region holds any more gives no height, as a tile never downloaded does`() = runTest {
        // S22 session 11: a region deleted, and another waypoint in the same relief tile still read a height.
        val store = FakeStore(mapOf(hood to slope))
        val source = source(store)
        val point = at(331 * 256 + 100.5, 733 * 256 + 50.5)
        assertEquals(1000.0 + 100 + 2 * 50, (source.groundHeightAt(point) as GroundHeight.Known).metres, 1e-6)

        store.tiles = emptyMap() // the region that held it is deleted

        assertEquals(GroundHeight.Unknown(GroundHeightUnknown.NOT_DOWNLOADED), source.groundHeightAt(point))
        assertEquals(listOf("No downloaded region holds relief tile 11/331/733: no ground height there."), logLines)
    }

    @Test
    fun `a kept tile whose stored row changed is read again`() = runTest {
        val store = FakeStore(mapOf(hood to slope))
        val source = source(store)
        val point = at(331 * 256 + 100.5, 733 * 256 + 50.5)
        source.groundHeightAt(point)

        store.tiles = mapOf(hood to ReliefTileRead.Found(tile { _, _ -> 500.0 }))
        store.fingerprints = mapOf(hood to "v2")

        assertEquals(500.0, (source.groundHeightAt(point) as GroundHeight.Known).metres, 1e-6)
        assertEquals("read once, then again for the new row", listOf(hood, hood), store.reads)
    }
}
