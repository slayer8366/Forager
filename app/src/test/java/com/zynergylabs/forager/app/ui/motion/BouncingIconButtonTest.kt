package com.zynergylabs.forager.app.ui.motion

import android.app.Application
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.test.core.app.ApplicationProvider
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

/**
 * Amendment 1 to motion Part 1 (RECORD -657; the owner: "Every icon button"). Every `IconButton` call in the app goes through
 * [BouncingIconButton]; this drives it under the app's theme with a real finger: the icon dips while pressed and not with the
 * phone's animations off, and the tap lands either way. The call sites themselves are not each driven here.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class BouncingIconButtonTest {

    private val composeRule = createAndroidComposeRule<ComponentActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(layoutFixesHostActivityRule()).around(composeRule)

    private val resolver get() = ApplicationProvider.getApplicationContext<Application>().contentResolver
    private var clicks = 0

    @After
    fun animationsBackOn() {
        Settings.Global.putFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
    }

    private fun show() {
        composeRule.setContent {
            ForagerTheme {
                BouncingIconButton(onClick = { clicks++ }, modifier = Modifier.testTag("button")) {
                    Icon(Icons.Filled.Search, contentDescription = "Search")
                }
            }
        }
        composeRule.waitForIdle()
    }

    private fun scale(): Float =
        composeRule.onNode(SemanticsMatcher.keyIsDefined(PressBounceScaleKey) and hasAnyAncestor(hasTestTag("button")), useUnmergedTree = true)
            .fetchSemanticsNode().config[PressBounceScaleKey]

    private fun pressHoldRelease(): Float {
        composeRule.onNodeWithTag("button").performTouchInput { down(center) }
        composeRule.mainClock.advanceTimeBy(300)
        composeRule.waitForIdle()
        val held = scale()
        composeRule.onNodeWithTag("button").performTouchInput { up() }
        composeRule.mainClock.advanceTimeBy(1_000)
        composeRule.waitForIdle()
        return held
    }

    @Test
    fun `the icon dips while a finger is down and the tap lands`() {
        show()
        val held = pressHoldRelease()
        assertTrue("dipped while held: $held", held < 0.97f)
        assertEquals("back at rest", 1f, scale(), 0.001f)
        assertEquals("the tap landed", 1, clicks)
    }

    @Test
    fun `with the phone's animations off the icon does not dip and the tap still lands`() {
        Settings.Global.putFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 0f)
        show()
        assertEquals("no dip", 1f, pressHoldRelease(), 0.001f)
        assertEquals("the tap landed", 1, clicks)
    }
}
