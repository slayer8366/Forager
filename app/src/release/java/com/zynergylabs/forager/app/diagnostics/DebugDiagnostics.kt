package com.zynergylabs.forager.app.diagnostics

import android.content.Context

/**
 * The release build's twin of the debug-only diagnostics installer: the same two entry points
 * `ForagerApplication` calls, doing nothing. No StrictMode policy, no log, no file. The debug
 * version in `src/debug` documents what the real one does and why; this one exists so the call
 * sites in `src/main` compile in both build types without a `BuildConfig.DEBUG` branch — which,
 * with `isMinifyEnabled = false` on the release build type, would have shipped the debug classes
 * in the release APK unreachable rather than absent.
 */
class DebugDiagnostics private constructor() {

    @Suppress("UNUSED_PARAMETER")
    fun recordSweep(deleted: Int) = Unit

    /**
     * The debug twin writes one entry per shot; this does nothing. Arguments are values rather than
     * a formatted string precisely so that this costs nothing to call: there is no interpolation at
     * the call site to evaluate before arriving here.
     */
    @Suppress("UNUSED_PARAMETER")
    fun recordCaptureShot(deviceRotation: Int?, targetRotation: Int, requestDegrees: Int?, resolution: String?) = Unit

    /** The second of the debug twin's two entries per capture; this does nothing. */
    @Suppress("UNUSED_PARAMETER")
    fun recordCaptureOrientation(
        fileName: String,
        branch: String,
        fromTag: Int? = null,
        toTag: Int? = null,
        degrees: Int? = null,
        reason: String? = null,
        error: Throwable? = null,
    ) = Unit

    companion object {
        @Suppress("UNUSED_PARAMETER")
        fun install(context: Context): DebugDiagnostics = DebugDiagnostics()
    }
}
