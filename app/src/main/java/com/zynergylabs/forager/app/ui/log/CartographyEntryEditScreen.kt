package com.zynergylabs.forager.app.ui.log

import com.zynergylabs.forager.app.ui.motion.BouncingIconButton
import androidx.activity.compose.BackHandler
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.zynergylabs.forager.app.domain.ComputeTrackStatisticsUseCase
import com.zynergylabs.forager.app.domain.EntryGroup
import com.zynergylabs.forager.app.domain.entryContentsOf
import com.zynergylabs.forager.app.domain.networkFixExclusionNote
import com.zynergylabs.forager.app.domain.OfflineRegionSummary
import com.zynergylabs.forager.app.domain.model.CartographyEntry
import com.zynergylabs.forager.app.domain.model.DerivedTrip
import com.zynergylabs.forager.app.domain.model.DistanceUnit
import com.zynergylabs.forager.app.domain.model.Track
import com.zynergylabs.forager.app.domain.model.GalleryPhoto
import com.zynergylabs.forager.app.domain.model.PhotoAttachment
import com.zynergylabs.forager.app.domain.model.PhotoSource
import com.zynergylabs.forager.app.domain.model.formatDistanceMeters
import com.zynergylabs.forager.app.ui.theme.Spacing
import java.time.Instant
import java.time.ZoneId
import com.zynergylabs.forager.app.ui.format.displayDate

