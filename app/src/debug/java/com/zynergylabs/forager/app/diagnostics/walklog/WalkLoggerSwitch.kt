package com.zynergylabs.forager.app.diagnostics.walklog

/**
 * The Diagnostics screen's "Walk logger" switch (dispatch 2026-09-28-532, Amendment 3, RECORD -560):
 * off by default, remembered across restarts, and the logger runs only while it is on. Kept in the
 * debug build's diagnostics DataStore file by [DataStoreWalkLoggerSwitch], its own store since dispatch
 * 2026-10-29-05, Amendment 1.
 */
interface WalkLoggerSwitch {
    suspend fun isWalkLoggerEnabled(): Result<Boolean>

    suspend fun setWalkLoggerEnabled(enabled: Boolean): Result<Unit>
}
