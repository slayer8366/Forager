package com.forager.app.ui.log

import android.app.Application
import android.content.ComponentName
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import com.forager.app.photo.CameraCaptureFiles
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowToast

/**
 * [MushroomLogUiState.saveErrorMessage]'s Toast on [LogPanel] — the drawer-hosted counterpart to
 * [JournalTabTest]'s identical coverage of [JournalTab]. Both entry points wire the same
 * `LaunchedEffect` independently (see [LogPanel]'s own doc comment), so both get the render-wiring
 * check; the clearing logic itself is [MushroomLogViewModelTest]'s to prove.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class LogPanelTest {

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

    private fun setScreen(uiState: MushroomLogUiState) {
        composeRule.setContent {
            LogPanel(
                uiState = uiState,
                cameraCaptureFiles = CameraCaptureFiles(ApplicationProvider.getApplicationContext()),
                onOpenEntry = {},
                onCloseEntry = {},
                onEntryChanged = {},
                onAddPhoto = {},
                onRemovePhoto = {},
                onDeleteEntry = {},
                onBackToSearch = {},
                onSaveErrorDismissed = {},
            )
        }
    }

    @Test
    fun `a set saveErrorMessage shows a Toast with that text`() {
        setScreen(MushroomLogUiState(saveErrorMessage = "Couldn't save your changes."))

        composeRule.waitForIdle()

        assertEquals("Couldn't save your changes.", ShadowToast.getTextOfLatestToast())
    }

    @Test
    fun `with saveErrorMessage unset, no Toast shows`() {
        setScreen(MushroomLogUiState())

        composeRule.waitForIdle()

        assertEquals(null, ShadowToast.getTextOfLatestToast())
    }
}
