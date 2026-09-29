package com.zynergylabs.forager.app.ui.log

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver

/**
 * The "Welcome back" return prompt's state (pending-edit-and-fixes dispatch, Item 1), moved out of
 * `CartographyScreen` unchanged in behaviour so it has an entry point a test can drive with a real
 * Activity lifecycle (Part 2 follow-ups F1, item 8). See [rememberReturnPromptState] for when it shows.
 */
@Stable
internal class ReturnPromptState(backgroundedWhileDirty: Boolean, showReturnPrompt: Boolean) {
    internal var backgroundedWhileDirty by mutableStateOf(backgroundedWhileDirty)
    var showReturnPrompt by mutableStateOf(showReturnPrompt)
}

/**
 * Backgrounding must not commit a dirty committed entry; on return the user is asked instead. The two
 * flags are saveable so a real Activity recreation while the app is backgrounded (a config change, or
 * process death short of losing the process outright) still shows the prompt on return.
 *
 * On ON_STOP with the entry dirty (and no photo acquisition in flight, whose camera round trip fires
 * the same events): remembers it was backgrounded. On ON_RESUME: clears focus while an entry is open,
 * and shows the prompt if it was remembered.
 */
@Composable
internal fun rememberReturnPromptState(
    entryDirty: Boolean,
    entryOpen: Boolean,
    photoAcquisitionInFlight: Boolean,
): ReturnPromptState {
    val state = rememberSaveable(saver = ReturnPromptStateSaver) { ReturnPromptState(backgroundedWhileDirty = false, showReturnPrompt = false) }
    val lifecycleOwner = LocalLifecycleOwner.current
    val focusManager = LocalFocusManager.current
    val activity = LocalContext.current.findActivity()
    val latestIsEntryDirty by rememberUpdatedState(entryDirty)
    val latestIsEntryOpen by rememberUpdatedState(entryOpen)
    val latestAcquisitionInFlight by rememberUpdatedState(photoAcquisitionInFlight)
    DisposableEffect(lifecycleOwner, state, activity) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                // Not while the Activity is being rebuilt for a configuration change (a night-mode toggle, a
                // font-scale change): that ON_STOP is the platform tearing the Activity down and standing it up
                // again around the user, who never left the app, and on API 28+ the saved state is taken after
                // it, so the flag set here would carry into the new Activity and show "Welcome back" on a
                // screen the user was looking at (Part 2 Session 2, item 18). A configuration change that lands
                // while the app is in the background has no such ON_STOP (the Activity had already stopped), so
                // that path still asks, as this state's saveable flags were made to.
                Lifecycle.Event.ON_STOP ->
                    if (latestIsEntryDirty && !latestAcquisitionInFlight && activity?.isChangingConfigurations != true) {
                        state.backgroundedWhileDirty = true
                    }
                Lifecycle.Event.ON_RESUME -> {
                    if (latestIsEntryOpen) focusManager.clearFocus(force = true)
                    if (state.backgroundedWhileDirty) {
                        state.showReturnPrompt = true
                        state.backgroundedWhileDirty = false
                    }
                }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    return state
}

private val ReturnPromptStateSaver = androidx.compose.runtime.saveable.Saver<ReturnPromptState, List<Boolean>>(
    save = { listOf(it.backgroundedWhileDirty, it.showReturnPrompt) },
    restore = { ReturnPromptState(backgroundedWhileDirty = it[0], showReturnPrompt = it[1]) },
)
