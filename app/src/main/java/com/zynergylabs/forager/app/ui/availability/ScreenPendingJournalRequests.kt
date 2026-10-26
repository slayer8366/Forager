package com.zynergylabs.forager.app.ui.availability

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import com.zynergylabs.forager.app.ui.log.PendingJournalDestination

/**
 * The one-shot request into JournalTab and the three record ids that ride with it, read and written through the States
 * [rememberPendingJournalRequests] remembers, never copies of them (Stage G). Built afresh on each composition and not
 * remembered, so it adds no slot.
 */
internal class ScreenPendingJournalRequests(
    private val destinationState: MutableState<PendingJournalDestination?>,
    private val findIdState: MutableState<String?>,
    private val entryIdState: MutableState<String?>,
    private val trackIdState: MutableState<String?>,
) {
    var pendingJournalDestination: PendingJournalDestination?
        get() = destinationState.value
        set(value) { destinationState.value = value }
    var pendingJournalFindId: String?
        get() = findIdState.value
        set(value) { findIdState.value = value }
    var pendingJournalEntryId: String?
        get() = entryIdState.value
        set(value) { entryIdState.value = value }
    var pendingJournalTrackId: String?
        get() = trackIdState.value
        set(value) { trackIdState.value = value }
}

/**
 * Moved out of [AvailabilityScreen] whole by dispatch 2026-10-26-17 (RECORD -1682), a pure move, and called at the line where
 * the group stood, so its four remembers keep their order (-1484).
 */
@Composable
internal fun rememberPendingJournalRequests(): ScreenPendingJournalRequests {
    // Stage 2d's routing fix: a one-shot request into JournalTab, set alongside the compactTab
    // switch — see JournalTab's own doc comment, "The map '+' routing bug," for why this exists and
    // why it stays a single-purpose token rather than a shared navigation type. Only JournalTab reads it.
    val pendingJournalDestinationState = remember { mutableStateOf<PendingJournalDestination?>(null) }
    // M1: the find a PendingJournalDestination.VIEW_FIND request opens, cleared with the request.
    val pendingJournalFindIdState = remember { mutableStateOf<String?>(null) }
    // J8-4: the day entry a PendingJournalDestination.VIEW_ENTRY request opens, cleared with the request.
    val pendingJournalEntryIdState = remember { mutableStateOf<String?>(null) }
    // Plan T16: the imported track a PendingJournalDestination.VIEW_IMPORTED_TRACK request opens, cleared with the request.
    val pendingJournalTrackIdState = remember { mutableStateOf<String?>(null) }
    return ScreenPendingJournalRequests(
        pendingJournalDestinationState,
        pendingJournalFindIdState,
        pendingJournalEntryIdState,
        pendingJournalTrackIdState,
    )
}
