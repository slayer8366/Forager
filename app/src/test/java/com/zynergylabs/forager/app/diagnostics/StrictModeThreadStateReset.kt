package com.zynergylabs.forager.app.diagnostics

import org.junit.rules.ExternalResource

/**
 * Clears `StrictMode`'s per-thread batching state before and after each test. Any test that
 * asserts a violation reaches the listener needs this rule, or it passes alone and fails in the
 * full suite.
 *
 * **`StrictMode` batches a thread's violations in a static `ThreadLocal<ArrayList>` until the
 * thread's looper completes its current pass, and ignores every further violation once that list
 * holds `MAX_OFFENSES_PER_LOOP` (ten).** On a device the main looper runs the flush at the end of
 * every pass, so the list never stays full. Under Robolectric every test runs on the same JVM
 * thread, the Application under test installs the diagnostics policy on it in every test, the
 * Room tests then read disk on it — ten violations, batched — and the flush posted to the looper
 * is dropped with the looper's queue at the test boundary. The list stays at ten for the rest of
 * the JVM, and a later test's violation is "not worth measuring" and never reaches any listener.
 * The failure message that found this printed `violationsBeingTimed=10` with the same looper on
 * both sides; without that read it was a bare 5-second timeout that passed when the class ran
 * alone (2026-09-15).
 *
 * Same family as [com.zynergylabs.forager.app.photo.FileProviderCacheReset]: process-wide state that is
 * invisible until a second test meets it. Field names were confirmed against the Robolectric
 * framework jar by `javap`, not assumed, and the reset fails loudly if either is missing rather than
 * silently doing nothing — a reset that quietly stops resetting is how this returns as a flake.
 */
class StrictModeThreadStateReset : ExternalResource() {
    override fun before() = clear()
    override fun after() = clear()

    /** Public so `DebugDiagnosticsTest` can apply it mid-test to a batch it filled on purpose and show delivery come back. */
    fun clear() {
        val strictMode = Class.forName("android.os.StrictMode")
        (batchedViolations().get() as? MutableList<*>)?.clear()
        // The handler is bound to the looper it was first created on; dropping it lets the next
        // violation post its flush on this test's looper rather than a previous test's.
        threadLocal(strictMode, "THREAD_HANDLER").remove()
    }

    /** How many violations `StrictMode` is holding for this thread right now; what the diagnosing failure message read. */
    fun batchedCount(): Int = (batchedViolations().get() as? List<*>)?.size ?: 0

    private fun batchedViolations(): ThreadLocal<*> = threadLocal(Class.forName("android.os.StrictMode"), "violationsBeingTimed")

    private fun threadLocal(owner: Class<*>, name: String): ThreadLocal<*> {
        val field = runCatching { owner.getDeclaredField(name) }.getOrElse {
            throw AssertionError(
                "android.os.StrictMode no longer has a static `$name` field. Its per-thread violation " +
                    "batching is what makes listener tests fail in a suite run but pass alone; find " +
                    "where it lives now rather than deleting this.",
                it,
            )
        }
        field.isAccessible = true
        return field.get(null) as ThreadLocal<*>
    }
}
