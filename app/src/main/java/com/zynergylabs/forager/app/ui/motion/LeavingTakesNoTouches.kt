package com.zynergylabs.forager.app.ui.motion

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ProvidedValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.platform.ViewConfiguration
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection

/**
 * For something drawn over the map that animates away: while [leaving], it goes on drawing exactly as it does (its exit
 * animation included) but takes no new touch, so a tap on it lands on what is beneath and none of its buttons can be pressed. The search dropdown (scout item Q5; the owner, RECORD -651: "Fix it in this
 * sweep": "Once it starts closing, taps go beneath it and its buttons stop responding").
 *
 * Put it on the leaving thing's own modifier, outside its exit animation (an `AnimatedVisibility`'s `modifier`), so what it
 * draws includes the shrink and the fade.
 *
 * **How, and why this way.** Compose has no "ignore pointers" switch for a subtree, and a parent cannot hide its children from
 * hit testing except by clipping them: a pointer outside a clipping layer's outline reaches nothing inside it. So while
 * [leaving]:
 * 1. the subtree is clipped to an outline that lies wholly outside it ([OffBoundsOutlineShape]), so no pointer can reach it, which
 *    also hides its drawing;
 * 2. inside that clip, the subtree's drawing is recorded into a graphics layer each frame;
 * 3. outside the clip, that recording is drawn in its place, so the user sees what they saw.
 * While not leaving, nothing is recorded and the subtree draws and takes touches as before, so the open state is unchanged.
 *
 * **Not covered:** a finger already down on one of its buttons when it starts leaving keeps the hit path it found at its down,
 * so (1) cannot stop that one press. A consumer that could would have to sit on this node while it is open too, and any
 * pointer node there takes touches across the panel's whole box, which the open panel does not (it is a `Box` with a
 * background, no `Surface`, so its bare background passes taps to the dismiss scrim beneath): a touch-area change to the open
 * state, which is not this item's to make. Reaching that case needs a second finger, or Back, while the first is held.
 *
 * **Not verified anywhere:** this rests on Compose's hit test skipping the children of a clipping layer for a pointer outside
 * its outline, and on a graphics layer recorded inside a clip being drawable outside it. Both are my understanding of the
 * framework, not read from its source in this session and not run: nothing in this change has been compiled. The tests for
 * Q5 are what will show it.
 */
@Composable
fun Modifier.leavingTakesNoTouches(leaving: Boolean): Modifier {
    val recording = rememberGraphicsLayer()
    return this
        // 3. Draw the recording outside the clip while leaving.
        .drawWithContent {
            drawContent()
            if (leaving) drawLayer(recording)
        }
        // 1. While leaving, clip everything inside to an outline no pointer inside these bounds can be in.
        .graphicsLayer {
            clip = leaving
            shape = if (leaving) OffBoundsOutlineShape else androidx.compose.ui.graphics.RectangleShape
        }
        // 2. While leaving, record what the subtree draws, for (3); otherwise draw it straight through.
        .drawWithContent {
            if (leaving) {
                recording.record { this@drawWithContent.drawContent() }
                drawLayer(recording)
            } else {
                drawContent()
            }
        }
}

/**
 * A one-pixel rectangle up and to the left of the bounds, so its outline contains no point of the bounds (no pointer inside
 * them is in it) and the clip shows nothing. Not an empty outline: what an empty outline does as a clip is left to the platform.
 */
private object OffBoundsOutlineShape : Shape {
    override fun createOutline(size: androidx.compose.ui.geometry.Size, layoutDirection: LayoutDirection, density: Density): Outline =
        Outline.Rectangle(Rect(left = -2f, top = -2f, right = -1f, bottom = -1f))
}

/**
 * While [active], gives [content] a view configuration with no minimum touch target, so nothing inside it is hit "near" a touch
 * that lands outside it. Put it around whatever carries [leavingTakesNoTouches], with the same flag.
 *
 * **Why (found at motion Part 3's build, 2026-10-08, by a failing test and a semantics dump, not by reasoning).** Compose widens
 * the hit area of anything smaller than the minimum touch target (48 dp): a touch that misses a small node can still be given to
 * it "near". That path runs even when the touch misses a clipping layer, so [leavingTakesNoTouches]'s off-bounds clip did not stop
 * a leaving list row once it had shrunk below 48 dp tall: `ListMotionTest`'s lazy-list case saw a touch on a leaving Entries card,
 * 41.7 dp tall at that moment, open it. A row of 76 dp (the waypoint case) was not affected, which is why only one of the two
 * tests caught it. With no minimum, a leaving node and everything in it is hit only inside its own bounds, which the clip already
 * refuses. Not active, the configuration is the caller's, unchanged, so no touch area changes outside a leaving moment.
 */
@Composable
fun NoTouchTargetExpansion(active: Boolean, content: @Composable () -> Unit) {
    val base = LocalViewConfiguration.current
    val none = remember(base) {
        object : ViewConfiguration by base {
            override val minimumTouchTargetSize: DpSize get() = DpSize.Zero
        }
    }
    // One provider call either way, so starting to leave does not rebuild the content.
    val provided: Array<ProvidedValue<*>> = if (active) arrayOf(LocalViewConfiguration provides none) else emptyArray()
    CompositionLocalProvider(*provided, content = content)
}
