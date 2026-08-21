package com.forager.app.ui.motion

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween

/**
 * Named durations, easings, and AnimationSpecs from docs/motion-spec.md §2. No magic numbers at
 * call sites -- Composables should reference a constant/spec here rather than hardcoding a
 * duration or easing curve.
 *
 * Every spec below is `tween`-based (never `spring`), which is what keeps panels and navigation
 * "grounded; no springy overshoot" per §2 without needing a separate mechanism to enforce it.
 */
object MotionTokens {

    /** Material's standard decelerate curve -- used wherever §2 calls for "ease-out." */
    val EaseOut: Easing = CubicBezierEasing(0.0f, 0.0f, 0.2f, 1.0f)

    // §2 "Feedback motion": 150-400 ms, ease-out.
    const val FEEDBACK_MOTION_DURATION_MS: Int = 200
    val feedbackMotionSpec: AnimationSpec<Float> = tween(durationMillis = FEEDBACK_MOTION_DURATION_MS, easing = EaseOut)

    // §2 "Narrative reveals": up to 800-1200 ms, interruptible. `tween` is itself interruptible
    // when driven by an Animatable, so no separate mechanism is needed for that requirement.
    const val NARRATIVE_REVEAL_DURATION_MS: Int = 900
    val narrativeRevealSpec: AnimationSpec<Float> = tween(durationMillis = NARRATIVE_REVEAL_DURATION_MS, easing = EaseOut)

    // §2 "Map markers": soft scale + fade entrance when density/performance allow.
    const val MARKER_ENTRANCE_DURATION_MS: Int = 250
    val markerEntranceSpec: AnimationSpec<Float> = tween(durationMillis = MARKER_ENTRANCE_DURATION_MS, easing = EaseOut)

    // §2 "Map markers": "clustering fans out with staggered timing, not uniform snaps" -- the
    // per-marker delay a cluster-expansion animation should apply, in fan-out order.
    const val MARKER_CLUSTER_STAGGER_STEP_MS: Int = 40

    // §2 "Selection emphasis": low-amplitude breathing pulse. Selection emphasis only -- this
    // spec defines no other kind of emphasis (see docs/motion-spec.md "Scope boundary").
    const val SELECTION_PULSE_PERIOD_MS: Int = 1600
    const val SELECTION_PULSE_MIN_SCALE: Float = 0.96f
    const val SELECTION_PULSE_MAX_SCALE: Float = 1.04f
    val selectionPulseHalfCycleSpec: AnimationSpec<Float> =
        tween(durationMillis = SELECTION_PULSE_PERIOD_MS / 2, easing = LinearEasing)

    // §2 "User location": animate only on meaningful GPS change; avoid jitter.
    const val LOCATION_INDICATOR_MOVE_DURATION_MS: Int = 350
    val locationIndicatorMoveSpec: AnimationSpec<Float> =
        tween(durationMillis = LOCATION_INDICATOR_MOVE_DURATION_MS, easing = EaseOut)

    // §2 "Routes": progressive reveal at human-scale pace; recalculation morphs existing
    // segments rather than redrawing.
    const val ROUTE_REVEAL_MS_PER_KM: Int = 400
    const val ROUTE_RECALCULATION_MORPH_DURATION_MS: Int = 500
    val routeRecalculationMorphSpec: AnimationSpec<Float> =
        tween(durationMillis = ROUTE_RECALCULATION_MORPH_DURATION_MS, easing = EaseOut)

    // §2 "Panels and navigation": ease-out, grounded; no springy overshoot.
    const val PANEL_MOTION_DURATION_MS: Int = 300
    val panelMotionSpec: AnimationSpec<Float> = tween(durationMillis = PANEL_MOTION_DURATION_MS, easing = EaseOut)

    // §2 "Data layer overlays": cross-fade or gentle radial growth. No particle systems.
    const val DATA_LAYER_OVERLAY_DURATION_MS: Int = 450
    val dataLayerOverlaySpec: AnimationSpec<Float> =
        tween(durationMillis = DATA_LAYER_OVERLAY_DURATION_MS, easing = EaseOut)
}
