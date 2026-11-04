package com.zynergylabs.forager.app.ui.map

import com.zynergylabs.forager.app.map.TRAIL_STYLE_URL
import com.zynergylabs.forager.app.map.TRAIL_STYLE_V3_URL
import com.zynergylabs.forager.app.map.TRAIL_STYLE_V4_STREET_URL
import java.io.File
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Trail Map v4's clone layers take their originals' night (dispatch 2026-11-04-03). The first zooms (#474) record 78 clones
 * in `forager:layer-clones`; 13 of them are clones of layers with a named night colour, which they lost because the night
 * tables are keyed by layer id. Each style file the app can load as v4 is read here, through [trailNightTablesFor] with its
 * own URL and [withClones] of its own metadata, the chain `MapStyleReload` runs.
 */
class CloneLayersNightTest {

    /** Every copy of v4 the app can draw: the repository's two names, and the two the Worker serves. */
    private val v4Files = mapOf(
        "../map-style/forager-trail-v4.json" to TRAIL_STYLE_URL,
        "../map-style/forager-street-v4.json" to TRAIL_STYLE_V4_STREET_URL,
        "../server/pmtiles-worker/src/trail-style-v4.json" to TRAIL_STYLE_URL,
        "../server/pmtiles-worker/src/street-style-v4.json" to TRAIL_STYLE_V4_STREET_URL,
    )

    /** The 13 clones #474's body names as losing a night exception (checked against the style for this dispatch). */
    private val namedCases = setOf(
        "roads_major__tertiary", "roads_major__links", "roads_major__primary", "roads_major__secondary", "roads_minor__road",
        "boundaries__admin5", "boundaries__admin6", "boundaries__admin7", "boundaries__admin8",
        "roads_path_informal_z13__cycle_bridle",
        "landuse_edges__institutions", "landuse_edges__settled", "landuse_edges__sport",
    )

    private class V4(path: String, url: String) {
        val text = File(path).readText()
        val style = Json.parseToJsonElement(text).jsonObject
        val layers = style.getValue("layers").jsonArray.associateBy { it.jsonObject.getValue("id").jsonPrimitive.content }
        val clones = style.getValue("metadata").jsonObject.getValue(LAYER_CLONES_KEY).jsonObject.mapValues { it.value.jsonPrimitive.content }
        val tables = trailNightTablesFor(url).withClones(layerClonesOf(text))

        /** [id]'s night for [property], drawn with its own paint value: the outcome without the layer it was asked for. */
        fun nightOf(id: String, property: String): Any? {
            val layer = layers.getValue(id).jsonObject
            val value = layer["paint"]?.jsonObject?.get(property) ?: return null
            val outcome = trailNightRecolourOf(LayerColourProperty(id, property, colourValueOf(value)), layer["source-layer"]?.jsonPrimitive?.content, tables)
            return when (outcome) {
                is NightRecolour.Recoloured -> outcome.night
                is NightRecolour.Left -> "left: ${outcome.reason}"
            }
        }

        fun typeOf(id: String) = layers.getValue(id).jsonObject.getValue("type").jsonPrimitive.content
    }

    private fun v4(path: String) = V4(path, v4Files.getValue(path))

    @Test
    fun `every clone's night is its original's, for every colour property, in every v4 copy`() {
        for (path in v4Files.keys) {
            val v4 = v4(path)
            assertEquals("$path: clones recorded", 78, v4.clones.size)
            assertTrue("$path: the 13 named cases are among the clones", v4.clones.keys.containsAll(namedCases))
            val differing = mutableListOf<String>()
            var compared = 0
            for ((clone, original) in v4.clones) {
                for (property in NIGHT_RECOLOURED_PROPERTIES.getValue(v4.typeOf(clone))) {
                    val cloneNight = v4.nightOf(clone, property) ?: continue
                    compared++
                    if (cloneNight != v4.nightOf(original, property)) differing += "$clone/$property"
                }
            }
            assertEquals("$path: clones whose night differs from their original's", emptyList<String>(), differing)
            assertTrue("$path: colour properties compared ($compared)", compared >= 78)
        }
    }

    @Test
    fun `the 13 named cases draw #474's intended night colours`() {
        for (path in v4Files.keys) {
            val v4 = v4(path)
            fun pairs(id: String) = dayToNight(v4.layers.getValue(id).jsonObject.getValue("paint").jsonObject.getValue("line-color"), v4.nightOf(id, "line-color"))

            for (id in listOf("roads_major__tertiary", "roads_major__links", "roads_minor__road")) {
                assertEquals("$path $id: the day white", "#333333", pairs(id).getValue("#ffffff"))
            }
            // Their own kinds never draw the white; what they draw stays the inversion, as #474's table says.
            for ((id, kindColours) in mapOf("roads_major__primary" to listOf("#efc46c", "#f5d28c"), "roads_major__secondary" to listOf("#ecd58c", "#f8e3ae"))) {
                for (day in kindColours) assertEquals("$path $id $day", inversionOf(day), pairs(id).getValue(day))
                assertEquals("$path $id: the white it never draws, as roads_major's", "#333333", pairs(id).getValue("#ffffff"))
            }
            for (id in listOf("boundaries__admin5", "boundaries__admin6", "boundaries__admin7", "boundaries__admin8")) {
                assertEquals("$path $id", StyleColourValue.Literal("#bdbdbd"), v4.nightOf(id, "line-color"))
            }
            assertEquals("$path cycle_bridle", StyleColourValue.Literal("#bb8a6f"), v4.nightOf("roads_path_informal_z13__cycle_bridle", "line-color"))

            val edges = mapOf(
                "landuse_edges__institutions" to mapOf("#c9c0bf" to "#3c3231", "#c9c3bd" to "#3b352e"),
                "landuse_edges__settled" to mapOf(
                    "#c3bfba" to "#1d1b18", "#d0bdb9" to "#241a18", "#c6b3c3" to "#382735", "#ccceb4" to "#131207",
                    "#b0c286" to "#3a4b14", "#88b99d" to "#255239",
                ),
                "landuse_edges__sport" to mapOf("#b4caa8" to "#203216", "#88b99d" to "#255239"),
            )
            for ((id, expected) in edges) {
                val drawn = pairs(id)
                for ((day, night) in expected) assertEquals("$path $id $day", night, drawn.getValue(day))
            }
        }
    }

    @Test
    fun `a clone added later takes its original's night with no change to the app`() {
        val json = """
            {"metadata": {"$LAYER_CLONES_KEY": {"boundaries__future": "boundaries", "landuse_edges__future": "landuse_edges", "roads_rail__x": "roads_rail"}},
             "layers": []}
        """.trimIndent()
        val tables = TRAIL_V4_NIGHT_TABLES.withClones(layerClonesOf(json))
        assertEquals("#bdbdbd", tables.layerColours["boundaries__future"])
        assertEquals(TRAIL_V4_NIGHT_TABLES.lineColours.getValue("landuse_edges"), tables.lineColours["landuse_edges__future"])
        // v4's rail has no layer exception (the plain inversion), so its clone gets none either.
        assertEquals(null, tables.layerColours["roads_rail__x"])
        assertEquals(null, tables.lineColours["roads_rail__x"])
        // The tables keyed by colour or source layer are not touched.
        assertSame(TRAIL_V4_NIGHT_TABLES.fillColours, tables.fillColours)
        assertSame(TRAIL_V4_NIGHT_TABLES.hillshade, tables.hillshade)
    }

    @Test
    fun `a layer the tables name itself keeps its own entry over its original's`() {
        val tables = TrailNightTables(layerColours = mapOf("a" to "#111111", "a__b" to "#222222"), fillColours = emptyMap())
        val cloned = tables.withClones(LayerClones.Recorded(mapOf("a__b" to "a", "a__c" to "a")))
        assertEquals(mapOf("a" to "#111111", "a__b" to "#222222", "a__c" to "#111111"), cloned.layerColours)
    }

    @Test
    fun `the clones are read as the style records them`() {
        val v4 = v4("../map-style/forager-trail-v4.json")
        assertEquals(LayerClones.Recorded(v4.clones), layerClonesOf(v4.text))
    }

    @Test
    fun `a style that records no clones keeps today's tables`() {
        // Dispatch 2026-11-04-05 B1: a style with no metadata at all is told apart from one whose metadata lacks the key.
        assertEquals(LayerClones.NotRecorded(hasMetadata = true), layerClonesOf(File("../map-style/forager-street-v3.json").readText()))
        assertEquals(LayerClones.NotRecorded(hasMetadata = false), layerClonesOf("{}"))
        assertEquals(LayerClones.NotRecorded(hasMetadata = true), layerClonesOf("""{"metadata": {}}"""))
        assertSame(TRAIL_NIGHT_TABLES, trailNightTablesFor(TRAIL_STYLE_V3_URL).withClones(LayerClones.NotRecorded(hasMetadata = true)))
        assertSame(TRAIL_V4_NIGHT_TABLES, TRAIL_V4_NIGHT_TABLES.withClones(LayerClones.NotRecorded(hasMetadata = false)))
    }

    @Test
    fun `unreadable clones fall back to today's tables, with a reason`() {
        val unreadable = listOf(
            "not json",
            "[]",
            """{"metadata": []}""",
            """{"metadata": {"$LAYER_CLONES_KEY": ["boundaries__admin5", "boundaries"]}}""",
            """{"metadata": {"$LAYER_CLONES_KEY": {"boundaries__admin5": 5}}}""",
            """{"metadata": {"$LAYER_CLONES_KEY": {"boundaries__admin5": null}}}""",
        )
        for (json in unreadable) {
            val clones = layerClonesOf(json)
            assertTrue("$json: $clones", clones is LayerClones.Unreadable && clones.reason.isNotBlank())
            assertSame(json, TRAIL_V4_NIGHT_TABLES, TRAIL_V4_NIGHT_TABLES.withClones(clones))
        }
    }

    @Test
    fun `the night pass gives the tables the loaded style's clones`() {
        val reload = File("src/main/java/com/zynergylabs/forager/app/ui/map/MapStyleReload.kt").readText()
        assertEquals(1, Regex("""trailNightTablesForSource\(source\)\.withLayerClonesOf\(style\)""").findAll(reload).count())
    }

    /** Each day colour literal in [day] against the night colour at the same place in [night], both as `#rrggbb`. */
    private fun dayToNight(day: JsonElement, night: Any?): Map<String, String> {
        val nightElement = when (night) {
            is StyleColourValue.Literal -> JsonPrimitive(night.text)
            is StyleColourValue.Expression -> Json.parseToJsonElement(night.json)
            else -> error("no night colour: $night")
        }
        val out = mutableMapOf<String, String>()
        fun walk(d: JsonElement, n: JsonElement) {
            when {
                d is JsonPrimitive && d.isString && d.content.startsWith("#") -> {
                    val dayHex = d.content.lowercase()
                    val nightHex = hexOf(n.jsonPrimitive.content)
                    val earlier = out.put(dayHex, nightHex)
                    assertTrue("$dayHex drew both $earlier and $nightHex", earlier == null || earlier == nightHex)
                }
                d is JsonArray -> d.indices.forEach { walk(d[it], (n as JsonArray)[it]) }
                d is JsonObject -> d.keys.forEach { walk(d.getValue(it), (n as JsonObject).getValue(it)) }
            }
        }
        walk(day, nightElement)
        return out
    }

    private fun hexOf(colour: String): String {
        if (colour.startsWith("#")) return colour.lowercase()
        val channels = Regex("""rgba?\((\d+),\s*(\d+),\s*(\d+)""").find(colour)!!.groupValues.drop(1).map { it.toInt() }
        return "#%02x%02x%02x".format(channels[0], channels[1], channels[2])
    }

    private fun inversionOf(hex: String): String =
        "#%06x".format(nightColorOf((0xFF shl 24) or hex.removePrefix("#").toInt(16)) and 0xFFFFFF)
}

private fun colourValueOf(value: JsonElement): StyleColourValue =
    if (value is JsonPrimitive) StyleColourValue.Literal(value.content) else StyleColourValue.Expression(value.toString())
