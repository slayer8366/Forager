package com.zynergylabs.forager.app.ui.map

import com.zynergylabs.forager.app.map.OFFLINE_STYLE_URL
import com.zynergylabs.forager.app.map.TRAIL_STYLE_URL
import com.zynergylabs.forager.app.ui.map.layers.LayerRenderer
import com.zynergylabs.forager.app.ui.map.layers.MAP_LAYER_REGISTRY
import com.zynergylabs.forager.app.ui.map.layers.MapLayerIds
import com.zynergylabs.forager.app.ui.map.layers.MapLayersState
import com.zynergylabs.forager.app.ui.map.layers.ZGroup
import com.zynergylabs.forager.app.ui.map.layers.orderedLayers
import java.io.File
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Dispatch 2026-11-06-01 Amendment 1, category I (RECORD -2183). The owner, verbatim: "can we have street and trail names
 * overlap the navigation line rather than be written under?"; "All lines the app draws"; "Names and icons on top"; on
 * Trail Map "Under all names, trail no. too" (I5); for the forecast colours, the offline tint and the journal rings "Move
 * down with the lines (Recommended)" (I6); for the fan-out legs "Stay with the markers" (I7). The planner's reading
 * after I7: on Trail Map the app's block from the forecast colours up to its top line moves, in its present order, to
 * just under Trail Map's first name or icon layer; Topographical (day and night), Street and the offline style keep
 * today's placement.
 *
 * **What is real here and what is modelled.** Each basemap's layers are read from the real style: Trail Map from the
 * Worker's own copies of every served version (`server/pmtiles-worker/src`), Topographical and Street from the JSON the
 * app builds for them ([mapStyleSourceFor], [styleJsonFor]), the offline style from its Worker copy. The registry's
 * order is the app's own ([orderedLayers] of [MAP_LAYER_REGISTRY]), and each registry layer's place is the production
 * decision [overlayPlacement]. What cannot run here is `initializeOverlayLayers` itself: every MapLibre `Layer` and
 * `Style` needs the native library (`SightingsMapOverlayDataTest`'s doc comment), so the adds it makes after the
 * registry are modelled in [stackAfterOverlays] from their code: the way home above the kept tracks
 * (`RouteHomeLayers.kt`, `addRouteHomeLayers`), the plotted route and its stops below the way home's lowest line
 * (`PlottedRouteLayers.kt`, `addPlottedRouteLayers`), the waypoint names above the waypoints (`WaypointNameLayers.kt`),
 * and the fan-out on top (`FanOutLayers.kt`). That MapLibre honours the order on a phone is the device check's.
 */
class AppLinesUnderNamesTest {

    private val json = Json { ignoreUnknownKeys = true }

    /** Every Trail Map style the Worker serves: today's under both its addresses, and the frozen v3, v2 and v1. */
    private val trailStyleFiles = listOf("trail-style-v4.json", "street-style-v4.json", "street-style-v3.json", "street-style-v2.json", "street-style-v1.json")

    /**
     * The first of each served Trail Map's top run of names and icons. I8 (dispatch 2026-11-06-01 Amendment 1, RECORD -2183;
     * the owner: "Update v4 in place") moved the Forest Service trail number to the bottom of v4's labels, directly under
     * `address_label`, so on v4 (both addresses) the run starts with it. v3 keeps it among the roads, and v1 and v2 have
     * none, so their run starts at `address_label` as before.
     */
    private val firstNameOf = mapOf(
        "trail-style-v4.json" to MapLayerIds.REFERENCE_FS_NUMBER,
        "street-style-v4.json" to MapLayerIds.REFERENCE_FS_NUMBER,
        "street-style-v3.json" to "address_label",
        "street-style-v2.json" to "address_label",
        "street-style-v1.json" to "address_label",
    )

    private fun layersOfJson(text: String): List<BasemapLayer> =
        json.parseToJsonElement(text).jsonObject.getValue("layers").jsonArray.map { element ->
            val layer = element.jsonObject
            BasemapLayer(layer.getValue("id").jsonPrimitive.content, layer.getValue("type").jsonPrimitive.content == "symbol")
        }

    private fun workerStyle(file: String): List<BasemapLayer> = layersOfJson(File("../server/pmtiles-worker/src/$file").readText())

    /**
     * The style file of [layers] as `readNamesAnchor` reads it (I10): every layer here is one the file declares, so the
     * file's own list is those ids. Topographical at night, where the picture is not in the file, reads the real file.
     */
    private fun fileOf(layers: List<BasemapLayer>): StyleFileLayers = StyleFileLayers.Read(layers.map { it.id }.toSet())

    /** The registry's own layers, as `initializeOverlayLayers` walks them (the style's own band layers are not added). */
    private val registry = orderedLayers(MAP_LAYER_REGISTRY, MapLayersState.DEFAULT).filter { it.renderer != LayerRenderer.STYLE_LAYER }

    private val wayHomeLines = listOf(
        RouteHomeIds.PASSED_LAYER, RouteHomeIds.AHEAD_CASING_LAYER, RouteHomeIds.AHEAD_LAYER, RouteHomeIds.AHEAD_DOTTED_LAYER,
        RouteHomeIds.AHEAD_STRAIGHT_LAYER, RouteHomeIds.STRAIGHT_LAYER, RouteHomeIds.STRAIGHT_OFFLINE_LAYER,
    )
    private val plottedLines = listOf(PlottedRouteIds.CASING_LAYER, PlottedRouteIds.STRAIGHT_CASING_LAYER, PlottedRouteIds.TRAIL_LAYER, PlottedRouteIds.STRAIGHT_LAYER)
    private val fanOut = listOf(FanOutIds.LEGS_CASING_LAYER, FanOutIds.LEGS_LAYER, FanOutIds.DOTS_LAYER, FanOutIds.ICONS_LAYER)

    /** Every line the app draws but the fan-out's legs (I7): the registry's lines, the plotted route, the way home and the way to a waypoint. */
    private val appLines = registry.filter { it.zGroup == ZGroup.LINES }.map { it.id } + plottedLines + wayHomeLines

    /** The block that moves (I5, I6): the registry from the forecast colours up to its lines, and the routes, with the stop dots. */
    private val block = registry.filter { it.zGroup != ZGroup.MARKERS }.map { it.id } + plottedLines + PlottedRouteIds.STOPS_LAYER + wayHomeLines

    /** What stays on top: the markers, the arrival ring, the waypoint and stop names, the fan-out (I3, I7). */
    private val onTop = registry.filter { it.zGroup == ZGroup.MARKERS }.map { it.id } +
        listOf(RouteHomeIds.ARRIVED_LAYER, WaypointNameIds.LAYER, WaypointNameIds.STOP_LAYER) + fanOut

    /**
     * The style's layers after `initializeOverlayLayers` on [basemap], bottom to top, with the names anchor [anchor]
     * (`null` is today's placement). The registry goes where [overlayPlacement] says; the rest is modelled from the add
     * calls in `OverlayLayerSetup.kt` lines 94 to 104, in their order.
     */
    private fun stackAfterOverlays(basemap: List<BasemapLayer>, anchor: String? = namesAnchorOf(basemap, fileOf(basemap))): List<String> {
        val stack = basemap.map { it.id }.toMutableList()
        fun indexOf(id: String) = stack.indexOf(id).also { require(it >= 0) { "$id is not in the stack" } }
        for (spec in registry) {
            when (val placement = overlayPlacement(spec, anchor)) {
                OverlayPlacement.OnTop -> stack.add(spec.id)
                is OverlayPlacement.Below -> stack.add(indexOf(placement.layerId), spec.id)
            }
        }
        var above = MapLayerIds.KEPT_TRACKS
        for (id in wayHomeLines) { stack.add(indexOf(above) + 1, id); above = id }
        stack.add(indexOf(MapLayerIds.WAYPOINTS) + 1, RouteHomeIds.ARRIVED_LAYER)
        var below = RouteHomeIds.PASSED_LAYER
        for (id in plottedLines.asReversed()) { stack.add(indexOf(below), id); below = id }
        stack.add(indexOf(RouteHomeIds.PASSED_LAYER), PlottedRouteIds.STOPS_LAYER)
        stack.add(indexOf(MapLayerIds.WAYPOINTS) + 1, WaypointNameIds.LAYER)
        stack.add(indexOf(WaypointNameIds.LAYER) + 1, WaypointNameIds.STOP_LAYER)
        stack.addAll(fanOut)
        return stack
    }

    @Test
    fun `on every served Trail Map the app's lines go just under the first name, with the roads, rail and bridges under them`() {
        for (file in trailStyleFiles) {
            val basemap = workerStyle(file)
            val stack = stackAfterOverlays(basemap)
            val firstName = basemap.indexOfLast { !it.namesOrIcons } + 1
            val anchor = firstNameOf.getValue(file)
            assertEquals("$file: the app goes under the first of the top run of names and icons", anchor, namesAnchorOf(basemap, fileOf(basemap)))
            val lastBelow = basemap[firstName - 1].id
            val blockAt = block.map { stack.indexOf(it) }
            assertEquals("$file: the block is one run, directly above $lastBelow", stack.indexOf(lastBelow) + 1, blockAt.min())
            assertEquals("$file: the block is one run, directly under $anchor", stack.indexOf(anchor) - 1, blockAt.max())
            for (line in appLines) {
                for (name in basemap.drop(firstName)) {
                    assertTrue("$file: ${name.id} must draw over the app's line $line", stack.indexOf(name.id) > stack.indexOf(line))
                }
                for (road in basemap.take(firstName).filter { !it.namesOrIcons }) {
                    assertTrue("$file: the app's line $line must draw over ${road.id}", stack.indexOf(line) > stack.indexOf(road.id))
                }
            }
        }
    }

    @Test
    fun `on Trail Map v4 the roads and bridges stay under the lines`() {
        // The 2b-1 report's premise 1: before I8, Trail Map's first symbol layer was the Forest Service trail number, at index 84
        // of 182, with every road, rail line, bridge and boundary above it. Under that, a way home along a road would be drawn
        // under the road. Since I8 the number is in the top run, and this holds by the same rule.
        val basemap = workerStyle("trail-style-v4.json")
        val stack = stackAfterOverlays(basemap)
        for (road in listOf("roads_bridges_highway", "roads_bridges_major", "roads_major", "roads_rail", "boundaries")) {
            assertTrue("$road must stay under the way home", stack.indexOf(road) < stack.indexOf(RouteHomeIds.AHEAD_LAYER))
        }
    }

    @Test
    fun `on Trail Map v4 the Forest Service trail number draws over every app line, and the roads and bridges stay under them`() {
        // I5, the owner: "Under all names, trail no. too"; I8: "Update v4 in place". Before I8 the number sat at layer 84 of 182,
        // just above the trail lines and under the roads, so the app's lines, under address_label, drew over it.
        for (file in listOf("trail-style-v4.json", "street-style-v4.json")) {
            val basemap = workerStyle(file)
            val stack = stackAfterOverlays(basemap)
            val number = stack.indexOf(MapLayerIds.REFERENCE_FS_NUMBER)
            assertEquals("$file: the trail number is directly under address_label", stack.indexOf("address_label") - 1, number)
            for (line in appLines) assertTrue("$file: the trail number must draw over the app's line $line", number > stack.indexOf(line))
            for (road in listOf("roads_bridges_highway", "roads_bridges_major", "roads_major", "roads_rail", "boundaries")) {
                assertTrue("$file: $road must stay under the trail number and the way home", stack.indexOf(road) < stack.indexOf(RouteHomeIds.AHEAD_LAYER))
            }
        }
    }

    @Test
    fun `the block keeps its present order, and so does everything left on top`() {
        for (file in trailStyleFiles) {
            val basemap = workerStyle(file)
            val today = stackAfterOverlays(basemap, anchor = null)
            val moved = stackAfterOverlays(basemap)
            assertEquals("$file: the block's own order", today.filter { it in block }, moved.filter { it in block })
            assertEquals("$file: the markers, names and fan-out's own order", today.filter { it in onTop }, moved.filter { it in onTop })
        }
    }

    @Test
    fun `the forecast colours, the offline tint and the journal rings move down with the lines, under every line and under the names`() {
        // I6, the owner: "Move down with the lines (Recommended)", so the rings stay below all lines ("Move marker rings below all lines").
        val basemap = workerStyle("trail-style-v4.json")
        val stack = stackAfterOverlays(basemap)
        val beneath = COLOUR_FIELD_IDS + MapLayerIds.OFFLINE_REGION_FILL +
            listOf(MapLayerIds.JOURNAL_ENTRY_WAYPOINTS, MapLayerIds.JOURNAL_ENTRY_FINDS, MapLayerIds.JOURNAL_ENTRY_PHOTOS)
        for (id in beneath) {
            assertTrue("$id must draw under the names", stack.indexOf(id) < stack.indexOf(MapLayerIds.REFERENCE_FS_NUMBER))
            for (line in appLines) assertTrue("$id must draw under the app's line $line", stack.indexOf(id) < stack.indexOf(line))
        }
    }

    @Test
    fun `markers, waypoint names, the arrival ring and the fan-out with its legs stay over the whole of Trail Map`() {
        // I3 and I7, the owner: "Names and icons on top"; the fan-out legs "Stay with the markers".
        val basemap = workerStyle("trail-style-v4.json")
        val stack = stackAfterOverlays(basemap)
        val topOfBasemap = stack.indexOf(basemap.last().id)
        for (id in onTop) assertTrue("$id must stay over every Trail Map layer", stack.indexOf(id) > topOfBasemap)
        assertTrue("the stop dots move with the plotted route, under the way home", stack.indexOf(PlottedRouteIds.STOPS_LAYER) < stack.indexOf(RouteHomeIds.PASSED_LAYER))
        assertTrue("the stop dots sit over the plotted route", stack.indexOf(PlottedRouteIds.STOPS_LAYER) > stack.indexOf(PlottedRouteIds.STRAIGHT_LAYER))
    }

    @Test
    fun `Topographical by day, Street day and night and every other picture map keep today's placement`() {
        val checked = mutableListOf<String>()
        for (basemap in Basemap.entries) {
            for (night in listOf(false, true)) {
                val source = mapStyleSourceFor(basemap, night = night, useOfflineTiles = false)
                if (source !is MapStyleSource.Json) continue
                val layers = layersOfJson(source.json)
                assertNull("$basemap night=$night: a picture map has no names to go under", namesAnchorOf(layers, fileOf(layers)))
                assertEquals("$basemap night=$night: every app layer on top, as today", stackAfterOverlays(layers, anchor = null), stackAfterOverlays(layers))
                checked += "$basemap/$night"
            }
        }
        // Not a loop that saw nothing: every map but Trail Map is a picture style, day and night, except Topographical at night
        // (Trail Map under the picture, the next test). That is Topographical by day and, while it is registered, Street day and
        // night; Street is named nowhere here, so the app still builds with it deleted (CI's street-removable job).
        val expected = Basemap.entries.filter { it != Basemap.FORAGER_TRAIL }
            .flatMap { map -> listOf(false, true).filter { night -> !(map == Basemap.OPEN_TOPO_MAP && night) }.map { "$map/$it" } }
        assertEquals(expected, checked)
        assertTrue("Topographical by day is checked", "${Basemap.OPEN_TOPO_MAP}/false" in checked)
    }

    @Test
    fun `Topographical at night keeps today's placement, since its picture covers Trail Map's names`() {
        val source = mapStyleSourceFor(Basemap.OPEN_TOPO_MAP, night = true, useOfflineTiles = false)
        assertTrue("Topographical at night is Trail Map under the Topographical picture", source is MapStyleSource.TrailWithTopoOver)
        assertEquals(TRAIL_STYLE_URL, (source as MapStyleSource.TrailWithTopoOver).trailUri)
        // trailWithTopoOverBuilder: the Trail Map style by URL, then the picture added as the top layer.
        val layers = workerStyle("trail-style-v4.json") + BasemapLayer(RASTER_LAYER_ID, namesOrIcons = false)
        // The style file is Trail Map's alone; the picture is the builder's, and counts as the map's own (BUILDER_BASEMAP_LAYER_IDS).
        val anchor = namesAnchorOf(layers, styleFileLayersOf(File("../server/pmtiles-worker/src/trail-style-v4.json").readText()))
        assertNull(anchor)
        assertEquals(stackAfterOverlays(layers, anchor = null), stackAfterOverlays(layers, anchor))
    }

    @Test
    fun `the offline style keeps today's placement`() {
        assertEquals(MapStyleSource.Uri(OFFLINE_STYLE_URL), mapStyleSourceFor(Basemap.FORAGER_TRAIL, night = false, useOfflineTiles = true))
        val layers = workerStyle("offline-style.json")
        assertNull(namesAnchorOf(layers, fileOf(layers)))
        assertEquals(stackAfterOverlays(layers, anchor = null), stackAfterOverlays(layers))
    }

    @Test
    fun `the style-load log line says where the app's layers went and why`() {
        val trail = workerStyle("trail-style-v4.json")
        val under = namesAnchorLogLine(namesAnchorOf(trail, fileOf(trail)), trail, fileOf(trail))
        // Since I8 the trail number is the first name on v4, directly above the last bridge.
        assertTrue(under, under.contains("below ${MapLayerIds.REFERENCE_FS_NUMBER} (layer 116 of 182)"))
        val topo = layersOfJson((mapStyleSourceFor(Basemap.OPEN_TOPO_MAP, night = false, useOfflineTiles = false) as MapStyleSource.Json).json)
        val kept = namesAnchorLogLine(namesAnchorOf(topo, fileOf(topo)), topo, fileOf(topo))
        assertTrue(kept, kept.contains("today's placement") && kept.contains(topo.last().id))
    }

    private companion object {
        val COLOUR_FIELD_IDS = com.zynergylabs.forager.app.ui.map.layers.COLOUR_FIELDS.map { it.layerId }
    }
}
