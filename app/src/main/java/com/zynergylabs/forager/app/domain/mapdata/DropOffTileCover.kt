package com.zynergylabs.forager.app.domain.mapdata

import com.zynergylabs.forager.app.domain.model.GeoBoundingBox

/** STUB (dispatch 2026-11-04-09, tests first): an area's zoom-15 tiles. Built in the next commit. */
object DropOffTileCover {
    const val ZOOM = 15

    fun of(box: GeoBoundingBox): Cover? = Cover(IntRange.EMPTY, IntRange.EMPTY)

    /** The tiles x in [xs] by y in [ys], all at [ZOOM]. */
    data class Cover(val xs: IntRange, val ys: IntRange) {
        val size: Long get() = xs.count().toLong() * ys.count().toLong()

        fun tiles(): List<TileId> = xs.flatMap { x -> ys.map { y -> TileId(ZOOM, x, y) } }
    }
}
