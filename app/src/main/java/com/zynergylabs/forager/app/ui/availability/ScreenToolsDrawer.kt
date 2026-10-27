package com.zynergylabs.forager.app.ui.availability

import androidx.compose.material3.DrawerState
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableIntState
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.SoftwareKeyboardController
import androidx.compose.ui.unit.LayoutDirection
import com.zynergylabs.forager.app.ui.log.ScreenEdge

// Dispatch 2026-10-26-17 (RECORD -1682), a pure move: the Tools drawer's state and the three things that keep the animated
// drawer and its open flag in step, moved out of AvailabilityScreen's body. Each is called in the body at the line where it
// was written, so the remembers and the two effects keep their order and keys (-1484). The comments moved with the code;
// "below", "above" and "here" in them mean the screen's body, where these were written.

/**
 * The Tools drawer's state that [rememberScreenToolsDrawer] remembers or reads. The two flags are read and written through
 * the original State objects, never copies of them (Stage G); the rest are passed on as themselves. Built afresh on each
 * composition and not remembered, so it adds no slot.
 */
internal class ScreenToolsDrawer(
    val drawerState: DrawerState,
    val focusManager: FocusManager,
    val keyboardController: SoftwareKeyboardController?,
    val appClearFocusInProgress: BooleanArray,
    private val isDrawerOpenState: MutableState<Boolean>,
    private val openSettingsOnlyRequestState: MutableIntState,
) {
    var isDrawerOpen: Boolean
        get() = isDrawerOpenState.value
        set(value) { isDrawerOpenState.value = value }
    var openSettingsOnlyRequest: Int
        get() = openSettingsOnlyRequestState.intValue
        set(value) { openSettingsOnlyRequestState.intValue = value }
}

/**
 * The animated drawer, the focus manager and keyboard its close hands back through, the app's own clear-focus guard, the
 * open flag and the settings-only request, in the order the body wrote them. The body's `context` read, which sat between
 * the drawer state and the focus manager, stays in the body just after this call; it is a composition-local read, not a slot.
 */
@Composable
internal fun rememberScreenToolsDrawer(): ScreenToolsDrawer {
    val drawerState = rememberDrawerState(DrawerValue.Closed)

    // The authority for "should the drawer be open" — not drawerState.isOpen. DrawerState is an
    // animated slide-in/out; its currentValue only settles to Open/Closed once that animation
    // finishes, so routing back-press decisions off it left a real window, mid-animation, where a
    // rapid open-then-back could still read as closed and fall through to the exit-warning
    // handler below instead of the close-drawer one — a real bug, not a hypothetical, reported
    // against the previous version. isDrawerOpen flips the instant an open/close is requested,
    // synchronously, so both BackHandlers below always route correctly on the very next press no
    // matter where the slide animation currently is. The LaunchedEffect drives the actual
    // animated drawer as a side effect of this flag, and Compose's animateTo cancels and reverses
    // cleanly if the flag flips again before a prior animation finished, so rapid open/close
    // toggling stays responsive rather than queuing up stale animations.
    // Read once here, reused below and inside compactMainScaffold's own showQuickSearch handling —
    // both close points need the same "actually dismiss the IME" fix, not just clear this
    // composable's own idea of which panel is showing. Neither call was made anywhere in this app
    // before this fix (grepped the whole tree) — closing a panel that held focus (the drawer's own
    // search fields, the quick-search species field) unmounts the focused TextField, which doesn't
    // reliably hide the IME on every device/OEM on its own; a stray back press or drawer-close could
    // leave a device's keyboard visibly "stuck" over whatever's shown next.
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    // Dispatch 2026-09-28-312, items 11 and 12. The rule: a focus handed back by the app's own clearFocus never
    // opens the search dropdown. Up only while one of two clearFocus(force = true) calls is running: the Tools
    // drawer's close just below, and the search dropdown's own close in CompactMainScaffold. Out of touch mode (a
    // hardware keyboard, a D-pad) View.clearFocus hands focus to the first focusable view inside that same call;
    // when that is the search field, its focus callback opened the dropdown: Back could never close the dropdown,
    // and closing Tools opened one nobody asked for, which then took the next Back ahead of a fan or a bubble.
    // The scaffold's two onFieldFocused ignore a focus gain while this is up; a focus the user gives the field
    // opens the dropdown as before. No timer: the hand-back is synchronous (traced: clearOwnerFocus,
    // View.clearFocus, rootViewRequestFocus, AndroidComposeView.requestFocus, onFieldFocused, one call stack).
    // The other two clearFocus calls are outside it on purpose: SearchDropdown's, on its own scroll, runs with
    // the dropdown already open, and ReturnPromptState's runs while an entry is open, when the bar is not composed.
    // A one-element array, not snapshot state: nothing draws from it (TwoStageSwipe's holder is the precedent).
    val appClearFocusInProgress = remember { booleanArrayOf(false) }
    val isDrawerOpenState = remember { mutableStateOf(false) }
    // Counts up when the settings-reset snackbar's "Settings" is tapped (RECORD -661): the Tools drawer opens at Settings.
    val openSettingsOnlyRequestState = remember { mutableIntStateOf(0) }
    return ScreenToolsDrawer(
        drawerState = drawerState,
        focusManager = focusManager,
        keyboardController = keyboardController,
        appClearFocusInProgress = appClearFocusInProgress,
        isDrawerOpenState = isDrawerOpenState,
        openSettingsOnlyRequestState = openSettingsOnlyRequestState,
    )
}

