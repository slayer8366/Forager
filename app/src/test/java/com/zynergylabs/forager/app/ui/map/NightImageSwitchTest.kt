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
 * Trail Map's night icons and patterns as the style takes them (dispatch 2026-10-22-06 Amendment 1, RECORD -1566): the
 * night copies added as `night/<name>` and the style's image references switched to them, read against the served v2
 * and v1 styles. The first test is the one that would have caught dispatch 2026-10-22-05's bug: it runs the night step
 * at the style callback with no sprite image loaded, then loads the sprite the way MapLibre Native does, after the
 * callback and replacing any image of the same name, and asks what each reference draws.
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

    /**
     * The image a switched expression draws for a feature whose day expression gives [dayName], read from the switch's
     * own form, `["match", E, names, ["concat", "night/", E], E]`; any other form is a failure, not a guess.
     */
    private fun switchedImageFor(json: String, original: JsonElement, dayName: String): String {
        val e = Json.parseToJsonElement(json).jsonArray
        assertEquals("match", e[0].jsonPrimitive.content)
        assertEquals(original, e[1])
        assertEquals(JsonArray(listOf(JsonPrimitive("concat"), JsonPrimitive(NIGHT_IMAGE_PREFIX), original)), e[3])
        assertEquals(original, e[4])
        val labels = e[2].jsonArray.map { it.jsonPrimitive.content }
        return if (dayName in labels) NIGHT_IMAGE_PREFIX + dayName else dayName
    }

    /**
     * A loaded style as MapLibre Native 13.5.0 holds it: images by name and the layers' image references. Its sprite
     * loads after the `setStyle` callback (about 220 ms later on the S22: `nonNull=0/61` at the callback, `61/61` at the
     * next main-loop turn, dispatch 2026-10-22-05), and replaces any image already present under the same name
     * (`style_impl.cpp:374-381` at `android-v13.5.0`).
     */
    private inner class LoadedStyle(private val style: JsonObject) : NightImageTarget<String> {
        val images = mutableMapOf<String, String>()
        val references: MutableMap<String, Any> = HashMap<String, Any>(imageReferences(style))

        override val styleJson: String get() = style.toString()

        override fun addImages(images: Map<String, String>) {
            this.images.putAll(images)
        }

        override fun setImageReference(layerId: String, property: String, value: ImageReference): Boolean {
            val key = "$layerId/$property"
            if (key !in references) return false
            references[key] = value
            return true
        }

        fun loadSprite(sprite: Map<String, String>) = images.putAll(sprite)

        /** The image [key] draws for a feature whose day expression names [dayName]: `null` when no such image. */
        fun drawn(key: String, dayName: String): String? {
            val original = imageReferences(style).getValue(key)
            val name = when (val reference = references.getValue(key)) {
                is ImageReference.Name -> reference.name
                is ImageReference.Expr -> switchedImageFor(reference.json, original, dayName)
                else -> dayName // not switched: the day expression's own name
            }
            return images[name]
        }
    }

    @Test
    fun `night images survive the sprite arriving after the style callback, and every reference draws its night copy`() {
        val style = LoadedStyle(styleOf(2))
        // At the callback: no sprite image is in the style yet.
        assertTrue(style.images.isEmpty())
        val outcome = applyNightImages(style, spriteNames.associateWith { "night pixels of $it" })
        // The sprite arrives after the callback, and replaces images of its own names.
        style.loadSprite(spriteNames.associateWith { "day pixels of $it" })

        assertEquals("Street night: 61 night images added, 6 image references switched, 0 left in day images; no night copy missing.", outcome.logLine())
        val drawn = listOf(
            "landuse_forest_glyphs/fill-pattern" to "tree_dark_irregular",
            "landuse_wetland_tufts/fill-pattern" to "tufts",
            "roads_oneway/icon-image" to "arrow",
            "roads_shields/icon-image" to "US:I-2char",
            "roads_shields/icon-image" to "generic_shield-3char",
            "pois/icon-image" to "parking",
            "pois/icon-image" to "peak",
            "pois/icon-image" to "viewpoint_c",
            "pois/icon-image" to "trailhead_b",
            "pois/icon-image" to "toilets",
            "pois/icon-image" to "restaurant",
            "places_locality/icon-image" to "capital",
            "places_locality/icon-image" to "townspot",
        ).map { (key, name) -> "$key $name" to style.drawn(key, name) }
        assertEquals(drawn.map { (what, _) -> what to "night pixels of ${what.substringAfter(' ')}" }, drawn)
    }

    @Test
    fun `v2 switches exactly its six image references, the literals by name and the expressions by match`() {
        val style = styleOf(2)
        val plan = nightImageSwitchPlan(style.toString(), spriteNames)
        assertEquals(emptyList<String>(), plan.left)
        assertEquals(emptyList<String>(), plan.missingNightCopies)
        val switched = plan.switches.associate { "${it.layerId}/${it.property}" to it.night }
        val references = imageReferences(style)
        assertEquals(references.keys, switched.keys)
        assertEquals(
            setOf("landuse_forest_glyphs/fill-pattern", "landuse_wetland_tufts/fill-pattern", "roads_oneway/icon-image", "roads_shields/icon-image", "pois/icon-image", "places_locality/icon-image"),
            switched.keys,
        )
        assertEquals(ImageReference.Name("night/tree_dark_irregular"), switched["landuse_forest_glyphs/fill-pattern"])
        assertEquals(ImageReference.Name("night/tufts"), switched["landuse_wetland_tufts/fill-pattern"])
        assertEquals(ImageReference.Name("night/arrow"), switched["roads_oneway/icon-image"])
        for (key in listOf("roads_shields/icon-image", "pois/icon-image", "places_locality/icon-image")) {
            val json = (switched.getValue(key) as ImageReference.Expr).json
            val labels = Json.parseToJsonElement(json).jsonArray[2].jsonArray.map { it.jsonPrimitive.content }
            assertEquals(key, spriteNames.sorted(), labels)
            assertEquals(key, "night/peak", switchedImageFor(json, references.getValue(key), "peak"))
            // A name with no image at all (Protomaps kinds the sprite does not draw, or places_locality's "" from zoom 8)
            // stays exactly what the day expression gives.
            assertEquals(key, "", switchedImageFor(json, references.getValue(key), ""))
        }
    }

    @Test
    fun `v1 switches every image reference it has, with no night copy missing`() {
        val style = styleOf(1)
        val plan = nightImageSwitchPlan(style.toString(), spriteNames)
        assertEquals(emptyList<String>(), plan.left)
        assertEquals(emptyList<String>(), plan.missingNightCopies)
        assertEquals(imageReferences(style).keys, plan.switches.map { "${it.layerId}/${it.property}" }.toSet())
        assertTrue(plan.switches.isNotEmpty())
    }

    @Test
    fun `a missing night copy leaves its day image drawn, and is named in the log line`() {
        val style = LoadedStyle(styleOf(2))
        val night = (spriteNames - setOf("arrow", "parking")).associateWith { "night pixels of $it" }
        val outcome = applyNightImages(style, night)
        style.loadSprite(spriteNames.associateWith { "day pixels of $it" })

        assertEquals(59, outcome.added)
        assertEquals(5, outcome.switched)
        assertEquals(listOf("roads_oneway/icon-image: no night copy of \"arrow\""), outcome.left)
        assertEquals(listOf("arrow", "parking"), outcome.missingNightCopies.sorted())
        assertEquals("day pixels of arrow", style.drawn("roads_oneway/icon-image", "arrow"))
        assertEquals("day pixels of parking", style.drawn("pois/icon-image", "parking"))
        assertEquals("night pixels of peak", style.drawn("pois/icon-image", "peak"))
        assertTrue(outcome.logLine(), outcome.logLine().endsWith("no night copy of [arrow, parking], drawn in day colours."))
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
