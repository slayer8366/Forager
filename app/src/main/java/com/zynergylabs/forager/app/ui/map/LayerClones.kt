package com.zynergylabs.forager.app.ui.map

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * Trail Map v4's clone layers (dispatch 2026-11-04-03): the first zooms (#474) split 25 layers into 78 clones, each drawing
 * part of its original's features from an earlier zoom, with the original's paint. The style records which is which in
 * `metadata["forager:layer-clones"]`, clone id to original id. The night tables are keyed by layer id
 * ([TrailNightTables.layerColours], [TrailNightTables.lineColours]), so without this a clone drew the plain inversion where
 * its original takes a named night colour. Pure Kotlin over the style's JSON, so every case is asserted headless
 * (`CloneLayersNightTest`); the live style is read by [withLayerClonesOf].
 */
internal sealed interface LayerClones {
    /** The style records its clones: clone id to original id. */
    data class Recorded(val originals: Map<String, String>) : LayerClones

    /** The style records none (Trail Map v1 to v3, and any style without the key). */
    data object NotRecorded : LayerClones

    /** The style has the key, or its JSON, in a form this cannot read; [reason] is what gets logged. */
    data class Unreadable(val reason: String) : LayerClones
}

/** The style metadata key the generator records the clones under (`map-style/generate-trail.mjs`). */
internal const val LAYER_CLONES_KEY = "forager:layer-clones"

/**
 * [styleJson]'s clones, as its `forager:layer-clones` records them: [LayerClones.NotRecorded] when the style has no such key,
 * and [LayerClones.Unreadable] when it, or the style, is not in the form the generator writes (an object of layer ids). One
 * bad entry makes the whole record unreadable, so a half-read record never draws some clones one way and some another.
 */
internal fun layerClonesOf(styleJson: String): LayerClones {
    val style: JsonElement = try {
        Json.parseToJsonElement(styleJson)
    } catch (e: IllegalArgumentException) {
        return LayerClones.Unreadable("the style's JSON could not be read: ${e.message?.take(120)}")
    }
    if (style !is JsonObject) return LayerClones.Unreadable("the style is not a JSON object")
    val metadata = style["metadata"] ?: return LayerClones.NotRecorded
    if (metadata !is JsonObject) return LayerClones.Unreadable("the style's metadata is not an object")
    val record = metadata[LAYER_CLONES_KEY] ?: return LayerClones.NotRecorded
    if (record !is JsonObject) return LayerClones.Unreadable("$LAYER_CLONES_KEY is not an object")
    val originals = record.mapValues { (clone, original) ->
        (original as? JsonPrimitive)?.takeIf { it.isString }?.content
            ?: return LayerClones.Unreadable("$LAYER_CLONES_KEY gives \"$clone\" an original that is not a layer id: $original")
    }
    return LayerClones.Recorded(originals)
}

/**
 * These tables with each recorded clone given its original's layer-keyed night: its [TrailNightTables.layerColours] colour
 * and its [TrailNightTables.lineColours] table, where the original has one. A layer the tables already name keeps its own
 * entry, so a night ruled for one clone is never overwritten by its original's. The tables keyed by day colour
 * ([TrailNightTables.fillColours]) or not by layer at all (the water, [TrailNightTables.hillshade]) need nothing: a clone
 * has its original's paint and source layer. Anything but [LayerClones.Recorded] returns these tables themselves.
 *
 * Why at the tables, not in [trailNightRecolourOf]: the clone is known only from the loaded style, and the tables are what
 * the night pass already takes from it ([trailNightTablesForSource]); the recolour itself is unchanged.
 */
internal fun TrailNightTables.withClones(clones: LayerClones): TrailNightTables {
    if (clones !is LayerClones.Recorded) return this
    return copy(
        layerColours = clonedEntries(layerColours, clones.originals) + layerColours,
        lineColours = clonedEntries(lineColours, clones.originals) + lineColours,
    )
}

/** For each clone whose original has an entry in [byLayer], the clone with that entry. */
private fun <V> clonedEntries(byLayer: Map<String, V>, originals: Map<String, String>): Map<String, V> =
    originals.mapNotNull { (clone, original) -> byLayer[original]?.let { clone to it } }.toMap()
