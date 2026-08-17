package com.forager.app.domain

import com.forager.app.domain.model.TaxonFilter
import com.forager.app.domain.model.TaxonSearchResult

/**
 * What the user currently has selected, together with the broad iNaturalist group it belongs to
 * when that is known.
 *
 * This exists because [TaxonFilter.SpecificTaxon] carries a taxon id and a label and nothing else,
 * so a filter alone cannot answer "which category's guidance applies here". [TaxonSearchResult]
 * already knows the answer — it carries `iconicTaxonName` from iNaturalist and was dropping it on
 * the floor in `toFilter()` — so [fromSearchResult] is where that information is picked back up.
 *
 * The alternative, adding `iconicTaxonName` to [TaxonFilter.SpecificTaxon], was rejected: the
 * filter type's whole job is to describe an iNaturalist query, and the iconic name is not part of
 * the query a specific-taxon search sends. Keeping it out also keeps [TaxonFilter] equality
 * meaning "the same search".
 */
data class ForagingSelection(
    val filter: TaxonFilter,
    /** iNaturalist iconic taxon name, e.g. "Fungi" or "Plantae". Null when not known. */
    val iconicTaxonName: String?,
) {
    companion object {
        fun fromCategory(category: TaxonFilter.IconicCategory) =
            ForagingSelection(category, category.iconicTaxonName)

        /** A species the user searched for by name, carrying the group iNaturalist filed it under. */
        fun fromSearchResult(result: TaxonSearchResult) =
            ForagingSelection(result.toFilter(), result.iconicTaxonName)

        /**
         * The selection behind one of [TaxonFilter.DEFAULT_CATEGORIES]' chips.
         *
         * The Lichens chip is deliberately given a null group rather than "Fungi". iNaturalist
         * does file lichens under Fungi, but the general pattern this app states — fleshy fungi
         * fruiting some weeks after sustained rain — is about fruiting bodies, and reusing it for
         * lichenized fungi would present it as a claim about organisms it was not written about.
         * Forager has no sourced guidance for lichens, so it says so; see
         * [ForagingWeatherGuidance].
         */
        fun forChip(filter: TaxonFilter): ForagingSelection = when (filter) {
            is TaxonFilter.IconicCategory -> fromCategory(filter)
            is TaxonFilter.SpecificTaxon -> ForagingSelection(filter, iconicTaxonName = null)
        }
    }
}

/**
 * Interpretation text for a selection: a general pattern, stated as a general pattern.
 *
 * Three rules this file exists to enforce, all of them load-bearing:
 *
 * 1. **Nothing here is species-specific.** iNaturalist returns thousands of species this app has
 *    no sourced information about, and writing confident fruiting triggers for an arbitrary one
 *    would be fabricating expertise. Guidance is keyed to broad groups only.
 * 2. **Groups do not share text.** Fungi and plants have genuinely different relationships to
 *    rainfall, and a group with nothing useful to say gets told so rather than padded.
 * 3. **A specific taxon always gets [Guidance.speciesDataCaveat].** That sentence is what stops a
 *    general pattern being read as a claim about the species the user picked, and it is the seam a
 *    later data-derived per-species feature fills in.
 */
object ForagingWeatherGuidance {

    /**
     * Guidance to show for a selection.
     *
     * @param paragraphs the general pattern, or an explicit statement that there isn't one.
     * @param speciesDataCaveat non-null exactly when a specific taxon is selected.
     */
    data class Guidance(
        val heading: String,
        val paragraphs: List<String>,
        val speciesDataCaveat: String?,
    )

    /** iNaturalist's iconic taxon name for fungi, as it appears in `iconic_taxon_name`. */
    private const val FUNGI = "Fungi"

    /** iNaturalist's iconic taxon name for plants. */
    private const val PLANTAE = "Plantae"

    fun forSelection(selection: ForagingSelection): Guidance {
        val group = groupGuidance(selection.iconicTaxonName)
        return when (val filter = selection.filter) {
            is TaxonFilter.IconicCategory -> group
            is TaxonFilter.SpecificTaxon -> group.copy(
                speciesDataCaveat = speciesCaveat(filter.label, selection.iconicTaxonName),
            )
        }
    }

    private fun groupGuidance(iconicTaxonName: String?): Guidance = when (iconicTaxonName) {
        FUNGI -> Guidance(
            heading = "Rain and fungi: the general pattern",
            paragraphs = listOf(
                "Many fleshy fungi fruit roughly one to three weeks after sustained rain, once " +
                    "the soil has stayed damp long enough for mycelium already in the ground to " +
                    "respond. That one-to-three-week range is long-standing foraging lore and " +
                    "general mycological writing — it is not a figure Forager has measured, and " +
                    "the real lag varies with species, substrate and temperature.",
                "Soil moisture and soil temperature in the top few centimetres are shown because " +
                    "that is the layer mycelium actually sits in; surface rainfall can overstate " +
                    "or understate how wet it is down there. A soil temperature of roughly " +
                    "${format(FruitingPatternAssumptions.TEMPERATE_FRUITING_SOIL_TEMPERATURE_C.start)}–" +
                    "${format(FruitingPatternAssumptions.TEMPERATE_FRUITING_SOIL_TEMPERATURE_C.endInclusive)} °C " +
                    "is often quoted as broadly typical for temperate fleshy fungi. It is a wide " +
                    "band quoted as a rough one, and plenty of species sit outside it.",
                "Forager has not measured any relationship between these conditions and how often " +
                    "anything is actually observed near you. The dates and measurements are what " +
                    "the weather did; whether that adds up to a trip is your call, not a score " +
                    "this app is in a position to give.",
            ),
            speciesDataCaveat = null,
        )

        PLANTAE -> Guidance(
            heading = "Rain and plants: no pattern to offer",
            paragraphs = listOf(
                "Forager has no weather-based pattern for plants, and is not going to reuse the " +
                    "fungal one. Rain obviously matters to plants, but what makes a plant worth " +
                    "foraging on a particular day is mostly where it is in its own year — " +
                    "leafing, flowering, fruiting, setting seed — rather than a lag after a rain " +
                    "event the way it is for fleshy fungi.",
                "The rainfall and soil measurements are still shown, without an interpretation " +
                    "attached. The month filter on the ranked list is the seasonality signal " +
                    "Forager does have, and it is built on real observation counts.",
            ),
            speciesDataCaveat = null,
        )

        else -> Guidance(
            heading = "No weather guidance for this selection",
            paragraphs = listOf(
                "Forager only has a general weather pattern written for fungi, and an explicit " +
                    "\"no pattern\" for plants. It has nothing sourced for this selection, so it " +
                    "is not going to offer an interpretation of the measurements below.",
            ),
            speciesDataCaveat = null,
        )
    }

    private fun speciesCaveat(label: String, iconicTaxonName: String?): String {
        val group = when (iconicTaxonName) {
            FUNGI -> "fungi in general"
            PLANTAE -> "plants in general"
            else -> null
        }
        return if (group == null) {
            "No species-specific data is available for $label. Forager has no per-species " +
                "fruiting or growth data at all, and nothing above is a statement about $label."
        } else {
            "No species-specific data is available for $label. Anything above is the general " +
                "pattern for $group — it is not a claim about $label. Forager has no per-species " +
                "fruiting or growth data, and iNaturalist returns thousands of species this app " +
                "has no sourced information for."
        }
    }

    /** Trims a whole-number double to "10" rather than "10.0" for use in prose. */
    private fun format(value: Double): String =
        if (value == value.toLong().toDouble()) value.toLong().toString() else value.toString()
}
