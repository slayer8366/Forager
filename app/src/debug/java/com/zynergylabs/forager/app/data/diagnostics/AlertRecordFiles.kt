package com.zynergylabs.forager.app.data.diagnostics

import android.content.Context
import com.zynergylabs.forager.app.domain.BackByRecord
import com.zynergylabs.forager.app.domain.CurrentTimeProvider
import com.zynergylabs.forager.app.domain.ReturnRecord
import com.zynergylabs.forager.app.domain.SundownRecord
import java.io.File

/**
 * Debug builds: the three alert records as files in app storage, as `AppContainer` built them inline
 * until dispatch 2026-10-11 (RECORD -830) made them debug-only. `files/return-record.log`
 * ([FileReturnRecord]), `files/back-by-record.log` ([FileBackByRecord]) and
 * `files/sundown-record.log` ([FileSundownRecord]), read by the device checks over
 * `adb shell run-as`. The release version of this object, in `src/release`, writes nothing.
 */
object AlertRecordFiles {
    fun returnRecord(context: Context, clock: CurrentTimeProvider): ReturnRecord =
        FileReturnRecord(File(context.filesDir, RETURN_RECORD_FILE_NAME), clock)

    fun backByRecord(context: Context, clock: CurrentTimeProvider): BackByRecord =
        FileBackByRecord(File(context.filesDir, BACK_BY_RECORD_FILE_NAME), clock)

    fun sundownRecord(context: Context, clock: CurrentTimeProvider): SundownRecord =
        FileSundownRecord(File(context.filesDir, SUNDOWN_RECORD_FILE_NAME), clock)
}
