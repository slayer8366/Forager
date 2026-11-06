package com.zynergylabs.forager.app.ui.motion

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsPropertyKey
import androidx.compose.ui.semantics.SemanticsPropertyReceiver
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntSize

/*
 * Motion Part 3, item 2 (dispatch 2026-09-28-676; the owner, RECORD -651: Lists "Slide and close up (Recommended)": "Removed rows
 * fade and shrink, the rest glide up, Undo reverses, and new or reordered rows glide into place").
 *
 * **How a removed row stays to leave.** A list here draws whatever its caller's list holds, so a deleted row is simply absent from
 * the next one. [rememberListRows] keeps each row that has gone missing in the list it hands back, where it was, marked as leaving,
 * until its exit has played ([ListRowMotion] fades and shrinks it); the rows below close up as its height goes, which is the glide
 * up. If the row comes back while it is leaving (Undo), it is the same row turned round: it grows back from where it had got to.
 * A row new to the list fades in and grows, pushing the rest down. A lazy list adds its own placement glide on top
 * (`Modifier.animateItem` at the call site, on [MotionTokens.listRowSpec]) so a row that moves to a new place glides there.
 *
 * **What it never animates.** The first rows a list shows (a list opening, or loading into an empty screen), and every change after
 * [resetKey] changes (the caller's way to say "this is a different list now", such as a different filter): those appear at once,
 * as before. Under reduced motion ([LocalReduceMotion]) nothing is kept and nothing grows: rows come and go at once.
 *
 * **A leaving row takes no touch** ([leavingTakesNoTouches]) and is gone from the semantics tree but for its marker, from its first
 * leaving frame, so a tap on it reaches nothing and a screen reader does not find a row that is no longer in the list. A swipe row
 * that leaves is wrapped, not changed: its own drag and its snap shut are exactly as before.
 */

/** Whether a list row is leaving, readable in tests. */
val ListRowLeavingKey = SemanticsPropertyKey<Boolean>("ListRowLeaving")
var SemanticsPropertyReceiver.listRowLeaving by ListRowLeavingKey

/**
 * One row as a list draws it: its [item], its stable [key], and [visible], the row's own enter and exit state, shared by every
 * [ListRow] for the same key, so a row that leaves and comes back reverses rather than starting again.
 */
class ListRow<T> internal constructor(val key: Any, val item: T, val visible: MutableTransitionState<Boolean>) {
    /** Whether this row is on its way out of the list. */
    val leaving: Boolean get() = !visible.targetState
}

/**
 * [items] as the list should draw them, with each row that has gone missing kept where it was while it leaves ([ListRow.leaving]).
 * Draw every row with [ListRowMotion]. The kept rows are dropped once their exit has played.
 */
@Composable
fun <T> rememberListRows(items: List<T>, key: (T) -> Any, resetKey: Any? = null): List<ListRow<T>> {
    val reduceMotion = LocalReduceMotion.current
    val held = remember { ListRowsHolder<T>() }
    // Bumped when a leaving row has finished, so the list is worked out again without it.
    var gone by remember { mutableIntStateOf(0) }
    val rows = remember(items, resetKey, reduceMotion, gone) {
        held.merge(items, key, animate = !reduceMotion && held.shownBefore && held.resetKey == resetKey)
            .also { held.resetKey = resetKey }
    }
    // A row whose exit has played leaves the list. Keyed on how many rows are leaving and on each one's state, read here so a
    // finished exit is seen.
    val finished = rows.filter { it.leaving && it.visible.isIdle }
    if (finished.isNotEmpty()) {
        LaunchedEffect(finished.map { it.key }) {
            held.drop(finished.map { it.key }.toSet())
            gone++
        }
    }
    return rows
}

/** The rows [rememberListRows] keeps between compositions. Plain, not snapshot state: it is read and written in one place. */
internal class ListRowsHolder<T> {
    private var rows: List<ListRow<T>> = emptyList()
    var shownBefore = false
        private set
    var resetKey: Any? = null

    fun merge(items: List<T>, key: (T) -> Any, animate: Boolean): List<ListRow<T>> {
        rows = mergeListRows(previous = rows, items = items, key = key, animate = animate)
        if (items.isNotEmpty()) shownBefore = true
        return rows
    }

    fun drop(keys: Set<Any>) {
        rows = rows.filterNot { it.key in keys && it.leaving }
    }
}

/**
 * The merge behind [rememberListRows], pure so it is tested on its own. Every key in [items] is a row, in [items]' order, reusing
 * its earlier visibility state when it had one (and turning it back to visible: Undo). Every earlier row whose key is no longer in
 * [items] is kept, leaving, right after the row it followed before (or first, if nothing before it is still in the list), when
 * [animate]; without [animate], missing rows are dropped and new rows start visible, so nothing plays.
 */
