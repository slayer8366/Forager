package com.zynergylabs.forager.app.ui.map

import java.io.File
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Dispatch 2026-10-27-02 (RECORD -1730): Trail Map's buildings are solid, `#c2c2c2` at z14 and `#cccccc` from z15, with
 * a `#a6a6a6` outline from z15, after the OpenStreetMap Carto style's buildings (`map-style/overrides/forager-street.mjs`,
 * `withBuildingOutline`). The owner: "Can we have a border around the buildings and houses to separate them from the
 * rest of the map?"
 *
 * Night takes no exception for them: the app's own V1 inversion. This holds what the app's Street night recolour
 * ([streetNightRecolourOf], the function the map's night adapter calls for each layer colour) gives for the served
 * street-v3's buildings layers: `#3d3d3d` at z14, `#333333` from z15, and `#595959` for the outline, the values the
 * dispatch computed by hand from `night = c + 255 - 2 * mean`.
 */
class BuildingsNightTest {

    private val layers: Map<String, JsonObject> =
        Json.parseToJsonElement(File("../server/pmtiles-worker/src/street-style-v3.json").readText()).jsonObject
            .getValue("layers").jsonArray.map { it.jsonObject }.associateBy { it.getValue("id").jsonPrimitive.content }

    private fun nightOf(id: String, property: String): NightRecolour {
        val layer = layers[id] ?: error("no layer $id in street-v3")
        val value = layer.getValue("paint").jsonObject[property] ?: error("$id has no $property")
        val colour = if (value is JsonPrimitive) StyleColourValue.Literal(value.content) else StyleColourValue.Expression(value.toString())
        return streetNightRecolourOf(LayerColourProperty(id, property, colour), layer["source-layer"]?.jsonPrimitive?.content)
    }

    @Test
    fun `the buildings fill inverts to 3d3d3d at z14 and 333333 from z15, its zoom step kept`() {
        val night = nightOf("buildings", "fill-color") as NightRecolour.Recoloured
        val json = Json.parseToJsonElement((night.night as StyleColourValue.Expression).json)
        assertEquals(Json.parseToJsonElement("""["step",["zoom"],"rgba(61, 61, 61, 1)",15,"rgba(51, 51, 51, 1)"]"""), json)
    }

    @Test
    fun `the buildings outline inverts to 595959`() {
        val night = nightOf("buildings_outline", "line-color") as NightRecolour.Recoloured
        assertEquals(StyleColourValue.Literal("rgba(89, 89, 89, 1)"), night.night)
    }
}
