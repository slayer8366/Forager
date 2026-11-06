package com.zynergylabs.forager.app.ui.log

import com.zynergylabs.forager.app.ui.motion.BouncingIconButton
import com.zynergylabs.forager.app.ui.motion.StateCrossfade
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.zynergylabs.forager.app.R
import com.zynergylabs.forager.app.domain.model.Association
import com.zynergylabs.forager.app.domain.model.CapSection
import com.zynergylabs.forager.app.domain.model.ContextFleshSection
import com.zynergylabs.forager.app.domain.model.Feature
import com.zynergylabs.forager.app.domain.model.HostSubstrateSection
import com.zynergylabs.forager.app.domain.model.HymenophoreDetails
import com.zynergylabs.forager.app.domain.model.HymenophoreSection
import com.zynergylabs.forager.app.domain.model.LogPhoto
import com.zynergylabs.forager.app.domain.model.MushroomLogEntry
import com.zynergylabs.forager.app.domain.model.Observed
import com.zynergylabs.forager.app.domain.model.SporePrintSection
import com.zynergylabs.forager.app.domain.model.StipeDetails
import com.zynergylabs.forager.app.domain.model.StipeSection
import com.zynergylabs.forager.app.domain.model.VeilSection
import com.zynergylabs.forager.app.domain.model.valueOrNull
import com.zynergylabs.forager.app.ui.theme.Spacing
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalWindowInfo
import com.zynergylabs.forager.app.ui.format.is24HourClock

/**
 * The Journal gallery's default view for an *existing* entry — a compiled, readable report of
 * whatever has been recorded so far, rather than dropping straight into [LogEntryDetailScreen]'s
 * edit form. [JournalTab] only shows this for an entry opened via [JournalTab.onOpenEntry]; a
 * brand-new entry started from the gallery's "+" tile goes straight to editing, since there is
 * nothing yet to report — see [JournalTab]'s own doc comment.
 *
 * Only [Observed.Recorded]/[Feature.Present] values are rendered as lines — an unrecorded field is
 * left out of the report entirely (Stage 2d — previously printed "Not recorded yet." per section;
 * changed on the owner's own reasoning: *"Not recorded yet." asserts an absence that may be false* —
 * a forager who examined the cap and found nothing notable, or skipped a spore print because the
 * identification was obvious, is told their record is incomplete when it is not. Blank makes no
 * claim; a partially filled find is not a problem needing explanation, so an empty section is simply
 * omitted, silently, with no message). [LogGalleryScreen]'s former "Incomplete" tile label (removed 2026-09-13) was a separate,
 * pre-existing signal this change doesn't touch.
 *
 * **[isEntirelyEmpty]:** when literally nothing has been recorded anywhere on this screen — no
 * location, no own identification, no photos, no notes, and all seven taxonomic sections empty —
 * omitting every section leaves only a title and nothing else: honest, but a screen with no
 * affordance on it. That specific, fully-empty case shows [EMPTY_FIND_MESSAGE] instead of the
 * ordinary body, pointing at this screen's own overflow-menu Edit route. Anything short of fully
 * empty renders exactly as it always has — individual empty sections omitted, no message.
 */
