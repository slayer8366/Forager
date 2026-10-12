package com.zynergylabs.forager.app.data.diagnostics

import android.util.Log
import com.zynergylabs.forager.app.domain.AlertDeliveryOutcome
import com.zynergylabs.forager.app.domain.CurrentTimeProvider
import com.zynergylabs.forager.app.domain.ReturnRecord
import com.zynergylabs.forager.app.domain.ReturnRecordEvent
import java.io.File
import java.io.IOException
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/**
 * The Return record as a file in app storage (dispatch 2026-09-28-451): one line per entry, UTC
 * time, kind, track id and details, appended; no positions.
 *
 * **A file, not Room.** Nothing joins these entries to tracks or filters by them: it is a flat
 * diagnostic log a person reads, so CLAUDE.md's split (Room for data that relates, a file or
 * DataStore for the flat kind) puts it in a file, with no migration and no read path in the app.
 * In a debug build: `adb shell run-as com.zynergylabs.forager.app cat files/return-record.log`.
 * It outlives the process and the system log's rotation, which is what it is for.
 *
 * **An operating limit:** past [maxBytes] the oldest half of the lines is dropped, keeping the
 * newest. At about 70 bytes a line, [RETURN_RECORD_MAX_BYTES] is several thousand entries, far
 * more than a recording writes.
 *
 * A write that fails is logged, never thrown: the record must not take a recording down with it.
 */
class FileReturnRecord(private val file: File, private val clock: CurrentTimeProvider, private val maxBytes: Long = RETURN_RECORD_MAX_BYTES) : ReturnRecord {
    @Synchronized
    override fun write(event: ReturnRecordEvent) {
        try {
            file.parentFile?.mkdirs()
            file.appendText(lineFor(event, clock.nowEpochMillis()) + "\n")
            if (file.length() > maxBytes) {
                val lines = file.readLines()
                file.writeText(lines.drop(lines.size / 2).joinToString(separator = "\n", postfix = "\n"))
            }
        } catch (e: IOException) {
            Log.w(TAG, "The Return record could not be written; this entry is lost: ${event::class.simpleName}", e)
        }
    }

    private companion object {
        const val TAG = "ReturnRecord"
    }
}

/** One entry as its line. Times are UTC, to the millisecond. */
internal fun lineFor(event: ReturnRecordEvent, atMillis: Long): String {
    val details = when (event) {
        is ReturnRecordEvent.ReturnStarted -> "return-started track=${event.trackId}"
        is ReturnRecordEvent.ReturnRefused -> "return-refused track=${event.trackId} watched=${event.watchedTrackId ?: "none"}"
        is ReturnRecordEvent.ReturnEnded -> "return-ended track=${event.trackId} reason=${event.reason.name.lowercase().replace('_', '-')}"
        is ReturnRecordEvent.WentOffTrack -> "went-off-track track=${event.trackId} reading=${utc(event.readingAtMillis)}"
        is ReturnRecordEvent.ReArmed -> "re-armed track=${event.trackId} reading=${utc(event.readingAtMillis)}"
        is ReturnRecordEvent.AlertWithheld -> "alert-withheld track=${event.trackId} reason=reminder-off"
        is ReturnRecordEvent.AlertDelivered -> "alert-delivery track=${event.trackId} " + (
            event.outcome?.let { o ->
                "notification=${if (o.notificationPosted) "posted" else "not-posted(${o.notificationProblem})"} " +
                    "vibration=${vibrationWord(o)}"
            } ?: "outcome=not-reported"
            )
    }
    return "${utc(atMillis)} $details"
}

/**
 * "done", "skipped(phone on silent)" or "failed(<problem>)". Skipped is Android choosing not to play
 * a vibration that was issued without error (dispatch 2026-09-28-685, fix 3); it is never "done".
 */
private fun vibrationWord(o: AlertDeliveryOutcome): String = when {
    o.vibrationSkipped != null -> "skipped(${o.vibrationSkipped})"
    o.vibrated -> "done"
    else -> "failed(${o.vibrationProblem})"
}

private val UTC_MILLIS: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'").withZone(ZoneOffset.UTC)

private fun utc(millis: Long): String = UTC_MILLIS.format(Instant.ofEpochMilli(millis))

/** The record's file name in app storage (`filesDir`). */
const val RETURN_RECORD_FILE_NAME = "return-record.log"

/** The record's size limit; see [FileReturnRecord]. */
const val RETURN_RECORD_MAX_BYTES = 512L * 1024L
