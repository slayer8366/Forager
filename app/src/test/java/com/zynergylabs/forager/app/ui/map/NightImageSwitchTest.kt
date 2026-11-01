package com.zynergylabs.forager.app.ui.map

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
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Trail Map's night icons and patterns as the style takes them (dispatch 2026-10-22-06 Amendments 1 and 2, RECORD
 * -1566): the night copies added as `night/<name>` and the style's image references switched to them, read against the
 * served v2 and v1 styles. The first test is the one that would have caught dispatch 2026-10-22-05's bug: it runs the
 * night step at the style callback with no sprite image loaded, then loads the sprite the way MapLibre Native does,
 * after the callback and replacing any image of the same name, and asks what each reference draws.
 *
 * Amendment 2 (Sorrel's review of PR #380, H1, M1, L1): MapLibre refused the first switch's `places_locality` value,
 * because it put `["zoom"]` below the top level; the app counted it switched anyway. These tests hold MapLibre's zoom
 * rule on every switched value, keep the day expression's own structure (only its outputs change), and count a
 * reference switched only when the style reads back the night value.
 */
class NightImageSwitchTest {

    private fun styleOf(version: Int): JsonObject =
        Json.parseToJsonElement(File("../server/pmtiles-worker/src/street-style-v$version.json").readText()).jsonObject

    /** Sprite v5's 61 names, from its day index; `NightSpriteTest` holds the bundled night index equal to it. */
    private val spriteNames: Set<String> =
        Json.parseToJsonElement(File("src/test/resources/sprite-v5-day/light@2x.json").readText()).jsonObject.keys

    /** Every image reference in [style], by `layer/property`, with its day value as the style writes it. */
    private fun imageReferences(style: JsonObject): Map<String, JsonElement> = buildMap {
        for (layer in style.getValue("layers").jsonArray) {
            val o = layer.jsonObject
            for ((property, block) in SWITCHED_IMAGE_PROPERTIES) {
                o[block]?.jsonObject?.get(property)?.let { put("${o.getValue("id").jsonPrimitive.content}/$property", it) }
            }
        }
    }

    private fun ImageReference.json(): JsonElement = when (this) {
        is ImageReference.Name -> JsonPrimitive(name)
        is ImageReference.Expr -> Json.parseToJsonElement(json)
    }

    /**
     * A loaded style as MapLibre Native 13.5.0 holds it: images by name and the layers' image references. Its sprite
     * loads after the `setStyle` callback (about 220 ms later on the S22: `nonNull=0/61` at the callback, `61/61` at the
     * next main-loop turn, dispatch 2026-10-22-05), and replaces any image already present under the same name
     * (`style_impl.cpp:374-381` at `android-v13.5.0`). Like MapLibre, it keeps the day value of a property whose new
     * value breaks the zoom rule, and says nothing to the caller (Sorrel's H1 and M1: the SDK logs the refusal natively).
     */
    private inner class LoadedStyle(private val style: JsonObject) : NightImageTarget<String> {
        val images = mutableMapOf<String, String>()
        val references: MutableMap<String, JsonElement> = imageReferences(style).toMutableMap()

        override val styleJson: String get() = style.toString()

        override fun addImages(images: Map<String, String>) {
            this.images.putAll(images)
        }

        override fun setImageReference(layerId: String, property: String, value: ImageReference): Boolean {
            val key = "$layerId/$property"
            if (key !in references) return false
            val json = value.json()
            if (zoomRuleViolations(json).isEmpty()) references[key] = json
            return true
        }

        override fun readImageReference(layerId: String, property: String): String? = references["$layerId/$property"]?.toString()

        fun loadSprite(sprite: Map<String, String>) = images.putAll(sprite)

        /** The image [key] draws for a feature with [properties] at [zoom]: `null` when no such image. */
        fun drawn(key: String, properties: Map<String, Any?> = emptyMap(), zoom: Double = 13.0): String? =
            images[evaluateStyleExpression(references.getValue(key), properties, zoom) as String]
    }

    /** What the bug test asks each reference to draw: the reference, a feature, a zoom, and the day image it names. */
    private data class Sample(val key: String, val properties: Map<String, Any?>, val zoom: Double, val dayName: String)

    private val v2Samples = listOf(
        Sample("landuse_forest_glyphs/fill-pattern", emptyMap(), 13.0, "tree_dark_irregular"),
        Sample("landuse_wetland_tufts/fill-pattern", emptyMap(), 13.0, "tufts"),
        Sample("roads_oneway/icon-image", emptyMap(), 15.0, "arrow"),
        Sample("roads_shields/icon-image", mapOf("network" to "US:I", "shield_text" to "84"), 10.0, "US:I-2char"),
        Sample("roads_shields/icon-image", mapOf("network" to "US:US", "shield_text" to "101"), 10.0, "generic_shield-3char"),
        Sample("pois/icon-image", mapOf("kind" to "parking"), 15.0, "parking"),
        Sample("pois/icon-image", mapOf("kind" to "peak"), 15.0, "peak"),
        Sample("pois/icon-image", mapOf("kind" to "viewpoint"), 15.0, "viewpoint_c"),
        Sample("pois/icon-image", mapOf("kind" to "trailhead"), 15.0, "trailhead_b"),
        Sample("pois/icon-image", mapOf("kind" to "shelter"), 15.0, "townspot"),
        Sample("pois/icon-image", mapOf("kind" to "toilets"), 15.0, "toilets"),
        Sample("pois/icon-image", mapOf("kind" to "restaurant"), 15.0, "restaurant"),
        Sample("places_locality/icon-image", mapOf("capital" to "yes"), 6.0, "capital"),
        Sample("places_locality/icon-image", mapOf("capital" to "no"), 6.0, "townspot"),
    )

    @Test
    fun `night images survive the sprite arriving after the style callback, and every reference draws its night copy`() {
        val style = LoadedStyle(styleOf(2))
        // At the callback: no sprite image is in the style yet.
        assertTrue(style.images.isEmpty())
        val outcome = applyNightImages(style, spriteNames.associateWith { "night pixels of $it" })
        // The sprite arrives after the callback, and replaces images of its own names.
        style.loadSprite(spriteNames.associateWith { "day pixels of $it" })

        assertEquals(
            "Street night: 61 night images added, 6 image references switched, 0 refused by MapLibre, 0 left in day images; no night copy missing.",
            outcome.logLine(),
        )
        val drawn = v2Samples.map { s -> "${s.key} ${s.dayName}" to style.drawn(s.key, s.properties, s.zoom) }
        assertEquals(drawn.map { (what, _) -> what to "night pixels of ${what.substringAfter(' ')}" }, drawn)
    }

    @Test
    fun `every switched value keeps zoom as the input of a top-level step only, at v1 and v2`() {
        for (version in listOf(1, 2)) {
            val plan = nightImageSwitchPlan(styleOf(version).toString(), spriteNames)
            for (switch in plan.switches) {
                assertEquals("v$version ${switch.layerId}/${switch.property}", emptyList<String>(), zoomRuleViolations(switch.night.json()))
            }
        }
    }

    @Test
    fun `the switch changes only the day expression's image outputs, keeping its own operators, inputs and labels`() {
        val references = imageReferences(styleOf(2))
        val switched = nightImageSwitchPlan(styleOf(2).toString(), spriteNames).switches.associate { "${it.layerId}/${it.property}" to it.night.json() }
        fun names(): JsonArray = JsonArray(spriteNames.sorted().map { JsonPrimitive(it) })
        fun wrapped(e: JsonElement) = JsonArray(listOf(JsonPrimitive("match"), e, names(), JsonArray(listOf(JsonPrimitive("concat"), JsonPrimitive("night/"), e)), e))
        fun p(s: String) = JsonPrimitive(s)
        val day = references.mapValues { it.value }

        // The zoom step stays on top; its outputs take their night names (H1).
        val placesDay = day.getValue("places_locality/icon-image").jsonArray
        val placesCase = placesDay[2].jsonArray
        assertEquals(
            JsonArray(listOf(p("step"), placesDay[1], JsonArray(listOf(p("case"), placesCase[1], p("night/capital"), p("night/townspot"))), placesDay[3], p(""))),
            switched["places_locality/icon-image"],
        )
        // The match on kind stays; its literal outputs take night names, and its ["get", "kind"] otherwise stays in an
        // image position, wrapped, so it keeps the day style's image coercion (L1).
        val poisDay = day.getValue("pois/icon-image").jsonArray
        assertEquals(
            JsonArray(listOf(p("match"), poisDay[1], poisDay[2], p("night/townspot"), p("trailhead"), p("night/trailhead_b"), p("viewpoint"), p("night/viewpoint_c"), wrapped(poisDay[8]))),
            switched["pois/icon-image"],
        )
        // The match on network stays; each computed name is wrapped.
        val shieldsDay = day.getValue("roads_shields/icon-image").jsonArray
        assertEquals(
            JsonArray(listOf(p("match"), shieldsDay[1], shieldsDay[2], wrapped(shieldsDay[3]), shieldsDay[4], wrapped(shieldsDay[5]), wrapped(shieldsDay[6]))),
            switched["roads_shields/icon-image"],
        )
        assertEquals(p("night/tree_dark_irregular"), switched["landuse_forest_glyphs/fill-pattern"])
        assertEquals(p("night/tufts"), switched["landuse_wetland_tufts/fill-pattern"])
        assertEquals(p("night/arrow"), switched["roads_oneway/icon-image"])
    }

    @Test
    fun `a value MapLibre refuses is counted as refused and named, never as switched`() {
        // MapLibre keeps the day value and tells the app nothing; only reading the property back shows it.
        val style = object : NightImageTarget<String> {
            val values = imageReferences(styleOf(2)).mapValues { it.value.toString() }.toMutableMap()
            override val styleJson: String = styleOf(2).toString()
            override fun addImages(images: Map<String, String>) = Unit
            override fun setImageReference(layerId: String, property: String, value: ImageReference): Boolean {
                if (layerId != "places_locality") values["$layerId/$property"] = value.json().toString()
                return true
            }
            override fun readImageReference(layerId: String, property: String): String? = values["$layerId/$property"]
        }
        val outcome = applyNightImages(style, spriteNames.associateWith { it })
        assertEquals(5, outcome.switched)
        assertEquals(listOf("places_locality/icon-image"), outcome.refused)
        assertTrue(outcome.logLine(), outcome.logLine().contains("5 image references switched, 1 refused by MapLibre: [places_locality/icon-image]"))
    }

    @Test
    fun `v4 with sprite v6's night copies switches its orchard patterns too, with nothing left or missing`() {
        // Dispatch 2026-10-31-coder-07 Part 3: Street v4 names sprite v6, whose three patterns each have a night copy;
        // the match's unreachable "" output names no image, so it is neither switched nor missing.
        val v4 = Json.parseToJsonElement(File("../map-style/forager-street-v4.json").readText()).jsonObject
        val v6Names = Json.parseToJsonElement(File("src/test/resources/sprite-v6-day/light@2x.json").readText()).jsonObject.keys
        val plan = nightImageSwitchPlan(v4.toString(), v6Names)
        assertEquals(emptyList<String>(), plan.left)
        assertEquals(emptyList<String>(), plan.missingNightCopies)
        val orchards = plan.switches.single { it.layerId == "landuse_orchards_pattern" && it.property == "fill-pattern" }
        val json = (orchards.night as ImageReference.Expr).json
        for (name in listOf("orchard_dots", "vineyard_rows", "nursery_dots")) assertTrue(json, json.contains("\"night/$name\""))
    }

    @Test
    fun `v2 switches exactly its six image references, with nothing left or missing`() {
        val style = styleOf(2)
        val plan = nightImageSwitchPlan(style.toString(), spriteNames)
        assertEquals(emptyList<String>(), plan.left)
        assertEquals(emptyList<String>(), plan.missingNightCopies)
        assertEquals(
            setOf("landuse_forest_glyphs/fill-pattern", "landuse_wetland_tufts/fill-pattern", "roads_oneway/icon-image", "roads_shields/icon-image", "pois/icon-image", "places_locality/icon-image"),
            plan.switches.map { "${it.layerId}/${it.property}" }.toSet(),
        )
        assertEquals(imageReferences(style).keys, plan.switches.map { "${it.layerId}/${it.property}" }.toSet())
    }

    @Test
    fun `v1 switches every image reference it has, each drawing its night copy, with no night copy missing`() {
        val style = LoadedStyle(styleOf(1))
        val outcome = applyNightImages(style, spriteNames.associateWith { "night pixels of $it" })
        style.loadSprite(spriteNames.associateWith { "day pixels of $it" })
        assertEquals("Street night: 61 night images added, 4 image references switched, 0 refused by MapLibre, 0 left in day images; no night copy missing.", outcome.logLine())
        assertEquals("night pixels of arrow", style.drawn("roads_oneway/icon-image"))
        assertEquals("night pixels of US:I-1char", style.drawn("roads_shields/icon-image", mapOf("network" to "US:I", "shield_text" to "5")))
        assertEquals("night pixels of townspot", style.drawn("pois/icon-image", mapOf("kind" to "parking")))
        assertEquals("night pixels of peak", style.drawn("pois/icon-image", mapOf("kind" to "peak")))
        assertEquals("night pixels of capital", style.drawn("places_locality/icon-image", mapOf("capital" to "yes"), 5.0))
    }

    @Test
    fun `places draw no icon from zoom 8 at night, as by day`() {
        val style = LoadedStyle(styleOf(2))
        applyNightImages(style, spriteNames.associateWith { "night pixels of $it" })
        assertEquals("", evaluateStyleExpression(style.references.getValue("places_locality/icon-image"), mapOf("capital" to "yes"), 8.0))
    }

    @Test
    fun `a missing night copy leaves its day image drawn, and is named in the log line`() {
        val style = LoadedStyle(styleOf(2))
        val night = (spriteNames - setOf("arrow", "parking", "townspot")).associateWith { "night pixels of $it" }
        val outcome = applyNightImages(style, night)
        style.loadSprite(spriteNames.associateWith { "day pixels of $it" })

        assertEquals(58, outcome.added)
        assertEquals(5, outcome.switched)
        assertEquals(listOf("roads_oneway/icon-image: no night copy of \"arrow\""), outcome.left)
        assertEquals(listOf("arrow", "parking", "townspot"), outcome.missingNightCopies.sorted())
        assertEquals("day pixels of arrow", style.drawn("roads_oneway/icon-image"))
        assertEquals("day pixels of parking", style.drawn("pois/icon-image", mapOf("kind" to "parking")))
        assertEquals("day pixels of townspot", style.drawn("pois/icon-image", mapOf("kind" to "shelter")))
        assertEquals("day pixels of townspot", style.drawn("places_locality/icon-image", mapOf("capital" to "no"), 6.0))
        assertEquals("night pixels of capital", style.drawn("places_locality/icon-image", mapOf("capital" to "yes"), 6.0))
        assertEquals("night pixels of peak", style.drawn("pois/icon-image", mapOf("kind" to "peak")))
        assertTrue(outcome.logLine(), outcome.logLine().endsWith("no night copy of [arrow, parking, townspot], drawn in day colours."))
    }

    @Test
    fun `with no night sheets at all nothing is added, nothing switched, and every reference is reported left`() {
        val style = LoadedStyle(styleOf(2))
        val outcome = applyNightImages(style, emptyMap())
        assertEquals(0, outcome.added)
        assertEquals(0, outcome.switched)
        assertEquals(6, outcome.left.size)
        assertTrue(style.images.isEmpty())
    }

    @Test
    fun `a layer the live style does not have is reported left, not counted as switched`() {
        val style = object : NightImageTarget<String> {
            override val styleJson: String = styleOf(2).toString()
            override fun addImages(images: Map<String, String>) = Unit
            override fun setImageReference(layerId: String, property: String, value: ImageReference): Boolean = layerId != "pois"
            override fun readImageReference(layerId: String, property: String): String? = if (layerId == "pois") null else NIGHT_IMAGE_PREFIX
        }
        val outcome = applyNightImages(style, spriteNames.associateWith { it })
        assertEquals(5, outcome.switched)
        assertEquals(listOf("pois/icon-image: no such layer to set"), outcome.left)
    }

    @Test
    fun `a style whose JSON cannot be read switches nothing and says so`() {
        val plan = nightImageSwitchPlan("not json", spriteNames)
        assertEquals(emptyList<NightImageReference>(), plan.switches)
        assertEquals(listOf("style: its JSON could not be read"), plan.left)
    }
}
