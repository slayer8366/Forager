package com.zynergylabs.forager.app.ui.map

import com.zynergylabs.forager.app.map.OFFLINE_STYLE_URL
import com.zynergylabs.forager.app.ui.map.layers.LayerRenderer
import com.zynergylabs.forager.app.ui.map.layers.MAP_LAYER_REGISTRY
import com.zynergylabs.forager.app.ui.map.layers.MapLayerIds
import com.zynergylabs.forager.app.ui.map.layers.MapLayersState
import com.zynergylabs.forager.app.ui.map.layers.orderedLayers
import java.io.File
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.maplibre.android.location.LocationLayersProbe

/**
 * Dispatch 2026-11-06-01 Amendment 1, I9 (RECORD -2183), from the S22 check on `tools/s22-combined` `505c55dda`: on the
 * phone, Topographical by day and Street day and night logged "…go below mapbox-location-background-layer (layer 5 of 8)",
 * and Topographical at night "(layer 187 of 190)", where the reading after I7 says those maps keep today's placement.
 * After a style reload `style.layers` already holds MapLibre's own position-dot layers, symbol layers on top, so the names
 * anchor counted them as the basemap's top run of names. I9: the anchor ignores every layer that is not the basemap's
 * own, MapLibre's position-dot layers and any layer the app adds, and Trail Map's anchor is unchanged.
 *
 * **What is real and what is modelled.** The basemaps' layers are read as [AppLinesUnderNamesTest] reads them: Trail Map
 * and the offline style from the Worker's copies, Topographical and Street from the JSON the app builds. The position
 * dot's layer ids are MapLibre's own constants ([LocationLayersProbe]); their order and types, and that they are in the
 * style when the app's callback runs, are modelled from `javap -c` on the pinned 13.5.0 AAR (the probe's doc comment).
 * `readNamesAnchor` itself needs a native `Style` and is not run; it hands every layer of `style.layers` to
 * [namesAnchorOf] and [namesAnchorLogLine], which are what is run here.
 *
 * Since I10 the anchor also reads the style file's own layer list; every check here is made both ways, with the file
 * read and with it unreadable (the id-only fallback), so I9's position-dot rule is held on each.
 */
class AppLinesUnderNamesForeignLayersTest {

    private val json = Json { ignoreUnknownKeys = true }

    private fun layersOfJson(text: String): List<BasemapLayer> =
        json.parseToJsonElement(text).jsonObject.getValue("layers").jsonArray.map { element ->
            val layer = element.jsonObject
            BasemapLayer(layer.getValue("id").jsonPrimitive.content, layer.getValue("type").jsonPrimitive.content == "symbol")
        }

    private fun workerStyle(file: String): List<BasemapLayer> = layersOfJson(File("../server/pmtiles-worker/src/$file").readText())

    /** [layers]' style file read (I10), and unreadable, so the id-only fallback is checked too. */
    private fun readingsOf(layers: List<BasemapLayer>): List<StyleFileLayers> =
        listOf(StyleFileLayers.Read(layers.map { it.id }.toSet()), StyleFileLayers.Unreadable("modelled"))

    private fun labelOf(reading: StyleFileLayers): String = if (reading is StyleFileLayers.Read) "file read" else "file unreadable"

    /** MapLibre's position-dot layers in each of the two orders 13.5.0 adds them in, bottom to top. */
    private val locationOrders: Map<String, List<BasemapLayer>> = mapOf(
        "bearing on top" to LocationLayersProbe.bearingOnTop(),
        "bearing below" to LocationLayersProbe.bearingBelow(),
    ).mapValues { (_, layers) -> layers.map { BasemapLayer(it[0] as String, it[1] as Boolean) } }

    private val locationIds = locationOrders.getValue("bearing on top").map { it.id }

