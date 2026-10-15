package com.zynergylabs.forager.app.diagnostics.burst

import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Where the fast-burst test's frames are saved and what they are called (dispatch 2026-10-15-09: "the app's own storage,
 * not the Gallery or the journal, under a folder the planner can pull with `run-as`"), and the corner-to-centre figure the
 * shading-OFF run is read by.
 */
class PlatformBurstFilesTest {

    private val startedAt = Instant.parse("2026-10-15T22:15:02.789Z").toEpochMilli()

    @Test
    fun `a session's frames sit under one folder named by its UTC start and session, one subfolder a run`() {
        val session = PlatformBurstFiles.sessionFolder(startedAt, session = 3)
        assertEquals("raw-burst-test/20261015-221502Z-s3", session)
        val run = PlatformBurstFiles.runFolder(session, PlatformRun.NR_HOTPIXEL_OFF)
        assertEquals("raw-burst-test/20261015-221502Z-s3/nr_hotpixel_off", run)
        assertEquals(
            (1..8).map { "raw-burst-test/20261015-221502Z-s3/nr_hotpixel_off/frame-0$it.dng" },
            (0 until 8).map { PlatformBurstFiles.frameFile(run, it) },
        )
    }

    @Test
    fun `two sessions never share a folder, and four runs never share a file`() {
        assertNotEquals(PlatformBurstFiles.sessionFolder(startedAt, 1), PlatformBurstFiles.sessionFolder(startedAt, 2))
        assertNotEquals(PlatformBurstFiles.sessionFolder(startedAt, 1), PlatformBurstFiles.sessionFolder(startedAt + 1_000, 1))
        val session = PlatformBurstFiles.sessionFolder(startedAt, 1)
        val all = PlatformRun.entries.flatMap { run -> (0 until 8).map { PlatformBurstFiles.frameFile(PlatformBurstFiles.runFolder(session, run), it) } }
        assertEquals(32, all.toSet().size)
    }

    @Test
    fun `the folder is in the app's own storage, not the journal's photos or the camera's scratch folder`() {
        val session = PlatformBurstFiles.sessionFolder(startedAt, 1)
        assertEquals("raw-burst-test", session.substringBefore('/'))
        assertNotEquals("photos", session.substringBefore('/'))
        assertNotEquals("captures", session.substringBefore('/'))
    }

    /** A 64x48 GRBG-like mosaic: black 64 everywhere; [level] above black at each pixel. */
    private fun mosaic(width: Int = 64, height: Int = 48, level: (x: Int, y: Int) -> Int): ShortArray =
        ShortArray(width * height) { i -> (64 + level(i % width, i / width)).toShort() }

    private val black = listOf(64f, 64f, 64f, 64f)

    @Test
    fun `a flat frame reads one`() {
        val ratio = rawCornerToCentre(mosaic { _, _ -> 500 }, 64, 48, black)!!
        assertEquals(1.0, ratio, 1e-9)
    }

    @Test
    fun `a frame dark at the corners reads the corners' level over the centre's`() {
        // Blocks are 48/16 = 3, rounded down to even: 2x2. The corner blocks sit at 0 and at the far edge; the centre
        // block at 31,23 rounded to even: 30,22. Corners at 100 above black, the rest at 400.
        val frame = mosaic { x, y ->
            val nearCorner = (x < 2 || x >= 62) && (y < 2 || y >= 46)
            if (nearCorner) 100 else 400
        }
        assertEquals(0.25, rawCornerToCentre(frame, 64, 48, black)!!, 1e-9)
    }

    @Test
    fun `each colour cell's own black level is subtracted`() {
        // Black differs per 2x2 position; every pixel sits exactly 300 above its own black: flat.
        val cellBlack = listOf(60f, 64f, 66f, 70f)
        val frame = ShortArray(64 * 48) { i ->
            val x = i % 64
            val y = i / 64
            (cellBlack[(y and 1) * 2 + (x and 1)] + 300).toInt().toShort()
        }
        assertEquals(1.0, rawCornerToCentre(frame, 64, 48, cellBlack)!!, 1e-9)
    }

    @Test
    fun `samples above 32767 are read unsigned, as the 16-bit sensor values they are`() {
        val frame = ShortArray(64 * 48) { 40_064.toShort() } // 40000 above black, past Short.MAX_VALUE
        assertEquals(1.0, rawCornerToCentre(frame, 64, 48, black)!!, 1e-9)
    }

    @Test
    fun `no figure when it cannot mean anything`() {
        assertNull("centre at black", rawCornerToCentre(mosaic { _, _ -> 0 }, 64, 48, black))
        assertNull("too small for a block", rawCornerToCentre(ShortArray(16), 4, 4, black))
        assertNull("sample count does not match the size", rawCornerToCentre(ShortArray(10), 64, 48, black))
        assertNull("black level not four values", rawCornerToCentre(mosaic { _, _ -> 500 }, 64, 48, listOf(64f)))
    }
}
