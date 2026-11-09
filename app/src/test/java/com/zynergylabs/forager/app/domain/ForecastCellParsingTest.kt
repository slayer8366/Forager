package com.zynergylabs.forager.app.domain

import com.zynergylabs.forager.app.domain.model.LatLng
import java.time.LocalDate
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The forecast-cell interface's pure half (map layers L0b, B5, with planner message 2's keying):
 * a block file's features parsed and validated against D55's property list, blocks, the ISO week,
 * and the explicit "no forecast data" answer.
 */
class ForecastCellParsingTest {

    private val week = LocalDate.of(2026, 9, 28)

    /** A 0.1-degree cell polygon around ([lat], [lng]), edges at x.x5, and D55's properties. */
    private fun cell(
        lat: Double,
        lng: Double,
        overrides: Map<String, JsonElement?> = emptyMap(),
        halfSide: Double = 0.05,
        geometryType: String = "Polygon",
    ): JsonObject {
        val ring = listOf(
            lng - halfSide to lat - halfSide,
            lng + halfSide to lat - halfSide,
            lng + halfSide to lat + halfSide,
            lng - halfSide to lat + halfSide,
            lng - halfSide to lat - halfSide,
        )
        val properties = linkedMapOf<String, JsonElement>(
            "group" to JsonPrimitive("chanterelles"),
            "week" to JsonPrimitive("2026-09-28"),
            "chance" to JsonPrimitive(0.3),
            "uncertainty_low" to JsonPrimitive(0.2),
            "uncertainty_high" to JsonPrimitive(0.4),
            "applicable" to JsonPrimitive(true),
            "drivers" to buildJsonArray {
                add(buildJsonObject { put("label", "Rain, last 14 days"); put("value", 0.5) })
                add(buildJsonObject { put("label", "Soil temperature"); put("value", "11 C") })
            },
            "weather_through" to JsonPrimitive("2026-09-26"),
            "model_version" to JsonPrimitive("synthetic-1"),
        )
        overrides.forEach { (key, value) -> if (value == null) properties.remove(key) else properties[key] = value }
        return buildJsonObject {
            put("type", "Feature")
            put(
                "geometry",
                buildJsonObject {
                    put("type", geometryType)
                    put("coordinates", JsonArray(listOf(JsonArray(ring.map { (x, y) -> JsonArray(listOf(JsonPrimitive(x), JsonPrimitive(y))) }))))
                },
            )
            put("properties", JsonObject(properties))
        }
    }

    private fun file(vararg features: JsonObject): String =
        buildJsonObject { put("type", "FeatureCollection"); put("features", JsonArray(features.toList())) }.toString()

    @Test
    fun `a well-formed block file parses every feature into a cell with all its properties`() {
        val parsed = parseForecastCells(file(cell(45.1, -122.3), cell(45.0, -122.0, mapOf("applicable" to JsonPrimitive(false))))).getOrThrow()

        assertEquals(emptyList<ForecastCellRejection>(), parsed.rejected)
        assertEquals(
            ForecastCell(
                group = "chanterelles",
                week = week,
                centre = LatLng(45.1, -122.3),
                chance = 0.3,
                uncertaintyLow = 0.2,
                uncertaintyHigh = 0.4,
                applicable = true,
                drivers = listOf(ForecastDriver("Rain, last 14 days", "0.5"), ForecastDriver("Soil temperature", "11 C")),
                weatherThrough = LocalDate.of(2026, 9, 26),
                modelVersion = "synthetic-1",
            ),
            parsed.cells.first(),
        )
        assertEquals("a cell that is not applicable parses, carrying its flag", false, parsed.cells[1].applicable)
        assertEquals(LatLng(45.0, -122.0), parsed.cells[1].centre)
    }

