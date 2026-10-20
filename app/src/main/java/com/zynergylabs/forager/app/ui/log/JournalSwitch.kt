package com.zynergylabs.forager.app.ui.log

// Moved from JournalTab.kt unchanged (UI split Phase 1, PR 10e, dispatch 2026-10-19-22).

import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag

/**
 * The Entries | Records switch (journal redesign J2, T1; plan J1): one single-choice segmented
 * button. [JournalTab] draws it full width at the top in portrait; in a short window it sits at the
 * start of the L1 row ([ShortWindowJournalHeader], J5), sized to its labels. Extracted by J5 so the
 * two places draw one control with one set of tags.
 */
@Composable
internal fun JournalSwitch(selected: JournalTopTab, onSelect: (JournalTopTab) -> Unit, modifier: Modifier = Modifier) {
    SingleChoiceSegmentedButtonRow(modifier = modifier.testTag(JOURNAL_SWITCH_TAG)) {
        JournalTopTab.entries.forEachIndexed { index, tab ->
            SegmentedButton(
                selected = selected == tab,
                onClick = { onSelect(tab) },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = JournalTopTab.entries.size),
                modifier = Modifier.testTag(journalSwitchTestTag(tab)),
                label = { Text(tab.switchLabel) },
            )
        }
    }
}

/**
 * Which of the Journal's two tabs is selected — Cartography (Journal Stage 2b's new authored entity)
 * or Records (Stage 1, gained a fourth Finds submenu in 2b). Used by [JournalTab]; `internal`, not
 * `private`, since [JournalScreenState] holds it; declared once here rather than in each file, since Kotlin does
 * not allow two files in the same package to each declare a file-private top-level type of the same
 * name.
 */
internal enum class JournalTopTab(
    /** The on-screen label on [JournalTab]'s Entries | Records switch (J2, T1): CARTOGRAPHY reads "Entries" (plan J1). */
    val switchLabel: String,
) {
    CARTOGRAPHY("Entries"),
    RECORDS("Records"),
}

/** The row holding [JournalTab]'s Entries | Records switch. */
internal const val JOURNAL_SWITCH_TAG = "journal-switch"

/** One side of [JournalTab]'s Entries | Records switch: fixed strings, not enum names, so a rename cannot move a test's target. */
internal fun journalSwitchTestTag(tab: JournalTopTab): String = when (tab) {
    JournalTopTab.CARTOGRAPHY -> "journal-switch-entries"
    JournalTopTab.RECORDS -> "journal-switch-records"
}
