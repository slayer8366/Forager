package com.zynergylabs.forager.app.ui.map.layers

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Tap precedence (map layers L0a, A4; L0 design ruling 3, "Markers, then lines, then colour"; the
 * owner's ruling 4 on `prompts/preserved/2026-09-27-30.md`: the offline outline is tappable and its
 * fill is not, and a finger-sized box is queried so thin lines are hittable). Headless: the native
 * hit test only produces [TapHit]s, and these functions decide among them.
 */
class TapPrecedenceTest {

    private val order = orderedLayers(MAP_LAYER_REGISTRY, MapLayersState.DEFAULT)

    private fun hit(layerId: String, featureId: String? = "id-$layerId") = TapHit(layerId, featureId)

    @Test
    fun `every record marker, line and colour field is queried, never a casing, the offline fill, the search centre or the recording trail`() {
        // M1 (owner's ruling 4, "Not tappable"): the search centre and the recording trail left the
        // list; the colour fields joined it (planner's M1 ruling: cells are tappable).
        assertEquals(
            listOf(
                MapLayerIds.FORECAST_CHICKEN_OF_THE_WOODS,
                MapLayerIds.FORECAST_CHANTERELLES,
                MapLayerIds.OFFLINE_REGION_OUTLINE,
                MapLayerIds.KEPT_TRACKS,
                MapLayerIds.SIGHTINGS,
                MapLayerIds.PLANNED_TRIPS,
                MapLayerIds.WAYPOINTS,
                MapLayerIds.FINDS,
                MapLayerIds.PHOTOS,
            ),
            tappableLayerIds(order),
        )
    }

    @Test
    fun `a marker wins over a line whichever order the hits arrive in`() {
        val marker = hit(MapLayerIds.SIGHTINGS)
        val line = hit(MapLayerIds.KEPT_TRACKS)
        assertEquals(marker, tapWinner(listOf(line, marker), order))
        assertEquals(marker, tapWinner(listOf(marker, line), order))
    }

    @Test
    fun `the offline outline is a line - it loses to a marker and wins over nothing tappable`() {
        val outline = hit(MapLayerIds.OFFLINE_REGION_OUTLINE)
        assertEquals(hit(MapLayerIds.FINDS), tapWinner(listOf(outline, hit(MapLayerIds.FINDS)), order))
        assertEquals(outline, tapWinner(listOf(hit(MapLayerIds.OFFLINE_REGION_FILL), outline), order))
    }

    @Test
    fun `within the markers the layer drawn on top wins`() {
        assertEquals(hit(MapLayerIds.WAYPOINTS), tapWinner(listOf(hit(MapLayerIds.WAYPOINTS), hit(MapLayerIds.SIGHTINGS)), order))
        assertEquals(hit(MapLayerIds.PHOTOS), tapWinner(listOf(hit(MapLayerIds.FINDS), hit(MapLayerIds.PHOTOS), hit(MapLayerIds.PLANNED_TRIPS)), order))
    }

    @Test
    fun `within the lines the layer drawn on top wins`() {
        // M1: the recording trail is no longer a line a tap lands on, so the two tappable lines left
        // are compared (kept tracks draw above the offline outline).
        assertEquals(hit(MapLayerIds.KEPT_TRACKS), tapWinner(listOf(hit(MapLayerIds.OFFLINE_REGION_OUTLINE), hit(MapLayerIds.KEPT_TRACKS)), order))
        assertEquals(hit(MapLayerIds.KEPT_TRACKS), tapWinner(listOf(hit(MapLayerIds.KEPT_TRACKS), hit(MapLayerIds.OFFLINE_REGION_OUTLINE)), order))
    }

    @Test
    fun `within one layer the first hit the query returned wins`() {
        val first = hit(MapLayerIds.WAYPOINTS, "wp-1")
        val second = hit(MapLayerIds.WAYPOINTS, "wp-2")
        assertEquals(first, tapWinner(listOf(first, second), order))
    }

    @Test
    fun `hits only on untappable or unknown layers win nothing, and no hits win nothing`() {
        assertNull(tapWinner(listOf(hit(MapLayerIds.BREADCRUMB_CASING), hit(MapLayerIds.OFFLINE_REGION_FILL), hit("not-a-layer")), order))
        // M1 (owner's ruling 4): the search centre and the recording trail take no taps.
        assertNull(tapWinner(listOf(hit(MapLayerIds.SEARCH_CENTRE), hit(MapLayerIds.BREADCRUMB)), order))
        assertNull(tapWinner(emptyList(), order))
    }

    @Test
    fun `a colour field wins only when no marker or line is hit`() {
        val field = MapLayerSpec(
            id = "field",
            kind = LayerKind.COLOUR_FIELD,
            renderer = LayerRenderer.FILL,
            sourceId = "field-src",
            zGroup = ZGroup.COLOUR_FIELDS,
            paletteRole = null,
            userToggleable = true,
            userOpacity = true,
            userReorderable = true,
            tapGroup = TapGroup.COLOUR_FIELD,
            baseOpacities = listOf(BaseOpacity(OpacityProperty.FILL, 0.6f)),
        )
        val withField = orderedLayers(listOf(field) + MAP_LAYER_REGISTRY, MapLayersState.DEFAULT)
        assertEquals(hit("field"), tapWinner(listOf(hit("field"), hit(MapLayerIds.OFFLINE_REGION_FILL)), withField))
        assertEquals(hit(MapLayerIds.KEPT_TRACKS), tapWinner(listOf(hit("field"), hit(MapLayerIds.KEPT_TRACKS)), withField))
    }

