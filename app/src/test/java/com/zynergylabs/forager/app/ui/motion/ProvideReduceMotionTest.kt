package com.zynergylabs.forager.app.ui.motion

import android.app.Application
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.core.app.ApplicationProvider
import com.zynergylabs.forager.app.ui.availability.layoutFixesHostActivityRule
import com.zynergylabs.forager.app.ui.theme.ForagerTheme
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Motion Part 1, item 1 (dispatch 2026-09-28-652; the owner, RECORD -651: "Yes, follow it"): the phone's reduce-motion setting
 * reaches every screen through the app's theme, read from its real source, `Settings.Global`, not handed in by the test.
 * [ForagerTheme] is what `MainActivity` wraps the whole app in, so a composable under it sees what the app sees.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ProvideReduceMotionTest {

    private val composeRule = createAndroidComposeRule<ComponentActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(layoutFixesHostActivityRule()).around(composeRule)

    private val resolver get() = ApplicationProvider.getApplicationContext<Application>().contentResolver

    private fun put(name: String, scale: Float) = Settings.Global.putFloat(resolver, name, scale)

    @After
    fun animationsBackOn() {
        put(Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
        put(Settings.Global.TRANSITION_ANIMATION_SCALE, 1f)
    }

    /** What a screen under the app's theme reads, after the composition has settled. */
    private var seen: Boolean? = null

    private fun showUnderTheTheme() {
        composeRule.setContent { ForagerTheme { seen = LocalReduceMotion.current } }
        composeRule.waitForIdle()
    }

    @Test
    fun `with the phone's animations on, the app reads no reduced motion`() {
        put(Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
        put(Settings.Global.TRANSITION_ANIMATION_SCALE, 1f)
        showUnderTheTheme()
        assertEquals(false, seen)
    }

    @Test
    fun `with the animator scale at 0, the app reads reduced motion`() {
        put(Settings.Global.ANIMATOR_DURATION_SCALE, 0f)
        showUnderTheTheme()
        assertEquals(true, seen)
    }

    @Test
    fun `with the transition scale at 0 alone, the app reads reduced motion`() {
        put(Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
        put(Settings.Global.TRANSITION_ANIMATION_SCALE, 0f)
        showUnderTheTheme()
        assertEquals(true, seen)
    }

    /**
     * Turned off in the system Settings while the app runs: the screens follow without a restart. The system's settings provider
     * notifies observers of the changed setting's URI; Robolectric's `putFloat` is not known to, so the test sends that
     * notification itself, as the provider would. What is under test is that the app listens for it.
     */
    @Test
    fun `a change to the setting while the app runs reaches the screens`() {
        put(Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
        showUnderTheTheme()
        assertEquals("before the change", false, seen)

        put(Settings.Global.ANIMATOR_DURATION_SCALE, 0f)
        resolver.notifyChange(Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE), null)
        composeRule.waitForIdle()
        assertEquals("after the change", true, seen)

        put(Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
        resolver.notifyChange(Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE), null)
        composeRule.waitForIdle()
        assertEquals("after turning it back on", false, seen)
    }
}
