package com.zynergylabs.forager.app.ui.map

import com.zynergylabs.forager.app.ui.map.layers.MAP_LAYER_REGISTRY
import com.zynergylabs.forager.app.ui.map.layers.MapLayerIds
import java.io.File
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.maplibre.android.constants.MapLibreConstants
import org.maplibre.android.location.LocationLayersProbe

/**
 * Dispatch 2026-11-06-01 Amendment 1, I10 (RECORD -2183), from the S22 check on build 4825 (`tools/s22-combined`
 * `3529c63ff`): I9's filter left out MapLibre's position-dot layers but not its annotation layer
 * `org.maplibre.annotations.points`, a symbol layer, so Topographical by day, Street day and night and Topographical at
 * night anchored under it ("layer 1 of 2", "183 of 184") instead of keeping today's placement. Trail Map passed. I10: the
 * basemap's own layers are those that came from the style file; MapLibre's own of any kind and the app's are left out.
 *
 * **What is real and what is modelled.** The style files are real: Trail Map and the offline style from the Worker's
 * copies, Topographical and Street from the JSON the app builds, each read by the app's own [styleFileLayersOf]. The
 * annotation layer's id is MapLibre's public constant ([MapLibreConstants.LAYER_ID_ANNOTATIONS]); the position dot's are
 * its constants too ([LocationLayersProbe]). Modelled, from `javap -c` on the pinned 13.5.0 AAR and the phone's counts:
 * the loaded order, the style file's layers, then `Style.Builder`'s (added below the annotation layer by
 * `Style.onDidFinishLoadingStyle`), then the annotation layer, then the dot; and that `Style.getJson()` returns the file
 * the style was loaded from. That last is the native core's, not readable from the AAR's Java, and is what the S22
 * logcat step confirms (the log line's "declares (N)").
 */
class AppLinesUnderNamesMapLibreLayersTest {

    private val json = Json { ignoreUnknownKeys = true }

    private fun layersOfJson(text: String): List<BasemapLayer> =
        json.parseToJsonElement(text).jsonObject.getValue("layers").jsonArray.map { element ->
            val layer = element.jsonObject
            BasemapLayer(layer.getValue("id").jsonPrimitive.content, layer.getValue("type").jsonPrimitive.content == "symbol")
        }

    private fun workerText(file: String): String = File("../server/pmtiles-worker/src/$file").readText()

    /** MapLibre's annotation layer, a symbol layer, as the native core adds it on every style load. */
    private val annotations = BasemapLayer(MapLibreConstants.LAYER_ID_ANNOTATIONS, namesOrIcons = true)

    private val dots: List<List<BasemapLayer>> = listOf(LocationLayersProbe.bearingOnTop(), LocationLayersProbe.bearingBelow())
        .map { order -> order.map { BasemapLayer(it[0] as String, it[1] as Boolean) } }

    /** A style as loaded: its file's text, and the layers the builder adds below the annotation layer. */
    private data class Loaded(val fileText: String, val builderLayers: List<BasemapLayer> = emptyList())

    private fun Loaded.fileLayers() = layersOfJson(fileText)

    /** `style.layers` as `readNamesAnchor` finds it, the annotation layer in, and the position dot [dot] on top. */
    private fun Loaded.styleLayers(dot: List<BasemapLayer> = emptyList()) = fileLayers() + builderLayers + annotations + dot

    /** Every map that keeps today's placement: the picture maps by day and night, Topographical at night, offline. */
    private fun keptMaps(): Map<String, Loaded> {
        val maps = linkedMapOf<String, Loaded>()
        for (basemap in Basemap.entries) {
            for (night in listOf(false, true)) {
                val source = mapStyleSourceFor(basemap, night = night, useOfflineTiles = false)
                if (source is MapStyleSource.Json) maps["$basemap night=$night"] = Loaded(source.json)
            }
        }
        // trailWithTopoOverBuilder: Trail Map's file by URL, the picture by Style.Builder.withLayer.
        maps["${Basemap.OPEN_TOPO_MAP} night=true"] = Loaded(workerText("trail-style-v4.json"), listOf(BasemapLayer(RASTER_LAYER_ID, namesOrIcons = false)))
        maps["offline"] = Loaded(workerText("offline-style.json"))
        return maps
    }

