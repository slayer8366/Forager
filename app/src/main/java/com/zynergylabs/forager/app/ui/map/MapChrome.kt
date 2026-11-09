package com.zynergylabs.forager.app.ui.map

import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.DialogWindowProvider
import android.os.Build
import android.util.Log
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.SemanticsPropertyKey
import androidx.compose.ui.semantics.semantics
import com.zynergylabs.forager.app.ui.theme.SurfaceContainerDark
import com.zynergylabs.forager.app.ui.theme.SurfaceContainerHighestDark
import com.zynergylabs.forager.app.ui.theme.SurfaceContainerLight
import com.zynergylabs.forager.app.ui.theme.SurfaceContainerLowestLight
import com.zynergylabs.forager.app.ui.theme.navigationBarContainerColor
import com.zynergylabs.forager.app.ui.theme.Bark
import com.zynergylabs.forager.app.ui.theme.LocalForagerDarkTheme

/**
 * [MapIconBar]'s fill — see [MapBarIconButton]. Picked per [com.zynergylabs.forager.app.ui.theme.LocalForagerDarkTheme]
 * (the app's own "Night Mode" checkbox, not the device's system theme — see that local's own doc
 * comment for the bug reading [androidx.compose.foundation.isSystemInDarkTheme] directly here caused), independent of
 * the map's own night mode (deliberately not derived from it, the same way [MapPalette] is
 * deliberately not derived from the ambient scheme — this is app chrome over the map, not a mark
 * on it, but the two axes are still kept separate on principle: a device in light mode looking at
 * a map in night mode, or the reverse, are both real states, and neither should silently steer
 * the other).
 *
 * **History: opaque, plus a hairline edge, replacing the translucent fill these circles used
 * before.** Confirmed on real hardware (Portland-metro, USGS Topo): at a 78%-alpha fill, map data
 * underneath — contour lines, place labels — composited straight through the buttons, reading as
 * barely-there smudges; the stack's one already-opaque icon (the green "add" button,
 * [MaterialTheme.colorScheme.primary]) read perfectly on the same terrain in the same screenshot.
 * Opacity was the only difference. **Requested back down to 80% since** — a deliberate, informed
 * choice after that finding was raised again, not a reversion made without knowing it. Provisional
 * pending a hardware look at this specific value, the same status the border below already had.
 *
 * **Rejected alternative: tuning the alpha per basemap.** A single translucency value can be tuned
 * to look right against one basemap's palette, but this app ships pale topo, dark aerial imagery,
 * and (later) hillshade — a fill that reads on one will not read on the others, and per-basemap
 * tuning means re-solving this every time a basemap is added.
 *
 * The hairline border ([MAP_ICON_STACK_BORDER_COLOR_DARK]/[MAP_ICON_STACK_BORDER_COLOR_LIGHT]) is
 * what keeps a *dark* fill working on imagery specifically: an opaque dark circle alone reads fine
 * against pale topo but risks merging into imagery, which is dark nearly everywhere. A light edge
 * separates control from map regardless of what's underneath. The light-theme fill below is new,
 * unverified on hardware in either direction — its own dark hairline border mirrors the same
 * reasoning for the opposite risk (merging into snow, sand, or other pale terrain), but nobody has
 * looked at it on a real screen yet.
 */
internal val MapIconStackButtonColorDark = SurfaceContainerDark.copy(alpha = MAP_CHROME_OVER_MAP_ALPHA)

/**
 * C1 (dispatch 2026-09-28-210): **this colour, and [MapIconStackButtonColorLight], are the navigation bar's container
 * colour, not a hue of their own.** They were Bark and Cream. They are built from `SurfaceContainerDark` and
 * `SurfaceContainerLight`, the two values `Theme.kt` gives `colorScheme.surfaceContainer` (what
 * [com.zynergylabs.forager.app.ui.theme.navigationBarContainerColor] returns), because they are read at about twenty
 * places that cannot call a composable; `MapChromeColourTokenTest` asserts they equal the scheme's own in both
 * themes. Kept under their names, with their 0.8, because `MapChromeAlphaTest` pins that alpha. The owner, verbatim:
 * "Have them be the same color as the bottom app navigation bar."
 *
 * [MapIconStackButtonColorDark]'s light-theme counterpart — see that color's own doc comment. */
internal val MapIconStackButtonColorLight = SurfaceContainerLight.copy(alpha = MAP_CHROME_OVER_MAP_ALPHA)

