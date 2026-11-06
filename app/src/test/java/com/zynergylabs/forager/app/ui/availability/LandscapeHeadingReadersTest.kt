package com.zynergylabs.forager.app.ui.availability

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorManager
import android.view.Surface
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.State
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.test.core.app.ApplicationProvider
import com.zynergylabs.forager.app.domain.ComputeTrueHeadingUseCase
import com.zynergylabs.forager.app.domain.DeclinationProvider
import com.zynergylabs.forager.app.domain.FixProvider
import com.zynergylabs.forager.app.domain.LocationFix
import com.zynergylabs.forager.app.domain.model.DistanceUnit
import com.zynergylabs.forager.app.domain.model.LatLng
import com.zynergylabs.forager.app.domain.model.UnitSystem
import com.zynergylabs.forager.app.domain.model.Waypoint
import com.zynergylabs.forager.app.domain.model.WaypointDesignation
import com.zynergylabs.forager.app.sensor.AndroidCompassProvider
import com.zynergylabs.forager.app.ui.map.TrueHeadingCompassEngine
import com.zynergylabs.forager.app.ui.map.TrueHeadingReading
import com.zynergylabs.forager.app.ui.map.rememberTrueHeading
import com.zynergylabs.forager.app.ui.theme.ForagerTheme
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowDisplay
import org.robolectric.shadows.ShadowSensor
import org.robolectric.shadows.ShadowSensorManager
import kotlin.math.cos
import kotlin.math.sin

/**
 * Dispatch 2026-10-12-09 (RECORD -960): every reader of the heading, in landscape, from one fixed sensor reading. The real
 * [AndroidCompassProvider] feeds the real [rememberTrueHeading] (declination fixed at zero, so true equals magnetic), and the one
 * reading it produces is handed to each reader the app hands it to:
 *
 * - the map's bearing while navigating facing up, and the puck's cone: [TrueHeadingCompassEngine], what the map library's camera and
 *   puck read (`SightingsMap`). The camera's own bearing cannot be read under Robolectric (no native map), so this stops at
 *   the value handed to the map library;
 * - the navigation display's heading text, north arrow and turn needle: [navigationReadout];
 * - the compass strip's heading text when not navigating: the real [CompassElevationStrip], read from its node.
 *
 * The phone lies flat with its natural top at 30°. At ROTATION_90 the screen's top faces 120° (SE); at ROTATION_270, 300° (NW).
 * The target is due north of the fix, so the needle reads 0 − 120 = 240 and 0 − 300 = 60.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w823dp-h384dp-land-xxhdpi")
class LandscapeHeadingReadersTest {

    private val composeRule = createAndroidComposeRule<ComponentActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(layoutFixesHostActivityRule()).around(composeRule)

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val shadow: ShadowSensorManager = Shadows.shadowOf(context.getSystemService(SensorManager::class.java))

    private val t = 1_700_000_000_000L
    private val fix = LocationFix.Update(lat = 45.52, lng = -122.68, altitude = 50.0, accuracyMeters = 5f, timestampEpochMillis = t, provider = FixProvider.GPS)
    private val north = Waypoint(id = "origin", lat = 45.53, lng = -122.68, altitude = null, name = "Start", note = "", createdAtEpochMillis = t, trackId = "t1", designation = WaypointDesignation.ORIGIN)

    private object NoDeclination : DeclinationProvider {
        override fun declinationDegrees(latitude: Double, longitude: Double, altitudeMeters: Double?, epochMillis: Long): Float = 0f
    }

    @After
    fun restoreRotation() {
        Shadows.shadowOf(ShadowDisplay.getDefaultDisplay()).setRotation(Surface.ROTATION_0)
    }

    private class Readers(
        val heading: TrueHeadingReading,
        val mapBearing: Float,
        val hud: NavigationHudReadout,
        val stripText: String,
    )

    private fun readersAt(rotation: Int): Readers {
        Shadows.shadowOf(ShadowDisplay.getDefaultDisplay()).setRotation(rotation)
        shadow.addSensor(Sensor.TYPE_ROTATION_VECTOR, ShadowSensor.newInstance(Sensor.TYPE_ROTATION_VECTOR))
        lateinit var heading: State<TrueHeadingReading>
        composeRule.setContent {
            ForagerTheme {
                heading = rememberTrueHeading(AndroidCompassProvider(context), ComputeTrueHeadingUseCase(NoDeclination), fix)
                CompassElevationStrip(
                    heading = heading,
                    elevationMeters = 100.0,
                    unitSystem = UnitSystem.METRIC,
                    location = LatLng(fix.lat, fix.lng),
                    showDecimalDegrees = false,
                    onToggleCoordinateFormat = {},
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        composeRule.waitForIdle()
        assertTrue("the provider registered its listener", shadow.listeners.isNotEmpty())

        val halfAngle = Math.toRadians(-30.0) / 2
        shadow.sendSensorEventToListeners(
            ShadowSensorManager.createSensorEvent(5, Sensor.TYPE_ROTATION_VECTOR).apply {
                values[2] = sin(halfAngle).toFloat()
                values[3] = cos(halfAngle).toFloat()
                values[4] = -1f
                accuracy = SensorManager.SENSOR_STATUS_ACCURACY_HIGH
            },
        )
        composeRule.mainClock.advanceTimeBy(2_000)
        composeRule.waitForIdle()

        val reading = heading.value
        val engine = TrueHeadingCompassEngine().apply { update(reading) }
        val strip = composeRule.onAllNodesWithTag(COMPASS_STRIP_HEADING_TAG).fetchSemanticsNodes()
            .flatMap { it.config.getOrElse(SemanticsProperties.Text) { emptyList() } }
            .joinToString(" | ") { it.text }
        return Readers(
            heading = reading,
            mapBearing = engine.getLastHeading(),
            hud = navigationReadout(reading, fix, north, DistanceUnit.MILES, t + 1_000L),
            stripText = strip,
        )
    }

    private fun assertReaders(readers: Readers, degrees: Float, text: String, needle: Float) {
        assertEquals("the heading", degrees, (readers.heading as TrueHeadingReading.Available).degrees, 0.05f)
        assertEquals("the map's bearing and the puck's cone (handed to the map library)", degrees, readers.mapBearing, 0.05f)
        assertEquals("the navigation display's heading", text, readers.hud.headingText)
        assertEquals("the navigation display's north arrow", -degrees, readers.hud.northArrowDegrees!!, 0.05f)
        assertEquals("the navigation display's turn needle", needle, readers.hud.targetArrowDegrees!!, 0.05f)
        assertEquals("the compass strip's heading", text, readers.stripText)
    }

    @Test
    fun `landscape at ROTATION_90 - every reader takes the heading of the screen's top`() {
        assertReaders(readersAt(Surface.ROTATION_90), degrees = 120f, text = "120° SE", needle = 240f)
    }

    @Test
    fun `reverse landscape at ROTATION_270 - every reader takes the heading of the screen's top`() {
        assertReaders(readersAt(Surface.ROTATION_270), degrees = 300f, text = "300° NW", needle = 60f)
    }
}
