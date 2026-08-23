package com.forager.app.domain

import java.util.concurrent.TimeUnit
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OfflineRegionStalenessTest {

    private val createdAt = 1_755_000_000_000L

    @Test
    fun `just downloaded is not stale`() {
        assertFalse(isOfflineRegionStale(createdAt, nowEpochMillis = createdAt, thresholdDays = 60))
    }

    @Test
    fun `one day short of the threshold is not stale`() {
        val now = createdAt + TimeUnit.DAYS.toMillis(59)
        assertFalse(isOfflineRegionStale(createdAt, now, thresholdDays = 60))
    }

    @Test
    fun `exactly at the threshold is stale`() {
        val now = createdAt + TimeUnit.DAYS.toMillis(60)
        assertTrue(isOfflineRegionStale(createdAt, now, thresholdDays = 60))
    }

    @Test
    fun `well past the threshold is stale`() {
        val now = createdAt + TimeUnit.DAYS.toMillis(120)
        assertTrue(isOfflineRegionStale(createdAt, now, thresholdDays = 60))
    }

    @Test
    fun `a configured threshold of zero treats any age as stale`() {
        assertTrue(isOfflineRegionStale(createdAt, nowEpochMillis = createdAt, thresholdDays = 0))
    }
}
