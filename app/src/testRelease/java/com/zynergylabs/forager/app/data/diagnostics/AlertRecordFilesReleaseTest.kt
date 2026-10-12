package com.zynergylabs.forager.app.data.diagnostics

import androidx.test.core.app.ApplicationProvider
import com.zynergylabs.forager.app.ForagerApplication
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Release builds write no alert record files (dispatch 2026-10-11; the owner, RECORD -830: "The
 * background alert record files"). The same [AlertRecordScenario] that writes a line to each file in
 * a debug build (`AlertRecordFilesDebugTest`) leaves app storage with none, and the file writers are
 * not in the release build at all: a build-type source set, not a branch, as with the Diagnostics
 * panel.
 *
 * The diagnostics log is checked too. It was already debug-only before this dispatch (its writer is
 * in `src/debug`, the release `DebugDiagnostics` is a no-op), so that assertion is a guard, not
 * evidence of this change: it passes on the base as well.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AlertRecordFilesReleaseTest {

    @Test
    fun `a release build writes no alert record file`() {
        val app = ApplicationProvider.getApplicationContext<ForagerApplication>()
        AlertRecordScenario.run(app)

        val written = AlertRecordScenario.recordFilesUnder(app.filesDir)
        if (written.isNotEmpty()) {
            fail("a release build wrote " + written.joinToString { "files/${it.name} (${it.readLines()})" })
        }
    }

    @Test
    fun `the alert record file writers are not in a release build`() {
        val present = listOf("FileReturnRecord", "FileBackByRecord", "FileSundownRecord")
            .map { "com.zynergylabs.forager.app.data.diagnostics.$it" }
            .filter { name -> runCatching { Class.forName(name) }.isSuccess }
        assertEquals("release classes that write alert record files", emptyList<String>(), present)
    }

    @Test
    fun `a release build writes no diagnostics log`() {
        val app = ApplicationProvider.getApplicationContext<ForagerApplication>()
        AlertRecordScenario.run(app)

        val roots = listOfNotNull(app.filesDir, app.getExternalFilesDir(null))
        val logs = roots.flatMap { root -> root.walkTopDown().filter { it.isFile && it.name == "diagnostics.log" }.toList() }
        assertEquals("diagnostics.log files in a release build", emptyList<File>(), logs)
    }
}
