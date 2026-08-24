package com.forager.app.ui.log

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.forager.app.R
import com.forager.app.domain.model.Association
import com.forager.app.domain.model.CapSection
import com.forager.app.domain.model.ContextFleshSection
import com.forager.app.domain.model.Feature
import com.forager.app.domain.model.HostSubstrateSection
import com.forager.app.domain.model.HymenophoreDetails
import com.forager.app.domain.model.HymenophoreSection
import com.forager.app.domain.model.LogPhoto
import com.forager.app.domain.model.MushroomLogEntry
import com.forager.app.domain.model.Observed
import com.forager.app.domain.model.SporePrintSection
import com.forager.app.domain.model.StipeDetails
import com.forager.app.domain.model.StipeSection
import com.forager.app.domain.model.VeilSection
import com.forager.app.domain.model.valueOrNull
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The Journal gallery's default view for an *existing* entry — a compiled, readable report of
 * whatever has been recorded so far, rather than dropping straight into [LogEntryDetailScreen]'s
 * edit form. [JournalTab] only shows this for an entry opened via [JournalTab.onOpenEntry]; a
 * brand-new entry started from the gallery's "+" tile goes straight to editing, since there is
 * nothing yet to report — see [JournalTab]'s own doc comment.
 *
 * Only [Observed.Recorded]/[Feature.Present] values are rendered as lines — an unrecorded field is
 * left out of the report rather than printed as "Not recorded" for every one of the (usually many)
 * fields a field record accumulates gradually; [LogGalleryScreen]'s "Incomplete" tile label is
 * where the gallery already signals that some fields are still open. A section with nothing
 * recorded at all says so explicitly instead of rendering an empty heading with nothing under it.
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

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = LogSpacing.lg, vertical = LogSpacing.sm),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(LogSpacing.sm)) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back to your log")
                }
                Text("Find on ${entry.foundOn}", style = MaterialTheme.typography.titleMedium)
            }
            Box {
                IconButton(onClick = { menuExpanded = true }) {
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
                .padding(horizontal = LogSpacing.lg),
            verticalArrangement = Arrangement.spacedBy(LogSpacing.lg),
        ) {
            Text(
                entry.foundAt?.let { location -> "Found at ${"%.4f".format(location.lat)}, ${"%.4f".format(location.lng)}" }
                    ?: stringResource(R.string.log_entry_no_location),
                style = MaterialTheme.typography.bodySmall,
            )

            entry.ownIdentification?.takeIf { it.isNotBlank() }?.let { identification ->
                Text("Your own identification: $identification", style = MaterialTheme.typography.bodyMedium)
            }

            if (entry.photos.isNotEmpty()) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(LogSpacing.sm)) {
                    entry.photos.forEach { photo -> ReportPhotoThumbnail(photo = photo) }
                }
            }

            HorizontalDivider()

            ReportSection("Cap", capReportLines(entry.cap))
            ReportSection("Hymenophore", hymenophoreReportLines(entry.hymenophore))
            ReportSection("Stipe", stipeReportLines(entry.stipe))
            ReportSection("Veil remnants", veilReportLines(entry.veil))
            ReportSection("Context / flesh", contextFleshReportLines(entry.contextFlesh))
            ReportSection("Spore print", sporePrintReportLines(entry.sporePrint))
            ReportSection("Host & substrate", hostSubstrateReportLines(entry.hostSubstrate))

            if (entry.notes.isNotBlank()) {
                ReportSection("Notes", listOf(entry.notes))
            }

            Spacer(modifier = Modifier.heightIn(min = LogSpacing.lg))
        }
    }
}

/** One report section: a heading, then either its compiled lines or an explicit "nothing yet" line. */
@Composable
private fun ReportSection(title: String, lines: List<String>) {
    Column(verticalArrangement = Arrangement.spacedBy(LogSpacing.xs)) {
        Text(title, style = MaterialTheme.typography.titleSmall)
        if (lines.isEmpty()) {
            Text(
                "Not recorded yet.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            lines.forEach { line -> Text(line, style = MaterialTheme.typography.bodyMedium) }
        }
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

private const val REPORT_PHOTO_SIZE_DP = 88
private const val REPORT_PHOTO_SAMPLE_SIZE = 4

/** Read-only counterpart to [LogEntryDetailScreen]'s removable [LogPhotoThumbnail] — same decode pattern, no remove action. */
@Composable
private fun ReportPhotoThumbnail(photo: LogPhoto) {
    val context = LocalContext.current
    var bitmap by remember(photo.id) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(photo.id) {
        bitmap = withContext(Dispatchers.IO) {
            runCatching {
                val options = BitmapFactory.Options().apply { inSampleSize = REPORT_PHOTO_SAMPLE_SIZE }
                BitmapFactory.decodeFile(File(context.filesDir, photo.relativePath).absolutePath, options)
                    ?.asImageBitmap()
            }.getOrNull()
        }
    }

    Box(modifier = Modifier.size(REPORT_PHOTO_SIZE_DP.dp)) {
        val loaded = bitmap
        if (loaded != null) {
            Image(
                bitmap = loaded,
                contentDescription = "Log photo",
                modifier = Modifier.size(REPORT_PHOTO_SIZE_DP.dp),
                contentScale = ContentScale.Crop,
            )
        } else {
            Box(modifier = Modifier.size(REPORT_PHOTO_SIZE_DP.dp).background(MaterialTheme.colorScheme.surfaceVariant))
        }
    }
}
