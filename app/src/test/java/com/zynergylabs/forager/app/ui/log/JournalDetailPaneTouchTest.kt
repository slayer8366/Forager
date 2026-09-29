package com.zynergylabs.forager.app.ui.log

import android.app.Application
import android.content.ComponentName
import androidx.activity.ComponentActivity
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
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
 * J6a, ruling 1 (list-detail): the detail pane that takes the wide tree's right side is opaque to
 * touch, so nothing beneath it (the results pane's tabs, search bar and map) is reached through it.
 * A real coordinate touch, not a semantic click (CLAUDE.md: a semantic `performClick` bypasses hit
 * testing, so it passes even when another composable covers the control).
 *
 * The pane's content here is an empty, non-interactive `Box`, on purpose. A real detail (a report, an
 * editor) is a scrolling column, which takes touches by itself over most of its area, so a test through
 * the whole screen cannot tell a pane that blocks touches from one that merely happens to be covered by
 * scrolling content (`WideJournalTest`'s touch test was shown not to, by a revert). This is the pane on
 * its own, over something clickable.
 *
 * A positive control runs first in each test: with no pane the same touches do click what is beneath, so
 * the zero after it is the pane's doing and not a coordinate that reaches nothing.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w824dp-h800dp-mdpi")
class JournalDetailPaneTouchTest {

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

    private var clicksBeneath by mutableIntStateOf(0)
    private var paneShowing by mutableStateOf(false)

    private fun setScreen() {
        composeRule.setContent {
            MaterialTheme {
                Box(Modifier.fillMaxSize()) {
                    Box(Modifier.fillMaxSize().testTag("beneath").clickable { clicksBeneath++ })
                    if (paneShowing) {
                        JournalDetailPane(JournalDetailLayer(JournalDetailPriority.ENTRY) { Box(Modifier.fillMaxSize()) })
                    }
                }
            }
        }
        composeRule.waitForIdle()
    }

    private val samples = listOf(0.1f to 0.1f, 0.5f to 0.5f, 0.9f to 0.9f, 0.9f to 0.1f, 0.1f to 0.9f)

    private fun touchAcross() {
        for ((fx, fy) in samples) {
            composeRule.onNodeWithTag("beneath").performTouchInput { click(Offset(width * fx, height * fy)) }
            composeRule.waitForIdle()
        }
    }

    @Test
    fun `touches across the pane do not click what is beneath it`() {
        setScreen()
        touchAcross()
        assertEquals("positive control: with no pane every touch clicks what is beneath", samples.size, clicksBeneath)

        clicksBeneath = 0
        paneShowing = true
        composeRule.waitForIdle()
        touchAcross()

        assertEquals("with the pane up, no touch reaches what is beneath it", 0, clicksBeneath)
    }
}
