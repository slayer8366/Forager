package com.forager.app.domain.model

/** What to search for: a broad iNaturalist group, or one specific taxon (from a species search). */
sealed interface TaxonFilter {
    val label: String

    /** Maps to iNaturalist's `iconic_taxa` query param. */
    data class IconicCategory(val iconicTaxonName: String, override val label: String) : TaxonFilter

    /** Maps to iNaturalist's `taxon_id` query param, which also matches descendant taxa. */
    data class SpecificTaxon(val taxonId: Long, override val label: String) : TaxonFilter

    companion object {
        val FUNGI = IconicCategory(iconicTaxonName = "Fungi", label = "Fungi")
        val PLANTS = IconicCategory(iconicTaxonName = "Plantae", label = "Plants")

        /**
         * iNaturalist has no distinct top-level "lichen" group — lichens are classified
         * under its Fungi iconic taxon (labeled "Fungi Including Lichens" by iNaturalist
         * itself). This approximates a lichen filter using Lecanoromycetes (taxon 54743),
         * the class covering the large majority of lichenized fungi species, rather than
         * claiming exact coverage the API doesn't provide.
         */
        val LICHENS = SpecificTaxon(taxonId = 54743L, label = "Lichens (approx.)")

        val DEFAULT_CATEGORIES = listOf(FUNGI, PLANTS, LICHENS)
    }
}