internal fun <T> mergeListRows(previous: List<ListRow<T>>, items: List<T>, key: (T) -> Any, animate: Boolean): List<ListRow<T>> {
    val keys = items.mapTo(HashSet(), key)
    val before = previous.associateBy { it.key }
    // Each leaving row, filed under the nearest earlier row that stays (null: nothing earlier stays).
    val leavingAfter = LinkedHashMap<Any?, MutableList<ListRow<T>>>()
    if (animate) {
        var lastStaying: Any? = null
        for (row in previous) {
            if (row.key in keys) {
                lastStaying = row.key
            } else {
                row.visible.targetState = false
                leavingAfter.getOrPut(lastStaying) { mutableListOf() } += ListRow(row.key, row.item, row.visible)
            }
        }
    }
    val result = ArrayList<ListRow<T>>(items.size + leavingAfter.values.sumOf { it.size })
    leavingAfter[null]?.let(result::addAll)
    for (item in items) {
        val k = key(item)
        val earlier = before[k]
        val visible = if (earlier != null && animate) {
            earlier.visible.also { it.targetState = true }
        } else {
            // New to the list: grows in when animating; otherwise simply there.
            MutableTransitionState(!animate || earlier != null).also { it.targetState = true }
        }
        result += ListRow(k, item, visible)
        leavingAfter[k]?.let(result::addAll)
    }
    return result
}

/** How a row leaves and arrives: a list row shrinks to nothing in height; a grid tile shrinks in place, and the grid closes up after. */
enum class ListRowShape { ROW, TILE }

/**
 * Draws [row] with its enter and exit ([rememberListRows]): a [ListRowShape.ROW] fades and grows in height, a [ListRowShape.TILE]
 * fades and grows from its centre; both reverse to leave, on [MotionTokens.listRowSpec]. Leaving, it takes no touch and is gone
 * from the semantics tree but for its marker. Under reduced motion it appears and goes at once.
 */
@Composable
fun <T> ListRowMotion(
    row: ListRow<T>,
    modifier: Modifier = Modifier,
    shape: ListRowShape = ListRowShape.ROW,
    content: @Composable (T) -> Unit,
) {
    val reduceMotion = LocalReduceMotion.current
    val fade = MotionTokens.listRowSpec<Float>()
    val size = MotionTokens.listRowSpec<IntSize>()
    val enter: EnterTransition
    val exit: ExitTransition
    when {
        reduceMotion -> {
            enter = EnterTransition.None
            exit = ExitTransition.None
        }
        shape == ListRowShape.ROW -> {
            enter = fadeIn(animationSpec = fade) + expandVertically(animationSpec = size)
            exit = fadeOut(animationSpec = fade) + shrinkVertically(animationSpec = size)
        }
        else -> {
            enter = fadeIn(animationSpec = fade) + scaleIn(animationSpec = fade, initialScale = LIST_TILE_ENTER_SCALE)
            exit = fadeOut(animationSpec = fade) + scaleOut(animationSpec = fade, targetScale = LIST_TILE_ENTER_SCALE)
        }
    }
    NoTouchTargetExpansion(active = row.leaving) {
        AnimatedVisibility(
            visibleState = row.visible,
            modifier = modifier
                .leavingTakesNoTouches(row.leaving)
                .then(if (row.leaving) Modifier.clearAndSetSemantics { listRowLeaving = true } else Modifier.semantics { listRowLeaving = false }),
            enter = enter,
            exit = exit,
            label = "listRow",
        ) { content(row.item) }
    }
}

/** Where a grid tile grows from as it arrives, and shrinks to as it leaves. Chosen, not measured, like the pop-ups' scale. */
private const val LIST_TILE_ENTER_SCALE = 0.85f

/**
 * Something that arrives late inside a page grows in like a list row (motion Part 3, Amendment 2, RECORD -682, the planner's call;
 * scout E2, E3: an entry's map and its offline-map row): it fades in and opens to its height on [MotionTokens.listRowSpec], pushing
 * what is below it down smoothly instead of in one frame. With [shrinkOut] it closes the same way when it goes; without, it goes at
 * once. Already [visible] when first composed, it is simply there. Under reduced motion it appears and goes at once.
 *
 * The height opens by clipping, not by measuring: what is inside is measured at its full size from the first frame, so a hosted
 * map is not re-measured as it grows (`expandVertically` lays its child out at the full size and animates only its own size; my
 * reading of the library, not run).
 */
@Composable
fun GrowIn(
    visible: Boolean,
    modifier: Modifier = Modifier,
    shrinkOut: Boolean = true,
    content: @Composable () -> Unit,
) {
    val reduceMotion = LocalReduceMotion.current
    val fade = MotionTokens.listRowSpec<Float>()
    val size = MotionTokens.listRowSpec<IntSize>()
    val state = remember { MutableTransitionState(visible) }
    state.targetState = visible
    AnimatedVisibility(
        visibleState = state,
        modifier = modifier,
        enter = if (reduceMotion) EnterTransition.None else fadeIn(animationSpec = fade) + expandVertically(animationSpec = size),
        exit = if (reduceMotion || !shrinkOut) ExitTransition.None else fadeOut(animationSpec = fade) + shrinkVertically(animationSpec = size),
        label = "growIn",
    ) { content() }
}