/**
 * A Cartography entry's curation-and-writing surface — Journal Stage 2b, extended by its own
 * follow-up dispatch (point 2). [candidates]/[candidateOfflineRegions] are that day's *live* trip
 * report, reloaded on every open (creation or reopen); [entryContentsOf] merges them against the
 * entry's own persisted decisions into three states per candidate — **included** (kept), **left out**
 * (withheld), or **new** (not yet decided) — and [EntryContentsPanel] shows them (data part A, dispatch
 * 2026-09-28-667, which replaced the one-card-per-item Withhold list). A `null` [candidates] means the
 * trip report hasn't finished loading yet (or failed); the panel then shows the entry's own
 * already-decided items only, so the screen still renders sensibly rather than going blank.
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
 * itself stays the raw, unconditional close: both the leave prompt's own Save option and the
 * persistent Save button's own confirmation (below) call `onSave()` then `onBack()` directly, since
 * a just-saved entry has nothing left to ask about.
 *
 * ## Three prompts, three separate triggers (pending-edit-and-fixes dispatch, Items 1/3)
 *
 * `confirmingSave` (Item 3): the persistent Save button no longer saves directly — it opens this
 * local confirmation first, since "save confirms, then exits" (the prior dispatch's own change)
 * otherwise had no confirmation at all. [showLeavePrompt] and [showReturnPrompt] are each already
 * their own confirmation for their own trigger, so neither routes through `confirmingSave` — all
 * three are mutually exclusive, reached only by their own specific path (the arrow/system back, a
 * tap on Save, or resuming from the background), never stacked.
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
    /** A group switch in the "In this entry" panel: see [EntryContentsPanel] and [CartographyViewModel.onSetEntryGroupIncluded]. */
    onSetGroupIncluded: (EntryGroup, Boolean) -> Unit,
    /** Which of the panel's groups are open, hoisted so it survives a tab change; see [EntryContentsPanel]. */
    openGroups: Set<EntryGroup>,
    onToggleGroupOpen: (EntryGroup) -> Unit,
    onToggleKeptPhoto: (String) -> Unit,
    /**
     * Entry-photo-acquisition dispatch, Item 2: Camera and Import, reached from inside the open
     * entry rather than only "pull an existing Album photo" ([onToggleKeptPhoto] above). A photo
     * acquired here attaches to this entry automatically (owner decision — the user is standing in
     * the entry asking for a photo; unwanted, they remove it in the same one tap
     * [KeptPhotosSection]'s own "Remove this photo" already offers). See [PullPhotoPickerScreen]'s
     * own doc comment for why composing "persist" and "attach" happens above this screen, not here.
     */
    onOpenCamera: () -> Unit,
    onAcquirePhoto: (PhotoSource) -> Unit,
    /** See [PullPhotoPickerScreen]'s own doc comment on this same parameter. */
    onAcquisitionInFlightChanged: (Boolean) -> Unit,
    onFinish: () -> Unit,
    /** Explicit Save for a committed entry — saves in place. Both call sites below then call [onBack] themselves: the persistent button (back-nav-and-save-flow dispatch, Item 3 — save confirms, then exits) and the leave prompt's own Save option. A no-op call for a draft (never shown either). */
    onSave: () -> Unit,
    /** The leave-prompt's Discard option — reloads the entry from the database and leaves in one step. See this composable's own doc comment. */
    onDiscardChanges: () -> Unit,
    /**
     * The backgrounding-return prompt — pending-edit-and-fixes dispatch, Item 1. Hoisted from
     * [CartographyScreen]'s own lifecycle observer, the same shape [showLeavePrompt] already
     * established: shown once, after the app backgrounded with this entry dirty and has now
     * resumed, never on an ordinary in-app visit. [onContinueEditing] just dismisses (the pending
     * edit is already sitting right here, untouched); [onCommit] persists it in place, same as
     * [onSave]; [onSaveAsDraft] demotes the entry back to a draft, with the edit in place, and
     * closes this screen — see [CartographyViewModel.onSaveEntryAsDraft]'s own doc comment for why
     * demoting closes rather than staying open in draft mode.
     */
    showReturnPrompt: Boolean,
    onContinueEditing: () -> Unit,
    onCommit: () -> Unit,
    onSaveAsDraft: () -> Unit,
    onDeleteEntry: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    /** Off while the Tools drawer is open over the Journal, so Back closes the drawer (intent 2026-09-28-28); see [JournalTab]'s parameter of the same name. `true` (the default) is every other caller, unchanged. */
    backEnabled: Boolean = true,
    /**
     * Motion Part 3, item 1 (RECORD -651, Journal pages: "Slide in, slide back"; scout J7): whether the album picker is open over
     * this editor, and whether this screen draws that picker itself. [CartographyScreen] holds the first and draws the picker as
     * its own page, which slides in over the editor and out again on Back, so it passes `false` for the second and this screen
     * draws the form only. The defaults are this screen on its own, as before: it holds the flag and draws the picker in place.
     */
    pullingPhotoState: MutableState<Boolean> = remember(entry.id) { mutableStateOf(false) },
    drawsPhotoPicker: Boolean = true,
) {
    var pullingPhoto by pullingPhotoState
    // Entry-photo-acquisition dispatch, Item 3: found on device — system back from this picker
    // landed on the Cartography entry *list*, skipping this editor entirely. CartographyScreen's
    // own BackHandler only knows "an entry is open"; pullingPhoto is a level inside that it had no
    // handler for, so back consumed the entry level and this one went untouched. Nested here,
    // enabled only while the picker is actually showing, so Compose's dispatcher tries this before
    // CartographyScreen's own — the same "innermost enabled handler wins" shape every other
    // multi-level back case in this codebase already uses (see CartographyScreen's own BackHandler
    // doc comment). This is the fifth instance of the same four-layer navigation problem in this
    // codebase (the map "+" flow, the camera return, the Cartography entry, the Records sub-tab,
    // now this) — a shared navigation abstraction remains queued behind the AvailabilityScreen.kt
    // split, not attempted here.
    BackHandler(enabled = backEnabled && pullingPhoto && drawsPhotoPicker) {
        pullingPhoto = false
    }
    if (pullingPhoto && drawsPhotoPicker) {
        PullPhotoPickerScreen(
            photos = galleryPhotos,
            onPhotoSelected = { photo -> onToggleKeptPhoto(photo.id); pullingPhoto = false },
            onOpenCamera = onOpenCamera,
            onPhotoAcquired = onAcquirePhoto,
            onAcquisitionInFlightChanged = onAcquisitionInFlightChanged,
            modifier = modifier.fillMaxSize(),
        )
        return
    }

    var menuExpanded by remember(entry.id) { mutableStateOf(false) }
    var confirmingDelete by remember(entry.id) { mutableStateOf(false) }
    var confirmingSave by remember(entry.id) { mutableStateOf(false) }
    var tagsText by remember(entry.id) { mutableStateOf(entry.tags.joinToString(", ")) }

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.lg, vertical = Spacing.sm),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                BouncingIconButton(onClick = onRequestBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back to Cartography")
                }
                Text(displayDate(entry.date), style = MaterialTheme.typography.titleMedium)
                if (isLoadingCandidates) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp))
                }
            }
            Box {
                BouncingIconButton(onClick = { menuExpanded = true }) {
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

            // Data part A (dispatch 2026-09-28-667): one panel in place of the four card lists.
            val contents = remember(entry, candidates, candidateOfflineRegions) {
                entryContentsOf(entry, candidates, candidateOfflineRegions, ComputeTrackStatisticsUseCase())
            }
            EntryContentsPanel(
                contents = contents,
                distanceUnit = distanceUnit,
                openGroups = openGroups,
                onToggleGroupOpen = onToggleGroupOpen,
                onSetGroupIncluded = onSetGroupIncluded,
                onSetFindIncluded = onSetFindDecision,
                onSetTrackIncluded = onSetTrackDecision,
                onSetWaypointIncluded = onSetWaypointDecision,
                onSetOfflineMapIncluded = onSetOfflineRegionDecision,
            )

            if (entry.isDraft) {
                Button(onClick = onFinish, modifier = Modifier.fillMaxWidth()) { Text("Finish entry") }
            } else {
                // Back-nav-and-save-flow dispatch, Item 3: save confirms, then exits to the
                // previous screen — the standard shape, not stay-on-screen. Composed here rather
                // than folded into onSave itself so onSave alone (also used by the leave prompt's
                // own Save option below, and the return prompt's Commit option) stays "just
                // persist," with each call site deciding separately whether leaving afterward
                // makes sense. Pending-edit-and-fixes dispatch, Item 3: this button itself no
                // longer saves directly — it opens confirmingSave below, since neither the leave
                // prompt nor the return prompt route through it (each is already its own
                // confirmation, and must keep showing exactly one dialog).
                Button(onClick = { confirmingSave = true }, modifier = Modifier.fillMaxWidth()) { Text("Save") }
            }

            Spacer(modifier = Modifier.heightIn(min = Spacing.lg))
        }
    }

    if (confirmingDelete) {
        AlertDialog(
            onDismissRequest = { confirmingDelete = false },
            title = { Text("Delete this entry?") },
            text = { Text(DELETE_ENTRY_DIALOG_TEXT) },
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

    // Pending-edit-and-fixes dispatch, Item 3: the persistent Save button's own confirmation —
    // separate from showLeavePrompt above (reached only via the back arrow/system back) and from
    // showReturnPrompt below (reached only after backgrounding), so a tap on this button can never
    // show a second dialog on top of either of those; each of the three is its own, mutually
    // exclusive trigger.
    if (confirmingSave) {
        AlertDialog(
            onDismissRequest = { confirmingSave = false },
            title = { Text("Save this entry?") },
            text = { Text("This saves your changes to the entry.") },
            confirmButton = {
                TextButton(
                    onClick = { confirmingSave = false; onSave(); onBack() },
                    modifier = Modifier.testTag(SAVE_CONFIRM_TEST_TAG),
                ) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { confirmingSave = false }) { Text("Cancel") } },
        )
    }

    // Pending-edit-and-fixes dispatch, Item 1: shown once, right after the app resumes from having
    // backgrounded this entry while dirty — see CartographyScreen's own lifecycle-observer doc
    // comment for when this actually fires. No Discard option here, deliberately: the two
    // resolutions on offer are Commit (keep it, as a committed entry) or Save as draft (keep it,
    // demoted) — discarding a pending edit the user hasn't even seen a chance to review yet isn't
    // one of the choices this prompt exists to offer.
    if (showReturnPrompt) {
        AlertDialog(
            onDismissRequest = onContinueEditing,
            title = { Text("Welcome back") },
            text = { Text("This entry had an edit still pending when the app went to the background. Continue editing, submit it, or save it as a draft.") },
            confirmButton = {
                // Search-focus-and-hide dispatch, Item 3: "Submit," not "Commit" — this same file's
                // own persistent "Finish entry" button (onFinish, above) already uses that verb for
                // the identical action, so this dialog was the one place still saying "Commit." The
                // tag/callback names (RETURN_PROMPT_COMMIT_TEST_TAG, onCommit) are unchanged — this
                // is a label rename, not a rename of what the action does.
                TextButton(onClick = onCommit, modifier = Modifier.testTag(RETURN_PROMPT_COMMIT_TEST_TAG)) { Text("Submit") }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = onSaveAsDraft, modifier = Modifier.testTag(RETURN_PROMPT_SAVE_AS_DRAFT_TEST_TAG)) { Text("Save as draft") }
                    TextButton(onClick = onContinueEditing, modifier = Modifier.testTag(RETURN_PROMPT_CONTINUE_EDITING_TEST_TAG)) { Text("Continue editing") }
                }
            },
        )
    }
}

