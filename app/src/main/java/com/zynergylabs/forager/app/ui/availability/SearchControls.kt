package com.zynergylabs.forager.app.ui.availability

// Moved unchanged from AvailabilitySearchUi.kt (dispatch 2026-10-19-17, UI split Phase 1, PR 10a).

import com.zynergylabs.forager.app.ui.motion.clickableWithShapedPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.zynergylabs.forager.app.ui.theme.Spacing


/**
 * The Tools drawer's search section: the Trip Planner (the one thing it still holds).
 * [CompactToolsDrawerContent] hosts it. Species search and Recent Searches live in [SearchDropdown],
 * and "Advanced search" (location, radius, month) in [AdvancedSearchDropdown], both over the map, so
 * none of them is repeated here.
 *
 * Until dispatch 2026-09-28-245 the tablet's permanent drawer hosted the other two sections here
 * through [includeAdvancedSearch] and [includeRecentSearches] flags, defaulted on for it; with the
 * tablet tree removed both flags, and the parameters that only they read, are gone.
 *
 * The scroll modifier on the outer [Column] is not optional: a drawer sheet is a fixed-height
 * container, so without it the section would be unreachable on a short screen or at a large font
 * scale.
 */
@Composable
internal fun SearchControls(
    modifier: Modifier = Modifier,
    uiState: AvailabilityUiState,
    onDeletePlannedTrip: (String) -> Unit,
    /**
     * Drawn under the Trip Planner's header, in the same scroll: the Tools page's Sundown section
     * (dispatch 2026-09-28-707). A slot rather than a second scrolling column, because a scroll nested
     * in a scroll on the same axis is measured with infinite height and cannot lay out. Under the
     * Trip Planner rather than above it so the drawer still opens with the Trip Planner's one-line
     * header at its top, which six test classes read as "the drawer is open"; the Trip Planner
     * starts collapsed, so the Sundown section is the second thing on the page.
     */
    following: @Composable ColumnScope.() -> Unit = {},
) {
    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Spacing.lg, vertical = Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        CollapsibleSection(title = "Trip Planner") {
            TripPlannerSection(uiState = uiState, onDeletePlannedTrip = onDeletePlannedTrip)
        }
        following()
    }
}

/**
 * A single tappable header row that expands on tap to reveal [content], collapsing back on a
 * second tap. Collapsed by default: both drawer sections start as one line, per the user's
 * request, rather than remembering whichever state they were last left in.
 *
 * Lives in this file rather than becoming a generic shared component, because both call sites
 * (see [SearchControls]) are this drawer's two sections and nothing else in the app has asked for
 * this pattern yet — CLAUDE.md: no speculative generality ahead of a second real caller.
 */
@Composable
// internal rather than private: com.zynergylabs.forager.app.ui.log's own drawer panel reuses this exact
// "tap header to expand" shape for its record sections, rather than re-implementing the same
// molecule a second time.
internal fun CollapsibleSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    // The one-shot expandRequested/onExpandRequestConsumed pair that was here had one caller, the
    // search panel's two coordinate folds, which dispatch 2026-09-28-697 removed; so did it.
    var expanded by remember { mutableStateOf(false) }
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickableWithShapedPress { expanded = !expanded },
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Icon(
                imageVector = if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                contentDescription = if (expanded) "Collapse $title" else "Expand $title",
            )
        }
        if (expanded) {
            Column(
                modifier = Modifier.padding(top = Spacing.md),
                verticalArrangement = Arrangement.spacedBy(Spacing.lg),
            ) {
                content()
            }
        }
    }
}