/** The standing opacity for chrome floating over the map — the one value every fill here targets. */
internal const val MAP_CHROME_OVER_MAP_ALPHA = 0.8f

/**
 * Dispatch 2026-10-12-08 (RECORD -945, the owner: "Lighter pill + shadow (Recommended)"; RECORD -951 for the roles): the search
 * pill over the map, and in landscape the compass strip beside it (the two are the top bar, RECORD -946), are a shade lighter
 * than the strip or card tucked under them, so the tuck reads by day and at night. The fill's colour changes, not its alpha:
 * still [MAP_CHROME_OVER_MAP_ALPHA]. Taken from the theme's own surface ramp, its lightest end in each theme, since no role this
 * theme sets is lighter than `surfaceContainer` in both: `surfaceContainerLowest` by day (#FFFFFF) and `surfaceContainerHighest`
 * at night (#373737). `MapChromeColourTokenTest` asserts they equal the scheme's own.
 *
 * Rejected: one step lighter (`surfaceContainerLow` / `surfaceContainerHigh`): at 0.8 the day step is about 5%, which does not
 * read over a busy map. Rejected too: setting `surfaceBright` in the theme to these values, which would have been one role
 * name at the cost of a theme change the planner ruled out (RECORD -951).
 */
internal val MapPillColorLight = SurfaceContainerLowestLight.copy(alpha = MAP_CHROME_OVER_MAP_ALPHA)

/** [MapPillColorLight]'s night counterpart. */
internal val MapPillColorDark = SurfaceContainerHighestDark.copy(alpha = MAP_CHROME_OVER_MAP_ALPHA)

/**
 * Map chrome at 80% where it covers a map (dispatch 2026-09-28-56 as amended by -58; planner message
 * -77): the container colour a sheet, dialog, menu, drawer, snackbar or surface was given, exposed to
 * tests the way the Layers sheet exposes its own (`MapLayersSheet`). Set on the node that is given the
 * colour, beside its test tag.
 */
internal val MapChromeContainerColor = SemanticsPropertyKey<Color>("MapChromeContainerColor")

/**
 * The content colour inside the same surface: `LocalContentColor` as read inside it wherever the
 * surface has a child to read it on, else the content colour the surface was given.
 */
internal val MapChromeContentColor = SemanticsPropertyKey<Color>("MapChromeContentColor")

/**
 * A surface's fill for where it is shown (owner, 2026-09-28: "My idea is that nothing should fully
 * obstruct the map view. All map chrome gets 80% opacity as a result"): its [role] at
 * [MAP_CHROME_OVER_MAP_ALPHA] when a map is drawn on screen beneath it ([overMap]; planner message
 * 2026-09-28-77, Q2), and the role itself, solid, where none is (owner: "In the places that aren't
 * covering a map, they can stay solid"). Only the fill: each caller pins its content colour to the
 * unaltered role's own, because `contentColorFor` matches a colour-scheme role exactly and a
 * translucent fill matches none (`MapLayersSheet`).
 */
internal fun mapChromeFill(role: Color, overMap: Boolean): Color =
    if (overMap) role.copy(alpha = MAP_CHROME_OVER_MAP_ALPHA) else role

/** Marks this node with the container colour it was given ([MapChromeContainerColor]). */
internal fun Modifier.mapChromeContainerColor(color: Color): Modifier = semantics { set(MapChromeContainerColor, color) }

/** Marks this node with the content colour read inside its surface ([MapChromeContentColor]). */
internal fun Modifier.mapChromeContentColor(color: Color): Modifier = semantics { set(MapChromeContentColor, color) }

/**
 * Icon-bar-unify-container dispatch: [MapIconBar] and `TrailheadControls` now sit inside one
 * filled container (their cluster — measured, dragged, clamped and minimised as one), and the
 * owner's design layers two lighter fills so the cluster still reads at [MAP_CHROME_OVER_MAP_ALPHA]
 * where a child sits over the container. **Layered alpha composites; it does not add.** Two
 * same-colour layers read as `top + bottom × (1 − top)` ([srcOverAlpha]), so these two were chosen
 * to land on exactly 0.8 — 0.6 under 0.5 — not summed to it (0.5 under 0.3 would read as ~0.65).
 * `MapChromeAlphaTest` asserts the composite of these declared constants, so the pair cannot drift
 * apart without a test saying so; it does not pretend to verify appearance, which only a real
 * screen over real terrain can. The owner read "the pills a lighter one again" literally: the
 * children are the lighter layer. Where the container extends past its children (the gap between
 * bar and pill, the arm's own width) it reads at the container's own 0.6 — intentional, that
 * region holds no control. Every icon sits on a child, i.e. on the full 0.8 composite, so icon
 * legibility against bright terrain is unchanged from before the container existed.
 */
