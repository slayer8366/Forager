package com.zynergylabs.forager.app.diagnostics

import android.content.Context
import android.util.Log
import com.zynergylabs.forager.app.domain.CurrentTimeProvider
import com.zynergylabs.forager.app.domain.SystemCurrentTimeProvider
import java.io.File
import java.time.Instant
import java.time.format.DateTimeFormatter

/**
 * An append-only, timestamped plain-text log for the things the device check needs to read that
 * logcat would otherwise be the only home for: StrictMode violations, the startup sweep's count.
 * **Debug builds only** — this file lives in `src/debug`, so a release build has no such class.
 *
 * ## Where it lives, and why not in [com.zynergylabs.forager.app.crash.CrashFileStore]'s directory
 *
 * A sibling directory, `diagnostics/`, beside `crashes/` under the same app-external root, for the
 * same reason that root was chosen for crashes: the device's own file manager can reach it with
 * no root and no ADB, and this dispatch exists because the owner has neither. Not *inside*
 * `crashes/`, because that directory has a contract — one file per crash, pruned to the ten newest
 * on every write, `list()` recognising only its own filename shape — and an append-only log that
 * grows across launches is a different lifecycle. It would be tolerated there (`list()` ignores a
 * stray) but it would be a stray, and the prune rule would say nothing about it. Its own class, so
 * that its own rule (rotate, below) is written down beside it rather than implied.
 *
 * ## Surviving a force stop and a relaunch
 *
 * Step 4 of the device check measures exactly that: the count the sweep reports on the relaunch
 * after a force stop. Nothing here truncates on open — [append] opens the file for append every
 * time and never holds it — so a new process continues the same file, and the "process started"
 * line [DebugDiagnostics] writes is what separates one launch from the next when reading it.
 *
 * ## Rotation, not pruning
 *
 * A StrictMode listener under a debug policy can fire on framework code the app never wrote, at
 * every launch, and each entry carries a stack. Uncapped, the file would grow until the device
 * ran out of space. When it passes [maxBytes] the file is renamed to `.1` (replacing any earlier
 * `.1`) and a fresh one started, so at most two files of roughly [maxBytes] exist and the newest
 * entries are always in the one the panel shows. Chosen over a head-truncate because rename is
 * atomic and a truncate would need the whole file read and rewritten on every append past the cap.
 *
 * Pure `java.io.File` plus [CurrentTimeProvider], same shape as `CrashFileStore`, so it is tested
 * as a plain JVM class against a temp directory.
 */
