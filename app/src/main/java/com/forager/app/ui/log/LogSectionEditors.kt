package com.forager.app.ui.log

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.forager.app.domain.model.Association
import com.forager.app.domain.model.CapSection
import com.forager.app.domain.model.CapShape
import com.forager.app.domain.model.CapSurface
import com.forager.app.domain.model.CapMargin
import com.forager.app.domain.model.ContextFleshSection
import com.forager.app.domain.model.FleshTexture
import com.forager.app.domain.model.ForestType
import com.forager.app.domain.model.GillAttachment
import com.forager.app.domain.model.GillEdge
import com.forager.app.domain.model.GillSpacing
import com.forager.app.domain.model.HostHealth
import com.forager.app.domain.model.HostSubstrateSection
import com.forager.app.domain.model.HymenophoreDetails
import com.forager.app.domain.model.HymenophoreSection
import com.forager.app.domain.model.AnnulusType
import com.forager.app.domain.model.Observed
import com.forager.app.domain.model.SporePrint
import com.forager.app.domain.model.SporePrintColor
import com.forager.app.domain.model.SporePrintSection
import com.forager.app.domain.model.StipeBase
import com.forager.app.domain.model.StipeDetails
import com.forager.app.domain.model.StipeInterior
import com.forager.app.domain.model.StipePosition
import com.forager.app.domain.model.StipeSection
import com.forager.app.domain.model.VeilSection
import com.forager.app.domain.model.VolvaType
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/** A toggleable chip for selecting one variant of a sealed choice (which [HymenophoreDetails]/[StipeDetails]/[Association] this entry has) — the sealed-type analogue of [FilterChip] used inside [ObservedEnumField] for plain enums. */
@Composable
private fun KindChip(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(selected = selected, onClick = onClick, label = { Text(label) })
}

@Composable
internal fun CapEditor(section: CapSection, modifier: Modifier = Modifier, onChanged: (CapSection) -> Unit) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(LogSpacing.md)) {
        ObservedEnumField("Shape", section.shape, CapShape.entries, CapShape::label) { onChanged(section.copy(shape = it)) }
        ObservedEnumField("Surface", section.surface, CapSurface.entries, CapSurface::label) { onChanged(section.copy(surface = it)) }
        CapDecorationsField(section.decorations) { onChanged(section.copy(decorations = it)) }
        ObservedEnumField("Margin", section.margin, CapMargin.entries, CapMargin::label) { onChanged(section.copy(margin = it)) }
        NotesField(section.notes) { onChanged(section.copy(notes = it)) }
    }
}

/**
 * The type-system-enforced inapplicability rule made concrete: choosing "Gills" is the only way
 * [HymenophoreDetails.Gills]'s attachment/spacing/edge fields come into existence at all, and
 * choosing anything else (or nothing) leaves no field for them to be wrongly filled into — see
 * [HymenophoreDetails]'s doc comment.
 */
@Composable
internal fun HymenophoreEditor(section: HymenophoreSection, modifier: Modifier = Modifier, onChanged: (HymenophoreSection) -> Unit) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(LogSpacing.md)) {
        val details = section.details
        val recorded = (details as? Observed.Recorded<HymenophoreDetails>)?.value

        Text("Type", style = MaterialTheme.typography.labelLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(LogSpacing.xs)) {
            val gillsSelected = recorded is HymenophoreDetails.Gills
            KindChip("Gills", gillsSelected) {
                onChanged(
                    section.copy(
                        details = if (gillsSelected) {
                            Observed.NotObserved
                        } else {
                            Observed.Recorded(HymenophoreDetails.Gills(Observed.NotObserved, Observed.NotObserved, Observed.NotObserved))
                        },
                    ),
                )
            }
            val poresSelected = recorded is HymenophoreDetails.Pores
            KindChip("Pores", poresSelected) {
                onChanged(section.copy(details = if (poresSelected) Observed.NotObserved else Observed.Recorded(HymenophoreDetails.Pores)))
            }
            val teethSelected = recorded is HymenophoreDetails.Teeth
            KindChip("Teeth", teethSelected) {
                onChanged(section.copy(details = if (teethSelected) Observed.NotObserved else Observed.Recorded(HymenophoreDetails.Teeth)))
            }
            val smoothSelected = recorded is HymenophoreDetails.SmoothOrWrinkled
            KindChip("Smooth or wrinkled", smoothSelected) {
                onChanged(
                    section.copy(
                        details = if (smoothSelected) Observed.NotObserved else Observed.Recorded(HymenophoreDetails.SmoothOrWrinkled),
                    ),
                )
            }
        }
        if (details is Observed.NotObserved) NotRecordedIndicator()

        val gills = recorded as? HymenophoreDetails.Gills
        if (gills != null) {
            ObservedEnumField("Attachment", gills.attachment, GillAttachment.entries, GillAttachment::label) {
                onChanged(section.copy(details = Observed.Recorded(gills.copy(attachment = it))))
            }
            ObservedEnumField("Spacing", gills.spacing, GillSpacing.entries, GillSpacing::label) {
                onChanged(section.copy(details = Observed.Recorded(gills.copy(spacing = it))))
            }
            ObservedEnumField("Edge", gills.edge, GillEdge.entries, GillEdge::label) {
                onChanged(section.copy(details = Observed.Recorded(gills.copy(edge = it))))
            }
        }
        NotesField(section.notes) { onChanged(section.copy(notes = it)) }
    }
}

