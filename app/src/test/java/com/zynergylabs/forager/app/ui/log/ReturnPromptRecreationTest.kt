package com.zynergylabs.forager.app.ui.log

import android.app.Application
import android.content.ComponentName
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config

/**
 * Part 2 follow-ups F1 item 8 (Part 2 Session 2, item 18): the "Welcome back" prompt, meant for returning
 * from the camera app or from backgrounding, also showed after an activity *recreation* with a pending
 * edit (`cmd uimode night`). Driven by the real Activity lifecycle through [rememberReturnPromptState],
 * the code `CartographyScreen` calls: a real `ActivityScenario.recreate()` for the recreation, and
 * `moveToState(CREATED)` then `RESUMED` for a real backgrounding.
 *
 * **Why an extracted state and not the whole screen.** The screen's fixture keeps the open entry in
 * remembered state that a recreation resets, so a recreation could not leave it dirty there; the
 * prompt's own state is what a recreation touches.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ReturnPromptRecreationTest {

    /** Hosts the composable under test; [dirty] stands in for the open entry having an unsaved edit. */
    class HostActivity : ComponentActivity() {
        override fun onCreate(savedInstanceState: Bundle?) {
            super.onCreate(savedInstanceState)
            setContent { PromptProbe(entryDirty = dirty, entryOpen = true, photoAcquisitionInFlight = false) }
        }
    }

    @get:Rule
    val composeRule = createEmptyComposeRule()

    private lateinit var scenario: ActivityScenario<HostActivity>

    @Before
    fun setUp() {
        dirty = true
        val app = ApplicationProvider.getApplicationContext<Application>()
        Shadows.shadowOf(app.packageManager).addActivityIfNotPresent(ComponentName(app, HostActivity::class.java))
        scenario = ActivityScenario.launch(HostActivity::class.java)
        composeRule.waitForIdle()
    }

    @After
    fun tearDown() {
        scenario.close()
    }

    private fun promptShown(): Boolean = composeRule.onAllNodes(hasText(PROMPT_TEXT)).fetchSemanticsNodes().isNotEmpty()

    @Test
    fun `an activity recreation with a pending edit shows no prompt`() {
        scenario.recreate()
        composeRule.waitForIdle()

        assertEquals("the recreation must have reached the new activity's RESUMED state", Lifecycle.State.RESUMED, scenario.state)
        assertFalse("no Welcome back after a recreation the user did not leave the app for", promptShown())
    }

    @Test
    fun `two recreations in a row still show no prompt`() {
        scenario.recreate()
        scenario.recreate()
        composeRule.waitForIdle()

        assertFalse(promptShown())
    }

    @Test
    fun `backgrounding with a pending edit and returning shows the prompt`() {
        scenario.moveToState(Lifecycle.State.CREATED)
        composeRule.waitForIdle()
        scenario.moveToState(Lifecycle.State.RESUMED)
        composeRule.waitForIdle()

        assertTrue("the return after a real backgrounding still asks", promptShown())
    }

    @Test
    fun `a pending edit that was backgrounded still asks after a rebuild on return`() {
        // A config change that lands while the app is in the background: the activity is not recreated
        // until the user comes back, so ON_STOP fired with no configuration change under way.
        scenario.moveToState(Lifecycle.State.CREATED)
        composeRule.waitForIdle()
        scenario.recreate()
        scenario.moveToState(Lifecycle.State.RESUMED)
        composeRule.waitForIdle()

        assertTrue("a backgrounded pending edit is still asked about after the rebuild", promptShown())
    }

    @Test
    fun `backgrounding with no pending edit and returning shows no prompt`() {
        // A clean entry from the start: the flag is read when the Activity is created, so relaunch with it off.
        scenario.close()
        dirty = false
        scenario = ActivityScenario.launch(HostActivity::class.java)
        composeRule.waitForIdle()
        scenario.moveToState(Lifecycle.State.CREATED)
        composeRule.waitForIdle()
        scenario.moveToState(Lifecycle.State.RESUMED)
        composeRule.waitForIdle()

        assertFalse(promptShown())
    }

    private companion object {
        const val PROMPT_TEXT = "Welcome back (test probe)"

        @Volatile var dirty = true
    }
}

/** Shows [ReturnPromptRecreationTest]'s probe text while [rememberReturnPromptState] says the return prompt is up. */
@Composable
private fun PromptProbe(entryDirty: Boolean, entryOpen: Boolean, photoAcquisitionInFlight: Boolean) {
    val state = rememberReturnPromptState(entryDirty, entryOpen, photoAcquisitionInFlight)
    if (state.showReturnPrompt) Text("Welcome back (test probe)")
}
