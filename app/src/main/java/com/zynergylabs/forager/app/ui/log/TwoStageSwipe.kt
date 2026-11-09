package com.zynergylabs.forager.app.ui.log

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import com.zynergylabs.forager.app.ui.motion.PressHighlight
import com.zynergylabs.forager.app.ui.motion.ShapedPressDefaultShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.gestures.AnchoredDraggableDefaults
import androidx.compose.foundation.gestures.AnchoredDraggableState
import androidx.compose.foundation.gestures.DraggableAnchors
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.anchoredDraggable
import androidx.compose.foundation.gestures.animateTo
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.snapTo
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

/**
 * Where a [TwoStageSwipeRow] rests (journal redesign J4b, the owner's "slide once to reveal an edit
 * button, and slide again to delete"): [Closed]; [Open], the row slid part way with its actions
 * showing behind it; [Delete], slid its whole width, which asks for the delete.
 */
internal enum class SwipeRevealValue { Closed, Open, Delete }

/**
 * The rows of one list that open by swiping, so that only one is open at a time (the continuation
 * dispatch `prompts/preserved/2026-09-27-23.md`: "only one card is open at a time"). A row that
 * settles [SwipeRevealValue.Open] becomes [openKey]; every other row of the group closes when it
 * sees a different key. [swipeRevealTouchWatcher] on the list closes the open row when a touch lands
 * outside it.
 */
@Stable
internal class SwipeRevealGroup {
    /** The key of the one open row, or `null`. */
    var openKey: Any? by mutableStateOf(null)
        internal set

    private val bounds = mutableMapOf<Any, Rect>()

    /** Closes whichever row is open (a scroll of the list, for one). */
    fun closeAll() {
        openKey = null
    }

    internal fun setBounds(key: Any, rect: Rect) {
        bounds[key] = rect
    }

    internal fun forget(key: Any) {
        bounds.remove(key)
        if (openKey == key) openKey = null
    }

    /** A touch went down at [position] (root coordinates): the open row closes unless the touch is on it. */
    internal fun onDownInRoot(position: Offset) {
        val key = openKey ?: return
        val rowBounds = bounds[key]
        if (rowBounds == null || !rowBounds.contains(position)) openKey = null
    }
}

@Composable
internal fun rememberSwipeRevealGroup(): SwipeRevealGroup = remember { SwipeRevealGroup() }

/**
 * On a list of [TwoStageSwipeRow]s: a touch going down anywhere outside the open row closes it (the
 * continuation's "a tap elsewhere ... closes a revealed card"). It watches in the initial pass and
 * consumes nothing, so the touch still does what it does (a tap on another card still opens that
 * card, a drag still scrolls): the open row only closes. Rejected: swallowing that first tap, as
 * some mail apps do, which would make a tap on a visible, ordinary card do nothing for no reason the
 * user can see.
 */
@Composable
internal fun Modifier.swipeRevealTouchWatcher(group: SwipeRevealGroup): Modifier {
    val holder = remember { arrayOfNulls<LayoutCoordinates>(1) }
    return this
        .onGloballyPositioned { holder[0] = it }
        .pointerInput(group) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                val coordinates = holder[0]
                if (coordinates != null && coordinates.isAttached) group.onDownInRoot(coordinates.localToRoot(down.position))
            }
        }
}