@Composable
internal fun StipeEditor(section: StipeSection, modifier: Modifier = Modifier, onChanged: (StipeSection) -> Unit) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(LogSpacing.md)) {
        val details = section.details
        val recorded = (details as? Observed.Recorded<StipeDetails>)?.value

        Text("Stipe", style = MaterialTheme.typography.labelLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(LogSpacing.xs)) {
            val absentSelected = recorded is StipeDetails.Absent
            KindChip("Absent", absentSelected) {
                onChanged(section.copy(details = if (absentSelected) Observed.NotObserved else Observed.Recorded(StipeDetails.Absent)))
            }
            val presentSelected = recorded is StipeDetails.Present
            KindChip("Present", presentSelected) {
                onChanged(
                    section.copy(
                        details = if (presentSelected) {
                            Observed.NotObserved
                        } else {
                            Observed.Recorded(StipeDetails.Present(Observed.NotObserved, Observed.NotObserved, Observed.NotObserved))
                        },
                    ),
                )
            }
        }
        if (details is Observed.NotObserved) NotRecordedIndicator()

        val present = recorded as? StipeDetails.Present
        if (present != null) {
            ObservedEnumField("Position", present.position, StipePosition.entries, StipePosition::label) {
                onChanged(section.copy(details = Observed.Recorded(present.copy(position = it))))
            }
            ObservedEnumField("Interior", present.interior, StipeInterior.entries, StipeInterior::label) {
                onChanged(section.copy(details = Observed.Recorded(present.copy(interior = it))))
            }
            ObservedEnumField("Base", present.base, StipeBase.entries, StipeBase::label) {
                onChanged(section.copy(details = Observed.Recorded(present.copy(base = it))))
            }
        }
        NotesField(section.notes) { onChanged(section.copy(notes = it)) }
    }
}

@Composable
internal fun VeilEditor(section: VeilSection, modifier: Modifier = Modifier, onChanged: (VeilSection) -> Unit) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(LogSpacing.md)) {
        FeatureEnumField("Annulus", section.annulus, AnnulusType.entries, AnnulusType::label) { onChanged(section.copy(annulus = it)) }
        FeatureEnumField("Volva", section.volva, VolvaType.entries, VolvaType::label) { onChanged(section.copy(volva = it)) }
        NotesField(section.notes) { onChanged(section.copy(notes = it)) }
    }
}

@Composable
internal fun ContextFleshEditor(section: ContextFleshSection, modifier: Modifier = Modifier, onChanged: (ContextFleshSection) -> Unit) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(LogSpacing.md)) {
        ObservedEnumField("Texture", section.texture, FleshTexture.entries, FleshTexture::label) { onChanged(section.copy(texture = it)) }
        FeatureTextField("Colour change on cutting", section.colorChangeOnCutting) { onChanged(section.copy(colorChangeOnCutting = it)) }
        FeatureTextField("Exudate (latex)", section.exudate) { onChanged(section.copy(exudate = it)) }
        NotesField(section.notes) { onChanged(section.copy(notes = it)) }
    }
}

