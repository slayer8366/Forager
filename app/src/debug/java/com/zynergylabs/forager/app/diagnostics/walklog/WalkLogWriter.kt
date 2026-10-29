package com.zynergylabs.forager.app.diagnostics.walklog

import android.util.Log
import java.io.BufferedWriter
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.OutputStreamWriter

/**
 * One walk log file (dispatch 2026-09-28-532, verify item 5).
 *
 * **Storage.** Below [minFreeBytes] free (200 MB, RECORD -559 choice 4) it writes one `STOPPED` line
 * and refuses everything after: at [open], and at every [checkSpace], which the session calls once a
 * minute. A write that fails is the same kind of stop, with reason `write-error`, logged. A stop
 * here never reaches the recording: nothing in this class can throw out of [write] or [checkSpace].
 *
 * **Surviving a killed process.** Lines are buffered and flushed to the file once [FLUSH_INTERVAL_NANOS]
 * has passed since the last flush, so a process killed mid-walk loses at most that much. The file
 * descriptor is synced at each [checkSpace] (once a minute), so a phone that powers off mid-walk
 * loses at most a minute. Opened for append, never truncated.
 *
 * Not thread-safe: the session calls it from one thread.
 */
class WalkLogWriter(
    val file: File,
    private val freeBytes: () -> Long,
    private val nowNanos: () -> Long,
    private val minFreeBytes: Long = MIN_FREE_BYTES,
) {
    private var stream: FileOutputStream? = null
    private var out: BufferedWriter? = null
    private var lastFlushNanos = 0L

    val isOpen: Boolean get() = out != null

    /** Why the log stopped early (`storage-low`, `write-error`), or null while it runs and after a normal close. */
    var stoppedReason: String? = null
        private set

    /** True when the file is open for writing; false, with a `STOPPED` line written where possible, when it is not. */
    fun open(): Boolean {
        if (out != null) return true
        if (stoppedReason != null) return false
        val free = freeBytes()
        if (free < minFreeBytes) {
            appendStopLineDirectly(STORAGE_LOW, "free=$free min=$minFreeBytes")
            return false
        }
        return try {
            file.parentFile?.mkdirs()
            val opened = FileOutputStream(file, true)
            stream = opened
            out = BufferedWriter(OutputStreamWriter(opened, Charsets.UTF_8))
            lastFlushNanos = nowNanos()
            true
        } catch (e: IOException) {
            Log.w(TAG, "Couldn't open the walk log ${file.name}; nothing will be logged for this recording.", e)
            stoppedReason = WRITE_ERROR
            false
        }
    }

    /** False once the log has stopped or closed; the lines are then dropped, which the `STOPPED` line already says. */
    fun write(lines: List<String>): Boolean {
        val writer = out ?: return false
        return try {
            lines.forEach { writer.write(it); writer.newLine() }
            val now = nowNanos()
            if (now - lastFlushNanos >= FLUSH_INTERVAL_NANOS) {
                writer.flush()
                lastFlushNanos = now
            }
            true
        } catch (e: IOException) {
            stopOnWriteError(e)
            false
        }
    }

    /**
     * [write], then flushed at once whatever the flush interval: the header, so the file holds it before the session
     * reports itself active (CI run 38115289698). False, with the log stopped, on a write error.
     */
    fun writeAndFlush(lines: List<String>): Boolean {
        val writer = out ?: return false
        return try {
            lines.forEach { writer.write(it); writer.newLine() }
            writer.flush()
            lastFlushNanos = nowNanos()
            true
        } catch (e: IOException) {
            stopOnWriteError(e)
            false
        }
    }

    /** Once a minute: stop below the floor; otherwise flush and sync, so a phone switched off mid-walk loses a minute at most. */
    fun checkSpace(): Boolean {
        val writer = out ?: return false
        val free = freeBytes()
        if (free < minFreeBytes) {
            stop(STORAGE_LOW, "free=$free min=$minFreeBytes")
            return false
        }
        return try {
            writer.flush()
            stream?.fd?.sync()
            lastFlushNanos = nowNanos()
            true
        } catch (e: IOException) {
            stopOnWriteError(e)
            false
        }
    }

    /** Writes [endLine] and closes. Does nothing to a log that already stopped: its `STOPPED` line stays the last. */
    fun close(endLine: String) {
        val writer = out ?: return
        try {
            writer.write(endLine)
            writer.newLine()
            writer.flush()
            stream?.fd?.sync()
        } catch (e: IOException) {
            Log.w(TAG, "Couldn't write the walk log's end line to ${file.name}.", e)
        } finally {
            closeQuietly()
        }
    }

    private fun stop(reason: String, detail: String) {
        val writer = out
        try {
            writer?.write(WalkLogFormat.stopped(nowNanos(), reason, detail))
            writer?.newLine()
            writer?.flush()
        } catch (e: IOException) {
            Log.w(TAG, "Couldn't write the walk log's stop line ($reason) to ${file.name}.", e)
        } finally {
            closeQuietly()
            stoppedReason = reason
        }
        Log.w(TAG, "Walk log ${file.name} stopped: $reason $detail. The recording carries on.")
    }

    private fun stopOnWriteError(error: IOException) {
        Log.w(TAG, "A write to the walk log ${file.name} failed.", error)
        stop(WRITE_ERROR, "error=${error.javaClass.simpleName}")
    }

    /** Below the floor before anything was opened: one line, appended in one go, so the file says why it is empty. */
    private fun appendStopLineDirectly(reason: String, detail: String) {
        stoppedReason = reason
        try {
            file.parentFile?.mkdirs()
            file.appendText(WalkLogFormat.stopped(nowNanos(), reason, detail) + "\n")
        } catch (e: IOException) {
            Log.w(TAG, "Couldn't write the walk log's stop line ($reason) to ${file.name}.", e)
        }
        Log.w(TAG, "Walk log ${file.name} not started: $reason $detail. The recording carries on.")
    }

    private fun closeQuietly() {
        try {
            out?.close()
        } catch (e: IOException) {
            Log.w(TAG, "Couldn't close the walk log ${file.name}.", e)
        }
        out = null
        stream = null
    }

    companion object {
        /** RECORD -559, choice 4: stop below 200 MB free. */
        const val MIN_FREE_BYTES = 200L * 1024 * 1024

        /** A process killed mid-walk loses at most this much of the log. */
        const val FLUSH_INTERVAL_NANOS = 5_000_000_000L

        const val STORAGE_LOW = "storage-low"
        const val WRITE_ERROR = "write-error"
        private const val TAG = "WalkLog"
    }
}
