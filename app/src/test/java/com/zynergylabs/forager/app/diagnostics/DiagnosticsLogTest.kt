package com.zynergylabs.forager.app.diagnostics

import com.zynergylabs.forager.app.domain.CurrentTimeProvider
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * [DiagnosticsLog] is pure `java.io.File` plus [CurrentTimeProvider], same as `CrashFileStore`, so
 * this is a plain JVM test against a temp directory. What it asserts is the file's actual text —
 * the timestamp, the summary line, the indented detail — not that a file appeared.
 */
class DiagnosticsLogTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private fun newLog(maxBytes: Long = DiagnosticsLog.DEFAULT_MAX_LOG_BYTES, clock: CurrentTimeProvider = FixedClock(1_700_000_000_000L)): DiagnosticsLog =
        DiagnosticsLog(File(tempFolder.newFolder(), "diagnostics.log"), clock, maxBytes)

    @Test
    fun `append writes an ISO timestamp, the summary, and each detail line indented beneath it`() {
        val log = newLog()

        log.append("strictmode DiskReadViolation", "android.os.strictmode.DiskReadViolation\n\tat com.example.Foo.bar(Foo.kt:12)")

        assertEquals(
            "2023-11-14T22:13:20Z strictmode DiskReadViolation\n" +
                "    android.os.strictmode.DiskReadViolation\n" +
                "    \tat com.example.Foo.bar(Foo.kt:12)\n",
            log.read(),
        )
    }

    @Test
    fun `append creates the directory when it does not exist yet`() {
        val dir = tempFolder.newFolder("external").resolve("diagnostics")
        assertFalse(dir.exists())
        val log = DiagnosticsLog(File(dir, "diagnostics.log"), FixedClock(1_000L))

        log.append("sweep deleted=0 orphaned capture file(s)")

        assertTrue(dir.isDirectory)
        assertEquals("1970-01-01T00:00:01Z sweep deleted=0 orphaned capture file(s)\n", log.read())
    }

    /** The relaunch case: a new instance over the same file (a new process, in production) appends after what the previous one wrote. */
    @Test
    fun `a new instance over the same file continues it rather than truncating`() {
        val file = File(tempFolder.newFolder("diagnostics"), "diagnostics.log")
        DiagnosticsLog(file, FixedClock(1_000L)).append("process started pid=1")
        DiagnosticsLog(file, FixedClock(1_000L)).append("sweep deleted=3 orphaned capture file(s)")

        val secondProcess = DiagnosticsLog(file, FixedClock(2_000L))
        secondProcess.append("process started pid=2")
        secondProcess.append("sweep deleted=0 orphaned capture file(s)")

        assertEquals(
            listOf(
                "1970-01-01T00:00:01Z process started pid=1",
                "1970-01-01T00:00:01Z sweep deleted=3 orphaned capture file(s)",
                "1970-01-01T00:00:02Z process started pid=2",
                "1970-01-01T00:00:02Z sweep deleted=0 orphaned capture file(s)",
            ),
            secondProcess.read().lines().filter { it.isNotEmpty() },
        )
    }

    @Test
    fun `read on a log nothing has written is empty rather than an error`() {
        val log = newLog()
        assertEquals("", log.read())
        assertEquals(0L, log.sizeBytes())
    }

    /**
     * The cap is checked before each append: the append that finds the file already past it
     * rotates first and its entry is the first of the new generation. Three ~97-byte entries pass
     * a 250-byte cap without rotating (the check before the third saw 194), so the fourth rotates.
     */
    @Test
    fun `the append that finds the file past the cap rotates it to dot-one and starts the fresh file`() {
        val log = newLog(maxBytes = 250, clock = FixedClock(1_000L))
        repeat(3) { log.append("filler entry number $it, long enough that three of them pass two hundred and fifty") }
        assertFalse("under the cap at every check, nothing has rotated yet", log.rotatedFile.exists())
        assertTrue("precondition: the file is now past the cap (${log.sizeBytes()} bytes)", log.sizeBytes() > 250)

        log.append("first entry of the new generation")

        assertTrue(log.rotatedFile.exists())
        val previous = log.rotatedFile.readText()
        assertTrue("the old generation keeps all three fillers: $previous", (0..2).all { previous.contains("filler entry number $it,") })
        assertEquals("1970-01-01T00:00:01Z first entry of the new generation\n", log.read())
    }

    @Test
    fun `a second rotation replaces the previous dot-one rather than failing`() {
        val log = newLog(maxBytes = 50)
        repeat(6) { log.append("entry $it padded out to more than fifty bytes of text here") }

        assertTrue(log.rotatedFile.exists())
        assertTrue("the current file holds only what came after the latest rotation", log.sizeBytes() <= 2 * 90)
    }

    private class FixedClock(private val epochMillis: Long) : CurrentTimeProvider {
        override fun nowEpochMillis(): Long = epochMillis
    }
}
