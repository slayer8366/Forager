package com.forager.app.domain

import com.forager.app.domain.model.TaxonFilter
import com.forager.app.domain.model.TaxonSearchResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ForagingWeatherGuidanceTest {

    private fun guidanceFor(selection: ForagingSelection) =
        ForagingWeatherGuidance.forSelection(selection)

    private fun textOf(selection: ForagingSelection) =
        guidanceFor(selection).paragraphs.joinToString(" ")

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

    // ---- category guidance varies by category ----------------------------------------------

    @Test
    fun `fungi guidance states the rain lag pattern and hedges it as a rule of thumb`() {
        val guidance = guidanceFor(ForagingSelection.fromCategory(TaxonFilter.FUNGI))
        val text = guidance.paragraphs.joinToString(" ")

        assertTrue(text.contains("one to three weeks after sustained rain"))
        // Traceability: the claim is attributed and explicitly not presented as measured here.
        assertTrue(text.contains("not a figure Forager has measured"))
        assertNull(guidance.speciesDataCaveat)
    }

    @Test
    fun `fungi guidance quotes the soil temperature band from the labelled assumption`() {
        val text = textOf(ForagingSelection.fromCategory(TaxonFilter.FUNGI))

        assertTrue(
            "guidance should quote ${FruitingPatternAssumptions.TEMPERATE_FRUITING_SOIL_TEMPERATURE_C}",
            text.contains("10–20 °C"),
        )
        assertEquals(10.0, FruitingPatternAssumptions.TEMPERATE_FRUITING_SOIL_TEMPERATURE_C.start, 0.0)
        assertEquals(20.0, FruitingPatternAssumptions.TEMPERATE_FRUITING_SOIL_TEMPERATURE_C.endInclusive, 0.0)
    }

    @Test
    fun `plants guidance is not the fungi text`() {
        val fungi = textOf(ForagingSelection.fromCategory(TaxonFilter.FUNGI))
        val plants = textOf(ForagingSelection.fromCategory(TaxonFilter.PLANTS))

        assertFalse(plants == fungi)
        assertFalse(
            "plants must not inherit the fungal rain-lag claim",
            plants.contains("one to three weeks after sustained rain"),
        )
    }

    @Test
    fun `plants guidance says plainly that there is no weather pattern to offer`() {
        val guidance = guidanceFor(ForagingSelection.fromCategory(TaxonFilter.PLANTS))

        assertEquals("Rain and plants: no pattern to offer", guidance.heading)
        assertTrue(guidance.paragraphs.first().contains("no weather-based pattern for plants"))
    }

    @Test
    fun `a category with no written guidance says so rather than borrowing another's`() {
        val insects = TaxonFilter.IconicCategory(iconicTaxonName = "Insecta", label = "Insects")

        val guidance = guidanceFor(ForagingSelection.fromCategory(insects))
        val text = guidance.paragraphs.joinToString(" ")

        assertEquals("No weather guidance for this selection", guidance.heading)
        assertFalse(text.contains("one to three weeks after sustained rain"))
        assertTrue(text.contains("nothing sourced for this selection"))
    }

    // ---- a specific taxon always gets the caveat -------------------------------------------

    @Test
    fun `a specific species falls back to its category's guidance`() {
        val chanterelles = ForagingSelection(
            filter = TaxonFilter.SpecificTaxon(taxonId = 47348, label = "chanterelles"),
            iconicTaxonName = "Fungi",
        )

        val guidance = guidanceFor(chanterelles)

        assertEquals(
            guidanceFor(ForagingSelection.fromCategory(TaxonFilter.FUNGI)).paragraphs,
            guidance.paragraphs,
        )
    }

    @Test
    fun `a specific species states explicitly that no species-specific data exists`() {
        val chanterelles = ForagingSelection(
            filter = TaxonFilter.SpecificTaxon(taxonId = 47348, label = "chanterelles"),
            iconicTaxonName = "Fungi",
        )

        val caveat = guidanceFor(chanterelles).speciesDataCaveat

        assertNotNull("a specific taxon must always carry the no-species-data statement", caveat)
        assertTrue(caveat!!.contains("No species-specific data is available for chanterelles"))
        assertTrue(caveat.contains("not a claim about chanterelles"))
        assertTrue(caveat.contains("general pattern for fungi in general"))
    }

    @Test
    fun `the caveat is present even when the species has no category guidance to fall back on`() {
        val unknownGroup = ForagingSelection(
            filter = TaxonFilter.SpecificTaxon(taxonId = 99999, label = "Some Beetle"),
            iconicTaxonName = null,
        )

        val guidance = guidanceFor(unknownGroup)

        assertEquals("No weather guidance for this selection", guidance.heading)
        assertNotNull(guidance.speciesDataCaveat)
        assertTrue(guidance.speciesDataCaveat!!.contains("No species-specific data is available for Some Beetle"))
    }

    @Test
    fun `the lichens chip is not given the fungi fruiting pattern`() {
        // iNaturalist files lichens under Fungi, but the stated pattern is about fruiting bodies.
        // Reusing it here would present it as a claim about organisms it was not written about.
        val guidance = guidanceFor(ForagingSelection.forChip(TaxonFilter.LICHENS))
        val text = guidance.paragraphs.joinToString(" ")

        assertFalse(text.contains("one to three weeks after sustained rain"))
        assertNotNull(guidance.speciesDataCaveat)
        assertTrue(guidance.speciesDataCaveat!!.contains("Lichens (approx.)"))
    }

    @Test
    fun `every default selection produces guidance, and only a specific-taxon selection carries a caveat`() {
        val caveats = listOf(TaxonFilter.FUNGI, TaxonFilter.PLANTS, TaxonFilter.LICHENS).associate { filter ->
            filter.label to guidanceFor(ForagingSelection.forChip(filter)).speciesDataCaveat
        }

        assertEquals(
            mapOf("Fungi" to false, "Plants" to false, "Lichens (approx.)" to true),
            caveats.mapValues { it.value != null },
        )
    }

    @Test
    fun `no guidance text states a score, probability or best day`() {
        val everySelection = listOf(
            ForagingSelection.fromCategory(TaxonFilter.FUNGI),
            ForagingSelection.fromCategory(TaxonFilter.PLANTS),
            ForagingSelection.forChip(TaxonFilter.LICHENS),
            ForagingSelection(TaxonFilter.SpecificTaxon(1, "Amanita phalloides"), "Fungi"),
        )
        val forbidden = listOf("%", "best day", "chance of", "score", "star", "out of 5", "likely to find")

        val offenders = everySelection.flatMap { selection ->
            val guidance = guidanceFor(selection)
            val text = (guidance.paragraphs + listOfNotNull(guidance.speciesDataCaveat)).joinToString(" ")
            forbidden.filter { text.lowercase().contains(it) }.map { "${selection.filter.label}: $it" }
        }

        assertEquals(emptyList<String>(), offenders)
    }
}
