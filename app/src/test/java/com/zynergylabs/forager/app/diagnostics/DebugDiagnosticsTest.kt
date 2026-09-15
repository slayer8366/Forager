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
 * once; the deadline is generous and a miss fails, never passes — and names `StrictMode`'s
 * per-thread state when it does, which is how the suite-order failure [StrictModeThreadStateReset]
 * clears was diagnosed rather than guessed.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class DebugDiagnosticsTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    /** See the rule's own doc: without it, this class passes alone and two of its tests time out in the full suite. */
    @get:Rule
    val strictModeState = StrictModeThreadStateReset()

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

    /**
     * The suite-order failure, reproduced on purpose and in one test so it does not depend on
     * which class Gradle happens to run first. Ten violations raised without the looper idling
     * fill `StrictMode`'s per-thread batch; the framework then drops the eleventh outright, which
     * is what left this class's own violations undelivered in the full suite. Applying the same
     * clear the rule applies at the test boundary restores delivery; the ten fillers, cleared
     * before any flush ran, never arrive, and neither does the dropped one.
     */
    @Test
    fun `a full StrictMode batch drops later violations, and the reset restores delivery`() {
        val log = newLog()
        DebugDiagnostics.install(log)

        repeat(10) { StrictMode.noteSlowCall("filler-batched-and-never-flushed") }
        assertTrue("precondition: the batch is at the framework's cap", strictModeState.batchedCount() == 10)
        StrictMode.noteSlowCall("dropped-at-the-cap")
        assertTrue("the eleventh is ignored, not queued", strictModeState.batchedCount() == 10)

        strictModeState.clear()
        assertTrue(strictModeState.batchedCount() == 0)
        StrictMode.noteSlowCall("delivered-after-reset")
        ShadowLooper.shadowMainLooper().idle()

        val text = awaitLog(log) { it.contains("delivered-after-reset") }
        assertTrue("the fillers were cleared before any flush; they must not appear:\n$text", !text.contains("filler-batched"))
        assertTrue("the one the framework dropped stays dropped:\n$text", !text.contains("dropped-at-the-cap"))
    }

    private fun awaitLog(log: DiagnosticsLog, deadlineMillis: Long = 5_000, condition: (String) -> Boolean): String {
        val deadline = System.currentTimeMillis() + deadlineMillis
        var text = log.read()
        while (!condition(text) && System.currentTimeMillis() < deadline) {
            Thread.sleep(20)
            text = log.read()
        }
        assertTrue("condition not met within ${deadlineMillis} ms; StrictMode thread state: ${strictModeThreadState()}; log was:\n$text", condition(text))
        return text
    }

    /**
     * What `StrictMode` holds for this thread in its private static `ThreadLocal`s: the list of
     * violations it is batching until the looper's next pass, and the handler it posts that pass
     * with. Read by reflection, for a failure message that names the mechanism instead of a bare
     * timeout — see the class doc.
     */
    private fun strictModeThreadState(): String = runCatching {
        val strictMode = Class.forName("android.os.StrictMode")
        val timed = strictMode.getDeclaredField("violationsBeingTimed").apply { isAccessible = true }.get(null) as ThreadLocal<*>
        val handlerLocal = strictMode.getDeclaredField("THREAD_HANDLER").apply { isAccessible = true }.get(null) as ThreadLocal<*>
        val records = timed.get() as? List<*>
        val handler = handlerLocal.get() as? android.os.Handler
        "violationsBeingTimed=${records?.size} handlerLooper=${handler?.looper} myLooper=${android.os.Looper.myLooper()} sameLooper=${handler?.looper === android.os.Looper.myLooper()}"
    }.getOrElse { "unreadable: $it" }
}
