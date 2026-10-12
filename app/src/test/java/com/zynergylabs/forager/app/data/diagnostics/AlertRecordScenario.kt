package com.zynergylabs.forager.app.data.diagnostics

import com.zynergylabs.forager.app.ForagerApplication
import java.io.File
import kotlinx.coroutines.runBlocking

/**
 * One entry into each of the three alert records, through the app's own container: the
 * `ReturnWatch`, `BackByWatch` and `SundownWatch` instances that `MainActivity`, the recording
 * service and `WakeUpAlarmReceiver` all use, so what is written is what the app writes. Shared by the
 * debug and release halves of the alert-record check (dispatch 2026-10-11, RECORD -830): the debug
 * half proves this scenario does reach every record (so the release half's "nothing written" is not
 * a scenario that writes nothing anyway), and the release half asks the same scenario for files.
 *
 * - Return taken on a watch with no recording begun: `ReturnWatch.startReturn` takes it and records
 *   `return-started`.
 * - The Back by and sundown wake-up alarms arriving with nothing watched, exactly what
 *   `WakeUpAlarmReceiver.onReceive` calls: each records `alarm-delivered` and does nothing else.
 *
 * The file names are written out here rather than read from the debug-only constants, so the release
 * half can name them at all, and so a rename in the writer cannot quietly move the files out of view.
 */
internal object AlertRecordScenario {

    const val TRACK_ID = "record-scenario-track"

    val RECORD_FILE_NAMES = listOf("return-record.log", "back-by-record.log", "sundown-record.log")

    /** Clears any earlier record files, then drives the three entries. */
    fun run(app: ForagerApplication) {
        RECORD_FILE_NAMES.forEach { File(app.filesDir, it).delete() }
        val container = app.container
        container.returnWatch.startReturn(TRACK_ID)
        runBlocking {
            container.backByWatch.onAlarm()
            container.sundownWatch.onAlarm()
        }
    }

    /** Every file under [root] whose name ends `-record.log`: the three by name, and any renamed one. */
    fun recordFilesUnder(root: File): List<File> =
        root.walkTopDown().filter { it.isFile && it.name.endsWith("-record.log") }.toList()
}
