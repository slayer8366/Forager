package com.zynergylabs.forager.app.forecast

import android.content.Context
import com.zynergylabs.forager.app.domain.AbsentForecastCellStore
import com.zynergylabs.forager.app.domain.ForecastCellStore

/**
 * The release build's forecast-cell store (map layers L0b, B6; owner: "Debug-only source
 * (Recommended)"): the store that reports "no forecast data" for every group and week, and nothing
 * else. The debug build's twin, in `src/debug`, is the synthetic store behind the Diagnostics switch.
 * A build-type source set, not a `BuildConfig.DEBUG` branch, for the reason the Diagnostics panel's
 * own doc comment gives: with minification off, a branch would ship the synthetic classes in the
 * release APK, unreachable rather than absent.
 */
@Suppress("UNUSED_PARAMETER")
fun forecastCellStore(context: Context): ForecastCellStore = AbsentForecastCellStore
