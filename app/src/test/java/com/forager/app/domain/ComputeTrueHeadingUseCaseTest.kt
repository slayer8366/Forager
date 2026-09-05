package com.forager.app.domain

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * HUD-foundations dispatch, Item 2. The arithmetic (`true = magnetic + declination`, wrapped into
 * `[0, 360)`) and the recompute cadence, against a fake [DeclinationProvider] that records every
 * call — so the cadence is asserted on real call counts, not inferred. Expected headings are
 * hand-computed literals.
 */
class ComputeTrueHeadingUseCaseTest {

    private val portland = Site(lat = 45.52, lng = -122.68)
    private val t0 = 1_780_000_000_000L

    @Test
    fun `an east declination is added to the magnetic heading`() {
        val useCase = ComputeTrueHeadingUseCase(FixedDeclination(15f))

        assertEquals(15f, useCase(0f, portland.lat, portland.lng, 50.0, t0), 1e-4f)
        assertEquals(105f, useCase(90f, portland.lat, portland.lng, 50.0, t0), 1e-4f)
    }

    @Test
    fun `a west declination is subtracted, and the result wraps into zero to 360`() {
        val useCase = ComputeTrueHeadingUseCase(FixedDeclination(-14f))

        // 5° magnetic at 14° west declination is 351° true, not −9°.
        assertEquals(351f, useCase(5f, portland.lat, portland.lng, 50.0, t0), 1e-4f)
    }

    @Test
    fun `350 degrees magnetic at plus 15 declination is 5 degrees true, not 365`() {
        val useCase = ComputeTrueHeadingUseCase(FixedDeclination(15f))

        assertEquals(5f, useCase(350f, portland.lat, portland.lng, 50.0, t0), 1e-4f)
    }

    @Test
    fun `declination is computed once for a walk that stays within ten kilometres and one day`() {
        val provider = FixedDeclination(15f)
        val useCase = ComputeTrueHeadingUseCase(provider)

        useCase(0f, portland.lat, portland.lng, 50.0, t0)
        // ~5 km north of the first call, an hour later: inside both bounds, so no recompute.
        useCase(0f, portland.lat + 0.045, portland.lng, 50.0, t0 + 60L * 60L * 1_000L)
        useCase(0f, portland.lat + 0.045, portland.lng, 50.0, t0 + 2L * 60L * 60L * 1_000L)

        assertEquals(1, provider.calls.size)
    }

    @Test
    fun `moving more than ten kilometres recomputes declination`() {
        val provider = FixedDeclination(15f)
        val useCase = ComputeTrueHeadingUseCase(provider)

        useCase(0f, portland.lat, portland.lng, 50.0, t0)
        // ~11 km north (0.1° of latitude is ~11.1 km): past the distance bound.
        useCase(0f, portland.lat + 0.1, portland.lng, 50.0, t0 + 1_000L)

        assertEquals(2, provider.calls.size)
        assertEquals(portland.lat + 0.1, provider.calls.last().lat, 1e-9)
    }

    @Test
    fun `more than a day at the same spot recomputes declination`() {
        val provider = FixedDeclination(15f)
        val useCase = ComputeTrueHeadingUseCase(provider)

        useCase(0f, portland.lat, portland.lng, 50.0, t0)
        useCase(0f, portland.lat, portland.lng, 50.0, t0 + 25L * 60L * 60L * 1_000L)

        assertEquals(2, provider.calls.size)
        assertEquals(t0 + 25L * 60L * 60L * 1_000L, provider.calls.last().epochMillis)
    }

    @Test
    fun `a recompute is what the next heading actually uses`() {
        val provider = FixedDeclination(15f)
        val useCase = ComputeTrueHeadingUseCase(provider)
        useCase(0f, portland.lat, portland.lng, 50.0, t0)

        provider.declination = -14f
        val afterMove = useCase(0f, portland.lat + 0.1, portland.lng, 50.0, t0 + 1_000L)

        assertEquals(346f, afterMove, 1e-4f)
    }

    private data class Site(val lat: Double, val lng: Double)

    private data class Call(val lat: Double, val lng: Double, val altitudeMeters: Double?, val epochMillis: Long)

    private class FixedDeclination(var declination: Float) : DeclinationProvider {
        val calls = mutableListOf<Call>()

        override fun declinationDegrees(latitude: Double, longitude: Double, altitudeMeters: Double?, epochMillis: Long): Float {
            calls += Call(latitude, longitude, altitudeMeters, epochMillis)
            return declination
        }
    }
}
