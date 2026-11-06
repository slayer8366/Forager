package com.zynergylabs.forager.app.ui.adaptive

import android.app.Application
import android.content.ComponentName
import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config

/**
 * Lint dispatch 2026-10-17-03, Phase 2 (RECORD -1195), ConfigurationScreenWidthHeight: the window classes are read from
 * the window's own size (`LocalWindowInfo.current.containerDpSize`), not from `Configuration.screenWidthDp` /
 * `screenHeightDp`, which lint says do not reliably describe the window (their inset handling depends on the target SDK,
 * and they are rounded).
 *
 * Each test sets a configuration that says one thing (a 360 x 800 dp portrait phone, this class's qualifiers) and a
 * window that says another, and asserts the classifier follows the window. Before the fix each returns the
 * configuration's answer.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w360dp-h800dp-xhdpi")
class WindowClassFollowsTheWindowTest {

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

    private fun <T> readInWindow(size: DpSize, read: @androidx.compose.runtime.Composable () -> T): T {
        var result: T? = null
        var configurationHeight = -1
        composeRule.setContent {
            configurationHeight = LocalConfiguration.current.statedSize().height.value.toInt()
            val window = FixedWindowInfo(LocalWindowInfo.current, size, LocalDensity.current.density)
            CompositionLocalProvider(LocalWindowInfo provides window) { result = read() }
        }
        composeRule.waitForIdle()
        assertEquals("the configuration still says an 800 dp tall portrait phone", 800, configurationHeight)
        @Suppress("UNCHECKED_CAST")
        return result as T
    }

    @Test
    fun `a 384 dp tall window is short though the configuration says 800 dp`() {
        assertEquals(true, readInWindow(DpSize(823.dp, 384.dp)) { isShortWindow() })
    }

    @Test
    fun `a 700 dp wide window is MEDIUM though the configuration says 360 dp`() {
        assertEquals(WindowWidthClass.MEDIUM, readInWindow(DpSize(700.dp, 900.dp)) { currentWindowWidthClass() })
    }

    @Test
    fun `a 900 dp wide window is EXPANDED though the configuration says 360 dp`() {
        assertEquals(WindowWidthClass.EXPANDED, readInWindow(DpSize(900.dp, 900.dp)) { currentWindowWidthClass() })
    }

    /** A pin (passes before and after by design): where the window and configuration agree, nothing changes. */
    @Test
    fun `the real window of this class's 360 by 800 dp phone is COMPACT and not short`() {
        var widthClass: WindowWidthClass? = null
        var short: Boolean? = null
        composeRule.setContent {
            widthClass = currentWindowWidthClass()
            short = isShortWindow()
        }
        composeRule.waitForIdle()
        assertEquals(WindowWidthClass.COMPACT, widthClass)
        assertEquals(false, short)
    }
}
