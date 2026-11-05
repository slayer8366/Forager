package com.zynergylabs.forager.app.domain.mapdata

/** STUB (dispatch 2026-11-04-09, tests first): basemap patch 0004's fence mark, defined once. Built in the next commit. */
object BarrierMark {
    fun carriedBy(tile: MvtTile): Boolean = false

    fun of(mvt: ByteArray): TileMark = TileMark.Undecodable("not built yet")
}

/** What a tile's bytes say about the mark. */
sealed interface TileMark {
    data object Marked : TileMark

    data object Unmarked : TileMark

    data class Undecodable(val reason: String) : TileMark
}
