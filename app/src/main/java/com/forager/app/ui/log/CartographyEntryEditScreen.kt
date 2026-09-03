package com.forager.app.ui.log

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.forager.app.domain.ComputeTrackStatisticsUseCase
import com.forager.app.domain.OfflineRegionSummary
import com.forager.app.domain.model.CartographyEntry
import com.forager.app.domain.model.DerivedTrip
import com.forager.app.domain.model.DistanceUnit
import com.forager.app.domain.model.GalleryPhoto
import com.forager.app.domain.model.PhotoAttachment
import com.forager.app.domain.model.formatDistanceKm
import com.forager.app.ui.theme.Spacing
import java.time.Instant
import java.time.ZoneId
import kotlin.math.roundToInt

/**
 * A Cartography entry's curation-and-writing surface — Journal Stage 2b, extended by its own
 * follow-up dispatch (point 2). [candidates]/[candidateOfflineRegions] are that day's *live* trip
 * report, reloaded on every open (creation or reopen); each section below merges them against the
 * entry's own persisted decisions into three states per candidate — **kept**, **withheld**, or **not
 * yet decided** — via [mergeDecisionRows]. A `null` [candidates] means the trip report hasn't finished
 * loading yet (or failed); every section falls back to the entry's own already-decided rows only, so
 * the screen still renders sensibly rather than going blank.
 *
 * **No field here is required, and nothing here reads as incomplete for being empty** —
 * `amendment-2b-optional-writing.md`: selection alone is a complete act of authorship, prose is one
 * optional component. The writing field's own placeholder says "optional" rather than prompting
 * completion, and [onFinish] (shown only while [CartographyEntry.isDraft]) never gates on [entry.text]
 * or any decided-item count.
 *
 * ## Save/Discard/Cancel for a committed entry (device-check patch, Item 1)
 *
 * A **draft** still autosaves silently on every change — [onFinish] ("Finish entry") is its only
 * button, and [onBack] still just closes the form with no prompt, unchanged from before this
 * dispatch. A **committed** entry ([CartographyEntry.isDraft] `false`) is different: changes are
 * explicit now, tracked by [hasUnsavedChanges]. This screen shows a Save button for one (mirroring
 * where "Finish entry" sits for a draft — the two are mutually exclusive since a single entry is
 * never both), calling [onSave], which persists in place without leaving. Tapping back
 * ([onBack]'s icon) while [hasUnsavedChanges] instead asks — Save / Discard / Cancel — rather than
 * silently discarding the way a draft's own close does; Cancel dismisses the prompt and stays right
 * here, Save calls [onSave] then leaves, Discard calls [onDiscardChanges] (which reloads the entry
 * from the database and leaves in one step — see that callback's own doc comment for why it's not
 * an in-memory revert). Backgrounding a committed entry with unsaved changes is deliberately **not**
 * handled here: there is no window to show this prompt in at that moment, so `AvailabilityScreen`
 * saves silently on its own lifecycle hook instead — see that file's own doc comment on why that's
 * not a reason to discard on, say, a phone call.
 *
 * ## The leave prompt is hoisted, not local (back-nav-and-save-flow dispatch, Items 1/2)
 *
 * [showLeavePrompt]/[onRequestBack]/[onDismissLeavePrompt] used to be this composable's own local
 * `confirmingLeave` state and a private `requestBack()` wrapper around [onBack], reachable only by
 * the arrow below. System back was routed entirely around it — [CartographyScreen] had no
 * `BackHandler` at all reaching this deep, so back exited the whole Journal in one step and this
 * dialog never fired regardless of [hasUnsavedChanges]. [CartographyScreen] now owns the same
 * decision (its own `requestLeaveEntry()`) so its `BackHandler` and this screen's own arrow drive
 * the identical prompt through the identical path — see that composable's own doc comment. [onBack]
 * itself stays the raw, unconditional close: the persistent Save button below and the leave
 * prompt's own Save option both call `onSave()` then `onBack()` directly, since a just-saved entry
 * has nothing left to ask about.
 */
