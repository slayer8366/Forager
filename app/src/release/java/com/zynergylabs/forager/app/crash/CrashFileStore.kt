package com.zynergylabs.forager.app.crash

import android.content.Context
import java.io.File

/**
 * The release build's twin of the debug-only crash store (dispatch 2026-10-11, RECORD -830): the two
 * entry points `src/main` reaches, `forContext` (`AppContainer`, `AvailabilityScreen`'s default) and
 * `list` (Settings' crash list, which a release build never composes). It writes nothing and lists
 * nothing; the debug version in `src/debug` documents the real one. A twin rather than a
 * `BuildConfig.DEBUG` branch for the reason the release `DebugDiagnostics` gives: with minification
 * off, a branch would ship the debug classes in the release APK.
 */
class CrashFileStore private constructor() {

    /** The debug store lists its crash files; a release build captures none, so there are none to list. */
    fun list(): List<File> = emptyList()

    companion object {
        @Suppress("UNUSED_PARAMETER")
        fun forContext(context: Context): CrashFileStore = CrashFileStore()
    }
}
