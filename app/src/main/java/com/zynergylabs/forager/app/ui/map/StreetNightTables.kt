package com.zynergylabs.forager.app.ui.map

/**
 * The Street night's two tables for one Trail Map style version: line colours by layer, and fill colours by day colour
 * (see [streetNightRecolourOf]). Each version's design copy is in its style's metadata, `forager:night-layers` and
 * `forager:night-fills`, and a test holds the app's copy equal to it.
 */
internal data class StreetNightTables(val layerColours: Map<String, String>, val fillColours: Map<Int, Int>)

/** Trail Map v1 to v3's night tables, and Street under Topographical's: the ones every version had before v4. */
internal val STREET_NIGHT_TABLES = StreetNightTables(STREET_NIGHT_LAYER_COLOURS, STREET_NIGHT_FILL_COLOURS)

/** The night tables for the Trail Map style loaded from [styleUri]; `null` (not loaded by URI) is v1 to v3's. */
internal fun streetNightTablesFor(styleUri: String?): StreetNightTables = STREET_NIGHT_TABLES