    private val trailFirstNameOf = mapOf(
        "trail-style-v4.json" to MapLayerIds.REFERENCE_FS_NUMBER,
        "street-style-v4.json" to MapLayerIds.REFERENCE_FS_NUMBER,
        "street-style-v3.json" to "address_label",
        "street-style-v2.json" to "address_label",
        "street-style-v1.json" to "address_label",
    )

    private val unreadable = StyleFileLayers.Unreadable("modelled")

    @Test
    fun `with MapLibre's annotation layer in the style, every map but Trail Map keeps today's placement, the file read or not`() {
        val maps = keptMaps()
        assertTrue(maps.keys.toString(), "${Basemap.OPEN_TOPO_MAP} night=false" in maps && "${Basemap.OPEN_TOPO_MAP} night=true" in maps && "offline" in maps)
        for ((name, loaded) in maps) {
            val read = styleFileLayersOf(loaded.fileText)
            assertTrue("$name: the style file is read", read is StyleFileLayers.Read)
            for ((label, reading) in listOf("file read" to read, "file unreadable" to unreadable)) {
                for (dot in listOf(emptyList<BasemapLayer>()) + dots) {
                    val anchor = namesAnchorOf(loaded.styleLayers(dot), reading)
                    assertNull("$name ($label, ${dot.size} dot layers): MapLibre's annotation layer is not the map's names", anchor)
                    for (spec in MAP_LAYER_REGISTRY) {
                        assertEquals("$name ($label): ${spec.id} goes on top, as today", OverlayPlacement.OnTop, overlayPlacement(spec, anchor))
                    }
                }
            }
        }
    }

    @Test
    fun `with MapLibre's annotation layer in the style, every served Trail Map keeps its own anchor`() {
        // A control: the annotation layer sits on top of Trail Map's top run of names, so its first is the same (the phone's
        // Trail Map line passed). It passes before I10 too.
        for ((file, firstName) in trailFirstNameOf) {
            val loaded = Loaded(workerText(file))
            for (reading in listOf(styleFileLayersOf(loaded.fileText), unreadable)) {
                for (dot in listOf(emptyList<BasemapLayer>()) + dots) {
                    assertEquals("$file (${dot.size} dot layers)", firstName, namesAnchorOf(loaded.styleLayers(dot), reading))
                }
            }
        }
    }

    @Test
    fun `a run-time layer no id rule knows is not the map's own when the style file is read`() {
        // Why the style file is the rule and the prefixes only its fallback: a layer added at run time under an id no list
        // names (a later MapLibre's, say) is still not in the file.
        val unknown = BasemapLayer("a-layer-no-prefix-names", namesOrIcons = true)
        for ((name, loaded) in keptMaps()) {
            val layers = loaded.styleLayers(dots.first()) + unknown
            assertNull("$name: ${unknown.id} is not in the style file", namesAnchorOf(layers, styleFileLayersOf(loaded.fileText)))
        }
        for ((file, firstName) in trailFirstNameOf) {
            val loaded = Loaded(workerText(file))
            assertEquals(file, firstName, namesAnchorOf(loaded.styleLayers(dots.first()) + unknown, styleFileLayersOf(loaded.fileText)))
        }
    }

    @Test
    fun `MapLibre's annotation layer id is one the id fallback leaves out`() {
        assertEquals("org.maplibre.annotations.points", MapLibreConstants.LAYER_ID_ANNOTATIONS)
        assertTrue(MAPLIBRE_LAYER_ID_PREFIXES.toString(), MAPLIBRE_LAYER_ID_PREFIXES.any { MapLibreConstants.LAYER_ID_ANNOTATIONS.startsWith(it) })
    }

