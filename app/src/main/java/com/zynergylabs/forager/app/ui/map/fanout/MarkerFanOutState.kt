package com.zynergylabs.forager.app.ui.map.fanout

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.zynergylabs.forager.app.ui.motion.isReduceMotionEnabled

/**
 * What a map's fan-out is doing, as plain observable state: which markers are fanned ([members], kept
 * while it folds so they can be drawn folding), how far ([progress], 0 folded to 1 spread), and
 * whether it is meant to be open ([wantOpen]). [MarkerFanOutHost] animates [progress]; the map's tap
 * handler ([MapTapHandler]) opens and folds it.
 *
 * Holds no map object, so the tap logic and the clock are both testable without a `MapView`, which
 * cannot be built under Robolectric.
 */
class MarkerFanOutState {
    var members by mutableStateOf<List<FanMember>>(emptyList())
        internal set

    var progress by mutableFloatStateOf(0f)
        internal set

    var wantOpen by mutableStateOf(false)
        private set

    /** Bumped on every open and fold, so the host's effect restarts on each. */
    var generation by mutableIntStateOf(0)
        private set

    /** Open, or opening: a fan that is folding is not open, and takes no taps. */
    val isOpen: Boolean get() = wantOpen && members.isNotEmpty()

    /** Fans [members] out from wherever they are now. A fan still folding is replaced, not resumed. */
    fun open(members: List<FanMember>) {
        this.members = members
        progress = 0f
        wantOpen = true
        generation++
    }

    /** Starts folding; a no-op when nothing is open. The members stay until [release]. */
    fun fold() {
        if (!wantOpen) return
        wantOpen = false
        generation++
    }

    /** Lets go of the members once they are folded home (the host calls this), or at once in a test. */
    fun release() {
        members = emptyList()
        progress = 0f
        wantOpen = false
    }
}

/**
 * The clock for [state]: moves its progress to 1 when it opens and back to 0 when it folds, each over
 * [FAN_DURATION_MS], then releases it. With the system's animations off ([isReduceMotionEnabled], the
 * owner's "spread out at once") the progress jumps to its end. The easing is the standard
 * fast-out-slow-in: the owner chose the duration and not the curve.
 */
@Composable
fun MarkerFanOutHost(state: MarkerFanOutState) {
    val contentResolver = LocalContext.current.contentResolver
    LaunchedEffect(state.generation) {
        if (state.members.isEmpty()) return@LaunchedEffect
        val target = if (state.wantOpen) 1f else 0f
        if (isReduceMotionEnabled(contentResolver)) {
            state.progress = target
        } else {
            Animatable(state.progress).animateTo(target, tween(FAN_DURATION_MS, easing = FastOutSlowInEasing)) {
                state.progress = value
            }
        }
        if (!state.wantOpen) state.release()
    }
}

/**
 * Back folds an open fan, before anything else Back would close (the owner's choice, dispatch 2026-09-28-197). The handler is
 * composed **only while the fan is open**: a `BackHandler` composed later is asked first, so this one
 * sits above every handler already on the screen, whether or not that handler is enabled yet (a
 * bubble's own is always composed and enabled when its bubble shows, which can happen before the fan
 * opens: the way back from a find's page brings both. A tap on a fanned icon no longer leaves a bubble and a
 * fan up together, dispatch 2026-09-28-381, and a tap on a stack closes a showing bubble, continuation -383). `MarkerFanOutHostTest` asserts that order against a real dispatcher.
 *
 * **Except while a bubble shows ([bubbleOpen]):** then the bubble goes first and the fan second (dispatch
 * 2026-09-29-57, item 7, amendment -255, which supersedes the earlier "fan before bubble"). The handler is
 * simply not composed while the bubble shows, so the bubble's own handler is the one asked; once the bubble
 * closes it is composed again, later than every handler on the screen, and the next Back folds the fan.
 * `AvailabilityScreenFanBubbleDismissalTest` asserts the order against the real screen and dispatcher.
 *
 * **And except while the Tools drawer is open ([backEnabled] false):** the drawer goes first and the fan
 * stays open (dispatch 2026-09-28-293, the owner: "When a fan is spread out and I call the tool panel,
 * hitting the back button closes the fan instead of the tool panel, when it's expected to close the tool
 * panel"). Same mechanism as [bubbleOpen]: not composed, so the drawer's own handler is the one asked, and
 * composed again once the drawer closes, later than everything else, so the next Back folds the fan. This is
 * deliberately not an always-composed `BackHandler(enabled = ...)`: that would sit where it was first
 * composed, below the handlers composed since, which is the order the paragraph above exists to avoid.
 * `AvailabilityScreenFanBackDrawerTest` asserts it against the real screen and dispatcher.
 *
 * **And the same for fullscreen, the add-action menu and the pin pickers ([backEnabled] false while any is up):** a
 * "Log a find" or trip pick, and the search dropdown's "Set on map". The owner, dispatch 2026-09-28-298: "apply the
 * change to the other Back cases". Back closes that thing and the fan stays open; the next Back folds the fan. The
 * gate is computed where each one's state lives, `AvailabilityCompactScaffold` for the drawer, fullscreen and the
 * search dropdown, `CompactMapTab` for the menu and the pickers. `AvailabilityScreenFanBackOthersTest` asserts each
 * of -298's against the real screen and dispatcher.
 *
 * **And for the search dropdown (dispatch 2026-09-28-312, item 12):** the owner, "Yes dropdown should go away first
 * when hitting back". Its term waited, under continuation -303, on the dropdown staying closed after Back in the
 * Robolectric harness; the scaffold's focus fix ended that. `AvailabilityScreenBubbleAndDropdownBackTest` asserts it.
 * **Not covered: the taxon suggestions list**, which keeps the Back order it had. The bubble, the Layers sheet and
 * the navigation-exit dialog are unchanged: a bubble still closes before the fan, the drawer before both, the
 * sheet and the dialog in their own windows.
 */
@Composable
fun MarkerFanOutBackHandler(state: MarkerFanOutState, bubbleOpen: Boolean = false, backEnabled: Boolean = true) {
    if (state.isOpen && !bubbleOpen && backEnabled) {
        BackHandler { state.fold() }
    }
}