@Composable
internal fun LogEntryReportScreen(
    entry: MushroomLogEntry,
    onEdit: () -> Unit,
    onDeleteEntry: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var menuExpanded by remember(entry.id) { mutableStateOf(false) }
    // Same shape as LogEntryDetailScreen's PhotosSection: the id of the photo open in the viewer,
    // saveable so a rotation mid-inspection comes back on it; null when the viewer is closed.
    var viewingPhotoId by rememberSaveable(entry.id) { mutableStateOf<String?>(null) }

    val capLines = capReportLines(entry.cap)
    val hymenophoreLines = hymenophoreReportLines(entry.hymenophore)
    val stipeLines = stipeReportLines(entry.stipe)
    val veilLines = veilReportLines(entry.veil)
    val contextFleshLines = contextFleshReportLines(entry.contextFlesh)
    val sporePrintLines = sporePrintReportLines(entry.sporePrint)
    val hostSubstrateLines = hostSubstrateReportLines(entry.hostSubstrate)

    val isEntirelyEmpty = entry.foundAt == null &&
        entry.ownIdentification.isNullOrBlank() &&
        entry.photos.isEmpty() &&
        entry.notes.isBlank() &&
        capLines.isEmpty() &&
        hymenophoreLines.isEmpty() &&
        stipeLines.isEmpty() &&
        veilLines.isEmpty() &&
        contextFleshLines.isEmpty() &&
        sporePrintLines.isEmpty() &&
        hostSubstrateLines.isEmpty()

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.lg, vertical = Spacing.sm),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                BouncingIconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back to your log")
                }
                // Data part D (RECORD -703, "Match the tile"): the find's title by the tile's rule ([findTitle]), blank when it has
                // none, with the tile's screen-reader label.
                val title = findTitle(entry, is24HourClock())
                val blankLabel = findBlankTitleLabel(entry)
                Text(
                    title ?: "",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.testTag(FIND_PAGE_TITLE_TAG).then(if (title == null) Modifier.semantics { contentDescription = blankLabel } else Modifier),
                )
            }
            Box {
                BouncingIconButton(onClick = { menuExpanded = true }) {
                    Icon(Icons.Filled.MoreVert, contentDescription = "Entry options")
                }
                DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                    DropdownMenuItem(
                        text = { Text("Edit entry") },
                        leadingIcon = { Icon(Icons.Filled.Edit, contentDescription = null) },
                        onClick = {
                            menuExpanded = false
                            onEdit()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("Delete entry") },
                        leadingIcon = { Icon(Icons.Filled.Delete, contentDescription = null) },
                        onClick = {
                            menuExpanded = false
                            onDeleteEntry()
                        },
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
            // Motion Part 3, Amendment 2 (RECORD -682; scout F8): the empty message and the report crossfade instead of swapping in
            // one frame (motion/StateCrossfade.kt). The report keeps this column's spacing inside its own column.
            StateCrossfade(targetState = isEntirelyEmpty, modifier = Modifier.fillMaxWidth()) { empty ->
            if (empty) {
                Text(EMPTY_FIND_MESSAGE, style = MaterialTheme.typography.bodyMedium)
            } else {
              Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Spacing.lg)) {
                // RECORD -868 and -871 (the owner: "Under the title, whole photo, next one peeking (Recommended)"): the
                // photos come first, under the title, large and uncropped; "Found at" and the identification follow.
                // Until then they were 88 dp crops after those two lines.
                if (entry.photos.isNotEmpty()) {
                    ReportPhotos(photos = entry.photos, onOpen = { id -> viewingPhotoId = id })
                }

                Text(
                    entry.foundAt?.let { location -> "Found at ${"%.4f".format(location.lat)}, ${"%.4f".format(location.lng)}" }
                        ?: stringResource(R.string.log_entry_no_location),
                    style = MaterialTheme.typography.bodySmall,
                )

                entry.ownIdentification?.takeIf { it.isNotBlank() }?.let { identification ->
                    Text("Your own identification: $identification", style = MaterialTheme.typography.bodyMedium)
                }

                HorizontalDivider()

                ReportSection("Cap", capLines)
                ReportSection("Hymenophore", hymenophoreLines)
                ReportSection("Stipe", stipeLines)
                ReportSection("Veil remnants", veilLines)
                ReportSection("Context / flesh", contextFleshLines)
                ReportSection("Spore print", sporePrintLines)
                ReportSection("Host & substrate", hostSubstrateLines)

                if (entry.notes.isNotBlank()) {
                    ReportSection("Description Notes", listOf(entry.notes))
                }
              }
            }
            }

            Spacer(modifier = Modifier.heightIn(min = Spacing.lg))
        }
    }

    val viewingIndex = viewingPhotoId?.let { id -> entry.photos.indexOfFirst { it.id == id } }?.takeIf { it >= 0 }
    if (viewingIndex != null) {
        PhotoViewerDialog(photos = entry.photos, initialIndex = viewingIndex, onDismiss = { viewingPhotoId = null })
    }
}

/**
 * The owner's exact wording (Stage 2d dispatch) — reported verbatim so it can be adjusted rather
 * than paraphrased in code and drifting from what was actually specified.
 */
private const val EMPTY_FIND_MESSAGE = "There's nothing in here!  You can change this by tapping the three dot menu > tap edit."

/** One report section — omitted entirely when [lines] is empty, never a "Not recorded yet." placeholder (Stage 2d — see this file's own doc comment). */
@Composable
private fun ReportSection(title: String, lines: List<String>) {
    if (lines.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        Text(title, style = MaterialTheme.typography.titleSmall)
        lines.forEach { line -> Text(line, style = MaterialTheme.typography.bodyMedium) }
    }
}

