package com.zynergylabs.forager.app.data.repository

import com.zynergylabs.forager.app.domain.CameraFormat
import com.zynergylabs.forager.app.domain.CameraFormatRepository

/** An in-memory [CameraFormatRepository], shaped like [FakeCameraGridModeRepository]: reads and writes can each be made to fail. */
internal class FakeCameraFormatRepository(initial: CameraFormat = CameraFormat.Jpg) : CameraFormatRepository {
    var stored: CameraFormat = initial
        private set
    var failReads = false
    var failWrites = false
    var writes = 0
        private set

    override suspend fun getCameraFormat(): Result<CameraFormat> =
        if (failReads) Result.failure(IllegalStateException("the camera format could not be read")) else Result.success(stored)

    override suspend fun setCameraFormat(format: CameraFormat): Result<Unit> {
        writes += 1
        if (failWrites) return Result.failure(IllegalStateException("the camera format could not be written"))
        stored = format
        return Result.success(Unit)
    }
}
