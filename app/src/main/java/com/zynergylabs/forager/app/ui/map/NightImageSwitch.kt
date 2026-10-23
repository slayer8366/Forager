package com.zynergylabs.forager.app.ui.map

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray

/**
 * Trail Map's night icons and patterns (dispatch 2026-10-22-06 Amendment 1; the owner, RECORD -1566: "Yes, D"): a
 * ready-made night copy of every sprite image ships in the app, is added to the loaded style under its own name
 * (`night/<name>`), and at night the style's image references point at those copies. Nothing is repainted on the phone.
 *
 * **Why not the sprite's own names.** MapLibre Native 13.5.0 loads a style's sprite after the `setStyle` callback
 * (about 220 ms later on the S22, dispatch 2026-10-22-05) and replaces any image already in the style under the same
 * name when it does (`style_impl.cpp:374-381` at `android-v13.5.0`). A night copy added under the sprite's own name is
 * overwritten by the day image; under `night/<name>` no sprite image ever touches it, so it is there from the callback
 * on, whatever the sprite's timing.
 *
 * Pure Kotlin over the style's JSON, so every case is asserted headless (`NightImageSwitchTest`);
 * `NightImageApply.kt` is the thin adapter over the live style.
 */
internal const val NIGHT_IMAGE_PREFIX = "night/"

/** The style's name for [name]'s night copy. */
internal fun nightImageName(name: String): String = NIGHT_IMAGE_PREFIX + name

/**
 * The image properties the switch walks, by the block of a layer they sit in. Trail Map v1 and v2 draw images through
 * `icon-image` and `fill-pattern` only (six references in v2); [OTHER_IMAGE_PROPERTIES] are reported, never switched.
 */
internal val SWITCHED_IMAGE_PROPERTIES: Map<String, String> = mapOf("icon-image" to "layout", "fill-pattern" to "paint")

/** Image properties MapLibre has that the switch has no setter for: a layer using one is logged as left in day images. */
internal val OTHER_IMAGE_PROPERTIES: Map<String, String> =
    mapOf("line-pattern" to "paint", "background-pattern" to "paint", "fill-extrusion-pattern" to "paint")

/** An image reference's night value: a plain image name, or an expression as JSON text. */
internal sealed interface ImageReference {
    data class Name(val name: String) : ImageReference
    data class Expr(val json: String) : ImageReference
}

/** One layer property to switch, with its night value. */
internal data class NightImageReference(val layerId: String, val property: String, val night: ImageReference)

/**
 * What the switch will do to one style: the references it switches; those it leaves on their day images, each with its
 * reason; and the images the style draws or records that have no night copy, which therefore stay in day colours.
 */
internal data class NightImageSwitchPlan(
    val switches: List<NightImageReference>,
    val left: List<String>,
    val missingNightCopies: List<String>,
)

/**
 * The plan for [styleJson] given the night copies there are ([nightNames], by their day names).
 *
 * - A **literal name** with a night copy becomes the copy's name; one without is left, and named as missing.
 * - An **expression** becomes `["match", E, [names with a night copy], ["concat", "night/", E], E]`: the night copy
 *   when the day image it names has one, otherwise the day image itself. The fallback is decided here, by the app, from
 *   the names it holds, rather than by MapLibre's image availability (`coalesce`/`image`), whose result in a
 *   feature-constant property MapLibre Native evaluates without the available images (verify-first report).
 * - Names with no night copy are found from the literals and from the style's own record of its sprite
 *   (`metadata["forager:sprite-icons"]`, which v1 and v2 carry), since an expression's names are only known per feature.
 */