/**
 * A list row that swipes in two stages (journal redesign J4b; owner ruling "Lists swipe, grids
 * long-press (Recommended)"): a short end-to-start swipe settles it **open**, showing [onEdit]'s Edit
 * button (when the record has an edit path) and a Delete button behind it; a **full** swipe, past
 * half the distance from open to the row's whole width, deletes directly. Both Delete routes call
 * [onDelete], which asks the owning ViewModel for a *pending* delete (J4's delayed delete and Undo
 * snackbar); nothing is deleted here.
 *
 * - **Why this API.** Material 3's `SwipeToDismissBox` (J4's `SwipeToDeleteRow`) only dismisses; it
 *   has no resting open state. Foundation 1.12's [AnchoredDraggableState] with three anchors
 *   (closed at 0, open at minus the actions' width, delete at minus the row's width) is the
 *   component `SwipeToDismissBox` is itself built on, so the drag, fling and settle behave the same
 *   way J4's rows did, with one more place to rest.
 * - **End to start only**, mirrored in right-to-left layouts (`reverseDirection` on the drag, and
 *   [Modifier.offset], which mirrors itself): every anchor is at or before zero, so a swipe the other
 *   way does nothing.
 * - **Open.** The row's content slides by the actions' width and the actions behind it are
 *   ordinary buttons, reachable by touch. A tap on the open row's own content closes it and does
 *   not reach the content (an overlay takes that tap), so a revealed card does not open its entry;
 *   a swipe back closes it; opening another row of the same [group] closes this one; a touch
 *   elsewhere on the list closes it ([swipeRevealTouchWatcher]).
 * - **The actions are composed only while the row is off zero**, so a row at rest carries no Edit
 *   or Delete control of its own (what J4b L5's test pins for the region row).
 * - **Accessibility.** The row carries custom accessibility actions, "Edit" (when [onEdit] is set)
 *   and "Delete", making the same calls as the buttons, so TalkBack reaches both without the gesture
 *   (J4's "Delete" action, kept, with "Edit" beside it).
 *
 * Callers compose one of these per record inside `key(record id)`, as J4 required of its rows: the
 * swipe state is remembered per row. [testTag] is on the node that carries the custom actions and the
 * drag; the buttons are tagged [twoStageSwipeEditTag] and [twoStageSwipeDeleteTag] of it.
 */
