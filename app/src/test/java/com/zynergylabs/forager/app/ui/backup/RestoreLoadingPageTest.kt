package com.zynergylabs.forager.app.ui.backup

import android.content.ContentResolver
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.click
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.zynergylabs.forager.app.ui.theme.ForagerTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The restore's loading page on its own (owner, "6 B", and the tap animation "1 A"): the app icon pulsing with
 * "Loading your restored journal…"; when the reload is done it stops, the text reads "Your journal is restored.",
 * "Done" sits at the icon's centre, and the icon is the button. Everything asserted through what the user gets: the
 * visible words, the content description, the touch target, real coordinate touches (a semantic click bypasses
 * hit-testing, CLAUDE.md, Testing), and the clock.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w384dp-h823dp-xxhdpi")
class RestoreLoadingPageTest {

    private val composeRule = createAndroidComposeRule<ComponentActivity>()

    @get:Rule
    val rules: org.junit.rules.RuleChain = org.junit.rules.RuleChain.outerRule(com.zynergylabs.forager.app.ui.availability.mapChromeHostActivityRule()).around(composeRule)

    private var page by mutableStateOf(RestorePage.LOADING)
    private var taps = 0
    private var left = 0
    private var underneathClicks = 0

    /** [dismissOnTap] is how the real ViewModel behaves: a tap moves the page to LEAVING; the page calls onLeft when its animation ends. */
    private fun setPage(dismissOnTap: Boolean = true) {
        // The loading page pulses forever, and Compose's test clock never goes idle under an infinite animation, so
        // the clock is driven by hand in every test here.
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            ForagerTheme(darkTheme = true) {
                Box(Modifier.fillMaxSize()) {
                    // The screen underneath, a sibling of the page and not its parent: a clickable parent would merge the
                    // page's semantics into its own, and a test could not find the page.
                    Box(Modifier.fillMaxSize().clickable { underneathClicks++ })
                    // As BackupRestoreOverlay does: no page once the ViewModel has dropped it.
                    if (page != RestorePage.NONE) {
                        RestoreLoadingPage(
                            page = page,
                            onDoneTapped = { taps++; if (dismissOnTap) page = RestorePage.LEAVING },
                            onLeft = { left++; page = RestorePage.NONE },
                        )
                    }
                }
            }
        }
        composeRule.mainClock.advanceTimeBy(50)
    }

    private fun scale(): Float = composeRule.onNodeWithTag(RESTORE_ICON_TAG).fetchSemanticsNode().config[RestoreIconScale]

    @Test
    fun `while loading, the page shows the icon and the loading words, no Done, and the icon pulses`() {
        setPage()

        composeRule.onNodeWithText("Loading your restored journal…").assertIsDisplayed()
        composeRule.onNodeWithTag(RESTORE_ICON_TAG).assertIsDisplayed()
        assertEquals("no Done while loading", 0, composeRule.onAllNodesWithText("Done").fetchSemanticsNodes().size)
        val seen = mutableSetOf<Float>()
        repeat(12) {
            composeRule.mainClock.advanceTimeBy(150)
            seen += scale()
        }
        assertTrue("the icon's scale changes over time: $seen", seen.size > 3)
    }

    @Test
    fun `a touch on the icon while loading does nothing`() {
        setPage()

        composeRule.onNodeWithTag(RESTORE_ICON_TAG).performTouchInput { click(center) }
        composeRule.mainClock.advanceTimeBy(50)

        assertEquals(0, taps)
    }

    @Test
    fun `when done the icon stops pulsing, the words change, and Done is at its centre`() {
        setPage()
        composeRule.mainClock.advanceTimeBy(300)

        page = RestorePage.DONE
        composeRule.mainClock.advanceTimeBy(600) // the pulse eases back to rest
        val rest = scale()
        val later = mutableSetOf<Float>()
        repeat(8) {
            composeRule.mainClock.advanceTimeBy(150)
            later += scale()
        }

        composeRule.onNodeWithText("Your journal is restored.").assertIsDisplayed()
        assertEquals("the loading words are gone", 0, composeRule.onAllNodesWithText("Loading your restored journal…").fetchSemanticsNodes().size)
        composeRule.onNodeWithText("Done").assertIsDisplayed()
        assertEquals("it has stopped: $later", setOf(rest), later)
        val icon = composeRule.onNodeWithTag(RESTORE_ICON_TAG).getBoundsInRoot()
        val done = composeRule.onNodeWithText("Done", useUnmergedTree = true).getBoundsInRoot()
        val iconCentre = (icon.left + icon.right) / 2 to (icon.top + icon.bottom) / 2
        val doneCentre = (done.left + done.right) / 2 to (done.top + done.bottom) / 2
        assertEquals("Done is centred on the icon, across", iconCentre.first.value, doneCentre.first.value, 1f)
        assertEquals("Done is centred on the icon, down", iconCentre.second.value, doneCentre.second.value, 1f)
    }

    @Test
    fun `the icon is the button, with content description Done, a click action, and a touch target of at least 48 dp`() {
        page = RestorePage.DONE
        setPage()

        composeRule.onNodeWithContentDescription("Done").assertHasClickAction()
        val bounds = composeRule.onNodeWithContentDescription("Done").getBoundsInRoot()
        assertTrue("wide enough: ${bounds.right - bounds.left}", bounds.right - bounds.left >= 48.dp)
        assertTrue("tall enough: ${bounds.bottom - bounds.top}", bounds.bottom - bounds.top >= 48.dp)
    }

    @Test
    fun `real touches across the icon's own bounds all press it, not only one at its centre`() {
        page = RestorePage.DONE
        setPage(dismissOnTap = false)

        for ((fx, fy) in listOf(0.5f to 0.5f, 0.1f to 0.5f, 0.9f to 0.5f, 0.5f to 0.1f, 0.5f to 0.9f, 0.25f to 0.25f, 0.75f to 0.75f)) {
            val before = taps
            composeRule.onNodeWithTag(RESTORE_ICON_TAG).performTouchInput { click(Offset(width * fx, height * fy)) }
            composeRule.mainClock.advanceTimeBy(50)
            assertEquals("a touch at ($fx, $fy) of the icon pressed it", before + 1, taps)
        }
    }

    @Test
    fun `a touch on the page outside the icon does nothing and does not reach the screen underneath`() {
        page = RestorePage.DONE
        setPage(dismissOnTap = false)

        composeRule.onNodeWithTag(RESTORE_PAGE_TAG).performTouchInput { click(Offset(8f, 8f)) }
        composeRule.mainClock.advanceTimeBy(50)

        assertEquals(0, taps)
        assertEquals("the page covers the screen: a touch on it is not the screen's", 0, underneathClicks)
    }

    @Test
    fun `the tap makes the icon grow and fade for about 300 ms, then the page is gone`() {
        page = RestorePage.DONE
        setPage()
        val resting = scale()

        composeRule.onNodeWithTag(RESTORE_ICON_TAG).performTouchInput { click(center) }
        composeRule.mainClock.advanceTimeBy(150)
        val half = scale()

        assertEquals(1, taps)
        assertTrue("grown by halfway: $resting then $half", half > resting)
        assertEquals("still on screen at halfway", 0, left)
        composeRule.onNodeWithTag(RESTORE_PAGE_TAG).assertIsDisplayed()

        composeRule.mainClock.advanceTimeBy(250)

        assertEquals("gone once the animation ends", 1, left)
        assertEquals(0, composeRule.onAllNodesWithContentDescription("Done").fetchSemanticsNodes().size)
    }

    @Test
    fun `with the system's animations off, the tap leaves at once`() {
        val resolver: ContentResolver = ApplicationProvider.getApplicationContext<android.app.Application>().contentResolver
        Settings.Global.putFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 0f)
        page = RestorePage.DONE
        setPage()

        composeRule.onNodeWithTag(RESTORE_ICON_TAG).performTouchInput { click(center) }
        composeRule.mainClock.advanceTimeBy(20)

        assertEquals("no animation to wait for", 1, left)
        assertNotEquals(0, taps)
    }
}
