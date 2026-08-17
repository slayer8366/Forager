package com.forager.app.data.repository

import com.forager.app.data.remote.dto.DailyPrecipitationDto
import com.forager.app.data.remote.dto.HourlySoilDto
import com.forager.app.data.remote.dto.PrecipitationResponseDto
import com.forager.app.domain.model.Region
import com.forager.app.domain.model.SoilDepthBand
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * Mapping the response onto a [com.forager.app.domain.model.WeatherSeries].
 *
 * Two things are under test here that a mock echoing an assumption back would never catch, and
 * that the live-API script `scripts/verify-open-meteo-fields.sh` measured rather than assumed:
 * that soil depth bands differ by location and null out silently, and that hourly readings have to
 * be reduced to local days by their timestamps.
 */
class OpenMeteoWeatherSeriesTest {

    private val region = Region(lat = 45.5, lng = -122.6, radiusKm = 10)
    private val referenceDay: LocalDate = LocalDate.of(2026, 8, 16)
    private val firstDay: LocalDate = referenceDay.minusDays(2)
    private val dates = listOf(firstDay, firstDay.plusDays(1), referenceDay, referenceDay.plusDays(1))

    /** 24 hourly timestamps for [date], in the location's local time, as Open-Meteo formats them. */
    private fun hoursOf(date: LocalDate, count: Int = 24) =
        (0 until count).map { "%sT%02d:00".format(date, it) }

    private fun response(
        hourly: HourlySoilDto? = null,
        units: Map<String, String> = emptyMap(),
        precipitation: List<Double> = List(dates.size) { 0.0 },
        et0: List<Double?> = List(dates.size) { 2.0 },
    ) = PrecipitationResponseDto(
        utcOffsetSeconds = -25200,
        daily = DailyPrecipitationDto(
            time = dates.map { it.toString() },
            precipitationSum = precipitation,
            et0FaoEvapotranspiration = et0,
        ),
        hourly = hourly,
        hourlyUnits = units,
    )

    // ---- the past/future split, on the series side -----------------------------------------

    @Test
    fun `days before the reference day are observed and the rest are forecast`() {
        val series = toWeatherSeries(response(), region, referenceDay)

        assertEquals(dates, series.days.map { it.date })
        assertEquals(listOf(firstDay, firstDay.plusDays(1)), series.observedDays.map { it.date })
        assertEquals(listOf(referenceDay, referenceDay.plusDays(1)), series.forecastDays.map { it.date })
    }

    @Test
    fun `the reference day itself is forecast, not observed`() {
        val series = toWeatherSeries(response(), region, referenceDay)

        assertTrue(series.days.single { it.date == referenceDay }.isForecast)
        assertFalse(series.days.single { it.date == firstDay }.isForecast)
    }

    @Test
    fun `precipitation and evapotranspiration are carried through per day`() {
        val series = toWeatherSeries(
            response(precipitation = listOf(1.0, 2.0, 3.0, 4.0), et0 = listOf(0.5, 1.5, 2.5, 3.5)),
            region,
            referenceDay,
        )

        assertEquals(listOf(1.0, 2.0, 3.0, 4.0), series.days.map { it.precipitationMm })
        assertEquals(listOf(0.5, 1.5, 2.5, 3.5), series.days.map { it.evapotranspirationMm })
    }

    // ---- soil depth band resolution --------------------------------------------------------

    @Test
    fun `the 0 to 7cm band is used when the location's model serves it`() {
        // The shape London returns: 0–7cm populated, 0–10cm all null with units "undefined".
        val hours = dates.flatMap { hoursOf(it) }
        val series = toWeatherSeries(
            response(
                hourly = HourlySoilDto(
                    time = hours,
                    soilMoisture0To7Cm = List(hours.size) { 0.20 },
                    soilMoisture0To10Cm = List(hours.size) { null },
                    soilTemperature0To7Cm = List(hours.size) { 13.0 },
                    soilTemperature0To10Cm = List(hours.size) { null },
                ),
                units = mapOf(
                    "soil_moisture_0_to_7cm" to "m³/m³",
                    "soil_moisture_0_to_10cm" to "undefined",
                    "soil_temperature_0_to_7cm" to "°C",
                    "soil_temperature_0_to_10cm" to "undefined",
                ),
            ),
            region,
            referenceDay,
        )

        assertEquals(SoilDepthBand(0, 7), series.soilAvailability.shallowMoistureBand)
        assertEquals(SoilDepthBand(0, 7), series.soilAvailability.temperatureBand)
        assertEquals(0.20, series.days.first().shallowSoilMoistureM3M3!!, 0.0001)
    }

