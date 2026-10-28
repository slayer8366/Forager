package com.zynergylabs.forager.app.domain

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emptyFlow
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
 * Collects [counts] in [scope] for as long as the scope lives, handing each map to [write]. A failed read
 * is logged and ends the collection, and the last map written stays on screen: the owner accepted "a failed
 * read logs and keeps the last good counts" (Amendment 1). Before this, a failed one-shot read showed 0;
 * keeping the last good map instead means one failure does not wipe counts that were right a moment ago.
 * [what] names the record type in the log line.
 */
fun <K> watchEntryReferenceCounts(
    scope: CoroutineScope,
    counts: Flow<Map<K, Int>>,
    errorLog: ErrorLog,
    what: String,
    write: (Map<K, Int>) -> Unit,
): Job = scope.launch {
    counts
        .catch { error -> errorLog.w(ENTRY_REFERENCE_COUNTS_TAG, "Couldn't count journal entries keeping each $what; keeping the last counts.", error) }
        .collect { write(it) }
}

const val ENTRY_REFERENCE_COUNTS_TAG = "EntryReferenceCounts"
