package com.zynergylabs.forager.app.ui.availability

// UI split Phase 2.6, E2 (dispatch 2026-10-19-40, RECORD -1484): the main screen's seven Back handlers, moved out of
// AvailabilityScreen's body into one composable. The body calls it unconditionally, at the line where they were, so the seven
// register in the same order at the same point, before the frame's nested handlers; the order is the Back order. It returns
// the exit-navigation prompt's state, remembered here at its old position, because the prompt itself is composed in the body.
// A composable that returns a value is not a restart scope of its own, so what these handlers read is read in the body's
// scope, as before; in the dex it emits no group of its own, so the handlers' slots sit in the body's group exactly as before
// (Sorrel's reading on #352). AvailabilityScreenBackOverlapTest pins the four places where two of them can be enabled at once.

import android.app.Activity
import android.content.Context
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.zynergylabs.forager.app.domain.model.Waypoint
import kotlinx.coroutines.delay

/**
 * The seven handlers, their two remembered flags and the two effects that clear them, in the order they were written in the
 * body. Every state of the body they read or write goes through [state], read when composed (the enabled conditions) or when
 * Back is pressed (the handlers' bodies), as before. [isNavigating] and [navigatingToWaypoint] are the values of the
 * composition, as the body's own handlers captured them.
 */
