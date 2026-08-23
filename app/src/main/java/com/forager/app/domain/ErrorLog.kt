package com.forager.app.domain

/**
 * Owned abstraction over "log this throwable for diagnosis, without ever surfacing its text to
 * the user" — see the error-presentation spec's absolute rule (`docs/error-presentation-spec.md`).
 *
 * [android.util.Log] is not safe to call directly from [com.forager.app.ui.availability.AvailabilityViewModel]
 * or [com.forager.app.ui.track.TrackRecordingViewModel]: both are deliberately plain-JVM testable,
 * no Robolectric — the same reasoning [CurrentTimeProvider] gives for why the clock is injected
 * rather than read inline — and `Log.w` throws under a plain JVM unit test ("Method w in
 * android.util.Log not mocked"). Confirmed the hard way, not guessed: giving both ViewModels
 * `MushroomLogViewModel`'s identical `Log.w(TAG, ..., error)` shape broke 23 existing tests that
 * construct one incidentally — most via [AvailabilityViewModel]'s `init` block, which
 * unconditionally loads planned trips and offline map status, and which many existing tests stub
 * as a deliberately-failing stand-in for a dependency they don't otherwise care about.
 * `MushroomLogViewModel`'s own `Log.w` calls carry no such risk only because nothing tests its
 * failure paths at all — an absence of coverage, not a proof the pattern is safe everywhere.
 *
 * The throwable itself still has to survive somewhere for diagnosis — CLAUDE.md: a failure is
 * reported, never swallowed — so this doesn't discard it, it only moves *how* it's logged to
 * whichever caller constructs the ViewModel. See each constructor's own default for what a test
 * gets with no per-test setup, and `MainActivity` for the real, `Log.w`-backed one production
 * gets.
 */
fun interface ErrorLog {
    fun w(tag: String, message: String, error: Throwable)
}
