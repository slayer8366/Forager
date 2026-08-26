package com.forager.app.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.toArgb

/**
 * The nine colours the map overlay draws with, as `android.graphics` ARGB ints.
 *
 * MapLibre renders on a native canvas and cannot read a Compose [ColorScheme], so `SightingsMap`
 * still takes ints — it just stops *defining* them. Before this existed, nine raw literals lived
 * beside the layer code with no theme role governing any of them ("tag 04" in
 * docs/plans/understory-design-system.md): every future palette change had to find and edit all
 * nine, and none of them moved with the theme.
 *
 * ## Derivation, not identity
 *
 * Every value here is *derived* from its source rather than returned verbatim, and
 * `MapPaletteTest` asserts that no returned int equals the role it came from. That rule is the
 * whole point of the indirection, not a stylistic preference: `tertiary` and `error` take their
 * hues from this map's own trip blue and search-centre red, so binding the map back to those roles
 * would make retuning `error` for on-screen text contrast silently move what the map draws — two
 * unrelated things resolving to one value, which is the defect "tag 08" describes, in semantic
 * form. Sourcing a hue and binding a value are different things, and only the first is wanted.
 *
 * The derivation is a fixed [DERIVATION_SHIFT] toward the opposite tonal pole: markers that sit
 * *on* the ground shift toward [ColorScheme.surface], and the two that are themselves ground-like
 * (the dot stroke, the area-marker glyph) shift toward [ColorScheme.onSurface]. One rule, stated
 * once, and no value can collide with its source as long as the shift is non-zero.
 *
 * ## Known objection, recorded rather than acted on
 *
 * This varies with the device theme because the design document specifies that it should, and the
 * project owner confirmed that reading after the objection below was raised. The objection stands
 * on the record:
 *
 * **The basemap is fixed raster and has no theme variant.** `Basemap` offers USGS Topo, USGS
 * Imagery Topo, OpenTopoMap and OSM Standard; none of them changes with the device theme, and
 * nothing in the map layer reads `isSystemInDarkTheme`. Markers therefore need contrast against
 * *tiles*, not against a theme surface, and moving them by theme changes the mark without changing
 * the ground. The axis that genuinely varies is the user's **basemap choice** — pale tan for topo
 * against dark aerial photography for imagery — which is a within-theme switch this type does not
 * model.
 *
 * If the device gate finds markers reading worse in dark theme on a topo basemap, that is this
 * objection coming true, and the fix is to build the palette from a fixed reference scheme rather
 * than the ambient one — a one-line change at the [from] call site, not a redesign.
 */
data class MapPalette(
    val sightingDot: Int,
    val sightingDotStroke: Int,
    val connector: Int,
    val areaMarkerBackground: Int,
    val areaMarkerForeground: Int,
    val plannedTrip: Int,
    val searchCentre: Int,
    val breadcrumb: Int,
    val waypoint: Int,
) {
    companion object {

        /**
         * How far each colour moves from its source, toward the opposite tonal pole. Large enough
         * that no derived value can equal its source role (which `MapPaletteTest` asserts), small
         * enough that the marker still reads as the hue it came from.
         */
        internal const val DERIVATION_SHIFT: Float = 0.12f

        /**
         * The search centre's own base hue. **Deliberately not [ColorScheme.error]**, even though
         * `error` took its hue from this exact red: a pin meaning "you searched here" and text
         * meaning "something failed" have no reason to move together, and retuning `error` for
         * text legibility must not drag the map with it.
         */
        private val SearchCentreBase = Color(0xFFB33B3B)

        /**
         * The live-track blue. Deliberately outside the theme's own palette and not derived from
         * [ColorScheme.tertiary]: this follows the near-universal GPS-track convention (Gaia GPS,
         * Strava) rather than this app's quieter marker vocabulary, which is the point — it is
         * meant to read as "your trail," not as another one of this app's markers. Kept distinct
         * from the planned-trip diamond's muted blue for the same reason.
         */
        private val BreadcrumbBase = Color(0xFF2979FF)

        /** The waypoint pin's amber, distinct from every other marker on this map. */
        private val WaypointBase = Color(0xFFE0A030)

        /**
         * Builds the overlay palette for [colorScheme].
         *
         * Pure and total: no Android types, no composition, no device. `MapPaletteTest` calls it
         * directly with both schemes, which is what makes the derivation rule checkable at all.
         */
        fun from(colorScheme: ColorScheme): MapPalette {
            // Markers sit ON the ground, so they shift toward the surface tone.
            fun onGround(base: Color): Int =
                lerp(base, colorScheme.surface, DERIVATION_SHIFT).copy(alpha = 1f).toArgb()

            // These two are themselves ground-like -- the stroke that separates overlapping dots,
            // and the glyph inside a filled area marker -- so they shift the other way.
            fun againstGround(base: Color): Int =
                lerp(base, colorScheme.onSurface, DERIVATION_SHIFT).copy(alpha = 1f).toArgb()

            return MapPalette(
                sightingDot = onGround(colorScheme.onSurface),
                sightingDotStroke = againstGround(colorScheme.surface),
                connector = onGround(colorScheme.secondary),
                areaMarkerBackground = onGround(colorScheme.primary),
                areaMarkerForeground = againstGround(colorScheme.onPrimary),
                plannedTrip = onGround(colorScheme.tertiary),
                searchCentre = onGround(SearchCentreBase),
                breadcrumb = onGround(BreadcrumbBase),
                waypoint = onGround(WaypointBase),
            )
        }
    }
}