@Composable
internal fun CartographyEntryEditScreen(
    entry: CartographyEntry,
    candidates: DerivedTrip?,
    candidateOfflineRegions: List<OfflineRegionSummary>,
    isLoadingCandidates: Boolean,
    galleryPhotos: List<GalleryPhoto>,
    distanceUnit: DistanceUnit,
    /** Only meaningful while `!entry.isDraft` — see this composable's own doc comment on the Save/Discard/Cancel policy. */
    hasUnsavedChanges: Boolean,
    /** Hoisted from [CartographyScreen] — see this composable's own doc comment, "The leave prompt is hoisted, not local." */
    showLeavePrompt: Boolean,
    /** What the arrow below calls, and what [CartographyScreen]'s own `BackHandler` calls too — the one decision point for "leaving with unsaved changes needs to ask." */
    onRequestBack: () -> Unit,
    /** Cancel's own action — dismiss the prompt, stay right here. */
    onDismissLeavePrompt: () -> Unit,
    onTextChanged: (String) -> Unit,
    onTagsChanged: (List<String>) -> Unit,
    onSetFindDecision: (String, Boolean) -> Unit,
    onSetTrackDecision: (String, Boolean) -> Unit,
    onSetWaypointDecision: (String, Boolean) -> Unit,
    onSetOfflineRegionDecision: (Long, Boolean) -> Unit,
    onToggleKeptPhoto: (String) -> Unit,
    onFinish: () -> Unit,
    /** Explicit Save for a committed entry — saves in place. Both call sites below then call [onBack] themselves: the persistent button (back-nav-and-save-flow dispatch, Item 3 — save confirms, then exits) and the leave prompt's own Save option. A no-op call for a draft (never shown either). */
    onSave: () -> Unit,
    /** The leave-prompt's Discard option — reloads the entry from the database and leaves in one step. See this composable's own doc comment. */
    onDiscardChanges: () -> Unit,
    onDeleteEntry: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var pullingPhoto by remember(entry.id) { mutableStateOf(false) }
    if (pullingPhoto) {
        PullPhotoPickerScreen(
            photos = galleryPhotos,
            onPhotoSelected = { photo -> onToggleKeptPhoto(photo.id); pullingPhoto = false },
            modifier = modifier.fillMaxSize(),
        )
        return
    }

    var menuExpanded by remember(entry.id) { mutableStateOf(false) }
    var confirmingDelete by remember(entry.id) { mutableStateOf(false) }
    var tagsText by remember(entry.id) { mutableStateOf(entry.tags.joinToString(", ")) }

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.lg, vertical = Spacing.sm),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                IconButton(onClick = onRequestBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back to Cartography")
                }
                Text(entry.date.toString(), style = MaterialTheme.typography.titleMedium)
                if (isLoadingCandidates) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp))
                }
            }
            Box {
                IconButton(onClick = { menuExpanded = true }) {
                    Icon(Icons.Filled.MoreVert, contentDescription = "Entry options")
                }
                DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                    DropdownMenuItem(
                        text = { Text("Delete entry") },
                        leadingIcon = { Icon(Icons.Filled.Delete, contentDescription = null) },
                        onClick = { menuExpanded = false; confirmingDelete = true },
                    )
                }
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.lg),
        ) {
            OutlinedTextField(
                value = entry.text,
                onValueChange = onTextChanged,
                label = { Text("Your own account (optional)") },
                placeholder = { Text("Let the photos and selections speak, or write about the day.") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 3,
            )

            OutlinedTextField(
                value = tagsText,
                onValueChange = { text ->
                    tagsText = text
                    onTagsChanged(text.split(",").map { it.trim() }.filter { it.isNotEmpty() })
                },
                label = { Text("Tags (optional, comma-separated)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )

            HorizontalDivider()

            KeptPhotosSection(
                entry = entry,
                galleryPhotos = galleryPhotos,
                onToggleKeptPhoto = onToggleKeptPhoto,
                onAddFromAlbum = { pullingPhoto = true },
            )

            FindsSection(entry = entry, candidates = candidates, onSetDecision = onSetFindDecision)
            TracksSection(entry = entry, candidates = candidates, distanceUnit = distanceUnit, onSetDecision = onSetTrackDecision)
            WaypointsSection(entry = entry, candidates = candidates, onSetDecision = onSetWaypointDecision)
            OfflineRegionsSection(
                entry = entry,
                candidates = candidateOfflineRegions,
                distanceUnit = distanceUnit,
                onSetDecision = onSetOfflineRegionDecision,
            )

            if (entry.isDraft) {
                Button(onClick = onFinish, modifier = Modifier.fillMaxWidth()) { Text("Finish entry") }
            } else {
                // Back-nav-and-save-flow dispatch, Item 3: save confirms, then exits to the
                // previous screen — the standard shape, not stay-on-screen. Composed here rather
                // than folded into onSave itself so onSave alone (also used by the leave prompt's
                // own Save option below) stays "just persist," with each call site deciding
                // separately whether leaving afterward makes sense.
                Button(onClick = { onSave(); onBack() }, modifier = Modifier.fillMaxWidth()) { Text("Save") }
            }

            Spacer(modifier = Modifier.heightIn(min = Spacing.lg))
        }
    }

    if (confirmingDelete) {
        AlertDialog(
            onDismissRequest = { confirmingDelete = false },
            title = { Text("Delete this entry?") },
            text = { Text("This removes the entry and its kept selections. The finds, tracks, waypoints, and regions it kept stay in Records.") },
            confirmButton = {
                TextButton(onClick = { confirmingDelete = false; onDeleteEntry() }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { confirmingDelete = false }) { Text("Cancel") } },
        )
    }

    if (showLeavePrompt) {
        AlertDialog(
            onDismissRequest = onDismissLeavePrompt,
            title = { Text("Save your changes?") },
            text = { Text("This entry has unsaved changes. Save them, discard them, or keep editing.") },
            confirmButton = {
                TextButton(
                    onClick = { onDismissLeavePrompt(); onSave(); onBack() },
                    modifier = Modifier.testTag(LEAVE_PROMPT_SAVE_TEST_TAG),
                ) { Text("Save") }
            },
            dismissButton = {
                Row {
                    TextButton(
                        onClick = { onDismissLeavePrompt(); onDiscardChanges() },
                        modifier = Modifier.testTag(LEAVE_PROMPT_DISCARD_TEST_TAG),
                    ) { Text("Discard") }
                    TextButton(
                        onClick = onDismissLeavePrompt,
                        modifier = Modifier.testTag(LEAVE_PROMPT_CANCEL_TEST_TAG),
                    ) { Text("Cancel") }
                }
            },
        )
    }
}

/** [showLeavePrompt]'s own three buttons — tagged since the screen's own persistent "Save" button (shown for any committed entry) otherwise collides with this dialog's identically-labelled one for `onNodeWithText` in tests. */
internal const val LEAVE_PROMPT_SAVE_TEST_TAG = "cartography_leave_prompt_save"
internal const val LEAVE_PROMPT_DISCARD_TEST_TAG = "cartography_leave_prompt_discard"
internal const val LEAVE_PROMPT_CANCEL_TEST_TAG = "cartography_leave_prompt_cancel"

@Composable
private fun KeptPhotosSection(
    entry: CartographyEntry,
    galleryPhotos: List<GalleryPhoto>,
    onToggleKeptPhoto: (String) -> Unit,
    onAddFromAlbum: () -> Unit,
) {
    val photosById = galleryPhotos.associateBy { it.photo.id }
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        Text("Photos", style = MaterialTheme.typography.titleSmall)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            entry.photos.forEach { attachment ->
                Box {
                    KeptPhotoOrUnavailable(
                        attachment = attachment,
                        photo = photosById[attachment.photoId],
                        modifier = Modifier.size(KEPT_PHOTO_SIZE_DP.dp),
                    )
                    IconButton(
                        onClick = { onToggleKeptPhoto(attachment.photoId) },
                        modifier = Modifier.align(Alignment.TopEnd).size(24.dp),
                    ) {
                        Icon(Icons.Filled.Close, contentDescription = "Remove this photo", tint = MaterialTheme.colorScheme.error)
                    }
                }
            }
            IconButton(onClick = onAddFromAlbum) {
                Icon(Icons.Filled.Add, contentDescription = "Add a photo from the Album")
            }
        }
    }
}

