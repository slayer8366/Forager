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
 * Amendment Q, QC1 (the owner: "3 tries, then wait for me", option text "the app tries again by itself a few seconds later, up
 * to 3 tries in all", and "Growing: 3, then 10"): an automatic try that fails is followed by a timed one, 3 s after the first
 * failed and 10 s after the second ([timedTryDelayMs], [onTimedTryDue]), within the same three. A timed try that falls due with
 * no signal does not run; the next return of signal runs it, as before. One that falls due with Forager off screen waits for it
 * to come back. A return of signal during the wait runs at once, counts as the next try, and the timed one is dropped.
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

    /** QC1: an automatic try is running; its failure starts the wait for the next timed try. */
    private var automaticInFlight = false

    /** QC1: an automatic try failed and a timed try is due after [timedTryDelayMs]. */
    private var awaitingTimed = false

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
        return takeAutomatic()
    }

    /**
     * The search's state or Forager's place on screen changed; [validated] is the signal now. True when the search should run
     * again now: a held return, with the search failed, on screen, and the signal still there.
     */
    fun onStateChanged(failed: Boolean, running: Boolean, onScreen: Boolean, validated: Boolean): Boolean {
        if (!failed && !running) {
            // A success, a new search, or a cleared one: the count, any held return and any timed try start again.
            tries = 0
            returnHeld = false
            automaticInFlight = false
            awaitingTimed = false
            return false
        }
        if (failed && !running && automaticInFlight) {
            // QC1: the automatic try failed; the next is timed, while any of the three are left.
            automaticInFlight = false
            awaitingTimed = false
        }
        if (failed && !running && onScreen && returnHeld) {
            returnHeld = false
            return validated && takeAutomatic()
        }
        return false
    }

    /** QC1: how long to wait before the timed try that is due, or null when none is: 3 s after the first try, 10 s after the second. */
    fun timedTryDelayMs(): Long? = if (awaitingTimed) SEARCH_TIMED_TRY_DELAYS_MS.getOrNull(tries - 1) else null

    /** QC1: the wait for a timed try ended. True when the search should run again now. */
    fun onTimedTryDue(failed: Boolean, running: Boolean, onScreen: Boolean, validated: Boolean): Boolean {
        if (!awaitingTimed || !onScreen) return false
        awaitingTimed = false
        if (!failed || running || !validated) return false
        return takeAutomatic()
    }

    /** "Try again": the user ran it, so the automatic tries start again, and a timed one waiting is dropped. */
    fun onUserRetry() {
        tries = 0
        returnHeld = false
        automaticInFlight = false
        awaitingTimed = false
    }

    private fun takeAutomatic(): Boolean {
        val taken = take()
        if (taken) {
            automaticInFlight = true
            awaitingTimed = false
        }
        return taken
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

/** QC1, the owner: "Growing: 3, then 10": the wait before the second automatic try, and before the third. */
internal val SEARCH_TIMED_TRY_DELAYS_MS: List<Long> = listOf(3_000L, 10_000L)
