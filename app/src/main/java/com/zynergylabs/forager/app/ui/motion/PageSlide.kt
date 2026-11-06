package com.zynergylabs.forager.app.ui.motion

import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.snap
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ProvidedValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.SemanticsPropertyKey
import androidx.compose.ui.semantics.SemanticsPropertyReceiver
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.navigationevent.compose.LocalNavigationEventDispatcherOwner

/*
 * Motion Part 3, item 1 (dispatch 2026-09-28-676; the owner, RECORD -651: Journal pages "Slide in, slide back (Recommended)":
 * "Opened pages slide in from the right; Back slides them out to the right"). A page you open slides in from the right over the
 * one beneath, which stays where it is; going back, the page slides out to the right and uncovers the one beneath, retracing the
 * way in (CLAUDE.md memory: "Back retraces the way in").
 *
 * **What a leaving page does.** From its first leaving frame, a page that is going (the one being covered on the way in, the one
 * sliding out on the way back) takes no touch ([leavingTakesNoTouches], Part 1), is gone from the semantics tree but for its
 * leaving marker (Part 2's fix (a): a screen reader finds the arriving page alone), and its Back handlers are off (Part 2,
 * Amendment 1, item 5), so a Back pressed mid-slide is the arriving page's. These are the same three things [TabCrossfade] does to
 * a leaving tab, from the same pieces.
 *
 * **The arriving page takes touches where it is drawn.** The slide moves the page by placement, not by drawing alone, because two
 * Journal pages host a live map (the entry report and the find's location picker), and a hosted `View` follows placement, not a
 * drawn offset (see [holdWhileLeaving]). So for the length of a slide-in, the arriving page's controls are where they are drawn,
 * on their way to where they settle. Their settled touch areas are unchanged. This is the one place this part moves a touch area
 * while it moves, and it is reported as a stop for the owner rather than decided here.
 *
 * **The covered page is held by a fade that stays at full until the slide is done** ([pageCoverHold]). The first build showed
 * that with [ExitTransition.None] AnimatedContent drops it at once.
 */

/** Whether a page is the one leaving a [PageSlide] or a [SlideOverPage], readable in tests. */
val PageLeavingKey = SemanticsPropertyKey<Boolean>("PageLeaving")
var SemanticsPropertyReceiver.pageLeaving by PageLeavingKey

/**
 * One page at a time, sliding to the next when [targetState] changes, on [MotionTokens.pageSlideSpec]. Deeper pages
 * ([depthOf] larger) are further in: going to a page at least as deep as the current one slides it in from the right over the
 * current one; going to a shallower one slides the current page out to the right, uncovering it. Pages with the same [contentKey]
 * are one page: a change between them updates it in place, with no slide (an open find whose fields change, for instance).
 *
 * [content] must draw from the page it is handed, never from the caller's current state: while two pages are on screen the leaving
 * one is drawn from its own value. That is why a page carries what it shows (the open entry, say), so a report sliding out after
 * its entry has closed can still be drawn.
 *
 * Every page is drawn on [pageColor], opaque, so the page sliding in covers the one beneath rather than showing it through
 * (the pages themselves draw no background; the screen behind them did, before two of them shared it). The default is what the
 * app's Scaffold draws behind its content. Not for a surface over the map: an opaque page there would break the map chrome's 80%
 * rule, which is why the Tools drawer's pages do not slide (motion Part 3, Amendment 1's stops).
 *
 * Clipped to its own bounds, so a page sliding in from the right is not drawn over whatever sits beside the Journal (the landscape
 * rail). Under reduced motion ([LocalReduceMotion]) a page changes at once and no leaving page is kept (docs/motion-spec.md §4).
 */
@Composable
fun <T> PageSlide(
    targetState: T,
    depthOf: (T) -> Int,
    modifier: Modifier = Modifier,
    contentKey: (T) -> Any? = { it },
    pageColor: Color = MaterialTheme.colorScheme.background,
    style: PageSlideStyle = PageSlideStyle.OVER,
    content: @Composable (T) -> Unit,
) {
    val reduceMotion = LocalReduceMotion.current
    val slide = MotionTokens.pageSlideSpec<IntOffset>()
    val slideAsFloat = MotionTokens.pageSlideSpec<Float>()
    val coverHold = remember(slideAsFloat) { pageCoverHold(slideAsFloat) }
    val inertBack = rememberInertBackOwner()
    val targetKey = contentKey(targetState)
    AnimatedContent(
        targetState = targetState,
        modifier = modifier.clipToBounds(),
        transitionSpec = {
            when (style) {
                PageSlideStyle.OVER -> pageSlideTransform(reduceMotion, depthOf(initialState), depthOf(targetState), slide, coverHold)
                PageSlideStyle.PUSH_FROM_LEFT -> pagePushTransform(reduceMotion, depthOf(initialState), depthOf(targetState), slide)
            }
        },
        contentAlignment = Alignment.TopStart,
        contentKey = contentKey,
        label = "pageSlide",
    ) { page ->
        LeavingPageFrame(leaving = contentKey(page) != targetKey, inertBack = inertBack, pageColor = pageColor) { content(page) }
    }
}

