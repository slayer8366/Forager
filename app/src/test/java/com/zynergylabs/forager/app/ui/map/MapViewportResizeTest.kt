package com.zynergylabs.forager.app.ui.map

import android.app.Application
import android.content.ComponentName
import android.content.pm.ActivityInfo
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows
import org.robolectric.annotation.Config

/**
 * Part 2 finding (a), item 5: an open bubble stayed at its portrait place after a device rotation and
 * overlapped the cluster until the next pan (`docs/audits/2026-09-29-stage-device-check-part-2-run-record.md`,
 * Session 1, item 5, "fail on rotation"). The map re-anchors a bubble to its glyph at each camera idle
 * (`SightingsMap`'s `addOnCameraIdleListener`), and a rotation resizes the map view without any camera
 * idle, so nothing re-anchored.
 *
 * What is testable here is the trigger: [onViewportResized], which `SightingsMap` puts on its map
 * container and reacts to by re-anchoring, fires on a real configuration change. The host declares the
 * same `configChanges` `MainActivity` does (orientation|screenSize|screenLayout|smallestScreenSize), so
 * the rotation reaches the same Activity instead of recreating it, as on the device. **Not testable
 * under Robolectric:** the re-projection through a real `MapView` (it cannot be constructed here) and
 * whether the bubble then sits on its glyph: device-only (the report's device list).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w360dp-h640dp-xhdpi")
class MapViewportResizeTest {

    private val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val declareHostActivity = object : ExternalResource() {
        override fun before() {
            val app = ApplicationProvider.getApplicationContext<Application>()
            val info = ActivityInfo().apply {
                packageName = app.packageName
                name = ComponentActivity::class.java.name
                configChanges = ActivityInfo.CONFIG_ORIENTATION or ActivityInfo.CONFIG_SCREEN_SIZE or
                    ActivityInfo.CONFIG_SCREEN_LAYOUT or ActivityInfo.CONFIG_SMALLEST_SCREEN_SIZE or ActivityInfo.CONFIG_KEYBOARD_HIDDEN
            }
            Shadows.shadowOf(app.packageManager).addOrUpdateActivity(info)
        }
    }

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(declareHostActivity).around(composeRule)

    private val resizes = mutableListOf<IntSize>()

    private fun setContent() {
        composeRule.setContent {
            Box(Modifier.fillMaxSize().onViewportResized { resizes += it })
        }
        composeRule.waitForIdle()
    }

    private fun rotate(qualifier: String) {
        composeRule.activityRule.scenario.onActivity { RuntimeEnvironment.setQualifiers(qualifier) }
        composeRule.waitForIdle()
    }

    @Test
    fun `the first measurement is not a resize`() {
        setContent()

        assertEquals(emptyList<IntSize>(), resizes)
    }

    @Test
    fun `rotating from portrait to landscape reports the landscape size`() {
        setContent()

        rotate("+land")

        assertEquals("one resize, to the landscape viewport", 1, resizes.size)
        assertTrue("wider than tall after the turn: ${resizes.single()}", resizes.single().width > resizes.single().height)
    }

    @Test
    fun `rotating back from landscape to portrait reports the portrait size`() {
        setContent()
        rotate("+land")
        resizes.clear()

        rotate("+port")

        assertEquals("one resize, back to the portrait viewport", 1, resizes.size)
        assertTrue("taller than wide after the turn back: ${resizes.single()}", resizes.single().height > resizes.single().width)
    }

    @Test
    fun `a recomposition with the same size reports nothing`() {
        val tick = mutableStateOf(0)
        composeRule.setContent {
            tick.value
            Box(Modifier.fillMaxSize().onViewportResized { resizes += it })
        }
        composeRule.waitForIdle()

        composeRule.runOnIdle { tick.value = 1 }
        composeRule.waitForIdle()

        assertEquals(emptyList<IntSize>(), resizes)
        assertNotEquals("the harness ran a recomposition", 0, tick.value)
    }
}