/**
 * One kept photo attachment, resolved against the live gallery — [photo] is the [GalleryPhoto] row
 * backing [attachment] if it still exists, `null` if the gallery row is gone (see
 * [com.forager.app.data.local.CartographyEntryPhotoRefEntity]'s own doc comment for why this stays
 * visible with its attach date rather than silently vanishing). Stage 2c: shared between
 * [KeptPhotosSection] (which adds its own remove-button overlay) and [CartographyEntryReportScreen]'s
 * read-only photo grid (no overlay) — extracted so the "Photo unavailable" fallback exists in exactly
 * one place, not rebuilt a second time for the view screen.
 */
@Composable
internal fun KeptPhotoOrUnavailable(attachment: PhotoAttachment, photo: GalleryPhoto?, modifier: Modifier = Modifier) {
    if (photo != null) {
        DecodedPhoto(relativePath = photo.photo.relativePath, modifier = modifier)
    } else {
        Box(modifier = modifier, contentAlignment = Alignment.Center) {
            Text(
                "Photo unavailable\n(attached ${attachDateLabel(attachment.attachedAtEpochMillis)})",
                style = MaterialTheme.typography.labelSmall,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun FindsSection(entry: CartographyEntry, candidates: DerivedTrip?, onSetDecision: (String, Boolean) -> Unit) {
    val rows = mergeDecisionRows(
        decided = entry.findDecisions,
        decidedId = { it.findId },
        decidedRow = { DecisionRowState(id = it.findId, title = "Find on ${it.foundOn}", subtitle = it.ownIdentification, state = it.state) },
        candidates = candidates?.finds.orEmpty(),
        candidateId = { it.id },
        candidateRow = { DecisionRowState(id = it.id, title = "Find on ${it.foundOn}", subtitle = it.ownIdentification, state = DecisionState.UNDECIDED) },
    )
    DecisionSection(title = "Finds", rows = rows, onSetDecision = onSetDecision)
}

@Composable
private fun TracksSection(entry: CartographyEntry, candidates: DerivedTrip?, distanceUnit: DistanceUnit, onSetDecision: (String, Boolean) -> Unit) {
    val rows = mergeDecisionRows(
        decided = entry.trackDecisions,
        decidedId = { it.trackId },
        decidedRow = {
            DecisionRowState(
                id = it.trackId,
                title = it.name ?: "Recorded track",
                subtitle = trackSubtitle(it.distanceMeters, it.durationMillis, distanceUnit),
                state = it.state,
            )
        },
        candidates = candidates?.tracks.orEmpty(),
        candidateId = { it.id },
        candidateRow = { track ->
            // Recomputed directly rather than cached: a day's track list is small, and this only
            // runs while candidates are loaded, not a hot recomposition path — same one-computation-
            // per-item scale TrackRecordingViewModel.loadWaypoints' own doc comment accepts for 4b.
            val stats = ComputeTrackStatisticsUseCase()(track.points)
            DecisionRowState(
                id = track.id,
                title = track.name ?: "Recorded track",
                subtitle = trackSubtitle(stats.distanceMeters, stats.durationMillis, distanceUnit),
                state = DecisionState.UNDECIDED,
            )
        },
    )
    DecisionSection(title = "Tracks", rows = rows, onSetDecision = onSetDecision)
}

@Composable
private fun WaypointsSection(entry: CartographyEntry, candidates: DerivedTrip?, onSetDecision: (String, Boolean) -> Unit) {
    val rows = mergeDecisionRows(
        decided = entry.waypointDecisions,
        decidedId = { it.waypointId },
        decidedRow = {
            DecisionRowState(id = it.waypointId, title = it.name, subtitle = "${"%.4f".format(it.lat)}, ${"%.4f".format(it.lng)}", state = it.state)
        },
        candidates = candidates?.waypoints.orEmpty(),
        candidateId = { it.id },
        candidateRow = {
            DecisionRowState(
                id = it.id,
                title = it.name,
                subtitle = "${"%.4f".format(it.lat)}, ${"%.4f".format(it.lng)}",
                state = DecisionState.UNDECIDED,
            )
        },
    )
    DecisionSection(title = "Waypoints", rows = rows, onSetDecision = onSetDecision)
}

@Composable
private fun OfflineRegionsSection(
    entry: CartographyEntry,
    candidates: List<OfflineRegionSummary>,
    distanceUnit: DistanceUnit,
    onSetDecision: (Long, Boolean) -> Unit,
) {
    val rows = mergeDecisionRows(
        decided = entry.offlineRegionDecisions,
        decidedId = { it.offlineRegionId },
        decidedRow = { DecisionRowState(id = it.offlineRegionId.toString(), title = it.name, subtitle = formatDistanceKm(it.radiusKm, distanceUnit), state = it.state) },
        candidates = candidates,
        candidateId = { it.id },
        candidateRow = {
            DecisionRowState(id = it.id.toString(), title = it.name, subtitle = formatDistanceKm(it.region.radiusKm, distanceUnit), state = DecisionState.UNDECIDED)
        },
    )
    DecisionSection(title = "Offline Regions", rows = rows) { id, kept -> onSetDecision(id.toLong(), kept) }
}

/**
 * The three-state merge itself — Stage 2b follow-up dispatch, point 2. Every already-[decided] item
 * renders with its own persisted state (kept or withheld), regardless of whether it's still a live
 * [candidates] entry; every live candidate with no matching decision renders as
 * [DecisionState.UNDECIDED]. Decided rows come first (a stable, decision-order list you can act on
 * without them jumping around as new candidates appear), undecided rows after.
 */
private inline fun <D, C, Id> mergeDecisionRows(
    decided: List<D>,
    decidedId: (D) -> Id,
    decidedRow: (D) -> DecisionRowState,
    candidates: List<C>,
    candidateId: (C) -> Id,
    candidateRow: (C) -> DecisionRowState,
): List<DecisionRowState> {
    val decidedIds = decided.map(decidedId).toSet()
    val undecided = candidates.filter { candidateId(it) !in decidedIds }.map(candidateRow)
    return decided.map(decidedRow) + undecided
}

private enum class DecisionState { KEPT, WITHHELD, UNDECIDED }

private data class DecisionRowState(val id: String, val title: String, val subtitle: String?, val state: DecisionState)

private val Boolean.asDecisionState: DecisionState get() = if (this) DecisionState.KEPT else DecisionState.WITHHELD

// Every *Decision domain type carries the same `kept: Boolean` — this one extension property, read
// via each concrete type below, is what mergeDecisionRows' decidedRow lambdas call `.state` through.
private val com.forager.app.domain.model.FindDecision.state: DecisionState get() = kept.asDecisionState
private val com.forager.app.domain.model.TrackDecision.state: DecisionState get() = kept.asDecisionState
private val com.forager.app.domain.model.WaypointDecision.state: DecisionState get() = kept.asDecisionState
private val com.forager.app.domain.model.OfflineRegionDecision.state: DecisionState get() = kept.asDecisionState

@Composable
private fun DecisionSection(title: String, rows: List<DecisionRowState>, onSetDecision: (String, Boolean) -> Unit) {
    if (rows.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        Text(title, style = MaterialTheme.typography.titleSmall)
        rows.forEach { row -> DecisionRow(row = row, onSetKept = { kept -> onSetDecision(row.id, kept) }) }
    }
}

/**
 * One candidate's row — the withhold/keep interaction itself. A **kept** row reads normally with a
 * single **Withhold** action; a **withheld** row reads visibly dimmed and struck through with a
 * single **Keep** action, so withholding shows as a deliberate, revisitable act rather than an
 * unchecked filter box. An **undecided** row (new since the entry was last saved) reads normally with
 * *both* actions available and a small "New" label — nothing here defaults it either way.
 */
@Composable
private fun DecisionRow(row: DecisionRowState, onSetKept: (Boolean) -> Unit) {
    Card(modifier = Modifier.fillMaxWidth().alpha(if (row.state == DecisionState.WITHHELD) 0.5f else 1f)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(Spacing.md),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        row.title,
                        style = MaterialTheme.typography.bodyLarge,
                        textDecoration = if (row.state == DecisionState.WITHHELD) TextDecoration.LineThrough else TextDecoration.None,
                    )
                    if (row.state == DecisionState.UNDECIDED) {
                        Text("New", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                    }
                }
                row.subtitle?.let { subtitle -> Text(subtitle, style = MaterialTheme.typography.bodySmall) }
            }
            when (row.state) {
                DecisionState.KEPT -> TextButton(onClick = { onSetKept(false) }) { Text("Withhold") }
                DecisionState.WITHHELD -> TextButton(onClick = { onSetKept(true) }) { Text("Keep") }
                DecisionState.UNDECIDED -> Row {
                    TextButton(onClick = { onSetKept(true) }) { Text("Keep") }
                    TextButton(onClick = { onSetKept(false) }) { Text("Withhold") }
                }
            }
        }
    }
}

/** Stage 2c: `internal`, not `private` — [CartographyEntryReportScreen] reuses this exact formatting for its own kept-track lines. */
internal fun trackSubtitle(distanceMeters: Double, durationMillis: Long, distanceUnit: DistanceUnit): String {
    val km = (distanceMeters / 1000.0).roundToInt()
    val distanceLabel = formatDistanceKm(km, distanceUnit)
    val totalMinutes = durationMillis / 60_000
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    val durationLabel = if (hours > 0) "${hours}h ${minutes}m" else "${minutes}m"
    return "$distanceLabel · $durationLabel"
}

private fun attachDateLabel(epochMillis: Long): String =
    Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()).toLocalDate().toString()

/** Stage 2c: `internal`, not `private` — [CartographyEntryReportScreen] reuses the same size for its own read-only photo grid. */
internal const val KEPT_PHOTO_SIZE_DP = 88
