package com.zynergylabs.forager.app.forecast

import android.content.Context
import android.util.Log
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStoreFile
import com.zynergylabs.forager.app.data.repository.runCatchingCancellable
import com.zynergylabs.forager.app.diagnostics.walklog.WalkLoggerSwitch
import com.zynergylabs.forager.app.domain.ForecastAvailability
import com.zynergylabs.forager.app.domain.ForecastBlock
import com.zynergylabs.forager.app.domain.ForecastCell
import com.zynergylabs.forager.app.domain.ForecastCellStore
import com.zynergylabs.forager.app.domain.ForecastCellsResult
import com.zynergylabs.forager.app.domain.parseForecastCells
import com.zynergylabs.forager.app.ui.map.layers.COLOUR_FIELDS
import java.time.LocalDate
import kotlin.math.cos
import kotlin.math.roundToLong
import kotlin.math.sin
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Synthetic forecast cells for the debug build (map layers L0b, B6, as planner message 2 changed it):
 * block files in the **real** D55 format, one per group per week per 1-degree block, so the map reads
 * them through the same parser a downloaded store will use. Debug builds only (`src/debug`); the release
 * build has no synthetic code at all, only a store that reports "no forecast data".
 *
 * **Deterministic**: a file is a pure function of its group, week and block. Every block has cells, so
 * data exists wherever the phone is. Of each block's 100 cells, about one in ten is **left out** of the
 * file and about one in ten carries `applicable` false, the two ways D55 allows an unscored cell, and both
 * draw nothing, so "no forecast here" can be seen. Every other property is filled: `chance` from a smooth
 * pattern per group (so the two layers look different) with a little per-cell variation, uncertainty
 * bounds either side of it, two drivers, `weather_through` two days before the week starts, and a
 * `model_version` of [MODEL_VERSION]. Cells are centred on multiples of 0.1 degree with edges at x.x5,
 * and each is in the block holding its centre.
 *
 * None of these numbers is a forecast, and the layers that draw them are named as test data (owner:
 * "Real format, 'test data' name").
 */
object SyntheticForecastGenerator {
    const val MODEL_VERSION = "synthetic-1"

    fun blockGeoJson(group: String, week: LocalDate, block: ForecastBlock): String {
        val phase = (group.hashCode() and 0xFFFF) / 65535.0 * 6.0
        val features = buildJsonArray {
            for (i in 0 until 10) {
                for (j in 0 until 10) {
                    val latTenths = block.south * 10L + i
                    val lngTenths = block.west * 10L + j
                    val hash = mix(group.hashCode().toLong(), week.toEpochDay(), latTenths, lngTenths)
                    val roll = (hash ushr 1) % 10
                    if (roll == 0L) continue // left out of the file: an unscored cell may be omitted
                    add(cellFeature(group, week, latTenths, lngTenths, hash, phase, applicable = roll != 1L))
                }
            }
        }
        return buildJsonObject {
            put("type", "FeatureCollection")
            put("features", features)
        }.toString()
    }

    private fun cellFeature(
        group: String,
        week: LocalDate,
        latTenths: Long,
        lngTenths: Long,
        hash: Long,
        phase: Double,
        applicable: Boolean,
    ) = buildJsonObject {
        put("type", "Feature")
        put(
            "geometry",
            buildJsonObject {
                put("type", "Polygon")
                // Edges at x.x5, as hundredths so they are exact decimals.
                val south = (latTenths * 10 - 5) / 100.0
                val north = (latTenths * 10 + 5) / 100.0
                val west = (lngTenths * 10 - 5) / 100.0
                val east = (lngTenths * 10 + 5) / 100.0
                put(
                    "coordinates",
                    JsonArray(
                        listOf(
                            JsonArray(
                                listOf(west to south, east to south, east to north, west to north, west to south)
                                    .map { (x, y) -> JsonArray(listOf(JsonPrimitive(x), JsonPrimitive(y))) },
                            ),
                        ),
                    ),
                )
            },
        )
        val lat = latTenths / 10.0
        val lng = lngTenths / 10.0
        val jitter = unit(hash, 1) - 0.5
        // At most 0.35 + 0.075 from 0.5, so always inside 0 to 1 without clamping.
        val chance = round3(0.5 + 0.35 * sin(lat * 0.9 + phase) * cos(lng * 0.7 + phase * 0.5) + 0.15 * jitter)
        put(
            "properties",
            buildJsonObject {
                put("group", group)
                put("week", week.toString())
                put("chance", chance)
                put("uncertainty_low", round3(chance * 0.7))
                put("uncertainty_high", round3(chance + (1 - chance) * 0.3))
                put("applicable", applicable)
                put(
                    "drivers",
                    buildJsonArray {
                        add(buildJsonObject { put("label", "Rain, last 14 days (synthetic)"); put("value", (unit(hash, 2) * 60).roundToLong()) })
                        add(buildJsonObject { put("label", "Soil temperature (synthetic)"); put("value", "${8 + (unit(hash, 3) * 8).roundToLong()} C") })
                    },
                )
                put("weather_through", week.minusDays(2).toString())
                put("model_version", MODEL_VERSION)
            },
        )
    }

