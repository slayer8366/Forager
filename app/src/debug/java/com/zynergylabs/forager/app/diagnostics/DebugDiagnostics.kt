package com.zynergylabs.forager.app.diagnostics

import android.content.Context
import android.os.Build
import android.os.Process
import android.os.StrictMode
import android.os.strictmode.Violation
import com.zynergylabs.forager.app.BuildConfig
import java.util.concurrent.Executor
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

/**
 * The debug build's observation surface: installs a StrictMode thread policy whose penalty is
 * writing each violation, with its stack, into [DiagnosticsLog], and records the startup sweep's
 * count there too. Observation only — nothing here changes what the app does, and the release
 * twin of this class in `src/release` is a no-op with the same two entry points, which is how
 * `ForagerApplication` can call it unconditionally and a release build still carries none of it.
 *
 * ## StrictMode: there was no policy to change
 *
 * The device check assumed a debug thread policy with `penaltyLog` existed and asked for its
 * penalty to become a listener. **It did not exist.** `grep StrictMode` over `app/src/main` on
 * 2026-09-15 found two comments and no call: `CrashUncaughtExceptionHandler`'s doc listing "no
 * StrictMode" among the things the app had none of, and a note in `FilePhotoStore` deferring the
 * main-thread question to "a StrictMode run" on the device check. On a retail (user) build the
 * platform installs no default thread policy for a debuggable app either
 * (`StrictMode.conditionallyEnableDebugLogging` returns early on user builds), so until this class
 * the checklist's step 1 had nothing to observe, with or without logcat. This is the policy, new:
 * disk reads, disk writes, network and custom slow calls on the thread it is installed on, with
 * `penaltyLog` kept (it costs a logcat line) and `penaltyListener` doing the recording.
 *
 * **The thread it is installed on is the main thread**, because `Application.onCreate` runs
 * there — the same fact the sweep dispatch had to be corrected on. A thread policy is per-thread,
 * so a violation here means "on main"; work on `Dispatchers.IO` carries no policy and cannot
 * appear. That is exactly the question step 1 asks of `FilePhotoStore.persist`.
 *
 * `penaltyListener` is API 28+. `minSdk` is 26, so on 26 and 27 the listener cannot be installed
 * and the log says so explicitly rather than staying silent (CLAUDE.md, unsupported is stated).
 *
 * ## Repeats
 *
 * A framework path that reads disk on main at startup fires on every launch and often many times
 * per launch, each with the same stack. The first occurrence per process is written in full; a
 * repeat is one line naming the count, so the log stays readable on a phone. Keyed on the full
 * stack text, per process — a fresh process starts the tally over, which is also what makes each
 * launch's first occurrence carry its stack again.
 *
 * ## The executor
 *
 * The listener's writes are disk I/O and must not happen on the thread that is under the policy.
 * One worker, so entries land in order and the repeat tally needs no locking; core size zero with
 * a short keep-alive, so the thread exists only while there is something to write. That last part
 * matters under Robolectric, where `ForagerApplication` is instantiated for every test and a
 * long-lived thread per install would accumulate across a 1,400-test run.
 */
