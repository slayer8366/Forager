package com.forager.app.ui.log

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.forager.app.domain.model.CartographyEntry
import com.forager.app.ui.theme.Spacing

/**
 * The Entries/Drafts submenus' shared list — Journal Stage 2b. A grid, the same shape
 * [LogGalleryScreen]'s own gallery uses, not the plain scroll [LogEntryListScreen] uses for finds:
 * Cartography's grid columns are exactly the "more of the same thing at once" responsive knob owner
 * decision #3 calls for (one Cartography implementation, [columns] the only thing that changes
 * between window classes).
 *
 * [onAddEntry] renders the same "+" tile precedent [LogGalleryScreen]'s `AddEntryTile` established,
 * `null` for the Drafts list — starting a *new* entry from Drafts would read as "add a draft," not a
 * distinct action from "add an entry," the same reasoning [LogGalleryScreen] already applies.
 *
 * A card names its date, its tag chips (if any), and kept-item counts — **never whether it has
 * writing**. Per `amendment-2b-optional-writing.md`: a wordless entry with kept items is complete,
 * not incomplete, so no card here carries an "Incomplete"-style badge the way [LogGalleryScreen]'s
 * find tiles do; [MushroomLogEntry] is a different entity with a different completeness question,
 * and none of that framing carries over to entries that never claimed to be structured records.
 */
@Composable
internal fun CartographyEntryListScreen(
    entries: List<CartographyEntry>,
    isLoading: Boolean,
    onOpenEntry: (String) -> Unit,
    modifier: Modifier = Modifier,
    onAddEntry: (() -> Unit)? = null,
    loadErrorMessage: String? = null,
    /** Grid column count — 2 for compact, more for expanded/tablet. See this composable's own doc comment on owner decision #3. */
    columns: Int = 2,
) {
    if (isLoading && entries.isEmpty()) {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    if (entries.isEmpty() && onAddEntry == null && loadErrorMessage == null) {
        Text(
            "No drafts. An entry you haven't finished shows up here.",
            style = MaterialTheme.typography.bodyMedium,
            modifier = modifier.fillMaxWidth().padding(Spacing.lg),
        )
        return
    }

    Column(modifier = modifier.fillMaxSize()) {
        if (entries.isEmpty() && loadErrorMessage != null) {
            Text(
                loadErrorMessage,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.fillMaxWidth().padding(Spacing.lg),
            )
        }
        LazyVerticalGrid(
            columns = GridCells.Fixed(columns),
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(Spacing.lg),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            if (onAddEntry != null) item { AddCartographyEntryTile(onClick = onAddEntry) }
            items(entries, key = { it.id }) { entry -> CartographyEntryTile(entry = entry, onClick = { onOpenEntry(entry.id) }) }
        }
    }
}

/** Mirrors [LogGalleryScreen]'s `AddEntryTile` — a blank outline with a centered `+`. */
@Composable
private fun AddCartographyEntryTile(onClick: () -> Unit, modifier: Modifier = Modifier) {
    OutlinedCard(
        onClick = onClick,
        modifier = modifier.fillMaxWidth().aspectRatio(ENTRY_TILE_ASPECT_RATIO),
        shape = RoundedCornerShape(Spacing.sm),
        border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.outline),
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(
                Icons.Filled.Add,
                contentDescription = "New Cartography entry",
                tint = MaterialTheme.colorScheme.outline,
                modifier = Modifier.padding(Spacing.sm),
            )
        }
    }
}

@Composable
private fun CartographyEntryTile(entry: CartographyEntry, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val keptCount = entry.keptFinds.size + entry.keptTracks.size + entry.keptWaypoints.size + entry.keptOfflineRegions.size
    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth().aspectRatio(ENTRY_TILE_ASPECT_RATIO),
        shape = RoundedCornerShape(Spacing.sm),
    ) {
        Column(modifier = Modifier.fillMaxSize().padding(Spacing.sm), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Text(entry.date.toString(), style = MaterialTheme.typography.labelLarge)
            if (entry.text.isNotBlank()) {
                Text(
                    entry.text,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
            } else {
                Box(modifier = Modifier.weight(1f))
            }
            if (entry.tags.isNotEmpty()) {
                Text(
                    entry.tags.joinToString(" · "),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                if (keptCount == 1) "1 kept item" else "$keptCount kept items",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private const val ENTRY_TILE_ASPECT_RATIO = 0.85f
