package com.zynergylabs.forager.app.ui.map

import android.app.Application
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.test.core.app.ApplicationProvider
import com.zynergylabs.forager.app.domain.model.LatLng
import com.zynergylabs.forager.app.domain.model.Region
import com.zynergylabs.forager.app.ui.availability.layoutFixesHostActivityRule
import com.zynergylabs.forager.app.ui.theme.ForagerTheme
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * RECORD -761 (the owner: "Picture cover, this build (Recommended)"), dispatch 2026-09-28-755 item 4. A real MapView cannot run
 * under Robolectric, so the two parts that can are tested here: when a returning map shows the cover ([coverDecision]), and the
 * cover itself ([MapReturnCoverLayer]) over a stand-in for the map that records every touch it gets: real touches at screen
 * coordinates on the cover reach it, the cover goes on hand-over with a fade, and at once under reduced motion. That the map
 * takes its picture and hands over at its first full frame is the S22's (the report).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w360dp-h780dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class MapReturnCoverTest {

    private val composeRule = createAndroidComposeRule<ComponentActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(layoutFixesHostActivityRule()).around(composeRule)

    private val resolver get() = ApplicationProvider.getApplicationContext<Application>().contentResolver

    @After
    fun restore() {
        composeRule.mainClock.autoAdvance = true
        Settings.Global.putFloat(resolver, Settings.Global.TRANSITION_ANIMATION_SCALE, 1f)
    }

    // ── When a returning map shows the cover ──

    private val region = Region(lat = 45.326, lng = -122.634, radiusKm = 15)
    private val camera = MapCameraSnapshot(
        target = LatLng(45.326, -122.634), zoom = 12.0, bearing = 0.0, tilt = 0.0, following = false,
        appliedTarget = region to null,
    )

    private fun decide(
        picture: MapCameraSnapshot? = camera,
        saved: MapCameraSnapshot? = camera,
        target: Pair<Region, LatLng?> = region to null,
        frameId: Int = 0,
        appliedFrameId: Int = 0,
        request: Boolean = false,
    ) = coverDecision(picture, saved, target, frameId, appliedFrameId, request)

    @Test
    fun `the cover shows when the picture is of the camera the map restores and nothing will move it`() {
        assertEquals(CoverDecision.SHOW, decide())
        // A following camera creeps with the puck: a few metres is the same picture to the eye.
        assertEquals(CoverDecision.SHOW, decide(saved = camera.copy(target = LatLng(45.32602, -122.63402), following = true)))
    }

    @Test
    fun `no picture, no camera, a moved camera, or a move pending each fall back to the map drawing itself`() {
        assertEquals(CoverDecision.NO_PICTURE, decide(picture = null))
        assertEquals(CoverDecision.NO_CAMERA, decide(saved = null))
        assertEquals("another zoom", CoverDecision.CAMERA_MOVED, decide(saved = camera.copy(zoom = 13.0)))
        assertEquals("another place", CoverDecision.CAMERA_MOVED, decide(saved = camera.copy(target = LatLng(45.40, -122.634))))
        assertEquals("turned", CoverDecision.CAMERA_MOVED, decide(saved = camera.copy(bearing = 40.0)))
        assertEquals("a search frame to fly to", CoverDecision.MOVE_PENDING, decide(frameId = 3, appliedFrameId = 2))
        assertEquals("a new search region", CoverDecision.MOVE_PENDING, decide(target = Region(lat = -41.29, lng = 174.78, radiusKm = 15) to null))
        assertEquals("a locate target", CoverDecision.MOVE_PENDING, decide(target = region to LatLng(45.5, -122.5)))
        assertEquals("a camera request", CoverDecision.MOVE_PENDING, decide(request = true))
    }

    // ── The cover itself ──

    private val taps = mutableListOf<Offset>()
    private var drags = 0
    private val handedOver = mutableStateOf(false)
    private var goneCalls = 0

    private fun setCover() {
        val picture = ImageBitmap(360, 780)
        composeRule.setContent {
            ForagerTheme(darkTheme = false) {
                Box(Modifier.fillMaxSize()) {
                    // The map's stand-in: records every tap and drag that reaches it.
                    Box(
                        Modifier.fillMaxSize().testTag("stand-in-map")
                            .pointerInput(Unit) { detectTapGestures { taps += it } }
                            .pointerInput(Unit) { detectDragGestures(onDragEnd = { drags++ }) { _, _ -> } },
                    )
                    MapReturnCoverLayer(picture, handedOver = handedOver.value, onGone = { goneCalls++ })
                }
            }
        }
        composeRule.waitForIdle()
    }

    private fun coverShown() = composeRule.onAllNodes(androidx.compose.ui.test.hasTestTag(MAP_RETURN_COVER_TAG), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

    @Test
    fun `real touches on the cover reach the map beneath, taps and a drag alike`() {
        setCover()
        assertTrue("positive control: the cover is up", coverShown())
        val fractions = listOf(Offset(0.5f, 0.5f), Offset(0.1f, 0.1f), Offset(0.9f, 0.1f), Offset(0.1f, 0.9f), Offset(0.9f, 0.9f))
        fractions.forEachIndexed { i, f ->
            composeRule.onRoot().performTouchInput { click(Offset(width * f.x, height * f.y)) }
            composeRule.mainClock.advanceTimeBy(500)
            composeRule.waitForIdle()
            assertEquals("the tap at $f reached the map", i + 1, taps.size)
        }
        composeRule.onRoot().performTouchInput { swipe(Offset(width * 0.3f, height * 0.5f), Offset(width * 0.7f, height * 0.6f), 300) }
        composeRule.waitForIdle()
        assertEquals("the drag reached the map", 1, drags)
        assertTrue("the cover is still up: it took none of them", coverShown())
    }

    @Test
    fun `on hand-over the cover fades out over several frames, then is gone and released`() {
        setCover()
        composeRule.mainClock.autoAdvance = false
        composeRule.runOnUiThread {
            handedOver.value = true
            androidx.compose.runtime.snapshots.Snapshot.sendApplyNotifications()
        }
        composeRule.mainClock.advanceTimeByFrame()
        composeRule.mainClock.advanceTimeByFrame()
        assertTrue("still drawn a moment after hand-over (the fade)", coverShown())
        composeRule.mainClock.advanceTimeBy(1_000)
        composeRule.waitForIdle()
        assertTrue("gone after the fade", !coverShown())
        assertEquals("released once", 1, goneCalls)
    }

    @Test
    fun `under reduced motion the cover is cut at hand-over`() {
        Settings.Global.putFloat(resolver, Settings.Global.TRANSITION_ANIMATION_SCALE, 0f)
        setCover()
        composeRule.mainClock.autoAdvance = false
        composeRule.runOnUiThread {
            handedOver.value = true
            androidx.compose.runtime.snapshots.Snapshot.sendApplyNotifications()
        }
        composeRule.mainClock.advanceTimeByFrame()
        assertTrue("gone on the first frame after hand-over", !coverShown())
        composeRule.mainClock.advanceTimeByFrame()
        assertEquals(1, goneCalls)
    }
}