    /** Every layer the app adds on a style load: the registry's own (not the style's band) and the adds after it. */
    private val appIds: List<String> =
        orderedLayers(MAP_LAYER_REGISTRY, MapLayersState.DEFAULT).filter { it.renderer != LayerRenderer.STYLE_LAYER }.map { it.id } +
            listOf(
                RouteHomeIds.STRAIGHT_LAYER, RouteHomeIds.STRAIGHT_OFFLINE_LAYER, RouteHomeIds.PASSED_LAYER, RouteHomeIds.AHEAD_CASING_LAYER,
                RouteHomeIds.AHEAD_LAYER, RouteHomeIds.AHEAD_DOTTED_LAYER, RouteHomeIds.AHEAD_STRAIGHT_LAYER, RouteHomeIds.ARRIVED_LAYER,
                PlottedRouteIds.CASING_LAYER, PlottedRouteIds.STRAIGHT_CASING_LAYER, PlottedRouteIds.TRAIL_LAYER, PlottedRouteIds.STRAIGHT_LAYER,
                PlottedRouteIds.STOPS_LAYER, WaypointNameIds.LAYER, WaypointNameIds.STOP_LAYER,
                FanOutIds.LEGS_CASING_LAYER, FanOutIds.LEGS_LAYER, FanOutIds.DOTS_LAYER, FanOutIds.ICONS_LAYER,
            )

    private val trailFirstNameOf = mapOf(
        "trail-style-v4.json" to MapLayerIds.REFERENCE_FS_NUMBER,
        "street-style-v4.json" to MapLayerIds.REFERENCE_FS_NUMBER,
        "street-style-v3.json" to "address_label",
        "street-style-v2.json" to "address_label",
        "street-style-v1.json" to "address_label",
    )

    /**
     * Every map that keeps today's placement, by name: the picture maps by day and night, Topographical at night, offline.
     * Each is its style's layers as loaded, and the style file's readings ([readingsOf]); at night the file is Trail Map's
     * alone, the picture being the builder's.
     */
    private fun keptMaps(): Map<String, Pair<List<BasemapLayer>, List<StyleFileLayers>>> {
        val maps = linkedMapOf<String, Pair<List<BasemapLayer>, List<StyleFileLayers>>>()
        for (basemap in Basemap.entries) {
            for (night in listOf(false, true)) {
                val source = mapStyleSourceFor(basemap, night = night, useOfflineTiles = false)
                if (source is MapStyleSource.Json) layersOfJson(source.json).let { maps["$basemap night=$night"] = it to readingsOf(it) }
            }
        }
        val trail = workerStyle("trail-style-v4.json")
        maps["${Basemap.OPEN_TOPO_MAP} night=true"] = (trail + BasemapLayer(RASTER_LAYER_ID, namesOrIcons = false)) to readingsOf(trail)
        maps["offline"] = workerStyle("offline-style.json").let { it to readingsOf(it) }
        return maps
    }

    @Test
    fun `with MapLibre's position-dot layers in the style, every map but Trail Map keeps today's placement`() {
        val maps = keptMaps()
        // Not a loop over nothing: Topographical by day and at night, and the offline style, are always among them.
        assertTrue(maps.keys.toString(), "${Basemap.OPEN_TOPO_MAP} night=false" in maps && "${Basemap.OPEN_TOPO_MAP} night=true" in maps && "offline" in maps)
        for ((name, modelled) in maps) {
            val (basemap, readings) = modelled
            for (file in readings) {
                assertNull("$name (${labelOf(file)}): a control, no anchor without the position dot", namesAnchorOf(basemap, file))
                for ((order, dot) in locationOrders) {
                    val anchor = namesAnchorOf(basemap + dot, file)
                    assertNull("$name with the position dot ($order, ${labelOf(file)}): MapLibre's layers are not the map's names", anchor)
                    for (spec in MAP_LAYER_REGISTRY) {
                        assertEquals("$name ($order): ${spec.id} goes on top, as today", OverlayPlacement.OnTop, overlayPlacement(spec, anchor))
                    }
                }
            }
        }
    }

