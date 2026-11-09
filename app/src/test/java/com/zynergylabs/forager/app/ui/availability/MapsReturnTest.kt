package com.zynergylabs.forager.app.ui.availability

import androidx.activity.ComponentActivity
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performTouchInput
import com.zynergylabs.forager.app.domain.model.LatLng
import com.zynergylabs.forager.app.ui.map.MapCameraSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * RECORD -752, item 4 of dispatch 2026-09-28-755: returning to Maps from List, the S22 showed the map blank for a moment (the
 * heading "—"), then one frame drawn hugely magnified before the right view settled. A real MapView cannot run under Robolectric,
 * so what is measured here is what the screen hands the map on the way back, frame by frame through the tab crossfade, through
 * the real [AvailabilityScreen] and ViewModel ([RealSearchScreenRig]), every nav touch real: the size the map slot is measured at
 * on every frame (never zero, never other than where it settles), and the remembered camera it is handed from its first frame.
 */
abstract class MapsReturnTests {

    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(RealSearchScreenRig.touchModeHost()).around(composeRule)

    private val rig = RealSearchScreenRig(composeRule)

    private val remembered = MapCameraSnapshot(
        target = LatLng(45.326, -122.634),
        zoom = 14.5,
        bearing = 30.0,
        tilt = 10.0,
        following = false,
        appliedTarget = null,
    )

    private fun navItem(label: String) = composeRule.onAllNodes(
        hasText(label) and hasAnyAncestor(hasTestTag(COMPACT_BOTTOM_NAV_TAG) or hasTestTag(COMPACT_NAVIGATION_RAIL_TAG)),
        useUnmergedTree = true,
    ).onFirst()

    private fun touchNav(label: String) {
        val b = navItem(label).fetchSemanticsNode().boundsInRoot
        composeRule.onAllNodes(isRoot()).onFirst().performTouchInput { click(Offset(b.center.x, b.center.y)) }
    }

    @Test
    fun `returning to Maps from List, the map is measured at its settled size on every frame and handed the remembered camera`() {
        rig.setScreen()
        assertTrue("positive control: the map is up first", rig.mapMeasures.isNotEmpty())
        val before = rig.mapMeasures.last().size
        println("MEASURED -752 item 4: map before leaving $before")
        assertTrue("positive control: not leaving while Maps shows", !rig.mapLeavingSeen)
        touchNav(CompactTab.LIST.label)
        rig.settle()
        // RECORD -761: as Maps fades out, the map is told it is leaving, which is when it takes the cover's picture.
        assertTrue("leaving Maps by a real touch tells the map it is leaving", rig.mapLeavingSeen)
        // What a map that settled writes on its camera idle (SightingsMap): the camera the user left.
        val memory = rig.mapCameraMemory ?: error("the map was handed no camera memory")
        memory.saved = remembered
        rig.mapMeasures.clear()

        val returnedAt = composeRule.mainClock.currentTime
        touchNav(CompactTab.MAP.label)
        rig.settle()
        val perFrame = rig.mapMeasures.groupBy { it.atMs - returnedAt }
        val settled = rig.mapMeasures.last().size
        println("MEASURED -752 item 4: settled $settled; measures by ms after the touch ${perFrame.map { (t, m) -> "$t:${m.map { it.size }}" }}")

        val all = rig.mapMeasures.toList()
        assertTrue("positive control: the map was measured on the way back", all.isNotEmpty())
        assertEquals("positive control: the map settles at the size it had before leaving", before, settled)
        all.forEach { m ->
            assertTrue("the map is never measured at zero (${m.size})", m.size.width > 0 && m.size.height > 0)
            assertEquals("the map is measured at its settled size on every frame of the way back", settled, m.size)
        }
        assertSame("the map coming back is handed the camera memory it left", memory, rig.mapCameraMemory)
        assertEquals("from its first measure, the camera it holds is the one the user left", remembered, all.first().camera)
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w360dp-h780dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class MapsReturnPortraitTest : MapsReturnTests()

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w780dp-h360dp-land-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class MapsReturnLandscapeTest : MapsReturnTests()
