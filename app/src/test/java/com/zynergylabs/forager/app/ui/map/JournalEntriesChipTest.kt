package com.zynergylabs.forager.app.ui.map

import androidx.compose.runtime.remember
import android.app.Application
import android.content.ComponentName
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.test.core.app.ApplicationProvider
import com.zynergylabs.forager.app.domain.JournalEntryOnMap
import com.zynergylabs.forager.app.domain.model.LatLng
import com.zynergylabs.forager.app.ui.theme.Bark
import com.zynergylabs.forager.app.ui.theme.ForagerTheme
import java.time.LocalDate
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
 * J8's new map chrome at the map chrome's standing opacity (CLAUDE.md, UX defaults; planner's Q-A
 * ruling, continuation `2026-09-28-64`): the Maps-tab chip takes the taxon chip's own colour source,
 * [MapIconStackButtonColorDark] and [MapIconStackButtonColorLight], and its content colour; the chip's
 * list, and a highlighted record's bubble's list of keeping entries, are menus over the map (owner's
 * edge-case ruling 1, "80% over the map"), so their container is Material3's default menu role at
 * [MAP_CHROME_OVER_MAP_ALPHA] with that role's own content colour, opaque, as the Layers sheet does.
 * The colours are read from the composed nodes' semantics.
 *
 * Also the bubble's entry lines as drawn (owner's Q2 ruling, "Tap a date line"): one date line each up
 * to three, labelled "Open entry <date>", and past three one count line over an untitled list.
 * What this cannot check: how 80% reads over each basemap on a screen. A device item.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class JournalEntriesChipTest {

    private val composeRule = createComposeRule()

    private val declareHostActivity = object : ExternalResource() {
        override fun before() {
            val app = ApplicationProvider.getApplicationContext<Application>()
            Shadows.shadowOf(app.packageManager).addActivityIfNotPresent(ComponentName(app, ComponentActivity::class.java))
        }
    }

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(declareHostActivity).around(composeRule)

    private val entries = listOf(
        JournalEntryOnMap("a", LocalDate.of(2026, 9, 12)),
        JournalEntryOnMap("b", LocalDate.of(2026, 9, 5)),
        JournalEntryOnMap("c", LocalDate.of(2026, 9, 3)),
        JournalEntryOnMap("d", LocalDate.of(2026, 9, 1)),
    )

    private var menuRole = Color.Unspecified
    private var menuRoleContent = Color.Unspecified
    private val opened = mutableListOf<String>()

    private fun setChip(dark: Boolean) {
        composeRule.setContent {
            ForagerTheme(darkTheme = dark) {
                menuRole = MenuDefaults.containerColor
                menuRoleContent = MaterialTheme.colorScheme.onSurface
                Box(Modifier.fillMaxSize()) {
                    JournalEntriesMapChip(entries = entries.take(2), onHide = {}, onHideAll = {})
                }
            }
        }
        composeRule.waitForIdle()
    }

    private fun setBubble(keptIn: List<JournalEntryOnMap>) {
        composeRule.setContent {
            ForagerTheme(darkTheme = true) {
                menuRole = MenuDefaults.containerColor
                menuRoleContent = MaterialTheme.colorScheme.onSurface
                Box(Modifier.fillMaxSize()) {
                    MapFeatureBubble(
                        content = MapBubbleContent.Find(findId = "find-1", title = "Golden chanterelle", date = "Sep 12, 2026", coverPhotoPath = null, keptIn = keptIn),
                        tipInBubble = remember { mutableStateOf(null) },
                        onDismiss = {},
                        openFindLabel = OPEN_FIND_LABEL,
                        onOpenFind = null,
                        onViewPhoto = {},
                        onDirections = { _: String, _: LatLng -> },
                        onDetails = {},
                        onOpenEntry = { opened += it },
                    )
                }
            }
        }
        composeRule.waitForIdle()
    }

    private fun touchCentreOf(tag: String) {
        composeRule.onNodeWithTag(tag).performTouchInput { click(center) }
        composeRule.waitForIdle()
    }

    private fun menuColours(tag: String): Pair<Color, Color> {
        val node = composeRule.onNodeWithTag(tag).fetchSemanticsNode()
        return node.config[JournalMenuContainerColor] to node.config[JournalMenuContentColor]
    }

    // ── The chip ──

    @Test
    fun `by day the chip takes the taxon chip's light colour, at the map chrome's opacity, with Bark content`() {
        setChip(dark = false)
        val node = composeRule.onNodeWithTag(JOURNAL_ENTRIES_CHIP_TAG).fetchSemanticsNode()
        val container = node.config[JournalEntriesChipContainerColor]
        assertEquals("container alpha", MAP_CHROME_OVER_MAP_ALPHA, container.alpha, 0.001f)
        assertEquals("the taxon chip's own colour source", MapIconStackButtonColorLight, container)
        assertEquals("content, opaque", Bark, node.config[JournalEntriesChipContentColor])
    }

    @Test
    fun `at night the chip takes the taxon chip's dark colour, at the map chrome's opacity, with white content`() {
        setChip(dark = true)
        val node = composeRule.onNodeWithTag(JOURNAL_ENTRIES_CHIP_TAG).fetchSemanticsNode()
        val container = node.config[JournalEntriesChipContainerColor]
        assertEquals("container alpha", MAP_CHROME_OVER_MAP_ALPHA, container.alpha, 0.001f)
        assertEquals("the taxon chip's own colour source", MapIconStackButtonColorDark, container)
        assertEquals("content, opaque", Color.White, node.config[JournalEntriesChipContentColor])
    }

    @Test
    fun `the chip's list is the default menu role at the map chrome's opacity, its content that role's own, opaque`() {
        setChip(dark = true)
        touchCentreOf(JOURNAL_ENTRIES_CHIP_TAG)

        composeRule.onNodeWithTag(JOURNAL_ENTRIES_LIST_TAG).assertExists()
        val (container, content) = menuColours(JOURNAL_ENTRIES_LIST_TAG)
        assertEquals("container alpha", MAP_CHROME_OVER_MAP_ALPHA, container.alpha, 0.001f)
        assertEquals("container colour, alpha aside", menuRole.copy(alpha = 1f), container.copy(alpha = 1f))
        assertEquals("content colour", menuRoleContent, content)
        assertEquals("content alpha", 1f, content.alpha, 0.001f)
    }

    // ── A highlighted record's bubble ──

    @Test
    fun `up to three keeping entries are each a date line labelled Open entry and its date, and a touch opens that entry`() {
        setBubble(keptIn = entries.take(2))

        composeRule.onNodeWithTag(mapBubbleEntryLineTag("a")).assertIsDisplayed().assertContentDescriptionEquals("Open entry Sep 12, 2026")
        composeRule.onNodeWithTag(mapBubbleEntryLineTag("b")).assertIsDisplayed().assertContentDescriptionEquals("Open entry Sep 5, 2026")
        composeRule.onNodeWithText("Sep 5, 2026", useUnmergedTree = true).assertIsDisplayed()
        assertEquals("no count line for two", 0, composeRule.onAllNodesWithTag(MAP_BUBBLE_ENTRY_COUNT_TAG).fetchSemanticsNodes().size)

        touchCentreOf(mapBubbleEntryLineTag("b"))
        assertEquals(listOf("b"), opened)
    }

    @Test
    fun `more than three keeping entries are one line, Kept in 4 journal entries, whose list of dates is a menu at the map chrome's opacity`() {
        setBubble(keptIn = entries)

        composeRule.onNodeWithTag(MAP_BUBBLE_ENTRY_COUNT_TAG).assertIsDisplayed().assertTextEquals("In 4 journal entries")
        assertEquals("no date lines past three", 0, composeRule.onAllNodesWithTag(mapBubbleEntryLineTag("a")).fetchSemanticsNodes().size)

        touchCentreOf(MAP_BUBBLE_ENTRY_COUNT_TAG)
        composeRule.onNodeWithTag(MAP_BUBBLE_ENTRY_LIST_TAG).assertExists()
        val (container, content) = menuColours(MAP_BUBBLE_ENTRY_LIST_TAG)
        assertEquals("container alpha", MAP_CHROME_OVER_MAP_ALPHA, container.alpha, 0.001f)
        assertEquals("container colour, alpha aside", menuRole.copy(alpha = 1f), container.copy(alpha = 1f))
        assertEquals("content colour", menuRoleContent, content)
        composeRule.onNode(SemanticsMatcher.expectValue(androidx.compose.ui.semantics.SemanticsProperties.ContentDescription, listOf("Open entry Sep 3, 2026")))
            .assertExists()

        touchCentreOf(mapBubbleEntryLineTag("c"))
        assertEquals(listOf("c"), opened)
    }
}
