package com.zynergylabs.forager.app.diagnostics.walklog

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The walk log's file (dispatch 2026-09-28-532, verify item 5 and RECORD -559 choice 4): it stops
 * below 200 MB free and says so in the file; what was written reaches the disk within the flush
 * interval, so a process killed mid-walk loses seconds, not the walk.
 *
 * Under Robolectric only because the code logs its stops through `android.util.Log`, which a plain
 * JVM test here cannot call.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class WalkLogWriterTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private var free = 10L * 1024 * 1024 * 1024
    private var now = 0L

    private fun newWriter(file: File = File(tempFolder.root, "walklog-test.txt")) =
        WalkLogWriter(file, freeBytes = { free }, nowNanos = { now })

    @Test
    fun `the floor is 200 MB`() {
        assertEquals(209_715_200L, WalkLogWriter.MIN_FREE_BYTES)
    }

    @Test
    fun `lines written are in the file once it is closed, in order, followed by the end line`() {
        val writer = newWriter()
        assertTrue("opens with 10 GB free", writer.open())

        assertTrue(writer.write(listOf("1 A", "2 B")))
        assertTrue(writer.write(listOf("3 C")))
        writer.close("4 END reason=recording-stopped")

        assertEquals(listOf("1 A", "2 B", "3 C", "4 END reason=recording-stopped"), writer.file.readLines())
        assertFalse(writer.isOpen)
        assertNull("a normal end is not a stop", writer.stoppedReason)
    }

    @Test
    fun `what was written is on disk within five seconds without a close, so a killed process loses seconds`() {
        val writer = newWriter()
        writer.open()

        writer.write(listOf("0 FIRST"))
        now = 6_000_000_000L
        writer.write(listOf("6000000000 SECOND"))

        // Read without closing: what a killed process would have left behind.
        val onDisk = writer.file.readLines()
        assertTrue("expected both lines on disk without a close, got $onDisk", onDisk.containsAll(listOf("0 FIRST", "6000000000 SECOND")))
        writer.close("END")
    }

    @Test
    fun `below 200 MB at the start, nothing is logged but the file says why`() {
        free = WalkLogWriter.MIN_FREE_BYTES - 1
        now = 77L
        val writer = newWriter()

        assertFalse("must not open below the floor", writer.open())

        assertFalse(writer.isOpen)
        assertEquals("storage-low", writer.stoppedReason)
        assertEquals(listOf("77 STOPPED reason=storage-low free=209715199 min=209715200"), writer.file.readLines())
        assertFalse("a write after the stop is refused", writer.write(listOf("78 LATE")))
        assertEquals(1, writer.file.readLines().size)
    }

    @Test
    fun `falling below 200 MB mid-walk stops the log with a line saying so, and nothing after it`() {
        val writer = newWriter()
        writer.open()
        writer.write(listOf("1 BEFORE"))
        assertTrue("plenty of room still", writer.checkSpace())

        free = 1_000L
        now = 60_000_000_000L
        assertFalse("below the floor now", writer.checkSpace())

        assertFalse(writer.isOpen)
        assertEquals("storage-low", writer.stoppedReason)
        assertFalse(writer.write(listOf("60000000001 AFTER")))
        writer.close("60000000002 END reason=recording-stopped")
        assertEquals(
            "the stop line is the last line; a later close adds no end line to a stopped log",
            listOf("1 BEFORE", "60000000000 STOPPED reason=storage-low free=1000 min=209715200"),
            writer.file.readLines(),
        )
    }

    @Test
    fun `each recording gets its own file, so opening never truncates an existing one`() {
        val file = File(tempFolder.root, "walklog-existing.txt").apply { writeText("0 OLD\n") }
        val writer = newWriter(file)

        writer.open()
        writer.write(listOf("1 NEW"))
        writer.close("2 END")

        assertEquals(listOf("0 OLD", "1 NEW", "2 END"), file.readLines())
    }
}
