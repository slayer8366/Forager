package com.zynergylabs.forager.app.ui.availability

import androidx.activity.ComponentActivity
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.printToString
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.zynergylabs.forager.app.domain.OfflineMapRepository
import com.zynergylabs.forager.app.domain.OfflineRegionSummary
import com.zynergylabs.forager.app.domain.model.Region
import kotlinx.coroutines.CompletableDeferred
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Dispatch 2026-10-23-06 Amendment 4 (RECORD -1591; the owner chose "Yes, in #386", the option text: show "Paused · no signal ·
 * continues by itself" while it waits). On the S22 a paused download showed "627 / 1608 tiles" with the bar frozen and nothing
 * saying it was paused (Amendment 3 report).
 *
 * Driven through the real [AvailabilityScreen] and the real [AvailabilityViewModel] ([RealSearchScreenRig]): Tools, the
 * drawer's Offline maps row, a real touch on Download Maps, and the confirmation's Download. The repository is a fake that reports
 * what `MapLibreOfflineMapRepository` reports from MapLibre's observer: progress, a pause, progress again, the continue. Those
 * calls run on the main thread, as the observer's do.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w360dp-h780dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class DownloadPausedShownTest {

    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(RealSearchScreenRig.touchModeHost()).around(composeRule)

    private val download = PausingDownload()
    private val rig = RealSearchScreenRig(composeRule, download)

    private fun startDownload() {
        rig.setScreen()
        // The way in the S22 run took: Tools, then the drawer's Offline maps row.
        rig.touch(
            composeRule.onAllNodes(hasText("Tools") and hasAnyAncestor(hasTestTag(COMPACT_BOTTOM_NAV_TAG)), useUnmergedTree = true)
                .onFirst(),
        )
        rig.touch(composeRule.onNodeWithTag(TOOLS_OFFLINE_MAPS_ROW_TAG))
        if (composeRule.onAllNodes(hasText("Download Maps")).fetchSemanticsNodes().isEmpty()) {
            throw AssertionError("positive control: the Offline maps screen is not up; tree:\n" + composeRule.onAllNodes(isRoot()).onFirst().printToString(maxDepth = 60))
        }
        val button = composeRule.onNodeWithText("Download Maps").performScrollTo()
        rig.settle()
        rig.touch(button)
        composeRule.onNode(hasText("Download") and hasAnyAncestor(isDialog())).performClick()
        rig.settle()
        assertNotNull("positive control: the screen's download reached the repository", download.progress)
    }

    private fun onMain(block: () -> Unit) {
        composeRule.runOnUiThread(block)
        rig.settle()
    }

    /** Every determinate progress bar on screen, as a fraction (the radius slider's range is not 0..1, so it is left out). */
    private fun barFractions(): List<Float> = composeRule.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.ProgressBarRangeInfo))
        .fetchSemanticsNodes()
        .mapNotNull { it.config.getOrNull(SemanticsProperties.ProgressBarRangeInfo) }
        .filter { it != ProgressBarRangeInfo.Indeterminate && it.range == 0f..1f }
        .map { it.current }

    @Test
    fun `while the download waits for signal it says so, and the bar stays where it was`() {
        startDownload()
        onMain { download.progress!!(627, 1608) }
        composeRule.onNodeWithText("627 / 1608 tiles").assertIsDisplayed()

        onMain { download.pauseChanged!!(true) }

        composeRule.onNodeWithText("Paused · no signal · continues by itself").assertIsDisplayed()
        composeRule.onNodeWithText("627 / 1608 tiles").assertDoesNotExist()
        assertEquals("the bar stays where it was", listOf(627f / 1608), barFractions())

        // MapLibre reports status while paused too, with the same count: the line stays.
        onMain { download.progress!!(627, 1608) }
        composeRule.onNodeWithText("Paused · no signal · continues by itself").assertIsDisplayed()
    }

    @Test
    fun `when the download continues the count comes back`() {
        startDownload()
        onMain { download.progress!!(627, 1608) }
        onMain { download.pauseChanged!!(true) }
        composeRule.onNodeWithText("Paused · no signal · continues by itself").assertIsDisplayed()

        // The order MapLibreOfflineMapRepository reports it in: the new count, then the continue.
        onMain {
            download.progress!!(691, 1608)
            download.pauseChanged!!(false)
        }

        composeRule.onNodeWithText("691 / 1608 tiles").assertIsDisplayed()
        composeRule.onNodeWithText("Paused · no signal · continues by itself").assertDoesNotExist()
        assertEquals(listOf(691f / 1608), barFractions())
    }

    @Test
    fun `a pause that runs out ends in the usual error`() {
        startDownload()
        onMain { download.progress!!(627, 1608) }
        onMain { download.pauseChanged!!(true) }

        onMain { download.result.complete(Result.failure(java.io.IOException("Offline map download failed."))) }

        composeRule.onNodeWithText("Couldn't download offline maps.").assertIsDisplayed()
        composeRule.onNodeWithText("Paused · no signal · continues by itself").assertDoesNotExist()
    }
}

/** A download the test drives: it keeps the callbacks the ViewModel handed it and waits for [result]. */
private class PausingDownload : OfflineMapRepository {
    var progress: ((Int, Int) -> Unit)? = null
    var pauseChanged: ((Boolean) -> Unit)? = null
    val result = CompletableDeferred<Result<OfflineRegionSummary>>()

    override suspend fun download(name: String, region: Region, onProgress: (Int, Int) -> Unit): Result<OfflineRegionSummary> {
        progress = onProgress
        return result.await()
    }

    override suspend fun download(
        name: String,
        region: Region,
        onProgress: (Int, Int) -> Unit,
        onRouteStep: () -> Unit,
        onPauseChanged: (Boolean) -> Unit,
    ): Result<OfflineRegionSummary> {
        progress = onProgress
        pauseChanged = onPauseChanged
        return result.await()
    }

    override suspend fun deleteRegion(id: Long): Result<Unit> = Result.success(Unit)

    override suspend fun listRegions(): Result<List<OfflineRegionSummary>> = Result.success(emptyList())
}