/** [showLeavePrompt]'s own three buttons — tagged since the screen's own persistent "Save" button (shown for any committed entry) otherwise collides with this dialog's identically-labelled one for `onNodeWithText` in tests. */
internal const val LEAVE_PROMPT_SAVE_TEST_TAG = "cartography_leave_prompt_save"
internal const val LEAVE_PROMPT_DISCARD_TEST_TAG = "cartography_leave_prompt_discard"
internal const val LEAVE_PROMPT_CANCEL_TEST_TAG = "cartography_leave_prompt_cancel"

/** [confirmingSave]'s own confirm button — tagged for the same reason as the leave prompt's: this dialog's "Save" text collides with the screen's own persistent button and the leave prompt's own Save option. */
internal const val SAVE_CONFIRM_TEST_TAG = "cartography_save_confirm"

/** [showReturnPrompt]'s own three buttons. */
internal const val RETURN_PROMPT_COMMIT_TEST_TAG = "cartography_return_prompt_commit"
internal const val RETURN_PROMPT_SAVE_AS_DRAFT_TEST_TAG = "cartography_return_prompt_save_as_draft"
internal const val RETURN_PROMPT_CONTINUE_EDITING_TEST_TAG = "cartography_return_prompt_continue_editing"

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
                    BouncingIconButton(
                        onClick = { onToggleKeptPhoto(attachment.photoId) },
                        modifier = Modifier.align(Alignment.TopEnd).size(24.dp),
                    ) {
                        Icon(Icons.Filled.Close, contentDescription = "Remove this photo", tint = MaterialTheme.colorScheme.error)
                    }
                }
            }
            BouncingIconButton(onClick = onAddFromAlbum) {
                Icon(Icons.Filled.Add, contentDescription = "Add a photo from the Album")
            }
        }
    }
}

