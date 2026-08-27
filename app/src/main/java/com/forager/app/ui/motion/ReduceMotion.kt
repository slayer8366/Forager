package com.forager.app.ui.motion

import android.content.ContentResolver
import android.provider.Settings
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * Whether Reduce Motion should apply, per docs/motion-spec.md §4. Android's "Remove animations"
 * accessibility/developer setting sets both [Settings.Global.ANIMATOR_DURATION_SCALE] and
 * [Settings.Global.TRANSITION_ANIMATION_SCALE] to 0 together, but they are two independent
 * settings; either one alone at 0 is treated as the user's intent to reduce motion.
 */
fun isReduceMotionEnabled(contentResolver: ContentResolver): Boolean {
    val animatorScale = Settings.Global.getFloat(contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
    val transitionScale = Settings.Global.getFloat(contentResolver, Settings.Global.TRANSITION_ANIMATION_SCALE, 1f)
    return animatorScale == 0f || transitionScale == 0f
}

/**
 * Whether Reduce Motion is active for the current composition. Defaults to false; the app root
 * provides the real value from [isReduceMotionEnabled] against the device's ContentResolver.
 */
val LocalReduceMotion = staticCompositionLocalOf { false }

/** A motion category with a Reduce Motion mapping defined in docs/motion-spec.md §4. */
enum class MotionTreatment {
    ROUTE_PROGRESSIVE_DRAW,
    MARKER_ENTRANCE,
    SELECTION_PULSE,
    PANEL_MOTION,
}

/** The Reduce Motion replacement for a [MotionTreatment]'s default animated behavior. */
enum class ReducedMotionTreatment {
    INSTANT_FULL_PATH,
    ALPHA_CROSS_FADE_OR_INSTANT,
    STATIC_EMPHASIS,
    FADE_OR_INSTANT,
}

/**
 * Maps a [MotionTreatment] to its Reduce Motion equivalent, per docs/motion-spec.md §4's table.
 * This is a mapping, not a kill switch: every treatment maps to a still-visible equivalent, never
 * to "nothing," so the state each animation communicates (route drawn, marker present, item
 * selected, panel open) is never silently dropped.
 *
 * Unchanged by docs/adr/0002-motion-scheme-adoption.md: this table is a statement about what a
 * Reduce Motion user sees, not about which [androidx.compose.animation.core.FiniteAnimationSpec]
 * drives the full-motion version, so moving every category from `tween` onto
 * `MaterialTheme.motionScheme` needed no change here. A spring maps to a still-visible equivalent
 * the same way a tween did.
 */
fun reducedMotionEquivalent(treatment: MotionTreatment): ReducedMotionTreatment = when (treatment) {
    MotionTreatment.ROUTE_PROGRESSIVE_DRAW -> ReducedMotionTreatment.INSTANT_FULL_PATH
    MotionTreatment.MARKER_ENTRANCE -> ReducedMotionTreatment.ALPHA_CROSS_FADE_OR_INSTANT
    MotionTreatment.SELECTION_PULSE -> ReducedMotionTreatment.STATIC_EMPHASIS
    MotionTreatment.PANEL_MOTION -> ReducedMotionTreatment.FADE_OR_INSTANT
}
