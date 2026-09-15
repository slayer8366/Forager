package com.zynergylabs.forager.app.diagnostics

import android.os.StrictMode
import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLooper

/**
 * [DebugDiagnostics] against the real `StrictMode`, under Robolectric. Robolectric does not hook
 * disk or network calls into `BlockGuard`, so a real `File` read on the test thread fires nothing
 * here; what it does carry is `StrictMode` itself, and `StrictMode.noteSlowCall` is the framework's
 * own explicit way to raise a violation under `detectCustomSlowCalls`, which the installed policy
 * includes. So the assertion is that a violation raised on the thread under the policy reaches the
 * log through the real listener, the real executor and the real file — with its stack. Whether a
 * *disk* read raises one is the platform's behaviour and is device-only.
 *
 * The writes happen on the diagnostics worker, so each test polls the file rather than reading it
 * once; the deadline is generous and a miss fails, never passes.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class DebugDiagnosticsTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private fun newLog(): DiagnosticsLog = DiagnosticsLog(File(tempFolder.newFolder("diagnostics"), "diagnostics.log"))

    @Test
    fun `install writes a process-start line, and recordSweep the count with it`() {
        val log = newLog()
        val diagnostics = DebugDiagnostics.install(log)

        diagnostics.recordSweep(3)

        val text = awaitLog(log) { it.contains("sweep deleted=3 orphaned capture file(s)") }
        assertTrue("expected the process-start line first, got:\n$text", text.lines().first().contains("process started pid="))
    }

    @Test
    fun `a StrictMode violation on the policy's thread reaches the log with its stack`() {
        val log = newLog()
        DebugDiagnostics.install(log)

        StrictMode.noteSlowCall("diagnostics-test-slow-call")
        ShadowLooper.shadowMainLooper().idle()

        val text = awaitLog(log) { it.contains("strictmode CustomViolation") }
        assertTrue("expected the violation's own message in the stack, got:\n$text", text.contains("diagnostics-test-slow-call"))
        assertTrue("expected this test's frame in the stack, got:\n$text", text.contains(DebugDiagnosticsTest::class.java.simpleName))
    }

    @Test
    fun `a repeat of the same stack is one line with a count, not a second stack`() {
        val log = newLog()
        DebugDiagnostics.install(log)

        repeat(2) { StrictMode.noteSlowCall("diagnostics-test-repeat") } // one call site, one stack
        ShadowLooper.shadowMainLooper().idle()

        val text = awaitLog(log) { it.contains("again (x2") }
        val fullStacks = text.lines().count { it.contains("android.os.strictmode.CustomViolation: diagnostics-test-repeat") }
        assertTrue("expected exactly one full stack for two identical violations, got $fullStacks in:\n$text", fullStacks == 1)
    }

    private fun awaitLog(log: DiagnosticsLog, deadlineMillis: Long = 5_000, condition: (String) -> Boolean): String {
        val deadline = System.currentTimeMillis() + deadlineMillis
        var text = log.read()
        while (!condition(text) && System.currentTimeMillis() < deadline) {
            Thread.sleep(20)
            text = log.read()
        }
        assertTrue("condition not met within ${deadlineMillis} ms; log was:\n$text", condition(text))
        return text
    }
}
