package com.forager.app.crash

/**
 * Installed as [Thread.setDefaultUncaughtExceptionHandler] in `ForagerApplication.onCreate()`.
 *
 * Before this, the app had no crash visibility at all: no crash reporting SDK, no
 * `setDefaultUncaughtExceptionHandler`, no StrictMode, nothing — a read-only investigation into a
 * location-permission-denied crash (see that pulse's report) could get no further than "one of
 * three plausible mechanisms" for exactly that reason. This class is the instrument, not the fix:
 * it only observes. It writes a trace via [crashFileStore], then unconditionally hands the
 * throwable to [previousHandler] — the platform's own default, captured by the installer before
 * this one replaced it — so process-death behavior is unchanged from before this class existed.
 */
class CrashUncaughtExceptionHandler(
    private val crashFileStore: CrashFileStore,
    private val apiLevel: Int,
    private val previousHandler: Thread.UncaughtExceptionHandler?,
) : Thread.UncaughtExceptionHandler {

    override fun uncaughtException(thread: Thread, throwable: Throwable) {
        try {
            crashFileStore.write(thread, throwable, apiLevel)
        } catch (writeFailure: Throwable) {
            // Capturing the crash must never become the reason the crash isn't reported to
            // previousHandler — see this class's own doc comment.
        }
        previousHandler?.uncaughtException(thread, throwable)
    }
}