/**
 * The effect that drives the animated drawer from the open flag. [isDrawerOpen] is read in the body, as the effect's key was,
 * and passed in; the effect's block is the body's, unchanged.
 */
@Composable
internal fun ToolsDrawerFollowsFlag(isDrawerOpen: Boolean, toolsDrawer: ScreenToolsDrawer) {
    val drawerState = toolsDrawer.drawerState
    val appClearFocusInProgress = toolsDrawer.appClearFocusInProgress
    val focusManager = toolsDrawer.focusManager
    val keyboardController = toolsDrawer.keyboardController
    LaunchedEffect(isDrawerOpen) {
        if (isDrawerOpen) {
            drawerState.open()
        } else {
            drawerState.close()
            // Reset to the Search panel on every close — scrim tap, back button, or a search action
            // that closes the drawer itself — rather than leaving Settings showing the next time the
            // drawer opens. A minor, easily-revisited default: see this task's own notes.
            appClearFocusInProgress[0] = true
            try {
                focusManager.clearFocus(force = true)
            } finally {
                appClearFocusInProgress[0] = false
            }
            keyboardController?.hide()
        }
    }
}

/**
 * The effect that lowers the open flag after a close the drawer made itself. The flag is read and written through
 * [toolsDrawer], when the drawer settles, as the body's effect read and wrote it.
 */
@Composable
internal fun ToolsDrawerFollowsSettledClose(toolsDrawer: ScreenToolsDrawer) {
    val drawerState = toolsDrawer.drawerState
    var isDrawerOpen by toolsDrawer::isDrawerOpen
    // Landscape B3 (P12 as corrected by R2; owner ruling 1 in
    // docs/audits/2026-09-27-landscape-b3-prebuild-report.md, section 5). With gestures on
    // while the drawer is open, M3's scrim tap and swipe-to-close call drawerState.close()
    // themselves and never touch isDrawerOpen, the authority for "should the drawer be open"
    // (its comment, above). Left alone, the flag stays true after such a close: the drawer is
    // visibly shut, but Back is still routed to the close-drawer handler and Tools sets a flag
    // that is already true, so the drawer will not open again (seen in CompactToolsDrawerTest
    // with only the gesturesEnabled change applied). So the flag follows a close the drawer
    // made itself, once that close has settled. Only the settled-closed edge is acted on: a
    // just-requested open (flag true, drawer still closed and idle) emits nothing, because
    // the watched value has not changed.
    LaunchedEffect(drawerState) {
        snapshotFlow { drawerState.currentValue == DrawerValue.Closed && !drawerState.isAnimationRunning }
            .collect { settledClosed -> if (settledClosed && isDrawerOpen) isDrawerOpen = false }
    }
}

/**
 * The layout direction the drawer is anchored with. [ambientDirection] is the body's `LocalLayoutDirection` read, which stays
 * in the body because the drawer host reads it too.
 */
internal fun toolsDrawerDirection(isLandscapeWindow: Boolean, portEdge: ScreenEdge, ambientDirection: LayoutDirection): LayoutDirection {
    // Landscape B3 (P12, owner ruling 2, "Flip layout direction"): in a short landscape window
    // the drawer opens from the rail side, the port edge. ModalNavigationDrawer has no edge
    // parameter; it anchors to the start edge of LocalLayoutDirection. So the direction is
    // set around the drawer to the one whose start edge is the port edge, and the ambient
    // direction is restored inside both the sheet's content and the screen content, so only
    // the drawer's anchoring changes. Portrait and every non-short window keep the ambient
    // direction untouched. The mapping is physical (port on the right -> Rtl, on the left ->
    // Ltr), not "the opposite of ambient": in an RTL locale the drawer already starts on the
    // right, and flipping it would move it away from the rail. With the app's LTR locale
    // this is exactly "flip at ROTATION_90, unchanged at ROTATION_270". See the B3 drawer
    // completion report for that choice, which is open for the planner to confirm.
    val drawerDirection = if (isLandscapeWindow) {
        if (portEdge == ScreenEdge.Right) LayoutDirection.Rtl else LayoutDirection.Ltr
    } else {
        ambientDirection
    }
    return drawerDirection
}