    @Test
    fun `the 0 to 10cm band is used when 0 to 7cm is served as nulls`() {
        // The shape Portland, NYC, Denver and Vancouver return: 0–7cm all null with units
        // "undefined", 0–10cm populated. Hardcoding 0–7cm would ship an empty soil signal here.
        val hours = dates.flatMap { hoursOf(it) }
        val series = toWeatherSeries(
            response(
                hourly = HourlySoilDto(
                    time = hours,
                    soilMoisture0To7Cm = List(hours.size) { null },
                    soilMoisture0To10Cm = List(hours.size) { 0.19 },
                    soilMoisture7To28Cm = List(hours.size) { null },
                    soilMoisture10To40Cm = List(hours.size) { 0.22 },
                    soilTemperature0To7Cm = List(hours.size) { null },
                    soilTemperature0To10Cm = List(hours.size) { 21.0 },
                ),
                units = mapOf(
                    "soil_moisture_0_to_7cm" to "undefined",
                    "soil_moisture_0_to_10cm" to "m³/m³",
                    "soil_moisture_7_to_28cm" to "undefined",
                    "soil_moisture_10_to_40cm" to "m³/m³",
                    "soil_temperature_0_to_7cm" to "undefined",
                    "soil_temperature_0_to_10cm" to "°C",
                ),
            ),
            region,
            referenceDay,
        )

        assertEquals(SoilDepthBand(0, 10), series.soilAvailability.shallowMoistureBand)
        assertEquals(SoilDepthBand(10, 40), series.soilAvailability.deeperMoistureBand)
        assertEquals(SoilDepthBand(0, 10), series.soilAvailability.temperatureBand)
        assertEquals(0.19, series.days.first().shallowSoilMoistureM3M3!!, 0.0001)
        assertEquals(0.22, series.days.first().deeperSoilMoistureM3M3!!, 0.0001)
        assertEquals(21.0, series.days.first().soilTemperatureMeanC!!, 0.0001)
    }

    @Test
    fun `a band whose units say undefined is not used even if a stray value appears`() {
        // "undefined" is Open-Meteo's own marker that the variable is not served at this location.
        // A value arriving under that marker is not a reading this app is willing to report.
        val hours = dates.flatMap { hoursOf(it) }
        val series = toWeatherSeries(
            response(
                hourly = HourlySoilDto(
                    time = hours,
                    soilMoisture0To7Cm = List(hours.size) { 0.99 },
                    soilMoisture0To10Cm = List(hours.size) { 0.19 },
                ),
                units = mapOf(
                    "soil_moisture_0_to_7cm" to "undefined",
                    "soil_moisture_0_to_10cm" to "m³/m³",
                ),
            ),
            region,
            referenceDay,
        )

        assertEquals(SoilDepthBand(0, 10), series.soilAvailability.shallowMoistureBand)
        assertEquals(0.19, series.days.first().shallowSoilMoistureM3M3!!, 0.0001)
    }

    @Test
    fun `no served band reports an explicit unavailability instead of a substitute`() {
        val series = toWeatherSeries(response(), region, referenceDay)

        val availability = series.soilAvailability
        assertNull(availability.shallowMoistureBand)
        assertNull(availability.deeperMoistureBand)
        assertNull(availability.temperatureBand)
        assertFalse(availability.hasAnySoilData)
        assertEquals(3, availability.unavailable.size)
        assertTrue(
            availability.unavailable.any {
                it.startsWith("Soil temperature is unavailable at this location") &&
                    it.contains("0–7 cm or 0–10 cm")
            },
        )
        assertNull(series.days.first().shallowSoilMoistureM3M3)
        assertNull(series.days.first().soilTemperatureMeanC)
    }

    @Test
    fun `one unavailable variable does not suppress the ones that are served`() {
        val hours = dates.flatMap { hoursOf(it) }
        val series = toWeatherSeries(
            response(
                hourly = HourlySoilDto(
                    time = hours,
                    soilMoisture0To10Cm = List(hours.size) { 0.19 },
                ),
                units = mapOf("soil_moisture_0_to_10cm" to "m³/m³"),
            ),
            region,
            referenceDay,
        )

        assertEquals(SoilDepthBand(0, 10), series.soilAvailability.shallowMoistureBand)
        assertNull(series.soilAvailability.temperatureBand)
        assertEquals(2, series.soilAvailability.unavailable.size)
    }

    // ---- hourly to daily reduction ---------------------------------------------------------

