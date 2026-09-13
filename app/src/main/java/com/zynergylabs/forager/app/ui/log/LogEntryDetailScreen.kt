package com.zynergylabs.forager.app.ui.log

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ripple
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.zynergylabs.forager.app.R
import com.zynergylabs.forager.app.domain.model.LogPhoto
import com.zynergylabs.forager.app.domain.model.MushroomLogEntry
import com.zynergylabs.forager.app.domain.model.PhotoSource
import com.zynergylabs.forager.app.photo.CameraCaptureFiles
import com.zynergylabs.forager.app.ui.availability.CollapsibleSection
import com.zynergylabs.forager.app.ui.theme.Spacing

/**
 * The entry's detail/edit form — one screen for both, since [entry] is already persisted by the
 * time this shows (see [MushroomLogViewModel.onStartNewEntry]): "creating" and "editing" are the
 * same action here. Each characteristic section is a [CollapsibleSection] (reused from
 * `AvailabilityScreen`) so the form doesn't dump every field on screen at once — the same "single
 * line until tapped" shape the drawer's own Search/Trip Planner sections use.
 *
 * ## Standalone drafts (Workstream L4b, owner decision 2026-08-22; corrected 2026-08-25, L4b-R)
 *
 * [entry] here is always the **draft row** — [MushroomLogViewModel.onStartEditingEntry] created it
 * (or it's a brand-new entry's own row) before this screen ever opens; a committed entry's own row
 * is never bound to this form directly. [onEntryChanged] fires — and writes to disk — on every
 * field change, always onto that draft row. [onSave]/[onCancel] are the two deliberate exits this
 * screen exposes directly: Save commits the draft's current content (onto the parent's id, when
 * there is one) and removes the draft row; Cancel deletes the draft row outright and its own photo
 * references — for a re-edit, the parent is untouched throughout, so there is nothing to "restore."
 * [onBack] — the navigation arrow, not a labeled action — is the *incidental* exit instead: the
 * draft is already durably persisted (every [onEntryChanged] call wrote it), so leaving without
 * answering neither commits nor discards, just closes the form, the same as a tab switch or the app
 * backgrounding (see [MushroomLogViewModel.onLeaveEditingIncidentally]). Only the explicit Cancel
 * button ever discards.
 *
 * Workstream L4 (`docs/plans/pr26-rework.md`): entry creation routes here directly now, so [entry]
 * routinely arrives with [MushroomLogEntry.foundAt] `null`. [onAddLocation] is this screen's own
 * way to set one. Invoking the picker itself — full-screen, its own state in
 * [JournalTab]/[LogPanel], not embedded here — is the caller's job; see either composable's own doc
 * comment for why a centre-pin picker needs real screen space, the same reasoning `OfflineMapsPanel`
 * already established for the Offline Maps submenu.
 *
 * **L4c correction (2026-08-25):** this button originally lived in [PhotosSection]'s Camera/Gallery/
 * From Album row instead of beside the "Found at .../No location set." text it answers, reasoned at
 * the time as reading as a peer of that row's other "bring something in from outside this form"
 * actions. Two device reports turned out to be one bug: with all four buttons in one unconstrained,
 * non-wrapping `Row`, the row's real (text-driven) width exceeds a phone's screen width, so the
 * last button ran off-screen — not merely far from the text it answers, but frequently invisible
 * entirely, which is what the owner's screenshot's "missing" affordance and stray edge-of-screen
 * sliver both were. Moved here, next to the text, rather than duplicated: two affordances for one
 * action diverge eventually, and grouping fixes both the distance and the overflow's own worst
 * casualty in one edit. `Modifier.weight(1f)` on the text keeps a long coordinate string from
 * pushing this button off-screen the same way; the remaining three-button row was converted to a
 * wrapping `FlowRow` for the same reason.
 */