private fun capReportLines(cap: CapSection): List<String> = buildList {
    cap.shape.valueOrNull()?.let { add("Shape: ${it.label}") }
    cap.surface.valueOrNull()?.let { add("Surface: ${it.label}") }
    when (val decorations = cap.decorations) {
        is Feature.Present -> add("Decorations: ${decorations.value.joinToString { it.label }}")
        Feature.Absent -> add("No decorations observed")
        Feature.NotObserved -> Unit
    }
    cap.margin.valueOrNull()?.let { add("Margin: ${it.label}") }
    if (cap.notes.isNotBlank()) add("Notes: ${cap.notes}")
}

private fun hymenophoreReportLines(hymenophore: HymenophoreSection): List<String> = buildList {
    when (val details = hymenophore.details.valueOrNull()) {
        is HymenophoreDetails.Gills -> {
            add("Gills")
            details.attachment.valueOrNull()?.let { add("Attachment: ${it.label}") }
            details.spacing.valueOrNull()?.let { add("Spacing: ${it.label}") }
            details.edge.valueOrNull()?.let { add("Edge: ${it.label}") }
        }
        HymenophoreDetails.Pores -> add("Pores")
        HymenophoreDetails.Teeth -> add("Teeth")
        HymenophoreDetails.SmoothOrWrinkled -> add("Smooth or wrinkled")
        null -> Unit
    }
    if (hymenophore.notes.isNotBlank()) add("Notes: ${hymenophore.notes}")
}

private fun stipeReportLines(stipe: StipeSection): List<String> = buildList {
    when (val details = stipe.details.valueOrNull()) {
        StipeDetails.Absent -> add("No stipe present")
        is StipeDetails.Present -> {
            add("Stipe present")
            details.position.valueOrNull()?.let { add("Position: ${it.label}") }
            details.interior.valueOrNull()?.let { add("Interior: ${it.label}") }
            details.base.valueOrNull()?.let { add("Base: ${it.label}") }
        }
        null -> Unit
    }
    if (stipe.notes.isNotBlank()) add("Notes: ${stipe.notes}")
}

private fun veilReportLines(veil: VeilSection): List<String> = buildList {
    when (val annulus = veil.annulus) {
        is Feature.Present -> add("Annulus: ${annulus.value.label}")
        Feature.Absent -> add("No annulus")
        Feature.NotObserved -> Unit
    }
    when (val volva = veil.volva) {
        is Feature.Present -> add("Volva: ${volva.value.label}")
        Feature.Absent -> add("No volva")
        Feature.NotObserved -> Unit
    }
    if (veil.notes.isNotBlank()) add("Notes: ${veil.notes}")
}

private fun contextFleshReportLines(contextFlesh: ContextFleshSection): List<String> = buildList {
    contextFlesh.texture.valueOrNull()?.let { add("Texture: ${it.label}") }
    when (val colorChange = contextFlesh.colorChangeOnCutting) {
        is Feature.Present -> add("Color change on cutting: ${colorChange.value}")
        Feature.Absent -> add("No color change on cutting")
        Feature.NotObserved -> Unit
    }
    when (val exudate = contextFlesh.exudate) {
        is Feature.Present -> add("Exudate: ${exudate.value}")
        Feature.Absent -> add("No exudate")
        Feature.NotObserved -> Unit
    }
    if (contextFlesh.notes.isNotBlank()) add("Notes: ${contextFlesh.notes}")
}

private fun sporePrintReportLines(sporePrint: SporePrintSection): List<String> = buildList {
    sporePrint.details.valueOrNull()?.let { print ->
        add("Color: ${print.color.label}")
        add("Read on: ${print.readOn}")
    }
    if (sporePrint.notes.isNotBlank()) add("Notes: ${sporePrint.notes}")
}

private fun hostSubstrateReportLines(hostSubstrate: HostSubstrateSection): List<String> = buildList {
    when (val association = hostSubstrate.association.valueOrNull()) {
        is Association.Mycorrhizal -> add(
            "Mycorrhizal" + association.hostSpecies.takeIf { it.isNotBlank() }?.let { " with $it" }.orEmpty(),
        )
        is Association.DeadWood -> add(
            "Growing on dead wood" + association.hostSpecies.takeIf { it.isNotBlank() }?.let { " ($it)" }.orEmpty(),
        )
        Association.SoilOrLitter -> add("Growing in soil or litter")
        Association.Dung -> add("Growing on dung")
        is Association.Other -> add(association.text)
        null -> Unit
    }
    hostSubstrate.forestType.valueOrNull()?.let { add("Forest type: ${it.label}") }
    hostSubstrate.hostHealth.valueOrNull()?.let { add("Host health: ${it.label}") }
    if (hostSubstrate.notes.isNotBlank()) add("Notes: ${hostSubstrate.notes}")
}

