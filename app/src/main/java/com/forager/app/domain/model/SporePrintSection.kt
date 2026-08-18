package com.forager.app.domain.model

import java.time.LocalDate

/**
 * Spore print colour. A closed set of the commonly-used print-colour names, plus [Other] for
 * anything that doesn't fit — the same "enum plus an escape hatch" shape as
 * [com.forager.app.domain.model.TaxonFilter]'s fixed categories vs. a searched taxon, used here
 * because print colour genuinely doesn't reduce to eight buckets in every case.
 */
sealed interface SporePrintColor {
    val label: String

    data object White : SporePrintColor { override val label = "White" }
    data object Cream : SporePrintColor { override val label = "Cream" }
    data object PinkSalmon : SporePrintColor { override val label = "Pink/salmon" }
    data object Ochre : SporePrintColor { override val label = "Ochre" }
    data object Rust : SporePrintColor { override val label = "Rust" }
    data object ChocolateBrown : SporePrintColor { override val label = "Chocolate brown" }
    data object PurpleBrown : SporePrintColor { override val label = "Purple-brown" }
    data object Black : SporePrintColor { override val label = "Black" }
    data class Other(val text: String) : SporePrintColor {
        override val label: String get() = text
    }

    companion object {
        /** The closed, non-[Other] variants — for building a picker without reflection. */
        val CLOSED_VARIANTS: List<SporePrintColor> =
            listOf(White, Cream, PinkSalmon, Ochre, Rust, ChocolateBrown, PurpleBrown, Black)
    }
}

/**
 * A spore print reading: colour plus the date it was actually read, which is usually not the find
 * date — see [MushroomLogEntry]'s doc comment on deferred observation. Spore prints are read
 * overnight, so this is the field most likely to be filled in by a later edit rather than at
 * creation.
 */
data class SporePrint(
    val color: SporePrintColor,
    val readOn: LocalDate,
)

/** The spore print section of an entry: [details] is [Observed] because it may simply not have been taken (or read) yet. */
data class SporePrintSection(
    val details: Observed<SporePrint>,
    val notes: String,
) {
    companion object {
        val EMPTY = SporePrintSection(details = Observed.NotObserved, notes = "")
    }
}
