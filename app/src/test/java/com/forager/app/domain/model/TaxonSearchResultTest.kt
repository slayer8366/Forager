package com.forager.app.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

class TaxonSearchResultTest {

    @Test
    fun `toFilter prefers the common name as the label when present`() {
        val result = TaxonSearchResult(
            taxonId = 47348,
            scientificName = "Cantharellus",
            commonName = "chanterelles",
            rank = "genus",
            iconicTaxonName = "Fungi",
            photoUrl = null,
        )

        val filter = result.toFilter()

        assertEquals(TaxonFilter.SpecificTaxon(taxonId = 47348, label = "chanterelles"), filter)
    }

    @Test
    fun `toFilter falls back to the scientific name when there is no common name`() {
        val result = TaxonSearchResult(
            taxonId = 12345,
            scientificName = "Amanita phalloides",
            commonName = null,
            rank = "species",
            iconicTaxonName = "Fungi",
            photoUrl = null,
        )

        val filter = result.toFilter()

        assertEquals(TaxonFilter.SpecificTaxon(taxonId = 12345, label = "Amanita phalloides"), filter)
    }
}
