package com.zynergylabs.forager.app.data.repository

import com.zynergylabs.forager.app.domain.CameraGridModeRepository
import com.zynergylabs.forager.app.domain.GridMode

/**
 * The [CameraGridModeRepository] the camera's tests use: holds one mode, counts writes, and can be
 * made to fail a read or a write, so a test can see that the chip shows what was stored rather than
 * what was asked for.
 */
internal class FakeCameraGridModeRepository(initial: GridMode = GridMode.Off) : CameraGridModeRepository {
    var stored: GridMode = initial
        private set
    var failReads = false
    var failWrites = false
    var writes = 0
        private set

    override suspend fun getGridMode(): Result<GridMode> =
        if (failReads) Result.failure(IllegalStateException("the grid mode could not be read")) else Result.success(stored)

    override suspend fun setGridMode(mode: GridMode): Result<Unit> {
        writes += 1
        if (failWrites) return Result.failure(IllegalStateException("the grid mode could not be written"))
        stored = mode
        return Result.success(Unit)
    }
}
