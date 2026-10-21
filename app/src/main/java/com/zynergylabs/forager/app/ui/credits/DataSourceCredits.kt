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
            // OpenTopoMap's own required form (opentopomap.org/about), in full: the map's caption carries a
            // shortened one to fit a line (Basemap.OPEN_TOPO_MAP), and this page has the room.
            SourceCredit(
                "opentopomap",
                "Map data: © OpenStreetMap contributors, SRTM; map style: © OpenTopoMap (CC-BY-SA)",
                "licence",
                "https://opentopomap.org/about",
            ),
            // The offline map packs' vector basemap, and the own Street map's once it is on (dispatch 2026-10-18-05
            // Part 2, StreetStyle.kt); its OpenStreetMap data is credited in the row above.
            SourceCredit("protomaps", protomapsCreditText(), "licence", "https://github.com/protomaps/basemaps"),
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

/** The Protomaps row's text: what it is used for, which is offline maps today and Street too once the own Street is on. */
internal fun protomapsCreditText(ownStreet: Boolean = com.zynergylabs.forager.app.map.STREET_SERVED_BY_OWN_MAP): String =
    if (ownStreet) "Protomaps (Street and offline maps)" else "Protomaps (offline maps)"
