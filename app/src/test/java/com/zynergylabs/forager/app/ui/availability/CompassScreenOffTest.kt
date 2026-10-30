package com.zynergylabs.forager.app.ui.availability

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorManager
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ApplicationProvider
import com.zynergylabs.forager.app.domain.CompassProvider
import com.zynergylabs.forager.app.domain.CompassReading
import com.zynergylabs.forager.app.domain.ComputeTrueHeadingUseCase
import com.zynergylabs.forager.app.domain.CurrentTimeProvider
import com.zynergylabs.forager.app.domain.DeclinationProvider
import com.zynergylabs.forager.app.domain.FixProvider
import com.zynergylabs.forager.app.domain.HeadingUncertainty
import com.zynergylabs.forager.app.domain.LocationFix
import com.zynergylabs.forager.app.domain.model.LatLng
import com.zynergylabs.forager.app.domain.model.UnitSystem
import com.zynergylabs.forager.app.sensor.AndroidCompassProvider
import com.zynergylabs.forager.app.ui.map.MapSlot
import com.zynergylabs.forager.app.ui.map.TrueHeadingReading
import com.zynergylabs.forager.app.ui.map.rememberTrueHeading
import com.zynergylabs.forager.app.ui.theme.ForagerTheme
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowSensor
import org.robolectric.shadows.ShadowSensorManager
import kotlin.math.cos
import kotlin.math.sin

