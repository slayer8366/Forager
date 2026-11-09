package com.zynergylabs.forager.app.ui.map

import android.app.Application
import android.content.ComponentName
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.test.core.app.ApplicationProvider
import com.zynergylabs.forager.app.domain.model.Region
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.Shadows
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLog

/**
 * Dispatch 2026-10-18-04, Amendment 1 (RECORD -1283 (2), -1284): a failed MapLibre start-up shows "The map couldn't
 * start." and "Try again" in the map's place, instead of throwing during composition.
 *
 * Under Robolectric MapLibre's native library cannot load, so the real start-up always fails. The first two tests use
 * that: they compose the real [SightingsMapSlot], which every map in the app is drawn through, with the real
 * initialiser. A later success cannot happen under Robolectric, so the last two compose [MapStartGate] with a start-up
 * that fails and then succeeds, and a stand-in for the map.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class MapStartGateTest {
    private val composeRule = createComposeRule()

    // The compose rule's host activity is not in the app's manifest; declared here as WindowClassFollowsTheWindowTest does.
    private val declareHostActivity = object : ExternalResource() {
        override fun before() {
            val app = ApplicationProvider.getApplicationContext<Application>()
            Shadows.shadowOf(app.packageManager).addActivityIfNotPresent(ComponentName(app, ComponentActivity::class.java))
        }
    }

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(declareHostActivity).around(composeRule)

    private fun startFailuresLogged() =
        ShadowLog.getLogs().count { it.tag == "MapLibreStorage" && it.msg?.startsWith("MapLibre initialisation failed") == true }

    private fun composeRealMap() = composeRule.setContent {
        SightingsMapSlot(
            Region(lat = 45.5, lng = -122.6, radiusKm = 5),
            MapOverlayContent(),
            MapRenderMode(basemap = Basemap.OSM_STANDARD),
            null,
            {},
            {},
            { _, _, _ -> },
            {},
            Modifier.fillMaxSize(),
        )
    }

    @Test
    fun `the real map, when MapLibre cannot start, shows the message and Try again instead of throwing`() {
        composeRealMap()

        composeRule.onNodeWithText("The map couldn't start.").assertExists()
        composeRule.onNodeWithText("Try again").assertExists()
    }

    @Test
    fun `Try again on the real map retries MapLibre's start-up, logged, and the message stays while it still fails`() {
        composeRealMap()
        val before = startFailuresLogged()

        composeRule.onNodeWithText("Try again").performTouchInput { click() }
        composeRule.waitForIdle()

        assertEquals("Try again did not retry the start-up", before + 1, startFailuresLogged())
        composeRule.onNodeWithText("The map couldn't start.").assertExists()
    }

    @Test
    fun `a start-up that succeeds on Try again shows the map`() {
        var calls = 0
        composeRule.setContent {
            MapStartGate(Modifier.fillMaxSize(), start = { if (calls++ == 0) throw UnsatisfiedLinkError("no native library") }) {
                Text("the map")
            }
        }
        composeRule.onNodeWithText("the map").assertDoesNotExist()

        composeRule.onNodeWithText("Try again").performTouchInput { click() }
        composeRule.waitForIdle()

        composeRule.onNodeWithText("the map").assertExists()
        composeRule.onNodeWithText("The map couldn't start.").assertDoesNotExist()
        assertEquals(2, calls)
    }

    @Test
    fun `a start-up that succeeds first time shows the map with no message, starting once`() {
        var calls = 0
        composeRule.setContent {
            MapStartGate(Modifier.fillMaxSize(), start = { calls++ }) { Text("the map") }
        }

        composeRule.onNodeWithText("the map").assertExists()
        composeRule.onNodeWithText("The map couldn't start.").assertDoesNotExist()
        assertEquals(1, calls)
    }
}
