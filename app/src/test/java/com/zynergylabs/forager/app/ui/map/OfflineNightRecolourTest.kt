package com.zynergylabs.forager.app.ui.map

import java.io.File
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The offline style's night (colour build C1 (d)): [offlineNightRecolourOf] maps one layer colour
 * property to its V1 night value, for a literal and for the colour literals inside any expression
 * (planner ruling 2: `match`, `case`, `interpolate`, `step` or any other). The fixture style below
 * has one of each form `server/pmtiles-worker/src/offline-style.json` uses (literal, `match`,
 * `case`, `interpolate`), plus the `["rgba", r, g, b, a]` array form MapLibre may hand back from a
 * live style, and the cases that must be left and listed.
 *
 * Expected night colours are typed literals, worked by hand from the V1 formula
 * `clamp(c + 255 - 2 * mean(r, g, b))`, not computed from the code under test.
 *
 * What this cannot show: which form MapLibre Native returns a live layer's colours in. The adapter in
 * `SightingsMap.kt` reads them through the SDK's getters; that is a device item.
 */
class OfflineNightRecolourTest {

    private val json = Json

    private val fixtureStyle = """
        {
          "version": 8,
          "sources": {},
          "layers": [
            {"id": "background", "type": "background", "paint": {"background-color": "#cccccc"}},
            {"id": "earth", "type": "fill", "paint": {"fill-color": "rgba(210, 239, 207, 1)"}},
            {"id": "landcover", "type": "fill", "paint": {"fill-color":
              ["match", ["get", "kind"], "grassland", "rgba(210, 239, 207, 1)", "barren", "#fff3d7", "#e2dfda"]}},
            {"id": "landuse_park", "type": "fill", "paint": {"fill-color":
              ["case", ["in", ["get", "kind"], ["literal", ["park", "cemetery"]]], "#9cd3b4", "#e2dfda"]}},
            {"id": "roads_minor", "type": "line", "paint": {"line-color":
              ["interpolate", ["exponential", 1.6], ["zoom"], 11, "#ebebeb", 16, "#ffffff"]}},
            {"id": "roads_step", "type": "line", "paint": {"line-color":
              ["step", ["zoom"], "#000000", 12, "#ffffff"]}},
            {"id": "native_rgba", "type": "line", "paint": {"line-color":
              ["interpolate", ["linear"], ["zoom"], 5, ["rgba", 235, 235, 235, 1], 10, ["rgba", 255, 255, 255, 0.5]]}},
            {"id": "outlined", "type": "fill", "paint": {"fill-color": "#ffffff", "fill-outline-color": "#000000"}},
            {"id": "data_driven", "type": "fill", "paint": {"fill-color": ["get", "colour"]}},
            {"id": "named", "type": "line", "paint": {"line-color": "white"}},
            {"id": "symbols", "type": "symbol", "paint": {"text-color": "#000000"}},
            {"id": "labels", "type": "symbol", "paint": {"text-color": "#262626", "text-halo-color": "#ffffff", "text-halo-width": 1.75}}
          ]
        }
    """.trimIndent()

    /**
     * The colour properties of a style document's background, fill and line layers, in the form the
     * SightingsMap adapter builds from the SDK's getters: a string is a literal, anything else is an
     * expression, handed over as JSON text.
     */
    private fun colourPropertiesOf(styleJson: String): List<LayerColourProperty> =
        json.parseToJsonElement(styleJson).jsonObject.getValue("layers").jsonArray.flatMap { layer ->
            val obj = layer.jsonObject
            val id = obj.getValue("id").jsonPrimitive.content
            val paint = obj["paint"]?.jsonObject ?: return@flatMap emptyList()
            NIGHT_RECOLOURED_PROPERTIES.getOrDefault(obj.getValue("type").jsonPrimitive.content, emptyList())
                .mapNotNull { property ->
                    paint[property]?.let { value ->
                        LayerColourProperty(
                            layerId = id,
                            property = property,
                            value = if (value is JsonPrimitive && value.isString) {
                                StyleColourValue.Literal(value.content)
                            } else {
                                StyleColourValue.Expression(value.toString())
                            },
                        )
                    }
                }
        }

    private fun outcomes(styleJson: String = fixtureStyle) =
        colourPropertiesOf(styleJson).associate { "${it.layerId}/${it.property}" to offlineNightRecolourOf(it) }

    private fun nightExpression(key: String): JsonElement {
        val outcome = outcomes().getValue(key)
        assertTrue("$key should be recoloured, was $outcome", outcome is NightRecolour.Recoloured)
        val value = (outcome as NightRecolour.Recoloured).night
        assertTrue("$key should stay an expression, was $value", value is StyleColourValue.Expression)
        return json.parseToJsonElement((value as StyleColourValue.Expression).json)
    }

    private fun nightLiteral(key: String): String {
        val outcome = outcomes().getValue(key)
        assertTrue("$key should be recoloured, was $outcome", outcome is NightRecolour.Recoloured)
        val value = (outcome as NightRecolour.Recoloured).night
        assertTrue("$key should stay a literal, was $value", value is StyleColourValue.Literal)
        return (value as StyleColourValue.Literal).text
    }

    /**
     * Background, fill and line colours, and since dispatch 2026-10-18-05 Part 2 the symbol layers' text and halo colours
     * (-1288: "Labels are inverted too"; the own Street style has labels, the offline style has none). Width and other
     * non-colour properties are never walked.
     */
    @Test
    fun `background, fill, line and label colour properties are walked`() {
        assertEquals(
            listOf(
                "background/background-color", "earth/fill-color", "landcover/fill-color", "landuse_park/fill-color",
                "roads_minor/line-color", "roads_step/line-color", "native_rgba/line-color", "outlined/fill-color",
                "outlined/fill-outline-color", "data_driven/fill-color", "named/line-color", "symbols/text-color",
                "labels/text-color", "labels/text-halo-color",
            ),
            colourPropertiesOf(fixtureStyle).map { "${it.layerId}/${it.property}" },
        )
    }

