package com.zynergylabs.forager.app.domain.referencetrails

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Step 8c (dispatch 2026-10-26-16 and Amendment 1; RECORD -1680, -1681): what a tapped official-trails band reads out,
 * from fixture attributes as the archive's features would carry them. Headless: the record is plain Kotlin.
 */
class ReferenceTrailRecordTest {

    private fun fs(vararg attributes: Pair<String, String?>) =
        ReferenceTrailRecord.fromAttributes(ReferenceAgency.FOREST_SERVICE) { mapOf(*attributes)[it] }

    private fun blm(vararg attributes: Pair<String, String?>) =
        ReferenceTrailRecord.fromAttributes(ReferenceAgency.BLM) { mapOf(*attributes)[it] }

    @Test
    fun `a Forest Service trail with its number and name reads the owner's line`() {
        val record = fs("trail_no" to "017", "trail_name" to "Three Blaze")
        assertEquals("Forest Service trail 017 · Three Blaze", record.firstLine)
        assertEquals("Official record. Routes follow the trail map, not this layer.", record.secondLine)
    }

    @Test
    fun `a Forest Service trail missing a part reads only what it has`() {
        assertEquals("Forest Service trail · Three Blaze", fs("trail_name" to "Three Blaze").firstLine)
        assertEquals("Forest Service trail 017", fs("trail_no" to "017").firstLine)
        assertEquals("Forest Service trail", fs().firstLine)
    }

    @Test
    fun `a blank or padded value is read as missing or trimmed, never shown as an empty part`() {
        assertEquals("Forest Service trail · Three Blaze", fs("trail_no" to "  ", "trail_name" to " Three Blaze ").firstLine)
        assertEquals("Forest Service trail 017", fs("trail_no" to "017", "trail_name" to "").firstLine)
        assertEquals("BLM trail", blm("ROUTE_PRMRY_NM" to " ").firstLine)
    }

    @Test
    fun `a BLM trail reads its route name, or BLM trail alone, and never a number`() {
        assertEquals("BLM trail · Badlands Rock", blm("ROUTE_PRMRY_NM" to "Badlands Rock").firstLine)
        assertEquals("BLM trail", blm().firstLine)
        // BLM's numbering field is not settled (forager-reference.mjs), and the Forest Service's fields are not BLM's.
        assertEquals("BLM trail", blm("trail_no" to "017", "trail_name" to "Three Blaze", "FAMS_ID" to "123").firstLine)
        assertEquals(ReferenceTrailRecord(ReferenceAgency.BLM, number = null, name = "Badlands Rock"), blm("ROUTE_PRMRY_NM" to "Badlands Rock"))
    }

    /** Amendment 2 (RECORD -1688, the owner: "Normal case (Recommended)"): a name published all in capitals. */
    @Test
    fun `a name published entirely in capitals is shown in normal case, for both agencies`() {
        assertEquals("Forest Service trail 017 · West Leg Nordic", fs("trail_no" to "017", "trail_name" to "WEST LEG NORDIC").firstLine)
        assertEquals("BLM trail · West Leg Nordic", blm("ROUTE_PRMRY_NM" to "WEST LEG NORDIC").firstLine)
    }

    @Test
    fun `a name with any lower-case letter is shown as published`() {
        assertEquals("Forest Service trail · McDONALD RIDGE", fs("trail_name" to "McDONALD RIDGE").firstLine)
        assertEquals("Forest Service trail · west leg nordic", fs("trail_name" to "west leg nordic").firstLine)
        assertEquals("BLM trail · Badlands Rock", blm("ROUTE_PRMRY_NM" to "Badlands Rock").firstLine)
    }

    /**
     * The rule's edges (RECORD -1690, the owner: "Keep short words and codes"): in an all-capitals name each word is
     * capitalised, except a word with a digit, a Roman numeral, or a word of one or two letters that is not one of the
     * common short words (A, AN, AS, AT, BY, IN, IS, IT, OF, ON, OR, TO, UP), which stay as published. No dictionary beyond
     * that list. A word is a run of letters, digits and apostrophes, so a hyphen or a bracket starts a new one.
     */
    @Test
    fun `in an all-capitals name, codes, numerals and words with digits stay as published`() {
        fun shown(name: String) = fs("trail_name" to name).firstLine.removePrefix("Forest Service trail · ")
        assertEquals("NF Loop II", shown("NF LOOP II"))
        assertEquals("Spur 17A", shown("SPUR 17A"))
        assertEquals("4WD Road", shown("4WD ROAD"))
        assertEquals("FS Spur", shown("FS SPUR"))
        assertEquals("Loop III", shown("LOOP III"))
        assertEquals("Lake IV Tie", shown("LAKE IV TIE"))
        assertEquals("Camp XIV", shown("CAMP XIV"))
        // "I" alone is a numeral (Rowan, relaying the owner), so it stays as published either way.
        assertEquals("Ridge I", shown("RIDGE I"))
    }

    @Test
    fun `in an all-capitals name, the common short words and every longer word are capitalised as words`() {
        fun shown(name: String) = fs("trail_name" to name).firstLine.removePrefix("Forest Service trail · ")
        assertEquals("Trail Of The Cedars", shown("TRAIL OF THE CEDARS"))
        assertEquals("West Leg Nordic", shown("WEST LEG NORDIC"))
        assertEquals("Up And Over To A Lake", shown("UP AND OVER TO A LAKE"))
        assertEquals("North-Fork (Upper)", shown("NORTH-FORK (UPPER)"))
        assertEquals("Bob's Cabin", shown("BOB'S CABIN"))
        assertEquals("Bob’s Cabin", shown("BOB’S CABIN"))
        // A name with no letter at all is not all capitals; it reads as published.
        assertEquals("Forest Service trail · 1234", fs("trail_name" to "1234").firstLine)
    }

    @Test
    fun `the number is never recased, only the name`() {
        assertEquals("Forest Service trail 017A · Spur", fs("trail_no" to "017A", "trail_name" to "SPUR").firstLine)
    }

    @Test
    fun `the second line is the same for both agencies`() {
        assertEquals(fs().secondLine, blm().secondLine)
        assertEquals(ReferenceTrailRecord.OFFICIAL_RECORD_LINE, blm().secondLine)
    }

    @Test
    fun `each agency is read for its own fields`() {
        assertEquals(listOf("trail_no", "trail_name"), ReferenceTrailFields.of(ReferenceAgency.FOREST_SERVICE))
        assertEquals(listOf("ROUTE_PRMRY_NM"), ReferenceTrailFields.of(ReferenceAgency.BLM))
    }

    /** The field names are written once, in the style's design source; the Kotlin copies are held to it here. */
    @Test
    fun `the field names are the ones forager-reference_mjs writes`() {
        val source = File("../map-style/overrides/forager-reference.mjs").readText()
        fun constant(name: String) = Regex("""export const $name\s*=\s*"([^"]*)";""").find(source)?.groupValues?.get(1)
        assertEquals(constant("FS_TRAIL_NAME_FIELD"), ReferenceTrailFields.FS_TRAIL_NAME)
        assertEquals(constant("FS_TRAIL_NUMBER_FIELD"), ReferenceTrailFields.FS_TRAIL_NUMBER)
        assertEquals(constant("BLM_ROUTE_NAME_FIELD"), ReferenceTrailFields.BLM_ROUTE_NAME)
    }
}
