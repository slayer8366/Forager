package com.forager.app.ui.map

import android.app.Application
import android.content.ComponentName
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.forager.app.domain.model.LatLng
import com.forager.app.domain.model.Region
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
