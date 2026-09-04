package com.forager.app.data.local.fungiindex

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * One searchable name for one taxon — a taxon's scientific name plus every one of its common
 * names (all locales, see `data/species-index/README.md`) each get their own row, since a list
 * column can't be searched or indexed directly.
 *
 * [nameNormalized] is computed once at index-build time by
 * [com.forager.app.domain.normalizeSearchName] (see that function's doc comment for exactly
 * what normalization means) and stored, rather than normalized per query, so matching is a plain
 * indexed-column comparison against a query normalized the same way.
 */
@Entity(
    tableName = "fungi_taxon_names",
    foreignKeys = [
        ForeignKey(
            entity = FungiTaxonEntity::class,
            parentColumns = ["taxonId"],
            childColumns = ["taxonId"],
        ),
    ],
    indices = [Index("nameNormalized"), Index("taxonId")],
)
data class FungiTaxonNameEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(index = false) val taxonId: Long,
    /** The name as iNaturalist carries it, for display. */
    val name: String,
    val isScientific: Boolean,
    val nameNormalized: String,
)
