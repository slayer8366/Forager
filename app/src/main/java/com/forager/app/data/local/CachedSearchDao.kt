package com.forager.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction

/**
 * Room access to the cached-search table.
 *
 * An abstract class rather than an interface because the two multi-statement operations below are
 * [Transaction]s with a body: read-then-write and write-then-evict both have to be atomic, and
 * `@Transaction` is what makes them so. Doing the same sequence from the repository with separate
 * DAO calls is several statements that another coroutine's `save` can interleave with — the count
 * read back would be one row stale and the wrong row (or the wrong number of rows) would be
 * evicted. The individual queries stay separate and single-purpose; only their composition lives
 * here.
 */
@Dao
abstract class CachedSearchDao {

    @Query("SELECT * FROM cached_searches WHERE `key` = :key")
    abstract suspend fun getByKey(key: String): CachedSearchEntity?

    /** Replace-on-conflict: a re-run search overwrites its own previous row rather than duplicating it. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun upsert(entity: CachedSearchEntity)

    /** Most recently used first — the order the picker shows and the order eviction reads from the far end of. */
    @Query("SELECT * FROM cached_searches ORDER BY lastAccessedAtEpochMillis DESC")
    abstract suspend fun getAllOrderedByLastAccessed(): List<CachedSearchEntity>

    @Query("DELETE FROM cached_searches WHERE `key` = :key")
    abstract suspend fun deleteByKey(key: String)

    /**
     * Stores [entity], then drops every row beyond the [maxEntries] most recently used.
     *
     * "Every row beyond", not "the single oldest": if the table is somehow over the limit by more
     * than one — an interrupted earlier eviction, a limit lowered in a later build — deleting one
     * row would leave it over the limit indefinitely.
     */
    @Transaction
    open suspend fun upsertAndEvictBeyond(entity: CachedSearchEntity, maxEntries: Int) {
        upsert(entity)
        getAllOrderedByLastAccessed()
            .drop(maxEntries)
            .forEach { stale -> deleteByKey(stale.key) }
    }

    /**
     * Returns the row for [key] and marks it used at [accessedAtEpochMillis], so reading a cached
     * search counts towards keeping it — which is what makes this an LRU rather than a
     * least-recently-*written* cache. Returns the row as it now stands, bumped stamp included.
     */
    @Transaction
    open suspend fun getByKeyAndTouch(key: String, accessedAtEpochMillis: Long): CachedSearchEntity? {
        val stored = getByKey(key) ?: return null
        val touched = stored.copy(lastAccessedAtEpochMillis = accessedAtEpochMillis)
        upsert(touched)
        return touched
    }
}
