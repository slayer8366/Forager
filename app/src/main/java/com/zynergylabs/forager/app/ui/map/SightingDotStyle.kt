package com.zynergylabs.forager.app.ui.map

import com.zynergylabs.forager.app.ui.theme.MapPalette
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.layers.PropertyValue

/**
 * The sighting dot's own paint: colour, radius, and a ring keyed off each feature's `"selected"`
 * property (see [sightingStrokeColorExpression], [sightingStrokeWidthExpression]). Shared by the
 * sighting layer and the fan-out's copy of it, so a fanned dot is the dot it was.
 */
internal fun sightingCircleProperties(palette: MapPalette): Array<PropertyValue<*>> = arrayOf(
    PropertyFactory.circleColor(palette.sightingDot),
    PropertyFactory.circleRadius(SIGHTING_DOT_RADIUS_PX),
    // See sightingStrokeColorExpression's own doc comment — it keys off each feature's own
    // "selected" property, so nothing here needs seeding with the current
    // focusedObservationId the way an id-comparison expression would. The width keys off the
    // same property: the selected ring is 3dp, every other ring 1.5dp (colour build C2, an
    // owner-approved tweak; see sightingStrokeWidthExpression).
    PropertyFactory.circleStrokeColor(sightingStrokeColorExpression(palette)),
    PropertyFactory.circleStrokeWidth(sightingStrokeWidthExpression()),
)

/**
 * The sighting layer's `circle-stroke-color`: [MapPalette.sightingDotStroke] (white by day) for
 * every dot, except [MapPalette.sightingDotStrokeSelected] (blue) for whichever one
 * [sightingsFeatureCollection]'s own `"selected"` property currently marks `true` — the ring
 * [ObservationBubble]'s own arrow points at, so the highlighted dot and the bubble naming it agree
 * even in a dense cluster where the arrow's own tip alone could land ambiguously close to a
 * neighbour.
 *
 * A fixed expression, not parameterized on `focusedObservationId`: an id-comparison
 * (`["==", ["get","observationId"], id]`, even coerced through `to-string` on both sides first)
 * was tried and confirmed — twice, on real hardware, alongside a doubled stroke width that also
 * showed no change — to never actually select anything once `setProperties` reaches the native
 * renderer, for reasons this project's own off-device tests can't diagnose (`Expression.equals`
 * only checks the built tree, never how MapLibre's GL engine evaluates a numeric or string `eq`).
 * Baking `"selected"` into each feature's own data in Kotlin, and reading it here as a plain
 * boolean condition (`["case", ["get","selected"], ...]`, no comparison at all), sidesteps the
 * entire class of doubt: there is no representation for a boolean read back from GeoJSON to
 * disagree with.
 *
 * A widened selected-stroke width (first 3.5px, then 4.5px, alongside a deeper blue) was tried as
 * a second signal on top of colour, on the reasoning that a hairline is hard to read by hue alone
 * at a glance — and reverted on request, twice, to keep the highlight to colour alone: see
 * [SIGHTING_DOT_STROKE_WIDTH_PX]'s own doc comment for that history. `circle-stroke-width` is a
 * flat constant for every dot now, selected or not; only this colour expression is data-driven.
 *
 * A plain function, not inlined into [initializeOverlayLayers]: both [Expression] and
 * [org.maplibre.android.style.layers.PropertyValue] carry no native methods (`javap` against the
 * pinned `org.maplibre.gl:android-sdk:13.5.0` artifact confirms neither), so unlike the
 * `Style`/`CircleLayer`/`GeoJsonSource` boundary [sightingsFeatureCollection]'s own doc comment
 * describes, the expression this builds is itself constructible and comparable
 * (`Expression.equals`) off a real device — `SightingsMapOverlayDataTest` exercises it directly.
 */
internal fun sightingStrokeColorExpression(palette: MapPalette): Expression =
    Expression.switchCase(
        Expression.get("selected"),
        Expression.color(palette.sightingDotStrokeSelected),
        Expression.color(palette.sightingDotStroke),
    )

/**
 * The sighting layer's `circle-stroke-width`: [SIGHTING_DOT_SELECTED_STROKE_WIDTH_PX] (3dp) for the
 * dot [sightingsFeatureCollection] marks `"selected"`, [SIGHTING_DOT_STROKE_WIDTH_PX] (1.5dp) for every
 * other. Keyed on the same boolean property as [sightingStrokeColorExpression], for the same reason.
 *
 * Colour build C2, an owner-approved tweak: the highlight is colour **and** width again. Widths of
 * 3.5 and 4.5px were tried before and reverted on request to keep the highlight to colour alone
 * ([SIGHTING_DOT_STROKE_WIDTH_PX] has that history); the owner approved 3dp on the glyph board.
 */
internal fun sightingStrokeWidthExpression(): Expression =
    Expression.switchCase(
        Expression.get("selected"),
        Expression.literal(SIGHTING_DOT_SELECTED_STROKE_WIDTH_PX),
        Expression.literal(SIGHTING_DOT_STROKE_WIDTH_PX),
    )

// The overlay's colours come from ui/theme/MapPalette.kt, hand-authored per role in a day and a
// night variant and chosen by the Night Maps toggle (MapPalette.forMode) -- not derived from the app
// theme; that type's doc comment has why. Only non-colour geometry constants remain here.

/**
 * The casing every marker except the sighting dot is outlined in, on each side of its fill
 * (colour build C2 (c), the planner's call): the tracks' casing lines are this much wider on each
 * side, and the marker bitmaps (`MarkerGlyphs.kt`) draw it into their own padding.
 */
internal const val CASING_WIDTH_DP = 1.5f

// The dot's fill opacity (0.7, about the deleted osmdroid version's 0xB3 alpha) and its ring's
// (0.85) are the registry's base opacities since map layers L0a (MAP_LAYER_REGISTRY).
private const val SIGHTING_DOT_RADIUS_PX = 9f

// The stroke that keeps individual dots distinguishable within a dense cluster — see this file's
// class doc comment, "The overlay colours", for the hardware finding this fixes. A light, near-
// opaque stroke (not translucent like the fill, its 0.85 opacity now in the registry) so the
// boundary itself stays crisp regardless of how many dots overlap or what opacity the fill
// composites to underneath.
//
// The selected dot's ring used to widen this on top of recolouring — first to 3.5px, then to
// 4.5px alongside MapPalette.sightingDotStrokeSelected moving to a deeper blue — and both were
// reverted on request, leaving the highlight to colour alone. Colour build C2 widens it again, to
// SIGHTING_DOT_SELECTED_STROKE_WIDTH_PX, as an owner-approved tweak (sightingStrokeWidthExpression).
internal const val SIGHTING_DOT_STROKE_WIDTH_PX = 1.5f
internal const val SIGHTING_DOT_SELECTED_STROKE_WIDTH_PX = 3f
