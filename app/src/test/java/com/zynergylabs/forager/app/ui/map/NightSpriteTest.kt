package com.zynergylabs.forager.app.ui.map

import com.zynergylabs.forager.app.map.STREET_SPRITE_ICON_NAMES
import java.io.File
import java.security.MessageDigest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The bundled night sheets (`app/src/main/assets/night-sprite/v5`, dispatch 2026-10-22-06 Amendment 1, RECORD -1566;
 * and `night-sprite/v6`, dispatch 2026-10-31-coder-07 Part 3) are the night rules ([nightSpritePixelsOf]) applied to
 * each sprite's day sheets, pixel for pixel, at 1x and @2x, with the day index unchanged.
 *
 * This test is also how the sheets are made: when a committed sheet is missing or differs, it writes the sheets the
 * rules give to `app/build/test-results/testDebugUnitTest/night-sprite-generated/<version>/` (uploaded by CI with the
 * test report) and fails. Copying those files into the assets directory is the whole build step, and this test then
 * holds them. Reproducible means pixels and index, not PNG bytes.
 */
class NightSpriteTest {

    /** One sprite version: its committed day sheets, their sums, and its bundled night sheets. */
    private class SpriteSet(val version: String, val dayDir: File, val nightDir: File, val daySums: Map<String, String>)

    /**
     * Sprite v5 as R2 serves it: Aspen's sums from the upload's read-back (RECORD -1564; Forager-cartography
     * `docs/audits/2026-10-22-sprite-v5-upload.md`), which the live files also gave on 2026-10-22.
     */
    private val v5 = SpriteSet(
        "v5", File("src/test/resources/sprite-v5-day"), File("src/main/assets/$NIGHT_SPRITE_ASSET_DIR"),
        mapOf(
            "light.json" to "5d4f7869f8826454411d5fe02d9746b8cb46b0dfb4ccb634f6ad79c0729173e4",
            "light.png" to "6a1ddbf336980f4506d0f006072a22cb37ba966feda63fc94d1319731d458622",
            "light@2x.json" to "073f4064c08a35008afbdb2a0b8c19e1962be4906b905890e35f6e30fca2db9b",
            "light@2x.png" to "215987d8e70482f03350af734f347704d80a8c5dd553998af8b87b32414fc838",
        ),
    )

    /**
     * Sprite v6 as `map-style/tools/build-sprite-v5.mjs v6` wrote it on 2026-10-31 (v5 unchanged plus the orchard,
     * vineyard and nursery patterns). These are the bytes to upload to R2 `sprites/v6/`, which is its own go from the
     * owner; the upload's read-back should give these sums.
     */
    private val v6 = SpriteSet(
        "v6", File("src/test/resources/sprite-v6-day"), File("src/main/assets/$NIGHT_SPRITE_V6_ASSET_DIR"),
        mapOf(
            "light.json" to "c1f9b91269f1bb6d133c6f3b243524cf68b77e9be39ba827a07849c99e69fc78",
            "light.png" to "220679a627a383a13efed2e546cc786f6d103cf34ce6a033282285fa8ff4b792",
            "light@2x.json" to "64ad928acb33da5482e935db07e30b50b8ec5a006321670f178abfe9063d6ac5",
            "light@2x.png" to "428573d176ec1386c6237cc430302a2c8452640344ea3c5db673316b7ab58a5e",
        ),
    )

    private val sets = listOf(v5, v6)
    private val generatedRoot = File("build/test-results/testDebugUnitTest/night-sprite-generated")

    /** Sprite v6's own images, below v5's (`SPRITE_V6_ITEMS` in map-style/overrides/forager-fill-in.mjs). */
    private val v6Patterns = setOf("orchard_dots", "vineyard_rows", "nursery_dots")

    private val densities = listOf("", "@2x")

    private fun sha256(file: File): String = MessageDigest.getInstance("SHA-256").digest(file.readBytes()).joinToString("") { "%02x".format(it) }

    private fun index(file: File): JsonObject = Json.parseToJsonElement(file.readText()).jsonObject

    private fun pixels(image: RgbaImage, x: Int, y: Int, w: Int, h: Int): IntArray = image.pixels(x, y, w, h)

    /** The night sheet the rules give for [suffix]: every index entry's day pixels through [nightSpritePixelsOf]. */
    private fun expectedNightSheet(dayDir: File, suffix: String): RgbaImage {
        val day = readRgbaPng(File(dayDir, "light$suffix.png"))
        val night = RgbaImage(day.width, day.height)
        for ((name, entry) in index(File(dayDir, "light$suffix.json"))) {
            val e = entry.jsonObject
            val (x, y, w, h) = listOf("x", "y", "width", "height").map { e.getValue(it).jsonPrimitive.int }
            night.setPixels(x, y, w, h, nightSpritePixelsOf(name, pixels(day, x, y, w, h)))
        }
        return night
    }

