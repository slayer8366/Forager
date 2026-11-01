package com.zynergylabs.forager.app.ui.map

/**
 * The Street night's two tables for one Trail Map style version: line colours by layer, and fill colours by day colour
 * (see [streetNightRecolourOf]). Each version's design copy is in its style's metadata, `forager:night-layers` and
 * `forager:night-fills`, and a test holds the app's copy equal to it.
 *
 * Why per version: Street v4 (the fill-in) changes night colours v3 has, the major roads' and the rail's, so one table
 * shared by every version would change how regions downloaded on v3 draw at night. Each style is a URL that never
 * changes meaning, so the URL it was loaded from says which tables it takes.
 */
internal data class StreetNightTables(
    val layerColours: Map<String, String>,
    val fillColours: Map<Int, Int>,
    /** Per layer, night line colours by day colour (`forager:night-line-colours`, v4 only): one road class's night. */
    val lineColours: Map<String, Map<Int, Int>> = emptyMap(),
    /** The hillshade layers' night (`forager:night-hillshade`, v4 only); the recolour walks no hillshade colour. */
    val hillshade: HillshadeNight? = null,
)

/**
 * A hillshade layer's three night colours, as MapLibre colour strings (dispatch 2026-11-01-coder-11; Sage's sage-11 ALT).
 * Applied in place of the day's by [applyStreetHillshadeNight]: the night recolour never touches hillshade colours, and
 * the day's white highlight would glow on the dark map.
 */
internal data class HillshadeNight(val shadow: String, val highlight: String, val accent: String)

/** Trail Map v1 to v3's night tables, and Street under Topographical's: the ones every version had before v4. */
internal val STREET_NIGHT_TABLES = StreetNightTables(STREET_NIGHT_LAYER_COLOURS, STREET_NIGHT_FILL_COLOURS)

/**
 * Street v4's night (Forager-cartography `docs/design/2026-10-30-fill-in-rulings.md`; the owner: "Keep it osm-like",
 * "Yes, ship it"; ramps: "Invert them too (Recommended)"; then "Motorways" and "Night tertiary and rail"). The design's
 * copy is `NIGHT_LAYER_COLOURS_V4`, `NIGHT_FILL_COLOURS_V4` and `NIGHT_LINE_COLOURS_V4` in
 * `map-style/overrides/forager-fill-in.mjs`; `NightExceptionsV4Test` holds them equal.
 * - Lines: v3's, except the major roads, the motorways and their ramps ("Invert like OSM"), and the rail ("Plain
 *   inversion") take the plain inversion, as the app's OpenStreetMap night does (trunk `#845104`, motorway `#7f2939`,
 *   rail `#636c6f`; each ramp with its road), and the tertiary's day white is `#333333` ("Dark grey #333333").
 * - Fills: v3's, and the five new fills: residential, commercial, railway land, farmland and parks.
 */
internal val STREET_V4_NIGHT_TABLES = StreetNightTables(
    layerColours = STREET_NIGHT_LAYER_COLOURS - setOf(
        "roads_major", "roads_bridges_major",
        "roads_highway", "roads_bridges_highway", "roads_link", "roads_bridges_link",
        "roads_rail",
    ),
    fillColours = STREET_NIGHT_FILL_COLOURS + mapOf(
        0xD5D1CB to 0x2D2A25, // residential
        0xE3CFCA to 0x362824, // commercial
        0xD6D1D4 to 0x2B2829, // railway land
        0xE8EACD to 0x2E2A10, // farmland
        0xCDE5BF to 0x2C461E, // parks: the inversion #2b431d is 1.04:1 on the night water, under the ruled 1.05
    ),
    lineColours = mapOf(
        // The tertiary's day white, dark grey at night: under the night footpath in forest, where the black inversion outranked
        // it; the minor roads' white with it ("Low-zoom roads").
        "roads_major" to mapOf(0xFFFFFF to 0x333333),
        "roads_bridges_major" to mapOf(0xFFFFFF to 0x333333),
        "roads_minor" to mapOf(0xFFFFFF to 0x333333),
        // Darker night rims on the area edges ("Darker rims (Recommended)", Sage's REC table), by each edge's day colour.
        "landuse_edges" to mapOf(
            0x94B988 to 0x38552B, 0xC2CDAA to 0x1D240B, 0xB4CAA8 to 0x203216, 0x88B99D to 0x255239, 0xADC8BC to 0x203C36,
            0xB0ABA4 to 0x5D636C, 0xC3BFBA to 0x1D1B18, 0xD0BDB9 to 0x241A18, 0xC4BFC2 to 0x1A1919, 0xCCCEB4 to 0x131207,
            0xC9C3BD to 0x3B352E, 0xC9C0BF to 0x3C3231, 0xC6B3C3 to 0x382735,
            // Orchards, plant nurseries and vineyards (sage-10).
            0xB0C286 to 0x3A4B14,
        ),
    ),
    // Hill shading's night (Sage's sage-11 ALT): darker shadows and a faint cool highlight, so the relief never glows.
)

/**
 * The style file names that are Street v4: the Worker's route (`/style/street-v4.json`, not yet served) and the
 * repository's file (`map-style/forager-street-v4.json`), so a test build loading the file draws the same night.
 */
private val STREET_V4_STYLE_FILES = setOf("street-v4.json", "forager-street-v4.json")

/**
 * The night tables for the Trail Map style loaded from [styleUri]: v4's for a v4 style file, else v1 to v3's (`null`,
 * a style not loaded by URI, included). Only a style the app treats as Trail Map takes these at all (`usesStreetSprite`).
 */
internal fun streetNightTablesFor(styleUri: String?): StreetNightTables =
    if (styleUri?.substringAfterLast('/') in STREET_V4_STYLE_FILES) STREET_V4_NIGHT_TABLES else STREET_NIGHT_TABLES
