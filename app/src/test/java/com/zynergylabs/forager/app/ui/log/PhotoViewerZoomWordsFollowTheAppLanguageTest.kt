package com.zynergylabs.forager.app.ui.log

import android.app.Application
import android.content.ComponentName
import androidx.activity.ComponentActivity
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.test.core.app.ApplicationProvider
import com.zynergylabs.forager.app.domain.model.LogPhoto
import java.io.File
import java.util.Locale
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
 * Lint dispatch 2026-10-17-03, Phase 2 (RECORD -1195), NonObservableLocale at `PhotoViewerDialog.kt`: the zoom label
 * (what a screen reader announces for the photo) formats its number in the configuration's language, which Compose
 * observes, not the process-wide default. In French the decimal separator is a comma: "Zoom 1,0×". Before the fix the
 * label read "Zoom 1.0×" whatever the configuration said.
 *
 * French comes from this class's qualifiers, with the JVM default set back to English: the viewer is a dialog, whose
 * own view provides its own `LocalConfiguration`, so an override from outside it would never arrive.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "fr-rFR")
class PhotoViewerZoomWordsFollowTheAppLanguageTest {

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

    @Test
    fun `the zoom label uses the configuration's number format`() {
        val originalDefault = Locale.getDefault()
        Locale.setDefault(Locale.US)
        val context = ApplicationProvider.getApplicationContext<Application>()
        val file = File(context.filesDir, "photos/zoom-words.jpg")
        file.parentFile?.mkdirs()
        file.writeBytes(byteArrayOf(1, 2, 3, 4, 5))
        val photo = LogPhoto(id = "zoom-words.jpg", relativePath = "photos/zoom-words.jpg", createdAtEpochMillis = 1_000L)

        var configurationLanguage = ""
        composeRule.setContent {
            configurationLanguage = LocalConfiguration.current.locales[0].language
            PhotoViewerDialog(photos = listOf(photo), initialIndex = 0, onDismiss = {})
        }
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithContentDescription(VIEWER_PHOTO_DESCRIPTION).fetchSemanticsNodes().isNotEmpty()
        }

        val label = composeRule.onNodeWithContentDescription(VIEWER_PHOTO_DESCRIPTION)
            .fetchSemanticsNode().config[SemanticsProperties.StateDescription]
        try {
            assertEquals("the configuration says French", "fr", configurationLanguage)
            assertEquals("the JVM default says English", "en", Locale.getDefault().language)
            assertEquals("Zoom 1,0×", label)
        } finally {
            file.delete()
            Locale.setDefault(originalDefault)
        }
    }
}