class DebugDiagnostics private constructor(
    private val log: DiagnosticsLog,
    private val executor: Executor,
) {

    /** Touched only on [executor]'s single worker — see the class doc. */
    private val seenStacks = HashMap<String, Int>()

    /** The startup sweep's count, with the timestamp [DiagnosticsLog.append] stamps. Recorded even when zero: zero is the expected reading after the leak fix. */
    fun recordSweep(deleted: Int) {
        executor.execute { log.append("sweep deleted=$deleted orphaned capture file(s)") }
    }

    /**
     * A capture came back from the in-app camera for a find that is no longer being edited, so it
     * was persisted to the album instead of attached. Recorded **whether or not the album save
     * succeeded**, because the save is the user's remedy and not evidence that nothing went wrong:
     * the app reached a state it should not have, and that is the fact this entry exists to carry.
     *
     * Readable on a phone with no logcat, which is the whole reason this store exists. The user's
     * half is a Toast at the moment it happens; this is the developer's half, hours later.
     */
    fun recordCaptureWithoutEditingEntry(photoId: String?, error: Throwable? = null) {
        executor.execute {
            val outcome = if (error == null) "saved to the album instead (photo=$photoId)" else "and the album save failed too"
            log.append("capture arrived with no editing entry; $outcome", error?.stackTraceToString())
        }
    }

    /**
     * One entry per shot, written from [CameraXCaptureSession.capture] alongside its `Log.i` line,
     * never instead of it: logcat still works for anyone who has it, and this is for the device
     * check reading the panel with no cable.
     *
     * Values, not a formatted string. The release twin takes the same arguments and does nothing
     * with them, so a release build formats nothing — every argument here already exists at the
     * call site, and none of them is an interpolation.
     *
     * [displayRotation] sits beside [deviceRotation] on purpose (2026-09-18): the glyphs turn by
     * sensor minus display and the shutter's edge comes from display alone, so the pair is what a
     * reader compares. At open they agree; an entry where they differ is the finding, not a quirk.
     */
    fun recordCaptureShot(deviceRotation: Int?, displayRotation: Int?, targetRotation: Int, requestDegrees: Int?, resolution: String?) {
        executor.execute {
            log.append(
                "capture shot deviceRotation=$deviceRotation displayRotation=$displayRotation targetRotation=$targetRotation " +
                    "requestDegrees=$requestDegrees resolution=$resolution",
            )
        }
    }

    /**
     * The second of every capture's two entries: which branch the orientation-tag reapply took, and
     * the value that distinguishes it. Every path that follows a shot records one, including the
     * two that attempt no reapply at all — a shot with a `Shot:` entry and no outcome entry would
     * read as a dropped record rather than as the distinct path it is, and the panel gives a reader
     * no way to tell those apart. Two entries per capture is therefore an invariant, and an odd
     * count in the panel is itself a signal.
     *
     * [reason] carries a decline's own words and [error] a failure's throwable, because those two
     * are exactly what this instrument exists to separate: a HAL that rotated the pixels in memory
     * is a decline and is correct, while a file that could not be written is a failure and is not.
     * A failure writes the stack as the entry's detail, the same shape as a StrictMode entry.
     */
    fun recordCaptureOrientation(
        fileName: String,
        branch: String,
        fromTag: Int? = null,
        toTag: Int? = null,
        degrees: Int? = null,
        reason: String? = null,
        error: Throwable? = null,
    ) {
        executor.execute {
            val detail = buildString {
                if (fromTag != null) append(" fromTag=").append(fromTag)
                if (toTag != null) append(" toTag=").append(toTag)
                if (degrees != null) append(" degrees=").append(degrees)
                if (reason != null) append(" reason=").append(reason)
                if (error != null) append(" error=").append(error)
            }
            log.append("capture orientation '$fileName' $branch$detail", error?.stackTraceToString())
        }
    }

    private fun installStrictMode() {
        val builder = StrictMode.ThreadPolicy.Builder()
            .detectDiskReads()
            .detectDiskWrites()
            .detectNetwork()
            .detectCustomSlowCalls()
            .penaltyLog()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            builder.penaltyListener(executor) { violation -> recordViolation(violation) }
        } else {
            executor.execute {
                log.append(
                    "strictmode listener unsupported below API 28 (this device is API ${Build.VERSION.SDK_INT}); " +
                        "violations reach logcat only",
                )
            }
        }
        StrictMode.setThreadPolicy(builder.build())
    }

    /** On [executor]. [Violation] is a `Throwable`, so its stack is the offending call's. */
    private fun recordViolation(violation: Violation) {
        val kind = violation.javaClass.simpleName
        val stack = violation.stackTraceToString()
        val count = (seenStacks[stack] ?: 0) + 1
        seenStacks[stack] = count
        if (count == 1) {
            log.append("strictmode $kind", stack)
        } else {
            log.append("strictmode $kind again (x$count this process, same stack as its first entry above)")
        }
    }

    companion object {
        /** The production entry point: the log at [DiagnosticsLog.forContext], the worker described on the class. */
        fun install(context: Context): DebugDiagnostics = install(DiagnosticsLog.forContext(context), newExecutor())

        /**
         * Installs the policy on the calling thread and writes the process-start line. Exposed at
         * this granularity so a test can hand in a log on a temp directory; the executor is the
         * real one either way, because a direct executor would write to disk on the thread under
         * the policy — on a device, a violation inside the violation handler.
         */
        internal fun install(log: DiagnosticsLog, executor: Executor = newExecutor()): DebugDiagnostics {
            val diagnostics = DebugDiagnostics(log, executor)
            diagnostics.installStrictMode()
            executor.execute {
                log.append(
                    "process started pid=${Process.myPid()} api=${Build.VERSION.SDK_INT} " +
                        "build=${BuildConfig.VERSION_CODE}/${BuildConfig.VERSION_NAME}",
                )
            }
            return diagnostics
        }

        private fun newExecutor(): Executor =
            ThreadPoolExecutor(0, 1, WORKER_KEEP_ALIVE_SECONDS, TimeUnit.SECONDS, LinkedBlockingQueue()) { runnable ->
                Thread(runnable, "forager-diagnostics").apply { isDaemon = true }
            }

        private const val WORKER_KEEP_ALIVE_SECONDS = 5L
    }
}
