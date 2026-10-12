package com.zynergylabs.forager.app.data.diagnostics

import androidx.test.core.app.ApplicationProvider
import com.zynergylabs.forager.app.ForagerApplication
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Debug builds keep the three alert record files (dispatch 2026-10-11, RECORD -830: "debug builds keep
 * both, so device checks still work"). [AlertRecordScenario] through the real container writes one
 * line to each, with the words the device checks read. This is also the release half's control:
 * `AlertRecordFilesReleaseTest`'s "nothing written" means something only because this same scenario
 * does write, here.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AlertRecordFilesDebugTest {

    @Test
    fun `a debug build writes each alert record to its file in app storage`() {
        val app = ApplicationProvider.getApplicationContext<ForagerApplication>()
        AlertRecordScenario.run(app)

        val expected = mapOf(
            "return-record.log" to "return-started track=${AlertRecordScenario.TRACK_ID}",
            "back-by-record.log" to "alarm-delivered nothing-set",
            "sundown-record.log" to "alarm-delivered nothing-watched",
        )
        for ((name, words) in expected) {
            val file = File(app.filesDir, name)
            assertTrue("a debug build wrote no files/$name", file.exists())
            val lines = file.readLines()
            assertEquals("files/$name: $lines", 1, lines.size)
            assertTrue("files/$name: '${lines.single()}' does not end with '$words'", lines.single().endsWith("Z $words"))
        }
        assertEquals(
            "the three record files and no others",
            expected.keys,
            AlertRecordScenario.recordFilesUnder(app.filesDir).map { it.name }.toSet(),
        )
    }
}
