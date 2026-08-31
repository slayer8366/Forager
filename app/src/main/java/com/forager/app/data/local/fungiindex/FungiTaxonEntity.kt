package com.forager.app.data.local.fungiindex

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * One row per fungi taxon in the bundled search index (`data/species-index/` at the repo root;
 * see `data/species-index/README.md` for provenance and scope). Lives in [FungiIndexDatabase],
 * never [com.forager.app.data.local.ForagerDatabase] — see that database's own doc comment
 * boundary and [FungiIndexDatabase]'s for why a prepackaged, read-only index is kept apart from
 * user data.
 *
 * [scientificName] and [observationCount] are stored here rather than only on
 * [FungiTaxonNameEntity] rows so a search hit can be turned into a display result without a
 * second query. The taxon's scientific name is *also* inserted as a [FungiTaxonNameEntity] row
 * (`isScientific = true`) so it participates in name matching the same way a common name does.
 */
@Entity(tableName = "fungi_taxa")
data class FungiTaxonEntity(
    @PrimaryKey val taxonId: Long,
    val scientificName: String,
    /** US, research-grade observation count — the ranking signal within a match tier. */
    val observationCount: Int,
)
