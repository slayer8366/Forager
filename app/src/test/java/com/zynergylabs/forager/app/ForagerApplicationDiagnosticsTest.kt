package com.zynergylabs.forager.app

import androidx.test.core.app.ApplicationProvider
import com.zynergylabs.forager.app.diagnostics.DiagnosticsLog
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The debug build's diagnostics through their real entry point: Robolectric instantiates
 * [ForagerApplication] from the manifest and runs its `onCreate`, which installs
 * `DebugDiagnostics` and launches the capture sweep, so by the time this test body runs the
 * production log at `DiagnosticsLog.forContext` should carry both lines. The sweep runs on
 * `Dispatchers.IO` and the log's writes on the diagnostics worker, so the read polls.
 *
 * This is the test a revert of `ForagerApplication`'s `diagnostics.recordSweep(deleted)` line
 * fails; `DebugDiagnosticsTest` covers `recordSweep` itself but not that startup calls it.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ForagerApplicationDiagnosticsTest {

    @Test
    fun `startup writes the process-start line and the sweep's count to the production diagnostics log`() {
        val app = ApplicationProvider.getApplicationContext<ForagerApplication>()
        val log = DiagnosticsLog.forContext(app)

        val deadline = System.currentTimeMillis() + 5_000
        while (!log.read().contains("sweep deleted=") && System.currentTimeMillis() < deadline) Thread.sleep(20)
        val lines = log.read().lines()

        assertTrue("expected a process-start line, got:\n${lines.joinToString("\n")}", lines.any { it.matches(Regex(""".* process started pid=\d+ api=36 build=\d+/.*""")) })
        assertTrue("expected the sweep's count, got:\n${lines.joinToString("\n")}", lines.any { it.matches(Regex(""".* sweep deleted=\d+ orphaned capture file\(s\)""")) })
    }
}