    @Test
    fun `no served style file declares a layer under one of MapLibre's id prefixes`() {
        // A control: the id fallback must never leave out a layer a basemap declares itself.
        val files = keptMaps().mapValues { it.value.fileText } + trailFirstNameOf.keys.associateWith { workerText(it) }
        for ((name, text) in files) {
            for (layer in layersOfJson(text)) {
                assertTrue("$name: ${layer.id}", MAPLIBRE_LAYER_ID_PREFIXES.none { layer.id.startsWith(it) })
            }
        }
    }

    @Test
    fun `the style-load log lines read as the phone's should, counting only the map's own layers`() {
        // Topographical by day: the phone logged "layer 1 of 2" with the annotation layer counted.
        val topo = Loaded((mapStyleSourceFor(Basemap.OPEN_TOPO_MAP, night = false, useOfflineTiles = false) as MapStyleSource.Json).json)
        val topoFile = topo.fileLayers()
        val topoLine = namesAnchorLogLine(namesAnchorOf(topo.styleLayers(dots.first()), styleFileLayersOf(topo.fileText)), topo.styleLayers(dots.first()), styleFileLayersOf(topo.fileText))
        assertTrue(topoLine, topoLine.contains("today's placement") && topoLine.contains("(${topoFile.size} layers)") && topoLine.contains("its top layer $RASTER_LAYER_ID "))
        assertTrue(topoLine, topoLine.contains("declares (${topoFile.size})"))
        assertTrue(topoLine, topoLine.contains("Left out as not the map's own: ${MapLibreConstants.LAYER_ID_ANNOTATIONS}, mapbox-location-"))

        // Topographical at night: the phone logged "183 of 184"; the map's own are Trail Map's 182 and the picture.
        val night = keptMaps().getValue("${Basemap.OPEN_TOPO_MAP} night=true")
        val nightLayers = night.styleLayers(dots.first())
        val nightFile = styleFileLayersOf(night.fileText)
        val nightLine = namesAnchorLogLine(namesAnchorOf(nightLayers, nightFile), nightLayers, nightFile)
        val trailCount = night.fileLayers().size
        assertTrue(nightLine, nightLine.contains("today's placement") && nightLine.contains("(${trailCount + 1} layers)") && nightLine.contains("its top layer $RASTER_LAYER_ID "))

        // Trail Map v4: the phone logged "below address_label (layer 117 of 183)" before I8's style is live.
        val trail = Loaded(workerText("trail-style-v4.json"))
        val trailLine = namesAnchorLogLine(namesAnchorOf(trail.styleLayers(), styleFileLayersOf(trail.fileText)), trail.styleLayers(), styleFileLayersOf(trail.fileText))
        assertTrue(trailLine, trailLine.contains("below ${MapLayerIds.REFERENCE_FS_NUMBER} (layer 116 of $trailCount)"))
    }

    @Test
    fun `the style file's layer list is every layer the file declares, and a file that cannot be read says so`() {
        for (file in trailFirstNameOf.keys + "offline-style.json") {
            val text = workerText(file)
            assertEquals(file, StyleFileLayers.Read(layersOfJson(text).map { it.id }.toSet()), styleFileLayersOf(text))
        }
        for (text in listOf(null, "", "  ", "not json", "{\"version\": 8}", "{\"version\": 8, \"layers\": []}", "{\"layers\": [{\"type\": \"fill\"}]}")) {
            val reading = styleFileLayersOf(text)
            assertTrue("$text: $reading", reading is StyleFileLayers.Unreadable)
            val layers = Loaded((mapStyleSourceFor(Basemap.OPEN_TOPO_MAP, night = false, useOfflineTiles = false) as MapStyleSource.Json).json).styleLayers()
            val line = namesAnchorLogLine(namesAnchorOf(layers, reading), layers, reading)
            assertTrue(line, line.contains("The style file could not be read (${(reading as StyleFileLayers.Unreadable).reason}); layers left out by id alone."))
        }
    }
}