@Composable
internal fun TwoStageSwipeRow(
    testTag: String,
    rowKey: Any,
    group: SwipeRevealGroup,
    onDelete: () -> Unit,
    onEdit: (() -> Unit)?,
    modifier: Modifier = Modifier,
    /**
     * The shape of what [content] draws, for the press highlight of the tap that closes an open row (motion Part 1, dispatch
     * 2026-09-28-652 item 3, scout J16; the owner, RECORD -651: "Yes, round them all"). Rounded by default since Amendment 1
     * (RECORD -657), so a plain list row's highlight is rounded too; a card passes its own shape (the entry list, the waypoint
     * list). Drawing only: the close tap still covers the whole row.
     */
    highlightShape: Shape = ShapedPressDefaultShape,
    content: @Composable () -> Unit,
) {
    val currentOnDelete by rememberUpdatedState(onDelete)
    val currentOnEdit by rememberUpdatedState(onEdit)
    val hasEdit = onEdit != null
    val density = LocalDensity.current
    val isRtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val scope = rememberCoroutineScope()
    val state = remember { AnchoredDraggableState(SwipeRevealValue.Closed) }
    val actionWidthPx = with(density) { SWIPE_ACTION_WIDTH.toPx() }
    val actionCount = if (hasEdit) 2 else 1

    // Where the row came to rest decides what happens: open joins the group, a full swipe deletes.
    LaunchedEffect(state, rowKey) {
        snapshotFlow { state.settledValue }.collect { value ->
            when (value) {
                SwipeRevealValue.Open -> group.openKey = rowKey
                SwipeRevealValue.Closed -> if (group.openKey == rowKey) group.openKey = null
                SwipeRevealValue.Delete -> {
                    if (group.openKey == rowKey) group.openKey = null
                    currentOnDelete()
                    // The row usually leaves the list at once (the pending record is hidden); if it
                    // stays, or comes back on Undo, it is closed, never stuck slid away.
                    state.snapTo(SwipeRevealValue.Closed)
                }
            }
        }
    }
    // Another row of the group opened, or the list closed its open row: this one closes.
    LaunchedEffect(group, rowKey) {
        snapshotFlow { group.openKey }.collect { key ->
            if (key != rowKey && state.settledValue == SwipeRevealValue.Open) state.animateTo(SwipeRevealValue.Closed)
        }
    }
    DisposableEffect(group, rowKey) { onDispose { group.forget(rowKey) } }

    fun close() {
        scope.launch { state.animateTo(SwipeRevealValue.Closed) }
    }

    val flingBehavior = AnchoredDraggableDefaults.flingBehavior(state = state, positionalThreshold = { distance -> distance * 0.5f })
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clipToBounds()
            .onSizeChanged { size ->
                val width = size.width.toFloat()
                // Never more than 60% of the row, so the delete anchor stays well beyond the open
                // one on a narrow card (two columns in a short window).
                val reveal = min(actionWidthPx * actionCount, width * MAX_REVEAL_FRACTION)
                state.updateAnchors(
                    DraggableAnchors {
                        SwipeRevealValue.Closed at 0f
                        SwipeRevealValue.Open at -reveal
                        SwipeRevealValue.Delete at -width
                    },
                )
            }
            .onGloballyPositioned { group.setBounds(rowKey, it.boundsInRoot()) }
            .testTag(testTag)
            .semantics {
                customActions = listOfNotNull(
                    if (hasEdit) {
                        CustomAccessibilityAction(EDIT_ACTION_LABEL) {
                            currentOnEdit?.invoke()
                            true
                        }
                    } else {
                        null
                    },
                    CustomAccessibilityAction(DELETE_ACTION_LABEL) {
                        currentOnDelete()
                        true
                    },
                )
            }
            .anchoredDraggable(
                state = state,
                orientation = Orientation.Horizontal,
                reverseDirection = isRtl,
                flingBehavior = flingBehavior,
            ),
    ) {
        val offset = state.offset.takeUnless { it.isNaN() } ?: 0f
        if (offset < 0f) {
            val exposedWidth = with(density) { (-offset).toDp() }
            val revealWidth = SWIPE_ACTION_WIDTH * actionCount
            Row(modifier = Modifier.matchParentSize(), horizontalArrangement = Arrangement.End) {
                Row(modifier = Modifier.fillMaxHeight().width(max(exposedWidth.value, revealWidth.value).dp)) {
                    if (hasEdit) {
                        SwipeActionButton(
                            label = EDIT_ACTION_LABEL,
                            icon = Icons.Filled.Edit,
                            container = MaterialTheme.colorScheme.secondaryContainer,
                            content = MaterialTheme.colorScheme.onSecondaryContainer,
                            onClick = {
                                close()
                                currentOnEdit?.invoke()
                            },
                            modifier = Modifier.width(SWIPE_ACTION_WIDTH).testTag(twoStageSwipeEditTag(testTag)),
                        )
                    }
                    SwipeActionButton(
                        label = DELETE_ACTION_LABEL,
                        icon = Icons.Filled.Delete,
                        container = MaterialTheme.colorScheme.errorContainer,
                        content = MaterialTheme.colorScheme.onErrorContainer,
                        onClick = {
                            scope.launch { state.snapTo(SwipeRevealValue.Closed) }
                            currentOnDelete()
                        },
                        modifier = Modifier.weight(1f).testTag(twoStageSwipeDeleteTag(testTag)),
                    )
                }
            }
        }
        Box(
            modifier = Modifier
                .offset { IntOffset(offset.roundToInt(), 0) }
                .background(MaterialTheme.colorScheme.surface),
        ) {
            content()
            if (state.settledValue == SwipeRevealValue.Open || state.targetValue == SwipeRevealValue.Open) {
                // A tap on the revealed row's own body closes it and goes no further. Its press is drawn in the row's own
                // shape ([highlightShape]); the tap box is the whole row, as before.
                val closeInteraction = remember { MutableInteractionSource() }
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .clickable(interactionSource = closeInteraction, indication = null, onClickLabel = CLOSE_ACTIONS_LABEL, onClick = ::close),
                )
                PressHighlight(interactionSource = closeInteraction, shape = highlightShape)
            }
        }
    }
}

@Composable
private fun SwipeActionButton(
    label: String,
    icon: ImageVector,
    container: Color,
    content: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxHeight()
            .background(container)
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, contentDescription = null, tint = content)
            Text(label, style = MaterialTheme.typography.labelMedium, color = content)
        }
    }
}

/** One revealed action's width; two of them (Edit and Delete) are how far a short swipe opens a row. */
private val SWIPE_ACTION_WIDTH = 72.dp

/** The most of a row's width the open state may take, keeping the delete anchor clear of it. */
private const val MAX_REVEAL_FRACTION = 0.6f

/** The custom accessibility action's label for Edit on a two-stage swipe row, beside [DELETE_ACTION_LABEL]. */
internal const val EDIT_ACTION_LABEL = "Edit"

private const val CLOSE_ACTIONS_LABEL = "Close actions"

internal fun twoStageSwipeEditTag(rowTag: String): String = "$rowTag-edit"
internal fun twoStageSwipeDeleteTag(rowTag: String): String = "$rowTag-delete"
