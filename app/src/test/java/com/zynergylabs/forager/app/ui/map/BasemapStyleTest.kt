package com.zynergylabs.forager.app.ui.map

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/**
 * What [styleJsonFor] actually produces — the MapLibre-side successor to the deleted
 * `BasemapTileSourceTest`, which pinned the same set of facts against osmdroid's `TileSourceFactory`.
 *
 * ## Why a plain JVM test, not Robolectric
 *
 * [Basemap] and `BasemapStyles.kt` are both pure Kotlin with zero imports (verified: neither file has
 * an `import` line) — [styleJsonFor] builds a string, nothing more. Unlike the deleted test, which
 * needed Robolectric because osmdroid's `TileSourceFactory` sources reach into `android.graphics` to
 * decode a tile, there is no Android surface here to stand up, so no Robolectric runtime is needed.
 *
 * ## What this cannot check
 *
 * That MapLibre actually accepts this JSON and renders tiles from it — [styleJsonFor]'s output is
 * fed to `Style.Builder.fromJson`, and `Style` itself has no public constructor and cannot be built
 * off a real `NativeMap` (verified with `javap` against the pinned `org.maplibre.gl:android-sdk:13.5.0`
 * artifact: every `Layer`/`Source` subclass constructor calls a `native initialize`, so even
 * constructing one throws `UnsatisfiedLinkError` outside a real device or emulator). That gap is
 * hardware-only, the same class of gap `scripts/verify-usgs-basemap.sh` already covers for the raw
 * tile endpoints themselves — this test only pins the JSON [Basemap] hands to the renderer.
 */
class BasemapStyleTest {

    private val json = Json { ignoreUnknownKeys = true }

    private fun parsedStyle(basemap: Basemap) = json.parseToJsonElement(styleJsonFor(basemap)).jsonObject

    /**
     * Dispatch 2026-10-18-04, item 1: MapLibre's demo host (`demotiles.maplibre.org`) is not a production service
     * (Juniper's research, `docs/audits/2026-10-18-map-services-policy-and-cost-research.md` Q4: "Dev only"). Every style
     * the app builds, day and night, for every basemap, is read in full: no URL in it may point there.
     */
    @Test
    fun `no style the app builds asks MapLibre's demo server for anything`() {
        Basemap.entries.forEach { basemap ->
            listOf(false, true).forEach { night ->
                val style = styleJsonFor(basemap, night)
                assertFalse("${basemap.name} (night=$night) still names the demo host: $style", style.contains("demotiles.maplibre.org"))
            }
        }
    }

    @Test
    fun `every basemap produces valid, parseable style JSON`() {
        // The real assertion is that parseToJsonElement above doesn't throw for any basemap; this
        // just gives every entry its own failure message if one does.
        Basemap.entries.forEach { basemap ->
            val style = parsedStyle(basemap)
            assertEquals("${basemap.name}: style version must be 8", 8, style.getValue("version").jsonPrimitive.int)
        }
    }

    /**
     * The drift guard for [Basemap.maxZoom], now landing on the style's own `"maxzoom"` rather than
     * an osmdroid tile source's declared maximum. `SightingsMap` installs this same number a second
     * way, via `MapLibreMap.setMaxZoomPreference` — see [Basemap]'s class doc comment for why both are
     * needed. This test only covers the source-level declaration.
     */
    @Test
    fun `the style's declared maxzoom matches Basemap's own operating limit`() {
        Basemap.entries.forEach { basemap ->
            val source = parsedStyle(basemap).getValue("sources").jsonObject.getValue(RASTER_SOURCE_ID).jsonObject
            assertEquals(
                "${basemap.name}: styleJsonFor's maxzoom must equal Basemap.maxZoom, the app's own operating limit.",
                basemap.maxZoom,
                source.getValue("maxzoom").jsonPrimitive.int,
            )
        }
    }

    /**
     * The attribution check. Unlike osmdroid's `CopyrightOverlay` (which drew nothing for an empty
     * `getCopyrightNotice()`), MapLibre's tap-to-reveal attribution control is not the only place
     * this string lands — [SightingsMap] also renders it as an always-visible caption, per
     * [Basemap.attribution]'s own doc comment — but the style JSON is still the value that control
     * reads, so it must carry the real credit line too.
     */
    @Test
    fun `every basemap's style JSON carries its full attribution string`() {
        Basemap.entries.forEach { basemap ->
            val source = parsedStyle(basemap).getValue("sources").jsonObject.getValue(RASTER_SOURCE_ID).jsonObject
            assertEquals(
                "${basemap.name}: the style JSON's attribution must match Basemap.fullAttribution exactly.",
                basemap.fullAttribution,
                source.getValue("attribution").jsonPrimitive.content,
            )
        }
    }