internal const val MAP_ICON_CLUSTER_CONTAINER_ALPHA = 0.6f

/** See [MAP_ICON_CLUSTER_CONTAINER_ALPHA]. Applied to [MapIconBar] (via its `fillColor`) and both Trailhead pills inside the cluster — the bar is a child too, or its region would composite to 0.92, not 0.8. */
internal const val MAP_ICON_CLUSTER_CHILD_ALPHA = 0.5f

/** Source-over compositing of two same-colour translucent layers: the resulting alpha of [top] drawn over [bottom]. */
internal fun srcOverAlpha(top: Float, bottom: Float): Float = top + bottom * (1f - top)

/** The cluster container's own fill — see [MAP_ICON_CLUSTER_CONTAINER_ALPHA]. */
@Composable
@ReadOnlyComposable
internal fun mapIconClusterContainerColor(): Color =
    navigationBarContainerColor().copy(alpha = MAP_ICON_CLUSTER_CONTAINER_ALPHA)

/** The standing single-layer chrome fill ([MapIconStackButtonColorDark]/[MapIconStackButtonColorLight], 0.8) for whichever theme is current — the landscape L's pill, which has no container under it. */
@Composable
@ReadOnlyComposable
internal fun mapIconChromeFillColor(): Color =
    if (LocalForagerDarkTheme.current) MapIconStackButtonColorDark else MapIconStackButtonColorLight

/** The fill of each child inside the cluster container — see [MAP_ICON_CLUSTER_CHILD_ALPHA]. */
@Composable
@ReadOnlyComposable
internal fun mapIconClusterChildColor(): Color =
    navigationBarContainerColor().copy(alpha = MAP_ICON_CLUSTER_CHILD_ALPHA)

/** The hairline edge for whichever theme is current — [MAP_ICON_STACK_BORDER_COLOR_DARK]/[MAP_ICON_STACK_BORDER_COLOR_LIGHT]. */
@Composable
@ReadOnlyComposable
internal fun mapIconStackBorderColor(): Color =
    if (LocalForagerDarkTheme.current) MAP_ICON_STACK_BORDER_COLOR_DARK else MAP_ICON_STACK_BORDER_COLOR_LIGHT

/** The hairline edge on [MapIconStackButtonColorDark] circles — see that color's own doc comment. */
internal val MAP_ICON_STACK_BORDER_COLOR_DARK = Color.White.copy(alpha = 0.4f)

/** The hairline edge on [MapIconStackButtonColorLight] circles — the inverse of [MAP_ICON_STACK_BORDER_COLOR_DARK], for the inverse risk (a light fill merging into pale terrain rather than a dark one merging into dark imagery). */
internal val MAP_ICON_STACK_BORDER_COLOR_LIGHT = Bark.copy(alpha = 0.4f)

/**
 * Lets the map show through the navigation-bar band under a modal bottom sheet (dispatch
 * 2026-09-28-104, item 3). The device check found that band flat and opaque, (20, 19, 18), while a sheet
 * was up, so the sheet's own 0.8 container stopped short of the screen's foot. **The cause was not
 * established there** (the sheet window's navigation-bar background, or Android's contrast scrim for
 * three-button navigation), and I could not establish it here without a device. This turns off the
 * documented one of the two that an app can turn off: [android.view.Window.setNavigationBarContrastEnforced]
 * on the sheet's own window (API 29+). The other candidate, the window's navigation-bar colour, is
 * ignored for a target of API 35+ (see `log/CameraWindowChrome.kt`'s note on the status bar's colour), so
 * there is nothing to set for it. **A hypothesis, unverified by any test here**: if the band is still
 * opaque on the phone, the cause is the second one and the fix is not this. Call it inside the sheet's
 * content, where [LocalView]'s parent is the sheet's dialog window.
 */
@Composable
internal fun MapChromeSheetNavigationBar() {
    val window = (LocalView.current.parent as? DialogWindowProvider)?.window
    if (window == null) {
        // Not swallowed: the sheet's content was not inside a dialog window, so nothing was changed.
        SideEffect { Log.w("MapChrome", "MapChromeSheetNavigationBar: no dialog window above this sheet; the navigation-bar band is left as it was") }
        return
    }
    SideEffect {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) window.isNavigationBarContrastEnforced = false
    }
}