/**
 * The transform [PageSlide] uses between a page at [fromDepth] and one at [toDepth]: in from the right over the current page when
 * going as deep or deeper, the current page out to the right when going back. No size transform: pages fill the same box, and a
 * size animation would re-measure a hosted map every frame. `internal` for its own test.
 */
internal fun pageSlideTransform(
    reduceMotion: Boolean,
    fromDepth: Int,
    toDepth: Int,
    slide: FiniteAnimationSpec<IntOffset>,
    coverHold: FiniteAnimationSpec<Float>,
): ContentTransform = when {
    reduceMotion -> ContentTransform(EnterTransition.None, ExitTransition.None, targetContentZIndex = 0f, sizeTransform = null)
    // In: the new page slides in over the one beneath, which stays still and fully drawn until it is covered ([pageCoverHold]).
    toDepth >= fromDepth -> ContentTransform(
        targetContentEnter = slideInHorizontally(animationSpec = slide) { fullWidth -> fullWidth },
        initialContentExit = fadeOut(animationSpec = coverHold),
        targetContentZIndex = PAGE_ON_TOP,
        sizeTransform = null,
    )
    // Back: the page slides out to the right, drawn over the page it uncovers, which is in place from the first frame.
    else -> ContentTransform(
        targetContentEnter = EnterTransition.None,
        initialContentExit = slideOutHorizontally(animationSpec = slide) { fullWidth -> fullWidth },
        targetContentZIndex = PAGE_UNDERNEATH,
        sizeTransform = null,
    )
}

/**
 * How [PageSlide] moves its pages. [OVER] is the Journal's: a page slides in from the right over the one beneath, which stays
 * still. [PUSH_FROM_LEFT] is the Tools drawer's (motion Part 3, Amendment 2, RECORD -682; the owner: "Push slide from the left
 * (Recommended)"): the opened page slides in from the drawer's left edge while the current page slides out to the right, side by
 * side, never overlapping, and Back reverses it. Since the two never overlap, the pages need no fill of their own, which is what
 * lets the drawer's own fill stay the single 80% layer over the map.
 */
enum class PageSlideStyle { OVER, PUSH_FROM_LEFT }

/**
 * [PageSlideStyle.PUSH_FROM_LEFT]'s transform: going deeper, the new page enters from the left edge while the current one leaves by
 * the right; going back, the page leaves by the left while the one it covered returns from the right. Both move on the same spec
 * by a full width, so their edges meet and they never overlap. `internal` for its own test.
 */
internal fun pagePushTransform(
    reduceMotion: Boolean,
    fromDepth: Int,
    toDepth: Int,
    slide: FiniteAnimationSpec<IntOffset>,
): ContentTransform = when {
    reduceMotion -> ContentTransform(EnterTransition.None, ExitTransition.None, targetContentZIndex = 0f, sizeTransform = null)
    toDepth >= fromDepth -> ContentTransform(
        targetContentEnter = slideInHorizontally(animationSpec = slide) { fullWidth -> -fullWidth },
        initialContentExit = slideOutHorizontally(animationSpec = slide) { fullWidth -> fullWidth },
        targetContentZIndex = 0f,
        sizeTransform = null,
    )
    else -> ContentTransform(
        targetContentEnter = slideInHorizontally(animationSpec = slide) { fullWidth -> fullWidth },
        initialContentExit = slideOutHorizontally(animationSpec = slide) { fullWidth -> -fullWidth },
        targetContentZIndex = 0f,
        sizeTransform = null,
    )
}

/**
 * How the page being covered stays: fully drawn and still, for as long as the slide over it can take, then gone (a fade held at
 * full and then dropped at once, [snap] with a delay). The length is the slide spring's own settling time over a distance no
 * screen reaches ([COVER_DISTANCE_PX]), read from the spec, so it is never shorter than the slide and changes with it.
 *
 * **Why not `ExitTransition.None`** (the first build, 2026-10-08): with no exit animation, AnimatedContent dropped the covered page
 * at once, so the arriving page slid in over nothing (`JournalPageSlideTest` found no leaving page mid-slide, in all three forward
 * cases). `ExitTransition.KeepUntilTransitionsFinished`, which does exactly this, is `internal` to the animation library (1.12.1).
 */
internal fun pageCoverHold(slideAsFloat: FiniteAnimationSpec<Float>): FiniteAnimationSpec<Float> {
    val nanos = slideAsFloat.vectorize(Float.VectorConverter)
        .getDurationNanos(AnimationVector1D(COVER_DISTANCE_PX), AnimationVector1D(0f), AnimationVector1D(0f))
    return snap(delayMillis = (nanos / 1_000_000L).toInt() + 1)
}

