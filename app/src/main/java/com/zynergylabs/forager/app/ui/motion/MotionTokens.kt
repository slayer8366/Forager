package com.zynergylabs.forager.app.ui.motion

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.tween
import com.zynergylabs.forager.app.ui.map.NAVIGATION_VIEW_TRANSITION_MILLIS
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MotionScheme
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
    // -- provisional pending Gate G question 1. First production caller: the press bounce
    // (motion/PressFeedback.kt, dispatch 2026-09-28-652 item 4), and the slight grow of an icon swap.
    @Composable
    @ReadOnlyComposable
    fun <T> feedbackMotionSpec(): FiniteAnimationSpec<T> = MaterialTheme.motionScheme.fastSpatialSpec()

    // The press bounce's depth (the owner, RECORD -651: "Small press bounce"): the icon dips to this
    // scale while pressed and springs back on [feedbackMotionSpec]. An amplitude, not a curve, so it
    // stays a plain constant beside the spec, like the selection pulse's bounds below. Chosen, not
    // measured; whether it reads as "slight" on the phone is a device check.
    const val PRESS_BOUNCE_SCALE: Float = 0.88f

    // §2 "Feedback motion", the icon-swap half (dispatch 2026-09-28-652 item 5; the owner, RECORD
    // -651: "Quick crossfade", with a slight grow): an icon changing its picture, its tint, its
    // badge or its disabled dimming. Effects rather than spatial (colour and alpha, not bounds),
    // and fast, so the swap is short and can never overshoot: effects specs are critically damped
    // (MotionTokensTest). The slight grow that comes with it is spatial and rides
    // [feedbackMotionSpec].
    @Composable
    @ReadOnlyComposable
    fun <T> iconSwapSpec(): FiniteAnimationSpec<T> = MaterialTheme.motionScheme.fastEffectsSpec()

    // Where the incoming icon of a swap starts its slight grow from (to 1). Not applied under
    // reduced motion, where the swap is a plain crossfade (§4).
    const val ICON_SWAP_ENTER_SCALE: Float = 0.85f

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

    // §2 "Navigation chrome", the tab-switch half (dispatch 2026-09-28-666, items 1 and 2; the owner,
    // RECORD -651: tabs "Quick crossfade", the bottom bar "Fade with the tab"). One tab fading into the
    // next, and the bar's fill fading between solid and 80% on the same spec, so the two move as one.
    // Effects (alpha only, never a bounds change: the live map must not re-measure) and fast. Callers:
    // TabCrossfade and TabChromeFade (motion/TabCrossfade.kt).
    @Composable
    @ReadOnlyComposable
    fun <T> tabCrossfadeSpec(): FiniteAnimationSpec<T> = MaterialTheme.motionScheme.fastEffectsSpec()

    // §2 "Map pop-ups" (item 4; the owner, RECORD -651: "Fade and grow", each from where it belongs):
    // the bubble, the centre pin and its OK/Cancel row, Return to Route, the chips and the legend. The
    // fade is effects (critically damped, so it cannot overshoot an alpha bound); the grow, a draw-only
    // scale (MapPopUp.kt), rides the default spatial spec. The strip's sundown line grows its strip on
    // the same pair.
    @Composable
    @ReadOnlyComposable
    fun <T> mapPopUpFadeSpec(): FiniteAnimationSpec<T> = MaterialTheme.motionScheme.defaultEffectsSpec()

    @Composable
    @ReadOnlyComposable
    fun <T> mapPopUpGrowSpec(): FiniteAnimationSpec<T> = MaterialTheme.motionScheme.defaultSpatialSpec()

    // Where a map pop-up's drawing starts its grow from (to 1), and shrinks back to as it leaves. Not
    // applied under reduced motion, where pop-ups fade alone (§4). Chosen, not measured, the same as
    // ICON_SWAP_ENTER_SCALE; whether it reads as a grow on the phone is a device check.
    const val MAP_POPUP_ENTER_SCALE: Float = 0.85f

    // §2 "Navigation chrome", start and stop (item 3; the owner, RECORD -651: "Move with the map"): the
    // compass strip slides up and out while the navigation display slides down and in, timed to the
    // map's tilt, and the reverse on stop; each fades on the same animation. **The one timed animation
    // in this object, an exception to ADR-0002** (the owner, Amendment 1, RECORD -672: "Allow one
    // exception"): the map's tilt is a MapLibre camera change of fixed length,
    // NAVIGATION_VIEW_TRANSITION_MILLIS in map/NavigationView.kt, read from there so the two cannot
    // drift; a spring has no duration and could only approximate it. The easing is Material's
    // standard one, chosen: MapLibre's own camera curve was not read. Allowed by check 3 of
    // scripts/verify-design-tokens.sh at this line alone.
    @Composable
    @ReadOnlyComposable
    fun <T> navigationViewChromeSpec(): FiniteAnimationSpec<T> = tween(durationMillis = NAVIGATION_VIEW_TRANSITION_MILLIS.toInt(), easing = FastOutSlowInEasing)

    // §2 "Words that change" (item 6; the owner, RECORD -651: "Numbers instant, words fade"): a line
    // whose words change crossfades quickly; a change in its numbers alone is drawn at once
    // (motion/WordSwap.kt). Effects, fast, the same as an icon swap's fade.
    @Composable
    @ReadOnlyComposable
    fun <T> wordSwapSpec(): FiniteAnimationSpec<T> = MaterialTheme.motionScheme.fastEffectsSpec()

    // §2 "User location": animate only on meaningful GPS change; avoid jitter. Unsupported as a
    // Compose spec, stated as unsupported: SightingsMap.kt divides this duration against
    // MapLibre's own fixed internal duration and passes the ratio to
    // LocationComponentOptions.trackingAnimationDurationMultiplier, a scalar on a native
    // animation with no interpolator to supply. Stays a plain duration -- see
    // docs/adr/0002-motion-scheme-adoption.md's "location puck" section for why no function wraps
    // it.
    const val LOCATION_INDICATOR_MOVE_DURATION_MS: Int = 350

    // §2 "Journal pages" (motion Part 3, dispatch 2026-09-28-676 item 1; the owner, RECORD -651: "Slide in, slide back"): a page
    // opened in the Journal slides in from the right over the one beneath, and Back slides it out to the right
    // (motion/PageSlide.kt). Spatial, since it moves a page, but on the **effects** family's default spring, which is critically
    // damped: an underdamped slide-in would overshoot past its resting place and bare a strip of the page beneath at the
    // right edge, the overshoot ADR-0001 was written against on a primary surface. Chosen, not measured; how it feels is the
    // S22's. Under reduced motion a page changes at once (§4).
    @Composable
    @ReadOnlyComposable
    fun <T> pageSlideSpec(): FiniteAnimationSpec<T> = MaterialTheme.motionScheme.defaultEffectsSpec()

    // §2 "Lists" (motion Part 3, item 2; the owner, RECORD -651: "Slide and close up"): a removed row fades and shrinks while
    // the rest glide up, Undo reverses it, and new or reordered rows glide into place (motion/ListMotion.kt). One spec for the
    // row's fade, its height and a lazy list's placement glide, so they move as one. Critically damped for the reason above:
    // a list that closed up past its place and bounced back would move every row below the removed one twice.
    @Composable
    @ReadOnlyComposable
    fun <T> listRowSpec(): FiniteAnimationSpec<T> = MaterialTheme.motionScheme.defaultEffectsSpec()

    // §2 "Loaded content" (motion Part 3, Amendment 2, RECORD -682): a spinner, empty message or error giving way to content,
    // or back, crossfades (motion/StateCrossfade.kt). Effects, fast, like a word swap: quick, and it cannot overshoot an alpha.
    @Composable
    @ReadOnlyComposable
    fun <T> stateCrossfadeSpec(): FiniteAnimationSpec<T> = MaterialTheme.motionScheme.fastEffectsSpec()

    // §2 "Night mode" (motion Part 3, item 4; the owner, RECORD -651: "Fade the colours", and RECORD -652: "the fade is
    // permissible if it's fast and smooth, and not ceremonial and boring"): every colour of the theme blends from the old
    // scheme to the new (theme/Theme.kt). Effects, and the **fast** one, so the blend is quick and cannot overshoot a colour.
    // The one category read **above** the theme, from the [scheme] the theme is about to provide, because what it fades is the
    // theme's own colours: `ForagerTheme` works out the colour scheme before there is a [MaterialTheme.motionScheme] to read.
    fun <T> nightModeFadeSpec(scheme: MotionScheme): FiniteAnimationSpec<T> = scheme.fastEffectsSpec()
}
