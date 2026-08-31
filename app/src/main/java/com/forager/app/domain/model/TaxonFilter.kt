package com.forager.app.domain.model

/** What to search for: a broad iNaturalist group, or one specific taxon (from a species search). */
sealed interface TaxonFilter {
    val label: String

    /**
     * A taxon to subtract from this filter's results, or null to subtract nothing.
     * Maps to iNaturalist's `without_taxon_id` query param, which — like `taxon_id` —
     * also matches descendant taxa.
     */
    val excludedTaxonId: Long?

    /** Maps to iNaturalist's `iconic_taxa` query param. */
    data class IconicCategory(
        val iconicTaxonName: String,
        override val label: String,
        override val excludedTaxonId: Long? = null,
    ) : TaxonFilter

    /**
     * Maps to iNaturalist's `taxon_id` query param, which also matches descendant taxa.
     *
     * Never carries an exclusion, and deliberately offers no way to set one: a
     * [SpecificTaxon] is an explicit "I want this taxon" — either the Lichens chip or a
     * species the user searched for by name — and subtracting anything from an explicitly
     * chosen taxon would contradict the choice. Scoping exclusion to [IconicCategory]
     * alone is what keeps [LICHENS] working while Fungi drops lichens.
     */
    data class SpecificTaxon(val taxonId: Long, override val label: String) : TaxonFilter {
        override val excludedTaxonId: Long? get() = null
    }

    companion object {
        /**
         * Lecanoromycetes — the class covering the large majority of lichenized fungi
         * species. Single source of truth: it is both what [LICHENS] selects and what
         * [FUNGI] subtracts, so the chip and the exclusion cannot drift apart.
         */
        private const val LECANOROMYCETES_TAXON_ID = 54743L

        /**
         * iNaturalist classifies lichens under its Fungi iconic taxon, so an unfiltered
         * Fungi search returns them mixed in with mushrooms — in one real 15 km search,
         * three of the top five ranked species were lichens. Forager ranks what's worth
         * looking for, so Fungi subtracts them and the [LICHENS] chip is the opt-in.
         *
         * This removes *most* lichens, not all: Lecanoromycetes is the large majority of
         * lichenized species but not every one — other lichenized classes exist
         * (Arthoniomycetes, Lichinomycetes, Candelariomycetes and others), and taxa from
         * them do still appear in Fungi results. Widening this to a hand-assembled list of
         * every lichenized class was rejected as a bigger taxonomic call than the observed
         * problem justifies; the residue is small and can be revisited with real data.
         */
        val FUNGI = IconicCategory(
            iconicTaxonName = "Fungi",
            label = "Fungi",
            excludedTaxonId = LECANOROMYCETES_TAXON_ID,
        )

        val PLANTS = IconicCategory(iconicTaxonName = "Plantae", label = "Plants")

        /**
         * iNaturalist has no distinct top-level "lichen" group — lichens are classified
         * under its Fungi iconic taxon (labeled "Fungi Including Lichens" by iNaturalist
         * itself). This approximates a lichen filter using Lecanoromycetes (taxon 54743),
         * the class covering the large majority of lichenized fungi species, rather than
         * claiming exact coverage the API doesn't provide.
         */
        val LICHENS = SpecificTaxon(taxonId = LECANOROMYCETES_TAXON_ID, label = "Lichens (approx.)")
    }
}
