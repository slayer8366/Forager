package com.forager.app.domain

/**
 * Owned sink for the pace instrumentation record — Pass 2 of the return-estimate device checks
 * (`docs/audits/2026-09-08-pass2-speed-comparison-log-line-prebuild-report.md`, §F).
 *
 * The same shape as [ErrorLog], for the same reason: `TrackRecordingViewModel` is plain-JVM
 * testable and must not touch `android.util.Log` (that interface's doc comment records the 23
 * tests that broke when it was tried). This one is not an error sink — it carries one
 * machine-readable line per pace evaluation ([toLogRecord]) — so it is its own interface rather
 * than a second method on [ErrorLog]: a `Throwable`-taking warning and a debug-level data record
 * have nothing in common but the word "log". `MainActivity` wires the `Log.d`-backed one under
 * [PACE_LOG_TAG]; every test gets the discarding default unless it asks for a recording one.
 *
 * Diagnostic, nothing reads it — the owner's ruling on the Items 1–3 completion report was that
 * domain code does not log and the first consumer logs the comparison per track. This is that
 * consumer's sink, for the pace alone: the estimate itself stays unreachable until the path-home
 * ruling comes back (owner, 2026-09-08).
 */
fun interface PaceLog {
    fun record(line: String)
}

/** The pace record's tag — `adb logcat -s ForagerPace`, beside `ForagerFix`. */
const val PACE_LOG_TAG = "ForagerPace"
