package com.zynergylabs.forager.app.ui.map

import kotlinx.serialization.json.JsonObject

/**
 * The one line MapStyleReload logs on every style load (dispatch 2026-11-04-05 B1, RECORD -2061): which style loaded, how
 * many layers MapLibre holds, whether its JSON records Trail Map v4's clones, and how many metadata keys that JSON carries.
 *
 * Why: on the S22 every Trail Map load logged "no forager:layer-clones" while the Worker served the record, and nothing
 * logged could say whether the phone had a stale copy of trail-v4.json (104 layers, no record: Worker release #472, before
 * #477) or MapLibre's `Style.json` had dropped the metadata of a fresh one (182 layers). [layerCount] is MapLibre's own
 * layer list (`style.layers`), not the JSON's, so the two are read independently: 104 and absent is the stale copy, 182 and
 * "no metadata" is MapLibre dropping it, 182 and recorded is the fix drawing. The count includes any layer the style
 * builder adds (Topographical's raster over Trail Map: one more), and none of the overlays, which are added after.
 *
 * [url] is the style the load asked for (`trailUriOf`), `null` for a style built from JSON in the app. Pure, so each case
 * is asserted headless (`StyleLoadLogTest`). The JSON is parsed once here; the night pass parses it again for its own reads.
 */
internal fun styleLoadLogLine(url: String?, layerCount: Int, styleJson: String): String {
    val where = url ?: "none (a style built in the app)"
    val parsed = parsedStyleOf(styleJson)
    val (clones, keys) = when (parsed) {
        is ParsedStyle.Unparsed -> "style JSON unreadable (${parsed.reason})" to "metadata unread"
        is ParsedStyle.Parsed -> {
            val clones = when (val c = layerClonesIn(parsed.json)) {
                is LayerClones.Recorded -> "$LAYER_CLONES_KEY recorded: ${c.originals.size} clones"
                is LayerClones.NotRecorded -> "$LAYER_CLONES_KEY absent (${c.what})"
                is LayerClones.Unreadable -> "$LAYER_CLONES_KEY unreadable (${c.reason})"
            }
            val metadata = (parsed.json as? JsonObject)?.get("metadata")
            val keys = when {
                metadata == null -> "no metadata"
                metadata is JsonObject -> "${metadata.size} metadata keys"
                else -> "metadata not an object"
            }
            clones to keys
        }
    }
    return "Style loaded: url=$where, $layerCount layers, $clones, $keys."
}