    /** A well-mixed 64-bit value from the inputs (SplitMix64's finaliser), so nearby cells differ. */
    private fun mix(vararg parts: Long): Long {
        var h = 0x9E3779B97F4A7C15uL.toLong()
        for (part in parts) {
            var z = h xor part
            z = (z xor (z ushr 30)) * -0x40a7b892e31b1a47L
            z = (z xor (z ushr 27)) * -0x6b2fb644ecceee15L
            h = z xor (z ushr 31)
        }
        return h
    }

    /** A value in [0, 1) from [hash], different for each [salt]. */
    private fun unit(hash: Long, salt: Long): Double = (mix(hash, salt) ushr 11).toDouble() / (1L shl 53).toDouble()

    private fun round3(value: Double): Double = (value * 1000).roundToLong() / 1000.0
}

/** The Diagnostics screen's "Synthetic forecast layers" switch, as the panel reads and writes it. */
interface SyntheticForecastSwitch {
    suspend fun isEnabled(): Result<Boolean>

    suspend fun setEnabled(enabled: Boolean): Result<Unit>
}

/**
 * The debug build's forecast-cell store: synthetic cells from [SyntheticForecastGenerator], for the two
 * colour fields' groups ([COLOUR_FIELDS]), while the Diagnostics switch is on; "no forecast data" while
 * it is off, which is its default. It is also that switch ([SyntheticForecastSwitch]).
 *
 * The switch lives in its own debug-only DataStore file, [DATA_STORE_NAME], under [KEY_ENABLED] (the
 * dispatch, B6). One instance per process (`AppContainer` makes one) for the reason
 * `DataStoreMapPreferencesRepository` gives: DataStore refuses a second live instance on a file. [scope]
 * is DataStore's default unless a test passes one to release the file.
 *
 * Each requested block's file is generated and then **parsed** with [parseForecastCells], so what the map
 * draws has passed the real validation; any rejected feature is counted in the result, not dropped
 * quietly. A switch that cannot be read is logged and treated as off.
 *
 * It also holds the walk logger's switch ([WalkLoggerSwitch], dispatch 2026-09-28-532, Amendment 3),
 * under its own key in the same file, for the one-instance reason above.
 */
class SyntheticForecastCellStore(
    context: Context,
    scope: CoroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob()),
) : ForecastCellStore, SyntheticForecastSwitch, WalkLoggerSwitch {

    private val dataStore = PreferenceDataStoreFactory.create(
        scope = scope,
        produceFile = { context.applicationContext.preferencesDataStoreFile(DATA_STORE_NAME) },
    )

    private val groups: Set<String> = COLOUR_FIELDS.map { it.group }.toSet()

    override suspend fun isEnabled(): Result<Boolean> = runCatchingCancellable { dataStore.data.first()[KEY_ENABLED] ?: false }

    override suspend fun setEnabled(enabled: Boolean): Result<Unit> = runCatchingCancellable {
        dataStore.edit { prefs -> prefs[KEY_ENABLED] = enabled }
    }

    // The walk logger's switch (dispatch 2026-09-28-532, Amendment 3): here because this store owns
    // the one debug diagnostics DataStore file, and DataStore refuses a second live instance on it.
    // Its own key; independent of the forecast switch. Off by default.
    override suspend fun isWalkLoggerEnabled(): Result<Boolean> = runCatchingCancellable { dataStore.data.first()[KEY_WALK_LOGGER] ?: false }

    override suspend fun setWalkLoggerEnabled(enabled: Boolean): Result<Unit> = runCatchingCancellable {
        dataStore.edit { prefs -> prefs[KEY_WALK_LOGGER] = enabled }
    }

    override suspend fun availability(week: LocalDate): ForecastAvailability =
        if (enabled()) ForecastAvailability.Groups(groups) else ForecastAvailability.NoForecastData

    override suspend fun cells(group: String, week: LocalDate, blocks: Set<ForecastBlock>): ForecastCellsResult {
        if (!enabled() || group !in groups) return ForecastCellsResult.NoForecastData
        return withContext(Dispatchers.Default) {
            val cells = mutableListOf<ForecastCell>()
            var rejected = 0
            blocks.forEach { block ->
                parseForecastCells(SyntheticForecastGenerator.blockGeoJson(group, week, block)).fold(
                    onSuccess = { parsed ->
                        cells += parsed.cells
                        rejected += parsed.rejected.size
                        if (parsed.rejected.isNotEmpty()) {
                            Log.w(TAG, "Synthetic block $block for $group: ${parsed.rejected.size} feature(s) rejected, first: ${parsed.rejected.first()}")
                        }
                    },
                    onFailure = { error ->
                        rejected += 1
                        Log.w(TAG, "Synthetic block $block for $group did not parse as a whole.", error)
                    },
                )
            }
            ForecastCellsResult.Cells(cells, rejected)
        }
    }

    private suspend fun enabled(): Boolean = isEnabled().getOrElse { error ->
        Log.w(TAG, "Couldn't read the synthetic forecast switch; treating it as off.", error)
        false
    }

    private companion object {
        const val TAG = "SyntheticForecast"
        const val DATA_STORE_NAME = "debug_diagnostics_preferences"
        val KEY_ENABLED = booleanPreferencesKey("diagnostics.synthetic_forecast")
        val KEY_WALK_LOGGER = booleanPreferencesKey("diagnostics.walk_logger")
    }
}

/**
 * The debug build's store, made once by `AppContainer`: the synthetic store. The release twin in
 * `src/release` returns the store that reports "no forecast data".
 */
fun forecastCellStore(context: Context): ForecastCellStore = SyntheticForecastCellStore(context)
