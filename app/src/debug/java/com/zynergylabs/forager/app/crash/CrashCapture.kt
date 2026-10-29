package com.zynergylabs.forager.app.crash

import android.os.Build

/**
 * Debug builds: installs [CrashUncaughtExceptionHandler] as the default handler, chained to the one
 * it replaces, so a crash is written to [store] and then reported exactly as before. What
 * `ForagerApplication` did inline until dispatch 2026-10-11 (RECORD -830) moved crash capture to
 * debug only; the release version of this function, in `src/release`, does nothing.
 */
fun installCrashCapture(store: CrashFileStore) {
    val previousHandler = Thread.getDefaultUncaughtExceptionHandler()
    Thread.setDefaultUncaughtExceptionHandler(
        CrashUncaughtExceptionHandler(store, Build.VERSION.SDK_INT, previousHandler),
    )
}
