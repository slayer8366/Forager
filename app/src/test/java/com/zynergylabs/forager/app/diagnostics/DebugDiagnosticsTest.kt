package com.zynergylabs.forager.app.diagnostics

import android.os.StrictMode
import java.io.File
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
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

    /**
     * The capture-orientation dispatch: the `Shot:` values reach the store as one entry, with the
     * values themselves rather than a pre-formatted string handed in — which is what lets the
     * release twin take the same call and build nothing.
     */
    @Test
    fun `recordCaptureShot writes the shot's four values as one entry`() {
        val log = newLog()
        val diagnostics = DebugDiagnostics.install(log)

        diagnostics.recordCaptureShot(deviceRotation = 3, targetRotation = 0, requestDegrees = 90, resolution = "4032x3024")

        val text = awaitLog(log) { it.contains("capture shot") }
        assertTrue(
            "expected all four values on the shot entry, got:\n$text",
            text.contains("capture shot deviceRotation=3 targetRotation=0 requestDegrees=90 resolution=4032x3024"),
        )
    }

    /**
     * A decline carries its own words. This is half of the distinction the instrument exists for:
     * a HAL that rotated the pixels in memory declines and is *correct*, so the reason is the part
     * that makes the entry readable rather than alarming.
     */
    @Test
    fun `a declined reapply records its reason and the tag the file carries`() {
        val log = newLog()
        val diagnostics = DebugDiagnostics.install(log)

        diagnostics.recordCaptureOrientation(
            fileName = "IMG_0001.jpg",
            branch = "declined",
            fromTag = 6,
            degrees = 90,
            reason = "the HAL rotated the pixels",
        )

        val text = awaitLog(log) { it.contains("capture orientation") }
        assertTrue("expected the file and branch, got:\n$text", text.contains("capture orientation 'IMG_0001.jpg' declined"))
        assertTrue("expected the decline's own reason, got:\n$text", text.contains("reason=the HAL rotated the pixels"))
        assertTrue("expected the tag the file carries, got:\n$text", text.contains("fromTag=6"))
    }

    /**
     * The other half: a write that failed is not a decline, and its throwable goes in as the entry's
     * detail — the same shape a StrictMode entry uses for its stack, so the panel renders it the same.
     */
    @Test
    fun `a failed reapply records the throwable and its stack as the entry's detail`() {
        val log = newLog()
        val diagnostics = DebugDiagnostics.install(log)

        diagnostics.recordCaptureOrientation(
            fileName = "IMG_0002.jpg",
            branch = "failed",
            degrees = 270,
            error = java.io.IOException("permission denied writing the tag"),
        )

        val text = awaitLog(log) { it.contains("capture orientation 'IMG_0002.jpg' failed") }
        assertTrue("expected the throwable on the summary, got:\n$text", text.contains("permission denied writing the tag"))
        assertTrue("expected the stack indented beneath it, got:\n$text", text.contains("    java.io.IOException"))
    }

    /**
     * The paths that attempt no reapply — a failed capture, and a shot with no resolution info —
     * record an entry of their own, which is what makes two entries per capture possible.
     *
     * **This does not verify the invariant.** It exercises the recorder, not the call sites: the two
     * calls below are this test's, not `capture()`'s. Whether every path through `capture()` actually
     * makes both calls is the wiring, which no test here reaches — a bound camera is device-only, and
     * the owner accepted that cost when choosing this seam over a constructor parameter. The count
     * assertion below says one call writes one entry, nothing more.
     */
    @Test
    fun `a path that attempts no reapply records an entry of its own`() {
        val log = newLog()
        val diagnostics = DebugDiagnostics.install(log)

        diagnostics.recordCaptureShot(deviceRotation = 0, targetRotation = 0, requestDegrees = null, resolution = null)
        diagnostics.recordCaptureOrientation(
            fileName = "IMG_0003.jpg",
            branch = "not attempted",
            reason = "no resolution info for this shot; it keeps the HAL's tag",
        )

        val text = awaitLog(log) { it.contains("capture orientation 'IMG_0003.jpg' not attempted") }
        val captureEntries = text.lines().count { it.contains("capture shot") || it.contains("capture orientation") }
        assertEquals("expected one entry per call, got:\n$text", 2, captureEntries)
        assertTrue("expected the reason the reapply was not attempted, got:\n$text", text.contains("no resolution info for this shot"))
    }

    /**
     * The AVD crash (2026-09-17): a write that cannot open the file threw out of the worker and
     * killed the process. Reproduced with a directory where the file should be — every open for
     * append then fails with the same `FileNotFoundException` the device's EACCES raised, without
     * depending on file permissions the test's own user could override. The executor is a real
     * single-worker pool, as in production, whose thread records anything that escapes a task; a
     * throw out of a task is exactly what reaches a thread's uncaught-exception handler, and on the
     * device that handler ended the process.
     */
    @Test
    fun `a log write that fails stays on the worker and is recorded, not thrown`() {
        val log = DiagnosticsLog(tempFolder.newFolder("diagnostics", "diagnostics.log"))
        val escaped = CopyOnWriteArrayList<Throwable>()
        val executor = Executors.newSingleThreadExecutor { runnable ->
            Thread(runnable, "diagnostics-test-worker").apply { setUncaughtExceptionHandler { _, error -> escaped += error } }
        }

        val diagnostics = DebugDiagnostics.install(log, executor) // the process-start line: the write in the device's stack
        diagnostics.recordSweep(0)
        executor.shutdown()
        assertTrue("the diagnostics worker did not finish", executor.awaitTermination(5, TimeUnit.SECONDS))

        assertEquals("expected no exception to escape the worker, got: $escaped", emptyList<Throwable>(), escaped.toList())
        val failure = log.writeFailure
        assertEquals("expected both lost entries counted, got: $failure", 2, failure?.failedEntries)
        assertTrue("expected the write's own error, got: $failure", failure!!.lastError.contains("Is a directory"))
    }

    /** The other half of observability: a log that is writing reports no failure, so the panel's line cannot be permanent noise. */
    @Test
    fun `a log whose writes land reports no failure`() {
        val log = newLog()
        DebugDiagnostics.install(log).recordSweep(0)

        awaitLog(log) { it.contains("sweep deleted=0") }
        assertEquals(null, log.writeFailure)
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
