package com.forager.app.data.local.fungiindex

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface FungiIndexDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTaxon(taxon: FungiTaxonEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNames(names: List<FungiTaxonNameEntity>)

    /**
     * Every name row matching [normalizedQuery] as a substring, ordered exact-match first, then
     * prefix match, then substring match — [rawLimit] applies to this raw, not-yet-deduplicated
     * row count, since one taxon can supply several matching names (e.g. `lion` matches both
     * `Hericium erinaceus`'s `"lion's-mane mushroom"` and `"lion's mane mushroom"` rows). The
     * caller (`LocalFungiIndexRepository`) collapses to one row per taxon, keeping the
     * best-ranked match — first-seen wins, since this query already orders best-first.
     *
     * No FTS: 11,257 taxa is small enough that a `LIKE '%...%'` scan is fast, and FTS would make
     * the tiered ordering below harder to express, not easier (owner decision, see the dispatch
     * this was built from).
     */
    @Query(
        """
        SELECT n.taxonId AS taxonId, n.name AS matchedName, n.isScientific AS isScientific,
               t.scientificName AS scientificName, t.observationCount AS observationCount
        FROM fungi_taxon_names n
        JOIN fungi_taxa t ON t.taxonId = n.taxonId
        WHERE n.nameNormalized LIKE '%' || :normalizedQuery || '%'
        ORDER BY
            CASE
                WHEN n.nameNormalized = :normalizedQuery THEN 0
                WHEN n.nameNormalized LIKE :normalizedQuery || '%' THEN 1
                ELSE 2
            END ASC,
            t.observationCount DESC
        LIMIT :rawLimit
        """,
    )
    suspend fun searchByNormalizedName(normalizedQuery: String, rawLimit: Int): List<FungiNameMatchRow>
}

/** One matching name row, joined back to its taxon's display fields. See [FungiIndexDao.searchByNormalizedName]. */
data class FungiNameMatchRow(
    val taxonId: Long,
    val matchedName: String,
    val isScientific: Boolean,
    val scientificName: String,
    val observationCount: Int,
)
