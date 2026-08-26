package com.forager.app.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb

/**
 * The nine colours the map overlay draws with, as `android.graphics` ARGB ints, in a day and a
 * night variant.
 *
 * MapLibre renders on a native canvas and cannot read a Compose `ColorScheme`, so `SightingsMap`
 * still takes ints — it just stops *defining* them. Before this existed, nine raw literals lived
 * beside the layer code with nothing governing them ("tag 04" in
 * docs/plans/understory-design-system.md): every palette change had to find and edit all nine.
 *
 * ## Why this is keyed on a map mode, not on the app theme
 *
 * An earlier revision derived these from the ambient `ColorScheme`, so the map followed the
 * device's light/dark setting. That was built, measured, and abandoned, because the basemap it
 * draws on is fixed raster — USGS Topo, USGS Imagery Topo, OpenTopoMap, OSM Standard, none of
 * which changes with the device theme. Varying the marks by theme moved the mark without moving
 * the ground, and measurably: markers *darkened* in dark theme against a basemap that did not
 * darken, and the route connector and waypoint pin collapsed to 0.044 apart in Oklab (against
 * 0.12+ in light), which is the exact pair a user has to tell apart.
 *
 * Night mode replaces it. It is a property of the map, chosen for the conditions the map is being
 * read in, and it dims the basemap as well as re-colouring the marks — so here the ground moves
 * too, which is what makes a night variant meaningful at all.
 *
 * ## Why both palettes are authored by hand
 *
 * Three uniform transforms were tried and each satisfied one constraint by breaking the other:
 * lerping toward the surface tone darkened everything; lerping toward a warm off-white fixed
 * contrast but washed the hues together (worst pair 0.038); raising Oklab lightness with chroma
 * preserved fixed that but pushed the two blues into the sRGB gamut edge, where they clipped and
 * converged to 0.026 apart. That is structural, not a constant left untuned: lifting a saturated
 * colour far enough to clear a dark ground runs it out of gamut.
 *
 * So [NIGHT] is authored, the way [DAY] already was. Nine colours is a small enough set to choose
 * deliberately, and `MapPaletteTest` holds both to the two properties that matter — legibility
 * against the ground each is drawn on, and separability from each other.
 *
 * **[DAY] is byte-for-byte the palette that shipped before this type existed.** Those values were
 * tuned on hardware (see `SightingsMap`'s own history on the sighting-dot stroke), so this change
 * moves them into one place without changing a single one of them.
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

        /** Unchanged from the pre-existing, hardware-tuned constants. */
        val DAY = MapPalette(
            sightingDot = 0xFF3B2E24.toInt(),
            sightingDotStroke = Color.White.toArgb(),
            connector = 0xFFC97B3D.toInt(),
            areaMarkerBackground = 0xFF2E5339.toInt(),
            areaMarkerForeground = Color.White.toArgb(),
            plannedTrip = 0xFF3B6EA5.toInt(),
            searchCentre = 0xFFB33B3B.toInt(),
            breadcrumb = 0xFF2979FF.toInt(),
            waypoint = 0xFFE0A030.toInt(),
        )

        /**
         * For a dimmed basemap. Every mark is light where its day counterpart is dark, and the two
         * that are marks-upon-marks invert with them: the stroke that separates overlapping
         * sighting dots is white on dark dots by day and near-black on light dots at night, and
         * the area-marker glyph does the same. Their legibility is against the marker they sit on,
         * never against the tile, which is how `MapPaletteTest` checks them.
         *
         * Two hue choices are deliberate departures from the day palette rather than lightened
         * versions of it, both forced by measurement:
         *
         *  - **Planned trip moves blue → periwinkle.** Lightened, the muted planned-trip blue and
         *    the saturated breadcrumb blue converge. Moving one toward violet keeps them apart
         *    while leaving the breadcrumb on the GPS-track blue convention its day value follows.
         *  - **Search centre moves red → rose.** Lightened, red lands next to the lightened
         *    connector orange. Rose is still unmistakably the "you searched here" pin and clears
         *    the connector by a comfortable margin.
         */
        val NIGHT = MapPalette(
            sightingDot = 0xFFC9BBA6.toInt(),
            sightingDotStroke = 0xFF2A2622.toInt(),
            connector = 0xFFEF8F3C.toInt(),
            areaMarkerBackground = 0xFF7FB08A.toInt(),
            areaMarkerForeground = 0xFF16301D.toInt(),
            plannedTrip = 0xFFAE9BE8.toInt(),
            searchCentre = 0xFFFF6F92.toInt(),
            breadcrumb = 0xFF5FB0FF.toInt(),
            waypoint = 0xFFFFD65E.toInt(),
        )

        /**
         * The luminance the dimmed basemap is assumed to land at, as a reference colour for
         * contrast checks.
         *
         * **Provisional**, and treated as such — the same status and for the same reason as
         * `MotionPrecedence.MARKER_CLUSTERING_THRESHOLD`. It is a modelled value for a pale topo
         * tile under night mode's raster dimming, not a sampled one, and real tiles vary hugely
         * within a single basemap (a snowfield and a forest canopy are not the same ground).
         * `MapPaletteTest` uses it to bound regressions; it does not establish that anything is
         * legible. Only hardware does that — see the README's "Not yet verified" section.
         */
        val NIGHT_TILE_REFERENCE = 0xFF3E3D39.toInt()

        /** The reference pale topo tile the day palette is checked against. Provisional, as above. */
        val DAY_TILE_REFERENCE = 0xFFE8E4DC.toInt()

        fun forMode(night: Boolean): MapPalette = if (night) NIGHT else DAY
    }
}
