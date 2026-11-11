package com.zynergylabs.forager.app.ui.availability

/**
 * When a failed search may run again by itself (dispatch 2026-11-11-01). Plain Kotlin, no Android, so its rules are tested
 * headless (`SearchAutoRetryTest`); [RetryFailedSearchOnSignalReturn] feeds it and runs the retry it asks for.
 *
 * The owner's rulings it carries, verbatim:
 * - C4, **"At once (Recommended)"**: a return of signal while the search has failed runs it again straight away.
 * - C7, **"Notice stays, no auto retry (Recommended)"**: with no return of signal, nothing runs by itself.
 * - E2, **"Only with Forager on screen (Recommended)"**, whose option read "Forager in the background > nothing runs until you
 *   come back, then it runs once": a return while Forager is not on screen (not resumed) is held, and runs once when it comes
 *   back, if the search has still failed and the signal is still there. A failure with no return while away is not retried on
 *   coming back (C7).
 * - E3, **"3 tries, then wait for me (Recommended)"**: at most [maxTries] automatic tries per failed search; after that only
 *   "Try again" or a new search runs it. The count starts again on "Try again" ([onUserRetry]), on a new search and on a
 *   success (both seen as nothing failed and nothing running, [onStateChanged]).
 *
 * And Sorrel's F2: a return that comes while the fetch is still running is held, and if that fetch then fails, the search runs
 * again once ([onStateChanged]); if it succeeds, the held return is dropped.
 *
 * Kept for as long as the screen is composed: a turn of the phone keeps it (MainActivity handles the turn in place); an
 * Activity created again starts it at zero.
 */
internal class SearchAutoRetry(private val maxTries: Int = SEARCH_AUTO_RETRY_MAX_TRIES) {

    /** Automatic tries spent on the failed search showing. */
    var tries: Int = 0
        private set

    /** A return of signal not yet acted on: it came while the fetch was running, or while Forager was not on screen. */
    private var returnHeld = false

    /**
     * A return of signal (already spaced by [signalReturns]). [failed]: the map's half of the search has failed and shows its
     * notice; [running]: a fetch for it is running; [onScreen]: Forager is resumed. True when the search should run again now.
     */
    fun onSignalReturn(failed: Boolean, running: Boolean, onScreen: Boolean): Boolean {
        if (!failed && !running) return false
        if (!onScreen || running) {
            returnHeld = true
            return false
        }
        return take()
    }

    /**
     * The search's state or Forager's place on screen changed; [validated] is the signal now. True when the search should run
     * again now: a held return, with the search failed, on screen, and the signal still there.
     */
    fun onStateChanged(failed: Boolean, running: Boolean, onScreen: Boolean, validated: Boolean): Boolean {
        if (!failed && !running) {
            // A success, a new search, or a cleared one: the count and any held return start again.
            tries = 0
            returnHeld = false
            return false
        }
        if (failed && !running && onScreen && returnHeld) {
            returnHeld = false
            return validated && take()
        }
        return false
    }

    /** "Try again": the user ran it, so the automatic tries start again. */
    fun onUserRetry() {
        tries = 0
        returnHeld = false
    }

    private fun take(): Boolean = if (tries < maxTries) {
        tries++
        true
    } else {
        false
    }
}

/** E3, the owner: "3 tries, then wait for me (Recommended)". */
internal const val SEARCH_AUTO_RETRY_MAX_TRIES = 3
