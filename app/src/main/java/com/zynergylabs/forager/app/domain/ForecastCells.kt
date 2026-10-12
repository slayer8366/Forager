package com.zynergylabs.forager.app.domain

import com.zynergylabs.forager.app.domain.model.LatLng
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeParseException
import java.time.temporal.TemporalAdjusters
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.roundToLong
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull

/**
 * A 1-degree block, named by its south-west corner in whole degrees: it holds every cell whose centre
 * lies in `[south, south + 1)` by `[west, west + 1)`. The forecast project publishes one file per group
 * per week per block (D55); a cell belongs to the block holding its centre (planner message 2 on
 * dispatch 2026-09-28-03).
 */
data class ForecastBlock(val south: Int, val west: Int) {
    companion object {
        /**
         * The block holding [centre]. The centre is rounded to tenths first, because a centre on a
         * multiple of 0.1 computed in floating point can land a hair either side of a whole degree
         * (`450 * 0.1` is not exactly 45), and a hair under must not move it into the block below.
         */
        fun containing(centre: LatLng): ForecastBlock =
            ForecastBlock(Math.floorDiv(tenths(centre.lat), 10L).toInt(), Math.floorDiv(tenths(centre.lng), 10L).toInt())

        /**
         * Every block the box from ([south], [west]) to ([north], [east]) overlaps, edges included.
         * Latitude is kept to -90..89; an [east] below [west] is a box across the antimeridian, taken
         * as the two ranges either side of it.
         */
        fun touching(south: Double, west: Double, north: Double, east: Double): Set<ForecastBlock> {
            val lats = (floor(south).toInt().coerceIn(-90, 89))..(floor(north).toInt().coerceIn(-90, 89))
            val westBlock = floor(west).toInt().coerceIn(-180, 179)
            val eastBlock = floor(east).toInt().coerceIn(-180, 179)
            val lngs = if (east >= west) (westBlock..eastBlock).toList() else (westBlock..179).toList() + (-180..eastBlock).toList()
            return lats.flatMap { lat -> lngs.map { lng -> ForecastBlock(lat, lng) } }.toSet()
        }

        private fun tenths(degrees: Double): Long = (degrees * 10).roundToLong()
    }
}

/** One of a cell's top drivers, as the file carries it (D55: "list of `{label, value}`, top first"). */
data class ForecastDriver(val label: String, val value: String)

/**
 * One 0.1-degree weather cell of one group's forecast for one week, with the properties D55 names.
 * [centre] is the polygon's centre, on multiples of 0.1 degree.
 */
data class ForecastCell(
    val group: String,
    val week: LocalDate,
    val centre: LatLng,
    val chance: Double,
    val uncertaintyLow: Double,
    val uncertaintyHigh: Double,
    val applicable: Boolean,
    val drivers: List<ForecastDriver>,
    val weatherThrough: LocalDate,
    val modelVersion: String,
)

/** A feature a block file carried that did not become a [ForecastCell], and why. */
data class ForecastCellRejection(val featureIndex: Int, val reason: String)

/** A block file's features, parsed: the cells, and every feature that was rejected. */
data class ParsedForecastCells(val cells: List<ForecastCell>, val rejected: List<ForecastCellRejection>)

/**
 * A block file (D55: one GeoJSON `FeatureCollection` per group per week per 1-degree block) parsed and
 * validated, pure and headless. A feature that does not fit D55's shape is **rejected and counted**,
 * with its index and a reason, and the rest of the file still parses; nothing is clamped or defaulted
 * (CLAUDE.md, Errors). A document that is not a feature collection at all fails as a whole.
 *
 * What a feature must be:
 * - its geometry a `Polygon` whose outer ring spans exactly 0.1 degree each way and is centred on
 *   multiples of 0.1 (edges at x.x5, ERA5-Land's grid; planner message 2), so nothing finer than the
 *   weather cell is ever drawn (D55);
 * - `group` a non-empty string; `week` an ISO date that is a Monday (D55: "ISO week start");
 * - `chance`, `uncertainty_low` and `uncertainty_high` JSON numbers from 0 to 1;
 * - `applicable` a JSON boolean. A cell that is not applicable parses, carrying its flag; drawing it is
 *   the map's rule (it draws nothing), and a file may also leave such a cell out (D55 allows both);
 * - `drivers` an array of `{label, value}` objects, `label` a string and `value` a string or a number,
 *   kept as text (D55 does not say what a value means);
 * - `weather_through` an ISO date; `model_version` a non-empty string or number, kept as text.
 *
 * Not enforced, because D55 does not state it: that `uncertainty_low <= chance <= uncertainty_high`.
 */