/**
 * The find page's photos (RECORD -868: "have the picture be centered inside the find entry itself"; RECORD -871:
 * "Under the title, whole photo, next one peeking (Recommended)"). One photo: the page's width less its margins,
 * centred, in its own shape. Several: a row that swipes sideways ([HorizontalPager], one photo per page, snapping),
 * the current photo centred and the next one peeking in from the side by [REPORT_PHOTO_PEEK], less the gap.
 *
 * Each photo is capped at the smaller of [REPORT_PHOTO_MAX_HEIGHT] and [REPORT_PHOTO_WINDOW_FRACTION] of the window's
 * height (the planner's call, RECORD -874), so the title and the find's details stay in view, in a short landscape
 * window too; a capped photo is shown whole inside the cap, narrower, never cropped. The window's height is read the
 * way the entry page's short-window map cap reads it (`CartographyEntryReportScreen`'s `shortWindowMapCap`).
 *
 * The full-screen viewer has its own previous and next buttons and no swiping (see [PhotoViewerDialog]); that is
 * unchanged, and only this row swipes.
 */
@Composable
private fun ReportPhotos(photos: List<LogPhoto>, onOpen: (String) -> Unit) {
    if (photos.size == 1) {
        Box(modifier = Modifier.fillMaxWidth().testTag(FIND_PAGE_PHOTOS_TAG), contentAlignment = Alignment.Center) {
            Box(modifier = Modifier.testTag(findPagePhotoTag(photos.single().id))) { ReportPhoto(photo = photos.single(), onOpen = onOpen) }
        }
    } else {
        val pagerState = rememberPagerState { photos.size }
        HorizontalPager(
            state = pagerState,
            contentPadding = PaddingValues(horizontal = REPORT_PHOTO_PEEK),
            pageSpacing = Spacing.sm,
            verticalAlignment = Alignment.CenterVertically,
            key = { page -> photos[page].id },
            modifier = Modifier.fillMaxWidth().testTag(FIND_PAGE_PHOTOS_TAG),
        ) { page ->
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Box(modifier = Modifier.testTag(findPagePhotoTag(photos[page].id))) { ReportPhoto(photo = photos[page], onOpen = onOpen) }
            }
        }
    }
}

/** One photo on the find page, through the shared [DecodedPhoto], whole; tapping anywhere on it opens [PhotoViewerDialog]. */
@Composable
private fun ReportPhoto(photo: LogPhoto, onOpen: (String) -> Unit) {
    // The window's own height, not Configuration.screenHeightDp (lint ConfigurationScreenWidthHeight; see currentWindowWidthClass).
    val heightCap = minOf(REPORT_PHOTO_MAX_HEIGHT, LocalWindowInfo.current.containerDpSize.height * REPORT_PHOTO_WINDOW_FRACTION)
    DecodedPhoto(
        relativePath = photo.relativePath,
        // No fillMaxWidth: the width is the row's (its Box gives a maximum and no minimum), and DecodedPhoto's own aspect
        // ratio then picks the full width when the photo fits under the cap, or the cap's height and a narrower width when
        // it does not. A forced full width would leave a tall photo no way to narrow, and it would fall back to its pixel
        // size (a revert check on CI showed the forced width also hid a crop from the first test).
        modifier = Modifier
            .heightIn(max = heightCap)
            // No test tag on this node: DecodedPhotoSemanticsTest pins its semantics, and the tag sits on a Box around it.
            .clickable(onClickLabel = "Open full screen", onClick = { onOpen(photo.id) }),
        wholePhoto = true,
    )
}

/** How far the next photo's page reaches in from each side of the row, before the gap between pages. */
private val REPORT_PHOTO_PEEK = 32.dp

/** The tallest a find page photo is drawn on any window; see [ReportPhotos]. */
private val REPORT_PHOTO_MAX_HEIGHT = 480.dp

/** The share of the window's height a find page photo may take, when that is less than [REPORT_PHOTO_MAX_HEIGHT]. */
private const val REPORT_PHOTO_WINDOW_FRACTION = 0.6f

/** The find page's photo row (one photo or the swiping row), for tests. */
internal const val FIND_PAGE_PHOTOS_TAG = "find-page-photos"

/** One photo on the find page, for tests. */
internal fun findPagePhotoTag(photoId: String): String = "find-page-photo-$photoId"
