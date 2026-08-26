package com.forager.app.ui.motion

import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable

/**
 * Understory step 4 (ADR-0002, `docs/adr/0002-motion-scheme-adoption.md`): a thin map from this
 * app's own named motion categories (`docs/motion-spec.md` §2) onto
 * `MaterialTheme.motionScheme`'s six specs, so call sites keep referring to *what is moving*
 * (`panelMotionSpec`) rather than to a raw spring tier (`slowSpatialSpec`). Before this rewrite,
 * every value here was its own `tween` — no springs, so no overshoot, by construction rather than
 * by choice. That rule is now superseded for spatial motion; ADR-0002 records what changed and
 * what didn't.
 *
 * **Seven specs deleted outright, not reimplemented as wrappers around a dead value.** Before this
 * rewrite, `MotionTokens.kt` defined eight `tween`-based specs; exactly one —
 * `panelMotionSpec` — had a production caller anywhere in `app/src/main` outside this file. The
 * other seven (`feedbackMotionSpec`, `narrativeRevealSpec`, `markerEntranceSpec`,
 * `selectionPulseHalfCycleSpec`, `locationIndicatorMoveSpec` as a *spec* — its duration constant
 * below survives, see [LOCATION_INDICATOR_MOVE_DURATION_MS] — `routeRecalculationMorphSpec`, and
 * `dataLayerOverlaySpec`) were reachable only from `MotionTokensTest.kt`. **A correction to the
 * design doc's own tally, verified against this file's actual callers rather than carried over:**
 * the earlier review counted "eight specs, six dead, one live" and never named
 * `routeRecalculationMorphSpec` among the dead six — it had zero production callers then, same as
 * the other six, so the true count was seven dead, one live. A curve with no caller carries no
 * decision (contrast `MotionPrecedence.kt`'s doc comment, where the constants *do* encode one and
 * are kept unenforced rather than deleted); deleting a dead curve loses nothing.
 *
 * **`panelMotionSpec` is the one survivor as a name, not as a call site.** Understory step 5
 * replaced its one production usage (`AddActionTile`'s hand-built `AnimatedVisibility` pair) with a
 * real `FloatingActionButtonMenu`, whose own motion is `MotionScheme.expressive()` by way of
 * `MaterialExpressiveTheme` — not this file. Stock M3 components already read
 * `MaterialTheme.motionScheme` internally (`ModalNavigationDrawer`, `AlertDialog`,
 * `FloatingActionButtonMenu` itself), with no call site this app owns, so most of ADR-0002's
 * "panels accept mild overshoot" decision is enacted by the framework automatically the moment
 * `ForagerTheme` supplies `MotionScheme.expressive()` (Understory step 2) — not by a named spec at
 * all. `panelMotionSpec` stays as the documented policy for the one case framework wiring doesn't
 * reach: a panel, sheet, or dialog this app still hand-builds, which should reach for this name
 * rather than a raw `tween` or an unlabelled spring growing back in one call site at a time (tag
 * 05's shape, applied to motion instead of colour).
 *
 * Every accessor below is a live pass-through, not an independent value — it can never itself
 * drift out of sync with the active `MotionScheme` the way a `tween` literal could, which is what
 * makes keeping the full named vocabulary safe even where a category (`feedbackMotionSpec`,
 * `markerEntranceSpec`, `narrativeRevealSpec`) has no caller yet: there is no stale policy to carry,
 * only a name waiting for its first caller to use instead of inventing a new one.
 */
object MotionTokens {

    // §2 "User location": animate only on meaningful GPS change; avoid jitter. Not a Compose
    // AnimationSpec — SightingsMap.kt divides this by MapLibre's own internal transition duration
    // and passes the ratio to LocationComponentOptions.trackingAnimationDurationMultiplier, which
    // accepts a scalar multiplier and nothing else: there is no spring or easing to supply here.
    // See that call site's own doc comment, and ADR-0002's "Unsupported, stated as unsupported."
    const val LOCATION_INDICATOR_MOVE_DURATION_MS: Int = 350

