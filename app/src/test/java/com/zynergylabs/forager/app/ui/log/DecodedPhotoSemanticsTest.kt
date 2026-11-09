package com.zynergylabs.forager.app.ui.log

import android.app.Application
import android.content.ComponentName
import androidx.activity.ComponentActivity
import androidx.compose.ui.semantics.AccessibilityAction
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.semantics.getOrNull
import androidx.test.core.app.ApplicationProvider
import com.zynergylabs.forager.app.domain.model.GalleryPhoto
import com.zynergylabs.forager.app.domain.model.LatLng
import com.zynergylabs.forager.app.domain.model.LogPhoto
import com.zynergylabs.forager.app.domain.model.MushroomLogEntry
import java.time.LocalDate
import org.junit.After
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

/**
 * Dispatch 2026-09-28-317, planner ruling on the semantics stop: the gesture fix must leave what a
 * screen reader reads on the four affected photos **unchanged**, loaded and not yet loaded.
 *
 * For each site and each state this dumps everything the merged node carries (role,
 * contentDescription, click and long-click actions with their labels, custom actions, and every other
 * semantics property, sorted by name) plus the same node in the unmerged tree and its unmerged
 * children, and asserts it equals a recorded dump. The recorded dumps in [GOLDEN] were captured on
 * the **unfixed** build (`DecodedPhoto` as a `Box` until loaded, then an `Image`), and this class is
 * expected to pass identically before and after the fix: that is the claim under test, and so it is
 * the one place in this change where "passes before and after" is the point, not a warning. It bites
 * when a change makes the not-yet-loaded node expose a description or role the placeholder never
 * had, or drops the role or description once loaded.
 *
 * The "not yet loaded" state is held by [GatedDecode], so the placeholder is on screen for certain;
 * the state is asserted by the absence of any "Log photo" node before the dump is taken.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], shadows = [GatedBitmapFactoryShadow::class])
class DecodedPhotoSemanticsTest {

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

    @After
    fun releaseAnyHeldDecode() {
        GatedDecode.reset()
    }

    private val photo = LogPhoto(id = "semantics-photo", relativePath = "photos/none-semantics.jpg", createdAtEpochMillis = 1_758_300_000_000L)

    private val openFullScreen = SemanticsMatcher("has click label 'Open full screen'") {
        it.config.getOrNull(SemanticsActions.OnClick)?.label == "Open full screen"
    }

    private fun entryWithPhoto() = MushroomLogEntry
        .draft(id = "semantics-entry", location = LatLng(45.326, -122.634), date = LocalDate.of(2026, 8, 1))
        .copy(photos = listOf(photo))

    private fun photoNodes() = composeRule.onAllNodesWithContentDescription("Log photo")

    private fun render(value: Any?): String = when (value) {
        // An action's lambda has no stable text; its label is what TalkBack reads.
        is AccessibilityAction<*> -> "action(label=${value.label})"
        is CustomAccessibilityAction -> "custom(${value.label})"
        is List<*> -> value.joinToString(prefix = "[", postfix = "]") { render(it) }
        else -> value.toString()
    }

    private fun dump(node: SemanticsNode): String =
        node.config.map { (key, value) -> "${key.name}=${render(value)}" }.sorted().joinToString("\n")

    /** The merged node, the same node unmerged, and the unmerged node's children: what a reader can reach. */
    private fun dumpPhoto(): String {
        val merged = composeRule.onNode(openFullScreen).fetchSemanticsNode()
        val unmerged = composeRule.onNode(openFullScreen, useUnmergedTree = true).fetchSemanticsNode()
        return buildString {
            appendLine("-- merged --")
            appendLine(dump(merged))
            appendLine("-- unmerged --")
            appendLine(dump(unmerged))
            appendLine("-- unmerged children: ${unmerged.children.size} --")
            unmerged.children.forEach { appendLine(dump(it)) }
        }.trimEnd()
    }

    private val unrecorded = mutableListOf<String>()

    private fun assertDump(site: String, state: String) {
        val actual = dumpPhoto()
        val expected = GOLDEN.getValue("$site/$state")
        if (expected == SEMDUMP_MARKER) {
            unrecorded += "=== $site/$state ===\n$actual"
            return
        }
        assertEquals("semantics of $site, $state", expected, actual)
    }

    private fun unloadedThenLoaded(site: String) {
        composeRule.waitForIdle()
        assertTrue("reachability: the placeholder must be showing for the not-yet-loaded dump", photoNodes().fetchSemanticsNodes().isEmpty())
        assertDump(site, "not yet loaded")
        GatedDecode.release()
        assertTrue("the gated decode never finished on its IO thread", GatedDecode.awaitFinished())
        composeRule.waitUntil(conditionDescription = "the photo ('Log photo') to appear once decoded", timeoutMillis = 5_000) {
            photoNodes().fetchSemanticsNodes().isNotEmpty()
        }
        assertDump(site, "loaded")
        // A golden not recorded yet fails the test with the dump, so it cannot pass vacuously.
        assertTrue("golden not recorded yet; dumps to record:\n" + unrecorded.joinToString("\n"), unrecorded.isEmpty())
    }

    private fun setAlbum(wiredDelete: Boolean) {
        GatedDecode.holdDecodes(decodes = 1)
        composeRule.setContent {
            EntriesAlbum(
                photos = listOf(GalleryPhoto(photo = photo, referencingEntryIds = emptyList())),
                isLoading = false,
                onDeletePhoto = {},
                photoAcquisition = rememberPhotoAcquisitionLaunchers({}, {}),
                onRequestDeletePhoto = if (wiredDelete) ({ _: String -> }) else null,
            )
        }
    }

    @Test
    fun `album tile with delete wired reads the same before and after the photo loads as it always did`() {
        setAlbum(wiredDelete = true)
        unloadedThenLoaded("album-wired")
    }

    @Test
    fun `album tile with no delete wired reads the same before and after the photo loads as it always did`() {
        setAlbum(wiredDelete = false)
        unloadedThenLoaded("album-unwired")
    }

    @Test
    fun `find editor thumbnail reads the same before and after the photo loads as it always did`() {
        GatedDecode.holdDecodes(decodes = 1)
        composeRule.setContent {
            LogEntryDetailScreen(
                entry = entryWithPhoto(),
                onOpenCamera = {},
                onEntryChanged = {},
                onAddPhoto = {},
                onRemovePhoto = {},
                onPullPhoto = {},
                onAddLocation = {},
                onSave = {},
                onCancel = {},
                onDeleteEntry = {},
                onBack = {},
            )
        }
        unloadedThenLoaded("editor")
    }

    @Test
    fun `find report thumbnail reads the same before and after the photo loads as it always did`() {
        GatedDecode.holdDecodes(decodes = 1)
        composeRule.setContent {
            LogEntryReportScreen(entry = entryWithPhoto(), onEdit = {}, onDeleteEntry = {}, onBack = {})
        }
        unloadedThenLoaded("report")
    }

    private companion object {
        const val SEMDUMP_MARKER = "NOT-RECORDED"

        /**
         * Captured on the unfixed build (`DecodedPhoto` a `Box` until loaded, then an `Image`) from the
         * failure message of the first run, whose goldens were still [SEMDUMP_MARKER]; see the class doc.
         * Read off them: the not-yet-loaded placeholder carries no ContentDescription and no Role, and
         * the loaded node carries exactly those two more, so the fix must add them only on load.
         */
        val GOLDEN: Map<String, String> = mapOf(
            "album-wired/not yet loaded" to listOf(
                "-- merged --",
                "CustomActions=[custom(Delete)]",
                "Focused=false",
                "OnClick=action(label=Open full screen)",
                "OnLongClick=action(label=Options for photo)",
                "RequestFocus=action(label=null)",
                "Shape=RectangleShape",
                "-- unmerged --",
                "CustomActions=[custom(Delete)]",
                "Focused=false",
                "OnClick=action(label=Open full screen)",
                "OnLongClick=action(label=Options for photo)",
                "RequestFocus=action(label=null)",
                "Shape=RectangleShape",
                "-- unmerged children: 0 --",
            ).joinToString("\n"),
            "album-wired/loaded" to listOf(
                "-- merged --",
                "ContentDescription=[Log photo]",
                "CustomActions=[custom(Delete)]",
                "Focused=false",
                "OnClick=action(label=Open full screen)",
                "OnLongClick=action(label=Options for photo)",
                "RequestFocus=action(label=null)",
                "Role=Image",
                "Shape=RectangleShape",
                "-- unmerged --",
                "ContentDescription=[Log photo]",
                "CustomActions=[custom(Delete)]",
                "Focused=false",
                "OnClick=action(label=Open full screen)",
                "OnLongClick=action(label=Options for photo)",
                "RequestFocus=action(label=null)",
                "Role=Image",
                "Shape=RectangleShape",
                "-- unmerged children: 0 --",
            ).joinToString("\n"),
            "album-unwired/not yet loaded" to listOf(
                "-- merged --",
                "Focused=false",
                "OnClick=action(label=Open full screen)",
                "RequestFocus=action(label=null)",
                "Shape=RectangleShape",
                "-- unmerged --",
                "Focused=false",
                "OnClick=action(label=Open full screen)",
                "RequestFocus=action(label=null)",
                "Shape=RectangleShape",
                "-- unmerged children: 0 --",
            ).joinToString("\n"),
            "album-unwired/loaded" to listOf(
                "-- merged --",
                "ContentDescription=[Log photo]",
                "Focused=false",
                "OnClick=action(label=Open full screen)",
                "RequestFocus=action(label=null)",
                "Role=Image",
                "Shape=RectangleShape",
                "-- unmerged --",
                "ContentDescription=[Log photo]",
                "Focused=false",
                "OnClick=action(label=Open full screen)",
                "RequestFocus=action(label=null)",
                "Role=Image",
                "Shape=RectangleShape",
                "-- unmerged children: 0 --",
            ).joinToString("\n"),
            "editor/not yet loaded" to listOf(
                "-- merged --",
                "Focused=false",
                "OnClick=action(label=Open full screen)",
                "RequestFocus=action(label=null)",
                "Shape=RectangleShape",
                "-- unmerged --",
                "Focused=false",
                "OnClick=action(label=Open full screen)",
                "RequestFocus=action(label=null)",
                "Shape=RectangleShape",
                "-- unmerged children: 0 --",
            ).joinToString("\n"),
            "editor/loaded" to listOf(
                "-- merged --",
                "ContentDescription=[Log photo]",
                "Focused=false",
                "OnClick=action(label=Open full screen)",
                "RequestFocus=action(label=null)",
                "Role=Image",
                "Shape=RectangleShape",
                "-- unmerged --",
                "ContentDescription=[Log photo]",
                "Focused=false",
                "OnClick=action(label=Open full screen)",
                "RequestFocus=action(label=null)",
                "Role=Image",
                "Shape=RectangleShape",
                "-- unmerged children: 0 --",
            ).joinToString("\n"),
            "report/not yet loaded" to listOf(
                "-- merged --",
                "Focused=false",
                "OnClick=action(label=Open full screen)",
                "RequestFocus=action(label=null)",
                "Shape=RectangleShape",
                "-- unmerged --",
                "Focused=false",
                "OnClick=action(label=Open full screen)",
                "RequestFocus=action(label=null)",
                "Shape=RectangleShape",
                "-- unmerged children: 0 --",
            ).joinToString("\n"),
            "report/loaded" to listOf(
                "-- merged --",
                "ContentDescription=[Log photo]",
                "Focused=false",
                "OnClick=action(label=Open full screen)",
                "RequestFocus=action(label=null)",
                "Role=Image",
                "Shape=RectangleShape",
                "-- unmerged --",
                "ContentDescription=[Log photo]",
                "Focused=false",
                "OnClick=action(label=Open full screen)",
                "RequestFocus=action(label=null)",
                "Role=Image",
                "Shape=RectangleShape",
                "-- unmerged children: 0 --",
            ).joinToString("\n"),
        )
    }
}
