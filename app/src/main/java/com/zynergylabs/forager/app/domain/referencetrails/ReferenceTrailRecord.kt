package com.zynergylabs.forager.app.domain.referencetrails

/**
 * Step 8, PR 8c (dispatch 2026-10-26-16 and its Amendment 1; RECORD -1680, -1681): what a tap on an official-trails
 * band reads out. The band is the Forest Service's and BLM's own record, drawn apart from the trail map and never
 * routed on (-445, -1176); its bubble says whose trail it is and that routes follow the trail map, not this layer.
 *
 * Plain Kotlin with no map or UI type, so it runs headless: the tap path hands it the tapped feature's attributes by
 * name ([fromAttributes]) and shows its two lines.
 */
enum class ReferenceAgency { FOREST_SERVICE, BLM }

/**
 * The attribute names the archive's features carry, mirrored from `map-style/overrides/forager-reference.mjs`, the one
 * place they are written (`ReferenceTrailRecordTest` reads that file and holds these to it). They are premises for
 * Aspen's archive (the step 8 report, section 7), not facts until it exists: the Forest Service's are its service's
 * own field names, BLM's route name is the one Juniper read from the BLM GTLF public display schema on 2026-10-17
 * (`trail-data-comparison` branch, `analyze.py:325-327`).
 */
object ReferenceTrailFields {
    /** The Forest Service trail name (forager-reference.mjs `FS_TRAIL_NAME_FIELD`). */
    const val FS_TRAIL_NAME = "trail_name"

    /** The Forest Service trail number (forager-reference.mjs `FS_TRAIL_NUMBER_FIELD`), the label along its band. */
    const val FS_TRAIL_NUMBER = "trail_no"

    /** BLM's route name (forager-reference.mjs `BLM_ROUTE_NAME_FIELD`). BLM's numbering field is not settled, so none is read. */
    const val BLM_ROUTE_NAME = "ROUTE_PRMRY_NM"

    /** The attributes a band of [agency] is read for. */
    fun of(agency: ReferenceAgency): List<String> = when (agency) {
        ReferenceAgency.FOREST_SERVICE -> listOf(FS_TRAIL_NUMBER, FS_TRAIL_NAME)
        ReferenceAgency.BLM -> listOf(BLM_ROUTE_NAME)
    }
}

/**
 * One tapped official trail: its agency, and its number and name as published, each `null` when the feature does not
 * carry it (absent or blank). Nothing is made up for a missing part (Amendment 1, the owner: "Confirm (Recommended)");
 * the line reads only what the record has. [name] is held as published; the line shows it as published unless it is all
 * capitals, which is shown in normal case ([shownName], Amendment 2). The number is always shown as published.
 */
data class ReferenceTrailRecord(val agency: ReferenceAgency, val number: String?, val name: String?) {

    /**
     * The bubble's first line, the owner's wording (RECORD -1680, -1681): "Forest Service trail 017 · Three Blaze";
     * no number, "Forest Service trail · Three Blaze"; no name, "Forest Service trail 017"; neither, "Forest Service
     * trail". BLM: "BLM trail · Badlands Rock", or "BLM trail" with no name.
     */
    val firstLine: String
        get() {
            val base = when (agency) {
                ReferenceAgency.FOREST_SERVICE -> "Forest Service trail"
                ReferenceAgency.BLM -> "BLM trail"
            }
            val numbered = number?.let { "$base $it" } ?: base
            return name?.let { "$numbered · ${shownName(it)}" } ?: numbered
        }

    /** The bubble's second line, the same for every band (RECORD -1680). */
    val secondLine: String get() = OFFICIAL_RECORD_LINE

    companion object {
        /** The owner's second line (RECORD -1680, "Confirm as sketched (Recommended)"): the -445 rule, said plainly. */
        const val OFFICIAL_RECORD_LINE = "Official record. Routes follow the trail map, not this layer."

        /**
         * The record a band of [agency] reads from its attributes, looked up by name with [attribute]. A value that is
         * absent or blank is missing; any other is kept as published, trimmed. BLM carries no number (its numbering field
         * is not settled, forager-reference.mjs).
         */
        fun fromAttributes(agency: ReferenceAgency, attribute: (String) -> String?): ReferenceTrailRecord {
            fun read(field: String) = attribute(field)?.trim()?.takeIf { it.isNotEmpty() }
            return when (agency) {
                ReferenceAgency.FOREST_SERVICE -> ReferenceTrailRecord(agency, read(ReferenceTrailFields.FS_TRAIL_NUMBER), read(ReferenceTrailFields.FS_TRAIL_NAME))
                ReferenceAgency.BLM -> ReferenceTrailRecord(agency, number = null, name = read(ReferenceTrailFields.BLM_ROUTE_NAME))
            }
        }
    }
}

