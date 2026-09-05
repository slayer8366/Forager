package com.forager.app.sensor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * HUD-foundations dispatch, Item 2: the sign, not just the magnitude. Two real sites of opposite
 * sign, each pinned to a literal from the World Magnetic Model rather than read back from the
 * code under test — Portland, Oregon (about +14.5° east in 2026) and Boston, Massachusetts (about
 * −14° west). A flipped sign fails both floors outright; a stubbed `GeomagneticField` returning a
 * constant fails at least one of the two.
 *
 * **Why this proves something under Robolectric.** Robolectric's `android-all-instrumented-16`
 * jar (the SDK 36 framework this test runs on) carries the framework's real
 * `android/hardware/GeomagneticField.class` and its `LegendreTable` inner class — the full WMM
 * evaluation, not a stub — and Robolectric's `shadows-framework` ships no `ShadowGeomagneticField`,
 * so nothing intercepts the call. Both checked directly against the pinned jars in the Gradle
 * cache, not assumed.
 *
 * **Tolerance: ±1.5°.** The WMM edition inside the framework may be the 2020 or the 2025 model,
 * which differ by a few tenths of a degree at these dates; declination also drifts about a tenth
 * of a degree per year. 1.5° covers both without covering a sign flip, which is a 29° error here.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AndroidDeclinationProviderTest {

    private val provider = AndroidDeclinationProvider()

    /** 2026-09-05T00:00:00Z. */
    private val epochMillis = 1_788_566_400_000L

    @Test
    fun `Portland Oregon has an east declination of about plus 14 and a half degrees`() {
        val declination = provider.declinationDegrees(latitude = 45.52, longitude = -122.68, altitudeMeters = 50.0, epochMillis = epochMillis)

        assertEquals(14.5f, declination, 1.5f)
        assertTrue("Portland's declination must be east (positive), was $declination", declination > 10f)
    }

    @Test
    fun `Boston Massachusetts has a west declination of about minus 14 degrees`() {
        val declination = provider.declinationDegrees(latitude = 42.36, longitude = -71.06, altitudeMeters = 20.0, epochMillis = epochMillis)

        assertEquals(-14.0f, declination, 1.5f)
        assertTrue("Boston's declination must be west (negative), was $declination", declination < -10f)
    }

    /** A missing altitude evaluates at sea level — within hundredths of a degree of the 50 m answer. */
    @Test
    fun `a null altitude is evaluated at sea level, not rejected`() {
        val atFifty = provider.declinationDegrees(45.52, -122.68, 50.0, epochMillis)
        val unknown = provider.declinationDegrees(45.52, -122.68, null, epochMillis)

        assertEquals(atFifty, unknown, 0.05f)
    }
}
