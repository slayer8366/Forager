package com.zynergylabs.forager.app.diagnostics

import android.content.Context

/**
 * The release build's twin of the debug-only walk logger (dispatch 2026-09-28-532): the same calls
 * `TrackRecordingService` makes, doing nothing. No sensor, no listener, no wake lock, no file, and no
 * permission in the release manifest. The debug version in `src/debug` documents what the real one
 * does. A twin rather than a `BuildConfig.DEBUG` branch for the reason the release `DebugDiagnostics`
 * gives: with minification off, a branch would ship the debug classes in the release APK.
 */
class WalkLogger private constructor() {

    @Suppress("UNUSED_PARAMETER")
    fun onRecordingStarted(trackId: String) = Unit

    fun onRecordingStopped() = Unit

    companion object {
        private val INSTANCE = WalkLogger()

        @Suppress("UNUSED_PARAMETER")
        fun of(context: Context): WalkLogger = INSTANCE
    }
}
