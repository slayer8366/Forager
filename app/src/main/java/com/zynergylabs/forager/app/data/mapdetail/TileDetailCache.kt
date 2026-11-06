package com.zynergylabs.forager.app.data.mapdetail

import com.zynergylabs.forager.app.domain.mapdata.TileDetail
import com.zynergylabs.forager.app.domain.mapdata.TileId

/**
 * This session's tile answers for the picker's detail check (call C3, RECORD -2150): at most [maxAnswers], the least recently
 * read dropped first. Only definite answers are kept; an [TileDetail.Unread] one is asked again next time. Safe from every
 * request's thread.
 */
internal class TileDetailCache(private val maxAnswers: Int) {
    private val answers = object : LinkedHashMap<TileId, TileDetail>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<TileId, TileDetail>?): Boolean = size > maxAnswers
    }

    @Synchronized
    fun get(tile: TileId): TileDetail? = answers[tile]

    @Synchronized
    fun put(tile: TileId, detail: TileDetail) {
        if (detail !is TileDetail.Unread) answers[tile] = detail
    }

    val size: Int
        @Synchronized get() = answers.size
}
