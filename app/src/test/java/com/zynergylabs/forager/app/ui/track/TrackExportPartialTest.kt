package com.zynergylabs.forager.app.ui.track

import android.app.Application
import android.content.ComponentName
import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.test.core.app.ApplicationProvider
import com.zynergylabs.forager.app.domain.model.Track
import com.zynergylabs.forager.app.domain.model.TrackPoint
import com.zynergylabs.forager.app.domain.model.TrackPointRecord
import com.zynergylabs.forager.app.photo.FileProviderCacheReset
import com.zynergylabs.forager.app.ui.theme.ForagerTheme
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
import java.io.IOException

/**
 * Dispatch 2026-09-28-658, scout item R9: a GPX share whose full GPS record could not be read
 * tells the person so before the share sheet, because the file is partial. Through the track row's
 * own Share button, a real touch, as `RecordDetailsSheetTest`'s share test does; the reading is
 * the toast the platform was asked to show and the chooser that followed it.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class TrackExportPartialTest {

    private val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val declareHostActivity = object : ExternalResource() {
        override fun before() {
            val app = ApplicationProvider.getApplicationContext<Application>()
            Shadows.shadowOf(app.packageManager)
                .addActivityIfNotPresent(ComponentName(app, ComponentActivity::class.java))
        }
    }

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(declareHostActivity).around(FileProviderCacheReset()).around(composeRule)

    private val track = Track(
        id = "T1",
        name = null,
        startedAtEpochMillis = 1_790_000_000_000L,
        endedAtEpochMillis = 1_790_000_600_000L,
        points = listOf(
            TrackPoint(45.0, -122.0, null, 5f, 1_790_000_000_000L),
            TrackPoint(45.001, -122.0, null, 5f, 1_790_000_010_000L),
        ),
    )

    @Test
    fun `a share without the full record says the file is partial, then opens the share sheet`() {
        share { Result.failure(IOException("full record unreadable")) }

        val started = awaitStartedActivity()
        assertEquals(Intent.ACTION_CHOOSER, started.action)
        assertEquals(PARTIAL_GPX_EXPORT_MESSAGE, ShadowToast.getTextOfLatestToast())
    }

    @Test
    fun `a share with the full record says nothing extra`() {
        share { Result.success(emptyList()) }

        assertEquals(Intent.ACTION_CHOOSER, awaitStartedActivity().action)
        assertEquals("no toast for a complete export", 0, ShadowToast.shownToastCount())
    }

    private fun share(getFullRecord: suspend (String) -> Result<List<TrackPointRecord>>) {
        composeRule.setContent {
            ForagerTheme { TrackExportRow(track = track, waypoints = emptyList(), getFullRecord = getFullRecord) }
        }
        composeRule.onNodeWithTag("share-track-T1").performTouchInput { click(center) }
    }

    private fun awaitStartedActivity(): Intent {
        // The GPX file is written on Dispatchers.IO before the chooser starts; waitForIdle does not
        // cover that hop, so poll, as RecordDetailsSheetTest does.
        var started: Intent? = null
        composeRule.waitUntil(timeoutMillis = 5_000) {
            started = started ?: Shadows.shadowOf(composeRule.activity).nextStartedActivity
            started != null
        }
        return started!!
    }
}
