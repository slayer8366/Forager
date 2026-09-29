package com.zynergylabs.forager.app.data.repository

import com.zynergylabs.forager.app.data.local.ForagerDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * After a "Download again" finished under a new MapLibre id: points every reference to the old id at the new one and
 * removes the old row, in one transaction (owner, "1 B"; the same rewrite Merge does for an incoming region, dispatch
 * 2026-09-28-137 item 2). The references are the finds' `offlineRegionId` and the entries' offline-region ref rows; an
 * entry that already names the new region keeps its one reference. The old row goes only here, on the person's own
 * "Download again", and by their own delete; nothing else removes a region row (dispatch -106).
 */
class RoomOfflineRegionIdReplacer(private val database: ForagerDatabase) {

    suspend fun replace(oldId: Long, newId: Long): Result<Unit> = runCatchingCancellable {
        if (oldId == newId) return@runCatchingCancellable
        withContext(Dispatchers.IO) {
            val sql = database.openHelper.writableDatabase
            database.runInTransaction(Runnable {
                sql.execSQL("UPDATE mushroom_log_entries SET offlineRegionId = ? WHERE offlineRegionId = ?", arrayOf<Any?>(newId, oldId))
                sql.execSQL("UPDATE OR IGNORE cartography_entry_offline_region_refs SET offlineRegionId = ? WHERE offlineRegionId = ?", arrayOf<Any?>(newId, oldId))
                sql.execSQL("DELETE FROM cartography_entry_offline_region_refs WHERE offlineRegionId = ?", arrayOf<Any?>(oldId))
                sql.execSQL("DELETE FROM offline_regions WHERE id = ?", arrayOf<Any?>(oldId))
            })
        }
    }
}
