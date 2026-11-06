package com.zynergylabs.forager.app.domain

import com.zynergylabs.forager.app.domain.model.TaxonFilter
import com.zynergylabs.forager.app.domain.model.TaxonSearchResult

/**
 * What the user currently has selected, together with the broad iNaturalist group it belongs to
 * when that is known.
 *
 * This exists because [TaxonFilter.SpecificTaxon] carries a taxon id and a label and nothing else,
 * so a filter alone cannot answer "which category's guidance applies here". RECORD -727 (the owner:
 * "remove the block of text here labeled Rain and Fungi: the general pattern", then "Remove both" for
 * the plants block) removed that guidance, ForagingWeatherGuidance's Guidance and forSelection with
 * it; the group is kept because recent searches still store and restore it. [TaxonSearchResult]
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
         * A search reopened from the recent-searches list: a category carries its own group, and a
         * species the group stored with the search (dispatch 2026-09-28-695, amendment 1; the owner,
         * "Save it with the search"), so Fly Agaric reopened there shows the fungi pattern as it did
         * when it was searched by name. A species saved before that was stored, or the Lichens chip,
         * has none, and shows no guidance.
         */
        fun fromRecentSearch(summary: CachedSearchSummary): ForagingSelection = when (val filter = summary.filter) {
            is TaxonFilter.IconicCategory -> fromCategory(filter)
            is TaxonFilter.SpecificTaxon -> ForagingSelection(filter, summary.speciesIconicTaxonName)
        }

        /**
         * The selection behind an [TaxonFilter.IconicCategory] or [TaxonFilter.SpecificTaxon]
         * chosen some way other than a name search or a recent search: today only the screen's
         * starting selection, the Fungi category. A species given here has no group to carry, so it
         * shows no guidance; a recent search goes through [fromRecentSearch] instead, which carries
         * the group stored with it.
         *
         * A lichens selection is deliberately given a null group rather than "Fungi". iNaturalist
         * does file lichens under Fungi, but the general pattern this app states — fleshy fungi
         * fruiting some weeks after sustained rain — is about fruiting bodies, and reusing it for
         * lichenized fungi would present it as a claim about organisms it was not written about.
         * Forager has no sourced guidance for lichens. (The weather guidance text this group was
         * read for was removed by RECORD -727; the group is still stored with recent searches.)
         */
        fun forChip(filter: TaxonFilter): ForagingSelection = when (filter) {
            is TaxonFilter.IconicCategory -> fromCategory(filter)
            is TaxonFilter.SpecificTaxon -> ForagingSelection(filter, iconicTaxonName = null)
        }
    }
}