    @Test
    fun `the committed day sheets are sprite v5 as R2 serves it, and sprite v6 as built`() {
        for (set in sets) assertEquals(set.version, set.daySums, set.daySums.keys.associateWith { sha256(File(set.dayDir, it)) })
    }

    @Test
    fun `each night sheet is the night rules applied to the day sheet, pixel for pixel, at 1x and 2x, with the day index`() {
        val problems = mutableListOf<String>()
        for (set in sets) for (suffix in densities) {
            val dayDir = set.dayDir
            val nightDir = set.nightDir
            val generatedDir = File(generatedRoot, set.version)
            val expected = expectedNightSheet(dayDir, suffix)
            val dayIndexFile = File(dayDir, "light$suffix.json")
            val png = File(nightDir, "light$suffix.png")
            val json = File(nightDir, "light$suffix.json")
            val sheetProblems = mutableListOf<String>()
            if (!png.exists() || !json.exists()) {
                sheetProblems += "${set.version} light$suffix: no committed night sheet"
            } else {
                if (index(json) != index(dayIndexFile)) sheetProblems += "${set.version} light$suffix.json: not the day index"
                val actual = readRgbaPng(png)
                if (actual.width != expected.width || actual.height != expected.height) {
                    sheetProblems += "${set.version} light$suffix.png: ${actual.width}x${actual.height}, expected ${expected.width}x${expected.height}"
                } else {
                    val differing = index(dayIndexFile).filter { (_, entry) ->
                        val e = entry.jsonObject
                        val (x, y, w, h) = listOf("x", "y", "width", "height").map { e.getValue(it).jsonPrimitive.int }
                        !pixels(actual, x, y, w, h).contentEquals(pixels(expected, x, y, w, h))
                    }.keys
                    if (differing.isNotEmpty()) sheetProblems += "${set.version} light$suffix.png: night pixels differ from the rules for $differing"
                    val whole = pixels(actual, 0, 0, actual.width, actual.height).contentEquals(pixels(expected, 0, 0, expected.width, expected.height))
                    if (!whole && differing.isEmpty()) sheetProblems += "${set.version} light$suffix.png: pixels outside the index entries are not empty"
                }
            }
            if (sheetProblems.isNotEmpty()) {
                generatedDir.mkdirs()
                writeRgbaPng(expected, File(generatedDir, "light$suffix.png"))
                dayIndexFile.copyTo(File(generatedDir, "light$suffix.json"), overwrite = true)
                problems += sheetProblems
            }
        }
        assertEquals("the rules' sheets are written to ${generatedRoot.absolutePath}", emptyList<String>(), problems)
    }

    @Test
    fun `sprite v6 is sprite v5 with every entry unchanged, plus the three orchard patterns`() {
        for (suffix in densities) {
            val v5Index = index(File(v5.dayDir, "light$suffix.json"))
            val v6Index = index(File(v6.dayDir, "light$suffix.json"))
            assertEquals(v5Index.keys + v6Patterns, v6Index.keys)
            for ((name, entry) in v5Index) assertEquals("$suffix $name", entry, v6Index.getValue(name))
            // Street v4, which names sprite v6, records every v6 image but the painted tree glyph as one its night inverts.
            val v4Icons = index(File("../map-style/forager-street-v4.json")).getValue("metadata").jsonObject
                .getValue("forager:sprite-icons").jsonArray.map { it.jsonPrimitive.content }.toSet()
            assertEquals(v6Index.keys - "tree_dark_irregular", v4Icons)
            // None of the three is painted at night: each takes the plain inversion, as ruled.
            assertEquals(emptySet<String>(), v6Patterns.filter { it in STREET_NIGHT_PATTERN_PAINTS || it in STREET_NIGHT_ICON_RINGS }.toSet())
        }
    }

    @Test
    fun `the night set is the 61 images of sprite v5, every icon the style records and both patterns`() {
        for (suffix in densities) {
            val names = index(File(v5.dayDir, "light$suffix.json")).keys
            assertEquals(61, names.size)
            assertEquals(STREET_SPRITE_ICON_NAMES.toSet() + "tree_dark_irregular", names)
            assertEquals(STREET_NIGHT_PATTERN_PAINTS.keys, names.filter { it in STREET_NIGHT_PATTERN_PAINTS }.toSet())
            assertEquals(STREET_NIGHT_ICON_RINGS.keys, names.filter { it in STREET_NIGHT_ICON_RINGS }.toSet())
        }
    }
}
