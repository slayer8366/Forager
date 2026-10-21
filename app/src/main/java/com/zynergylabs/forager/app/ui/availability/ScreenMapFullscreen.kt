package com.zynergylabs.forager.app.ui.availability

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

/** The Maps tab's fullscreen flag, read and written through the State [rememberMapFullscreen] remembers. */
internal class MapFullscreenFlag(private val state: MutableState<Boolean>) {
    var isMapFullscreen: Boolean
        get() = state.value
        set(value) { state.value = value }
}

/**
 * The Maps tab's fullscreen flag and its one-time restore from the persisted value. Moved out of [AvailabilityScreen] whole by nav step 2, PR 4a (dispatch 2026-10-21-01, RECORD -1519), and called at the line
 * where the group stood, so its remembers and effects keep their order and keys (-1484). The holder wraps the original
 * State objects, never copies of them (Stage G).
 */
@Composable
internal fun rememberMapFullscreen(uiState: AvailabilityUiState): MapFullscreenFlag {
    // Local remembered state, same reasoning as selectedTab below: purely a display
    // decision the ViewModel has no part in. The compact map icon stack's fullscreen toggle sets
    // this — see CompactMapTab's call site. Only reachable while on the
    // Maps tab (the toggle icon lives in that tab's own icon stack), so this being true while
    // selectedTab != MAP cannot happen in practice.
    val isMapFullscreenState = remember { mutableStateOf(false) }
    var isMapFullscreen by isMapFullscreenState
    // Persisted across restarts (persist-fullscreen dispatch — see
    // MapPreferencesRepository.getMapFullscreen for the precedent and its reasoning). The loaded
    // value is applied exactly once, when it first arrives, and only if it arrived at all (a
    // failed read leaves it null and the screen starts as it always did). Two load-bearing
    // choices here:
    //
    // 1. Writes happen at the three user-driven mutation sites (the toggle below, the fullscreen
    //    BackHandler, the tab handler's exit) — never from a LaunchedEffect on isMapFullscreen.
    //    Such an effect would fire on first composition with the default `false` and write it
    //    before the DataStore read lands, clobbering a persisted `true`: a data-loss bug that
    //    would present as "the feature doesn't work" and be hard to trace.
    // 2. The read is asynchronous, so the first frame composes with `false`; when a persisted
    //    `true` lands the search bar, nav and cluster slide away rather than starting hidden.
    //    Chosen, not overlooked (owner's call): the alternatives were gating the map's first
    //    composition on a disk read, or recreating the map once loaded, which would re-fit it —
    //    the very thing the "nothing may re-fit the map" rule exists to protect. A DataStore
    //    first read is a few milliseconds, typically before any tile has appeared.
    var hasAppliedPersistedMapFullscreen by remember { mutableStateOf(false) }
    LaunchedEffect(uiState.persistedMapFullscreen) {
        val persisted = uiState.persistedMapFullscreen
        if (persisted != null && !hasAppliedPersistedMapFullscreen) {
            hasAppliedPersistedMapFullscreen = true
            isMapFullscreen = persisted
        }
    }
    return remember { MapFullscreenFlag(isMapFullscreenState) }
}