@Composable
internal fun LogEntryDetailScreen(
    entry: MushroomLogEntry,
    cameraCaptureFiles: CameraCaptureFiles,
    onEntryChanged: (MushroomLogEntry) -> Unit,
    onAddPhoto: (PhotoSource) -> Unit,
    onRemovePhoto: (LogPhoto) -> Unit,
    onPullPhoto: () -> Unit,
    onAddLocation: () -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit,
    onDeleteEntry: () -> Unit,
    onBack: () -> Unit,
    /**
     * Reports [PhotoAcquisitionLaunchers.isAcquisitionInFlight] up whenever it changes — device-check
     * patch, Items 2/3 — so a caller several layers up (`AvailabilityScreen`'s own ON_STOP hook) can
     * suppress "the user backgrounded the app" while this screen's own [PhotosSection] is mid camera
     * or gallery round-trip, not something the user chose to leave for. Defaulted, same reasoning as
     * every other optional callback here: existing callers/tests that don't care about this signal
     * don't need to pass one just to compile.
     */
    onPhotoAcquisitionInFlightChanged: (Boolean) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.lg, vertical = Spacing.sm),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                // weight(fill = false), not a plain Row: without it, this row (in particular the
                // date text, which has no width bound of its own) is measured at its own intrinsic
                // width regardless of what the Cancel/Save/Delete row on the right needs, so on a
                // long date string or a large font scale the two rows' combined width exceeds the
                // screen and something has to give — what actually gave, on hardware, was the Save
                // button being squeezed narrower than "Save" needs, wrapping it to "Sav"/"e". Same
                // fix this file's own class doc comment already documents for the location row's
                // near-identical overflow bug: bound the flexible side with weight so the fixed
                // side (the buttons) always gets its full intrinsic width, and the date truncates
                // with an ellipsis instead.
                modifier = Modifier.weight(1f, fill = false),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back to your log")
                }
                Text(
                    "Find on ${entry.foundOn}",
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                TextButton(onClick = onCancel) { Text("Cancel") }
                Button(onClick = onSave) { Text("Save", maxLines = 1, softWrap = false) }
                IconButton(onClick = onDeleteEntry) {
                    Icon(Icons.Filled.Delete, contentDescription = "Delete this entry")
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
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    entry.foundAt?.let { location -> "Found at ${"%.4f".format(location.lat)}, ${"%.4f".format(location.lng)}" }
                        ?: stringResource(R.string.log_entry_no_location),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.weight(1f),
                )
                Button(onClick = onAddLocation) { Text(if (entry.foundAt != null) "Change Location" else "Add Location") }
            }

            OutlinedTextField(
                value = entry.ownIdentification.orEmpty(),
                onValueChange = { text -> onEntryChanged(entry.copy(ownIdentification = text.ifBlank { null })) },
                label = { Text("Your own identification (optional)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            PhotosSection(
                photos = entry.photos,
                cameraCaptureFiles = cameraCaptureFiles,
                onPhotoSourceSelected = onAddPhoto,
                onRemovePhoto = onRemovePhoto,
                onPullPhoto = onPullPhoto,
                onAcquisitionInFlightChanged = onPhotoAcquisitionInFlightChanged,
            )

            HorizontalDivider()

            CollapsibleSection(title = "Cap") {
                CapEditor(entry.cap, onChanged = { onEntryChanged(entry.copy(cap = it)) })
            }
            CollapsibleSection(title = "Hymenophore") {
                HymenophoreEditor(entry.hymenophore, onChanged = { onEntryChanged(entry.copy(hymenophore = it)) })
            }
            CollapsibleSection(title = "Stipe") {
                StipeEditor(entry.stipe, onChanged = { onEntryChanged(entry.copy(stipe = it)) })
            }
            CollapsibleSection(title = "Veil remnants") {
                VeilEditor(entry.veil, onChanged = { onEntryChanged(entry.copy(veil = it)) })
            }
            CollapsibleSection(title = "Context / flesh") {
                ContextFleshEditor(entry.contextFlesh, onChanged = { onEntryChanged(entry.copy(contextFlesh = it)) })
            }
            CollapsibleSection(title = "Spore print") {
                SporePrintEditor(entry.sporePrint, onChanged = { onEntryChanged(entry.copy(sporePrint = it)) })
            }
            CollapsibleSection(title = "Host & substrate") {
                HostSubstrateEditor(entry.hostSubstrate, onChanged = { onEntryChanged(entry.copy(hostSubstrate = it)) })
            }

            NotesField(entry.notes, onValueChanged = { onEntryChanged(entry.copy(notes = it)) })

            Spacer(modifier = Modifier.heightIn(min = Spacing.lg))
        }
    }
}

