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
 * Normal case, word by word (RECORD -1690, the owner: "Keep short words and codes"). A word is a run of letters, digits
 * and apostrophes, so a hyphen, a bracket, a full stop or a space ends one ("NORTH-FORK (UPPER)" -> "North-Fork (Upper)",
 * "BOB'S" -> "Bob's"). A word stays as published when it holds a digit ("17A", "4WD"), is a Roman numeral ("II", "XIV";
 * "I" alone counts as one), or has one or two letters and is not one of [COMMON_SHORT_WORDS] ("NF", "FS"). Every other
 * word keeps its first letter capital and the rest go lower case ("OF" -> "Of", "THE" -> "The"). No dictionary beyond that
 * list: a two-letter word outside it is read as a code ("MT." stays "MT.", "NO" stays "NO"), and a longer word that
 * happens to spell a valid numeral stays in capitals ("MIX", "LIV", "DIX").
 */
internal fun shownName(name: String): String {
    if (name.any { it.isLowerCase() } || name.none { it.isUpperCase() }) return name
    return WORD.replace(name) { match ->
        val word = match.value
        if (keptAsPublished(word)) word else word.first().titlecaseChar() + word.drop(1).lowercase()
    }
}

/** A word of a name, for [shownName]: a run of letters, digits and apostrophes (straight or curly). */
private val WORD = Regex("""[\p{L}\p{N}'\u2019]+""")

/** A Roman numeral, I to MMMCMXCIX, written in capitals. */
private val ROMAN_NUMERAL = Regex("^M{0,3}(CM|CD|D?C{0,3})(XC|XL|L?X{0,3})(IX|IV|V?I{0,3})$")

/** The one- and two-letter words capitalised as words in an all-capitals name; any other is read as a code (RECORD -1690). */
internal val COMMON_SHORT_WORDS = setOf("A", "AN", "AS", "AT", "BY", "IN", "IS", "IT", "OF", "ON", "OR", "TO", "UP")

/** Whether [word] of an all-capitals name stays as published: a word with a digit, a Roman numeral, or a short code. */
private fun keptAsPublished(word: String): Boolean =
    word.any { it.isDigit() } ||
        ROMAN_NUMERAL.matches(word) ||
        (word.count { it.isLetter() } <= 2 && word !in COMMON_SHORT_WORDS)