    @Test
    fun `with MapLibre's position-dot layers in the style, every served Trail Map keeps its own anchor`() {
        for ((file, firstName) in trailFirstNameOf) {
            val basemap = workerStyle(file)
            for (reading in readingsOf(basemap)) {
                assertEquals("$file (${labelOf(reading)}): a control, the anchor without the position dot", firstName, namesAnchorOf(basemap, reading))
                for ((order, dot) in locationOrders) {
                    assertEquals("$file with the position dot ($order, ${labelOf(reading)})", firstName, namesAnchorOf(basemap + dot, reading))
                }
            }
        }
    }

    @Test
    fun `layers the app adds are not the map's own either, whatever their type`() {
        // Today none is in the style when the anchor is read (setStyle drops them, and the anchor is read before the first
        // add); I9 asks for the anchor not to count them should that ever change.
        for (namesOrIcons in listOf(true, false)) {
            val app = appIds.map { BasemapLayer(it, namesOrIcons) }
            for ((file, firstName) in trailFirstNameOf) {
                val basemap = workerStyle(file)
                for (reading in readingsOf(basemap)) {
                    assertEquals("$file with the app's layers on top (symbol=$namesOrIcons, ${labelOf(reading)})", firstName, namesAnchorOf(basemap + app, reading))
                }
            }
            for ((name, modelled) in keptMaps()) {
                for (reading in modelled.second) {
                    assertNull("$name with the app's layers on top (symbol=$namesOrIcons, ${labelOf(reading)})", namesAnchorOf(modelled.first + app, reading))
                }
            }
        }
    }

    @Test
    fun `no served map names a layer as MapLibre's position dot or the app do`() {
        // A control: what the anchor leaves out must never be a layer of a basemap itself.
        val foreign = (locationIds + appIds).toSet()
        val styles = keptMaps().mapValues { it.value.first } + trailFirstNameOf.keys.associateWith { workerStyle(it) }
        for ((name, basemap) in styles) {
            for (layer in basemap) {
                assertFalse("$name: ${layer.id} is the map's own", layer.id in foreign || layer.id.startsWith("mapbox-location-"))
            }
        }
    }

    @Test
    fun `the style-load log line for a picture map with the position dot says today's placement, and what it left out`() {
        val topo = layersOfJson((mapStyleSourceFor(Basemap.OPEN_TOPO_MAP, night = false, useOfflineTiles = false) as MapStyleSource.Json).json)
        val layers = topo + locationOrders.getValue("bearing on top")
        for (file in readingsOf(topo)) {
            val line = namesAnchorLogLine(namesAnchorOf(layers, file), layers, file)
            assertTrue(line, line.contains("today's placement"))
            assertTrue(line, line.contains("its top layer $RASTER_LAYER_ID "))
            assertTrue(line, line.contains("(${topo.size} layers)"))
            for (id in locationIds) assertTrue("$id named as left out: $line", line.contains(id))
        }
    }

    @Test
    fun `the style-load log line for Trail Map with the position dot counts only the map's own layers`() {
        val trail = workerStyle("trail-style-v4.json")
        val layers = trail + locationOrders.getValue("bearing on top")
        for (file in readingsOf(trail)) {
            val line = namesAnchorLogLine(namesAnchorOf(layers, file), layers, file)
            assertTrue(line, line.contains("below ${MapLayerIds.REFERENCE_FS_NUMBER} (layer 116 of ${trail.size})"))
            for (id in locationIds) assertTrue("$id named as left out: $line", line.contains(id))
        }
    }

    @Test
    fun `the offline style is the one the offline switch loads`() {
        // Ties keptMaps' "offline" entry to the style the app loads, as AppLinesUnderNamesTest does.
        assertEquals(MapStyleSource.Uri(OFFLINE_STYLE_URL), mapStyleSourceFor(Basemap.FORAGER_TRAIL, night = false, useOfflineTiles = true))
    }
}