/**
 * Dispatch 2026-10-30-14 (RECORD -1857, -1858): the compass is read only while the app is on screen. Screen off stops the
 * activity, and the real lifecycle is driven here the same way: `ActivityScenario.moveToState(CREATED)` runs the activity's own
 * `onStop`, and `RESUMED` its `onStart` and `onResume`.
 *
 * Most tests use the real [AndroidCompassProvider] over Robolectric's sensor manager, so "the compass stopped" is read where it
 * happens: the provider's listener is registered with the sensor manager or it is not. The last test composes the real
 * [AvailabilityScreen] on the Maps tab, so the claim also holds at the production call site, not only in a test host.
 *
 * The owner's answers (Amendment 1): back within 10 s keeps the last heading, as on a tab return (RECORD -761); after longer the
 * strip shows "—" until the compass reports; and a compass that was untrusted must hold good afresh after the gap.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w360dp-h640dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class CompassScreenOffTest {

    private val composeRule = createAndroidComposeRule<ComponentActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(layoutFixesHostActivityRule()).around(composeRule)

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val shadow: ShadowSensorManager = Shadows.shadowOf(context.getSystemService(SensorManager::class.java))

    private val t = 1_700_000_000_000L
    private val fix = LocationFix.Update(lat = 45.52, lng = -122.68, altitude = 50.0, accuracyMeters = 5f, timestampEpochMillis = t, provider = FixProvider.GPS)

    /** The clock `rememberTrueHeading` measures the screen-off gap with. */
    private var now = t

    private object NoDeclination : DeclinationProvider {
        override fun declinationDegrees(latitude: Double, longitude: Double, altitudeMeters: Double?, epochMillis: Long): Float = 0f
    }

    private lateinit var heading: State<TrueHeadingReading>

    /** The fix the host hands `rememberTrueHeading`; a test that loses and regains the fix sets it. */
    private val liveFix: MutableState<LocationFix.Update?> = mutableStateOf(fix)

    /** The real provider feeding the real `rememberTrueHeading`, and the real strip reading it. */
    private fun setHost() {
        shadow.addSensor(Sensor.TYPE_ROTATION_VECTOR, ShadowSensor.newInstance(Sensor.TYPE_ROTATION_VECTOR))
        composeRule.setContent {
            ForagerTheme {
                heading = rememberTrueHeading(AndroidCompassProvider(context), ComputeTrueHeadingUseCase(NoDeclination), liveFix, null, { now })
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
        assertEquals("one compass listener while on screen", 1, shadow.listeners.size)
    }

    /**
     * A rotation-vector reading with the phone flat and the screen's top at [degrees] (the display is at ROTATION_0 here), at
     * [atMillis] on the sensor's clock, with the sensor's own heading accuracy [accuracyDegrees].
     */
    private fun send(degrees: Float, atMillis: Long = 0L, accuracyDegrees: Float = 2f) {
        val halfAngle = Math.toRadians(-degrees.toDouble()) / 2
        shadow.sendSensorEventToListeners(
            ShadowSensorManager.createSensorEvent(5, Sensor.TYPE_ROTATION_VECTOR).apply {
                values[2] = sin(halfAngle).toFloat()
                values[3] = cos(halfAngle).toFloat()
                values[4] = Math.toRadians(accuracyDegrees.toDouble()).toFloat()
                accuracy = SensorManager.SENSOR_STATUS_ACCURACY_HIGH
                timestamp = atMillis * 1_000_000L
            },
        )
        composeRule.waitForIdle()
    }

    private fun screenOff() {
        composeRule.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
        composeRule.waitForIdle()
    }

    private fun screenOn() {
        composeRule.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        composeRule.waitForIdle()
    }

    private fun stripText(): String =
        composeRule.onAllNodesWithTag(COMPASS_STRIP_HEADING_TAG).fetchSemanticsNodes()
            .flatMap { it.config.getOrElse(SemanticsProperties.Text) { emptyList() } }
            .joinToString(" | ") { it.text }

    private fun assertHeading(message: String, degrees: Float) {
        val reading = heading.value
        assertTrue("$message: expected a heading, got $reading", reading is TrueHeadingReading.Available)
        assertEquals(message, degrees, (reading as TrueHeadingReading.Available).degrees, 0.05f)
    }

    @Test
    fun `screen off unregisters the compass listener, and screen on registers exactly one again`() {
        setHost()

        screenOff()
        assertEquals("compass listener still registered while the screen is off", 0, shadow.listeners.size)

        screenOn()
        assertEquals("exactly one compass listener once the screen is back on", 1, shadow.listeners.size)
    }

    @Test
    fun `a reading while the screen is off reaches nothing`() {
        setHost()
        send(30f)
        assertHeading("before the screen goes off", 30f)

        screenOff()
        send(200f)
        now += 5_000L
        screenOn()

        assertHeading("a reading sent while the screen was off was taken", 30f)
    }

    @Test
    fun `back within 10 s, the last heading stays until the compass reports`() {
        setHost()
        send(30f)

        screenOff()
        now += 10_000L
        screenOn()

        assertHeading("back after exactly 10 s", 30f)
        assertEquals("30° NE", stripText())
    }

    @Test
    fun `back after more than 10 s, the strip shows a dash until the compass reports, then that reading`() {
        setHost()
        send(30f)

        screenOff()
        now += 10_001L
        screenOn()

        assertEquals("back after 10 001 ms: a stale heading is still shown", TrueHeadingReading.NeedsFix, heading.value)
        assertEquals(NO_HEADING_TEXT, stripText())

        send(90f)
        assertHeading("the first reading back", 90f)
    }

    @Test
    fun `the first reading after the screen comes back is shown as it is, not blended with the one before`() {
        setHost()
        send(30f)

        screenOff()
        now += 2_000L
        screenOn()
        send(90f)

        // A smoother kept across the gap would give about 48° here (0.3 of the way from 30° to 90° on the unit circle).
        assertHeading("the first reading after the gap was blended with the heading before it", 90f)
    }

    @Test
    fun `a compass untrusted when the screen went off must hold good afresh after it comes back`() {
        setHost()
        send(30f, atMillis = 0L, accuracyDegrees = 20f)
        assertEquals("untrusted at 20°", TrueHeadingReading.Unreliable, heading.value)
        send(30f, atMillis = 1_000L, accuracyDegrees = 3f)
        assertEquals("1 000 ms into the hold", TrueHeadingReading.Unreliable, heading.value)

        screenOff()
        now += 5_000L
        screenOn()

        // 60 s later on the sensor's clock: counted from 1 000 ms, the hold would clear at once.
        send(30f, atMillis = 61_000L, accuracyDegrees = 3f)
        assertEquals("the first good reading after the gap was trusted without a fresh hold", TrueHeadingReading.Unreliable, heading.value)
        send(30f, atMillis = 62_999L, accuracyDegrees = 3f)
        assertEquals("1 999 ms held since the gap", TrueHeadingReading.Unreliable, heading.value)
        send(30f, atMillis = 63_000L, accuracyDegrees = 3f)
        assertHeading("2 000 ms held since the gap", 30f)
    }

    /**
     * Sorrel's review of #450, N-1 (dispatch 2026-10-30-14 Amendment 2): paused is not stopped. Under a system dialog or in
     * split-screen the activity is STARTED but not RESUMED and still on screen, so the compass must keep turning there.
     */
    @Test
    fun `paused under a dialog or in split-screen, the compass keeps reading and the heading keeps turning`() {
        setHost()

        composeRule.activityRule.scenario.moveToState(Lifecycle.State.STARTED)
        composeRule.waitForIdle()
        assertEquals("the compass listener was dropped while the app is paused but still on screen", 1, shadow.listeners.size)

        // The heading State is what the strip and the HUD read. The strip's text is not read here: the Compose test harness
        // finds no hierarchy in a paused activity (CI run 41135927202, "No compose hierarchies found", at the stripText call).
        send(90f)
        assertHeading("a reading while paused did not reach the heading", 90f)
    }

    /**
     * Sorrel's review of #450, N-4 (dispatch 2026-10-30-14 Amendment 2): losing and regaining the fix restarts the producer, and
     * the owner's "Yes, re-check (Recommended)" applies there too. A compass that was untrusted must hold good afresh after the
     * fix comes back, not be trusted on its first reading because a new judge was made with the restart.
     */
    @Test
    fun `a compass untrusted when the fix was lost must hold good afresh after the fix comes back`() {
        setHost()
        send(30f, atMillis = 0L, accuracyDegrees = 20f)
        assertEquals("untrusted at 20°", TrueHeadingReading.Unreliable, heading.value)
        send(30f, atMillis = 1_000L, accuracyDegrees = 3f)
        assertEquals("1 000 ms into the hold", TrueHeadingReading.Unreliable, heading.value)

        composeRule.runOnIdle { liveFix.value = null }
        composeRule.waitForIdle()
        send(30f, atMillis = 1_500L, accuracyDegrees = 3f)
        assertEquals("no fix, no true heading", TrueHeadingReading.NeedsFix, heading.value)

        composeRule.runOnIdle { liveFix.value = fix }
        composeRule.waitForIdle()
        assertEquals("one compass listener once the fix is back", 1, shadow.listeners.size)

        // 60 s later on the sensor's clock: counted from 1 000 ms, the hold would clear at once.
        send(30f, atMillis = 61_000L, accuracyDegrees = 3f)
        assertEquals("the first good reading after the fix came back was trusted without a fresh hold", TrueHeadingReading.Unreliable, heading.value)
        send(30f, atMillis = 62_999L, accuracyDegrees = 3f)
        assertEquals("1 999 ms held since the fix came back", TrueHeadingReading.Unreliable, heading.value)
        send(30f, atMillis = 63_000L, accuracyDegrees = 3f)
        assertHeading("2 000 ms held since the fix came back", 30f)
    }

    /** A compass whose collectors can be counted, and which reports only when told to. */
    private class CountingCompass : CompassProvider {
        val readings = MutableSharedFlow<CompassReading?>(extraBufferCapacity = 8)
        override val heading: Flow<CompassReading?> = readings
        fun report(degrees: Float) {
            check(readings.tryEmit(CompassReading(degrees, HeadingUncertainty.Estimated(2f), 0L)))
        }
    }

    @Test
    fun `the Maps tab of the real screen stops reading the compass while the screen is off, and keeps the 10 s rule`() {
        val compass = CountingCompass()
        val map: MapSlot = { _, _, _, _, _, _, _, _, modifier -> Box(modifier.testTag("compass-screen-off-map")) }
        composeRule.setContent {
            AvailabilityScreen(
                uiState = AvailabilityUiState(liveFix = fix),
                compassProvider = compass,
                computeTrueHeading = ComputeTrueHeadingUseCase(NoDeclination),
                currentTime = CurrentTimeProvider { now },
                mapSlot = map,
                searchInputs = AvailabilityScreenSearchInputs(
                    onUseCurrentLocation = {},
                    onManualLatChanged = {},
                    onManualLngChanged = {},
                    onSearchManualCoordinates = {},
                    onRadiusChanged = {},
                    onMonthSelected = {},
                    onMapTabSelected = {},
                    onSeasonalTabSelected = {},
                    onTaxonSearchQueryChanged = {},
                    onTaxonSearchResultSelected = {},
                    onDismissTaxonSuggestions = {},
                    onReopenTaxonSuggestions = {},
                    onPlaceTripPin = { _, _, _ -> },
                    onDeletePlannedTrip = {},
                    onRecentSearchSelected = {},
                ),
                offlineMaps = AvailabilityScreenOfflineMaps(
                    onOfflineMapLatChanged = {},
                    onOfflineMapLngChanged = {},
                    onOfflineMapRadiusChanged = {},
                    onOfflineMapNameChanged = {},
                    onOfflineMapsOpened = {},
                    onDownloadOfflineMaps = {},
                    onDeleteOfflineRegion = {},
                ),
                settings = AvailabilityScreenSettings(
                    onNightModeMapsChanged = {},
                    onThemeModeChanged = {},
                ),
            )
        }
        composeRule.waitForIdle()
        assertEquals("the Maps tab reads the compass while on screen", 1, compass.readings.subscriptionCount.value)
        composeRule.runOnIdle { compass.report(30f) }
        composeRule.waitForIdle()
        assertEquals("30° NE", stripText())

        screenOff()
        assertEquals("the Maps tab still reads the compass while the screen is off", 0, compass.readings.subscriptionCount.value)

        now += 10_001L
        screenOn()
        assertEquals("the Maps tab reads the compass again once back on", 1, compass.readings.subscriptionCount.value)
        assertEquals("back after 10 001 ms, the strip still shows the old heading", NO_HEADING_TEXT, stripText())

        composeRule.runOnIdle { compass.report(90f) }
        composeRule.waitForIdle()
        assertEquals("90° E", stripText())
    }
}