internal fun nightImageSwitchPlan(styleJson: String, nightNames: Set<String>): NightImageSwitchPlan {
    val style = try {
        Json.parseToJsonElement(styleJson) as? JsonObject
    } catch (e: IllegalArgumentException) {
        null
    } ?: return NightImageSwitchPlan(emptyList(), listOf("style: its JSON could not be read"), emptyList())
    val switches = mutableListOf<NightImageReference>()
    val left = mutableListOf<String>()
    val missing = linkedSetOf<String>()
    val labels = buildJsonArray { nightNames.sorted().forEach { add(JsonPrimitive(it)) } }
    for (layer in (style["layers"] as? JsonArray).orEmpty()) {
        val layerObject = layer as? JsonObject ?: continue
        val id = (layerObject["id"] as? JsonPrimitive)?.content ?: continue
        for ((property, block) in OTHER_IMAGE_PROPERTIES) {
            if ((layerObject[block] as? JsonObject)?.get(property) != null) left += "$id/$property: not switched (no setter for $property)"
        }
        for ((property, block) in SWITCHED_IMAGE_PROPERTIES) {
            val value = (layerObject[block] as? JsonObject)?.get(property) ?: continue
            when {
                value is JsonPrimitive && value.isString -> {
                    val name = value.content
                    if (name in nightNames) {
                        switches += NightImageReference(id, property, ImageReference.Name(nightImageName(name)))
                    } else {
                        left += "$id/$property: no night copy of \"$name\""
                        missing += name
                    }
                }
                value is JsonArray && nightNames.isEmpty() -> left += "$id/$property: no night copies at all"
                value is JsonArray -> {
                    val match = buildJsonArray {
                        add(JsonPrimitive("match"))
                        add(value)
                        add(labels)
                        add(buildJsonArray { add(JsonPrimitive("concat")); add(JsonPrimitive(NIGHT_IMAGE_PREFIX)); add(value) })
                        add(value)
                    }
                    switches += NightImageReference(id, property, ImageReference.Expr(match.toString()))
                }
                else -> left += "$id/$property: a value that is neither a name nor an expression: $value"
            }
        }
    }
    val recorded = recordedSpriteImages(style)
    missing += recorded.filter { it !in nightNames }
    return NightImageSwitchPlan(switches, left, missing.toList())
}

/** The sprite images [style] records drawing (`metadata["forager:sprite-icons"]`), or none when it records none. */
private fun recordedSpriteImages(style: JsonObject): List<String> {
    val recorded: JsonElement = (style["metadata"] as? JsonObject)?.get("forager:sprite-icons") ?: return emptyList()
    return (recorded as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.takeIf { p -> p.isString }?.content }.orEmpty()
}

/** Where the night step adds images and switches references: the live style on a phone, a fake in the tests. */
internal interface NightImageTarget<T> {
    /** The loaded style's own JSON, as it was fetched. */
    val styleJson: String

    /** Adds [images] to the style by name. */
    fun addImages(images: Map<String, T>)

    /** Sets [property] of layer [layerId] to [value]; `false` when there is no such layer or property to set. */
    fun setImageReference(layerId: String, property: String, value: ImageReference): Boolean
}

/** What one night step did: its log line says it all, so a partial result is never reported as a whole one. */
internal data class NightImageOutcome(
    val added: Int,
    val switched: Int,
    val left: List<String>,
    val missingNightCopies: List<String>,
) {
    fun logLine(): String =
        "Street night: $added night images added, $switched image references switched, ${left.size} left in day images" +
            (if (left.isEmpty()) "" else ": $left") +
            (if (missingNightCopies.isEmpty()) "; no night copy missing." else "; no night copy of $missingNightCopies, drawn in day colours.")
}

/**
 * The night step: adds every night copy in [nightImages] (keyed by its day name) under its night name, then switches
 * the style's references to them. Called in the `setStyle` callback; reads nothing from the sprite, so it does the same
 * whether or not MapLibre has loaded the sprite by then.
 */
internal fun <T> applyNightImages(target: NightImageTarget<T>, nightImages: Map<String, T>): NightImageOutcome {
    if (nightImages.isNotEmpty()) target.addImages(nightImages.mapKeys { (name, _) -> nightImageName(name) })
    val plan = nightImageSwitchPlan(target.styleJson, nightImages.keys)
    val left = plan.left.toMutableList()
    var switched = 0
    for (reference in plan.switches) {
        val set = try {
            target.setImageReference(reference.layerId, reference.property, reference.night)
        } catch (e: RuntimeException) {
            left += "${reference.layerId}/${reference.property}: could not set (${e.javaClass.simpleName}: ${e.message})"
            continue
        }
        if (set) switched++ else left += "${reference.layerId}/${reference.property}: no such layer to set"
    }
    return NightImageOutcome(nightImages.size, switched, left, plan.missingNightCopies)
}
