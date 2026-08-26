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
 *
 * ## Second pass: warm-shifted and dimmed, short of true night-vision preservation
 *
 * The first [NIGHT] cut solved legibility against a dimmed basemap and nothing else — several
 * markers ran through full-saturation blue (`breadcrumb` was `#5FB0FF`) or sat far above the
 * contrast floor for no reason ([waypoint] at 7.78:1 against a 4.0:1 target). A field app used
 * after dark has a second, different concern a legibility-only palette does not address: a bright,
 * blue-heavy screen fights the user's own dark adaptation every time they check it.
 *
 * **True scotopic preservation — red-dominant, blue and green suppressed almost entirely, the way
 * astronomy and tactical tools do it — was considered and rejected.** This map differentiates
 * seven roles by hue (`breadcrumb` blue for the GPS-track convention, `plannedTrip` violet for the
 * route, `searchCentre` red for the search pin, and so on); a near-monochrome red palette leaves
 * only one hue to work with and collapses that differentiation entirely. The compromise built here
 * instead: every marker keeps the hue family [DAY] already established, but the blue channel and
 * overall luminance come down wherever the 4.0:1 contrast floor has slack to give. `waypoint` had
 * the most room (7.78:1) and was dimmed the most; `searchCentre` had almost none (4.11:1) and was
 * left at its measured luminance, warmed only in hue.
 *
 * An unconstrained version of this search — minimize blue with no other bound — technically clears
 * every floor `MapPaletteTest` enforces but zeroes the blue channel on every marker, which turns
 * `plannedTrip` and `breadcrumb` into indistinguishable oranges. That is tag-08's defect again,
 * reintroduced by the fix meant to avoid it. So the hue each marker was authored with is a bound
 * the search respects, not a value it is free to erase.
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
         *  - **Planned trip moves blue → dusty violet.** Lightened, the muted planned-trip blue and
         *    the saturated breadcrumb blue converge. Moving one toward violet keeps them apart
         *    while leaving the breadcrumb on the GPS-track blue convention its day value follows.
         *  - **Search centre moves red → coral.** Lightened, red lands next to the lightened
         *    connector orange. Coral is still unmistakably the "you searched here" pin and clears
         *    the connector by a comfortable margin.
         *
         * Warm-shifted and dimmed per the second pass documented on the class above: every value
         * here keeps [DAY]'s hue family for that marker, with the blue channel and luminance pulled
         * down by however much slack that marker's contrast floor (4.0:1 against
         * [NIGHT_TILE_REFERENCE]) allowed. `MapPaletteTest` holds this to the same floors the first
         * cut was held to — none were loosened to make room for the warmer values.
         *
         * ## Third pass: re-tuned for a brighter ground, 2026-08-26
         *
         * Reported unusably dark on hardware (`raster-brightness-max` was 0.22). Raising ground
         * brightness at all turns out to have zero slack against the first two passes' values —
         * every marker sat right at its 4.0:1 floor already, so lifting the ground even 9% (0.22 →
         * 0.24) alone pulled `connector`/`waypoint` (both amber-family) to 0.097 apart in Oklab,
         * below the 0.099 separation floor: the exact "two things a user must tell apart become
         * one colour" defect (tag-08) this whole system exists to prevent. A pure-lightness lift
         * for every marker fails the same way at any brightness increase — Oklab's in-gamut region
         * narrows near white, so hue-similar marks converge as they're lifted, independent of which
         * pair happens to be closest.
         *
         * So this pass moves the ground substantially (`raster-brightness-max` 0.22 → 0.32,
         * [NIGHT_TILE_REFERENCE] more than doubling in luminance) and re-solves for every marker
         * jointly: lightness, hue, and chroma all move together, not lightness alone, with hue
         * drift bounded to keep each marker in the same colour family it already reads as (nothing
         * here departs from its day-palette hue by more than the plannedTrip/searchCentre
         * departures already accepted in the second pass above). Found by a constrained search
         * (simulated annealing over hue/chroma per marker, hard-rejecting any candidate that cannot
         * reach the 4.0:1 floor at any lightness), not hand-picked — the same kind of search the
         * class comment above already describes trying and rejecting when left *unconstrained*
         * ("zeroes the blue channel on every marker"). The floors themselves did not move: every
         * value below still clears 4.0:1 against [NIGHT_TILE_REFERENCE] and 0.099 pairwise
         * separation from every other marker, same as the first two passes.
         *
         * A materially brighter ground was checked and found infeasible at this design's floors:
         * past roughly `raster-brightness-max` 0.44 no colour at all — including pure white — can
         * reach 4.0:1 against the resulting ground, since `(1.0+0.05)/(lref+0.05)` itself drops
         * below 4.0. 0.32 was chosen with real headroom under that ceiling (max achievable ≈7:1),
         * not chosen at the edge of what's mathematically possible. A further push (`0.42`,
         * relaxing the contrast floor to WCAG's 3:1 non-text minimum to make room) was built and
         * rejected on review: low-contrast marks against a brightened ground make the map harder
         * to read, not easier — the opposite of what a legibility-motivated night mode is for. The
         * ground and the 4.0:1 floor both stay as this pass set them.
         *
         * ## Fourth pass: warmer, less cyan-leaning hues, 2026-08-26
         *
         * Requested directly: red/warm tones cost the eye's dark adaptation less than blue/cyan
         * ones, so where a marker can move warmer without spending any of the margin above,
         * it should. Re-solved at the same ground and the same two floors — nothing here is a
         * looser constraint, only a different optimum within the third pass's own feasible region
         * (search re-run with a warmth term added to the objective, gated behind a hard requirement
         * that pairwise separation never drop below what the third pass already shipped, 0.106, so
         * chasing warmth cannot quietly spend the margin that pass earned).
         *
         * Two markers had real room: `connector` moves from a yellow-leaning 80° to a true
         * orange 44°, and `breadcrumb` moves from 123° (sky-blue, adjacent to cyan) to 96°
         * (violet-blue, adjacent to indigo) — magenta-ward rather than cyan-ward, per direct
         * instruction, while staying recognisably blue for the GPS-track convention its day value
         * follows (see the class comment above). `areaMarkerBackground` and `waypoint` shift a few
         * degrees toward yellow within their own green/olive families; `sightingDot`,
         * `searchCentre` and `plannedTrip` were already near the objective's optimum and barely
         * move. No marker's family identity changes — the search stayed inside the same ≤45°
         * per-marker drift bound the third pass used.
         */
        val NIGHT = MapPalette(
            sightingDot = 0xFFC8C69E.toInt(),
            sightingDotStroke = 0xFF25211D.toInt(),
            connector = 0xFFFFB64B.toInt(),
            areaMarkerBackground = 0xFF74DD00.toInt(),
            areaMarkerForeground = 0xFF1B2C17.toInt(),
            plannedTrip = 0xFFD9B7F5.toInt(),
            searchCentre = 0xFFFFB0B0.toInt(),
            breadcrumb = 0xFF89CBFF.toInt(),
            waypoint = 0xFFCBCB04.toInt(),
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
         *
         * Scaled proportionally from the first pass's `0x3E3D39` for `raster-brightness-max`'s
         * move from 0.22 to 0.32 (`BasemapStyles.kt`) — the same modelling approach that value
         * used, not a fresh sample. See [NIGHT]'s own doc comment, "Third pass," for why the ground
         * moved this far.
         */
        val NIGHT_TILE_REFERENCE = 0xFF5A5953.toInt()

        /** The reference pale topo tile the day palette is checked against. Provisional, as above. */
        val DAY_TILE_REFERENCE = 0xFFE8E4DC.toInt()

        fun forMode(night: Boolean): MapPalette = if (night) NIGHT else DAY
    }
}