    @Test
    fun `a malformed feature is rejected and counted, and the rest of the file still parses`() {
        val parsed = parseForecastCells(
            file(
                cell(45.1, -122.3),
                cell(45.2, -122.3, mapOf("chance" to null)),
                cell(45.3, -122.3, mapOf("chance" to JsonPrimitive("high"))),
                cell(45.4, -122.3, mapOf("week" to JsonPrimitive("next week"))),
                cell(45.5, -122.3, mapOf("drivers" to JsonPrimitive("rain"))),
                cell(45.6, -122.3, mapOf("group" to JsonPrimitive(""))),
                cell(45.7, -122.3, geometryType = "Point"),
                cell(45.8, -122.3, halfSide = 0.5),
                cell(45.83, -122.3),
                cell(45.9, -122.3, mapOf("applicable" to JsonPrimitive("yes"))),
            ),
        ).getOrThrow()

        assertEquals(listOf(LatLng(45.1, -122.3)), parsed.cells.map { it.centre })
        assertEquals(listOf(1, 2, 3, 4, 5, 6, 7, 8, 9), parsed.rejected.map { it.featureIndex })
        assertTrue(parsed.rejected.toString(), parsed.rejected.all { it.reason.isNotBlank() })
    }

    @Test
    fun `a chance or bound outside 0 to 1 is rejected and counted, never clamped`() {
        val parsed = parseForecastCells(
            file(
                cell(45.1, -122.3, mapOf("chance" to JsonPrimitive(0))),
                cell(45.2, -122.3, mapOf("chance" to JsonPrimitive(1))),
                cell(45.3, -122.3, mapOf("chance" to JsonPrimitive(1.2))),
                cell(45.4, -122.3, mapOf("chance" to JsonPrimitive(-0.1))),
                cell(45.5, -122.3, mapOf("uncertainty_high" to JsonPrimitive(1.01))),
            ),
        ).getOrThrow()

        assertEquals(listOf(0.0, 1.0), parsed.cells.map { it.chance })
        assertEquals(listOf(2, 3, 4), parsed.rejected.map { it.featureIndex })
        assertTrue(parsed.rejected[0].reason, "1.2" in parsed.rejected[0].reason)
    }

    @Test
    fun `a file that is not a feature collection fails as a whole`() {
        assertTrue(parseForecastCells("""{"type":"Feature"}""").isFailure)
        assertTrue(parseForecastCells("not json").isFailure)
    }

    @Test
    fun `a cell belongs to the block holding its centre, whole degrees included, south and west of zero too`() {
        assertEquals(ForecastBlock(45, -123), ForecastBlock.containing(LatLng(45.0, -122.1)))
        assertEquals(ForecastBlock(45, -122), ForecastBlock.containing(LatLng(45.9, -122.0)))
        assertEquals(ForecastBlock(46, -122), ForecastBlock.containing(LatLng(46.0, -121.9)))
        assertEquals(ForecastBlock(-1, -1), ForecastBlock.containing(LatLng(-0.1, -0.1)))
        assertEquals(ForecastBlock(0, 0), ForecastBlock.containing(LatLng(0.0, 0.0)))
        // A centre computed in floating point, a hair under a whole degree, is still that degree's.
        assertEquals(ForecastBlock(45, -122), ForecastBlock.containing(LatLng(450 * 0.1 - 1e-12, -1220 * 0.1)))
    }

    @Test
    fun `the blocks touching an area are every block it overlaps and no other`() {
        assertEquals(
            setOf(ForecastBlock(45, -123), ForecastBlock(45, -122), ForecastBlock(46, -123), ForecastBlock(46, -122)),
            ForecastBlock.touching(south = 45.2, west = -122.8, north = 46.1, east = -121.9),
        )
        assertEquals(setOf(ForecastBlock(45, -123)), ForecastBlock.touching(south = 45.2, west = -122.8, north = 45.6, east = -122.3))
    }

    @Test
    fun `the absent store answers no forecast data explicitly, never an empty list`() = runTest {
        assertEquals(ForecastAvailability.NoForecastData, AbsentForecastCellStore.availability(week))
        assertEquals(ForecastCellsResult.NoForecastData, AbsentForecastCellStore.cells("chanterelles", week, setOf(ForecastBlock(45, -123))))
    }

    @Test
    fun `the week of a date is the Monday that starts its ISO week`() {
        assertEquals(LocalDate.of(2026, 9, 21), isoWeekStart(LocalDate.of(2026, 9, 27)))
        assertEquals(LocalDate.of(2026, 9, 28), isoWeekStart(LocalDate.of(2026, 9, 28)))
        assertEquals(LocalDate.of(2026, 9, 28), isoWeekStart(LocalDate.of(2026, 10, 3)))
    }
}
