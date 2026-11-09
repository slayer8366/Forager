package com.zynergylabs.forager.app.domain

import com.zynergylabs.forager.app.domain.model.TaxonFilter
import com.zynergylabs.forager.app.domain.model.TaxonSearchResult
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * [ForagingSelection], which recent searches still store and restore. Was ForagingWeatherGuidanceTest: RECORD -727
 * removed the weather guidance text (ForagingWeatherGuidance's Guidance and forSelection) and the tests of it.
 */
class ForagingSelectionTest {

    // ---- selection plumbing ----------------------------------------------------------------

    @Test
    fun `a category selection carries its own iconic taxon name`() {
        val selection = ForagingSelection.fromCategory(TaxonFilter.FUNGI)

        assertEquals("Fungi", selection.iconicTaxonName)
        assertEquals(TaxonFilter.FUNGI, selection.filter)
    }

    @Test
    fun `a searched species carries the group iNaturalist filed it under`() {
        val result = TaxonSearchResult(
            taxonId = 47348,
            scientificName = "Cantharellus",
            commonName = "chanterelles",
            rank = "genus",
            iconicTaxonName = "Fungi",
            photoUrl = null,
        )

        val selection = ForagingSelection.fromSearchResult(result)

        assertEquals(TaxonFilter.SpecificTaxon(taxonId = 47348, label = "chanterelles"), selection.filter)
        assertEquals("Fungi", selection.iconicTaxonName)
    }

    /** Amendment 1 to -695: a recent search hands back the group stored with it, and only a species carries one. */
    @Test
    fun `a recent search rebuilds the selection with its stored group, and an old or lichens one has none`() {
        val flyAgaric = TaxonFilter.SpecificTaxon(taxonId = 48715, label = "Fly Agaric")
        val region = com.zynergylabs.forager.app.domain.model.Region(lat = 45.326, lng = -122.634, radiusKm = 15)
        fun summary(filter: TaxonFilter, group: String?) =
            CachedSearchSummary(region = region, month = 10, filter = filter, cachedAtEpochMillis = 0L, speciesIconicTaxonName = group)

        assertEquals(ForagingSelection(flyAgaric, "Fungi"), ForagingSelection.fromRecentSearch(summary(flyAgaric, "Fungi")))
        assertEquals("saved before version 19", ForagingSelection(flyAgaric, null), ForagingSelection.fromRecentSearch(summary(flyAgaric, null)))
        assertEquals(ForagingSelection(TaxonFilter.LICHENS, null), ForagingSelection.fromRecentSearch(summary(TaxonFilter.LICHENS, null)))
        assertEquals(ForagingSelection.fromCategory(TaxonFilter.PLANTS), ForagingSelection.fromRecentSearch(summary(TaxonFilter.PLANTS, null)))
    }
}
