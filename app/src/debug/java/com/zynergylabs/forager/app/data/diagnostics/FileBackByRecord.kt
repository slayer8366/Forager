package com.zynergylabs.forager.app.data.diagnostics

import android.util.Log
import com.zynergylabs.forager.app.domain.AlertDeliveryOutcome
import com.zynergylabs.forager.app.domain.BackByRecord
import com.zynergylabs.forager.app.domain.BackByRecordEvent
import com.zynergylabs.forager.app.domain.CurrentTimeProvider
import java.io.File
import java.io.IOException
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/**
 * The Back by record as a file in app storage (dispatch 2026-09-28-796), in [FileReturnRecord]'s
 * style and for its reasons: one line per entry, UTC time, kind, track id and details, appended; no
 * positions; a flat log a person reads, so a file and not Room. In a debug build:
 * `adb shell run-as com.zynergylabs.forager.app cat files/back-by-record.log`.
 *
 * **An operating limit:** past [maxBytes] the oldest half of the lines is dropped. An evaluation line
 * is written at most every 15 s while a time is set (about 90 bytes), so [BACK_BY_RECORD_MAX_BYTES]
 * holds well over a day of set reminders.
 *
 * A write that fails is logged, never thrown: the record must not take a recording down with it.
 */
class FileBackByRecord(private val file: File, private val clock: CurrentTimeProvider, private val maxBytes: Long = BACK_BY_RECORD_MAX_BYTES) : BackByRecord {
    @Synchronized
    override fun write(event: BackByRecordEvent) {
        try {
            file.parentFile?.mkdirs()
            file.appendText(backByLineFor(event, clock.nowEpochMillis()) + "\n")
            if (file.length() > maxBytes) {
                val lines = file.readLines()
                file.writeText(lines.drop(lines.size / 2).joinToString(separator = "\n", postfix = "\n"))
            }
        } catch (e: IOException) {
            Log.w(TAG, "The Back by record could not be written; this entry is lost: ${event::class.simpleName}", e)
        }
    }

    private companion object {
        const val TAG = "BackByRecord"
    }
}

/** One entry as its line. Times are UTC, to the millisecond. */
internal fun backByLineFor(event: BackByRecordEvent, atMillis: Long): String {
    val details = when (event) {
        is BackByRecordEvent.Started -> "started track=${event.trackId} kept=${event.keptAtMillis?.let(::utcMillis) ?: "none"}"
        is BackByRecordEvent.Set ->
            "set track=${event.trackId} at=${utcMillis(event.atMillis)} " +
                if (event.accepted) "accepted" else "refused(watching ${event.watchedTrackId ?: "none"})"
        BackByRecordEvent.SetWithNoRecording -> "set-ignored reason=no-recording-on-screen"
        is BackByRecordEvent.Later -> "later track=${event.trackId} at=${utcMillis(event.atMillis)}"
        is BackByRecordEvent.Evaluated ->
            "evaluated track=${event.trackId} at=${utcMillis(event.atMillis)} due=${if (event.due) "yes" else "no"} by=${event.trigger.name.lowercase()}"
        is BackByRecordEvent.Fired -> "fired track=${event.trackId} at=${utcMillis(event.atMillis)} " + deliveryWords(event.outcome)
        is BackByRecordEvent.Ended -> "ended track=${event.trackId} reason=${event.reason.name.lowercase().replace('_', '-')}"
        is BackByRecordEvent.AlarmScheduled ->
            "alarm-scheduled track=${event.trackId} at=${utcMillis(event.atMillis)} ${if (event.accepted) "accepted" else "refused"}"
        is BackByRecordEvent.AlarmCancelled -> "alarm-cancelled track=${event.trackId}"
        is BackByRecordEvent.AlarmDelivered -> "alarm-delivered " + (event.trackId?.let { "track=$it" } ?: "nothing-set")
    }
    return "${utcMillis(atMillis)} $details"
}

internal fun deliveryWords(outcome: AlertDeliveryOutcome?): String = outcome?.let { o ->
    val vibration = when {
        o.vibrationSkipped != null -> "skipped(${o.vibrationSkipped})"
        o.vibrated -> "done"
        else -> "failed(${o.vibrationProblem})"
    }
    "notification=${if (o.notificationPosted) "posted" else "not-posted(${o.notificationProblem})"} vibration=$vibration"
} ?: "outcome=not-reported"

private val BACK_BY_UTC_MILLIS: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'").withZone(ZoneOffset.UTC)

internal fun utcMillis(millis: Long): String = BACK_BY_UTC_MILLIS.format(Instant.ofEpochMilli(millis))

/** The record's file name in app storage (`filesDir`). */
const val BACK_BY_RECORD_FILE_NAME = "back-by-record.log"

/** The record's size limit; see [FileBackByRecord]. */
const val BACK_BY_RECORD_MAX_BYTES = 512L * 1024L