/** Further than any page slides, in pixels, so [pageCoverHold] is never shorter than the slide it waits for. */
private const val COVER_DISTANCE_PX = 4000f

/** Z orders for [pageSlideTransform]: an arriving page goes over what it covers, and a page uncovered by Back goes under the one leaving. */
private const val PAGE_ON_TOP = 1f
private const val PAGE_UNDERNEATH = -1f

/**
 * A page that slides in from the right **over** content that stays composed beneath it ([visible] turning true), and back out to
 * the right ([visible] turning false), uncovering it: the find opened over the Journal from a map bubble (scout F3), whose view
 * beneath must come back exactly as it was. It shows at once, without a slide, if it is already [visible] when first composed (a
 * return to the Journal tab with the find still open).
 *
 * While it slides in, nothing beneath it takes a touch, over the whole of this box, the covered part and the part not yet covered
 * alike: the content beneath is the page being covered, which takes no touch on the way in (as [PageSlide]'s covered page does),
 * and the find over the view swallowed every touch over its full area before this existed (`JournalTab.kt`, its opaque Surface).
 * While it slides out it takes no touch itself, so the view it uncovers has every touch from the start. Under reduced motion it
 * appears and goes at once.
 */
@Composable
fun SlideOverPage(
    visible: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val reduceMotion = LocalReduceMotion.current
    val slide = MotionTokens.pageSlideSpec<IntOffset>()
    val inertBack = rememberInertBackOwner()
    // A fresh state settled at the current value on every change under reduced motion, so nothing plays (as TabChromeFade).
    val state = remember(if (reduceMotion) visible else null) { MutableTransitionState(visible) }
    state.targetState = visible
    val entering = visible && !state.isIdle
    Box(modifier = modifier.fillMaxSize()) {
        if (entering) {
            // Over the content beneath, under the arriving page: takes every touch the page does not, until it has arrived.
            Box(
                Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        awaitPointerEventScope {
                            while (true) awaitPointerEvent().changes.forEach { it.consume() }
                        }
                    },
            )
        }
        AnimatedVisibility(
            visibleState = state,
            modifier = Modifier.fillMaxSize(),
            enter = slideInHorizontally(animationSpec = slide) { fullWidth -> fullWidth },
            exit = slideOutHorizontally(animationSpec = slide) { fullWidth -> fullWidth },
            label = "slideOverPage",
        ) {
            LeavingPageFrame(leaving = !visible, inertBack = inertBack, pageColor = Color.Transparent) { content() }
        }
    }
}

/**
 * A page's frame: while [leaving], no touch, no semantics but the leaving marker, and Back handlers that nothing presses. One
 * provider call either way (an empty set while not leaving), so starting to leave does not rebuild the page.
 */
@Composable
private fun LeavingPageFrame(leaving: Boolean, inertBack: InertBack, pageColor: Color, content: @Composable () -> Unit) {
    val inertProvided: Array<ProvidedValue<*>> = if (leaving) {
        arrayOf(
            LocalNavigationEventDispatcherOwner provides inertBack,
            LocalOnBackPressedDispatcherOwner provides inertBack,
        )
    } else {
        emptyArray<ProvidedValue<*>>()
    }
    // RECORD -691 asked that a page that starts to leave give up any focus inside it, to fix LeavingTheJournalFixesTest F1. Two
    // ways were built and measured frame by frame at the build, and neither fixed it, so neither is kept (reported): clearing
    // focus at the start of the leave, and moving it to the slide's own container. With either, and with neither, focus ends on
    // the search field that has come back above the Journal, which opens its dropdown; only when it moves differs (the first
    // frame, or the frame the page goes).
    // RECORD -692: counted while it leaves, for a screen that waits on its pages (the Journal's search bar, which returns only
    // once a closed entry has finished sliding out; LeavingPages).
    val leavingPages = LocalLeavingPages.current
    if (leavingPages != null) {
        DisposableEffect(leaving) {
            if (leaving) leavingPages.count++
            onDispose { if (leaving) leavingPages.count-- }
        }
    }
    CompositionLocalProvider(*inertProvided) {
        NoTouchTargetExpansion(active = leaving) {
        Box(
            Modifier
                .fillMaxSize()
                .background(pageColor)
                .leavingTakesNoTouches(leaving)
                .then(if (leaving) Modifier.clearAndSetSemantics { pageLeaving = true } else Modifier.semantics { pageLeaving = false }),
        ) { content() }
        }
    }
}

/**
 * How many pages are leaving under one screen, for a screen that waits on them (RECORD -692; the owner: "Search bar returns after
 * the slide (Recommended)"): the compact scaffold provides one around the Journal ([LocalLeavingPages]), and every [PageSlide] or
 * [SlideOverPage] page inside counts itself while it leaves.
 */
class LeavingPages {
    var count by mutableIntStateOf(0)
}

/** The [LeavingPages] a leaving page counts itself in; `null` (the default) counts nothing. */
val LocalLeavingPages = staticCompositionLocalOf<LeavingPages?> { null }