class DiagnosticsLog(
    val file: File,
    private val currentTime: CurrentTimeProvider = SystemCurrentTimeProvider,
    private val maxBytes: Long = DEFAULT_MAX_LOG_BYTES,
) {

    /** The previous generation after a rotation; absent until the first one. */
    val rotatedFile: File get() = File(file.parentFile, "${file.name}.1")

    /**
     * Entries this instance could not write, or `null` while every write has landed. What lets a
     * reader of the panel tell "the log stopped recording" from "nothing happened": a log that
     * silently stopped growing reads exactly like a quiet run. See [append] for why a failure is
     * held here and not thrown.
     *
     * In memory, on this instance, and never cleared by a later successful write: entries lost
     * during the failure stay lost, and a log that resumed has a gap the file itself does not
     * mark. Per process, because the instance is — a relaunch starts with none until its own
     * first failed write. The panel reads it from the writer's own instance
     * (`DebugDiagnostics.log`), not from one it constructs, which would always read `null`.
     */
    @Volatile
    var writeFailure: WriteFailure? = null
        private set

    /**
     * Writes one entry: an ISO-8601 UTC timestamp, a space, [summary] on the same line, then each
     * line of [detail] (a stack trace, typically) indented beneath it. Synchronized because the
     * sweep, the StrictMode listener and the process-start line can arrive from different threads,
     * and an interleaved stack is unreadable. Never called on the main thread by this app's own
     * code — a disk write there would be the very violation the log records.
     *
     * **A write that fails is recorded in [writeFailure], never thrown** (diagnostics-write-failure
     * dispatch, 2026-09-17). Every caller runs this on `DebugDiagnostics`' worker, where a throw is
     * an uncaught exception and ends the process: on an AVD, after an uninstall and reinstall, a
     * `diagnostics.log` left behind under the previous install's uid made every open fail with
     * EACCES, and the process-start line killed the app. An instrument must not take down what it
     * instruments. Swallowing is not the alternative — a runner would read an incomplete log as a
     * complete one — so the failure goes to [writeFailure], which the panel shows, and the first
     * one per instance to logcat. The whole body is covered, [rotate] included, because every
     * caller shares the one worker. Every later call still tries the write, so a condition that
     * clears is recovered from without a relaunch. Surfacing never writes to this log, so a
     * failure cannot recurse into another one.
     */
    @Synchronized
    fun append(summary: String, detail: String? = null) {
        try {
            file.parentFile?.mkdirs()
            if (file.length() > maxBytes) rotate()
            file.appendText(
                buildString {
                    append(TIMESTAMP_FORMAT.format(Instant.ofEpochMilli(currentTime.nowEpochMillis())))
                    append(' ')
                    appendLine(summary)
                    detail?.lineSequence()?.forEach { line -> append("    ").appendLine(line) }
                },
            )
        } catch (error: Exception) {
            recordWriteFailure(error)
        }
    }

    private fun recordWriteFailure(error: Exception) {
        val previous = writeFailure
        writeFailure = WriteFailure(
            firstFailedAtEpochMillis = previous?.firstFailedAtEpochMillis ?: currentTime.nowEpochMillis(),
            failedEntries = (previous?.failedEntries ?: 0) + 1,
            lastError = error.toString(),
        )
        if (previous == null) {
            Log.w(TAG, "Couldn't write to '${file.path}'; entries are not being recorded. The Diagnostics panel shows the count.", error)
        }
    }

    /** The current file's whole text, or empty when nothing has been written yet. Disk I/O; callers keep it off main. */
    @Synchronized
    fun read(): String = if (file.exists()) file.readText() else ""

    /** The current file's size in bytes, zero when absent. */
    fun sizeBytes(): Long = file.length()

    private fun rotate() {
        rotatedFile.delete()
        if (!file.renameTo(rotatedFile)) {
            // Reported, never silent (CLAUDE.md): the cap failing to hold is a fact the reader of
            // this log should see in the log.
            Log.w(TAG, "Couldn't rotate '${file.name}'; it will keep growing past ${maxBytes} bytes.")
        }
    }

    /** See [writeFailure]. [lastError] is the most recent throwable's `toString()`: the message a runner needs, without a stack the panel has no room for. */
    data class WriteFailure(val firstFailedAtEpochMillis: Long, val failedEntries: Int, val lastError: String)

    companion object {
        /**
         * The one diagnostics file this app ever writes, under the same app-external root as
         * `CrashFileStore.forContext` with the same `filesDir` fallback — see the class doc for
         * why a sibling of `crashes/`. The single source of truth for that path: `DebugDiagnostics`
         * (the writer) and the Settings panel (the reader) both construct through this.
         */
        fun forContext(context: Context): DiagnosticsLog =
            DiagnosticsLog(File(File(context.getExternalFilesDir(null) ?: context.filesDir, DIRECTORY_NAME), FILE_NAME))

        const val DIRECTORY_NAME = "diagnostics"
        const val FILE_NAME = "diagnostics.log"

        /** Roughly 300 full-stack StrictMode entries, or many launches' worth of one-line ones, per generation. */
        const val DEFAULT_MAX_LOG_BYTES = 1L shl 20

        private val TIMESTAMP_FORMAT: DateTimeFormatter = DateTimeFormatter.ISO_INSTANT
        private const val TAG = "DiagnosticsLog"
    }
}
