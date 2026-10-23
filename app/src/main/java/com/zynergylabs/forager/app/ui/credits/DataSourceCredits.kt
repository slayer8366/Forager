package com.zynergylabs.forager.app.ui.credits

/**
 * The data Forager shows from other people's work, credited as each one's licence asks (dispatch
 * 2026-10-13-05; the owner: "We need a part in the app that shows our sources ... the data we have
 * should be credited appropriately"). The wording is the sources-and-credits inventory's
 * (`docs/audits/2026-10-13-sources-and-credits-inventory.md`, branch sources-credits-inventory), held
 * to each licence's required form, and the links are the planner's answer Q4.
 *
 * Anything later used from another person's work is credited here, in the same change that starts
 * using it (the owner: "anything used from his repo should be stated in the credits along with the
 * others").
 */
internal data class SourceCredit(
    /** Stable, for test tags. */
    val key: String,
    val text: String,
    /** What the row's link opens, in a word or two: "licence", "site". */
    val linkLabel: String,
    val url: String,
    /** A plain line shown under the row, when the source needs one. */
    val detail: String? = null,
)

internal data class SourceSection(val title: String, val credits: List<SourceCredit>)

internal val DATA_SOURCE_SECTIONS: List<SourceSection> = listOf(
    SourceSection(
        "Maps",
        listOf(
            // The OpenStreetMap Foundation's attribution guidelines: the credit, and a route to the licence.
            SourceCredit("openstreetmap", "© OpenStreetMap contributors", "licence", "https://www.openstreetmap.org/copyright"),
            // The trail routing data (BRouter .rd5 files, built from OpenStreetMap by the map build), credited under its
            // licence, the Open Database License, as dispatch 2026-10-23-02 asks. Credited from the change that builds
            // the router in, ahead of the downloads that will carry the data.
            SourceCredit("openstreetmap-routes", TRAIL_ROUTES_CREDIT_TEXT, "ODbL", "https://opendatacommons.org/licenses/odbl/1-0/"),
            // OpenTopoMap's own required form (opentopomap.org/about), in full: the map's caption carries a
            // shortened one to fit a line (Basemap.OPEN_TOPO_MAP), and this page has the room.
            SourceCredit(
                "opentopomap",
                "Map data: © OpenStreetMap contributors, SRTM; map style: © OpenTopoMap (CC-BY-SA)",
                "licence",
                "https://opentopomap.org/about",
            ),
            // Forager Trail Map's vector tiles and the offline map packs' (StreetStyle.kt); its OpenStreetMap data is
            // credited in the row above.
            SourceCredit("protomaps", PROTOMAPS_CREDIT_TEXT, "licence", "https://github.com/protomaps/basemaps"),
        ),
    ),
    // The forager-forecast map layers and the data behind them (GBIF occurrences among it) go here, as
    // their own section, in the change that makes them live. They are not listed yet: the map reads
    // AbsentForecastCellStore (AppContainer.forecastCellStore), so no forecast data is shown anywhere.
    SourceSection(
        "Weather",
        listOf(
            SourceCredit(
                "open-meteo",
                OPEN_METEO_CREDIT_TEXT,
                "CC BY 4.0",
                "https://open-meteo.com/en/licence",
                // CC BY 4.0 asks a user of the data to say if it was changed. The planner's answer Q5; the owner may reword it.
                detail = "Rain and soil figures in Forager are summaries calculated from this data.",
            ),
        ),
    ),
    SourceSection(
        "Sightings and species names",
        listOf(SourceCredit("inaturalist", "iNaturalist and its observers", "site", "https://www.inaturalist.org/")),
    ),
)

/** The trail routing data's row: OpenStreetMap data under the ODbL (dispatch 2026-10-23-02). */
internal const val TRAIL_ROUTES_CREDIT_TEXT = "Trail routes: © OpenStreetMap contributors"

/**
 * The Protomaps row's text: what it is used for. Forager Trail Map is always offered since dispatch 2026-10-22-01, so the
 * text no longer depends on a switch; "Street" became "Forager Trail Map" (RECORD -1556: credit text that names our map
 * says "Forager Trail Map").
 */
internal const val PROTOMAPS_CREDIT_TEXT = "Protomaps (Forager Trail Map and offline maps)"
