package com.zynergylabs.forager.app.data.diagnostics

import android.util.Log
import com.zynergylabs.forager.app.domain.CurrentTimeProvider
import com.zynergylabs.forager.app.domain.SundownRecord
import com.zynergylabs.forager.app.domain.SundownRecordEvent
import java.io.File
import java.io.IOException

/**
 * The sundown record as a file in app storage (dispatch 2026-09-28-796), as [FileBackByRecord] is: one
 * line per entry, UTC time, kind, track and details; no positions. An evaluation line every 15 s of a
 * recording is about 20 KB an hour, so [SUNDOWN_RECORD_MAX_BYTES] holds about a day before the oldest
 * half is dropped. In a debug build: `adb shell run-as com.zynergylabs.forager.app cat files/sundown-record.log`.
 * A write that fails is logged, never thrown.
 */
class FileSundownRecord(private val file: File, private val clock: CurrentTimeProvider, private val maxBytes: Long = SUNDOWN_RECORD_MAX_BYTES) : SundownRecord {
    @Synchronized
    override fun write(event: SundownRecordEvent) {
        try {
            file.parentFile?.mkdirs()
            file.appendText(sundownLineFor(event, clock.nowEpochMillis()) + "\n")
            if (file.length() > maxBytes) {
                val lines = file.readLines()
                file.writeText(lines.drop(lines.size / 2).joinToString(separator = "\n", postfix = "\n"))
            }
        } catch (e: IOException) {
            Log.w(TAG, "The sundown record could not be written; this entry is lost: ${event::class.simpleName}", e)
        }
    }

    private companion object {
        const val TAG = "SundownRecord"
    }
}

/** One entry as its line. Times are UTC, to the millisecond. */
internal fun sundownLineFor(event: SundownRecordEvent, atMillis: Long): String {
    val details = when (event) {
        is SundownRecordEvent.Evaluated -> "evaluated track=${event.trackId} by=${event.trigger.name.lowercase()}"
        is SundownRecordEvent.Fired -> "fired track=${event.trackId} alert=${event.alert.name.lowercase().replace('_', '-')} " + deliveryWords(event.outcome)
        is SundownRecordEvent.AlarmScheduled ->
            "alarm-scheduled track=${event.trackId} alert=${event.alert.name.lowercase().replace('_', '-')} at=${utcMillis(event.atMillis)} ${if (event.accepted) "accepted" else "refused"}"
        is SundownRecordEvent.AlarmCancelled -> "alarm-cancelled track=${event.trackId}"
        is SundownRecordEvent.AlarmDelivered -> "alarm-delivered " + (event.trackId?.let { "track=$it" } ?: "nothing-watched")
    }
    return "${utcMillis(atMillis)} $details"
}

/** The record's file name in app storage (`filesDir`). */
const val SUNDOWN_RECORD_FILE_NAME = "sundown-record.log"

const val SUNDOWN_RECORD_MAX_BYTES = 512L * 1024L