@Composable
private fun PhotosSection(
    photos: List<LogPhoto>,
    cameraCaptureFiles: CameraCaptureFiles,
    onPhotoSourceSelected: (PhotoSource) -> Unit,
    onRemovePhoto: (LogPhoto) -> Unit,
    onPullPhoto: () -> Unit,
    onAcquisitionInFlightChanged: (Boolean) -> Unit,
) {
    // The Camera-permission-then-capture and system-Gallery-picker launchers — shared with
    // PhotoGalleryScreen's own Camera/Gallery buttons (standalone-photos dispatch) via this one
    // function, rather than a second hand-copy of the ActivityResultContracts/permission wiring.
    val photoAcquisition = rememberPhotoAcquisitionLaunchers(cameraCaptureFiles, onPhotoSourceSelected)
    LaunchedEffect(photoAcquisition.isAcquisitionInFlight) {
        onAcquisitionInFlightChanged(photoAcquisition.isAcquisitionInFlight)
    }

    var viewingPhotoId by rememberSaveable { mutableStateOf<String?>(null) }

    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        Text("Photos", style = MaterialTheme.typography.titleSmall)
        // FlowRow, not Row: three real-width Material3 buttons plus their labels can exceed a
        // phone's screen width (confirmed analytically for the fourth button this row used to also
        // carry — see this file's own top doc comment on the L4c correction). A plain, non-scrolling
        // Row doesn't shrink or wrap overflowing children; they simply run past the screen edge,
        // invisible rather than clipped. Wrapping to a second line keeps every button reachable.
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            Button(onClick = photoAcquisition.launchCamera) { Text("Camera") }
            // Entry-photo-acquisition dispatch, Item 1: "Import," not "Gallery" — the app calls its
            // own photo collection "Album," so a button labelled "Gallery" that actually opens the
            // *device's* picker was already two near-synonyms meaning opposite things. "Import" says
            // what happens: a photo comes in from outside.
            Button(onClick = photoAcquisition.launchGallery) { Text("Import") }
            // Workstream G3: this button references an existing photo the app already has, so it
            // needs its own word from the one above. "Album" is taken too — see CompactTab's own doc
            // comment — by the bottom nav tab visible at the same time as this screen on compact, so
            // this reads "From Album" (a distinct exact string) rather than the bare word, checked
            // against every other button label and heading in this same screen and against the
            // bottom nav before landing here.
            Button(onClick = onPullPhoto) { Text("From Album") }
        }
        if (photos.isNotEmpty()) {
            // fillMaxWidth is load-bearing here, not decorative: without it this FlowRow sizes to
            // wrap its own content (the thumbnails plus their spacing) with no leftover width for
            // horizontalArrangement's Alignment.CenterHorizontally to center within, so the group
            // would sit flush left regardless of the arrangement passed here.
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm, Alignment.CenterHorizontally),
            ) {
                photos.forEach { photo ->
                    LogPhotoThumbnail(
                        photo = photo,
                        onOpen = { viewingPhotoId = photo.id },
                        onRemove = { onRemovePhoto(photo) },
                    )
                }
            }
        }
    }

    // The id, not the index: a removal reorders nothing but does shift indices, and an id that is
    // no longer in the list closes the viewer instead of opening a neighbour. rememberSaveable so a
    // rotation mid-inspection comes back on the same photo (see PhotoViewerDialog's own comment).
    val viewingIndex = viewingPhotoId?.let { id -> photos.indexOfFirst { it.id == id } }?.takeIf { it >= 0 }
    if (viewingIndex != null) {
        PhotoViewerDialog(photos = photos, initialIndex = viewingIndex, onDismiss = { viewingPhotoId = null })
    }
}

private const val PHOTO_THUMBNAIL_SIZE_DP = 88
private const val REMOVE_GLYPH_SCRIM_SIZE_DP = 28

