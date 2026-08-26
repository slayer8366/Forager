package com.forager.app.ui.map

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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
                "${basemap.name}: the style JSON's attribution must match Basemap.attribution exactly.",
                basemap.attribution,
                source.getValue("attribution").jsonPrimitive.content,
            )
        }
    }

    /**
     * The regression guard named in `BasemapStyles.kt`'s own doc comment: a style with no `glyphs`
     * URL renders every `text-field` (the numbered area-marker labels) as nothing, silently — hit
     * for real once already and fixed before `MapLibreBasemapPreviewActivity` (the scaffolding that
     * found it) was deleted. Checked for every basemap, not just one, since [SightingsMap] adds the
     * label layer on top of whichever basemap happens to be active.
     */
    @Test
    fun `every basemap's style JSON declares a glyphs URL`() {
        Basemap.entries.forEach { basemap ->
            assertEquals(
                "${basemap.name}: missing or wrong glyphs URL means area-marker number labels silently render as nothing.",
                GLYPHS_URL_TEMPLATE,
                parsedStyle(basemap).getValue("glyphs").jsonPrimitive.content,
            )
        }
    }

    /**
     * USGS uses ArcGIS's `tile/{z}/{y}/{x}` — row before column — not the `{z}/{x}/{y}` the
     * OSM-derived pair use. Getting that backwards is the failure mode worth a test: transposed
     * coordinates return a perfectly valid tile from the wrong place on Earth, so the map looks like
     * it works. Asserted against the real style JSON's `tiles` array, the same discipline the deleted
     * test applied to osmdroid's built `getTileURLString` output.
     */
    @Test
    fun `the USGS source declares ArcGIS row-column order in its tile template`() {
        assertEquals(
            "https://basemap.nationalmap.gov/arcgis/rest/services/USGSImageryOnly/MapServer/tile/{z}/{y}/{x}",
            tileTemplateFor(Basemap.USGS_IMAGERY_ONLY),
        )
    }

    /** The contrast case: the OSM-derived sources put x before y, so the two orderings are demonstrably different. */
    @Test
    fun `the OSM-derived sources declare x-y order in their tile template`() {
        assertEquals("https://tile.openstreetmap.org/{z}/{x}/{y}.png", tileTemplateFor(Basemap.OSM_STANDARD))
        assertEquals("https://a.tile.opentopomap.org/{z}/{x}/{y}.png", tileTemplateFor(Basemap.OPEN_TOPO_MAP))
    }

    /**
     * USGS Imagery really does stop lower than the OSM standard map, in the JSON MapLibre actually
     * reads — not just in [Basemap]'s own field, which `BasemapTest` already covers. A future
     * `styleJsonFor` bug that dropped or miscomputed `maxzoom` for one basemap but not the other
     * would fail here even if `BasemapTest` stayed green.
     */
    @Test
    fun `USGS Imagery's declared maxzoom is lower than the OSM standard map's`() {
        val usgsMaxZoom = parsedStyle(Basemap.USGS_IMAGERY_ONLY).getValue("sources").jsonObject
            .getValue(RASTER_SOURCE_ID).jsonObject.getValue("maxzoom").jsonPrimitive.int
        val osmMaxZoom = parsedStyle(Basemap.OSM_STANDARD).getValue("sources").jsonObject
            .getValue(RASTER_SOURCE_ID).jsonObject.getValue("maxzoom").jsonPrimitive.int
        assertTrue("USGS Imagery ($usgsMaxZoom) is expected to cap lower than OpenStreetMap ($osmMaxZoom).", usgsMaxZoom < osmMaxZoom)
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
