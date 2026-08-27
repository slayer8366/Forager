package com.forager.app.ui.motion

import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable

/**
 * Named motion categories from docs/motion-spec.md §2, each mapped onto one of
 * [MaterialTheme.motionScheme]'s six specs per docs/adr/0002-motion-scheme-adoption.md. No magic
 * numbers or raw `tween`/`spring` calls at call sites -- Composables reference a category function
 * here rather than resolving a spec themselves.
 *
 * Every function is `@Composable @ReadOnlyComposable`: [MaterialTheme.motionScheme] is itself
 * composable-only (it reads a `CompositionLocal`), so nothing here can be a plain value the way
 * the old `tween`-based specs were.
 */
object MotionTokens {

    // §2 "Feedback motion": buttons, chips, FAB, icon stack. Press feedback wants the overshoot
    // -- provisional pending Gate G question 1. No production caller yet; scaffolding, same as
    // before ADR-0002.
    @Composable
    @ReadOnlyComposable
    fun <T> feedbackMotionSpec(): FiniteAnimationSpec<T> = MaterialTheme.motionScheme.fastSpatialSpec()

    // §2 "Panels and navigation" -- the panel half. The only category with a real production
    // caller (AddActionTile, AvailabilityScreen.kt). Accepts mild overshoot as a taste call, per
    // ADR-0002; the damping value is provisional pending Gate G question 2.
    @Composable
    @ReadOnlyComposable
    fun <T> panelMotionSpec(): FiniteAnimationSpec<T> = MaterialTheme.motionScheme.slowSpatialSpec()

    // §2 "Panels and navigation" -- the nav-chrome half. Split from the panel row per
    // ADR-0002: "chrome; no positional truth to distort", so overshoot here is a harmless
    // flourish rather than felt on the primary interaction surface. No production caller yet.
    @Composable
    @ReadOnlyComposable
    fun <T> navigationMotionSpec(): FiniteAnimationSpec<T> = MaterialTheme.motionScheme.defaultSpatialSpec()

    // §2 "Map markers": soft scale + fade entrance when density/performance allow. No production
    // caller yet.
    @Composable
    @ReadOnlyComposable
    fun <T> markerEntranceSpec(): FiniteAnimationSpec<T> = MaterialTheme.motionScheme.defaultSpatialSpec()

    // §2 "Map markers": "clustering fans out with staggered timing, not uniform snaps" -- the
    // per-marker delay a cluster-expansion animation should apply, in fan-out order. A stagger
    // delay, not a curve, so it survives independent of the spec migration above.
    const val MARKER_CLUSTER_STAGGER_STEP_MS: Int = 40

    // §2 "Selection emphasis": low-amplitude breathing pulse. Selection emphasis only -- this
    // spec defines no other kind of emphasis (see docs/motion-spec.md "Scope boundary"). No
    // production caller yet.
    @Composable
    @ReadOnlyComposable
    fun <T> selectionPulseSpec(): FiniteAnimationSpec<T> = MaterialTheme.motionScheme.slowSpatialSpec()

    // Amplitude bounds for the pulse above -- independent of duration/spec, survive unchanged.
    const val SELECTION_PULSE_MIN_SCALE: Float = 0.96f
    const val SELECTION_PULSE_MAX_SCALE: Float = 1.04f

    // §2 "Narrative reveals": up to 800-1200 ms, interruptible. Not in understory-design-system.md
    // §3S's replacement table -- mapped here to slowEffectsSpec (content fading in, not a bounds
    // change, so effects rather than spatial; "slow" as the longest duration among all eight
    // categories) and recorded as a decision in docs/adr/0002-motion-scheme-adoption.md rather than
    // left for whoever writes the first caller. No production caller yet.
    @Composable
    @ReadOnlyComposable
    fun <T> narrativeRevealSpec(): FiniteAnimationSpec<T> = MaterialTheme.motionScheme.slowEffectsSpec()

    // §2 "User location": animate only on meaningful GPS change; avoid jitter. Unsupported as a
    // Compose spec, stated as unsupported: SightingsMap.kt divides this duration against
    // MapLibre's own fixed internal duration and passes the ratio to
    // LocationComponentOptions.trackingAnimationDurationMultiplier, a scalar on a native
    // animation with no interpolator to supply. Stays a plain duration -- see
    // docs/adr/0002-motion-scheme-adoption.md's "location puck" section for why no function wraps
    // it.
    const val LOCATION_INDICATOR_MOVE_DURATION_MS: Int = 350

    // §2 "Routes": progressive reveal at human-scale pace; recalculation morphs existing segments
    // rather than redrawing. The morph is a spring (effects: colour/alpha-adjacent segment
    // redraw, not a bounds change); the per-km reveal rate stays a rate, independent of the spec.
    // No production caller yet.
    const val ROUTE_REVEAL_MS_PER_KM: Int = 400

    @Composable
    @ReadOnlyComposable
    fun <T> routeRecalculationMorphSpec(): FiniteAnimationSpec<T> = MaterialTheme.motionScheme.slowEffectsSpec()

    // §2 "Data layer overlays": cross-fade or gentle radial growth. No particle systems. No
    // production caller yet.
    @Composable
    @ReadOnlyComposable
    fun <T> dataLayerOverlaySpec(): FiniteAnimationSpec<T> = MaterialTheme.motionScheme.defaultEffectsSpec()
}