fun parseForecastCells(geoJson: String): Result<ParsedForecastCells> = runCatching {
    val root = Json.parseToJsonElement(geoJson) as? JsonObject ?: throw IllegalArgumentException("the file is not a JSON object")
    require(root.string("type") == "FeatureCollection") { "the file is not a FeatureCollection" }
    val features = root["features"] as? JsonArray ?: throw IllegalArgumentException("the file has no features array")
    val cells = mutableListOf<ForecastCell>()
    val rejected = mutableListOf<ForecastCellRejection>()
    features.forEachIndexed { index, feature ->
        try {
            cells += cellOf(feature)
        } catch (e: CellRejected) {
            rejected += ForecastCellRejection(index, e.message.orEmpty())
        }
    }
    ParsedForecastCells(cells, rejected)
}

private class CellRejected(reason: String) : Exception(reason)

private fun reject(reason: String): Nothing = throw CellRejected(reason)

private fun JsonObject.string(key: String): String? = (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.content

private fun cellOf(feature: JsonElement): ForecastCell {
    val obj = feature as? JsonObject ?: reject("not an object")
    if (obj.string("type") != "Feature") reject("not a Feature")
    val centre = cellCentreOf(obj["geometry"] as? JsonObject ?: reject("no geometry"))
    val props = obj["properties"] as? JsonObject ?: reject("no properties")

    val group = props.string("group")?.takeIf { it.isNotBlank() } ?: reject("group is not a non-empty string")
    val week = props.date("week")
    if (week.dayOfWeek != DayOfWeek.MONDAY) reject("week $week is not an ISO week start (a Monday)")
    return ForecastCell(
        group = group,
        week = week,
        centre = centre,
        chance = props.unitInterval("chance"),
        uncertaintyLow = props.unitInterval("uncertainty_low"),
        uncertaintyHigh = props.unitInterval("uncertainty_high"),
        applicable = (props["applicable"] as? JsonPrimitive)?.takeIf { !it.isString }?.booleanOrNull
            ?: reject("applicable is not a boolean"),
        drivers = driversOf(props["drivers"]),
        weatherThrough = props.date("weather_through"),
        modelVersion = (props["model_version"] as? JsonPrimitive)?.takeIf { it !is JsonNull }?.content?.takeIf { it.isNotBlank() }
            ?: reject("model_version is missing"),
    )
}

private fun JsonObject.date(key: String): LocalDate {
    val text = string(key) ?: reject("$key is not a date string")
    return try {
        LocalDate.parse(text)
    } catch (e: DateTimeParseException) {
        reject("$key '$text' is not an ISO date")
    }
}

private fun JsonObject.unitInterval(key: String): Double {
    val value = (this[key] as? JsonPrimitive)?.takeIf { !it.isString }?.doubleOrNull ?: reject("$key is not a number")
    if (!value.isFinite() || value < 0.0 || value > 1.0) reject("$key $value is outside 0 to 1")
    return value
}

private fun driversOf(element: JsonElement?): List<ForecastDriver> {
    val array = element as? JsonArray ?: reject("drivers is not a list")
    return array.map { driver ->
        val obj = driver as? JsonObject ?: reject("a driver is not an object")
        val label = obj.string("label") ?: reject("a driver's label is not a string")
        val value = (obj["value"] as? JsonPrimitive)?.takeIf { it !is JsonNull && (it.isString || it.doubleOrNull != null) }?.content
            ?: reject("driver '$label' has no string or number value")
        ForecastDriver(label, value)
    }
}

/** A 0.1-degree cell's centre, from its polygon; rejects any other shape or alignment. */
private fun cellCentreOf(geometry: JsonObject): LatLng {
    if (geometry.string("type") != "Polygon") reject("geometry is not a Polygon")
    val ring = ((geometry["coordinates"] as? JsonArray)?.firstOrNull() as? JsonArray) ?: reject("the polygon has no outer ring")
    if (ring.size < 4) reject("the polygon's ring has ${ring.size} positions")
    val positions = ring.map { position ->
        val pair = position as? JsonArray ?: reject("a position is not an array")
        val lng = (pair.getOrNull(0) as? JsonPrimitive)?.takeIf { !it.isString }?.doubleOrNull ?: reject("a position has no longitude")
        val lat = (pair.getOrNull(1) as? JsonPrimitive)?.takeIf { !it.isString }?.doubleOrNull ?: reject("a position has no latitude")
        lat to lng
    }
    val south = positions.minOf { it.first }
    val north = positions.maxOf { it.first }
    val west = positions.minOf { it.second }
    val east = positions.maxOf { it.second }
    if (abs(north - south - CELL_DEGREES) > ALIGNMENT_TOLERANCE || abs(east - west - CELL_DEGREES) > ALIGNMENT_TOLERANCE) {
        reject("the polygon spans ${north - south} by ${east - west} degrees, not a 0.1-degree cell")
    }
    val lat = (south + north) / 2
    val lng = (west + east) / 2
    if (!onTenth(lat) || !onTenth(lng)) reject("the cell's centre ($lat, $lng) is not on a multiple of 0.1 degree")
    if (lat !in -90.0..90.0 || lng !in -180.0..180.0) reject("the cell's centre ($lat, $lng) is off the globe")
    return LatLng((lat * 10).roundToLong() / 10.0, (lng * 10).roundToLong() / 10.0)
}

private fun onTenth(degrees: Double): Boolean = abs(degrees * 10 - (degrees * 10).roundToLong()) <= ALIGNMENT_TOLERANCE * 10

private const val CELL_DEGREES = 0.1
private const val ALIGNMENT_TOLERANCE = 1e-6

/** What a [ForecastCellStore] answers for one group, week and set of blocks. */
sealed interface ForecastCellsResult {
    /** The store holds no forecast data for this group and week. An explicit answer, never an empty [Cells]. */
    data object NoForecastData : ForecastCellsResult

    /** The cells the store holds for the requested blocks, and how many features were rejected reading them. */
    data class Cells(val cells: List<ForecastCell>, val rejectedCount: Int) : ForecastCellsResult
}

/** Which groups a [ForecastCellStore] holds data for, for one week. */
sealed interface ForecastAvailability {
    /** No group has data for this week. */
    data object NoForecastData : ForecastAvailability

    data class Groups(val groups: Set<String>) : ForecastAvailability
}

/**
 * The stored-data interface for forecast cells (map layers L0b, B5): what the map reads its colour
 * fields from. Keyed by group, week and 1-degree block (planner message 2). A real, downloaded store
 * will stand behind this later; no downloader exists, and nothing here touches the network.
 */
interface ForecastCellStore {
    suspend fun availability(week: LocalDate): ForecastAvailability

    suspend fun cells(group: String, week: LocalDate, blocks: Set<ForecastBlock>): ForecastCellsResult
}

/**
 * The store with no forecast data: every question answered with the explicit "no forecast data", never
 * an empty list passed off as success (the dispatch, B5; CLAUDE.md, Errors: an unsupported capability
 * says so). The release build's only store (`forecastCellStore` in `src/release`), and what a map with
 * no store is treated as having.
 */
object AbsentForecastCellStore : ForecastCellStore {
    override suspend fun availability(week: LocalDate): ForecastAvailability = ForecastAvailability.NoForecastData

    override suspend fun cells(group: String, week: LocalDate, blocks: Set<ForecastBlock>): ForecastCellsResult =
        ForecastCellsResult.NoForecastData
}

/** The Monday that starts [date]'s ISO week: the `week` key a forecast file carries (D55: "ISO week start"). */
fun isoWeekStart(date: LocalDate): LocalDate = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
