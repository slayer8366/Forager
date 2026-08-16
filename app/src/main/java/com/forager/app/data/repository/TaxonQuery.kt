package com.forager.app.data.repository

import com.forager.app.domain.model.TaxonFilter

/**
 * The iNaturalist query parameters one [TaxonFilter] translates to.
 *
 * Both `observations/species_counts` and `observations` take the same three taxon
 * parameters, and both endpoints are called with the same filter semantics. Translating
 * once here keeps the two call sites in [INaturalistMushroomRepository] from drifting
 * apart as filter kinds gain parameters.
 */
internal data class TaxonQuery(
    val iconicTaxa: String?,
    val taxonId: Long?,
    val withoutTaxonId: Long?,
)

internal fun TaxonFilter.toQuery(): TaxonQuery = TaxonQuery(
    iconicTaxa = (this as? TaxonFilter.IconicCategory)?.iconicTaxonName,
    taxonId = (this as? TaxonFilter.SpecificTaxon)?.taxonId,
    withoutTaxonId = excludedTaxonId,
)
