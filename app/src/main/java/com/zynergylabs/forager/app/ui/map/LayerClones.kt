package com.zynergylabs.forager.app.ui.map

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

/** [styleJson]'s clones, as its `forager:layer-clones` records them. */
internal fun layerClonesOf(styleJson: String): LayerClones = LayerClones.NotRecorded

/**
 * These tables with each recorded clone given its original's layer-keyed night: its [TrailNightTables.layerColours] colour
 * and its [TrailNightTables.lineColours] table, where the original has one.
 */
internal fun TrailNightTables.withClones(clones: LayerClones): TrailNightTables = this
