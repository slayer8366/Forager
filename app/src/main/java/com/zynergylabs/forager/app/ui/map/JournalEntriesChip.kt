package com.zynergylabs.forager.app.ui.map

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsPropertyKey
import androidx.compose.ui.semantics.SemanticsPropertyReceiver
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import com.zynergylabs.forager.app.domain.JournalEntryOnMap
import com.zynergylabs.forager.app.ui.theme.navigationBarContainerColor
import com.zynergylabs.forager.app.ui.theme.Bark
import com.zynergylabs.forager.app.ui.theme.LocalForagerDarkTheme
import com.zynergylabs.forager.app.ui.theme.Spacing

/** The Maps-tab chip (J8-3). */
internal const val JOURNAL_ENTRIES_CHIP_TAG = "journal-entries-chip"

/** The chip's list of shown entries. */
internal const val JOURNAL_ENTRIES_LIST_TAG = "journal-entries-list"

/** One shown entry's row in the chip's list, whose tap hides that entry. */
internal fun journalEntriesListRowTag(entryId: String) = "journal-entries-list-row-$entryId"

/** The chip list's "Hide all". */
internal const val JOURNAL_ENTRIES_HIDE_ALL_TAG = "journal-entries-hide-all"

/** The chip's container colour, read by tests from the composed chip's semantics. */
internal val JournalEntriesChipContainerColor = SemanticsPropertyKey<Color>("JournalEntriesChipContainerColor")
private var SemanticsPropertyReceiver.journalEntriesChipContainerColor by JournalEntriesChipContainerColor

/** The chip's content colour, as its text is drawn in. */
internal val JournalEntriesChipContentColor = SemanticsPropertyKey<Color>("JournalEntriesChipContentColor")
private var SemanticsPropertyReceiver.journalEntriesChipContentColor by JournalEntriesChipContentColor

/** A J8 menu's container colour: the chip's list, and a bubble's list of keeping entries. */
internal val JournalMenuContainerColor = SemanticsPropertyKey<Color>("JournalMenuContainerColor")
private var SemanticsPropertyReceiver.journalMenuContainerColor by JournalMenuContainerColor

/** A J8 menu's content colour, as its rows' text is drawn in. */
internal val JournalMenuContentColor = SemanticsPropertyKey<Color>("JournalMenuContentColor")
private var SemanticsPropertyReceiver.journalMenuContentColor by JournalMenuContentColor

/**
 * A J8 menu over the map, the chip's list and a bubble's list of keeping entries: Material3's own
 * default menu container role (`MenuDefaults.containerColor`) at [MAP_CHROME_OVER_MAP_ALPHA] (the
 * owner's edge-case ruling 1 for menus over a map, "80% over the map"; planner, continuation
 * `2026-09-28-64`), as the Layers sheet puts its own sheet role at that alpha (`MapLayersSheet`).
 */
@Composable
internal fun journalMenuContainerColor(): Color = navigationBarContainerColor().copy(alpha = MAP_CHROME_OVER_MAP_ALPHA)

/**
 * A J8 menu's content colour: the default menu role's own content colour, opaque. Pinned to the
 * unaltered role for the reason the Layers sheet gives: `contentColorFor` matches a colour-scheme role
 * exactly, and given the container at 80% it matches none and falls back to `LocalContentColor`.
 */
@Composable
internal fun journalMenuContentColor(): Color = contentColorFor(navigationBarContainerColor())

/** Marks a J8 menu's content with its colours, for tests. */
internal fun Modifier.journalMenuColours(container: Color, content: Color): Modifier =
    semantics {
        journalMenuContainerColor = container
        journalMenuContentColor = content
    }

/**
 * The Maps-tab chip (J8-3; owner: "Top, by the species chip (Recommended)"): how many saved entries are
 * shown on the map ([journalEntriesChipLabel]), composed only while at least one is. A tap lists them by
 * the report header's date ([journalEntryDateLabel]), one row each, whose tap hides that entry ("Hide"),
 * with "Hide all" last; hiding writes `shownOnMap` through the host's callbacks and never touches the
 * Layers sheet's "Journal entries" switch.
 *
 * Its fill is the taxon chip's own colour source, [MapIconStackButtonColorDark] and
 * [MapIconStackButtonColorLight] at [MAP_CHROME_OVER_MAP_ALPHA], with the taxon chip's content colours
 * (planner's Q-A ruling, continuation `2026-09-28-64`, under CLAUDE.md's UX default that new map chrome
 * starts at 80%), so the row reads as one. The list is a menu over the map at the same alpha
 * ([journalMenuContainerColor]). The chip is a [Surface] sized to its text and the list a popup, so
 * nothing here takes a touch outside the pill itself (CLAUDE.md, the Surface pitfall); a host places it
 * in a row bounded to its content.
 *
 * No shadow (J8 follow-ups, continuation `2026-09-28-87`, item 3; the planner's ruling, "Layered fills
 * composite to that value; they do not each carry it"): a `Surface` shadow is drawn beneath its fill, so
 * under this translucent fill the shadow added to the 0.8. J8's device check measured the chip at 0.833
 * to 0.840 on the S22 and put that down to its 4 dp shadow; that is an inference, re-measured on the
 * device. The taxon chip beside it has no shadow either.
 */
@Composable
internal fun JournalEntriesMapChip(
    entries: List<JournalEntryOnMap>,
    onHide: (String) -> Unit,
    onHideAll: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    val isDarkTheme = LocalForagerDarkTheme.current
    val container = if (isDarkTheme) MapIconStackButtonColorDark else MapIconStackButtonColorLight
    val content = if (isDarkTheme) Color.White else Bark
    Box(modifier = modifier) {
        Surface(
            onClick = { expanded = true },
            modifier = Modifier
                .testTag(JOURNAL_ENTRIES_CHIP_TAG)
                .semantics {
                    journalEntriesChipContainerColor = container
                    journalEntriesChipContentColor = content
                },
            shape = RoundedCornerShape(percent = 50),
            color = container,
            contentColor = content,
        ) {
            Text(
                journalEntriesChipLabel(entries.size),
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.sm),
            )
        }
        val menuContainer = journalMenuContainerColor()
        val menuContent = journalMenuContentColor()
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            containerColor = menuContainer,
            modifier = Modifier.testTag(JOURNAL_ENTRIES_LIST_TAG).journalMenuColours(menuContainer, menuContent),
        ) {
            CompositionLocalProvider(LocalContentColor provides menuContent) {
                entries.forEach { entry ->
                    DropdownMenuItem(
                        text = { Text(journalEntryDateLabel(entry.date)) },
                        trailingIcon = { Text(HIDE_JOURNAL_ENTRY_LABEL, color = MaterialTheme.colorScheme.primary) },
                        onClick = {
                            if (entries.size == 1) expanded = false
                            onHide(entry.entryId)
                        },
                        colors = MenuDefaults.itemColors(textColor = menuContent),
                        modifier = Modifier.testTag(journalEntriesListRowTag(entry.entryId)),
                    )
                }
                HorizontalDivider()
                DropdownMenuItem(
                    text = { Text(HIDE_ALL_JOURNAL_ENTRIES_LABEL) },
                    onClick = {
                        expanded = false
                        onHideAll()
                    },
                    colors = MenuDefaults.itemColors(textColor = menuContent),
                    modifier = Modifier.testTag(JOURNAL_ENTRIES_HIDE_ALL_TAG),
                )
            }
        }
    }
}
