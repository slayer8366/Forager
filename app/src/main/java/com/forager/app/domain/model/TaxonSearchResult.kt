package com.forager.app.domain.model

/** One match from a species/taxon name search, used to build a [TaxonFilter.SpecificTaxon]. */
data class TaxonSearchResult(
    val taxonId: Long,
    val scientificName: String,
    val commonName: String?,
    val rank: String?,
    val iconicTaxonName: String?,
    val photoUrl: String?,
) {
    fun toFilter(): TaxonFilter = TaxonFilter.SpecificTaxon(
        taxonId = taxonId,
        label = commonName ?: scientificName,
    )
}
