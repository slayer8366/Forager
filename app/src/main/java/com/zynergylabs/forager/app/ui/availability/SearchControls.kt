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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.zynergylabs.forager.app.ui.map.MapLayersControls
import com.zynergylabs.forager.app.ui.theme.Spacing


/** A fold's header row, by its title, for tests. */
internal fun collapsibleSectionHeaderTag(title: String) = "collapsible-section-header:$title"

/**
 * The Tools drawer's search section: the Trip Planner, then Map Layers under it (dispatch 2026-10-28-02, RECORD -1764; the
 * owner: "move the map layers into the Tools drawer beneath Trip Planner as Map Layers").
 * [CompactToolsDrawerContent] hosts it. Species search and Recent Searches live in [SearchDropdown],
 * and "Advanced search" (location, radius, month) in [AdvancedSearchDropdown], both over the map, so
 * none of them is repeated here.
 *
 * Until dispatch 2026-09-28-245 the tablet's permanent drawer hosted the other two sections here
 * through [includeAdvancedSearch] and [includeRecentSearches] flags, defaulted on for it; with the
 * tablet tree removed both flags, and the parameters that only they read, are gone.
 *
 * Both folds are held by `AvailabilityScreen` ([tripPlannerOpen], [mapLayersOpen]), so each stays open across closing the
 * drawer, changing tab, a trip into Settings and a rotation, within a session (CLAUDE.md, UX defaults), and neither across a
 * restart (the dispatch: "It is not persisted across restarts."). Trip Planner's joined Map Layers' in Amendment 3 (RECORD
 * -1770; the owner: "Yes, same as Map Layers (Recommended)"); until then it was its own and reset on a trip into Settings.
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
    mapLayers: MapLayersControls = MapLayersControls(),
    mapLayersOpen: Boolean = false,
    onMapLayersOpenChange: (Boolean) -> Unit = {},
    tripPlannerOpen: Boolean = false,
    onTripPlannerOpenChange: (Boolean) -> Unit = {},
) {
    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Spacing.lg, vertical = Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        CollapsibleSection(title = "Trip Planner", expanded = tripPlannerOpen, onExpandedChange = onTripPlannerOpenChange) {
            TripPlannerSection(uiState = uiState, onDeletePlannedTrip = onDeletePlannedTrip)
        }
        MapLayersToolsSection(controls = mapLayers, open = mapLayersOpen, onOpenChange = onMapLayersOpenChange)
    }
}

/**
 * A single tappable header row that expands on tap to reveal [content], collapsing back on a
 * second tap. Collapsed the first time. This form holds its own open state, so it lasts as long as
 * the fold is composed; the Tools drawer's two folds use the hoisted form below instead, held
 * above the drawer, so they are remembered within a session (CLAUDE.md, UX defaults; Amendment 3,
 * RECORD -1770, corrected this comment, which said they reset "per the user's request"). Its one
 * remaining caller is Recent searches over the map (`SearchDropdown`).
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
    CollapsibleSection(title = title, expanded = expanded, onExpandedChange = { expanded = it }, content = content)
}

/**
 * [CollapsibleSection] with its open state held by the caller (dispatch 2026-10-28-02: Map Layers' fold, held above the
 * drawer). The header is the whole row and a tap anywhere on it opens or closes the fold. It draws no arrow (the owner:
 * "Remove all Chevrons from the drop down lists, those are banned from the app."); what the arrow's description said,
 * "Expand <title>" or "Collapse <title>", is the row's click label, so TalkBack still says which a tap will do.
 */
@Composable
internal fun CollapsibleSection(
    title: String,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickableWithShapedPress(onClickLabel = if (expanded) "Collapse $title" else "Expand $title") { onExpandedChange(!expanded) }
                .testTag(collapsibleSectionHeaderTag(title)),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium)
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
