package com.forager.app.domain

import com.forager.app.domain.model.ConditionsSummary
import com.forager.app.domain.model.Region

/**
 * Owned abstraction over recent-precipitation data. Domain and UI code depend on this
 * interface, never on Retrofit or the Open-Meteo DTOs directly (CLAUDE.md: wrap external
 * integrations behind an interface this project owns), same pattern as [MushroomRepository].
 */
interface WeatherProvider {
    /** Recent observed precipitation for [region], covering a fixed lookback window ending today. */
    suspend fun getRecentPrecipitation(region: Region): Result<ConditionsSummary>
}