@Composable
internal fun AvailabilityScreenBackHandlers(
    state: AvailabilityScreenState,
    isNavigating: Boolean,
    navigatingToWaypoint: Waypoint?,
    endWaypointNavigation: (Boolean) -> Unit,
    onMapFullscreenChanged: (Boolean) -> Unit,
    context: Context,
): MutableState<Boolean> {
    // System back navigates one step toward "home" — the Maps tab, chrome visible, drawer closed
    // — before falling through to the exit-confirmation handler at the bottom, rather than exiting
    // from wherever the user happens to be. Nested UI further down the tree (a Journal entry, the
    // Settings offline-maps submenu, the map's own add-action tile) unwinds itself first via its
    // own local BackHandler — see JournalTab's, CompactSettingsTab's, and CompactMapTab's — since
    // Compose's OnBackPressedDispatcher tries the most-recently-composed enabled callback first, so
    // a nested composable's own handler naturally takes priority over these four.
    //
    // Each condition below explicitly excludes the states "more nested" than it, so at most one is
    // ever enabled at once — declaration order isn't what decides precedence, in case that ever
    // stops being true. Verified end to end (not just reasoned about) in
    // AvailabilityScreenBackNavigationTest, including the case only compact width can reach where
    // isDrawerOpen and isMapFullscreen are both true (the drawer's own Search entry point is still
    // reachable while fullscreen — see MapIconBar). Corrected 2026-08-28: named MapIconStack here
    // before that composable was renamed. Navigation-chrome amendment: a fifth step, navigation,
    // sits between "return to the Maps tab" and the exit handler — see its own comment below.
    //
    // Only isDrawerOpen/isMapFullscreen/compactTab drive this — all three are state of this one tree.
    //
    // The drawer opens over whatever tab is showing, and that tab's nested handlers are registered
    // after this one, so they would win while the drawer is open. The Journal's are turned off while
    // it is (JournalTab's backEnabled, intent 2026-09-28-28), so Back closes the drawer there.
    BackHandler(enabled = state.isDrawerOpen()) {
        state.setIsDrawerOpen(false)
    }
    BackHandler(enabled = !state.isDrawerOpen() && state.isMapFullscreen()) {
        state.setIsMapFullscreen(false)
        onMapFullscreenChanged(false)
    }
    // RECORD -871: Back from Records reached by the Tools drawer's "Offline maps" row (see toolsReturn in AvailabilityScreen.kt). The page the
    // user came from goes back under the drawer, its tab and, when it was the Journal, its top tab and chip, and fullscreen if
    // the map was fullscreen, and the drawer reopens; the next Back is the drawer's own handler, above, which closes it on
    // that page. The Journal's own handlers are off while this is pending (JournalTab's backEnabled, through
    // compactMainScaffold), the same "off rather than outranked" the open drawer uses, so Records' chip step does not take
    // this press. The tab handler below excludes it, so at most one of the two is enabled.
    BackHandler(enabled = !state.isDrawerOpen() && state.toolsReturn() != null) {
        val back = state.toolsReturn() ?: return@BackHandler
        state.setToolsReturn(null)
        state.journalScreenState.topTab = back.journalTopTab
        state.journalScreenState.recordsFilter = back.recordsFilter
        state.setCompactTab(back.compactTab)
        state.setSelectedTab(back.selectedTab)
        if (back.mapFullscreen) {
            state.setIsMapFullscreen(true)
            onMapFullscreenChanged(true)
        }
        state.setIsDrawerOpen(true)
    }
    BackHandler(enabled = !state.isDrawerOpen() && !state.isMapFullscreen() && state.compactTab() != CompactTab.MAP && state.toolsReturn() == null) {
        state.setCompactTab(CompactTab.MAP)
        // Keeps the shared ResultsTab-driven state in sync, same as ForagerBottomNav's own tap
        // handler does — see compactTab's own doc comment for why the two exist side by side.
        state.setSelectedTab(ResultsTab.MAP)
    }

    // Navigation is the last thing backed out of before the app closes, and backing out of it
    // asks first (navigation-chrome amendment, owner-directed, found on device). This handler is
    // enabled only once everything nested is unwound — dropdown, drawer, fullscreen, other tab —
    // so a back press while navigating in fullscreen exits fullscreen and does not touch the
    // navigation; the next press asks. Stage one had this in CompactMapTab's own handler with no
    // exclusions, which — being the deepest registered — exited navigation BEFORE fullscreen, the
    // drawer or the dropdown unwound: a user in fullscreen lost the return leg to a press they
    // expected to exit fullscreen. That inversion was the bug this replaces.
    //
    // THIS HANDLER PRODUCES A LOOP ON PURPOSE. Back raises the prompt; back dismisses it; back
    // raises it again. Back can never exit navigation, and — because this handler is enabled
    // whenever the exit handler below would otherwise be — back can no longer close the app from
    // this screen while navigating. Home and the app switcher still work, so nobody is trapped.
    // The reason, in the owner's words: backing out can kill a process a person is relying on.
    // Someone navigating out of the woods must not lose it to a stray press. The user decides when
    // to exit — the HUD's ✕ and the control pill's toggle are the only exits, both deliberate
    // presses, and neither asks. Do not "fix" the loop; do not let a second press confirm (that
    // would be the accidental exit with one extra step: the second press cancels); do not remove
    // this handler without another that consumes back here, or a stray press falls through to
    // the exit handler and drops the user out of the app while a track is recording.
    //
    // Toggle, not raise-only: on a device the open AlertDialog is its own window and consumes
    // back itself (onDismissRequest → keep navigating), so this handler only ever sees the
    // raising press there. Under Robolectric, onBackPressedDispatcher.onBackPressed() reaches the
    // Activity, not the dialog window, so the tests' second press lands here — the toggle makes
    // that path end in the same place. The tests therefore exercise THIS toggle path, not the
    // dialog window's own dismiss; the guarantee (back never exits) holds by either route, but
    // the dialog's own back handling is device-only coverage.
    val showExitNavigationPromptState = remember { mutableStateOf(false) }
    var showExitNavigationPrompt by showExitNavigationPromptState
    // Off while a waypoint is the target: Back has its own step for that, below. With no waypoint chosen it is as it was.
    BackHandler(enabled = !state.isDrawerOpen() && !state.isMapFullscreen() && state.compactTab() == CompactTab.MAP && isNavigating && navigatingToWaypoint == null) {
        showExitNavigationPrompt = !showExitNavigationPrompt
    }
    // Dispatch 2026-09-28-502: navigating to a chosen waypoint, "Back from the navigating HUD stops navigating", with no
    // prompt, and goes back to where Navigate was tapped (Amendment 1). The loop above is the return's; it does not apply
    // here. The same exclusions as the handlers above, so what is drawn on top (the drawer, fullscreen, another tab, and
    // the bubbles and menus registered after this) is backed out of first. A return the waypoint overruled picks up again,
    // and the next Back on the bare map is the loop above, untouched.
    BackHandler(enabled = !state.isDrawerOpen() && !state.isMapFullscreen() && state.compactTab() == CompactTab.MAP && navigatingToWaypoint != null) {
        endWaypointNavigation(true)
    }
    // If navigation ends by any other route while the prompt is up (the recording stopping under
    // it, say), the prompt has nothing left to ask about.
    LaunchedEffect(isNavigating) {
        if (!isNavigating) showExitNavigationPrompt = false
    }
    // The prompt itself is composed in AvailabilityScreen.kt, after this call, once the window's tree is known (map chrome at 80%).

    // "Home": drawer closed, chrome visible, Maps tab selected, not navigating. A second back
    // press within the window actually exits; a lone press just warns. This is a single-Activity
    // app with nothing else back could sensibly navigate to once nothing is nested, so an
    // un-warned single back press (which a pan gesture can graze) would otherwise dump the user
    // straight out. Never reached while navigating — the handler above holds that state, by
    // design (see its comment); this exclusion is what makes the two mutually exclusive, the same
    // way the tab and fullscreen handlers exclude the states nested inside them.
    var backPressedOnce by remember { mutableStateOf(false) }
    BackHandler(enabled = !state.isDrawerOpen() && !state.isMapFullscreen() && state.compactTab() == CompactTab.MAP && !isNavigating) {
        if (backPressedOnce) {
            (context as? Activity)?.finish()
        } else {
            backPressedOnce = true
            Toast.makeText(context, "Tap Back Button Again to Exit", Toast.LENGTH_SHORT).show()
        }
    }
    LaunchedEffect(backPressedOnce) {
        if (backPressedOnce) {
            delay(DOUBLE_BACK_EXIT_WINDOW_MS)
            backPressedOnce = false
        }
    }
    return showExitNavigationPromptState
}
