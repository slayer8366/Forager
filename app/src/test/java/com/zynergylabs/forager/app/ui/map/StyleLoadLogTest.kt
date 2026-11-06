package com.zynergylabs.forager.app.ui.map

import com.zynergylabs.forager.app.map.TRAIL_STYLE_URL
import com.zynergylabs.forager.app.map.TRAIL_STYLE_V3_URL
import java.io.File
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Dispatch 2026-11-04-05 B1 (RECORD -2061): the style-load line tells the three readings of "no forager:layer-clones" on
 * the S22 apart, over the styles the app loads. The expected counts are read from each file here, independently of the
 * code under test, so a regenerated style does not break this; the cases are the shapes, not the numbers.
 */
class StyleLoadLogTest {

    private val trailV4 = File("../map-style/forager-trail-v4.json").readText()
    private val trailV4Json = Json.parseToJsonElement(trailV4).jsonObject
    private val metadata = trailV4Json.getValue("metadata").jsonObject

    /** [trailV4] with its metadata changed by [edit], as JSON text. */
    private fun trailV4With(edit: (MutableMap<String, kotlinx.serialization.json.JsonElement>) -> Unit): String {
        val style = trailV4Json.toMutableMap()
        edit(style)
        return JsonObject(style).toString()
    }

    @Test
    fun `today's trail-v4 reads as recorded, with its clone and metadata key counts`() {
        val clones = metadata.getValue(LAYER_CLONES_KEY).jsonObject.size
        assertTrue("the fixture records clones", clones > 0)
        assertEquals(
            "Style loaded: url=$TRAIL_STYLE_URL, 182 layers, $LAYER_CLONES_KEY recorded: $clones clones, ${metadata.size} metadata keys.",
            styleLoadLogLine(TRAIL_STYLE_URL, 182, trailV4),
        )
    }

    @Test
    fun `a trail-v4 without the record, as Worker release 472 served it, reads as absent from a metadata that has keys`() {
        val stale = trailV4With { it["metadata"] = JsonObject(metadata - LAYER_CLONES_KEY) }
        assertEquals(
            "Style loaded: url=$TRAIL_STYLE_URL, 104 layers, $LAYER_CLONES_KEY absent (the style's metadata has no $LAYER_CLONES_KEY), " +
                "${metadata.size - 1} metadata keys.",
            styleLoadLogLine(TRAIL_STYLE_URL, 104, stale),
        )
    }

    @Test
    fun `a style whose metadata was dropped reads as having no metadata at all`() {
        val dropped = trailV4With { it.remove("metadata") }
        assertEquals(
            "Style loaded: url=$TRAIL_STYLE_URL, 182 layers, $LAYER_CLONES_KEY absent (the style has no metadata, so no $LAYER_CLONES_KEY), no metadata.",
            styleLoadLogLine(TRAIL_STYLE_URL, 182, dropped),
        )
    }

    @Test
    fun `street-v3 has metadata but no record`() {
        val v3 = File("../map-style/forager-street-v3.json").readText()
        val keys = Json.parseToJsonElement(v3).jsonObject.getValue("metadata").jsonObject.size
        assertEquals(
            "Style loaded: url=$TRAIL_STYLE_V3_URL, 97 layers, $LAYER_CLONES_KEY absent (the style's metadata has no $LAYER_CLONES_KEY), $keys metadata keys.",
            styleLoadLogLine(TRAIL_STYLE_V3_URL, 97, v3),
        )
    }

    @Test
    fun `a style built in the app names no url`() {
        val line = styleLoadLogLine(null, 2, styleJsonFor(Basemap.OPEN_TOPO_MAP, night = false))
        assertEquals(
            "Style loaded: url=none (a style built in the app), 2 layers, $LAYER_CLONES_KEY absent (the style has no metadata, so no $LAYER_CLONES_KEY), no metadata.",
            line,
        )
    }

    @Test
    fun `an unreadable style says so, and never reads as absent`() {
        val line = styleLoadLogLine(TRAIL_STYLE_URL, 0, "not json")
        assertTrue(line, line.startsWith("Style loaded: url=$TRAIL_STYLE_URL, 0 layers, style JSON unreadable (the style's JSON could not be read: "))
        assertTrue(line, line.endsWith("), metadata unread."))
    }

    @Test
    fun `every style load logs the line, with MapLibre's own layer count`() {
        // The setStyle callback needs a native MapView, which no unit test has: held at its one call site, as
        // CloneLayersNightTest holds the night pass's.
        val reload = File("src/main/java/com/zynergylabs/forager/app/ui/map/MapStyleReload.kt").readText()
        val call = Regex("""map\.setStyle\(builder\) \{ style ->\s*\n(?:\s*//[^\n]*\n)*\s*disableSymbolFade\(style\)\n(?:\s*//[^\n]*\n)*\s*Log\.i\(SIGHTINGS_MAP_TAG, styleLoadLogLine\(trailUriOf\(source\), style\.layers\.size, style\.json\)\)""")
        assertEquals(1, call.findAll(reload).count())
        assertEquals(1, Regex("""styleLoadLogLine\(""").findAll(reload).count())
    }
}
