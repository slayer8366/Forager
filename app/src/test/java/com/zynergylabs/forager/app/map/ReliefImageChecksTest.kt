package com.zynergylabs.forager.app.map

import com.zynergylabs.forager.app.domain.GroundHeight
import com.zynergylabs.forager.app.domain.GroundHeightUnknown
import com.zynergylabs.forager.app.domain.relief.ReliefGroundHeightSource
import com.zynergylabs.forager.app.domain.relief.ReliefTileRead
import com.zynergylabs.forager.app.map.SyntheticReliefTiles.FIRST_IDAT_END
import java.io.File
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Dispatch 2026-11-05-01, Amendment 2 (A10, B4): a stored relief image is checked before it is decoded, by its own
 * checksums, and a damaged one gives the waypoint bubble no height, with one line naming the tile, never a wrong height.
 *
 * Through the real read: [OfflineReliefTileStore] on a store file, inside the bubble's height source as
 * `MapLibreStoreRefusalTest` drives it. The tiles are synthetic and real-shaped ([SyntheticReliefTiles]). Native graphics,
 * so the PNG is decoded by the platform's own decoder, which is the thing the check stands in front of.
 *
 * What each damage leaves for a check to find (the fixture checks this of itself; the laptop's zlib agreed):
 * - one byte changed in the first IDAT chunk: that chunk's CRC-32, and the image data's Adler-32;
 * - a splice at a 4 KiB page inside the first chunk: that chunk's CRC-32, and the image data's zlib stream;
 * - a splice at a page boundary where the first chunk ends: every chunk's CRC-32 matches, and only the image data's zlib
 *   stream can tell.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ReliefImageChecksTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val logged = mutableListOf<Pair<String, Throwable>>()

    private val first = SyntheticReliefTiles.image(flatMetres = 1234.5, seed = 1)
    private val second = SyntheticReliefTiles.image(flatMetres = 2345.25, seed = 2)

    private val tile = "${SyntheticReliefTiles.Z}/${SyntheticReliefTiles.X}/${SyntheticReliefTiles.Y}"

    private fun store(png: ByteArray, compressed: Boolean = false): File =
        SyntheticReliefTiles.store(File(tmp.newFolder(), "mbgl-offline.db"), png, compressed)

    /** The waypoint bubble's height at the synthetic tile's flat patch, read from [png] stored as MapLibre stores it. */
    private suspend fun TestScope.bubbleHeight(png: ByteArray, compressed: Boolean = false): GroundHeight =
        ReliefGroundHeightSource(
            OfflineReliefTileStore(store(png, compressed)),
            { _, message, error -> logged += message to error },
            StandardTestDispatcher(testScheduler),
        ).groundHeightAt(SyntheticReliefTiles.TARGET)

    private fun logs() = logged.map { (message, error) -> "$message [${error.message}]" }

    /** No height, which the bubble shows as no line, and one line naming the tile whose error names [check]. */
    private fun assertRefused(height: GroundHeight, check: String) {
        assertEquals(
            "no height, the bubble's no-line case, never a wrong one (logged: ${logs()})",
            GroundHeight.Unknown(GroundHeightUnknown.READ_FAILED),
            height,
        )
        assertEquals("one line for the refused image: ${logs()}", 1, logged.size)
        val (message, error) = logged.single()
        assertTrue("the line names the tile, $tile: $message", tile in message)
        assertTrue("the line says the image failed its $check: ${error.message}", check in error.message.orEmpty())
    }

    // ── The guard: an intact image reads as before ──

    @Test
    fun `an intact stored relief image still gives its height, pixel for pixel, and logs nothing`() = runTest {
        val read = OfflineReliefTileStore(store(first.png)).tile(SyntheticReliefTiles.Z, SyntheticReliefTiles.X, SyntheticReliefTiles.Y)

        assertTrue("expected the tile, got $read", read is ReliefTileRead.Found)
        val decoded = (read as ReliefTileRead.Found).tile
        for (y in 0 until decoded.size) for (x in 0 until decoded.size) {
            assertEquals("pixel ($x, $y)", first.metresAt(x, y), decoded.heightAt(x, y), 0.0)
        }
        val height = bubbleHeight(first.png)
        assertTrue("expected a height, got $height (logged: ${logs()})", height is GroundHeight.Known)
        assertEquals(1234.5, (height as GroundHeight.Known).metres, 1e-6)
        assertEquals("nothing logged: ${logs()}", 0, logged.size)
    }

    // ── A damaged image is refused ──

    @Test
    fun `one byte changed inside a chunk's data fails its CRC-32 - no height, and one line naming the tile`() = runTest {
        val damaged = SyntheticReliefTiles.oneByteChanged(first.png)
        assertEquals("fixture: only the first IDAT chunk's CRC-32 fails", listOf(true, false, true, true, true), SyntheticReliefTiles.chunks(damaged).map { it.second })

        assertRefused(bubbleHeight(damaged), "CRC-32")
    }

    @Test
    fun `one byte changed inside a chunk's data of an image stored deflated is refused after it is inflated`() = runTest {
        assertRefused(bubbleHeight(SyntheticReliefTiles.oneByteChanged(first.png), compressed = true), "CRC-32")
    }

    @Test
    fun `an image spliced from two at a page inside a chunk - no height, and one line naming the tile`() = runTest {
        val torn = SyntheticReliefTiles.splice(first.png, second.png, at = 4 * 4096)
        assertEquals("fixture: only the first IDAT chunk's CRC-32 fails", listOf(true, false, true, true, true), SyntheticReliefTiles.chunks(torn).map { it.second })

        assertRefused(bubbleHeight(torn), "CRC-32")
    }

    @Test
    fun `an image spliced from two where a chunk ends passes every CRC-32 and fails its zlib stream - no height, one line`() = runTest {
        val torn = SyntheticReliefTiles.splice(first.png, second.png, at = FIRST_IDAT_END)
        assertEquals("fixture: every chunk's CRC-32 matches", listOf(true, true, true, true, true), SyntheticReliefTiles.chunks(torn).map { it.second })

        assertRefused(bubbleHeight(torn), "zlib")
    }
}
