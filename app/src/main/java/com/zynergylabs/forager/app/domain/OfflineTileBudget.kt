package com.zynergylabs.forager.app.domain

/**
 * What an area costs against the tile budget ([OfflineMapRepository.TILE_COUNT_LIMIT]): its own estimate, recomputed
 * from its stored centre and radius with the one function the picker's "~N tiles" uses ([estimateServedOfflineTileCount]),
 * so the used side of the budget is counted the way the estimate side always was (dispatch 2026-10-26-21 Amendment 1,
 * T1; the owner: "Yes, the estimate (Recommended)", RECORD -1703).
 *
 * Why not MapLibre's own count ([OfflineRegionSummary.tileCount]): since Trail Map v3 an area stores the official-trails
 * band's tiles beside the basemap's, one band tile (mostly empty) per basemap tile, and MapLibre counts every stored tile
 * of every source with no per-source figure (`OfflineRegionStatus` in MapLibre 13.5.0 has none). Counted that way one
 * 24 km area would read about double its estimate and fill the budget, and the next download would be refused, against
 * the owner's "Don't count, keep 24 km" (RECORD -1678). Rejected alternatives, priced in the Phase 1 report
 * (`prompts/preserved/2026-10-26-21-report.md` section 2): halving the stored count (exact only while both sources cover
 * the same tiles), storing the estimate at download time (the same figure, plus storage), and reading MapLibre's private
 * database for a per-source count.
 *
 * An area restored from a backup and not downloaded on this phone holds no tiles and costs nothing, as before.
 */
fun OfflineRegionSummary.budgetTileCount(): Int = tileCount // STUB (fail-first): MapLibre's count, as before T1

/** The budget's used side: every area's [budgetTileCount], a pending delete's included (its tiles are still on disk). */
fun Collection<OfflineRegionSummary>.budgetTilesUsed(): Long = sumOf { it.budgetTileCount().toLong() }
