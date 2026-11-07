package com.zynergylabs.forager.app.ui.map

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

// The style file's own layer list, for the names anchor (AppLinesUnderNames.kt; dispatch 2026-11-06-01 Amendment 1, I10,
// RECORD -2183). From the S22 check on build 4825: I9's filter left out MapLibre's position-dot layers by their id prefix
// but not its annotation layer `org.maplibre.annotations.points`, a symbol layer the map's native core adds on top of every
// style it loads, so Topographical, Street and Topographical at night anchored under it. I10: the basemap's own layers are
// those that came from the style file, and it asks for a rule that reads the style's own list over a list of known
// prefixes if the API allows. MapLibre 13.5.0 does (javap on the pinned AAR): `Style.getJson()` returns
// `NativeMap.getStyleJson()`, the JSON the style was loaded from, by URL or as a string; layers added at run time (the
// annotation layer, the position dot, `Style.Builder.withLayer`, the app's) are not in that file. The prefix list stays,
// as the logged fallback when the file cannot be read and as a second guard ([isBasemapOwnLayer]).

/** What the style file says about its own layers. */
internal sealed interface StyleFileLayers {
    /** The ids of the layers the style file declares. */
    data class Read(val ids: Set<String>) : StyleFileLayers

    /** The file could not be read, for [reason]; the anchor falls back to leaving layers out by id alone, and says so. */
    data class Unreadable(val reason: String) : StyleFileLayers
}

@Serializable
private class StyleFileLayer(val id: String)

@Serializable
private class StyleFile(val layers: List<StyleFileLayer> = emptyList())

/** Only `layers[].id` is decoded; everything else in the file (sources, paint, metadata) is skipped. */
private val styleFileJson = Json { ignoreUnknownKeys = true }

/**
 * The layers [json] (the loaded style's `Style.getJson()`) declares. Blank (`NativeMapView.getStyleJson` returns `""` on a
 * destroyed map), unparseable, or declaring no layer at all is [StyleFileLayers.Unreadable], never an empty [Read]: an
 * empty list would quietly leave out every layer, where the fallback still counts the basemap's.
 */
internal fun styleFileLayersOf(json: String?): StyleFileLayers {
    if (json.isNullOrBlank()) return StyleFileLayers.Unreadable("the style returned no JSON")
    val file = try {
        styleFileJson.decodeFromString(StyleFile.serializer(), json)
    } catch (e: IllegalArgumentException) {
        // kotlinx's SerializationException is an IllegalArgumentException, as is a malformed-input error from its lexer.
        return StyleFileLayers.Unreadable("its JSON did not parse (${e.message?.lineSequence()?.firstOrNull()})")
    }
    if (file.layers.isEmpty()) return StyleFileLayers.Unreadable("its JSON declares no layers")
    return StyleFileLayers.Read(file.layers.map { it.id }.toSet())
}
