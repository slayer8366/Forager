package com.zynergylabs.forager.app.ui.map

import android.app.Application
import android.content.ComponentName
import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.core.app.ApplicationProvider
import com.zynergylabs.forager.app.ui.theme.DarkColors
import com.zynergylabs.forager.app.ui.theme.ForagerTheme
import com.zynergylabs.forager.app.ui.theme.LightColors
import com.zynergylabs.forager.app.ui.theme.SurfaceContainerDark
import com.zynergylabs.forager.app.ui.theme.SurfaceContainerLight
import com.zynergylabs.forager.app.ui.theme.navigationBarContainerColor
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
 * C1 (dispatch 2026-09-28-210, item 1): the one token, the bottom navigation bar's container colour, and the
 * colour constants every piece of map chrome reads. The owner, verbatim: "Have them be the same color as the bottom
 * app navigation bar." Nothing here asserts an alpha that `MapChromeAlphaTest` does not already pin: each check
 * compares a colour aside from its alpha, and the alpha checks that appear are the unchanged 0.8, 0.6 and 0.5.
 */
class MapChromeColourTokenTest {

    @Test
    fun `the token is the navigation bar's container in both themes, #202020 dark and #F4EFE2 light`() {
        assertEquals(Color(0xFF202020), DarkColors.surfaceContainer)
        assertEquals(Color(0xFFF4EFE2), LightColors.surfaceContainer)
        assertEquals(SurfaceContainerDark, DarkColors.surfaceContainer)
        assertEquals(SurfaceContainerLight, LightColors.surfaceContainer)
    }

    @Test
    fun `the chrome's dark fill is the token at the standing 0_8, not Bark`() {
        assertEquals(DarkColors.surfaceContainer, MapIconStackButtonColorDark.copy(alpha = 1f))
        assertEquals(MAP_CHROME_OVER_MAP_ALPHA, MapIconStackButtonColorDark.alpha, 0.0001f)
    }

    @Test
    fun `the chrome's light fill is the token at the standing 0_8, not Cream`() {
        assertEquals(LightColors.surfaceContainer, MapIconStackButtonColorLight.copy(alpha = 1f))
        assertEquals(MAP_CHROME_OVER_MAP_ALPHA, MapIconStackButtonColorLight.alpha, 0.0001f)
    }

    /**
     * Dispatch 2026-10-12-08 (RECORD -945, -951): the search pill's lighter fill is a theme role, not a raw value: the surface
     * ramp's lightest end in each theme (surfaceContainerLowest by day, #FFFFFF; surfaceContainerHighest at night, #373737), at
     * the standing 0.8; its colour, not its alpha, is what differs from the token.
     */
    @Test
    fun `the pill's fill is the surface ramp's lightest end in each theme, at the standing 0_8`() {
        assertEquals(LightColors.surfaceContainerLowest, MapPillColorLight.copy(alpha = 1f))
        assertEquals(DarkColors.surfaceContainerHighest, MapPillColorDark.copy(alpha = 1f))
        assertEquals(Color(0xFFFFFFFF), MapPillColorLight.copy(alpha = 1f))
        assertEquals(Color(0xFF373737), MapPillColorDark.copy(alpha = 1f))
        assertEquals(MAP_CHROME_OVER_MAP_ALPHA, MapPillColorLight.alpha, 0.0001f)
        assertEquals(MAP_CHROME_OVER_MAP_ALPHA, MapPillColorDark.alpha, 0.0001f)
    }
}

/** The composable side of the token: what a composition in each theme reads, and the cluster's two layers built from it. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class MapChromeColourTokenCompositionTest {

    private val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val declareHostActivity = object : ExternalResource() {
        override fun before() {
            val app = ApplicationProvider.getApplicationContext<Application>()
            Shadows.shadowOf(app.packageManager).addActivityIfNotPresent(ComponentName(app, ComponentActivity::class.java))
        }
    }

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(declareHostActivity).around(composeRule)

    private class Read {
        var scheme = Color.Unspecified
        var token = Color.Unspecified
        var container = Color.Unspecified
        var child = Color.Unspecified
    }

    private fun read(dark: Boolean): Read {
        val out = Read()
        composeRule.setContent {
            ForagerTheme(darkTheme = dark) {
                out.scheme = MaterialTheme.colorScheme.surfaceContainer
                out.token = navigationBarContainerColor()
                out.container = mapIconClusterContainerColor()
                out.child = mapIconClusterChildColor()
            }
        }
        composeRule.waitForIdle()
        return out
    }

    private fun check(dark: Boolean) {
        val r = read(dark)
        assertEquals("the token is the theme's surfaceContainer", r.scheme, r.token)
        assertEquals("the cluster's container layer is the token at 0.6", r.token.copy(alpha = MAP_ICON_CLUSTER_CONTAINER_ALPHA), r.container)
        assertEquals("the cluster's child layer is the token at 0.5", r.token.copy(alpha = MAP_ICON_CLUSTER_CHILD_ALPHA), r.child)
    }

    @Test fun `in dark the cluster's two layers are the token at 0_6 and 0_5`() = check(dark = true)

    @Test fun `in light the cluster's two layers are the token at 0_6 and 0_5`() = check(dark = false)
}