/**
 * A trail or route name as the bubble shows it (dispatch 2026-10-26-16 Amendment 2; RECORD -1688, the owner: "Normal case
 * (Recommended)"): a name published entirely in capitals ("WEST LEG NORDIC") is shown in normal case ("West Leg Nordic");
 * a name with any lower-case letter, or with no letter at all, is shown as published.
 *
 * Normal case, word by word (RECORD -1690, the owner: "Keep short words and codes"; RECORD -1692, "Fix as proposed";
 * RECORD -1694, "Small known list (Recommended)"). A word is a run of letters, digits and apostrophes, so a hyphen, a
 * bracket, a full stop or a space ends one ("NORTH-FORK (UPPER)" -> "North-Fork (Upper)", "BOB'S" -> "Bob's"). Each word
 * is read by the first of these that fits it:
 *  1. An ordinal, a number and ST, ND, RD or TH, reads its suffix in lower case ("2ND" -> "2nd", "21ST" -> "21st").
 *  2. Any other word with a digit stays as published ("17A", "4WD").
 *  3. A Roman numeral from I to XX stays ("II", "XIV"; "I" alone counts as one; "XXI" and "MIX" are words).
 *  4. A known name stays in capitals ([KnownCapitals]): one of its list, "US" directly before a number ("US 101",
 *     "US-26"), or "AT" as the name's first word. This comes before the full-stop check, so "FS." stays "FS.".
 *  5. A word of one or two letters stays as published when it is not one of [COMMON_SHORT_WORDS] and is not directly
 *     followed by a full stop ("NF"; but "MT." -> "Mt.", "ST." -> "St."). The numeral is checked first, so "II." stays.
 *  6. "MC" and more reads "Mc" and a capital ("MCDONALD" -> "McDonald").
 *  7. A letter, an apostrophe and more reads two capitals ("O'BRIEN" -> "O'Brien", "D'ARCY" -> "D'Arcy").
 *  8. Every other word keeps its first letter capital and the rest go lower case ("OF" -> "Of", "MIX" -> "Mix").
 * No dictionary beyond these lists.
 */
internal fun shownName(name: String): String {
    if (name.any { it.isLowerCase() } || name.none { it.isUpperCase() }) return name
    val firstWordAt = WORD.find(name)?.range?.first
    return WORD.replace(name) { match ->
        val word = match.value
        val after = match.range.last + 1
        val abbreviated = name.getOrNull(after) == '.'
        val first = match.range.first == firstWordAt
        val beforeNumber = name.getOrNull(after)?.let { it in KnownCapitals.BEFORE_NUMBER_SEPARATORS } == true &&
            name.getOrNull(after + 1)?.isDigit() == true
        ordinal(word) ?: when {
            keptAsPublished(word, abbreviated, first, beforeNumber) -> word
            word.length > 2 && word.startsWith("MC") -> "Mc" + capitalised(word.drop(2))
            word.length > 2 && word[0].isLetter() && word[1] in APOSTROPHES -> word.take(2) + capitalised(word.drop(2))
            else -> capitalised(word)
        }
    }
}

/**
 * The names kept in capitals inside an all-capitals name (RECORD -1694, the owner: "Small known list (Recommended)"), in
 * one place: the agencies and the long trails, "US" only directly before a number ("US 101"; elsewhere it is the common
 * word, [COMMON_SHORT_WORDS]), and "AT" only as the name's first word (elsewhere it is the common word "at").
 */
internal object KnownCapitals {
    val LIST = setOf("BLM", "USFS", "NFS", "NF", "FS", "PCT", "CDT")
    const val US = "US"
    const val AT_FIRST = "AT"

    /** What may stand between "US" and the number it is kept before: one space or one hyphen ("US 101", "US-26"). */
    val BEFORE_NUMBER_SEPARATORS = setOf(' ', '-')
}

/** [word] with its first letter capital and the rest lower case. */
private fun capitalised(word: String): String = word.first().titlecaseChar() + word.drop(1).lowercase()

/** [word] as an ordinal with its suffix in lower case ("2ND" -> "2nd"), or `null` when it is not a number and a suffix. */
private fun ordinal(word: String): String? {
    val digits = word.dropLast(2)
    val suffix = word.takeLast(2)
    return if (digits.isNotEmpty() && digits.all { it.isDigit() } && suffix in ORDINAL_SUFFIXES) digits + suffix.lowercase() else null
}

private val ORDINAL_SUFFIXES = setOf("ST", "ND", "RD", "TH")

private val APOSTROPHES = setOf('\'', '\u2019')

/** A word of a name, for [shownName]: a run of letters, digits and apostrophes (straight or curly). */
private val WORD = Regex("""[\p{L}\p{N}'\u2019]+""")

/** The Roman numerals kept as published in an all-capitals name: I to XX only (RECORD -1692). */
private val ROMAN_NUMERALS = setOf(
    "I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X",
    "XI", "XII", "XIII", "XIV", "XV", "XVI", "XVII", "XVIII", "XIX", "XX",
)

/** The one- and two-letter words capitalised as words in an all-capitals name; any other is read as a code (RECORD -1692). */
internal val COMMON_SHORT_WORDS = setOf(
    "A", "AN", "AS", "AT", "BE", "BY", "DO", "GO", "HE", "IF", "IN", "IS", "IT",
    "ME", "MY", "NO", "OF", "OH", "ON", "OR", "OX", "SO", "TO", "UP", "US", "WE",
)

/**
 * Whether [word] of an all-capitals name stays as published: a word with a digit, a numeral from I to XX, a known name
 * ([KnownCapitals]: [first] says it is the name's first word, [beforeNumber] that a number directly follows it), or a short
 * code. A short word [abbreviated] (directly followed by a full stop) is an abbreviation, capitalised as a word (RECORD -1692).
 */
private fun keptAsPublished(word: String, abbreviated: Boolean, first: Boolean, beforeNumber: Boolean): Boolean =
    word.any { it.isDigit() } ||
        word in ROMAN_NUMERALS ||
        word in KnownCapitals.LIST ||
        (word == KnownCapitals.US && beforeNumber) ||
        (word == KnownCapitals.AT_FIRST && first) ||
        (word.count { it.isLetter() } <= 2 && word !in COMMON_SHORT_WORDS && !abbreviated)
