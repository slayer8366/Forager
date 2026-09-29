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
 * fast-out-slow-in: the owner ruled the duration and not the curve.
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
 * Back folds an open fan, before anything else Back would close (the owner's rule 5). The handler is
 * composed **only while the fan is open**: a `BackHandler` composed later is asked first, so this one
 * sits above every handler already on the screen, whether or not that handler is enabled yet (a
 * bubble's own is always composed and enabled when its bubble shows, which happens after the fan
 * opens). `MarkerFanOutHostTest` asserts that order against a real dispatcher.
 */
@Composable
fun MarkerFanOutBackHandler(state: MarkerFanOutState) {
    if (state.isOpen) {
        BackHandler { state.fold() }
    }
}