    @Test
    fun `soil moisture is reduced to the day's mean`() {
        val day = dates.first()
        val hours = hoursOf(day)
        // 0.0 .. 0.23 in 0.01 steps; mean is 0.115.
        val values = (0 until 24).map { it * 0.01 }
        val series = toWeatherSeries(
            response(
                hourly = HourlySoilDto(time = hours, soilMoisture0To10Cm = values),
                units = mapOf("soil_moisture_0_to_10cm" to "m³/m³"),
            ),
            region,
            referenceDay,
        )

        assertEquals(0.115, series.days.single { it.date == day }.shallowSoilMoistureM3M3!!, 0.0001)
    }

    @Test
    fun `soil temperature keeps the day's mean, min and max`() {
        val day = dates.first()
        val hours = hoursOf(day)
        // A real diurnal swing: 9 °C overnight up to 20 °C mid-afternoon. A lone mean would
        // report 14.5 °C and hide the whole range.
        val values = (0 until 24).map { 9.0 + it * (11.0 / 23.0) }
        val series = toWeatherSeries(
            response(
                hourly = HourlySoilDto(time = hours, soilTemperature0To10Cm = values),
                units = mapOf("soil_temperature_0_to_10cm" to "°C"),
            ),
            region,
            referenceDay,
        )

        val reading = series.days.single { it.date == day }
        assertEquals(14.5, reading.soilTemperatureMeanC!!, 0.0001)
        assertEquals(9.0, reading.soilTemperatureMinC!!, 0.0001)
        assertEquals(20.0, reading.soilTemperatureMaxC!!, 0.0001)
    }

    @Test
    fun `hourly readings are grouped by their own local date, not by position`() {
        val hours = dates.flatMap { hoursOf(it) }
        // A distinct constant per day, so a mis-grouped reduction cannot produce these numbers.
        val values = dates.flatMapIndexed { index: Int, _: LocalDate ->
            List(24) { 10.0 + index }
        }
        val series = toWeatherSeries(
            response(
                hourly = HourlySoilDto(time = hours, soilTemperature0To10Cm = values),
                units = mapOf("soil_temperature_0_to_10cm" to "°C"),
            ),
            region,
            referenceDay,
        )

        assertEquals(
            listOf(10.0, 11.0, 12.0, 13.0),
            series.days.map { it.soilTemperatureMeanC },
        )
    }

    @Test
    fun `a day with too few readings reports no soil value rather than a partial mean`() {
        val shortDay = dates[0]
        val fullDay = dates[1]
        // 19 readings is one short of the minimum; the full day beside it still reports.
        val hours = hoursOf(shortDay, count = 19) + hoursOf(fullDay)
        val values = List(19) { 5.0 } + List(24) { 15.0 }
        val series = toWeatherSeries(
            response(
                hourly = HourlySoilDto(time = hours, soilTemperature0To10Cm = values),
                units = mapOf("soil_temperature_0_to_10cm" to "°C"),
            ),
            region,
            referenceDay,
        )

        assertNull(series.days.single { it.date == shortDay }.soilTemperatureMeanC)
        assertEquals(15.0, series.days.single { it.date == fullDay }.soilTemperatureMeanC!!, 0.0001)
    }

    @Test
    fun `a 23-hour daylight-saving day still reports a soil value`() {
        val dstDay = dates[0]
        val hours = hoursOf(dstDay, count = 23)
        val series = toWeatherSeries(
            response(
                hourly = HourlySoilDto(time = hours, soilTemperature0To10Cm = List(23) { 12.0 }),
                units = mapOf("soil_temperature_0_to_10cm" to "°C"),
            ),
            region,
            referenceDay,
        )

        assertEquals(12.0, series.days.single { it.date == dstDay }.soilTemperatureMeanC!!, 0.0001)
    }

    @Test
    fun `scattered null hours inside a day are dropped, not treated as zero`() {
        val day = dates.first()
        val hours = hoursOf(day)
        // 22 readings of 16.0 and two holes. A null-as-zero reduction would report ~14.67.
        val values = (0 until 24).map { if (it == 5 || it == 6) null else 16.0 }
        val series = toWeatherSeries(
            response(
                hourly = HourlySoilDto(time = hours, soilTemperature0To10Cm = values),
                units = mapOf("soil_temperature_0_to_10cm" to "°C"),
            ),
            region,
            referenceDay,
        )

        assertEquals(16.0, series.days.single { it.date == day }.soilTemperatureMeanC!!, 0.0001)
    }
}