    // §2 "Map markers": clustering fans out with staggered timing, not uniform snaps — the
    // per-marker delay a cluster-expansion animation should apply, in fan-out order. A delay
    // between retargets, not a curve, so adopting MotionScheme doesn't touch it (ADR-0002).
    const val MARKER_CLUSTER_STAGGER_STEP_MS: Int = 40

    // §2 "Selection emphasis": low-amplitude breathing pulse amplitude bounds. Unchanged by
    // ADR-0002 — only the curve driving the pulse between these bounds moved, to
    // [selectionPulseSpec] below.
    const val SELECTION_PULSE_MIN_SCALE: Float = 0.96f
    const val SELECTION_PULSE_MAX_SCALE: Float = 1.04f

    // §2 "Routes": progressive reveal at human-scale pace. A rate (progress per kilometre of
    // corridor), not an easing curve, so it stays a plain constant rather than a MotionScheme
    // lookup — see ADR-0002, "Route reveal & recalculation": the per-km reveal pace stays a rate,
    // only the recalculation morph itself is a spring now ([narrativeRevealSpec]).
    const val ROUTE_REVEAL_MS_PER_KM: Int = 400

    /** §2 "Feedback motion": buttons, chips, FAB, icon stack press feedback. Wants the overshoot — the one place Expressive is most obviously right. */
    val feedbackMotionSpec: FiniteAnimationSpec<Float>
        @Composable @ReadOnlyComposable get() = MaterialTheme.motionScheme.fastSpatialSpec()

    /**
     * §2's "Panels and navigation" split in two by ADR-0002, since the two halves now take
     * different specs: this is the chrome half — nav bar, nav rail, tab switch. No positional
     * truth to distort, so the ordinary spatial default.
     */
    val navigationMotionSpec: FiniteAnimationSpec<Float>
        @Composable @ReadOnlyComposable get() = MaterialTheme.motionScheme.defaultSpatialSpec()

    /**
     * §2 "Panels and navigation", the other half: sheets, drawers, dialogs, panels. **Accepts
     * mild overshoot** — ADR-0002 records this as a deliberate taste call, not a finding that the
     * original "no springy overshoot" concern was misplaced. See this object's own class doc
     * comment for why this name currently has no call site of its own.
     */
    val panelMotionSpec: FiniteAnimationSpec<Float>
        @Composable @ReadOnlyComposable get() = MaterialTheme.motionScheme.slowSpatialSpec()

    /** §2 "Map markers": entrance and clustering. Scale/bounds change, so spatial, not effects — the 40ms stagger ([MARKER_CLUSTER_STAGGER_STEP_MS]) is unaffected, since it's a delay, not a curve. */
    val markerEntranceSpec: FiniteAnimationSpec<Float>
        @Composable @ReadOnlyComposable get() = MaterialTheme.motionScheme.defaultSpatialSpec()

    /** §2 "Selection emphasis": the curve driving the pulse between [SELECTION_PULSE_MIN_SCALE] and [SELECTION_PULSE_MAX_SCALE]. */
    val selectionPulseSpec: FiniteAnimationSpec<Float>
        @Composable @ReadOnlyComposable get() = MaterialTheme.motionScheme.slowSpatialSpec()

    /** §2 "Data layer overlays": alpha only. Effects, so critically damped — an overlay must not flash past full opacity (R1's argument, ADR-0002). */
    val dataLayerOverlaySpec: FiniteAnimationSpec<Float>
        @Composable @ReadOnlyComposable get() = MaterialTheme.motionScheme.defaultEffectsSpec()

    /**
     * §2 "Narrative reveals", and §2 "Routes"'s recalculation morph (the morph is a spring; the
     * per-km reveal rate itself stays [ROUTE_REVEAL_MS_PER_KM], a rate, not a curve). Effects, not
     * spatial: a reveal changes visibility/extent, not a position or size a retarget could
     * overshoot past in a way that reads as wrong.
     */
    val narrativeRevealSpec: FiniteAnimationSpec<Float>
        @Composable @ReadOnlyComposable get() = MaterialTheme.motionScheme.slowEffectsSpec()
}
