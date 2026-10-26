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
 * Normal case is plain word capitalisation with no dictionary: a letter that follows anything other than a letter, a digit
 * or an apostrophe starts a word and is capitalised; every other letter is lower case. So short words are capitalised
 * like any other ("TRAIL OF THE CEDARS" -> "Trail Of The Cedars"), abbreviations and Roman numerals lose their capitals
 * after the first ("NF" -> "Nf", "LOOP II" -> "Loop Ii"), a hyphen or a bracket starts a word ("NORTH-FORK (UPPER)" ->
 * "North-Fork (Upper)"), an apostrophe does not ("BOB'S" -> "Bob's"), and letters after a digit stay with it ("17A" ->
 * "17a", "4WD" -> "4wd"). Digits are unchanged.
 */
internal fun shownName(name: String): String {
    if (name.any { it.isLowerCase() } || name.none { it.isUpperCase() }) return name
    val shown = StringBuilder(name.length)
    var previous: Char? = null
    for (c in name) {
        val startsWord = previous == null || !(previous.isLetterOrDigit() || previous == '\'' || previous == '\u2019')
        shown.append(if (c.isLetter() && startsWord) c.titlecaseChar() else c.lowercaseChar())
        previous = c
    }
    return shown.toString()
}
