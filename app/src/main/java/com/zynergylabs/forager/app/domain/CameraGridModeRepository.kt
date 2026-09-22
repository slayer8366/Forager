package com.zynergylabs.forager.app.domain

/**
 * The camera's grid mode: what the strip's grid chip shows and cycles (decision record B7,
 * `docs/audits/2026-09-21-camera-strip-basics-decisions.md`).
 *
 * - [Off]: nothing over the preview.
 * - [Grid]: a 3 by 3 grid over the preview.
 * - [GridLevel]: the grid and a horizon line that tilts with the phone.
 */
enum class GridMode { Off, Grid, GridLevel }

/**
 * Where the grid mode persists (B7a: across camera sessions and app restarts, unlike torch).
 * DataStore rather than Room, per CLAUDE.md: a flat value nothing will join against.
 *
 * **Defaults to [GridMode.Off]**, so an install that predates the grid sees the preview it had. A
 * stored value this build does not recognise is a failed read, not a silent Off.
 */
interface CameraGridModeRepository {

    /** [GridMode.Off] until the user has ever chosen another. */
    suspend fun getGridMode(): Result<GridMode>

    suspend fun setGridMode(mode: GridMode): Result<Unit>
}