/**
 * One kept photo attachment, resolved against the live gallery — [photo] is the [GalleryPhoto] row
 * backing [attachment] if it still exists, `null` if the gallery row is gone (see
 * [com.zynergylabs.forager.app.data.local.CartographyEntryPhotoRefEntity]'s own doc comment for why this stays
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

/**
 * What a track row appends when the read seam excluded most or all of it as network-provider fixes
 * (timestamp-filter dispatch, Item 3): the live track's own note when the track is at hand, else —
 * on a snapshot row with no live track loaded — the one thing the snapshot can say, that it has no
 * usable points (the recompute in `CartographyViewModel.onOpenEntry` keeps `pointCount` current, and
 * this is that column's first reader). Empty in the ordinary case.
 */
internal fun trackExclusionSuffix(liveTrack: Track?, snapshotPointCount: Int?): String {
    liveTrack?.let(::networkFixExclusionNote)?.let { return " · $it" }
    return if (snapshotPointCount == 0) " · no usable points" else ""
}

/**
 * A track's length and duration — once the one formatter behind all three track-length sites (the
 * edit screen's decided and candidate rows, the report screen's kept rows). Since data part A
 * (dispatch 2026-09-28-667) those three use [labelledTrackLine], which writes the time as "1 h 12 min";
 * this one is left for the Journal list card's stats row, which is not one of that part's screens.
 *
 * **Why `formatDistanceMeters` and not `formatDistanceKm`** (track-distance-label dispatch): the
 * kilometre formatter exists for offline-map download ceilings, which are whole miles and whole
 * kilometres by construction, and its rounding is correct there. Track lengths live mostly in the
 * range that rounding destroys. Stage 2b reused it here, and it rounded metres to whole kilometres
 * *before* converting to whole miles — so a stored 733 m printed "1 mi", every length from 500 m to
 * just under 2.5 km printed "1 mi", and a real 92 m, 26-point track printed **"0 mi"**: a false
 * statement about something the user did, found on the owner's own device
 * (`docs/audits/2026-09-07-track-distance-display-pulse.md`). The two formatters look like the
 * same job and are not; do not reunify them.
 */
internal fun trackSubtitle(distanceMeters: Double, durationMillis: Long, distanceUnit: DistanceUnit): String {
    val distanceLabel = formatDistanceMeters(distanceMeters, distanceUnit)
    val totalMinutes = durationMillis / 60_000
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    val durationLabel = if (hours > 0) "${hours}h ${minutes}m" else "${minutes}m"
    return "$distanceLabel · $durationLabel"
}

private fun attachDateLabel(epochMillis: Long): String =
    displayDate(Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()).toLocalDate())

/**
 * The delete confirmation on the editor and the report, in the owner's word for a kept item, "included"
 * (RECORD -656). Before data part A it said "its kept selections" and "regions it kept".
 */
internal const val DELETE_ENTRY_DIALOG_TEXT =
    "This removes the entry and the choices made in it. The finds, tracks, waypoints, and offline maps it included stay in Records."

/** Stage 2c: `internal`, not `private` — [CartographyEntryReportScreen] reuses the same size for its own read-only photo grid. */
internal const val KEPT_PHOTO_SIZE_DP = 88
