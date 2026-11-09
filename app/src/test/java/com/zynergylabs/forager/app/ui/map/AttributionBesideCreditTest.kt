package com.zynergylabs.forager.app.ui.map

import android.app.Application
import android.content.ComponentName
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.zynergylabs.forager.app.ui.theme.ForagerTheme
import org.junit.Assert.assertArrayEquals
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
import org.robolectric.annotation.GraphicsMode

/**
 * RECORD -954 (the owner: "Have the map i next to the open street maps logo"; "Just landscape"). The "i" is MapLibre's own
 * attribution control, placed through `UiSettings.setAttributionGravity` and `setAttributionMargins` (SightingsMap), and a real
 * MapView cannot be built under Robolectric, so the control itself cannot be touched or measured here: that is a device item. What
 * is held here is the placement it is given:
 * - the margins: its start just past the credit strip's end by the gap, its bottom on the strip's bottom (and mirrored in RTL);
 * - laid against the real credit strip, composed with the default basemap's credit at the S22's landscape size: the "i"'s square
 *   is clear of the strip, beside it, sharing its bottom edge, and left of the landscape navigation card at ROTATION_90, whose
 *   left edge is 385.33 dp in that window (`LandscapeCardMeetsBar823Test`, run 38261622357: the card at the strip's x, 385.33 to
 *   743).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w823dp-h384dp-land-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AttributionBesideCreditTest {

    private val composeRule = createAndroidComposeRule<ComponentActivity>()
    private val declareHostActivity = object : ExternalResource() {
        override fun before() {
            val app = ApplicationProvider.getApplicationContext<Application>()
            Shadows.shadowOf(app.packageManager).addActivityIfNotPresent(ComponentName(app, ComponentActivity::class.java))
        }
    }

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(declareHostActivity).around(composeRule)

    @Test
    fun `the margins put the button just past the credit strip's end, on its bottom, mirrored in RTL`() {
        val defaults = intArrayOf(10, 11, 12, 13)
        assertArrayEquals("LTR: start = strip + gap, top and end MapLibre's, bottom the strip's", intArrayOf(300 + 12, 11, 12, 40), attributionBesideCreditMarginsPx(defaults, creditWidthPx = 300, gapPx = 12, creditBottomPx = 40, isRtl = false))
        assertArrayEquals("RTL: the same on the right", intArrayOf(10, 11, 300 + 12, 40), attributionBesideCreditMarginsPx(defaults, creditWidthPx = 300, gapPx = 12, creditBottomPx = 40, isRtl = true))
    }

    @Test
    fun `against the real credit strip, the button sits beside it, clear of it, and left of the landscape card`() {
        composeRule.setContent {
            ForagerTheme {
                Box(Modifier.fillMaxSize()) {
                    MapCreditCaption(text = attributionCaption(listOf(Basemap.DEFAULT.attribution)), modifier = Modifier.align(Alignment.BottomStart))
                }
            }
        }
        composeRule.waitForIdle()
        val window = composeRule.onRoot().getUnclippedBoundsInRoot()
        val credit = composeRule.onNodeWithTag(MAP_CREDIT_CAPTION_TAG).getUnclippedBoundsInRoot()
        val density = composeRule.density
        val margins = with(density) {
            attributionBesideCreditMarginsPx(intArrayOf(0, 0, 0, 0), (credit.right - credit.left).roundToPx(), ATTRIBUTION_BESIDE_CREDIT_GAP_DP.dp.roundToPx(), 0, isRtl = false)
        }
        val (buttonLeft, buttonBottom) = with(density) { margins[0].toDp() to window.bottom - margins[3].toDp() }
        val buttonRight = buttonLeft + ATTRIBUTION_BUTTON_DP.dp
        val buttonTop = buttonBottom - ATTRIBUTION_BUTTON_DP.dp
        println("MEASURED credit $credit (${(credit.right - credit.left).value} dp wide); the i at x ${buttonLeft.value}..${buttonRight.value}, y ${buttonTop.value}..${buttonBottom.value}")
        assertTrue("the i ($buttonLeft) starts past the credit's end (${credit.right})", buttonLeft >= credit.right - 0.5.dp)
        assertEquals("by the gap", ATTRIBUTION_BESIDE_CREDIT_GAP_DP, (buttonLeft - credit.right).value, 1f)
        assertEquals("the two share a bottom edge", credit.bottom.value, buttonBottom.value, 0.5f)
        assertTrue("the i ($buttonRight) is left of the landscape card at ROTATION_90 (385.33)", buttonRight <= 385.33.dp)
    }
}