    @Test
    fun `labels invert: near-black text goes near-white and a white halo goes black`() {
        // V1 by hand: #262626 has mean 38, so each channel becomes 38 + 255 - 76 = 217 (#d9d9d9); white goes to 0.
        assertEquals("rgba(217, 217, 217, 1)", nightLiteral("labels/text-color"))
        assertEquals("rgba(0, 0, 0, 1)", nightLiteral("labels/text-halo-color"))
        assertEquals("rgba(255, 255, 255, 1)", nightLiteral("symbols/text-color"))
    }

    @Test
    fun `an icon's pixels go through the same V1, alpha kept`() {
        // Opaque white, half-transparent black, and a fully transparent pixel, worked by hand.
        val day = intArrayOf(0xFFFFFFFF.toInt(), 0x80000000.toInt(), 0x00A8452A)
        val night = nightPixelsOf(day)
        assertEquals(0xFF000000.toInt(), night[0])
        assertEquals(0x80FFFFFF.toInt(), night[1])
        // #a8452a (168, 69, 42): mean 93, shift 255 - 186 = 69: (237, 138, 111) = #ed8a6f; alpha 0 kept.
        assertEquals(0x00ED8A6F, night[2])
    }

    @Test
    fun `literal colours are recoloured, hex and rgba alike`() {
        assertEquals("rgba(51, 51, 51, 1)", nightLiteral("background/background-color"))
        assertEquals("rgba(28, 57, 25, 1)", nightLiteral("earth/fill-color"))
        assertEquals("rgba(0, 0, 0, 1)", nightLiteral("outlined/fill-color"))
        assertEquals("rgba(255, 255, 255, 1)", nightLiteral("outlined/fill-outline-color"))
    }

    @Test
    fun `match recolours its outputs and leaves its labels`() {
        assertEquals(
            json.parseToJsonElement(
                """["match", ["get", "kind"], "grassland", "rgba(28, 57, 25, 1)", "barren", "rgba(35, 23, 0, 1)", "rgba(36, 33, 28, 1)"]""",
            ),
            nightExpression("landcover/fill-color"),
        )
    }

    @Test
    fun `case recolours its outputs and leaves its condition`() {
        assertEquals(
            json.parseToJsonElement(
                """["case", ["in", ["get", "kind"], ["literal", ["park", "cemetery"]]], "rgba(46, 101, 70, 1)", "rgba(36, 33, 28, 1)"]""",
            ),
            nightExpression("landuse_park/fill-color"),
        )
    }

    @Test
    fun `interpolate and step recolour their stops and leave the zoom levels`() {
        assertEquals(
            json.parseToJsonElement("""["interpolate", ["exponential", 1.6], ["zoom"], 11, "rgba(20, 20, 20, 1)", 16, "rgba(0, 0, 0, 1)"]"""),
            nightExpression("roads_minor/line-color"),
        )
        assertEquals(
            json.parseToJsonElement("""["step", ["zoom"], "rgba(255, 255, 255, 1)", 12, "rgba(0, 0, 0, 1)"]"""),
            nightExpression("roads_step/line-color"),
        )
    }

    @Test
    fun `rgba arrays inside an expression are recoloured, alpha kept`() {
        assertEquals(
            json.parseToJsonElement("""["interpolate", ["linear"], ["zoom"], 5, ["rgba", 20, 20, 20, 1], 10, ["rgba", 0, 0, 0, 0.5]]"""),
            nightExpression("native_rgba/line-color"),
        )
    }

    @Test
    fun `a property with no colour it can read is left, with a reason`() {
        val all = outcomes()
        for (key in listOf("data_driven/fill-color", "named/line-color")) {
            val outcome = all.getValue(key)
            assertTrue("$key should be left, was $outcome", outcome is NightRecolour.Left)
            assertTrue("$key needs a reason to log", (outcome as NightRecolour.Left).reason.isNotBlank())
        }
    }

    @Test
    fun `an expression that is not JSON is left, with a reason`() {
        val outcome = offlineNightRecolourOf(
            LayerColourProperty("broken", "line-color", StyleColourValue.Expression("[\"interpolate\", ")),
        )
        assertTrue("was $outcome", outcome is NightRecolour.Left)
    }

    /**
     * The authored offline style itself, as the repository holds it: every one of its 57 colour
     * properties is one this function can transform. That is a claim about the file, not about what
     * MapLibre hands back at runtime, nor about the file the deployed worker serves.
     */
    @Test
    fun `every colour property in the repository's offline style is recoloured`() {
        val style = File("../server/pmtiles-worker/src/offline-style.json").readText()
        val all = outcomes(style)

        assertEquals(57, all.size)
        assertEquals(emptyMap<String, NightRecolour>(), all.filterValues { it is NightRecolour.Left })
    }

    /**
     * The own Street style as the Worker serves it (dispatch 2026-10-18-05 Part 2): every one of its 85 colour
     * properties, labels and halos included, is one this function can transform. Like the test above, a claim about
     * the file, not about the forms MapLibre hands back at runtime, which is a device item.
     */
    @Test
    fun `every colour property in the Street style is recoloured, labels included`() {
        val style = File("../server/pmtiles-worker/src/street-style-v1.json").readText()
        val all = outcomes(style)

        assertEquals(85, all.size)
        assertTrue(all.keys.any { it.endsWith("/text-color") } && all.keys.any { it.endsWith("/text-halo-color") })
        assertEquals(emptyMap<String, NightRecolour>(), all.filterValues { it is NightRecolour.Left })
    }
}
