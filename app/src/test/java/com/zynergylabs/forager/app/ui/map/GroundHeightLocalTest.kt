package com.zynergylabs.forager.app.ui.map

import android.app.Application
import android.content.ComponentName
import androidx.activity.ComponentActivity
import androidx.test.core.app.ApplicationProvider
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain
import org.robolectric.Shadows
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.zynergylabs.forager.app.domain.GroundHeight
import com.zynergylabs.forager.app.domain.GroundHeightSource
import com.zynergylabs.forager.app.domain.NoGroundHeightSource
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertSame
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Dispatch 2026-11-03-07: `MainActivity`'s root, [ForagerThemeWithNoSignal], hands the app's ground heights to every map
 * with bubbles as [LocalGroundHeightSource]; without it a map has [NoGroundHeightSource] and shows no line.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class GroundHeightLocalTest {

    private val composeRule = createAndroidComposeRule<ComponentActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(
        object : ExternalResource() {
            override fun before() {
                val app = ApplicationProvider.getApplicationContext<Application>()
                Shadows.shadowOf(app.packageManager).addActivityIfNotPresent(ComponentName(app, ComponentActivity::class.java))
            }
        },
    ).around(composeRule)

    @Test
    fun `the app's root provides the source it is given`() {
        val source = GroundHeightSource { GroundHeight.Known(1.0) }
        var seen: GroundHeightSource? = null
        composeRule.setContent {
            ForagerThemeWithNoSignal(darkTheme = false, noSignal = MutableStateFlow(false), offlineRegions = emptyList(), groundHeightSource = source) {
                seen = LocalGroundHeightSource.current
            }
        }
        composeRule.waitForIdle()
        assertSame(source, seen)
    }

    @Test
    fun `with nothing provided a map has no source`() {
        var seen: GroundHeightSource? = null
        composeRule.setContent { seen = LocalGroundHeightSource.current }
        composeRule.waitForIdle()
        assertSame(NoGroundHeightSource, seen)
    }
}