    // resolveTap: the point decides, the box only when the point has nothing tappable.

    @Test
    fun `what lies under the point decides, and the box is not queried`() {
        var boxQueries = 0
        val winner = resolveTap(listOf(hit(MapLayerIds.SIGHTINGS)), { boxQueries++; listOf(hit(MapLayerIds.PHOTOS)) }, order)
        assertEquals(hit(MapLayerIds.SIGHTINGS), winner)
        assertEquals(0, boxQueries)
    }

    @Test
    fun `with nothing tappable under the point the box decides`() {
        assertEquals(hit(MapLayerIds.KEPT_TRACKS), resolveTap(emptyList(), { listOf(hit(MapLayerIds.KEPT_TRACKS)) }, order))
        assertEquals(
            hit(MapLayerIds.KEPT_TRACKS),
            resolveTap(listOf(hit(MapLayerIds.OFFLINE_REGION_FILL)), { listOf(hit(MapLayerIds.KEPT_TRACKS)) }, order),
        )
    }

    // M1: colour-field cells are tappable, but a cell wins only when it is under the finger and
    // neither the point nor the 48 dp box finds a marker or a line (planner's ruling on Q5).

    private val cell = hit(MapLayerIds.FORECAST_CHANTERELLES, "45.5,-122.6")

    @Test
    fun `a cell under the finger with nothing else near wins`() {
        assertEquals(cell, resolveTap(listOf(cell), { listOf(cell) }, order))
    }

    // Each test below first shows the cell winning alone, so it can only pass once cells are
    // tappable; the second half is the precedence the ruling adds.

    @Test
    fun `a line in the box beats the cell under the finger`() {
        assertEquals(cell, resolveTap(listOf(cell), { listOf(cell) }, order))
        assertEquals(hit(MapLayerIds.KEPT_TRACKS), resolveTap(listOf(cell), { listOf(cell, hit(MapLayerIds.KEPT_TRACKS)) }, order))
    }

    @Test
    fun `a marker in the box beats the cell under the finger`() {
        assertEquals(cell, resolveTap(listOf(cell), { listOf(cell) }, order))
        assertEquals(hit(MapLayerIds.WAYPOINTS), resolveTap(listOf(cell), { listOf(hit(MapLayerIds.WAYPOINTS), cell) }, order))
    }

    @Test
    fun `at the edge of a cell area a cell only in the box never wins`() {
        // Inside the scored area the cell under the finger wins; one step past it the finger is on an
        // empty cell and the box reaches a scored one, which must not win.
        assertEquals(cell, resolveTap(listOf(cell), { listOf(cell) }, order))
        assertNull(resolveTap(emptyList(), { listOf(cell) }, order))
        assertNull(resolveTap(listOf(hit(MapLayerIds.OFFLINE_REGION_FILL)), { listOf(cell) }, order))
    }

    @Test
    fun `a marker under the finger still wins without the box being queried, with a cell under it too`() {
        assertEquals(cell, resolveTap(listOf(cell), { listOf(cell) }, order))
        var boxQueries = 0
        val winner = resolveTap(listOf(cell, hit(MapLayerIds.FINDS)), { boxQueries++; emptyList() }, order)
        assertEquals(hit(MapLayerIds.FINDS), winner)
        assertEquals(0, boxQueries)
    }

    // mapTapOutcome: what a resolved tap fires (owner's M1 ruling 1, "Bubble only").

    @Test
    fun `a feature tap is a feature tap and nothing else`() {
        assertEquals(MapTapOutcome.OnFeature(MapLayerIds.WAYPOINTS, "wp-1"), mapTapOutcome(hit(MapLayerIds.WAYPOINTS, "wp-1")))
        assertEquals(MapTapOutcome.OnFeature(MapLayerIds.FORECAST_CHANTERELLES, "45.5,-122.6"), mapTapOutcome(cell))
    }

    @Test
    fun `a sighting tap names its observation, and nothing tapped is a plain tap`() {
        assertEquals(MapTapOutcome.OnSighting(42L), mapTapOutcome(hit(MapLayerIds.SIGHTINGS, "42")))
        assertEquals(MapTapOutcome.OnSighting(null), mapTapOutcome(hit(MapLayerIds.SIGHTINGS, null)))
        assertEquals(MapTapOutcome.Plain, mapTapOutcome(null))
    }

    @Test
    fun `a feature with no id is reported as unidentified`() {
        assertEquals(MapTapOutcome.UnidentifiedFeature(MapLayerIds.FINDS), mapTapOutcome(hit(MapLayerIds.FINDS, null)))
    }

    @Test
    fun `nothing under the point or in the box wins nothing`() {
        assertNull(resolveTap(emptyList(), { emptyList() }, order))
    }

    @Test
    fun `the tap box is 48dp square, the minimum touch target`() {
        assertEquals(48f, TAP_BOX_DP)
    }
}
