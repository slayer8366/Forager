package com.zynergylabs.forager.app.domain

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.retryWhen
import kotlinx.coroutines.launch

/**
 * How many committed journal entries keep each waypoint, track, offline region and photo, as live counts:
 * each flow emits a new map whenever a journal entry that could change it is saved or deleted, by any path
 * (dispatch 2026-10-28-08, RECORD -1780 and -1781; the owner: "Live count (Recommended)").
 *
 * What the counts feed: a waypoint's "Used in" in its details, and the "used in N journal entries" of each
 * record's delete message, the album's journal badge and the map's photo bubble line. Until this dispatch
 * each list read its counts once, when the list loaded, and no entry save or delete read them again, so a
 * count could stay wrong on screen (docs/audits/2026-10-28-records-follow-ups-look.md, section 1).
 *
 * A map holds only ids with at least one entry keeping them; a missing id means 0. Its own interface, not
 * four more methods on [CartographyEntryRepository], so that repository's many test fakes are untouched.
 *
 * Rejected: a refresh of each list on an "entries changed" signal from the save and delete use cases. It
 * covers only the write paths that remember to send it; a draft commit, a backup restore or a later writer
 * that did not would bring the stale count back unseen. The database's own change tracking covers them all.
 */
interface EntryReferenceCountSource {
    fun waypointCounts(): Flow<Map<String, Int>>
    fun trackCounts(): Flow<Map<String, Int>>
    fun offlineRegionCounts(): Flow<Map<Long, Int>>
    fun photoCounts(): Flow<Map<String, Int>>
}

/** No counts ever: the default for ViewModels built in tests that do not read them. Every count reads 0. */
object NoEntryReferenceCounts : EntryReferenceCountSource {
    override fun waypointCounts(): Flow<Map<String, Int>> = emptyFlow()
    override fun trackCounts(): Flow<Map<String, Int>> = emptyFlow()
    override fun offlineRegionCounts(): Flow<Map<Long, Int>> = emptyFlow()
    override fun photoCounts(): Flow<Map<String, Int>> = emptyFlow()
}

/**
 * Collects [counts] in [scope] for as long as the scope lives, handing each map to [write]. A failed read is logged and
 * counting starts again on its own after [entryReferenceCountRetryDelay]; the last map written stays on screen meanwhile,
 * and the next good read replaces it. Cancelling [scope] (the ViewModel being cleared) ends it, waits included.
 * [what] names the record type in the log line.
 *
 * Amendment 1 accepted "a failed read logs and keeps the last good counts", and this first ended the collection there, so
 * one failure froze the counts until the ViewModel was made again, a restart in practice (Sorrel's review of PR #434, N4).
 * Amendment 2 (RECORD -1784; the owner: "Retry by itself (Recommended)") asked for the retry, logging each failure. A good
 * read resets the wait, so a fault that comes back after a while is retried quickly again. `retryWhen` does not catch
 * cancellation, nor an exception thrown by [write] (the flow's own exception transparency), so neither is retried.
 */
fun <K> watchEntryReferenceCounts(
    scope: CoroutineScope,
    counts: Flow<Map<K, Int>>,
    errorLog: ErrorLog,
    what: String,
    write: (Map<K, Int>) -> Unit,
): Job = scope.launch {
    var failuresInARow = 0
    counts
        .onEach { failuresInARow = 0 }
        .retryWhen { error, _ ->
            failuresInARow++
            val wait = entryReferenceCountRetryDelay(failuresInARow)
            errorLog.w(ENTRY_REFERENCE_COUNTS_TAG, "Couldn't count journal entries keeping each $what; trying again in $wait.", error)
            delay(wait)
            true
        }
        .collect { write(it) }
}

/**
 * How long [watchEntryReferenceCounts] waits after [failuresInARow] failed reads in a row (1 for the first): 1 s, doubling
 * each time, never more than [ENTRY_REFERENCE_COUNT_RETRY_CEILING], so 1, 2, 4, 8, 16, then 30 s for as long as it lasts.
 *
 * Why these numbers (Amendment 2 asked for a bounded backoff and its timing proposed): the database is on the phone, so a
 * failed read is most likely a moment's contention, and 1 s brings the counts back before the user can act on them. A fault
 * that lasts is retried every 30 s, which keeps a count wrong for at most half a minute after it clears and writes two log
 * lines a minute, not a stream. No field data shows either kind of failure yet (CLAUDE.md, "Don't build speculative
 * correction ... without real data"); the ceiling is the one number to revisit if one appears.
 */
fun entryReferenceCountRetryDelay(failuresInARow: Int): Duration =
    (ENTRY_REFERENCE_COUNT_FIRST_RETRY * (1 shl (failuresInARow - 1).coerceIn(0, 5))).coerceAtMost(ENTRY_REFERENCE_COUNT_RETRY_CEILING)

val ENTRY_REFERENCE_COUNT_FIRST_RETRY: Duration = 1.seconds
val ENTRY_REFERENCE_COUNT_RETRY_CEILING: Duration = 30.seconds

const val ENTRY_REFERENCE_COUNTS_TAG = "EntryReferenceCounts"