    /**
     * Dispatch 2026-10-18-04, item 3, the owner's "option c": OpenTopoMap's full credit, as its own page asks for it,
     * goes in the tap-to-reveal attribution, which MapLibre builds from each style source's `attribution`. Read at
     * topo night too, where the style has the Street source below it with its own credit.
     */
    @Test
    fun `OpenTopoMap's tap-to-reveal credit is its full credit, day and night`() {
        val full = "© OpenStreetMap contributors, SRTM | © OpenTopoMap (CC-BY-SA)"
        listOf(false, true).forEach { night ->
            val sources = json.parseToJsonElement(styleJsonFor(Basemap.OPEN_TOPO_MAP, night)).jsonObject.getValue("sources").jsonObject
            assertEquals("night=$night", full, sources.getValue(RASTER_SOURCE_ID).jsonObject.getValue("attribution").jsonPrimitive.content)
        }
        val night = json.parseToJsonElement(styleJsonFor(Basemap.OPEN_TOPO_MAP, night = true)).jsonObject.getValue("sources").jsonObject
        val attributions = night.values.map { it.jsonObject.getValue("attribution").jsonPrimitive.content }
        assertEquals(listOf(Basemap.OSM_STANDARD.fullAttribution, full).sorted(), attributions.sorted())
    }

    /**
     * Dispatch 2026-10-18-04, item 1 (owner: "remove the fonts requests, but keep the text"). No style declares a
     * `glyphs` URL, so MapLibre has nowhere to fetch fonts from. This replaces the test that every style declared one:
     * that guarded the numbered area-marker labels, which left the app with the foraging-areas feature (`192db6c7`),
     * and no layer the app adds has drawn map text since.
     */
    @Test
    fun `no style declares a glyphs URL, day or night`() {
        Basemap.entries.forEach { basemap ->
            listOf(false, true).forEach { night ->
                val style = json.parseToJsonElement(styleJsonFor(basemap, night)).jsonObject
                assertFalse("${basemap.name} (night=$night) still declares glyphs: ${style["glyphs"]}", style.containsKey("glyphs"))
            }
        }
    }

    /**
     * "Keep the text", as the owner confirmed it: the place and trail names are printed into the raster tile images
     * themselves, so what keeps them is every raster layer still drawing its basemap's own tile template. Read for every
     * source of every style, day and night (topo night has two).
     */
    @Test
    fun `the raster tiles that carry the map's printed names are untouched`() {
        Basemap.entries.forEach { basemap ->
            listOf(false, true).forEach { night ->
                val style = json.parseToJsonElement(styleJsonFor(basemap, night)).jsonObject
                val sources = style.getValue("sources").jsonObject
                assertEquals(
                    "${basemap.name} (night=$night): the basemap's own tiles",
                    listOf(basemap.tileUrlTemplate),
                    sources.getValue(RASTER_SOURCE_ID).jsonObject.getValue("tiles").jsonArray.map { it.jsonPrimitive.content },
                )
                sources.values.forEach { assertEquals("raster", it.jsonObject.getValue("type").jsonPrimitive.content) }
                style.getValue("layers").jsonArray.forEach { assertEquals("raster", it.jsonObject.getValue("type").jsonPrimitive.content) }
            }
        }
    }

    /**
     * The OSM-derived sources put x before y. This was the contrast case to a USGS test pinning ArcGIS's
     * row-before-column order, removed with Satellite (dispatch 2026-09-28-708); it stays as the pin on
     * the two templates the app still uses.
     */
    @Test
    fun `the OSM-derived sources declare x-y order in their tile template`() {
        assertEquals("https://tile.openstreetmap.org/{z}/{x}/{y}.png", tileTemplateFor(Basemap.OSM_STANDARD))
        assertEquals("https://a.tile.opentopomap.org/{z}/{x}/{y}.png", tileTemplateFor(Basemap.OPEN_TOPO_MAP))
    }

    /** Every basemap's raster layer must actually reference the source declared alongside it, or MapLibre renders nothing. */
    @Test
    fun `every basemap's raster layer references its own raster source`() {
        Basemap.entries.forEach { basemap ->
            val layers = parsedStyle(basemap).getValue("layers").jsonArray
            assertEquals(1, layers.size)
            val layer = layers[0].jsonObject
            assertEquals("raster", layer.getValue("type").jsonPrimitive.content)
            assertEquals(RASTER_SOURCE_ID, layer.getValue("source").jsonPrimitive.content)
        }
    }

    private fun tileTemplateFor(basemap: Basemap): String =
        parsedStyle(basemap).getValue("sources").jsonObject.getValue(RASTER_SOURCE_ID).jsonObject
            .getValue("tiles").jsonArray.single().jsonPrimitive.content
}
