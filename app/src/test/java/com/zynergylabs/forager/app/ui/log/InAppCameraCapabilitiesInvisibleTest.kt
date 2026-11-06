package com.zynergylabs.forager.app.ui.log

import android.app.Application
import android.content.ComponentName
import android.view.View
import android.view.Window
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.core.app.ApplicationProvider
import com.zynergylabs.forager.app.domain.CameraCapabilityFixtures
import com.zynergylabs.forager.app.domain.GridMode
import com.zynergylabs.forager.app.photo.CameraCaptureFiles
import com.zynergylabs.forager.app.photo.CameraSessionState
import com.zynergylabs.forager.app.photo.FakeCameraCaptureSession
import com.zynergylabs.forager.app.photo.FileProviderCacheReset
import com.zynergylabs.forager.app.sensor.FakeLevelProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config

/**
 * RAW stage 1 changes nothing a user sees (dispatch 2026-10-13-04): the camera screen is the same
 * before the capability probe lands, after it lands reporting everything supported, and after it
 * lands reporting nothing supported. The capabilities are set after composition, as the real bind
 * and the real background encoder read deliver them, on a fake session the real dialog opens.
 *
 * "The same" is the screen's semantics tree, node by node: tags, text, descriptions, state, role
 * and bounds.
 *
 * ## A check that passes before and after by design
 *
 * Stage 1 adds no UI, so this passes on the commit before the probe existed as well as after it,
 * which CLAUDE.md calls suspect. What makes it evidence is a positive control: with a temporary
 * edit that shows something on the screen when RAW is supported, it must fail. That control's run
 * is recorded in the PR. Its job from here on is to fail the first time a later stage puts a
 * capability on the screen, so that change is made on purpose and this test is updated with it.
 *
 * ## Updated by RAW stage 2, on purpose (dispatch 2026-10-15-01; RECORD -1002, "Show it now")
 *
 * Stage 2 is that first time: where the probe reports RAW, the format chip appears. So "everything supported" now
 * checks that the format chip is the one thing added, by test tag, and nothing else on the screen is gone; "nothing
 * supported" and "the encoders alone" still compare the whole tree. The chip's own behaviour is `CameraFormatChipTest`.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class InAppCameraCapabilitiesInvisibleTest {

    private val composeRule = createAndroidComposeRule<ComponentActivity>()

    /** As in `InAppCameraDialogTest`: nothing registers `ComponentActivity`, so the rule could not launch its host. */
    private val declareHostActivity = object : ExternalResource() {
        override fun before() {
            val app = ApplicationProvider.getApplicationContext<Application>()
            Shadows.shadowOf(app.packageManager)
                .addActivityIfNotPresent(ComponentName(app, ComponentActivity::class.java))
        }
    }

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(declareHostActivity).around(FileProviderCacheReset()).around(composeRule)

    private val context: Application get() = ApplicationProvider.getApplicationContext()

    /** Every node of the screen, depth-indented, with what a user or an accessibility service can see of it. */
    private fun screen(): List<String> {
        val lines = mutableListOf<String>()
        fun walk(node: SemanticsNode, depth: Int) {
            val config = node.config
            val seen = listOf(
                SemanticsProperties.TestTag,
                SemanticsProperties.Text,
                SemanticsProperties.EditableText,
                SemanticsProperties.ContentDescription,
                SemanticsProperties.StateDescription,
                SemanticsProperties.Role,
                SemanticsProperties.ToggleableState,
                SemanticsProperties.Selected,
                SemanticsProperties.Disabled,
            ).mapNotNull { key -> config.getOrNull(key)?.let { "${key.name}=$it" } }
            lines += "  ".repeat(depth) + seen.joinToString(" ") + " @" + node.boundsInRoot
            node.children.forEach { walk(it, depth + 1) }
        }
        walk(composeRule.onRoot(useUnmergedTree = true).fetchSemanticsNode(), 0)
        return lines
    }

    @Test
    fun `the camera screen is the same whatever the probe reports`() {
        val session = FakeCameraCaptureSession(state = CameraSessionState.Ready)
        composeRule.setContent {
            InAppCameraDialog(
                session = session,
                cameraCaptureFiles = CameraCaptureFiles(context),
                onPhotoCaptured = {},
                onDismiss = {},
                gridMode = GridMode.Off,
                onGridModeChanged = {},
                autoSaveLocationToPhotos = true,
                onAutoSaveLocationToPhotosChanged = {},
                levelProvider = FakeLevelProvider(),
                statusBarHider = object : StatusBarHider {
                    override fun hide(window: Window, view: View) = Unit
                    override fun show(window: Window, view: View) = Unit
                },
                viewfinder = { modifier -> Box(modifier.fillMaxSize()) },
            )
        }
        composeRule.waitForIdle()
        val beforeProbe = screen()
        // Not an empty comparison: the screen is up, shutter and all.
        assertTrue("the camera screen is composed", beforeProbe.any { "TestTag=$CAMERA_SHUTTER_TAG" in it })

        session.encoderCapabilities = CameraCapabilityFixtures.everyEncoder
        composeRule.waitForIdle()
        assertEquals("the encoders alone change nothing", beforeProbe, screen())

        session.capabilities = CameraCapabilityFixtures.everythingSupported
        composeRule.waitForIdle()
        assertEquals(
            "everything supported adds the format chip and nothing else (RAW stage 2, RECORD -1002)",
            testTags(beforeProbe) + CAMERA_FORMAT_CHIP_TAG,
            testTags(screen()),
        )

        session.capabilities = CameraCapabilityFixtures.nothingSupported
        session.encoderCapabilities = CameraCapabilityFixtures.noEncoder
        composeRule.waitForIdle()
        assertEquals("nothing supported", beforeProbe, screen())
    }

    private fun testTags(lines: List<String>): Set<String> =
        lines.mapNotNull { line -> Regex("TestTag=(\\S+)").find(line)?.groupValues?.get(1) }.toSet()
}
