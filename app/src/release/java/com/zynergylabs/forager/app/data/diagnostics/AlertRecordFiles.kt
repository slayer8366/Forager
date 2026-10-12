package com.zynergylabs.forager.app.data.diagnostics

import android.content.Context
import com.zynergylabs.forager.app.domain.BackByRecord
import com.zynergylabs.forager.app.domain.CurrentTimeProvider
import com.zynergylabs.forager.app.domain.NoBackByRecord
import com.zynergylabs.forager.app.domain.NoSundownRecord
import com.zynergylabs.forager.app.domain.ReturnRecord
import com.zynergylabs.forager.app.domain.SundownRecord

/**
 * The release build's twin of the debug-only alert record files (dispatch 2026-10-11; the owner,
 * RECORD -830: "The background alert record files"): the same three calls `AppContainer` makes,
 * each returning a record that writes nothing. The records are write-only (their interfaces have no
 * read, and nothing in the app opens their files), so the Return, Back by and sundown watches behave
 * exactly as in a debug build; only the lines are gone. A twin rather than a `BuildConfig.DEBUG`
 * branch, so the file writers are not in the release APK at all.
 */
object AlertRecordFiles {
    @Suppress("UNUSED_PARAMETER")
    fun returnRecord(context: Context, clock: CurrentTimeProvider): ReturnRecord = ReturnRecord { }

    @Suppress("UNUSED_PARAMETER")
    fun backByRecord(context: Context, clock: CurrentTimeProvider): BackByRecord = NoBackByRecord

    @Suppress("UNUSED_PARAMETER")
    fun sundownRecord(context: Context, clock: CurrentTimeProvider): SundownRecord = NoSundownRecord
}
