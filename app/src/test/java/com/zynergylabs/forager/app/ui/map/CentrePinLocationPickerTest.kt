package com.zynergylabs.forager.app.ui.map

import android.app.Application
import android.content.ComponentName
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Button
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.VectorPath
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.zynergylabs.forager.app.domain.model.LatLng
import com.zynergylabs.forager.app.domain.model.Region
import com.zynergylabs.forager.app.ui.theme.MapPalette
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * [CentrePinLocationPicker] and [CentrePinLocationPickerOverlay] are the one idiom every
 * location-placing site in this app shares (see that file's own class doc comment) — this is the
 * gate the L2 dispatch asks for directly: OK reports the camera centre, Cancel reports nothing,
 * and neither fires the other's callback.
 *
 * [SightingsMap] itself can't be composed under Robolectric — see
 * [SightingsMapOverlayDataTest]'s own doc comment for why (native MapLibre calls) — so both
 * composables here are driven through a stub [MapSlot] that exposes [MapSlot.onCameraIdle]
 * directly, the same pattern every `AvailabilityScreen*Test` map-flow test already uses.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class CentrePinLocationPickerTest {

    private val composeRule = createComposeRule()

    private val declareHostActivity = object : ExternalResource() {
        override fun before() {
            val app = ApplicationProvider.getApplicationContext<Application>()
            Shadows.shadowOf(app.packageManager)
                .addActivityIfNotPresent(ComponentName(app, ComponentActivity::class.java))
        }
    }

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(declareHostActivity).around(composeRule)

    private val region = Region(lat = 45.326, lng = -122.634, radiusKm = 15)

    @Test
    fun `OK confirms the region's own centre when the map is never panned`() {
        var confirmed: LatLng? = null
        var cancelled = false
        composeRule.setContent {
            CentrePinLocationPicker(
                mapSlot = PanningStubMapSlot,
                region = region,
                basemap = Basemap.DEFAULT,
                onConfirm = { confirmed = it },
                onCancel = { cancelled = true },
            )
        }

        composeRule.onNodeWithText("OK").performClick()

        assertEquals(LatLng(region.lat, region.lng), confirmed)
        assertEquals(false, cancelled)
    }

    @Test
    fun `OK confirms the panned-to location once the map has settled there`() {
        var confirmed: LatLng? = null
        composeRule.setContent {
            CentrePinLocationPicker(
                mapSlot = PanningStubMapSlot,
                region = region,
                basemap = Basemap.DEFAULT,
                onConfirm = { confirmed = it },
                onCancel = {},
            )
        }

        composeRule.onNodeWithText("Simulate pan").performClick()
        composeRule.onNodeWithText("OK").performClick()

        assertEquals(PANNED_LOCATION, confirmed)
    }

    @Test
    fun `Cancel reports nothing, never confirming a location`() {
        var confirmed: LatLng? = null
        var cancelled = false
        composeRule.setContent {
            CentrePinLocationPicker(
                mapSlot = PanningStubMapSlot,
                region = region,
                basemap = Basemap.DEFAULT,
                onConfirm = { confirmed = it },
                onCancel = { cancelled = true },
            )
        }

        composeRule.onNodeWithText("Simulate pan").performClick()
        composeRule.onNodeWithText("Cancel").performClick()

        assertNull("Cancel must never invoke onConfirm", confirmed)
        assertEquals(true, cancelled)
    }

    @Test
    fun `overlay's OK and Cancel are each wired to their own no-argument callback`() {
        var confirmCount = 0
        var cancelCount = 0
        composeRule.setContent {
            CentrePinLocationPickerOverlay(
                onConfirm = { confirmCount++ },
                onCancel = { cancelCount++ },
            )
        }

        composeRule.onNodeWithText("OK").performClick()

        assertEquals(1, confirmCount)
        assertEquals(0, cancelCount)
    }

    @Test
    fun `overlay's Cancel does not also confirm`() {
        var confirmCount = 0
        var cancelCount = 0
        composeRule.setContent {
            CentrePinLocationPickerOverlay(
                onConfirm = { confirmCount++ },
                onCancel = { cancelCount++ },
            )
        }

        composeRule.onNodeWithText("Cancel").performClick()

        assertEquals(0, confirmCount)
        assertEquals(1, cancelCount)
    }

    /**
     * Colour build C2 (e): the overlay's pin is the palette's centre-pin colour for its `night` value,
     * with a casing in the palette's casing colour just outside the head's top edge (y 2 on the
     * 24-unit grid; 1.5dp of casing is 0.9 units, so y 1.55 is its middle). Read from the drawn pixels.
     */
    @Test
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun `the overlay's pin is the palette's centre-pin colour with its casing, day and night`() {
        var night by mutableStateOf(false)
        composeRule.setContent { CentrePinLocationPickerOverlay(onConfirm = {}, onCancel = {}, night = night) }

        assertEquals("day fill", hex(MapPalette.DAY.centrePin), hex(composeRule.centrePinFillPixel()))
        assertEquals("day casing", hex(MapPalette.DAY.casing), hex(composeRule.centrePinPixel(12f, 1.55f)))

        night = true
        composeRule.waitForIdle()
        assertEquals("night fill", hex(MapPalette.NIGHT.centrePin), hex(composeRule.centrePinFillPixel()))
        assertEquals("night casing", hex(MapPalette.NIGHT.casing), hex(composeRule.centrePinPixel(12f, 1.55f)))
    }

    @Test
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun `the picker's own pin follows its night value`() {
        composeRule.setContent {
            CentrePinLocationPicker(
                mapSlot = PanningStubMapSlot,
                region = region,
                basemap = Basemap.DEFAULT,
                night = true,
                onConfirm = {},
                onCancel = {},
            )
        }

        assertEquals(hex(MapPalette.NIGHT.centrePin), hex(composeRule.centrePinFillPixel()))
    }

    /**
     * The casing is `LocationOn`'s own outline, not an approximation: the same path data, filled and
     * stroked in the casing colour, the stroke 1.8 units wide, which is 1.5dp on each side at the pin's
     * 40dp over 24 units.
     */
    @Test
    fun `the pin's casing is LocationOn's own outline, stroked to the casing width each side`() {
        val casing = centrePinCasingVector(MapPalette.NIGHT.casing)
        val original = Icons.Filled.LocationOn.root.map { it as VectorPath }
        val cased = casing.root.map { it as VectorPath }
        assertEquals(original.map { it.pathData }, cased.map { it.pathData })
        for (path in cased) {
            assertEquals(1.8f, path.strokeLineWidth, 1e-4f)
            assertEquals(SolidColor(Color(MapPalette.NIGHT.casing)), path.stroke)
            assertEquals(SolidColor(Color(MapPalette.NIGHT.casing)), path.fill)
            assertEquals(StrokeJoin.Round, path.strokeLineJoin)
        }
        assertEquals(Icons.Filled.LocationOn.viewportWidth, casing.viewportWidth)
        assertEquals(Icons.Filled.LocationOn.viewportHeight, casing.viewportHeight)
    }

    /**
     * Dispatch 2026-10-18-03, item 1: the offline-maps screen drops OK and Cancel ("Remove both OK and Cancel and just have the
     * Download Maps option", RECORD -1269), and "every other picker that shares CentrePinConfirmActions keeps its OK and
     * Cancel; if removing them here needs a parameter, it defaults to today's behaviour". Called with its defaults, as the
     * find's location picker (CentrePinLocationPicker) calls it: OK and Cancel are there and each does its job.
     */
    @Test
    fun `CentrePinConfirmActions with its defaults keeps OK and Cancel, for every picker but the offline screen`() {
        var confirmed: LatLng? = null
        var cancelled = false
        composeRule.setContent {
            CentrePinConfirmActions(state = CentrePinState(region), onConfirm = { confirmed = it }, onCancel = { cancelled = true })
        }
        composeRule.onNodeWithText("OK").assertIsDisplayed()
        composeRule.onNodeWithText("Cancel").assertIsDisplayed()
        composeRule.onNodeWithText("OK").performClick()
        assertEquals(LatLng(region.lat, region.lng), confirmed)
        composeRule.onNodeWithText("Cancel").performClick()
        assertEquals(true, cancelled)
    }

    private fun hex(argb: Int) = "#%08X".format(argb)
}

private val PANNED_LOCATION = LatLng(45.40, -122.70)

/** Exposes [MapSlot.onCameraIdle] as a button, the same pattern every `AvailabilityScreen*FlowTest` map stub uses. */
private val PanningStubMapSlot: MapSlot = { _, _, _, _, _, _, _, onCameraIdle, modifier ->
    Column(modifier) {
        Button(onClick = { onCameraIdle(PANNED_LOCATION) }) {
            Text("Simulate pan")
        }
    }
}