/**
 * Side of the remove control's touch target, in dp — the 28dp glyph circle plus a 4dp halo, flush in
 * the thumbnail's top-end corner. Full-screen-photo-viewer dispatch: the rest of the thumbnail now
 * opens [PhotoViewerDialog], so the two targets have to partition the 88dp tile, and the partition
 * is chosen here rather than left to what falls out of the framework. Before this, the control was
 * an [IconButton] at its 48dp Material minimum: laid out top-end, that box spans x 40–88 and
 * y 0–48 of the tile, which contains the tile's own centre (44, 44) — a tap in the middle of the
 * thumbnail, the natural "open this" gesture, was a remove. A 36dp box in the corner (x 52–88,
 * y 0–36) keeps 8dp clear of the centre on both axes, clears WCAG 2.5.8's 24dp target minimum with
 * room, and leaves the glyph itself the size it was. Not the Material 48dp: that minimum is for a
 * control standing alone, and this one shares an 88dp tile with a second target.
 *
 * Why the direct bounds are the whole story: Compose expands a touch target to the 48dp minimum
 * only where nothing else is hit directly, and the photo beneath is now a direct hit everywhere in
 * the tile, so the control's effective region is exactly this box. [LogEntryDetailScreenTest]
 * touches both sides of that edge at screen coordinates rather than trusting the arithmetic.
 */
internal const val REMOVE_TOUCH_TARGET_DP = 36

/** Exposed at file scope (not inlined into the composable) so [LogPhotoThumbnailRemoveAffordanceContrastTest] checks this exact value, not a copy that can drift from it. */
internal const val REMOVE_GLYPH_SCRIM_ALPHA = 0.6f

@Composable
private fun LogPhotoThumbnail(photo: LogPhoto, onOpen: () -> Unit, onRemove: () -> Unit) {
    Box(modifier = Modifier.size(PHOTO_THUMBNAIL_SIZE_DP.dp)) {
        DecodedPhoto(
            relativePath = photo.relativePath,
            modifier = Modifier.fillMaxSize().clickable(onClickLabel = "Open full screen", onClick = onOpen),
        )
        // B3 (2026-08-27): the bare glyph read near-invisible against pale photo content —
        // LocalContentColor here tracks the theme, not the photo underneath, so it had no
        // guaranteed contrast against arbitrary imagery. MaterialTheme.colorScheme.scrim is this
        // theme's own token for exactly that job (a backing layer that keeps content legible over
        // whatever's beneath it, the same role it plays behind a modal), so the fix reuses it
        // rather than introducing a new colour. A fixed white glyph on that scrim, not a
        // theme-following tint: the scrim's whole purpose is a stable, opaque-enough backdrop, so
        // the glyph on top only needs to contrast against the scrim's own dark tone, not against
        // the photo — the same reasoning that keeps a system status bar icon fixed-colour over a
        // scrim rather than swapping per background.
        //
        // A plain clickable Box of REMOVE_TOUCH_TARGET_DP, no longer an IconButton: IconButton
        // fixes its own 48dp target, and 48dp in this corner covers the tile's centre — see
        // REMOVE_TOUCH_TARGET_DP for the partition this replaces it with. Role.Button and the
        // "Remove photo" description keep it announced and findable exactly as before.
        //
        // The target is the whole square, and the ripple is drawn inside the glyph circle through
        // its own indication modifier rather than by clipping the clickable to a circle: a clip
        // clips hit-testing too, and the first run of LogEntryDetailScreenTest's partition check
        // found the square's corners falling through to the photo (a touch at (54, 2) dp opened
        // the viewer) with the clip in place.
        val removeInteraction = remember { MutableInteractionSource() }
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .size(REMOVE_TOUCH_TARGET_DP.dp)
                .clickable(interactionSource = removeInteraction, indication = null, role = Role.Button, onClick = onRemove),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .size(REMOVE_GLYPH_SCRIM_SIZE_DP.dp)
                    .clip(CircleShape)
                    .indication(removeInteraction, ripple())
                    .background(MaterialTheme.colorScheme.scrim.copy(alpha = REMOVE_GLYPH_SCRIM_ALPHA)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.Close, contentDescription = "Remove photo", tint = Color.White)
            }
        }
    }
}