/** [SporePrint.readOn] is normally a day or more after [MushroomLogEntry.foundOn][com.forager.app.domain.model.MushroomLogEntry.foundOn] — see this section's doc comment on deferred observation — so its date picker is edited independently, not derived from the find date. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SporePrintEditor(section: SporePrintSection, modifier: Modifier = Modifier, onChanged: (SporePrintSection) -> Unit) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(LogSpacing.md)) {
        val details = section.details
        val recorded = (details as? Observed.Recorded<SporePrint>)?.value

        Text("Colour", style = MaterialTheme.typography.labelLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(LogSpacing.xs)) {
            SporePrintColor.CLOSED_VARIANTS.forEach { colorOption ->
                val selected = recorded?.color == colorOption
                KindChip(colorOption.label, selected) {
                    onChanged(section.copy(details = sporePrintColorToggled(details, colorOption, selected)))
                }
            }
            val otherSelected = recorded?.color is SporePrintColor.Other
            KindChip("Other", otherSelected) {
                onChanged(section.copy(details = sporePrintColorToggled(details, SporePrintColor.Other(""), otherSelected)))
            }
        }
        if (details is Observed.NotObserved) NotRecordedIndicator()

        val otherColor = recorded?.color as? SporePrintColor.Other
        if (otherColor != null) {
            OutlinedTextField(
                value = otherColor.text,
                onValueChange = { text ->
                    onChanged(section.copy(details = Observed.Recorded(recorded.copy(color = SporePrintColor.Other(text)))))
                },
                label = { Text("Describe colour") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        if (recorded != null) {
            SporePrintDateField(recorded.readOn) { newDate ->
                onChanged(section.copy(details = Observed.Recorded(recorded.copy(readOn = newDate))))
            }
        }
        NotesField(section.notes) { onChanged(section.copy(notes = it)) }
    }
}

private fun sporePrintColorToggled(
    details: Observed<SporePrint>,
    option: SporePrintColor,
    wasSelected: Boolean,
): Observed<SporePrint> {
    if (wasSelected) return Observed.NotObserved
    val existingDate = (details as? Observed.Recorded<SporePrint>)?.value?.readOn ?: LocalDate.now()
    return Observed.Recorded(SporePrint(color = option, readOn = existingDate))
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SporePrintDateField(date: LocalDate, onDateChanged: (LocalDate) -> Unit) {
    var showPicker by remember { mutableStateOf(false) }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(LogSpacing.sm)) {
        Text("Read on: $date", style = MaterialTheme.typography.bodyMedium)
        TextButton(onClick = { showPicker = true }) { Text("Change") }
    }
    if (showPicker) {
        // DatePicker works in UTC-midnight epoch millis regardless of device time zone — same
        // conversion TripDatePickerDialog uses, restricted here to today-or-earlier since a print
        // can't be read before it exists and reading one in the future makes no sense.
        val todayUtcMillis = LocalDate.now().atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        val state = rememberDatePickerState(
            initialSelectedDateMillis = date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
            selectableDates = object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long): Boolean = utcTimeMillis <= todayUtcMillis
            },
        )
        DatePickerDialog(
            onDismissRequest = { showPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { millis ->
                        onDateChanged(Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate())
                    }
                    showPicker = false
                }) { Text("Set") }
            },
            dismissButton = { TextButton(onClick = { showPicker = false }) { Text("Cancel") } },
        ) {
            DatePicker(state = state)
        }
    }
}

@Composable
internal fun HostSubstrateEditor(section: HostSubstrateSection, modifier: Modifier = Modifier, onChanged: (HostSubstrateSection) -> Unit) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(LogSpacing.md)) {
        val association = section.association
        val recorded = (association as? Observed.Recorded<Association>)?.value

        Text("Growing on/with", style = MaterialTheme.typography.labelLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(LogSpacing.xs)) {
            val mycoSelected = recorded is Association.Mycorrhizal
            KindChip("Mycorrhizal", mycoSelected) {
                onChanged(section.copy(association = if (mycoSelected) Observed.NotObserved else Observed.Recorded(Association.Mycorrhizal(""))))
            }
            val deadWoodSelected = recorded is Association.DeadWood
            KindChip("Dead wood", deadWoodSelected) {
                onChanged(section.copy(association = if (deadWoodSelected) Observed.NotObserved else Observed.Recorded(Association.DeadWood(""))))
            }
            val soilSelected = recorded is Association.SoilOrLitter
            KindChip("Soil/litter", soilSelected) {
                onChanged(section.copy(association = if (soilSelected) Observed.NotObserved else Observed.Recorded(Association.SoilOrLitter)))
            }
            val dungSelected = recorded is Association.Dung
            KindChip("Dung", dungSelected) {
                onChanged(section.copy(association = if (dungSelected) Observed.NotObserved else Observed.Recorded(Association.Dung)))
            }
            val otherSelected = recorded is Association.Other
            KindChip("Other", otherSelected) {
                onChanged(section.copy(association = if (otherSelected) Observed.NotObserved else Observed.Recorded(Association.Other(""))))
            }
        }
        if (association is Observed.NotObserved) NotRecordedIndicator()

        when (recorded) {
            is Association.Mycorrhizal -> OutlinedTextField(
                value = recorded.hostSpecies,
                onValueChange = { onChanged(section.copy(association = Observed.Recorded(Association.Mycorrhizal(it)))) },
                label = { Text("Host species") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            is Association.DeadWood -> OutlinedTextField(
                value = recorded.hostSpecies,
                onValueChange = { onChanged(section.copy(association = Observed.Recorded(Association.DeadWood(it)))) },
                label = { Text("Host species") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            is Association.Other -> OutlinedTextField(
                value = recorded.text,
                onValueChange = { onChanged(section.copy(association = Observed.Recorded(Association.Other(it)))) },
                label = { Text("Describe") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            else -> Unit
        }

        ObservedEnumField("Forest type", section.forestType, ForestType.entries, ForestType::label) { onChanged(section.copy(forestType = it)) }
        ObservedEnumField("Host health", section.hostHealth, HostHealth.entries, HostHealth::label) { onChanged(section.copy(hostHealth = it)) }
        NotesField(section.notes) { onChanged(section.copy(notes = it)) }
    }
}
